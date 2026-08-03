/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testing.scenario;

import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Small world/inventory readers shared by container scenarios; none inspect chat or logs. */
final class ScenarioInventory {

    private ScenarioInventory() {}

    /**
     * The authoritative contents of a container, read from the integrated server.
     * <p>
     * <b>Not the client copy, and this is the whole point.</b> Minecraft does not send container
     * contents to clients -- that is what stops a client seeing inside a chest it has not opened --
     * so the client-side block entity is permanently empty and
     * {@code ctx().world().getBlockEntity(...)} reports zero of everything no matter what is really
     * in the box.
     * <p>
     * That silently broke every container assertion in the suite. Scenarios asserting a box
     * <i>gained</i> items failed against a bot that had done the job correctly; worse,
     * {@code restock-two-materials} asserts a box holds <i>fewer</i> items than staged, so a constant
     * zero made it pass without testing anything. Reading real deposits requires the server's copy.
     * <p>
     * Reading server state from the client thread is a deliberate exception to the rule that the
     * harness acts only through real packets. That rule exists so arenas and actions stay on paths a
     * player could take; it governs what the harness <i>does</i>, not what it is allowed to
     * <i>observe</i> when deciding a verdict. An assertion has to be able to see the truth.
     *
     * @return the container, or null if there is no integrated server or no container there
     */
    private static Container container(TestArena arena, int x, int y, int z) {
        BetterBlockPos pos = arena.at(x, y, z);
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null && arena.ctx().world() != null) {
            ServerLevel level = server.getLevel(arena.ctx().world().dimension());
            if (level != null && level.getBlockEntity(pos) instanceof Container container) {
                return container;
            }
        }
        // No integrated server means this is not a singleplayer harness run, which every scenario
        // already refuses to start in. Falling back keeps the readers total rather than throwing.
        return arena.ctx().world().getBlockEntity(pos) instanceof Container container ? container : null;
    }

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
        Container container = container(arena, x, y, z);
        if (container == null) {
            return -1;
        }
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
