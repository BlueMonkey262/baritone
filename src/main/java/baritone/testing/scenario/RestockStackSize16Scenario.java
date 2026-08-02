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

/** Protects stack-surplus accounting from assuming every item stacks to 64. */
public final class RestockStackSize16Scenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int STACK = 16;

    @Override
    public String name() {
        return "restock-stack-size-16";
    }

    @Override
    public String description() {
        return "Fetch two 16-stack sign stacks for a two-sign shortage plus one extra stack";
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
        settings.put("restockExtraStacks", 1);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        arena.setBlock(BUILD_X + 2, 0, 0, "minecraft:stone");
        // Keep this 26.1.2 item-NBT form in lockstep with RestockFromBoxScenario.
        arena.setBlock(BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:oak_sign\",count:16,Slot:0b},{id:\"minecraft:oak_sign\",count:16,Slot:1b},{id:\"minecraft:oak_sign\",count:16,Slot:2b}]}");
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
            arena.note("no world data, so the sign box at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(arena.at(BUILD_X, 0, 0).x, arena.at(BUILD_X, 0, 0).y, arena.at(BUILD_X, 0, 0).z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!arena.stateAt(BUILD_X, 1, 0).is(Blocks.OAK_SIGN)
                || !arena.stateAt(BUILD_X + 2, 1, 0).is(Blocks.OAK_SIGN)) {
            return null;
        }
        int playerSigns = ScenarioInventory.countPlayer(arena, Blocks.OAK_SIGN.asItem());
        int boxSigns = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Blocks.OAK_SIGN.asItem());
        // Missing two signs + one configured surplus stack is 18. Two full 16-stacks are therefore
        // moved (32 total), two are placed, and the third source stack remains untouched.
        if (playerSigns != 30 || boxSigns != STACK) {
            return Verdict.fail("two signs were built, but expected 30 signs in inventory and 16 in the box; found %d and %d",
                    playerSigns, boxSigns);
        }
        return Verdict.pass("16-stack surplus accounting took exactly two sign stacks, leaving one source stack");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "signs: left=" + arena.stateAt(BUILD_X, 1, 0).is(Blocks.OAK_SIGN)
                + ", right=" + arena.stateAt(BUILD_X + 2, 1, 0).is(Blocks.OAK_SIGN)
                + ", inventory=" + ScenarioInventory.countPlayer(arena, Blocks.OAK_SIGN.asItem());
    }

    private static StaticSchematic schematic() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[][][] states = new BlockState[3][1][2];
        for (int x = 0; x < 3; x++) {
            states[x][0][0] = air;
            states[x][0][1] = air;
        }
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[2][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.OAK_SIGN.defaultBlockState();
        states[2][0][1] = Blocks.OAK_SIGN.defaultBlockState();
        return new StaticSchematic(states);
    }
}
