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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla-command inventory fixtures shared by the crowded-inventory restock scenarios. */
final class RestockInventoryFixture {

    private static final String[] JUNK_IDS = {
            "minecraft:red_concrete", "minecraft:orange_concrete", "minecraft:yellow_concrete",
            "minecraft:lime_concrete", "minecraft:green_concrete", "minecraft:cyan_concrete",
            "minecraft:light_blue_concrete", "minecraft:blue_concrete", "minecraft:purple_concrete",
            "minecraft:magenta_concrete", "minecraft:pink_concrete", "minecraft:brown_concrete",
            "minecraft:black_concrete", "minecraft:gray_concrete", "minecraft:light_gray_concrete",
            "minecraft:red_wool", "minecraft:orange_wool", "minecraft:yellow_wool",
            "minecraft:lime_wool", "minecraft:green_wool", "minecraft:cyan_wool",
            "minecraft:light_blue_wool", "minecraft:blue_wool", "minecraft:purple_wool",
            "minecraft:magenta_wool", "minecraft:pink_wool", "minecraft:brown_wool",
            "minecraft:black_wool", "minecraft:gray_wool", "minecraft:light_gray_wool",
            "minecraft:white_wool", "minecraft:terracotta", "minecraft:red_terracotta",
            "minecraft:orange_terracotta"
    };

    private RestockInventoryFixture() {}

    /** Fill three protected slots and then the requested number of block-item junk slots. */
    static void stage(TestArena arena, int junkSlots) {
        arena.command("item replace entity @s inventory.0 with minecraft:iron_pickaxe[damage=1]");
        arena.command("item replace entity @s inventory.1 with minecraft:diamond_sword[damage=2]");
        arena.command("item replace entity @s inventory.2 with minecraft:golden_apple");
        arena.command("item replace entity @s armor.head with minecraft:diamond_helmet");
        for (int i = 0; i < junkSlots; i++) {
            arena.command("item replace entity @s inventory." + (i + 3) + " with " + JUNK_IDS[i]);
        }
    }

    static boolean staged(TestArena arena, int junkSlots) {
        return occupied(arena) == junkSlots + 3
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == 0
                && protectedItemsIntact(arena)
                && exactJunkSlots(arena, junkSlots);
    }

    static int occupied(TestArena arena) {
        int occupied = 0;
        for (ItemStack stack : arena.ctx().player().getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    static boolean protectedItemsIntact(TestArena arena) {
        ItemStack pickaxe = firstPlayer(arena, Items.IRON_PICKAXE);
        ItemStack sword = firstPlayer(arena, Items.DIAMOND_SWORD);
        return ScenarioInventory.countPlayer(arena, Items.IRON_PICKAXE) == 1
                && ScenarioInventory.countPlayer(arena, Items.DIAMOND_SWORD) == 1
                && ScenarioInventory.countPlayer(arena, Items.GOLDEN_APPLE) == 1
                && !pickaxe.isEmpty()
                && pickaxe.getDamageValue() == 1
                && !sword.isEmpty()
                && sword.getDamageValue() == 2
                && arena.ctx().player().getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET);
    }

    private static ItemStack firstPlayer(TestArena arena, Item item) {
        for (ItemStack stack : arena.ctx().player().getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean exactJunkSlots(TestArena arena, int junkSlots) {
        for (int i = 0; i < junkSlots; i++) {
            ItemStack stack = arena.ctx().player().getInventory().getNonEquipmentItems().get(i + 3);
            if (stack.isEmpty() || stack.getCount() != 1
                    || !JUNK_IDS[i].equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                return false;
            }
        }
        return true;
    }
}
