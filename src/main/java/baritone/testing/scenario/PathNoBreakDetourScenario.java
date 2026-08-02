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
 * The no-breaking half of the breakable-shortcut pair: it must use the long route around the wall.
 */
public final class PathNoBreakDetourScenario extends TestScenario {

    private static final int WALL_X = 12;
    private static final int DETOUR_Z = 7;
    private static final int GOAL_X = 30;

    private GoalBlock goal;
    private BetterBlockPos goalPos;
    private boolean sawDetour;
    private int lastProgressNote;
    private int bestDistance = Integer.MAX_VALUE;

    @Override
    public String name() {
        return "path-no-break-detour";
    }

    @Override
    public String description() {
        return "With breaking disabled, take the longer route around the otherwise direct stone shortcut";
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
        arena.fill(-4, -4, -20, 36, -1, 12, "minecraft:stone");
        // This is deliberately the same kind of short-vs-long comparison as the break-enabled
        // partner: breaking the wall at z=0 is geometrically shortest, while no breaking leaves
        // the north end at z=7 as the only grounded detour. The distances are deliberately close
        // enough to hold the no-break setting honest rather than merely proving that it can arrive.
        arena.fill(WALL_X, 0, -20, WALL_X, 2, 6, "minecraft:stone");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(WALL_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(WALL_X, 2, 0).is(Blocks.STONE)
                && arena.stateAt(WALL_X, -1, DETOUR_Z).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        this.goalPos = arena.at(GOAL_X, 0, 0);
        this.goal = new GoalBlock(this.goalPos);
        arena.note("goal is %s; breaking through x+12 is forbidden, so the route must pass north at z+7", this.goalPos);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        int x = feet.x - arena.origin().x;
        int z = feet.z - arena.origin().z;
        if (x >= WALL_X - 2 && x <= WALL_X + 2 && z >= DETOUR_Z) {
            this.sawDetour = true;
        }
        if (!arena.stateAt(WALL_X, 0, 0).is(Blocks.STONE)) {
            return Verdict.fail("broke the direct shortcut even though allowBreak is false");
        }
        int distance = (int) Math.sqrt(this.goalPos.distSqr(feet));
        this.bestDistance = Math.min(this.bestDistance, distance);
        if (this.goal.isInGoal(feet)) {
            return this.sawDetour
                    ? Verdict.pass(String.format(
                            "kept the shortcut intact and used the z+7 detour in %d ticks", elapsedTicks))
                    : Verdict.fail("reached the goal without taking the measured detour around the no-break wall");
        }
        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %s (x+%d z+%d), %d blocks out, detour seen=%b", elapsedTicks / 20,
                    feet, x, z, distance, this.sawDetour);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("closest approach was %d blocks; detour seen=%b; shortcut intact=%b; ended at %s",
                this.bestDistance, this.sawDetour, arena.stateAt(WALL_X, 0, 0).is(Blocks.STONE),
                arena.ctx().playerFeet());
    }
}
