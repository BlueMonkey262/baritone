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
 * Builds four repeaters, one for each horizontal facing and delay value.
 * <p>
 * Delay is deliberately not a placement property. The builder must first put down a correctly
 * facing repeater, then right-click it enough times to reach its requested delay. The verdict keeps
 * those two failures separate so a placement-orientation regression is not confused with missing
 * post-placement interaction.
 */
public final class RepeatersDelaysScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int SPACING = 4;

    private static final Direction[] FACINGS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private int builderInactiveSince = -1;
    private int lastProgressNote;

    @Override
    public String name() {
        return "repeaters-delays";
    }

    @Override
    public String description() {
        return "Build four repeaters with all horizontal facings and delays one through four";
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
        for (int i = 0; i < FACINGS.length; i++) {
            int x = targetX(i);
            Direction facing = FACINGS[i];
            // Each support lies on the side the repeater must face. It is part of the schematic,
            // rather than incidental staging, so the builder must leave it there while placing.
            arena.setBlock(x + facing.getStepX(), 0, facing.getStepZ(), "minecraft:stone");
        }
        arena.command("clear @s");
        arena.command("give @s minecraft:repeater 64");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        if (!arena.stateAt(0, -1, 0).is(Blocks.STONE)) {
            return false;
        }
        for (int i = 0; i < FACINGS.length; i++) {
            Direction facing = FACINGS[i];
            if (!arena.stateAt(targetX(i), 0, 0).isAir()
                    || !arena.stateAt(targetX(i) + facing.getStepX(), 0, facing.getStepZ())
                    .is(Blocks.STONE)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos origin = arena.at(BUILD_X + schematicMinX(), 0, -1);
        arena.note("building four repeaters at %s; each has a distinct facing and delay", origin);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), schematic(), new Vec3i(origin.x, origin.y, origin.z)
        );
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        Tally tally = tally(arena);
        if (tally.correct == FACINGS.length) {
            return Verdict.pass(String.format(
                    "all four repeaters have the right facing and delay in %d ticks (%.1fs)",
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
            int wantedDelay = i + 1;
            BlockState actual = arena.stateAt(targetX(i), 0, 0);
            if (!actual.is(Blocks.REPEATER)) {
                tally.missing++;
                tally.detail.add(String.format("%s: wanted repeater[facing=%s,delay=%d], found %s",
                        arena.at(targetX(i), 0, 0), wantedFacing.getName(), wantedDelay,
                        actual.getBlock().getName().getString()));
                continue;
            }
            Direction actualFacing = actual.getValue(BlockStateProperties.HORIZONTAL_FACING);
            int actualDelay = actual.getValue(BlockStateProperties.DELAY);
            if (actualFacing != wantedFacing) {
                tally.wrongFacing++;
                tally.detail.add(String.format("%s: wanted repeater[facing=%s,delay=%d], got "
                                + "repeater[facing=%s,delay=%d]",
                        arena.at(targetX(i), 0, 0), wantedFacing.getName(), wantedDelay,
                        actualFacing.getName(), actualDelay));
            } else if (actualDelay != wantedDelay) {
                tally.wrongDelay++;
                tally.detail.add(String.format("%s: right facing %s, wanted delay=%d, got delay=%d",
                        arena.at(targetX(i), 0, 0), wantedFacing.getName(), wantedDelay, actualDelay));
            } else {
                tally.correct++;
            }
        }
        return tally;
    }

    private static int targetX(int index) {
        return BUILD_X + index * SPACING;
    }

    private static StaticSchematic schematic() {
        int minX = schematicMinX();
        int width = schematicMaxX() - minX + 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        // StaticSchematic stores states as [x][z][y]; z spans the supports on both sides.
        BlockState[][][] states = new BlockState[width][3][1];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < 3; z++) {
                states[x][z][0] = air;
            }
        }
        for (int i = 0; i < FACINGS.length; i++) {
            Direction facing = FACINGS[i];
            int x = i * SPACING - minX;
            states[x][1][0] = Blocks.REPEATER.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                    .setValue(BlockStateProperties.DELAY, i + 1);
            states[x + facing.getStepX()][1 + facing.getStepZ()][0] = Blocks.STONE.defaultBlockState();
        }
        return new StaticSchematic(states);
    }

    /** The schematic includes supports on both sides of a target, independent of FACINGS order. */
    private static int schematicMinX() {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < FACINGS.length; i++) {
            min = Math.min(min, i * SPACING + Math.min(0, FACINGS[i].getStepX()));
        }
        return min;
    }

    private static int schematicMaxX() {
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < FACINGS.length; i++) {
            max = Math.max(max, i * SPACING + Math.max(0, FACINGS[i].getStepX()));
        }
        return max;
    }

    private static final class Tally {
        int correct;
        int missing;
        int wrongFacing;
        int wrongDelay;
        final List<String> detail = new ArrayList<>();

        String summary() {
            return String.format("%d correct, %d missing, %d wrong-facing, %d right-facing wrong-delay",
                    correct, missing, wrongFacing, wrongDelay);
        }
    }
}
