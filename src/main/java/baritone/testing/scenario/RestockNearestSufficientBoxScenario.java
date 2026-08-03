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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Equivalent indexed stock must be selected by distance. */
public final class RestockNearestSufficientBoxScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int NEAR_X = 2;
    private static final int NEAR_Z = 4;
    private static final int FAR_X = -6;
    private static final int FAR_Z = 10;
    private static final int BOX_COUNT = 16;

    @Override
    public String name() {
        return "restock-nearest-sufficient-box";
    }

    @Override
    public String description() {
        return "Choose the nearer of two equally sufficient indexed white-concrete boxes";
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
        arena.fill(-8, -3, -12, 16, -1, 12, "minecraft:stone");
        arena.fill(-8, 0, -12, 16, 3, 12, "minecraft:air");
        arena.command("clear @s");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        // Keep this 26.1.2 item-NBT form in lockstep with RestockFromBoxScenario.
        arena.setBlock(NEAR_X, 0, NEAR_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",Count:16b,Slot:0b}]}");
        arena.setBlock(FAR_X, 0, FAR_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",Count:16b,Slot:0b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(NEAR_X, 0, NEAR_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(FAR_X, 0, FAR_Z).getBlock() instanceof ShulkerBoxBlock;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so neither sufficient box could be registered");
            return;
        }
        BetterBlockPos near = arena.at(NEAR_X, 0, NEAR_Z);
        BetterBlockPos far = arena.at(FAR_X, 0, FAR_Z);
        world.getRestockBoxes().addBox(near);
        world.getRestockBoxes().addBox(far);
        // Both have the same recorded sufficient contents, so distance is the decisive key.
        world.getRestockBoxes().updateContents(near,
                Collections.singletonMap(Blocks.WHITE_CONCRETE.asItem(), BOX_COUNT));
        world.getRestockBoxes().updateContents(far,
                Collections.singletonMap(Blocks.WHITE_CONCRETE.asItem(), BOX_COUNT));
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(arena.at(BUILD_X, 0, 0).x, arena.at(BUILD_X, 0, 0).y, arena.at(BUILD_X, 0, 0).z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE)
                || !arena.stateAt(BUILD_X, 2, 0).is(Blocks.WHITE_CONCRETE)) {
            return null;
        }
        int near = ScenarioInventory.countContainer(arena, NEAR_X, 0, NEAR_Z, Blocks.WHITE_CONCRETE.asItem());
        int far = ScenarioInventory.countContainer(arena, FAR_X, 0, FAR_Z, Blocks.WHITE_CONCRETE.asItem());
        if (near >= BOX_COUNT || far != BOX_COUNT) {
            return Verdict.fail("the two targets were built but the nearer box has %d and farther box has %d white concrete", near, far);
        }
        return Verdict.pass("the nearer equally sufficient indexed box supplied the two white-concrete targets");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "near white=" + ScenarioInventory.countContainer(arena, NEAR_X, 0, NEAR_Z, Blocks.WHITE_CONCRETE.asItem())
                + ", far white=" + ScenarioInventory.countContainer(arena, FAR_X, 0, FAR_Z, Blocks.WHITE_CONCRETE.asItem());
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][3];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        states[0][0][2] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
