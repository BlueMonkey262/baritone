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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Small world/inventory readers shared by container scenarios; none inspect chat or logs. */
final class ScenarioInventory {

    private ScenarioInventory() {}

    /**
     * Counts something in a container, evaluated <b>on the server thread</b>.
     * <p>
     * Two separate problems make this the only way to read a container honestly, and both of them
     * silently returned zero rather than failing.
     * <p>
     * First, the client copy is always empty. Minecraft never sends container contents to clients --
     * that is what stops a client seeing inside a chest it has not opened -- so
     * {@code ctx().world().getBlockEntity(...)} reports zero of everything no matter what is in the
     * box.
     * <p>
     * Second, reaching for the server's copy from the client thread does not work either.
     * {@code ServerLevel#getBlockEntity} resolves through the server's chunk source, which is
     * confined to the server thread; called from the client it returns null even when the block is
     * demonstrably there. That was measured, not assumed: with a shulker box present in both worlds,
     * {@code serverBlock=shulker_box clientBlock=shulker_box be=null}.
     * <p>
     * So the whole read is submitted to the server thread and waited on. The wait is bounded, and a
     * timeout answers -1 rather than hanging a scenario.
     *
     * @return the count, or -1 if there is no integrated server, no container, or the read timed out
     */
    private static int countOnServer(TestArena arena, int x, int y, int z, ToIntFunction<Container> count) {
        BetterBlockPos pos = arena.at(x, y, z);
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || arena.ctx().world() == null) {
            return -1;
        }
        ResourceKey<Level> dimension = arena.ctx().world().dimension();
        try {
            return server.submit(() -> {
                ServerLevel level = server.getLevel(dimension);
                // CHECK, not the default IMMEDIATE. IMMEDIATE *creates* a block entity when the
                // chunk does not yet have one -- and then caches it -- so reading an arena the tick
                // after it was staged can mint an empty shulker box and permanently shadow the real
                // one. That is why this reader spent an evening reporting an empty box while the bot
                // pulled 63 concrete out of it.
                Object be = level == null ? null
                        : level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
                if (!(be instanceof Container container)) {
                    return -1;
                }
                return count.applyAsInt(container);
            }).get(SERVER_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (ExecutionException | TimeoutException e) {
            return -1;
        }
    }

    /**
     * How long to wait for the server thread to answer a container read. It answers within a tick in
     * practice; this only exists so a wedged server fails the scenario instead of hanging the client.
     */
    private static final long SERVER_READ_TIMEOUT_MS = 2000;




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
        return countOnServer(arena, x, y, z, container -> {
            int count = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.is(item)) {
                    count += stack.getCount();
                }
            }
            return count;
        });
    }

    static int countContainerMatching(TestArena arena, int x, int y, int z, Item item,
                                      Predicate<ItemStack> predicate) {
        return countOnServer(arena, x, y, z, container -> {
            int count = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.is(item) && predicate.test(stack)) {
                    count += stack.getCount();
                }
            }
            return count;
        });
    }

    static int countContainerSlots(TestArena arena, int x, int y, int z, Item item) {
        return countOnServer(arena, x, y, z, container -> {
            int count = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (container.getItem(slot).is(item)) {
                    count++;
                }
            }
            return count;
        });
    }

    static int countNonEmptyContainerSlots(TestArena arena, int x, int y, int z) {
        return countOnServer(arena, x, y, z, container -> {
            int count = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (!container.getItem(slot).isEmpty()) {
                    count++;
                }
            }
            return count;
        });
    }
}
