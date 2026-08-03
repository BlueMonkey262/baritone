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

/** Turning the restock master switch off must preserve the upstream shortage behavior. */
public final class RestockMasterOffNoTripScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int BUILD_X = 6;
    /** Kept: the scenario is named no-trip, so not visiting is part of the claim. */
    private boolean sawBox;

    private int startingCarriedWhite;
    private int startingBoxWhite;

    @Override
    public String name() {
        return "restock-master-off-no-trip";
    }

    @Override
    public String description() {
        return "Stop at a material shortage without visiting a registered box when restock is off";
    }

    @Override
    public int tickBudget() {
        return 20 * 150;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        arena.command("give @s minecraft:white_concrete 1");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        ContainerFixture.emptyBox(arena, BOX_X, 0, BOX_Z);
        ContainerFixture.put(arena, BOX_X, 0, BOX_Z, 0, Items.WHITE_CONCRETE, 2);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 1, 0).isAir()
                && arena.stateAt(BUILD_X, 2, 0).isAir()
                && ContainerFixture.isBox(arena, BOX_X, 0, BOX_Z)
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE) == 2
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the off-control box could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(box);
        this.startingCarriedWhite = ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE);
        this.startingBoxWhite = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z,
                Items.WHITE_CONCRETE);
        BetterBlockPos target = arena.at(BUILD_X, 0, 0);
        arena.note("restockFromBoxes=false; carrying white=%d while box holds white=%d",
                this.startingCarriedWhite, this.startingBoxWhite);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(target.x, target.y, target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean first = arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE);
        boolean second = arena.stateAt(BUILD_X, 2, 0).is(Blocks.WHITE_CONCRETE);
        boolean active = arena.baritone().getBuilderProcess().isActive();
        int boxWhite = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE);
        int carriedWhite = ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE);
        arena.note("off-control evidence at t=%d: targets=%b/%b, box white=%d (start=%d), carried white=%d (start=%d), builder active=%b",
                elapsedTicks, first, second, boxWhite, this.startingBoxWhite, carriedWhite,
                this.startingCarriedWhite, active);
        this.sawBox |= arena.ctx().playerFeet().distSqr(arena.at(BOX_X, 0, BOX_Z)) <= 9.0;
        if (this.sawBox || boxWhite != this.startingBoxWhite
                || carriedWhite > this.startingCarriedWhite) {
            return Verdict.fail("restock-off path visited the box or took from it: visited=%b, box white=%d (start=%d), carried=%d (start=%d)",
                    this.sawBox, boxWhite, this.startingBoxWhite, carriedWhite,
                    this.startingCarriedWhite);
        }
        if (first && !second && !active) {
            return Verdict.pass("the first target placed, then the ordinary material shortage stopped without a restock trip");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "targets=" + arena.stateAt(BUILD_X, 1, 0).getBlock() + "/" + arena.stateAt(BUILD_X, 2, 0).getBlock()
                + ", box white=" + ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE)
                + ", carried white=" + ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE)
                + ", starting box white=" + this.startingBoxWhite
                + ", starting carried white=" + this.startingCarriedWhite
                + ", active=" + arena.baritone().getBuilderProcess().isActive();
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][3];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        states[0][0][2] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
