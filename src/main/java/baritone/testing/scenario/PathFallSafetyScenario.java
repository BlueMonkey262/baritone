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
 * Rejects a shorter four-block damaging fall in favour of a longer one-block staircase.
 */
public final class PathFallSafetyScenario extends TestScenario {

    private static final int GOAL_X = 28;

    private GoalBlock goal;
    private BetterBlockPos goalPos;
    private int safeStairMask;
    private boolean landedInUnsafeBasin;
    private int lastProgressNote;
    private int bestDistance = Integer.MAX_VALUE;

    @Override
    public String name() {
        return "path-fall-safety";
    }

    @Override
    public String description() {
        return "Prefer a longer staircase over the shorter fall that exceeds the safe-fall default";
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
        arena.fill(-4, -4, -8, 34, -1, 12, "minecraft:stone");

        // The straight route reaches the east edge at x+11, then drops four blocks to the base:
        // shorter in distance, but above maxFallHeightNoWater's shipped value of three. This close
        // comparison holds that default honest: the stair route leaves north at x+4 and takes four
        // individually safe descents before heading east.
        arena.fill(-4, 3, -3, 11, 3, 3, "minecraft:stone");
        arena.setBlock(4, 2, 4, "minecraft:stone");
        arena.setBlock(4, 1, 5, "minecraft:stone");
        arena.setBlock(4, 0, 6, "minecraft:stone");
        // The final stair is the already-present base floor at y=-1, z+7.
        arena.teleport(0, 4, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, 3, 0).is(Blocks.STONE)
                && arena.stateAt(4, 2, 4).is(Blocks.STONE)
                && arena.stateAt(4, 1, 5).is(Blocks.STONE)
                && arena.stateAt(4, 0, 6).is(Blocks.STONE)
                && arena.stateAt(12, -1, 0).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        this.goalPos = arena.at(GOAL_X, 0, 0);
        this.goal = new GoalBlock(this.goalPos);
        arena.note("goal is %s; x+11 is a shorter damaging fall, while the safe route descends at z+4 through z+7",
                this.goalPos);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        int x = feet.x - arena.origin().x;
        int y = feet.y - arena.origin().y;
        int z = feet.z - arena.origin().z;
        if (x == 4 && z == 4 && y == 3) {
            this.safeStairMask |= 1;
        } else if (x == 4 && z == 5 && y == 2) {
            this.safeStairMask |= 2;
        } else if (x == 4 && z == 6 && y == 1) {
            this.safeStairMask |= 4;
        } else if (x == 4 && z == 7 && y == 0) {
            this.safeStairMask |= 8;
        }
        if (y == 0 && x >= 10 && x <= 14 && z >= -2 && z <= 2) {
            this.landedInUnsafeBasin = true;
            return Verdict.fail("took the shorter damaging fall, landing in its basin at x+%d z+%d",
                    x, z);
        }
        int distance = (int) Math.sqrt(this.goalPos.distSqr(feet));
        this.bestDistance = Math.min(this.bestDistance, distance);
        if (this.goal.isInGoal(feet)) {
            return this.safeStairMask == 15
                    ? Verdict.pass(String.format(
                            "used the safe staircase and reached the goal in %d ticks", elapsedTicks))
                    : Verdict.fail(
                            "reached the goal without traversing every level of the measured safe staircase (mask %d)",
                            this.safeStairMask);
        }
        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %s (x+%d y+%d z+%d), %d blocks out, stair mask=%d",
                    elapsedTicks / 20, feet, x, y, z, distance, this.safeStairMask);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("closest approach was %d blocks; stair mask=%d; unsafe basin=%b; ended at %s",
                this.bestDistance, this.safeStairMask, this.landedInUnsafeBasin, arena.ctx().playerFeet());
    }
}
