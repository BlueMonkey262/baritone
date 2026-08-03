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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;

/** Item saver must not fall back to slot zero when every hotbar tool is spent. */
public final class ItemSaverAllHotbarSpentScenario extends AbstractItemSaverScenario {

    @Override
    public String name() {
        return "itemsaver-all-hotbar-spent";
    }

    @Override
    public String description() {
        return "Stop a stone mine without spending any of nine spent hotbar pickaxes";
    }

    @Override
    public int tickBudget() {
        return 20 * 15;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = miningSettings(true);
        settings.put("autoTool", true);
        settings.put("allowInventory", false);
        settings.put("restockFromBoxes", false);
        return settings;
    }

    @Override
    protected void stageEquipment(TestArena arena) {
        for (int slot = 0; slot < 9; slot++) {
            arena.command("item replace entity @s hotbar." + slot + " with " + SPENT_PICKAXE);
        }
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        if (countWallBlocks(arena) != WALL_BLOCKS
                || !arena.stateAt(0, -1, 0).is(Blocks.OBSIDIAN)
                || ScenarioInventory.countPlayer(arena, Items.WOODEN_PICKAXE) != 9) {
            return false;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = arena.ctx().player().getInventory().getItem(slot);
            if (!isSpentPickaxe(stack)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start(TestArena arena) {
        startMining(arena);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int remaining = countWallBlocks(arena);
        if (remaining < WALL_BLOCKS || !allHotbarPickaxesUnchanged(arena)) {
            return Verdict.fail("itemSaver spent a protected hotbar tool or changed the target: wall=%d/%d",
                    remaining, WALL_BLOCKS);
        }
        if (elapsedTicks >= 60 && !mineActive(arena)) {
            return Verdict.pass("the mine stopped with all nine spent hotbar pickaxes and the wall unchanged");
        }
        if (elapsedTicks >= 20 * 10) {
            return Verdict.fail("the all-spent mine remained active for %d ticks without a safe tool",
                    elapsedTicks);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "wall=" + countWallBlocks(arena) + "/" + WALL_BLOCKS
                + ", all hotbar pickaxes unchanged=" + allHotbarPickaxesUnchanged(arena)
                + ", mineActive=" + mineActive(arena);
    }

    private static boolean allHotbarPickaxesUnchanged(TestArena arena) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = arena.ctx().player().getInventory().getItem(slot);
            if (!isSpentPickaxe(stack) || stack.getDamageValue() != PICKAXE_DAMAGE) {
                return false;
            }
        }
        return true;
    }
}
