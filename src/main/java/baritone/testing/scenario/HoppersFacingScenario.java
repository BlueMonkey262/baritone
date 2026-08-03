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
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds hoppers pointing in every legal direction.
 * <p>
 * A hopper faces the block clicked, rather than taking its facing from the player's horizontal look
 * direction. Every clicked support is plain stone and is declared in the schematic, so the builder
 * preserves it while placing the hopper.
 * <p>
 * This currently fails at tick one, and the cause is known: {@code BuilderProcess.ORIENTATION_PROPS}
 * names {@code DirectionalBlock.FACING}, which is {@code BlockStateProperties.FACING}, while a
 * hopper's facing is the distinct {@code BlockStateProperties.FACING_HOPPER}. {@code couldProduce}
 * therefore rejects a plain hopper as material for any specific facing, and the builder reports a
 * shortage while holding 64 of them. Tracked as U-local-04; the earlier stone-floor lead was wrong.
 */
public final class HoppersFacingScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int SPACING = 4;
    private static final int TARGET_Y = 1;
    private static final int Z_MIN = -1;

    private static final Direction[] FACINGS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN
    };

    private int builderInactiveSince = -1;
    private int lastProgressNote;

    @Override
    public String name() {
        return "hoppers-facing";
    }

    @Override
    public String description() {
        return "Build hoppers facing north, east, south, west, and down";
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
        arena.fill(-4, -3, -12, 30, -1, 12, "minecraft:stone");
        // The schematic includes the floor and the side supports; staging them first makes the
        // builder's only work the directional hoppers rather than an unrelated support build.
        int width = targetX(FACINGS.length - 1) - BUILD_X + 2;
        arena.fill(BUILD_X, 0, Z_MIN, BUILD_X + width - 1, 0, 1, "minecraft:stone");
        for (int i = 0; i < FACINGS.length; i++) {
            Direction facing = FACINGS[i];
            arena.setBlock(targetX(i) + facing.getStepX(), TARGET_Y + facing.getStepY(),
                    facing.getStepZ(), "minecraft:stone");
        }
        arena.command("clear @s");
        arena.command("give @s minecraft:hopper 64");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        if (!arena.stateAt(0, -1, 0).is(Blocks.STONE)) {
            return false;
        }
        for (int i = 0; i < FACINGS.length; i++) {
            Direction facing = FACINGS[i];
            if (!arena.stateAt(targetX(i), TARGET_Y, 0).isAir()) {
                return false;
            }
            if (!arena.stateAt(targetX(i) + facing.getStepX(), TARGET_Y + facing.getStepY(),
                    facing.getStepZ()).is(Blocks.STONE)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos origin = arena.at(BUILD_X, 0, Z_MIN);
        arena.note("building five hoppers at %s with one declared stone support per facing", origin);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), schematic(), new Vec3i(origin.x, origin.y, origin.z)
        );
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        Tally tally = tally(arena);
        if (tally.correct == FACINGS.length) {
            return Verdict.pass(String.format(
                    "all five hoppers point at their declared stone supports in %d ticks (%.1fs)",
                    elapsedTicks, elapsedTicks / 20.0
            ));
        }
        if (arena.baritone().getBuilderProcess().isPaused()) {
            return fail(arena, "builder paused", tally);
        }
        if (!arena.baritone().getBuilderProcess().isActive()) {
            if (this.builderInactiveSince < 0) {
                this.builderInactiveSince = elapsedTicks;
            } else if (elapsedTicks - this.builderInactiveSince > 40) {
                return fail(arena, "builder stopped", tally);
            }
        } else {
            this.builderInactiveSince = -1;
        }
        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %s", elapsedTicks / 20, tally.summary());
        }
        return null;
    }

    @Override
    public String progressMarker(TestArena arena) {
        return tally(arena).summary();
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        Tally tally = tally(arena);
        tally.detail.forEach(arena::note);
        return tally.summary();
    }

    private Verdict fail(TestArena arena, String prefix, Tally tally) {
        tally.detail.forEach(arena::note);
        return Verdict.fail(prefix + " with " + tally.summary());
    }

    private Tally tally(TestArena arena) {
        Tally tally = new Tally();
        for (int i = 0; i < FACINGS.length; i++) {
            Direction wantedFacing = FACINGS[i];
            BlockState actual = arena.stateAt(targetX(i), TARGET_Y, 0);
            if (!actual.is(Blocks.HOPPER)) {
                tally.missing++;
                tally.detail.add(String.format("%s: wanted hopper[facing=%s], found %s",
                        arena.at(targetX(i), TARGET_Y, 0), wantedFacing.getName(),
                        actual.getBlock().getName().getString()));
                continue;
            }
            Direction actualFacing = actual.getValue(HopperBlock.FACING);
            if (actualFacing == wantedFacing) {
                tally.correct++;
            } else {
                tally.wrongFacing++;
                tally.detail.add(String.format("%s: wanted hopper[facing=%s], got hopper[facing=%s]",
                        arena.at(targetX(i), TARGET_Y, 0), wantedFacing.getName(), actualFacing.getName()));
            }
        }
        return tally;
    }

    private static int targetX(int index) {
        return BUILD_X + index * SPACING;
    }

    private static StaticSchematic schematic() {
        int width = targetX(FACINGS.length - 1) - BUILD_X + 2;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[][][] states = new BlockState[width][3][2];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < 3; z++) {
                states[x][z][0] = Blocks.STONE.defaultBlockState();
                states[x][z][1] = air;
            }
        }
        for (int i = 0; i < FACINGS.length; i++) {
            Direction facing = FACINGS[i];
            int x = i * SPACING;
            states[x][1][TARGET_Y] = Blocks.HOPPER.defaultBlockState()
                    .setValue(HopperBlock.FACING, facing);
            if (facing != Direction.DOWN) {
                states[x + facing.getStepX()][1 + facing.getStepZ()][TARGET_Y] = Blocks.STONE.defaultBlockState();
            }
        }
        return new StaticSchematic(states);
    }

    private static final class Tally {
        int correct;
        int missing;
        int wrongFacing;
        final List<String> detail = new ArrayList<>();

        String summary() {
            return String.format("%d correct, %d missing, %d facing the wrong way",
                    correct, missing, wrongFacing);
        }
    }
}
