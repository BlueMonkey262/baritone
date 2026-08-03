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
 * Chooses a dry detour over a geometrically shorter route that requires clearing cobwebs.
 */
public final class PathCobwebAvoidanceScenario extends TestScenario {

    private static final int WEB_FROM_X = 12;
    private static final int WEB_TO_X = 22;
    private static final int WEB_TO_Z = 6;
    private static final int DETOUR_Z = 7;
    private static final int GOAL_X = 30;

    private GoalBlock goal;
    private BetterBlockPos goalPos;
    private boolean sawDetour;
    private int lastProgressNote;
    private int bestDistance = Integer.MAX_VALUE;

    @Override
    public String name() {
        return "path-cobweb-avoidance";
    }

    @Override
    public String description() {
        return "Prefer a longer clear detour over breaking through an eleven-block cobweb field";
    }

    @Override
    public int tickBudget() {
        return 20 * 180;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        // Breaking stays available so the short web route is genuinely viable. The assertion holds
        // the cost model honest: it must choose the longer route rather than merely having no way
        // through the cobwebs.
        settings.put("allowBreak", true);
        settings.put("allowPlace", false);
        settings.put("allowParkour", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -4, -20, 36, -1, 12, "minecraft:stone");
        // Eleven webs are straight ahead at z=0. Going around their north end at z=7 adds about
        // sixteen walking blocks, deliberately close enough to hold the default clearing cost
        // honest: an underpriced clearing route wins even though it is geometrically shorter.
        arena.fill(WEB_FROM_X, 0, -20, WEB_TO_X, 1, WEB_TO_Z, "minecraft:cobweb");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(17, 0, 0).is(Blocks.COBWEB)
                && arena.stateAt(17, 1, 0).is(Blocks.COBWEB)
                && arena.stateAt(17, -1, DETOUR_Z).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        this.goalPos = arena.at(GOAL_X, 0, 0);
        this.goal = new GoalBlock(this.goalPos);
        arena.note("goal is %s; the short line breaks eleven cobweb columns, the clear route goes to z+7",
                this.goalPos);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        int relativeX = feet.x - arena.origin().x;
        int relativeZ = feet.z - arena.origin().z;
        if (relativeX >= WEB_FROM_X - 2 && relativeX <= WEB_TO_X + 2 && relativeZ >= DETOUR_Z) {
            this.sawDetour = true;
        }
        if (!websIntact(arena)) {
            return Verdict.fail(
                    "broke a cobweb on the geometrically shorter route instead of taking the clear detour");
        }
        int distance = (int) Math.sqrt(this.goalPos.distSqr(feet));
        this.bestDistance = Math.min(this.bestDistance, distance);
        if (this.goal.isInGoal(feet)) {
            return this.sawDetour
                    ? Verdict.pass(String.format(
                            "left all cobwebs intact and used the clear detour in %d ticks", elapsedTicks))
                    : Verdict.fail("reached the goal without entering the measured clear detour");
        }
        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %s (z+%d), %d blocks out, detour seen=%b", elapsedTicks / 20,
                    feet, relativeZ, distance, this.sawDetour);
        }
        return null;
    }

    private static boolean websIntact(TestArena arena) {
        for (int x = WEB_FROM_X; x <= WEB_TO_X; x++) {
            for (int z = -20; z <= WEB_TO_Z; z++) {
                if (!arena.stateAt(x, 0, z).is(Blocks.COBWEB) || !arena.stateAt(x, 1, z).is(Blocks.COBWEB)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("closest approach was %d blocks; clear detour seen=%b; webs intact=%b; ended at %s",
                this.bestDistance, this.sawDetour, websIntact(arena), arena.ctx().playerFeet());
    }
}
