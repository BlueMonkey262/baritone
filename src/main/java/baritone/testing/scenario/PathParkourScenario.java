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

/** A sealed two-block gap must be crossed by the parkour movement. */
public final class PathParkourScenario extends TestScenario {

    private static final int GAP_X = 1;
    private static final int GOAL_X = 2;
    private GoalBlock goal;
    private int bestDistance = Integer.MAX_VALUE;

    @Override
    public String name() {
        return "path-parkour-on";
    }

    @Override
    public String description() {
        return "Cross a sealed two-block floor gap with allowParkour enabled";
    }

    @Override
    public int tickBudget() {
        return 20 * 120;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", false);
        settings.put("allowPlace", false);
        settings.put("allowParkour", true);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-2, -4, -2, 6, -1, 2, "minecraft:stone");
        arena.fill(GAP_X, -1, -2, GAP_X, -1, 2, "minecraft:air");
        arena.fill(-2, 0, -2, -2, 3, 2, "minecraft:stone");
        // Leave the first block beyond the goal open: MovementParkour checks its overshoot
        // position before accepting the jump. The side walls still make the gap the only route.
        arena.fill(-1, 0, -2, 3, 3, -2, "minecraft:stone");
        arena.fill(-1, 0, 2, 3, 3, 2, "minecraft:stone");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(GAP_X, -1, 0).isAir()
                && arena.stateAt(GOAL_X, -1, 0).is(Blocks.STONE)
                && arena.stateAt(0, 1, -1).is(Blocks.STONE)
                && arena.stateAt(GOAL_X, 1, 1).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos goalPos = arena.at(GOAL_X, 0, 0);
        this.goal = new GoalBlock(goalPos);
        arena.note("goal=%s; the only route is a two-block parkour jump over x+%d", goalPos, GAP_X);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        this.bestDistance = Math.min(this.bestDistance, (int) Math.sqrt(this.goal.getGoalPos().distSqr(feet)));
        if (this.goal.isInGoal(feet)) {
            if (!arena.stateAt(GAP_X, -1, 0).isAir()) {
                return Verdict.fail("the route completed only after the staged gap changed");
            }
            arena.note("observed after %d ticks: goal=%s, gap=%s, player=%s",
                    elapsedTicks, feet, arena.stateAt(GAP_X, -1, 0), feet);
            return Verdict.pass("crossed the sealed gap with parkour and left the terrain unchanged");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("closest=%d blocks, gap=%s, player=%s, pathing=%b",
                this.bestDistance, arena.stateAt(GAP_X, -1, 0), arena.ctx().playerFeet(),
                arena.baritone().getPathingBehavior().isPathing());
    }
}
