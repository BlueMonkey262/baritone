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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Raw ore and flint are deposited; diamonds and totems are not.
 * <p>
 * Both halves are the test. A deposit trip refuses anything that is not a {@code BlockItem}, which
 * is what keeps valuables out of the box, and that refusal is also why raw ore -- the bulk drop a
 * long mine actually produces -- could never be unloaded. The fix is the {@code depositableBulkItems}
 * allowlist rather than dropping the {@code BlockItem} test.
 * <p>
 * So this asserts the allowlist works <i>and</i> that the guard behind it still holds. A regression
 * that "fixed" the deposit by loosening the type test would pass the first assertion and fail the
 * second, which is the whole point of checking both in one scenario.
 */
public final class DepositBulkItemsScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;

    /** Enough to force a trip: the build needs free slots and these fill them. */
    private static final int RAW_ORE_COUNT = 64;
    private static final int FLINT_COUNT = 64;

    @Override
    public String name() {
        return "deposit-bulk-items";
    }

    @Override
    public String description() {
        return "Deposit raw ore and flint while keeping diamonds and totems out of the box";
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
        settings.put("restockDumpJunk", true);
        settings.put("restockDumpWhenFreeSlotsBelow", 4);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        arena.command("give @s minecraft:white_concrete 1");
        // The bulk drops a mine produces, which should end up in the box.
        arena.command("give @s minecraft:raw_iron " + RAW_ORE_COUNT);
        arena.command("give @s minecraft:flint " + FLINT_COUNT);
        // The valuables that must not, and which share the "not a BlockItem" property with them.
        arena.command("give @s minecraft:diamond 1");
        arena.command("give @s minecraft:totem_of_undying 1");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        // Keep this 26.1.2 item-NBT form in lockstep with RestockFromBoxScenario.
        arena.setBlock(BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:64,Slot:0b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        // Assert what was staged rather than trusting the give commands: a scenario staged without
        // the raw ore would deposit nothing and still satisfy the "valuables kept" half.
        boolean staged = ScenarioInventory.countPlayer(arena, Items.RAW_IRON) == RAW_ORE_COUNT
                && ScenarioInventory.countPlayer(arena, Items.FLINT) == FLINT_COUNT
                && ScenarioInventory.countPlayer(arena, Items.DIAMOND) == 1
                && ScenarioInventory.countPlayer(arena, Items.TOTEM_OF_UNDYING) == 1;
        return staged
                && arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
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
        arena.note("staged %d raw iron, %d flint, 1 diamond, 1 totem; box registered at %s",
                RAW_ORE_COUNT, FLINT_COUNT, box);
        BetterBlockPos target = arena.at(BUILD_X, 0, 0);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(target.x, target.y, target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE)
                || !arena.stateAt(BUILD_X, 2, 0).is(Blocks.WHITE_CONCRETE)) {
            return null;
        }
        int rawIronBoxed = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RAW_IRON);
        int flintBoxed = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.FLINT);
        int diamondBoxed = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.DIAMOND);
        int totemBoxed = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.TOTEM_OF_UNDYING);
        arena.note("after the build: box holds raw iron=%d, flint=%d, diamond=%d, totem=%d",
                rawIronBoxed, flintBoxed, diamondBoxed, totemBoxed);

        if (diamondBoxed > 0 || totemBoxed > 0) {
            return Verdict.fail("valuables were deposited: %d diamond, %d totem reached the box",
                    diamondBoxed, totemBoxed);
        }
        if (ScenarioInventory.countPlayer(arena, Items.DIAMOND) != 1
                || ScenarioInventory.countPlayer(arena, Items.TOTEM_OF_UNDYING) != 1) {
            return Verdict.fail("the diamond or totem left the inventory without reaching the box");
        }
        if (rawIronBoxed <= 0 || flintBoxed <= 0) {
            return Verdict.fail("bulk items were not deposited: raw iron=%d, flint=%d in the box",
                    rawIronBoxed, flintBoxed);
        }
        return Verdict.pass(String.format(
                "deposited %d raw iron and %d flint while keeping the diamond and totem",
                rawIronBoxed, flintBoxed));
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "first target=" + arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE)
                + ", second target=" + arena.stateAt(BUILD_X, 2, 0).is(Blocks.WHITE_CONCRETE)
                + ", raw iron in box=" + ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RAW_IRON)
                + ", flint in box=" + ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.FLINT)
                + ", raw iron carried=" + ScenarioInventory.countPlayer(arena, Items.RAW_IRON);
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][3];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        states[0][0][2] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
