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
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared machinery for the scenarios that ask the builder to put up the same small structure.
 * <p>
 * The structure is a hollow ring three blocks high -- 48 placements, no floor, no roof. Small
 * enough to finish inside a sane tick budget, and shaped so the builder has to move around it
 * rather than standing in one place, which is where the placement bugs live.
 * <p>
 * The verdict is a block-by-block comparison against the schematic. Not "did the builder say it was
 * done" -- the builder says that when it has run out of things it can do, which is exactly the
 * state a materials bug leaves it in.
 */
abstract class AbstractBoxBuildScenario extends TestScenario {

    /** Side length in x and z. */
    static final int SIDE = 5;
    /** Height in y. */
    static final int HEIGHT = 3;
    /** Ring perimeter times height: the number of blocks the builder must place. */
    static final int TOTAL_BLOCKS = (SIDE * SIDE - (SIDE - 2) * (SIDE - 2)) * HEIGHT;

    static final Block MATERIAL = Blocks.WHITE_CONCRETE;
    static final String MATERIAL_ID = "minecraft:white_concrete";

    /** Where the structure sits, relative to the arena origin. */
    private static final int BUILD_X = 6;
    private static final int BUILD_Z = -2;

    private int lastProgressNote;
    private int builderInactiveSince = -1;
    private int worstMismatch = TOTAL_BLOCKS;

    @Override
    public int tickBudget() {
        return 20 * 180;
    }

    /** Commands that give the builder something to build with. Runs after the arena is cleared. */
    protected abstract void stageSupplies(TestArena arena);

    /** Extra staging that must be visible on the client before the run starts. */
    protected boolean suppliesStaged(TestArena arena) {
        return true;
    }

    /** Called once, just before {@code build} is issued, for anything that is not a command. */
    protected void beforeBuild(TestArena arena) {}

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, 0, -12, 24, 12, 12, "minecraft:air");
        // Three deep, for the same reason the pathing course's floor is six: the origin can sit
        // above the natural ground, and a one-layer floor would be standing on nothing.
        arena.fill(-4, -3, -12, 24, -1, 12, "minecraft:stone");
        arena.command("clear @s");
        stageSupplies(arena);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 0, BUILD_Z).isAir()
                && suppliesStaged(arena);
    }

    @Override
    public void start(TestArena arena) {
        beforeBuild(arena);
        BetterBlockPos origin = arena.at(BUILD_X, 0, BUILD_Z);
        arena.note("building %d blocks of %s at %s", TOTAL_BLOCKS, MATERIAL_ID, origin);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(),
                schematic(),
                new Vec3i(origin.x, origin.y, origin.z)
        );
    }

    @Override
    public TestScenario.Verdict poll(TestArena arena, int elapsedTicks) {
        int missing = countMissing(arena);
        this.worstMismatch = Math.min(this.worstMismatch, missing);
        if (missing == 0) {
            return Verdict.pass(String.format(
                    "all %d blocks placed in %d ticks (%.1fs)",
                    TOTAL_BLOCKS, elapsedTicks, elapsedTicks / 20.0
            ));
        }

        // The builder going inactive is its way of saying it has nothing left it can do. Give the
        // last few placements time to arrive from the server before believing the count, then stop
        // -- waiting out the budget from here would only turn a clear failure into a vague one.
        if (!arena.baritone().getBuilderProcess().isActive()) {
            if (this.builderInactiveSince < 0) {
                this.builderInactiveSince = elapsedTicks;
            } else if (elapsedTicks - this.builderInactiveSince > 40) {
                return Verdict.fail(
                        "builder stopped with %d of %d blocks missing",
                        missing, TOTAL_BLOCKS
                );
            }
        } else {
            this.builderInactiveSince = -1;
        }

        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %d of %d placed, in control: %s",
                    elapsedTicks / 20,
                    TOTAL_BLOCKS - missing,
                    TOTAL_BLOCKS,
                    inControl(arena));
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format(
                "%d of %d blocks placed at best; builder active=%b, in control: %s",
                TOTAL_BLOCKS - this.worstMismatch,
                TOTAL_BLOCKS,
                arena.baritone().getBuilderProcess().isActive(),
                inControl(arena)
        );
    }

    static String inControl(TestArena arena) {
        return arena.baritone().getPathingControlManager()
                .mostRecentInControl()
                .map(process -> process.displayName())
                .orElse("nothing");
    }

    /** The positions the schematic wants filled, in world coordinates. */
    List<BetterBlockPos> targets(TestArena arena) {
        List<BetterBlockPos> targets = new ArrayList<>(TOTAL_BLOCKS);
        for (int x = 0; x < SIDE; x++) {
            for (int z = 0; z < SIDE; z++) {
                if (x != 0 && x != SIDE - 1 && z != 0 && z != SIDE - 1) {
                    continue;
                }
                for (int y = 0; y < HEIGHT; y++) {
                    targets.add(arena.at(BUILD_X + x, y, BUILD_Z + z));
                }
            }
        }
        return targets;
    }

    int countMissing(TestArena arena) {
        int missing = 0;
        for (BetterBlockPos pos : targets(arena)) {
            if (!arena.ctx().world().getBlockState(pos).is(MATERIAL)) {
                missing++;
            }
        }
        return missing;
    }

    static StaticSchematic schematic() {
        BlockState material = MATERIAL.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[][][] states = new BlockState[SIDE][SIDE][HEIGHT];
        for (int x = 0; x < SIDE; x++) {
            for (int z = 0; z < SIDE; z++) {
                boolean wall = x == 0 || x == SIDE - 1 || z == 0 || z == SIDE - 1;
                for (int y = 0; y < HEIGHT; y++) {
                    states[x][z][y] = wall ? material : air;
                }
            }
        }
        return new StaticSchematic(states);
    }
}
