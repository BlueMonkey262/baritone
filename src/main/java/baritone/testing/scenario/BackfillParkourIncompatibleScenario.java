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

/** Backfill is incompatible with parkour and must not fill the jump's hole. */
public final class BackfillParkourIncompatibleScenario extends TestScenario {

    private static final int GAP_X = 1;
    private static final int GOAL_X = 2;
    private GoalBlock goal;
    private boolean sawGapCrossing;

    @Override
    public String name() {
        return "backfill-parkour-incompatible";
    }

    @Override
    public String description() {
        return "Cross a parkour gap with backfill enabled while leaving the incompatible hole untouched";
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
        settings.put("backfill", true);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-2, -4, -2, 6, -1, 2, "minecraft:stone");
        arena.fill(GAP_X, -1, -2, GAP_X, -1, 2, "minecraft:air");
        arena.fill(-2, 0, -2, -2, 3, 2, "minecraft:stone");
        arena.fill(-1, 0, -2, 3, 3, -2, "minecraft:stone");
        arena.fill(-1, 0, 2, 3, 3, 2, "minecraft:stone");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(GAP_X, -1, 0).isAir()
                && arena.stateAt(GOAL_X, -1, 0).is(Blocks.STONE)
                && arena.stateAt(0, 1, -2).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos goalPos = arena.at(GOAL_X, 0, 0);
        this.goal = new GoalBlock(goalPos);
        arena.note("parkour goal=%s; backfill=true is incompatible with the sealed gap at x+%d",
                goalPos, GAP_X);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        this.sawGapCrossing |= feet.x - arena.origin().x >= GAP_X && feet.x - arena.origin().x <= GOAL_X;
        if (!arena.stateAt(GAP_X, -1, 0).isAir()) {
            return Verdict.fail("backfill filled the parkour gap with " + arena.stateAt(GAP_X, -1, 0));
        }
        if (this.goal.isInGoal(feet)) {
            arena.note("backfill/parkour evidence at t=%d: goal=%s, gap=%s, crossed=%b, player=%s",
                    elapsedTicks, feet, arena.stateAt(GAP_X, -1, 0), this.sawGapCrossing, feet);
            return this.sawGapCrossing
                    ? Verdict.pass("parkour crossed the gap while the incompatible backfill remained inactive")
                    : Verdict.fail("reached the goal without crossing the measured gap");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("goal=%s, gap=%s, crossed=%b, player=%s, pathing=%b",
                this.goal != null && this.goal.isInGoal(arena.ctx().playerFeet()),
                arena.stateAt(GAP_X, -1, 0), this.sawGapCrossing, arena.ctx().playerFeet(),
                arena.baritone().getPathingBehavior().isPathing());
    }
}
