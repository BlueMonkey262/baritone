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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds each horizontal stair facing in both the bottom and top half of its block space.
 * <p>
 * A top stair is not merely a bottom stair with another state value: placing one requires aiming
 * at the upper part of a supporting face. This makes the eight combinations here a direct test of
 * whether direction-aware building chooses the right hit target as well as the right look direction.
 * <p>
 * Stairs are three blocks apart along both axes so none of them can negotiate an inner or outer
 * {@code shape} with a neighbour. The scenario can therefore attribute every mismatch to the two
 * placement-controlled properties it asks for: {@code facing} and {@code half}.
 */
public final class StairsHalvesScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int BUILD_Z = -3;
    private static final int SPACING = 3;

    private static final Direction[] FACINGS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private static final Half[] HALVES = {
            Half.BOTTOM, Half.TOP
    };

    private static final int TOTAL_STAIRS = FACINGS.length * HALVES.length;

    private int builderInactiveSince = -1;
    private int lastProgressNote;

    @Override
    public String name() {
        return "stairs-halves";
    }

    @Override
    public String description() {
        return "Build all four stair facings as both bottom and top halves, and check both state properties";
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
        arena.fill(-4, -3, -12, 24, -1, 12, "minecraft:stone");
        // A top-half stair is selected by clicking the upper part of a side face, not by clicking
        // the floor. These supports are outside the schematic's two rows.
        int topRowZ = BUILD_Z + SPACING;
        for (int i = 0; i < FACINGS.length; i++) {
            arena.setBlock(BUILD_X + i * SPACING, 0, topRowZ + 1, "minecraft:stone");
        }
        arena.command("clear @s");
        arena.command("give @s minecraft:oak_stairs 64");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 0, BUILD_Z).isAir()
                && arena.stateAt(BUILD_X + (FACINGS.length - 1) * SPACING, 0,
                BUILD_Z + (HALVES.length - 1) * SPACING).isAir()
                && arena.stateAt(BUILD_X, 0, BUILD_Z + SPACING + 1).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos origin = arena.at(BUILD_X, 0, BUILD_Z);
        arena.note("building %d oak stairs at %s: four facings in each of bottom and top halves",
                TOTAL_STAIRS, origin);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(),
                schematic(),
                new Vec3i(origin.x, origin.y, origin.z)
        );
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        Tally tally = tally(arena);
        if (tally.correct == TOTAL_STAIRS) {
            return Verdict.pass(String.format(
                    "all %d stairs placed with the right facing and half in %d ticks (%.1fs)",
                    TOTAL_STAIRS, elapsedTicks, elapsedTicks / 20.0
            ));
        }

        // A paused builder still owns its schematic, so isActive() intentionally remains true.
        // Treat that as a prompt verdict rather than spending the rest of the scenario budget
        // polling a process that has explicitly stopped working.
        if (arena.baritone().getBuilderProcess().isPaused()) {
            tally.detail.forEach(arena::note);
            return Verdict.fail(
                    "builder paused with %d of %d stairs correct: %d missing, "
                            + "%d misoriented (%d facing mismatches, %d half mismatches)",
                    tally.correct, TOTAL_STAIRS, tally.missing, tally.misoriented,
                    tally.wrongFacing, tally.wrongHalf
            );
        }

        if (!arena.baritone().getBuilderProcess().isActive()) {
            if (this.builderInactiveSince < 0) {
                this.builderInactiveSince = elapsedTicks;
            } else if (elapsedTicks - this.builderInactiveSince > 40) {
                tally.detail.forEach(arena::note);
                return Verdict.fail(
                        "builder stopped with %d of %d stairs correct: %d missing, "
                                + "%d misoriented (%d facing mismatches, %d half mismatches)",
                        tally.correct, TOTAL_STAIRS, tally.missing, tally.misoriented,
                        tally.wrongFacing, tally.wrongHalf
                );
            }
        } else {
            this.builderInactiveSince = -1;
        }

        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %d correct, %d missing, %d misoriented "
                            + "(%d facing mismatches, %d half mismatches)",
                    elapsedTicks / 20, tally.correct, tally.missing, tally.misoriented,
                    tally.wrongFacing, tally.wrongHalf);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        Tally tally = tally(arena);
        tally.detail.forEach(arena::note);
        return String.format(
                "%d correct, %d missing, %d misoriented (%d facing mismatches, %d half mismatches)",
                tally.correct, tally.missing, tally.misoriented, tally.wrongFacing, tally.wrongHalf
        );
    }

    private Tally tally(TestArena arena) {
        Tally tally = new Tally();
        for (int halfIndex = 0; halfIndex < HALVES.length; halfIndex++) {
            for (int facingIndex = 0; facingIndex < FACINGS.length; facingIndex++) {
                int x = BUILD_X + facingIndex * SPACING;
                int z = BUILD_Z + halfIndex * SPACING;
                Direction wantedFacing = FACINGS[facingIndex];
                Half wantedHalf = HALVES[halfIndex];
                BlockState actual = arena.stateAt(x, 0, z);

                if (!actual.is(Blocks.OAK_STAIRS)) {
                    tally.missing++;
                    tally.detail.add(String.format(
                            "%s: wanted oak_stairs[facing=%s,half=%s], found %s",
                            arena.at(x, 0, z), wantedFacing.getName(), halfName(wantedHalf),
                            actual.getBlock().getName().getString()
                    ));
                    continue;
                }

                Direction actualFacing = actual.getValue(BlockStateProperties.HORIZONTAL_FACING);
                Half actualHalf = actual.getValue(StairBlock.HALF);
                boolean facingMatches = actualFacing == wantedFacing;
                boolean halfMatches = actualHalf == wantedHalf;
                if (facingMatches && halfMatches) {
                    tally.correct++;
                    continue;
                }

                tally.misoriented++;
                if (!facingMatches) {
                    tally.wrongFacing++;
                }
                if (!halfMatches) {
                    tally.wrongHalf++;
                }
                tally.detail.add(String.format(
                        "%s: wanted oak_stairs[facing=%s,half=%s], got "
                                + "oak_stairs[facing=%s,half=%s]",
                        arena.at(x, 0, z), wantedFacing.getName(), halfName(wantedHalf),
                        actualFacing.getName(), halfName(actualHalf)
                ));
            }
        }
        return tally;
    }

    private static String halfName(Half half) {
        return half == Half.TOP ? "top" : "bottom";
    }

    private static StaticSchematic schematic() {
        int width = (FACINGS.length - 1) * SPACING + 1;
        int depth = (HALVES.length - 1) * SPACING + 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[][][] states = new BlockState[width][depth][1];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                states[x][z][0] = air;
            }
        }
        for (int halfIndex = 0; halfIndex < HALVES.length; halfIndex++) {
            for (int facingIndex = 0; facingIndex < FACINGS.length; facingIndex++) {
                states[facingIndex * SPACING][halfIndex * SPACING][0] =
                        Blocks.OAK_STAIRS.defaultBlockState()
                                .setValue(BlockStateProperties.HORIZONTAL_FACING, FACINGS[facingIndex])
                                .setValue(StairBlock.HALF, HALVES[halfIndex]);
            }
        }
        return new StaticSchematic(states);
    }

    private static final class Tally {
        int correct;
        int missing;
        int misoriented;
        int wrongFacing;
        int wrongHalf;
        final List<String> detail = new ArrayList<>();
    }
}
