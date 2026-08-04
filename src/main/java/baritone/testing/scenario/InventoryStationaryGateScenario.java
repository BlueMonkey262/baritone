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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/** Inventory arbitration must defer a main-inventory tool swap until the miner is stationary. */
public final class InventoryStationaryGateScenario extends AbstractMiningScenario {

    private static final int TARGET_X = 8;
    private boolean sawEarlySwap;

    @Override
    public String name() {
        return "inventory-stationary-gate";
    }

    @Override
    public String description() {
        return "Delay the main-inventory pickaxe swap until the mining path reaches its target";
    }

    @Override
    public int tickBudget() {
        return 20 * 90;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>(miningSettings());
        settings.put("allowInventory", true);
        settings.put("autoTool", true);
        settings.put("inventoryMoveOnlyIfStationary", true);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.setBlock(TARGET_X, 0, 0, "minecraft:stone");
        for (int slot = 0; slot < 9; slot++) {
            arena.command("item replace entity @s hotbar." + slot + " with minecraft:dirt 1");
        }
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
        arena.note("target=%s; all nine hotbar slots hold dirt; diamond pickaxe starts in main inventory",
                arena.at(TARGET_X, 0, 0));
        arena.baritone().getMineProcess().mine(Blocks.STONE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean pickInHotbar = !ScenarioInventory.firstHotbar(arena, Items.DIAMOND_PICKAXE, stack -> true).isEmpty();
        int distance = (int) Math.sqrt(arena.ctx().playerFeet().distSqr(arena.at(TARGET_X, 0, 0)));
        this.sawEarlySwap |= pickInHotbar && distance > 3;
        if (this.sawEarlySwap) {
            return Verdict.fail("the main-inventory pickaxe reached the hotbar %d blocks from the target while movement was active",
                    distance);
        }
        if (!arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE)) {
            int damage = itemDamage(arena, Items.DIAMOND_PICKAXE);
            arena.note("stationary-gate evidence at t=%d: targetGone=true, pickaxeInHotbar=%b, diamondDamage=%d, player=%s",
                    elapsedTicks, pickInHotbar, damage, arena.ctx().playerFeet());
            return pickInHotbar && damage > 0
                    ? Verdict.pass("delayed the tool swap until the miner reached the stationary target, then mined it")
                    : Verdict.fail("the target broke without a hotbar pickaxe with positive damage");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        ItemStack hotbar = ScenarioInventory.firstHotbar(arena, Items.DIAMOND_PICKAXE, stack -> true);
        return String.format("target=%s, pickaxeInHotbar=%b, damage=%d, earlySwap=%b, player=%s, mineActive=%b",
                arena.stateAt(TARGET_X, 0, 0).getBlock(), !hotbar.isEmpty(), itemDamage(arena, Items.DIAMOND_PICKAXE),
                this.sawEarlySwap, arena.ctx().playerFeet(), mineActive(arena));
    }
}
