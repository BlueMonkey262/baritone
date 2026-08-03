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

/** A readable menu for box A must not be reused when the next transfer targets box B. */
public final class ContainerWrongMenuNoTransferScenario extends TestScenario {

    private static final int BOX_A_X = 2;
    private static final int BOX_B_X = -4;
    private static final int BOX_Z = 4;
    private static final int BUILD_X = 6;
    private static final int A_COUNT = 17;
    private static final int B_COUNT = 1;

    private boolean indexingStarted;
    private boolean buildStarted;

    @Override
    public String name() {
        return "container-wrong-menu-no-transfer";
    }

    @Override
    public String description() {
        return "Reject a stale readable menu from box A when restocking from box B";
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
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-8, -3, -12, 16, -1, 12, "minecraft:stone");
        arena.fill(-8, 0, -12, 16, 4, 12, "minecraft:air");
        arena.command("clear @s");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        ContainerFixture.emptyBox(arena, BOX_A_X, 0, BOX_Z);
        ContainerFixture.put(arena, BOX_A_X, 0, BOX_Z, 0, Items.DIRT, A_COUNT);
        ContainerFixture.emptyBox(arena, BOX_B_X, 0, BOX_Z);
        ContainerFixture.put(arena, BOX_B_X, 0, BOX_Z, 0, Items.WHITE_CONCRETE, B_COUNT);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 1, 0).isAir()
                && ContainerFixture.isBox(arena, BOX_A_X, 0, BOX_Z)
                && ContainerFixture.isBox(arena, BOX_B_X, 0, BOX_Z)
                && ScenarioInventory.countContainer(arena, BOX_A_X, 0, BOX_Z, Items.DIRT) == A_COUNT
                && ScenarioInventory.countContainer(arena, BOX_B_X, 0, BOX_Z, Items.WHITE_CONCRETE) == B_COUNT
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so the two-menu transfer could not be registered");
            return;
        }
        BetterBlockPos a = arena.at(BOX_A_X, 0, BOX_Z);
        world.getRestockBoxes().addBox(a);
        this.indexingStarted = arena.baritone().getRestockProcess().requestIndexing(false, stack -> true);
        arena.note("opened box A for a real sync before adding box B: indexing started=%b", this.indexingStarted);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!this.indexingStarted) {
            return Verdict.fail("could not start the box-A indexing phase");
        }
        if (!this.buildStarted
                && arena.baritone().getContainerInteractionBehavior().openContainer() != null
                && arena.baritone().getContainerInteractionBehavior().isContainerReadable()) {
            IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
            BetterBlockPos b = arena.at(BOX_B_X, 0, BOX_Z);
            world.getRestockBoxes().addBox(b);
            BetterBlockPos target = arena.at(BUILD_X, 0, 0);
            arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                    new Vec3i(target.x, target.y, target.z));
            this.buildStarted = true;
            arena.note("started the white-concrete build while box A's menu was readable; A dirt=%d, B white=%d",
                    ScenarioInventory.countContainer(arena, BOX_A_X, 0, BOX_Z, Items.DIRT),
                    ScenarioInventory.countContainer(arena, BOX_B_X, 0, BOX_Z, Items.WHITE_CONCRETE));
        }
        if (!this.buildStarted || !arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE)) {
            return null;
        }
        int aDirt = ScenarioInventory.countContainer(arena, BOX_A_X, 0, BOX_Z, Items.DIRT);
        int bWhite = ScenarioInventory.countContainer(arena, BOX_B_X, 0, BOX_Z, Items.WHITE_CONCRETE);
        int carriedWhite = ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE);
        arena.note("wrong-menu evidence at t=%d: A dirt=%d/%d, B white=%d/%d, carried white=%d, player=%s",
                elapsedTicks, aDirt, A_COUNT, bWhite, B_COUNT, carriedWhite, arena.ctx().playerFeet());
        if (aDirt != A_COUNT || bWhite != 0) {
            return Verdict.fail("the completed build used the wrong menu: A dirt=%d and B white=%d; expected %d and 0",
                    aDirt, bWhite, A_COUNT);
        }
        return carriedWhite == 0
                ? Verdict.pass("box A stayed untouched and box B supplied the completed build")
                : Verdict.fail("box B was selected but %d white concrete remained carried", carriedWhite);
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "indexing started=" + this.indexingStarted + ", build started=" + this.buildStarted
                + ", target=" + arena.stateAt(BUILD_X, 1, 0).getBlock()
                + ", A dirt=" + ScenarioInventory.countContainer(arena, BOX_A_X, 0, BOX_Z, Items.DIRT)
                + ", B white=" + ScenarioInventory.countContainer(arena, BOX_B_X, 0, BOX_Z, Items.WHITE_CONCRETE);
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][2];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
