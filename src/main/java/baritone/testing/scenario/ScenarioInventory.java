/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testing.scenario;

import baritone.testing.TestArena;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Small world/inventory readers shared by container scenarios; none inspect chat or logs. */
final class ScenarioInventory {

    private ScenarioInventory() {}

    static int countPlayer(TestArena arena, Item item) {
        return arena.ctx().player().getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    static int countPlayerMatching(TestArena arena, Item item, Predicate<ItemStack> predicate) {
        return arena.ctx().player().getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item))
                .filter(predicate)
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    static ItemStack firstPlayer(TestArena arena, Item item, Predicate<ItemStack> predicate) {
        return arena.ctx().player().getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item))
                .filter(predicate)
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    static ItemStack firstHotbar(TestArena arena, Item item, Predicate<ItemStack> predicate) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = arena.ctx().player().getInventory().getItem(slot);
            if (stack.is(item) && predicate.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    static int countContainer(TestArena arena, int x, int y, int z, Item item) {
        Object entity = arena.ctx().world().getBlockEntity(arena.at(x, y, z));
        if (!(entity instanceof Container)) {
            return -1;
        }
        Container container = (Container) entity;
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    static int countContainerMatching(TestArena arena, int x, int y, int z, Item item,
                                      Predicate<ItemStack> predicate) {
        Object entity = arena.ctx().world().getBlockEntity(arena.at(x, y, z));
        if (!(entity instanceof Container)) {
            return -1;
        }
        Container container = (Container) entity;
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item) && predicate.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
