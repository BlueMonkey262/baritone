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

import baritone.testing.TestArena;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/** A valid mining tool in the main inventory must be moved into a usable hotbar slot. */
public final class InventoryMainToolSwapScenario extends AbstractMiningScenario {

    private static final int TARGET_X = 5;

    @Override
    public String name() {
        return "inventory-main-tool-swap";
    }

    @Override
    public String description() {
        return "Move a main-inventory diamond pickaxe to the hotbar before mining stone";
    }

    @Override
    public int tickBudget() {
        return 20 * 60;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>(miningSettings());
        settings.put("allowInventory", true);
        settings.put("autoTool", true);
        settings.put("inventoryMoveOnlyIfStationary", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.setBlock(TARGET_X, 0, 0, "minecraft:stone");
        for (int slot = 0; slot < 9; slot++) {
            arena.command("item replace entity @s hotbar." + slot + " with minecraft:dirt 1");
        }
        // With all hotbar slots occupied, /give places this tool in the main inventory.
        arena.command("give @s minecraft:diamond_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE)
                && countPlayer(arena, Items.DIRT) == 9
                && countPlayer(arena, Items.DIAMOND_PICKAXE) == 1
                && ScenarioInventory.firstHotbar(arena, Items.DIAMOND_PICKAXE, stack -> true).isEmpty();
    }

    @Override
    public void start(TestArena arena) {
        arena.note("staged stone target with diamond pickaxe outside hotbar: dirt slots=%d, hotbar pickaxe=%b",
                countPlayer(arena, Items.DIRT),
                !ScenarioInventory.firstHotbar(arena, Items.DIAMOND_PICKAXE, stack -> true).isEmpty());
        arena.baritone().getMineProcess().mine(Blocks.STONE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean targetGone = !arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE);
        boolean pickInHotbar = !ScenarioInventory.firstHotbar(arena, Items.DIAMOND_PICKAXE, stack -> true).isEmpty();
        int damage = itemDamage(arena, Items.DIAMOND_PICKAXE);
        if (targetGone && pickInHotbar && damage > 0) {
            arena.note("observed after %d ticks: targetGone=%b, pickaxeInHotbar=%b, diamondDamage=%d, dirt=%d, player=%s",
                    elapsedTicks, targetGone, pickInHotbar, damage, countPlayer(arena, Items.DIRT),
                    arena.ctx().playerFeet());
            return Verdict.pass("swapped the main-inventory pickaxe into the hotbar and mined the target");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("targetGone=%b, pickaxeInHotbar=%b, diamondDamage=%d, dirt=%d, mineActive=%b, player=%s",
                !arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE),
                !ScenarioInventory.firstHotbar(arena, Items.DIAMOND_PICKAXE, stack -> true).isEmpty(),
                itemDamage(arena, Items.DIAMOND_PICKAXE), countPlayer(arena, Items.DIRT),
                mineActive(arena), arena.ctx().playerFeet());
    }
}
