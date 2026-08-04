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

/** The movement-pillar break-above branch must use the spent-tool guard. */
public final class ItemSaverPillarBreakScenario extends TestScenario {

    private static final int OVERHEAD_Y = 2;
    private static final int PICKAXE_DAMAGE = 50;

    @Override
    public String name() {
        return "itemsaver-pillar-break";
    }

    @Override
    public String description() {
        return "Stop a one-block pillar before breaking the overhead stone with a spent pickaxe";
    }

    @Override
    public int tickBudget() {
        return 20 * 30;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("allowInventory", false);
        settings.put("itemSaver", true);
        settings.put("autoTool", false);
        settings.put("allowParkour", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-3, -3, -3, 5, -1, 3, "minecraft:stone");
        arena.fill(-3, 0, -3, 5, 4, 3, "minecraft:air");
        arena.setBlock(0, OVERHEAD_Y, 0, "minecraft:stone");
        arena.command("clear @s");
        arena.command("give @s minecraft:wooden_pickaxe[damage=50] 1");
        arena.command("give @s minecraft:cobblestone 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        ItemStack pickaxe = arena.ctx().player().getInventory().getItem(0);
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(0, OVERHEAD_Y, 0).is(Blocks.STONE)
                && pickaxe.is(Items.WOODEN_PICKAXE)
                && pickaxe.getDamageValue() == PICKAXE_DAMAGE
                && ScenarioInventory.countPlayer(arena, Items.COBBLESTONE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("pillar goal=%s; overhead stone y+%d; wooden pickaxe damage=%d/%d",
                arena.at(0, 1, 0), OVERHEAD_Y, PICKAXE_DAMAGE, 59);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(arena.at(0, 1, 0)));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        ItemStack pickaxe = arena.ctx().player().getInventory().getItem(0);
        boolean overheadIntact = arena.stateAt(0, OVERHEAD_Y, 0).is(Blocks.STONE);
        if (!overheadIntact || !pickaxe.is(Items.WOODEN_PICKAXE) || pickaxe.getDamageValue() != PICKAXE_DAMAGE) {
            return Verdict.fail("pillar movement changed the overhead block or spent tool: overhead=%s, pickaxe=%s/%d",
                    arena.stateAt(0, OVERHEAD_Y, 0).getBlock(), pickaxe.getItem(), pickaxe.getDamageValue());
        }
        if (elapsedTicks >= 20 * 20 && !arena.baritone().getCustomGoalProcess().isActive()) {
            arena.note("pillar evidence at t=%d: overhead=%s, pickaxe damage=%d, player=%s, goalActive=%b",
                    elapsedTicks, arena.stateAt(0, OVERHEAD_Y, 0), pickaxe.getDamageValue(),
                    arena.ctx().playerFeet(), arena.baritone().getCustomGoalProcess().isActive());
            return Verdict.pass("the pillar branch stopped with the overhead stone and spent pickaxe unchanged");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        ItemStack pickaxe = arena.ctx().player().getInventory().getItem(0);
        return String.format("overhead=%s, pickaxe=%s/%d, cobblestone=%d, player=%s, goalActive=%b",
                arena.stateAt(0, OVERHEAD_Y, 0).getBlock(), pickaxe.getItem(), pickaxe.getDamageValue(),
                ScenarioInventory.countPlayer(arena, Items.COBBLESTONE), arena.ctx().playerFeet(),
                arena.baritone().getCustomGoalProcess().isActive());
    }
}
