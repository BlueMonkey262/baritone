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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** A single box must satisfy two independently depleted schematic materials. */
public final class RestockTwoMaterialsScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int BOX_COUNT = 16;

    @Override
    public String name() {
        return "restock-two-materials";
    }

    @Override
    public String description() {
        return "Fetch depleted white and orange concrete from one registered shulker box";
    }

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
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        arena.command("give @s minecraft:white_concrete 1");
        arena.command("give @s minecraft:orange_concrete 1");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        arena.setBlock(BUILD_X + 2, 0, 0, "minecraft:stone");
        // Keep this 26.1.2 item-NBT form in lockstep with RestockFromBoxScenario.
        arena.setBlock(BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:16,Slot:0b},{id:\"minecraft:orange_concrete\",count:16,Slot:1b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X + 2, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the two-material box at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(arena.at(BUILD_X, 0, 0).x, arena.at(BUILD_X, 0, 0).y, arena.at(BUILD_X, 0, 0).z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!built(arena)) {
            return null;
        }
        int whiteInBox = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Blocks.WHITE_CONCRETE.asItem());
        int orangeInBox = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Blocks.ORANGE_CONCRETE.asItem());
        if (whiteInBox >= BOX_COUNT || orangeInBox >= BOX_COUNT) {
            return Verdict.fail("the build completed but the box still has white=%d and orange=%d, so one depleted material was not fetched",
                    whiteInBox, orangeInBox);
        }
        return Verdict.pass("all four declared material targets were built after both box materials were consumed");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "white targets=" + targetsPresent(arena, Blocks.WHITE_CONCRETE)
                + "/2, orange targets=" + targetsPresent(arena, Blocks.ORANGE_CONCRETE) + "/2";
    }

    private static boolean built(TestArena arena) {
        return targetsPresent(arena, Blocks.WHITE_CONCRETE) == 2
                && targetsPresent(arena, Blocks.ORANGE_CONCRETE) == 2;
    }

    private static int targetsPresent(TestArena arena, net.minecraft.world.level.block.Block block) {
        int count = 0;
        if (arena.stateAt(BUILD_X, 1, 0).is(block)) {
            count++;
        }
        if (arena.stateAt(BUILD_X, 2, 0).is(block)) {
            count++;
        }
        if (arena.stateAt(BUILD_X + 2, 1, 0).is(block)) {
            count++;
        }
        if (arena.stateAt(BUILD_X + 2, 2, 0).is(block)) {
            count++;
        }
        return count;
    }

    private static StaticSchematic schematic() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[][][] states = new BlockState[3][1][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                states[x][0][y] = air;
            }
        }
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[2][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        states[0][0][2] = Blocks.WHITE_CONCRETE.defaultBlockState();
        states[2][0][1] = Blocks.ORANGE_CONCRETE.defaultBlockState();
        states[2][0][2] = Blocks.ORANGE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
