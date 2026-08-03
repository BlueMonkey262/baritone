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
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/** Movement-traverse obstruction handling must use the central spent-tool guard. */
public final class ItemSaverTraverseObstacleScenario extends TestScenario {

    private static final int OBSTACLE_X = 3;
    private static final int GOAL_X = 8;
    private static final int PICKAXE_DAMAGE = 50;

    @Override
    public String name() {
        return "itemsaver-traverse-obstacle";
    }

    @Override
    public String description() {
        return "Traverse around a one-block obstruction without swinging a spent held pickaxe";
    }

    @Override
    public int tickBudget() {
        return 20 * 30;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", true);
        settings.put("allowPlace", false);
        settings.put("allowInventory", false);
        settings.put("itemSaver", true);
        settings.put("autoTool", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.setBlock(OBSTACLE_X, 0, 0, "minecraft:stone");
        arena.command("clear @s");
        arena.command("give @s minecraft:wooden_pickaxe[damage=50] 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        ItemStack pickaxe = arena.ctx().player().getInventory().getItem(0);
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(OBSTACLE_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(GOAL_X, 0, 0).isAir()
                && pickaxe.is(Items.WOODEN_PICKAXE)
                && pickaxe.getDamageValue() == PICKAXE_DAMAGE;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("walking to %s past a protected stone obstruction at %s", arena.at(GOAL_X, 0, 0),
                arena.at(OBSTACLE_X, 0, 0));
        arena.baritone().getCustomGoalProcess().setGoalAndPath(
                new GoalBlock(arena.at(GOAL_X, 0, 0)));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!arena.stateAt(OBSTACLE_X, 0, 0).is(Blocks.STONE)) {
            return Verdict.fail("the traverse obstruction was broken with the spent pickaxe");
        }
        ItemStack pickaxe = arena.ctx().player().getInventory().getItem(0);
        if (!pickaxe.is(Items.WOODEN_PICKAXE) || pickaxe.getDamageValue() != PICKAXE_DAMAGE) {
            return Verdict.fail("the protected traverse tool changed to %s damage=%d",
                    pickaxe.getItem(), pickaxe.getDamageValue());
        }
        if (arena.ctx().playerFeet().equals(arena.at(GOAL_X, 0, 0))) {
            return Verdict.pass("the player reached the goal without breaking the obstruction or spending the protected tool");
        }
        if (elapsedTicks >= 20 * 20 && !arena.baritone().getCustomGoalProcess().isActive()) {
            return Verdict.fail("movement stopped before the goal while the protected obstruction remained; no detour completed");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("player=%s, goal=%s, obstacle=%s, pickaxeDamage=%d, goalActive=%b",
                arena.ctx().playerFeet(), arena.at(GOAL_X, 0, 0),
                arena.stateAt(OBSTACLE_X, 0, 0).getBlock().getName().getString(),
                arena.ctx().player().getInventory().getItem(0).getDamageValue(),
                arena.baritone().getCustomGoalProcess().isActive());
    }
}
