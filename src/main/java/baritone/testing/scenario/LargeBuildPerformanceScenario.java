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
 * along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.testing.scenario;

import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import baritone.utils.schematic.StaticSchematic;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** A real multi-thousand-position build makes placement-scaling regressions visible in ticks. */
public final class LargeBuildPerformanceScenario extends TestScenario {

    private static final int WIDTH = 48;
    private static final int DEPTH = 20;
    private static final int HEIGHT = 2;
    private static final int TOTAL_BLOCKS = WIDTH * DEPTH * HEIGHT;
    private static final int BUILD_X = 2;
    private static final int BUILD_Z = -10;

    /** Generous enough for chunk arrival and ordinary pathing noise, but finite for scaling bugs. */
    private static final int MAX_TICKS = 20 * 300;
    private int bestPlaced = 0;
    private int builderInactiveSince = -1;

    @Override
    public String name() {
        return "large-build-performance";
    }

    @Override
    public String description() {
        return "Build a 48x20x2 slab and fail if placement scaling exceeds the tick bound";
    }

    @Override
    public int tickBudget() {
        return MAX_TICKS + 20 * 30;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -1, -12, 52, -1, 12, "minecraft:stone");
        arena.fill(-4, 0, -12, 52, 5, 12, "minecraft:air");
        arena.command("clear @s");
        arena.command("give @s minecraft:white_concrete " + TOTAL_BLOCKS);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        if (!arena.stateAt(0, -1, 0).is(Blocks.STONE)
                || ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem()) != TOTAL_BLOCKS) {
            return false;
        }
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (!arena.stateAt(BUILD_X + x, y, BUILD_Z + z).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos origin = arena.at(BUILD_X, 0, BUILD_Z);
        arena.note("building %d white concrete blocks at %s; tick bound=%d", TOTAL_BLOCKS, origin, MAX_TICKS);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), schematic(), new Vec3i(origin.x, origin.y, origin.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int placed = countPlaced(arena);
        this.bestPlaced = Math.max(this.bestPlaced, placed);
        if (placed == TOTAL_BLOCKS) {
            if (elapsedTicks > MAX_TICKS) {
                return Verdict.fail("large build completed after the %d tick bound: %d ticks", MAX_TICKS, elapsedTicks);
            }
            return Verdict.pass(String.format("placed all %d blocks in %d ticks (%.1fs)",
                    TOTAL_BLOCKS, elapsedTicks, elapsedTicks / 20.0));
        }
        if (!arena.baritone().getBuilderProcess().isActive()) {
            if (this.builderInactiveSince < 0) {
                this.builderInactiveSince = elapsedTicks;
            } else if (elapsedTicks - this.builderInactiveSince > 40) {
                return Verdict.fail("builder stopped at %d of %d blocks before the performance bound",
                        placed, TOTAL_BLOCKS);
            }
        } else {
            this.builderInactiveSince = -1;
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("large build timed out at %d of %d blocks; best=%d, bound=%d ticks",
                    placed, TOTAL_BLOCKS, this.bestPlaced, MAX_TICKS);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("%d of %d blocks placed at best; builder active=%b; tick bound=%d",
                this.bestPlaced, TOTAL_BLOCKS, arena.baritone().getBuilderProcess().isActive(), MAX_TICKS);
    }

    private int countPlaced(TestArena arena) {
        int placed = 0;
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (arena.stateAt(BUILD_X + x, y, BUILD_Z + z).is(Blocks.WHITE_CONCRETE)) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[WIDTH][DEPTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    states[x][z][y] = Blocks.WHITE_CONCRETE.defaultBlockState();
                }
            }
        }
        return new StaticSchematic(states);
    }
}
