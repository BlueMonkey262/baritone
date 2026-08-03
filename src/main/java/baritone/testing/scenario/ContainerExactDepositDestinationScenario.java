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

/** The destination box, rather than player return, proves where junk went. */
public final class ContainerExactDepositDestinationScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int BUILD_X = 6;
    private static final int SOURCE_COUNT = 1;

    @Override
    public String name() {
        return "container-exact-deposit-destination";
    }

    @Override
    public String description() {
        return "Assert that opportunistic junk reaches the intended restock box";
    }

    @Override
    public int tickBudget() {
        return 20 * 220;
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
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        ContainerFixture.emptyBox(arena, BOX_X, 0, BOX_Z);
        ContainerFixture.put(arena, BOX_X, 0, BOX_Z, 0, Items.WHITE_CONCRETE, SOURCE_COUNT);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 1, 0).isAir()
                && ContainerFixture.isBox(arena, BOX_X, 0, BOX_Z)
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE) == SOURCE_COUNT
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE) == 0
                && RestockInventoryFixture.staged(arena, 33);
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the deposit destination could not be registered");
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
        int white = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE);
        int red = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE);
        int carriedRed = ScenarioInventory.countPlayer(arena, Items.RED_CONCRETE);
        arena.note("deposit evidence at t=%d: target=%s, destination white=%d, red=%d, carried red=%d, player=%s",
                elapsedTicks, arena.stateAt(BUILD_X, 1, 0).getBlock().getName().getString(),
                white, red, carriedRed, arena.ctx().playerFeet());
        if (white != 0 || red != 1) {
            return Verdict.fail("the build returned to work but destination contents were white=%d and red=%d; expected 0 and 1",
                    white, red);
        }
        return carriedRed == 0
                ? Verdict.pass("the fetched item was used and the one junk stack reached its intended destination")
                : Verdict.fail("the destination received the junk, but the player still carries %d red concrete", carriedRed);
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "target=" + arena.stateAt(BUILD_X, 1, 0).getBlock()
                + ", destination white=" + ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE)
                + ", destination red=" + ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE)
                + ", carried red=" + ScenarioInventory.countPlayer(arena, Items.RED_CONCRETE);
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][2];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
