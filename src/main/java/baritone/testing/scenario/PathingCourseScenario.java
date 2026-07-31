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

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Walks an obstacle course end to end, with breaking and placing switched off.
 * <p>
 * Denying the pathfinder its tools is the whole point. With {@code allowBreak} on, a wall is not an
 * obstacle -- it is a short delay -- and the run stops measuring navigation and starts measuring
 * mining speed. Everything here is instead solvable by walking: the walls have gaps, the trench has
 * sloped sides, the pool can be swum. If Baritone cannot finish this course it is because it failed
 * to find a route that exists.
 */
public final class PathingCourseScenario extends TestScenario {

    /**
     * The goal platform. {@code GOAL_X} is the first block of it, and the staircase leading up
     * occupies the four columns before it, one block per level -- so the goal's height and the
     * number of steps are the same number, and changing one changes the other.
     */
    private static final int GOAL_X = 44;
    private static final int GOAL_Y = 4;

    private GoalBlock goal;
    private BetterBlockPos goalPos;
    private int lastProgressNote;
    private int bestDistance = Integer.MAX_VALUE;
    private int ticksWithoutProgress;

    @Override
    public String name() {
        return "pathing-course";
    }

    @Override
    public String description() {
        return "Navigate walls, a trench and water to a raised platform, without breaking or placing";
    }

    @Override
    public int tickBudget() {
        return 20 * 180;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", false);
        settings.put("allowPlace", false);
        settings.put("allowParkour", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        // Clear the airspace and lay a floor. The floor is a slab six deep rather than one layer,
        // because the arena origin may sit above the natural ground: a single layer would have air
        // under it, the pool below would drain through, and the trench would open into a void.
        arena.fill(-4, 0, -12, 48, 16, 12, "minecraft:air");
        arena.fill(-4, -6, -12, 48, -1, 12, "minecraft:stone");

        // two staggered walls: each has a gap, on opposite sides, so the route has to zigzag
        arena.fill(10, 0, -12, 10, 4, 4, "minecraft:stone");
        arena.fill(18, 0, -4, 18, 4, 12, "minecraft:stone");

        // a trench sloping down and back up, four deep at the bottom
        arena.fill(26, -1, -12, 30, -1, 12, "minecraft:air");
        arena.fill(26, -2, -12, 26, -2, 12, "minecraft:stone");
        arena.fill(27, -3, -12, 27, -3, 12, "minecraft:stone");
        arena.fill(28, -4, -12, 28, -4, 12, "minecraft:stone");
        arena.fill(29, -3, -12, 29, -3, 12, "minecraft:stone");
        arena.fill(30, -2, -12, 30, -2, 12, "minecraft:stone");
        arena.fill(27, -2, -12, 27, -2, 12, "minecraft:air");
        arena.fill(28, -3, -12, 28, -2, 12, "minecraft:air");
        arena.fill(29, -2, -12, 29, -2, 12, "minecraft:air");

        // a pool to swim
        arena.fill(34, -1, -6, 38, -1, 6, "minecraft:water");

        // A staircase up to the goal platform. The platform is the last step's own level rather
        // than one above it: overlapping the two buries the top step, and what is left is a
        // two-block wall that cannot be climbed with placing disabled -- an unreachable goal, which
        // reads in the report as Baritone giving up.
        for (int step = 0; step < GOAL_Y; step++) {
            arena.fill(40 + step, step - 1, -2, 40 + step, step - 1, 2, "minecraft:stone");
        }
        arena.fill(GOAL_X, GOAL_Y - 1, -2, GOAL_X + 2, GOAL_Y - 1, 2, "minecraft:stone");

        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(10, 2, 0).is(Blocks.STONE)
                && arena.stateAt(28, -4, 0).is(Blocks.STONE)
                && arena.stateAt(GOAL_X, GOAL_Y - 1, 0).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        this.goalPos = arena.at(GOAL_X, GOAL_Y, 0);
        this.goal = new GoalBlock(this.goalPos);
        arena.note("goal is %s", this.goalPos);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public TestScenario.Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        if (this.goal.isInGoal(feet)) {
            return Verdict.pass(String.format("reached the goal in %d ticks (%.1fs)", elapsedTicks, elapsedTicks / 20.0));
        }

        int distance = (int) Math.sqrt(this.goalPos.distSqr(feet));
        if (distance < this.bestDistance) {
            this.bestDistance = distance;
            this.ticksWithoutProgress = 0;
        } else {
            this.ticksWithoutProgress++;
        }

        // Standing still with a goal set and nothing being calculated means it has given up, and
        // waiting out the rest of the budget only delays the same answer.
        if (this.ticksWithoutProgress > 20 * 20
                && !arena.baritone().getPathingBehavior().isPathing()
                && !arena.baritone().getPathingBehavior().getInProgress().isPresent()) {
            return Verdict.fail("gave up %d blocks from the goal, not pathing and not calculating", distance);
        }

        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %s, %d blocks out", elapsedTicks / 20, feet, distance);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format(
                "closest approach was %d blocks; ended at %s, pathing=%b",
                this.bestDistance,
                arena.ctx().playerFeet(),
                arena.baritone().getPathingBehavior().isPathing()
        );
    }
}
