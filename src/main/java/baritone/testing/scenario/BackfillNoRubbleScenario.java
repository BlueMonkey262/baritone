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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/** Backfill must defer when the configured rubble list has no matching inventory item. */
public final class BackfillNoRubbleScenario extends TestScenario {

    private static final int WALL_X = 5;
    private static final int GOAL_X = 12;
    private static final int WALL_BLOCKS = 6;
    private GoalBlock goal;
    private boolean sawWallBreak;
    private boolean notedWallOpen;

    @Override
    public String name() {
        return "backfill-no-rubble-no-placement";
    }

    @Override
    public String description() {
        return "Leave a pathing hole open when no configured backfill rubble is carried";
    }

    @Override
    public int tickBudget() {
        return 20 * 120;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", true);
        settings.put("allowPlace", false);
        settings.put("allowParkour", false);
        settings.put("backfill", true);
        settings.put("restockFromBoxes", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-2, -3, -2, 16, -1, 2, "minecraft:stone");
        arena.fill(-2, 0, -2, 16, 3, -2, "minecraft:stone");
        arena.fill(-2, 0, 2, 16, 3, 2, "minecraft:stone");
        arena.fill(WALL_X, 0, -1, WALL_X, 1, 1, "minecraft:stone");
        arena.command("clear @s");
        arena.command("give @s minecraft:iron_pickaxe 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && countWall(arena) == WALL_BLOCKS
                && arena.stateAt(WALL_X, 2, 0).isAir()
                && ScenarioInventory.countPlayer(arena, Items.IRON_PICKAXE) == 1
                && ScenarioInventory.countPlayer(arena, Items.COBBLESTONE) == 0
                && ScenarioInventory.countPlayer(arena, Items.DIRT) == 0;
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos goalPos = arena.at(GOAL_X, 0, 0);
        this.goal = new GoalBlock(goalPos);
        arena.note("sealed corridor goal=%s; wall=%d stone blocks; no configured rubble in inventory", goalPos,
                WALL_BLOCKS);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int wall = countWall(arena);
        if (wall < WALL_BLOCKS) {
            this.sawWallBreak = true;
        }
        if (wall > 0 && wall < WALL_BLOCKS && !this.notedWallOpen) {
            // The wall can be broken in more than one order; this branch simply keeps the
            // observation explicit in the report rather than treating the first air block as a
            // silent fixture detail.
            this.notedWallOpen = true;
            arena.note("path opened the wall at t=%d ticks: %d/%d stone remains", elapsedTicks, wall, WALL_BLOCKS);
        }
        if (this.sawWallBreak && this.goal.isInGoal(arena.ctx().playerFeet())) {
            if (wall == WALL_BLOCKS) {
                return Verdict.fail("backfill restored the pathing wall despite no rubble being carried");
            }
            arena.note("observed after %d ticks: goal=%s, wall=%d/%d stone, cobblestone=%d, dirt=%d, player=%s",
                    elapsedTicks, this.goal.isInGoal(arena.ctx().playerFeet()), wall, WALL_BLOCKS,
                    ScenarioInventory.countPlayer(arena, Items.COBBLESTONE),
                    ScenarioInventory.countPlayer(arena, Items.DIRT), arena.ctx().playerFeet());
            return Verdict.pass("completed the path while leaving the no-rubble hole unfilled");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("goal=%b, wall=%d/%d stone, sawWallBreak=%b, player=%s",
                this.goal != null && this.goal.isInGoal(arena.ctx().playerFeet()), countWall(arena),
                WALL_BLOCKS, this.sawWallBreak, arena.ctx().playerFeet());
    }

    private static int countWall(TestArena arena) {
        int count = 0;
        for (int y = 0; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                if (arena.stateAt(WALL_X, y, z).is(Blocks.STONE)) {
                    count++;
                }
            }
        }
        return count;
    }
}
