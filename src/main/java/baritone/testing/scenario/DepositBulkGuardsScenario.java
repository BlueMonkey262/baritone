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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Checks the pre-allowlist guards and worthKeeping on a dedicated shulker unload trip. */
public final class DepositBulkGuardsScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int RAW_IRON_START = 1;
    private static final int RAW_IRON_EXPECTED = 4;
    private static final int ORE_X = 3;
    private static final int ORE_COUNT = 3;
    private static final int JUNK_STACKS = 30;

    private static final Item[] GUARDED_ITEMS = {
            Items.APPLE,         // FOOD
            Items.IRON_PICKAXE,  // TOOL, and the tool used by the mine
            Items.IRON_SWORD,    // WEAPON
            Items.IRON_HELMET    // EQUIPPABLE
    };

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
        return "deposit-bulk-guards";
    }

    @Override
    public String description() {
        return "Keep active-mine raw iron and guarded items during a dedicated bulk unload trip";
    }

    @Override
    public int tickBudget() {
        return 20 * 300;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", true);
        settings.put("restockFromBoxes", true);
        settings.put("shulkerDump", true);
        settings.put("shulkerDumpWhenFreeSlotsBelow", 2);
        settings.put("shulkerDumpMaxBoxesPerTrip", 1);
        // The dedicated shulkerDump request must be the thing that deposits the fixture.
        settings.put("restockDumpJunk", false);
        // The scenario deliberately names every guarded item, so a guard-order regression fails.
        settings.put("depositableBulkItems", Arrays.asList(
                Items.RAW_IRON, Items.FLINT,
                Items.APPLE, Items.IRON_PICKAXE, Items.IRON_SWORD, Items.IRON_HELMET));
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        arena.command("give @s minecraft:raw_iron " + RAW_IRON_START);
        arena.command("give @s minecraft:flint 64");
        for (Item item : GUARDED_ITEMS) {
            arena.command("give @s " + BuiltInRegistries.ITEM.getKey(item) + " 1");
        }
        for (int i = 0; i < JUNK_STACKS; i++) {
            Item item = JUNK_ITEMS[i];
            arena.command("give @s " + BuiltInRegistries.ITEM.getKey(item) + " 1");
        }
        for (int i = 0; i < ORE_COUNT; i++) {
            arena.setBlock(ORE_X + i, 0, 0, "minecraft:iron_ore");
        }
        arena.setBlock(BOX_X, 0, BOX_Z, "minecraft:shulker_box[facing=up]");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        boolean oreStaged = true;
        for (int i = 0; i < ORE_COUNT; i++) {
            oreStaged &= arena.stateAt(ORE_X + i, 0, 0).is(Blocks.IRON_ORE);
        }
        boolean guardsStaged = true;
        for (Item item : GUARDED_ITEMS) {
            guardsStaged &= ScenarioInventory.countPlayer(arena, item) == 1;
        }
        return ScenarioInventory.countPlayer(arena, Items.RAW_IRON) == RAW_IRON_START
                && ScenarioInventory.countPlayer(arena, Items.FLINT) == 64
                && countPlayerJunk(arena) == JUNK_STACKS
                && guardsStaged
                && oreStaged
                && arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
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
        arena.note("staged %d raw iron, %d flint, %d guarded stacks, and %d rubble stacks; mining iron ore",
                RAW_IRON_START, 64, GUARDED_ITEMS.length, JUNK_STACKS);
        // Raw iron is an ordinary drop of the active job, so worthKeeping must win after the
        // allowlist. The full inventory forces requestDeposit before the first ore is mined.
        arena.baritone().getMineProcess().mineByName(RAW_IRON_EXPECTED, "minecraft:iron_ore");
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!allOreMined(arena)) {
            return null;
        }
        int rawIronBoxed = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RAW_IRON);
        int flintBoxed = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.FLINT);
        int junkBoxed = countContainerJunk(arena);
        int rawIronCarried = ScenarioInventory.countPlayer(arena, Items.RAW_IRON);
        arena.note("after the mine: raw iron carried=%d, boxed=%d; flint boxed=%d; rubble boxed=%d",
                rawIronCarried, rawIronBoxed, flintBoxed, junkBoxed);

        if (flintBoxed <= 0 || junkBoxed <= 0) {
            return Verdict.fail("all iron ore was mined but no dedicated unload was proven: flint boxed=%d, rubble boxed=%d",
                    flintBoxed, junkBoxed);
        }
        if (rawIronCarried < RAW_IRON_EXPECTED || rawIronBoxed != 0) {
            return Verdict.fail("the active mine drop was deposited: raw iron carried=%d, boxed=%d",
                    rawIronCarried, rawIronBoxed);
        }
        for (Item item : GUARDED_ITEMS) {
            int carried = ScenarioInventory.countPlayer(arena, item);
            int boxed = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, item);
            if (carried != 1 || boxed != 0) {
                return Verdict.fail("guarded item %s moved during unload: carried=%d, boxed=%d",
                        BuiltInRegistries.ITEM.getKey(item), carried, boxed);
            }
        }
        return Verdict.pass(String.format("dedicated unload deposited flint and %d rubble stacks while keeping raw iron and all four guards",
                junkBoxed));
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "ore mined=" + allOreMined(arena)
                + ", raw iron carried=" + ScenarioInventory.countPlayer(arena, Items.RAW_IRON)
                + ", raw iron boxed=" + ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RAW_IRON)
                + ", flint boxed=" + ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.FLINT)
                + ", rubble boxed=" + countContainerJunk(arena);
    }

    private static boolean allOreMined(TestArena arena) {
        for (int i = 0; i < ORE_COUNT; i++) {
            if (!arena.stateAt(ORE_X + i, 0, 0).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static int countPlayerJunk(TestArena arena) {
        int count = 0;
        for (int i = 0; i < JUNK_STACKS; i++) {
            count += ScenarioInventory.countPlayer(arena, JUNK_ITEMS[i]);
        }
        return count;
    }

    private static int countContainerJunk(TestArena arena) {
        int count = 0;
        for (int i = 0; i < JUNK_STACKS; i++) {
            count += ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, JUNK_ITEMS[i]);
        }
        return count;
    }
}
