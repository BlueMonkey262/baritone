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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** A stack-size-one block must not turn one missing item into a full configured surplus stack. */
public final class RestockLowStackItemScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int SOURCE_COUNT = 2;

    @Override
    public String name() {
        return "restock-low-stack-item";
    }

    @Override
    public String description() {
        return "Fetch exactly one stack-size-one shulker box for a one-block shortage";
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
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        arena.setBlock(BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:blue_shulker_box\",count:"
                        + SOURCE_COUNT + ",Slot:0b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 1, 0).isAir()
                && arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z,
                Blocks.BLUE_SHULKER_BOX.asItem()) == SOURCE_COUNT
                && ScenarioInventory.countPlayer(arena, Blocks.BLUE_SHULKER_BOX.asItem()) == 0;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the stack-size-one box at %s could not be registered", box);
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
        if (!arena.stateAt(BUILD_X, 1, 0).is(Blocks.BLUE_SHULKER_BOX)) {
            return null;
        }
        int remaining = ScenarioInventory.countContainer(
                arena, BOX_X, 0, BOX_Z, Blocks.BLUE_SHULKER_BOX.asItem());
        int carried = ScenarioInventory.countPlayer(arena, Blocks.BLUE_SHULKER_BOX.asItem());
        if (remaining != SOURCE_COUNT - 1 || carried != 0) {
            return Verdict.fail("the one-block build completed, but fetched %d source boxes and still carry %d; expected one and zero",
                    SOURCE_COUNT - remaining, carried);
        }
        return Verdict.pass("the stack-size-one shortage fetched exactly one shulker box and left the extra source box untouched");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "target=" + arena.stateAt(BUILD_X, 1, 0).getBlock()
                + ", source boxes=" + ScenarioInventory.countContainer(
                arena, BOX_X, 0, BOX_Z, Blocks.BLUE_SHULKER_BOX.asItem())
                + ", carried=" + ScenarioInventory.countPlayer(arena, Blocks.BLUE_SHULKER_BOX.asItem());
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][2];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.BLUE_SHULKER_BOX.defaultBlockState();
        return new StaticSchematic(states);
    }
}
