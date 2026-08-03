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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.testing.scenario;

import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import baritone.utils.schematic.StaticSchematic;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds an observer looking directly at a piston, with both components facing east.
 * <p>
 * The piston is the observer's declared placement support and the stone beyond the piston is its
 * own support. The two components follow opposite orientation rules, verified against the class
 * files in the loom jar rather than inferred from prose: {@code ObserverBlock} computes
 * {@code getNearestLookingDirection().getOpposite().getOpposite()}, i.e. facing follows the
 * player's look, while {@code PistonBaseBlock} applies a single {@code getOpposite()} and so
 * inverts it. Read the bytecode before changing this comment; it has been stated backwards before.
 * <p>
 * <b>This scenario does not yet discriminate those rules.</b> {@code FACING} is horizontal and the
 * target sits on a stone floor, so Baritone can pick a standing position whose look satisfies
 * either rule — horizontal cases pass with the support on the wrong side. It is registered
 * uncurated for that reason. Making it a real orientation test means adding a vertical pair, where
 * only one rule admits a reachable standing position.
 */
public final class PistonObserverPairScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final Direction FACING = Direction.EAST;

    private int builderInactiveSince = -1;
    private int lastProgressNote;

    @Override
    public String name() {
        return "piston-observer-pair";
    }

    @Override
    public String description() {
        return "Build an observer facing a piston, with both components facing east";
    }

    @Override
    public int tickBudget() {
        return 20 * 240;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        settings.put("buildOrientBeforePlacing", true);
        settings.put("buildIgnoreDirection", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -12, 16, -1, 12, "minecraft:stone");
        // This support is inside the schematic bounding box and is therefore declared there too.
        arena.setBlock(supportX(), 0, supportZ(), "minecraft:stone");
        arena.command("clear @s");
        arena.command("give @s minecraft:observer 64");
        arena.command("give @s minecraft:piston 64");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(observerX(), 0, observerZ()).isAir()
                && arena.stateAt(pistonX(), 0, pistonZ()).isAir()
                && arena.stateAt(supportX(), 0, supportZ()).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos origin = arena.at(BUILD_X + schematicMinX(), 0, schematicMinZ());
        arena.note("building observer -> piston pair at %s; both face east", origin);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), schematic(), new Vec3i(origin.x, origin.y, origin.z)
        );
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        PairState state = state(arena);
        if (state.complete()) {
            return Verdict.pass(String.format(
                    "observer faces the east-facing piston in %d ticks (%.1fs)",
                    elapsedTicks, elapsedTicks / 20.0
            ));
        }
        if (arena.baritone().getBuilderProcess().isPaused()) {
            return fail(arena, "builder paused", state);
        }
        if (!arena.baritone().getBuilderProcess().isActive()) {
            if (this.builderInactiveSince < 0) {
                this.builderInactiveSince = elapsedTicks;
            } else if (elapsedTicks - this.builderInactiveSince > 40) {
                return fail(arena, "builder stopped", state);
            }
        } else {
            this.builderInactiveSince = -1;
        }
        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %s", elapsedTicks / 20, state.summary());
        }
        return null;
    }

    @Override
    public String progressMarker(TestArena arena) {
        return state(arena).summary();
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        PairState state = state(arena);
        arena.note(state.summary());
        return state.summary();
    }

    private Verdict fail(TestArena arena, String prefix, PairState state) {
        arena.note(state.summary());
        return Verdict.fail(prefix + " with " + state.summary());
    }

    private PairState state(TestArena arena) {
        BlockState observer = arena.stateAt(observerX(), 0, observerZ());
        BlockState piston = arena.stateAt(pistonX(), 0, pistonZ());
        boolean observerCorrect = observer.is(Blocks.OBSERVER)
                && observer.getValue(BlockStateProperties.FACING) == FACING;
        boolean pistonCorrect = piston.is(Blocks.PISTON)
                && piston.getValue(BlockStateProperties.FACING) == FACING;
        return new PairState(observerCorrect, pistonCorrect, observer, piston);
    }

    private static StaticSchematic schematic() {
        int minX = schematicMinX();
        int minZ = schematicMinZ();
        BlockState[][][] states = new BlockState[schematicMaxX() - minX + 1][schematicMaxZ() - minZ + 1][1];
        states[observerX() - BUILD_X - minX][observerZ() - minZ][0] = Blocks.OBSERVER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, FACING);
        states[pistonX() - BUILD_X - minX][pistonZ() - minZ][0] = Blocks.PISTON.defaultBlockState()
                .setValue(BlockStateProperties.FACING, FACING);
        states[supportX() - BUILD_X - minX][supportZ() - minZ][0] = Blocks.STONE.defaultBlockState();
        return new StaticSchematic(states);
    }

    private static int observerX() {
        return BUILD_X;
    }

    private static int observerZ() {
        return 0;
    }

    private static int pistonX() {
        return observerX() + FACING.getStepX();
    }

    private static int pistonZ() {
        return observerZ() + FACING.getStepZ();
    }

    private static int supportX() {
        return pistonX() + FACING.getStepX();
    }

    private static int supportZ() {
        return pistonZ() + FACING.getStepZ();
    }

    private static int schematicMinX() {
        return Math.min(0, 2 * FACING.getStepX());
    }

    private static int schematicMaxX() {
        return Math.max(0, 2 * FACING.getStepX());
    }

    private static int schematicMinZ() {
        return Math.min(0, 2 * FACING.getStepZ());
    }

    private static int schematicMaxZ() {
        return Math.max(0, 2 * FACING.getStepZ());
    }

    private static final class PairState {
        private final boolean observerCorrect;
        private final boolean pistonCorrect;
        private final BlockState observer;
        private final BlockState piston;

        private PairState(boolean observerCorrect, boolean pistonCorrect, BlockState observer, BlockState piston) {
            this.observerCorrect = observerCorrect;
            this.pistonCorrect = pistonCorrect;
            this.observer = observer;
            this.piston = piston;
        }

        boolean complete() {
            return observerCorrect && pistonCorrect;
        }

        String summary() {
            return String.format("observer wanted east and is %s; piston wanted east and is %s",
                    describe(observer, Blocks.OBSERVER), describe(piston, Blocks.PISTON));
        }

        private static String describe(BlockState state, net.minecraft.world.level.block.Block wanted) {
            if (!state.is(wanted)) {
                return state.getBlock().getName().getString();
            }
            return state.getValue(BlockStateProperties.FACING).getName();
        }
    }
}
