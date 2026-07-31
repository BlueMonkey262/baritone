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
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Places oak logs on each of the three axes and checks the {@code axis} state property.
 * <p>
 * Horizontal logs cannot be placed correctly against the floor: clicking the floor's top face
 * creates a vertical log. The X and Z targets therefore each have a stone support beside the face
 * the builder must click. Both supports sit outside the schematic bounds so the builder neither
 * removes them as unwanted blocks nor depends on another log being placed first.
 */
public final class LogsAxesScenario extends TestScenario {

    /** Where the row of logs starts, relative to the arena origin. */
    private static final int BUILD_X = 6;

    /** Keeps the three targets independent and leaves room to reach every support face. */
    private static final int SPACING = 3;

    private static final Direction.Axis[] AXES = {
            Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z
    };

    private int builderInactiveSince = -1;
    private int lastProgressNote;

    @Override
    public String name() {
        return "logs-axes";
    }

    @Override
    public String description() {
        return "Build three oak logs with axis=x/y/z, and check their axis properties";
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

        // A horizontal pillar takes its axis from the clicked face. These supports are outside the
        // schematic's one-block-deep row and let either horizontal target be built first.
        arena.setBlock(BUILD_X - 1, 0, 0, "minecraft:stone");
        arena.setBlock(BUILD_X + 2 * SPACING, 0, -1, "minecraft:stone");

        arena.command("clear @s");
        arena.command("give @s minecraft:oak_log 64");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X - 1, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X + 2 * SPACING, 0, -1).is(Blocks.STONE)
                && targetsAreAir(arena);
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos origin = arena.at(BUILD_X, 0, 0);
        arena.note("building %d oak logs at %s, axes %s", AXES.length, origin, axisList());
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(),
                schematic(),
                new Vec3i(origin.x, origin.y, origin.z)
        );
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        Tally tally = tally(arena);
        if (tally.correct == AXES.length) {
            return Verdict.pass(String.format(
                    "all %d oak logs placed on the right axes in %d ticks (%.1fs)",
                    AXES.length, elapsedTicks, elapsedTicks / 20.0
            ));
        }

        if (!arena.baritone().getBuilderProcess().isActive()) {
            if (this.builderInactiveSince < 0) {
                this.builderInactiveSince = elapsedTicks;
            } else if (elapsedTicks - this.builderInactiveSince > 40) {
                tally.detail.forEach(arena::note);
                return Verdict.fail(
                        "builder stopped with %d of %d logs correct: %d missing, %d on the wrong axis",
                        tally.correct, AXES.length, tally.missing, tally.misoriented
                );
            }
        } else {
            this.builderInactiveSince = -1;
        }

        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %d correct, %d missing, %d on the wrong axis",
                    elapsedTicks / 20, tally.correct, tally.missing, tally.misoriented);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        Tally tally = tally(arena);
        tally.detail.forEach(arena::note);
        return String.format("%d correct, %d missing, %d on the wrong axis",
                tally.correct, tally.missing, tally.misoriented);
    }

    private boolean targetsAreAir(TestArena arena) {
        for (int i = 0; i < AXES.length; i++) {
            if (!arena.stateAt(BUILD_X + i * SPACING, 0, 0).isAir()) {
                return false;
            }
        }
        return true;
    }

    private String axisList() {
        StringBuilder out = new StringBuilder();
        for (Direction.Axis axis : AXES) {
            out.append(out.length() == 0 ? "" : ", ").append(axis.getName());
        }
        return out.toString();
    }

    private Tally tally(TestArena arena) {
        Tally tally = new Tally();
        for (int i = 0; i < AXES.length; i++) {
            int x = BUILD_X + i * SPACING;
            BlockState actual = arena.stateAt(x, 0, 0);
            if (!actual.is(Blocks.OAK_LOG)) {
                tally.missing++;
                tally.detail.add(String.format("%s: wanted oak log with axis=%s, found %s",
                        arena.at(x, 0, 0), AXES[i].getName(), actual.getBlock().getName().getString()));
                continue;
            }
            Direction.Axis axis = actual.getValue(RotatedPillarBlock.AXIS);
            if (axis == AXES[i]) {
                tally.correct++;
            } else {
                tally.misoriented++;
                tally.detail.add(String.format("%s: wanted oak log with axis=%s, got axis=%s",
                        arena.at(x, 0, 0), AXES[i].getName(), axis.getName()));
            }
        }
        return tally;
    }

    private static StaticSchematic schematic() {
        int width = (AXES.length - 1) * SPACING + 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[][][] states = new BlockState[width][1][1];
        for (int x = 0; x < width; x++) {
            states[x][0][0] = air;
        }
        for (int i = 0; i < AXES.length; i++) {
            states[i * SPACING][0][0] = Blocks.OAK_LOG.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, AXES[i]);
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
