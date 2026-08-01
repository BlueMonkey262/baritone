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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds observers with every possible value of their six-way {@code facing} property.
 * <p>
 * Horizontal observers exercise the same stand-on-the-correct-side machinery as stairs, while the
 * up- and down-facing observers also require the builder to choose a useful vertical angle. The
 * up-facing target needs two staged blocks, for two different reasons. The block above it is what
 * the real placement clicks: standing below and looking straight up, its underside is the first
 * face the ray meets. The side support exists because {@code placementGoal} gates on
 * {@code HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP}, so a target whose only
 * neighbour is above it never reaches the orientation planner at all. (The side support is also
 * what {@code acceptableFacings} happens to match {@code UP} against first, since a vertical look
 * and a vertical face normal are perpendicular and so permitted; the answer is the same either
 * way.) The down-facing target has an adjacent ledge so it can be aimed at from above.
 */
public final class ObserverBuildScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int SPACING = 3;
    // Exactly two above the arena floor's standing level. The UP goal puts the player's feet at
    // targetY-2 and searchForPlacables only scans to dy=+2, so this is the one height at which
    // both are satisfiable: feet at y=0, target at y=2, its underside clicked from below.
    private static final int UP_TARGET_Y = 2;
    private static final int DOWN_TARGET_Y = 0;
    private static final int SUPPORT_Z = -1;

    private static final Direction[] FACINGS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST,
            Direction.UP, Direction.DOWN
    };

    private int builderInactiveSince = -1;
    private int lastProgressNote;

    @Override
    public String name() {
        return "build-observers";
    }

    @Override
    public String description() {
        return "Build six observers and verify all horizontal and vertical facing states";
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

        // The side supports are outside the schematic's z=0 slice. The blocks above the vertical
        // targets are declared in the schematic too, so they remain in place for the placement.
        int upX = targetX(4);
        arena.setBlock(upX, UP_TARGET_Y + 1, 0, "minecraft:stone");
        arena.setBlock(upX, UP_TARGET_Y, SUPPORT_Z, "minecraft:stone");
        int downX = targetX(5);
        arena.setBlock(downX, DOWN_TARGET_Y, SUPPORT_Z, "minecraft:stone");
        arena.setBlock(downX, DOWN_TARGET_Y + 1, 0, "minecraft:stone");

        arena.command("clear @s");
        arena.command("give @s minecraft:observer 64");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        if (!arena.stateAt(0, -1, 0).is(Blocks.STONE)
                || !arena.stateAt(targetX(4), UP_TARGET_Y + 1, 0).is(Blocks.STONE)
                || !arena.stateAt(targetX(4), UP_TARGET_Y, SUPPORT_Z).is(Blocks.STONE)
                || !arena.stateAt(targetX(5), DOWN_TARGET_Y, SUPPORT_Z).is(Blocks.STONE)
                || !arena.stateAt(targetX(5), DOWN_TARGET_Y + 1, 0).is(Blocks.STONE)) {
            return false;
        }
        for (int i = 0; i < FACINGS.length; i++) {
            if (!arena.stateAt(targetX(i), targetY(FACINGS[i]), 0).isAir()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos origin = arena.at(BUILD_X, 0, 0);
        arena.note("building %d observers at %s with facings %s", FACINGS.length, origin, facingList());
        arena.note("up-facing target uses the underside of a block above it plus a side support for the placement gate; down-facing target has an adjacent y+1 ledge");
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(),
                schematic(),
                new Vec3i(origin.x, origin.y, origin.z)
        );
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        Tally tally = tally(arena);
        if (tally.correct == FACINGS.length) {
            return Verdict.pass(String.format(
                    "all %d observers placed with the right facing in %d ticks (%.1fs)",
                    FACINGS.length, elapsedTicks, elapsedTicks / 20.0
            ));
        }

        // A paused builder still owns its schematic, so isActive() intentionally remains true.
        // Stop as soon as it has explicitly given up rather than converting that result into a
        // four-minute timeout.
        if (arena.baritone().getBuilderProcess().isPaused()) {
            tally.detail.forEach(arena::note);
            return Verdict.fail(
                    "builder paused with %d of %d observers correct: %d missing, %d facing the wrong way",
                    tally.correct, FACINGS.length, tally.missing, tally.misoriented
            );
        }

        if (!arena.baritone().getBuilderProcess().isActive()) {
            if (this.builderInactiveSince < 0) {
                this.builderInactiveSince = elapsedTicks;
            } else if (elapsedTicks - this.builderInactiveSince > 40) {
                tally.detail.forEach(arena::note);
                return Verdict.fail(
                        "builder stopped with %d of %d observers correct: %d missing, %d facing the wrong way",
                        tally.correct, FACINGS.length, tally.missing, tally.misoriented
                );
            }
        } else {
            this.builderInactiveSince = -1;
        }

        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %d correct, %d missing, %d misoriented",
                    elapsedTicks / 20, tally.correct, tally.missing, tally.misoriented);
        }
        return null;
    }

    @Override
    public String progressMarker(TestArena arena) {
        Tally tally = tally(arena);
        return String.format("%d correct, %d missing, %d misoriented",
                tally.correct, tally.missing, tally.misoriented);
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        Tally tally = tally(arena);
        tally.detail.forEach(arena::note);
        return String.format("%d correct, %d missing, %d facing the wrong way",
                tally.correct, tally.missing, tally.misoriented);
    }

    private Tally tally(TestArena arena) {
        Tally tally = new Tally();
        for (int i = 0; i < FACINGS.length; i++) {
            int x = targetX(i);
            int y = targetY(FACINGS[i]);
            BlockState actual = arena.stateAt(x, y, 0);
            if (!actual.is(Blocks.OBSERVER)) {
                tally.missing++;
                tally.detail.add(String.format("%s: wanted observer[facing=%s], found %s",
                        arena.at(x, y, 0), FACINGS[i].getName(), actual.getBlock().getName().getString()));
                continue;
            }

            Direction actualFacing = actual.getValue(BlockStateProperties.FACING);
            if (actualFacing == FACINGS[i]) {
                tally.correct++;
            } else {
                tally.misoriented++;
                tally.detail.add(String.format("%s: wanted observer[facing=%s], got observer[facing=%s]",
                        arena.at(x, y, 0), FACINGS[i].getName(), actualFacing.getName()));
            }
        }
        return tally;
    }

    private static int targetX(int index) {
        return BUILD_X + index * SPACING;
    }

    private static int targetY(Direction facing) {
        return facing == Direction.UP ? UP_TARGET_Y : DOWN_TARGET_Y;
    }

    private static String facingList() {
        StringBuilder out = new StringBuilder();
        for (Direction facing : FACINGS) {
            out.append(out.length() == 0 ? "" : ", ").append(facing.getName());
        }
        return out.toString();
    }

    private static StaticSchematic schematic() {
        int width = targetX(FACINGS.length - 1) - BUILD_X + 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        // StaticSchematic stores states as [x][z][y], not [x][y][z].
        int height = Math.max(UP_TARGET_Y + 1, DOWN_TARGET_Y + 1) + 1;
        BlockState[][][] states = new BlockState[width][1][height];
        for (int x = 0; x < states.length; x++) {
            for (int y = 0; y < states[x][0].length; y++) {
                states[x][0][y] = air;
            }
        }
        states[targetX(4) - BUILD_X][0][UP_TARGET_Y + 1] = Blocks.STONE.defaultBlockState();
        states[targetX(5) - BUILD_X][0][DOWN_TARGET_Y + 1] = Blocks.STONE.defaultBlockState();
        for (int i = 0; i < FACINGS.length; i++) {
            states[i * SPACING][0][targetY(FACINGS[i])] = Blocks.OBSERVER.defaultBlockState()
                    .setValue(BlockStateProperties.FACING, FACINGS[i]);
        }
        return new StaticSchematic(states);
    }

    private static final class Tally {
        int correct;
        int missing;
        int misoriented;
        final List<String> detail = new ArrayList<>();
    }
}
