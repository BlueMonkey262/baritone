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
 * Places stairs facing four different ways, and checks they ended up facing those ways.
 * <p>
 * Every other build scenario uses white concrete, which has exactly one state, so none of them can
 * tell a correctly built structure from a pile of blocks in the right positions. Orientation is
 * where building actually gets hard: the facing of a stair depends on where the player stood and
 * which way they looked when they clicked, so getting it right means choosing a standing position
 * per block rather than placing from wherever the builder happens to be.
 * <p>
 * This fork has a {@code buildOrientBeforePlacing} setting that does exactly that, on by default and
 * until now completely untested. This scenario is what tests it.
 * <p>
 * <b>The stairs are spaced two apart on purpose.</b> Adjacent stairs negotiate a {@code shape}
 * property between themselves -- inner and outer corners -- which the world computes and a schematic
 * does not control. Touching stairs would therefore fail the comparison for reasons that have
 * nothing to do with whether the builder aimed correctly.
 */
public final class DirectionalBuildScenario extends TestScenario {

    /** Where the row of stairs starts, relative to the arena origin. */
    private static final int BUILD_X = 6;

    /** Gap between stairs, to keep them from connecting. */
    private static final int SPACING = 3;

    private static final Direction[] FACINGS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private int builderInactiveSince = -1;
    private int lastProgressNote;

    @Override
    public String name() {
        return "build-directional";
    }

    @Override
    public String description() {
        return "Build four stairs facing four different ways, and check the orientations came out right";
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
        // The setting under test. Explicit rather than inherited, so the scenario still means what
        // it says if the default ever changes.
        settings.put("buildOrientBeforePlacing", true);
        settings.put("buildIgnoreDirection", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -12, 24, -1, 12, "minecraft:stone");
        arena.command("clear @s");
        arena.command("give @s minecraft:oak_stairs 64");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE) && arena.stateAt(BUILD_X, 0, 0).isAir();
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos origin = arena.at(BUILD_X, 0, 0);
        arena.note("building %d oak stairs at %s, facings %s", FACINGS.length, origin, facingList());
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(),
                schematic(),
                new Vec3i(origin.x, origin.y, origin.z)
        );
    }

    @Override
    public TestScenario.Verdict poll(TestArena arena, int elapsedTicks) {
        Tally tally = tally(arena);
        if (tally.correct == FACINGS.length) {
            return Verdict.pass(String.format(
                    "all %d stairs placed facing the right way in %d ticks (%.1fs)",
                    FACINGS.length, elapsedTicks, elapsedTicks / 20.0
            ));
        }

        if (!arena.baritone().getBuilderProcess().isActive()) {
            if (this.builderInactiveSince < 0) {
                this.builderInactiveSince = elapsedTicks;
            } else if (elapsedTicks - this.builderInactiveSince > 40) {
                tally.detail.forEach(arena::note);
                // Misoriented is the interesting outcome and reads very differently from missing:
                // the builder thought it was finished, because from its point of view the block it
                // wanted is there. Saying which happened is the whole point of this scenario.
                return Verdict.fail(
                        "builder stopped with %d of %d stairs correct: %d missing, %d facing the wrong way",
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
    public String timeoutDiagnosis(TestArena arena) {
        Tally tally = tally(arena);
        tally.detail.forEach(arena::note);
        return String.format("%d correct, %d missing, %d facing the wrong way",
                tally.correct, tally.missing, tally.misoriented);
    }

    private String facingList() {
        StringBuilder out = new StringBuilder();
        for (Direction facing : FACINGS) {
            out.append(out.length() == 0 ? "" : ", ").append(facing.getName());
        }
        return out.toString();
    }

    private Tally tally(TestArena arena) {
        Tally tally = new Tally();
        for (int i = 0; i < FACINGS.length; i++) {
            BlockState actual = arena.stateAt(BUILD_X + i * SPACING, 0, 0);
            if (!actual.is(Blocks.OAK_STAIRS)) {
                tally.missing++;
                tally.detail.add(String.format("%s: wanted oak stairs facing %s, found %s",
                        arena.at(BUILD_X + i * SPACING, 0, 0), FACINGS[i].getName(),
                        actual.getBlock().getName().getString()));
                continue;
            }
            Direction facing = actual.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (facing == FACINGS[i]) {
                tally.correct++;
            } else {
                tally.misoriented++;
                tally.detail.add(String.format("%s: wanted facing %s, got facing %s",
                        arena.at(BUILD_X + i * SPACING, 0, 0), FACINGS[i].getName(), facing.getName()));
            }
        }
        return tally;
    }

    private static StaticSchematic schematic() {
        int width = (FACINGS.length - 1) * SPACING + 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[][][] states = new BlockState[width][1][1];
        for (int x = 0; x < width; x++) {
            states[x][0][0] = air;
        }
        for (int i = 0; i < FACINGS.length; i++) {
            states[i * SPACING][0][0] = Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, FACINGS[i]);
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
