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
import baritone.utils.schematic.StaticSchematic;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Shared live-inventory fixture for opportunistic junk dumping during a restock. */
abstract class AbstractRestockDumpScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final Item[] JUNK_ITEMS = {
            Items.RED_CONCRETE, Items.ORANGE_CONCRETE, Items.YELLOW_CONCRETE, Items.LIME_CONCRETE,
            Items.GREEN_CONCRETE, Items.CYAN_CONCRETE, Items.LIGHT_BLUE_CONCRETE, Items.BLUE_CONCRETE,
            Items.PURPLE_CONCRETE, Items.MAGENTA_CONCRETE, Items.PINK_CONCRETE, Items.BROWN_CONCRETE,
            Items.BLACK_CONCRETE, Items.GRAY_CONCRETE, Items.LIGHT_GRAY_CONCRETE, Items.RED_WOOL,
            Items.ORANGE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL, Items.GREEN_WOOL, Items.CYAN_WOOL,
            Items.LIGHT_BLUE_WOOL, Items.BLUE_WOOL, Items.PURPLE_WOOL, Items.MAGENTA_WOOL, Items.PINK_WOOL,
            Items.BROWN_WOOL, Items.BLACK_WOOL, Items.GRAY_WOOL, Items.LIGHT_GRAY_WOOL, Items.WHITE_WOOL,
            Items.TERRACOTTA, Items.RED_TERRACOTTA, Items.ORANGE_TERRACOTTA
    };

    protected abstract boolean keepShulker();

    @Override
    public int tickBudget() {
        return 20 * 300;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockIndexBeforeBuild", false);
        settings.put("restockExtraStacks", 0);
        settings.put("restockDumpJunk", true);
        settings.put("restockDumpWhenFreeSlotsBelow", 4);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        // The first target can be placed, then the second forces a genuine restock while the
        // inventory has fewer than four free slots.
        arena.command("give @s minecraft:white_concrete 1");
        if (keepShulker()) {
            arena.command("give @s minecraft:blue_shulker_box 1");
        }
        for (Item item : JUNK_ITEMS) {
            arena.command("give @s " + BuiltInRegistries.ITEM.getKey(item) + " 1");
        }
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        // Keep this 26.1.2 item-NBT form in lockstep with RestockFromBoxScenario.
        arena.setBlock(BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:64,Slot:0b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the dump box at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(arena.at(BUILD_X, 0, 0).x, arena.at(BUILD_X, 0, 0).y, arena.at(BUILD_X, 0, 0).z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE)
                || !arena.stateAt(BUILD_X, 2, 0).is(Blocks.WHITE_CONCRETE)) {
            return null;
        }
        int dumped = junkInBox(arena);
        int white = ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem());
        if (dumped <= 0 || white <= 0) {
            return Verdict.fail("the build completed with %d junk items deposited and %d white concrete retained", dumped, white);
        }
        if (keepShulker() && ScenarioInventory.countPlayer(arena, Blocks.BLUE_SHULKER_BOX.asItem()) != 1) {
            return Verdict.fail("the build completed, but the blue shulker box was stowed or lost during unloading");
        }
        return Verdict.pass(successMessage(dumped, white));
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "first target=" + arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE)
                + ", second target=" + arena.stateAt(BUILD_X, 2, 0).is(Blocks.WHITE_CONCRETE)
                + ", junk in box=" + junkInBox(arena);
    }

    protected String successMessage(int dumped, int white) {
        return "deposited " + dumped + " unwanted block items while retaining " + white + " white concrete";
    }

    private static int junkInBox(TestArena arena) {
        int count = 0;
        for (Item item : JUNK_ITEMS) {
            count += ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, item);
        }
        return count;
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][3];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        states[0][0][2] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
