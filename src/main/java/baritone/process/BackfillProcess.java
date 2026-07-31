/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.movement.IMovement;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.pathing.path.PathExecutor;
import baritone.utils.BaritoneProcessHelper;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;

public final class BackfillProcess extends BaritoneProcessHelper {

    public HashMap<BlockPos, BlockState> blocksToReplace = new HashMap<>();
    private boolean reportedParkourIncompatibility;

    public BackfillProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        if (!Baritone.settings().backfill.value || !Baritone.settings().allowParkour.value) {
            reportedParkourIncompatibility = false;
        }
        if (ctx.player() == null || ctx.world() == null) {
            return false;
        }
        if (!Baritone.settings().backfill.value) {
            return false;
        }
        if (Baritone.settings().allowParkour.value) {
            if (!reportedParkourIncompatibility) {
                logDirect("Backfill cannot be used with allowParkour true");
                reportedParkourIncompatibility = true;
            }
            return false;
        }
        for (BlockPos pos : new ArrayList<>(blocksToReplace.keySet())) {
            if (ctx.world().getChunk(pos) instanceof EmptyLevelChunk || ctx.world().getBlockState(pos).getBlock() != Blocks.AIR) {
                blocksToReplace.remove(pos);
            }
        }
        amIBreakingABlockHMMMMMMM();
        baritone.getInputOverrideHandler().clearAllKeys();

        return !toFillIn().isEmpty();
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (!isSafeToCancel) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        baritone.getInputOverrideHandler().clearAllKeys();
        // Restrict placement to junk blocks for the duration of this tick, so backfilling a tunnel
        // can't quietly consume the materials we're trying to build with.
        baritone.getInventoryBehavior().setThrowawayRestriction(Baritone.settings().backfillBlocks.value);
        try {
            return tickPlacement();
        } finally {
            baritone.getInventoryBehavior().setThrowawayRestriction(null);
        }
    }

    private PathingCommand tickPlacement() {
        for (BlockPos toPlace : toFillIn()) {
            MovementState fake = new MovementState();
            switch (MovementHelper.attemptToPlaceABlock(fake, baritone, toPlace, false, false)) {
                case NO_OPTION:
                    continue;
                case READY_TO_PLACE:
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                case ATTEMPTING:
                    // patience
                    baritone.getLookBehavior().updateTarget(fake.getTarget().getRotation().get(), true);
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                default:
                    throw new IllegalStateException();
            }
        }
        return new PathingCommand(null, PathingCommandType.DEFER); // cede to other process
    }

    private void amIBreakingABlockHMMMMMMM() {
        if (!ctx.getSelectedBlock().isPresent() || !baritone.getPathingBehavior().isPathing()) {
            return;
        }
        BlockPos breaking = ctx.getSelectedBlock().get();
        if (baritone.getBuilderProcess().managesPosition(breaking)) {
            return; // inside an active build; the builder owns this position, not us
        }
        blocksToReplace.put(breaking, ctx.world().getBlockState(breaking));
    }

    public List<BlockPos> toFillIn() {
        return blocksToReplace
                .keySet()
                .stream()
                .filter(pos -> ctx.world().getBlockState(pos).getBlock() == Blocks.AIR)
                .filter(pos -> baritone.getBuilderProcess().placementPlausible(pos, Blocks.DIRT.defaultBlockState()))
                .filter(pos -> !partOfCurrentMovement(pos))
                .filter(pos -> !baritone.getBuilderProcess().managesPosition(pos))
                .sorted(Comparator.<BlockPos>comparingDouble(ctx.playerFeet()::distSqr).reversed())
                .collect(Collectors.toList());
    }

    /**
     * How many movements ahead of the player to protect from backfilling.
     */
    private static final int LOOKAHEAD_MOVEMENTS = 10;

    /**
     * Whether the path still needs this block to be open.
     * <p>
     * This deliberately looks at upcoming movements, not just the one being executed. Checking only
     * the current movement means a hole dug for a movement a couple of steps away gets filled back
     * in immediately, and then has to be dug out again -- which reads as the bot breaking a block
     * and instantly replacing it.
     */
    private boolean partOfCurrentMovement(BlockPos pos) {
        PathExecutor exec = baritone.getPathingBehavior().getCurrent();
        if (exec == null || exec.finished() || exec.failed()) {
            return false;
        }
        List<IMovement> movements = exec.getPath().movements();
        int from = exec.getPosition();
        int to = Math.min(movements.size(), from + LOOKAHEAD_MOVEMENTS);
        for (int i = from; i < to; i++) {
            if (Arrays.asList(((Movement) movements.get(i)).toBreakAll()).contains(pos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onLostControl() {
        if (blocksToReplace != null && !blocksToReplace.isEmpty()) {
            blocksToReplace.clear();
        }
    }

    @Override
    public String displayName0() {
        return "Backfill";
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public double priority() {
        return 5;
    }
}
