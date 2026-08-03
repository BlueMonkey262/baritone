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
 * Leaves an enclosed upper deck through its only exit, an eight-block ladder.
 */
public final class PathLadderDescentScenario extends TestScenario {

    private GoalBlock goal;
    private BetterBlockPos goalPos;
    private int ladderTicks;
    private int lastProgressNote;
    private int bestDistance = Integer.MAX_VALUE;

    @Override
    public String name() {
        return "path-ladder-descent";
    }

    @Override
    public String description() {
        return "Descend the only exit from an enclosed upper deck";
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
        arena.fill(-4, -4, -6, 20, -1, 6, "minecraft:stone");
        arena.fill(-2, 7, -2, 6, 7, 2, "minecraft:stone");
        arena.fill(8, 0, -3, 8, 10, 3, "minecraft:stone");
        arena.fill(-2, 8, -3, -2, 10, 3, "minecraft:stone");
        arena.fill(-2, 8, -3, 8, 10, -3, "minecraft:stone");
        arena.fill(-2, 8, 3, 8, 10, 3, "minecraft:stone");
        arena.fill(-2, 11, -3, 8, 11, 3, "minecraft:stone");
        arena.fill(7, 0, 0, 7, 8, 0, "minecraft:ladder[facing=west]");

        arena.teleport(2, 8, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(2, 7, 0).is(Blocks.STONE)
                && arena.stateAt(7, 4, 0).is(Blocks.LADDER)
                && arena.stateAt(8, 4, 0).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        this.goalPos = arena.at(0, 0, 0);
        this.goal = new GoalBlock(this.goalPos);
        arena.note("goal is %s, below a deck whose only exit is the ladder at x+7", this.goalPos);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        if (onLadder(arena, feet)) {
            this.ladderTicks++;
        }
        int distance = (int) Math.sqrt(this.goalPos.distSqr(feet));
        this.bestDistance = Math.min(this.bestDistance, distance);
        if (this.goal.isInGoal(feet)) {
            return this.ladderTicks > 0
                    ? Verdict.pass(String.format(
                            "descended the ladder and reached ground level in %d ticks", elapsedTicks))
                    : Verdict.fail("reached the lower goal without ever occupying the deck's only ladder");
        }
        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %s, %d blocks out, %d ladder ticks",
                    elapsedTicks / 20, feet, distance, this.ladderTicks);
        }
        return null;
    }

    private static boolean onLadder(TestArena arena, BetterBlockPos feet) {
        int x = feet.x - arena.origin().x;
        int y = feet.y - arena.origin().y;
        int z = feet.z - arena.origin().z;
        return arena.stateAt(x, y, z).is(Blocks.LADDER) || arena.stateAt(x, y + 1, z).is(Blocks.LADDER);
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("closest approach was %d blocks; ended at %s after %d ladder ticks",
                this.bestDistance, arena.ctx().playerFeet(), this.ladderTicks);
    }
}
