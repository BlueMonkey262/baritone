/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone. If not, see <https://www.gnu.org/licenses/>.
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

/** A crowded restock must classify tools, food, armour and weapons as protected inventory. */
public final class RestockPreservesToolsScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int JUNK_SLOTS = 29;
    private static final int SOURCE_COUNT = 64;

    @Override
    public String name() {
        return "restock-preserves-tools";
    }

    @Override
    public String description() {
        return "Preserve tools, food, armour and weapons while restocking through a crowded inventory";
    }

    @Override
    public int tickBudget() {
        return 20 * 180;
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
        RestockInventoryFixture.stage(arena, JUNK_SLOTS);
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        arena.setBlock(BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:"
                        + SOURCE_COUNT + ",Slot:0b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 1, 0).isAir()
                && arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z,
                Blocks.WHITE_CONCRETE.asItem()) == SOURCE_COUNT
                && RestockInventoryFixture.staged(arena, JUNK_SLOTS);
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the protected-item box at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        BetterBlockPos origin = arena.at(BUILD_X, 0, 0);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(),
                schematic(),
                new Vec3i(origin.x, origin.y, origin.z)
        );
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE)) {
            return null;
        }
        int source = ScenarioInventory.countContainer(
                arena, BOX_X, 0, BOX_Z, Blocks.WHITE_CONCRETE.asItem());
        int dumped = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE);
        if (!RestockInventoryFixture.protectedItemsIntact(arena)) {
            return Verdict.fail("the crowded-inventory build completed, but a tool, food, weapon, or helmet changed");
        }
        if (source != SOURCE_COUNT - 1 || dumped <= 0) {
            return Verdict.fail("the crowded-inventory build completed with source white concrete=%d and dumped red concrete=%d; expected %d and some dumped junk",
                    source, dumped, SOURCE_COUNT - 1);
        }
        return Verdict.pass("crowded-inventory restocking deposited only junk and retained the protected tool, food, weapon and helmet");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "target=" + arena.stateAt(BUILD_X, 1, 0).getBlock()
                + ", occupied=" + RestockInventoryFixture.occupied(arena)
                + ", protected=" + RestockInventoryFixture.protectedItemsIntact(arena)
                + ", source white=" + ScenarioInventory.countContainer(
                arena, BOX_X, 0, BOX_Z, Blocks.WHITE_CONCRETE.asItem())
                + ", dumped red=" + ScenarioInventory.countContainer(
                arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE);
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][2];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
