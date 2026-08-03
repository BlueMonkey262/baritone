/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testing.scenario;

import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** An empty bulk allowlist must leave raw drops alone while an unload trip still makes room. */
public final class DepositBulkAllowlistScenario extends TestScenario {

    private static final int TARGET_X = 3;
    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int RAW_IRON_COUNT = 64;
    private static final int FLINT_COUNT = 64;

    /* These are deliberately not acceptable throwaway items, so a deposit trip has real work. */
    private static final Item[] JUNK_ITEMS = {
            Items.RED_CONCRETE, Items.ORANGE_CONCRETE, Items.YELLOW_CONCRETE, Items.LIME_CONCRETE,
            Items.GREEN_CONCRETE, Items.CYAN_CONCRETE, Items.LIGHT_BLUE_CONCRETE, Items.BLUE_CONCRETE,
            Items.PURPLE_CONCRETE, Items.MAGENTA_CONCRETE, Items.PINK_CONCRETE, Items.BROWN_CONCRETE,
            Items.BLACK_CONCRETE, Items.GRAY_CONCRETE, Items.LIGHT_GRAY_CONCRETE, Items.RED_WOOL,
            Items.ORANGE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL, Items.GREEN_WOOL, Items.CYAN_WOOL,
            Items.LIGHT_BLUE_WOOL, Items.BLUE_WOOL, Items.PURPLE_WOOL, Items.MAGENTA_WOOL, Items.PINK_WOOL,
            Items.BROWN_WOOL, Items.BLACK_WOOL, Items.GRAY_WOOL, Items.LIGHT_GRAY_WOOL, Items.WHITE_WOOL,
            Items.TERRACOTTA, Items.RED_TERRACOTTA
    };

    @Override
    public String name() {
        return "deposit-bulk-allowlist";
    }

    @Override
    public String description() {
        return "Keep raw iron and flint with an empty depositableBulkItems allowlist while unloading rubble";
    }

    @Override
    public int tickBudget() {
        return 20 * 240;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", true);
        settings.put("restockFromBoxes", true);
        settings.put("shulkerDump", true);
        settings.put("shulkerDumpWhenFreeSlotsBelow", 2);
        settings.put("shulkerDumpMaxBoxesPerTrip", 1);
        settings.put("restockDumpJunk", false);
        settings.put("depositableBulkItems", Collections.emptyList());
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        arena.command("give @s minecraft:raw_iron " + RAW_IRON_COUNT);
        arena.command("give @s minecraft:flint " + FLINT_COUNT);
        arena.command("give @s minecraft:iron_pickaxe 1");
        for (Item item : JUNK_ITEMS) {
            arena.command("give @s " + BuiltInRegistries.ITEM.getKey(item) + " 1");
        }
        arena.setBlock(TARGET_X, 0, 0, "minecraft:stone");
        arena.setBlock(BOX_X, 0, BOX_Z, "minecraft:shulker_box[facing=up]");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return ScenarioInventory.countPlayer(arena, Items.RAW_IRON) == RAW_IRON_COUNT
                && ScenarioInventory.countPlayer(arena, Items.FLINT) == FLINT_COUNT
                && ScenarioInventory.countPlayer(arena, Items.IRON_PICKAXE) == 1
                && countPlayerJunk(arena) == JUNK_ITEMS.length
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RAW_IRON) == 0
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.FLINT) == 0;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the unload box at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        arena.note("staged %d raw iron, %d flint, and %d rubble stacks; empty allowlist is active",
                RAW_IRON_COUNT, FLINT_COUNT, JUNK_ITEMS.length);
        arena.baritone().getMineProcess().mineByName(1, "minecraft:stone");
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!arena.stateAt(TARGET_X, 0, 0).isAir()) {
            return null;
        }
        int junkBoxed = countContainerJunk(arena);
        int rawIronBoxed = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RAW_IRON);
        int flintBoxed = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.FLINT);
        int rawIronCarried = ScenarioInventory.countPlayer(arena, Items.RAW_IRON);
        int flintCarried = ScenarioInventory.countPlayer(arena, Items.FLINT);
        arena.note("after the mine: rubble in box=%d, raw iron in box=%d, flint in box=%d", junkBoxed,
                rawIronBoxed, flintBoxed);

        if (junkBoxed <= 0) {
            return Verdict.fail("the stone was mined, but no rubble reached the box; no unload trip was proven");
        }
        if (rawIronBoxed != 0 || flintBoxed != 0
                || rawIronCarried != RAW_IRON_COUNT || flintCarried != FLINT_COUNT) {
            return Verdict.fail("empty allowlist was bypassed: carried raw iron=%d, flint=%d; boxed raw iron=%d, flint=%d",
                    rawIronCarried, flintCarried, rawIronBoxed, flintBoxed);
        }
        return Verdict.pass(String.format("unloaded %d rubble stacks while retaining %d raw iron and %d flint",
                junkBoxed, rawIronCarried, flintCarried));
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "stone target=" + arena.stateAt(TARGET_X, 0, 0).getBlock().getName().getString()
                + ", rubble in box=" + countContainerJunk(arena)
                + ", raw iron carried=" + ScenarioInventory.countPlayer(arena, Items.RAW_IRON)
                + ", flint carried=" + ScenarioInventory.countPlayer(arena, Items.FLINT);
    }

    private static int countPlayerJunk(TestArena arena) {
        int count = 0;
        for (Item item : JUNK_ITEMS) {
            count += ScenarioInventory.countPlayer(arena, item);
        }
        return count;
    }

    private static int countContainerJunk(TestArena arena) {
        int count = 0;
        for (Item item : JUNK_ITEMS) {
            count += ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, item);
        }
        return count;
    }
}
