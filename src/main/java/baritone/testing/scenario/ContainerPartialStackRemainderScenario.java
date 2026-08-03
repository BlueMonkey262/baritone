/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
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
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** A quick-move into a partial stack must merge without creating an extra slot. */
public final class ContainerPartialStackRemainderScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int BUILD_X = 6;
    private static final int INITIAL_RED = 60;

    @Override
    public String name() {
        return "container-partial-stack-remainder";
    }

    @Override
    public String description() {
        return "Merge a deposited four-item stack into a partial destination stack";
    }

    @Override
    public int tickBudget() {
        return 20 * 240;
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
        RestockInventoryFixture.stage(arena, 33);
        // Add three to the red stack placed in the first junk slot, making the total exactly four.
        arena.command("give @s minecraft:red_concrete 3");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        ContainerFixture.emptyBox(arena, BOX_X, 0, BOX_Z);
        ContainerFixture.put(arena, BOX_X, 0, BOX_Z, 0, Items.RED_CONCRETE, INITIAL_RED);
        ContainerFixture.put(arena, BOX_X, 0, BOX_Z, 1, Items.WHITE_CONCRETE, 1);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 1, 0).isAir()
                && ContainerFixture.isBox(arena, BOX_X, 0, BOX_Z)
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE) == INITIAL_RED
                && ScenarioInventory.countContainerSlots(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE) == 1
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE) == 1
                && ScenarioInventory.countPlayer(arena, Items.RED_CONCRETE) == 4
                && RestockInventoryFixture.protectedItemsIntact(arena);
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the partial-stack destination could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(box);
        BetterBlockPos target = arena.at(BUILD_X, 0, 0);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(target.x, target.y, target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE)) {
            return null;
        }
        int red = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE);
        int redSlots = ScenarioInventory.countContainerSlots(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE);
        int carriedRed = ScenarioInventory.countPlayer(arena, Items.RED_CONCRETE);
        arena.note("partial-stack evidence at t=%d: destination red=%d in %d slot(s), carried red=%d, target=%s",
                elapsedTicks, red, redSlots, carriedRed,
                arena.stateAt(BUILD_X, 1, 0).getBlock().getName().getString());
        if (red != INITIAL_RED + 4 || redSlots != 1) {
            return Verdict.fail("the destination ended with %d red concrete in %d slot(s); expected 64 in one slot",
                    red, redSlots);
        }
        return carriedRed == 0
                ? Verdict.pass("the four junk items merged into the existing partial stack without a second slot")
                : Verdict.fail("the merged destination is correct, but %d red concrete remained carried", carriedRed);
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "target=" + arena.stateAt(BUILD_X, 1, 0).getBlock()
                + ", destination red=" + ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE)
                + " in slots=" + ScenarioInventory.countContainerSlots(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE)
                + ", carried red=" + ScenarioInventory.countPlayer(arena, Items.RED_CONCRETE);
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][2];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
