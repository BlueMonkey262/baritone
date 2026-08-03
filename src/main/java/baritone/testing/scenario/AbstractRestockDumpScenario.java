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
import net.minecraft.world.level.block.Block;
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
            ScenarioInventory.item("minecraft:red_concrete"), ScenarioInventory.item("minecraft:orange_concrete"),
            ScenarioInventory.item("minecraft:yellow_concrete"), ScenarioInventory.item("minecraft:lime_concrete"),
            ScenarioInventory.item("minecraft:green_concrete"), ScenarioInventory.item("minecraft:cyan_concrete"),
            ScenarioInventory.item("minecraft:light_blue_concrete"), ScenarioInventory.item("minecraft:blue_concrete"),
            ScenarioInventory.item("minecraft:purple_concrete"), ScenarioInventory.item("minecraft:magenta_concrete"),
            ScenarioInventory.item("minecraft:pink_concrete"), ScenarioInventory.item("minecraft:brown_concrete"),
            ScenarioInventory.item("minecraft:black_concrete"), ScenarioInventory.item("minecraft:gray_concrete"),
            ScenarioInventory.item("minecraft:light_gray_concrete"), ScenarioInventory.item("minecraft:red_wool"),
            ScenarioInventory.item("minecraft:orange_wool"), ScenarioInventory.item("minecraft:yellow_wool"),
            ScenarioInventory.item("minecraft:lime_wool"), ScenarioInventory.item("minecraft:green_wool"),
            ScenarioInventory.item("minecraft:cyan_wool"), ScenarioInventory.item("minecraft:light_blue_wool"),
            ScenarioInventory.item("minecraft:blue_wool"), ScenarioInventory.item("minecraft:purple_wool"),
            ScenarioInventory.item("minecraft:magenta_wool"), ScenarioInventory.item("minecraft:pink_wool"),
            ScenarioInventory.item("minecraft:brown_wool"), ScenarioInventory.item("minecraft:black_wool"),
            ScenarioInventory.item("minecraft:gray_wool"), ScenarioInventory.item("minecraft:light_gray_wool"),
            ScenarioInventory.item("minecraft:white_wool"), ScenarioInventory.item("minecraft:terracotta"),
            ScenarioInventory.item("minecraft:red_terracotta"), ScenarioInventory.item("minecraft:orange_terracotta")
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
        Block whiteConcrete = ScenarioInventory.block("minecraft:white_concrete");
        if (!arena.stateAt(BUILD_X, 1, 0).is(whiteConcrete)
                || !arena.stateAt(BUILD_X, 2, 0).is(whiteConcrete)) {
            return null;
        }
        int dumped = junkInBox(arena);
        int white = ScenarioInventory.countPlayer(arena, ScenarioInventory.block("minecraft:white_concrete").asItem());
        if (dumped <= 0 || white <= 0) {
            return Verdict.fail("the build completed with %d junk items deposited and %d white concrete retained", dumped, white);
        }
        if (keepShulker() && ScenarioInventory.countPlayer(arena, ScenarioInventory.block("minecraft:blue_shulker_box").asItem()) != 1) {
            return Verdict.fail("the build completed, but the blue shulker box was stowed or lost during unloading");
        }
        return Verdict.pass(successMessage(dumped, white));
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        Block whiteConcrete = ScenarioInventory.block("minecraft:white_concrete");
        return "first target=" + arena.stateAt(BUILD_X, 1, 0).is(whiteConcrete)
                + ", second target=" + arena.stateAt(BUILD_X, 2, 0).is(whiteConcrete)
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
        Block whiteConcrete = ScenarioInventory.block("minecraft:white_concrete");
        states[0][0][1] = whiteConcrete.defaultBlockState();
        states[0][0][2] = whiteConcrete.defaultBlockState();
        return new StaticSchematic(states);
    }
}
