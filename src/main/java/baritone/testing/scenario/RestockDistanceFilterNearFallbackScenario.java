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

/** A stocked box outside the configured radius must not become a candidate. */
public final class RestockDistanceFilterNearFallbackScenario extends TestScenario {

    private static final int NEAR_X = 2;
    private static final int FAR_X = 10;
    private static final int BOX_Z = 4;
    private static final int BUILD_X = 6;
    private static final int SOURCE_COUNT = 2;
    private boolean sawFar;

    @Override
    public String name() {
        return "restock-distance-filter-near-fallback";
    }

    @Override
    public String description() {
        return "Use a nearby stocked box while excluding a stocked box beyond restockMaxDistance";
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
        settings.put("restockMaxDistance", 6);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-8, -3, -12, 20, -1, 12, "minecraft:stone");
        arena.fill(-8, 0, -12, 20, 4, 12, "minecraft:air");
        arena.command("clear @s");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        ContainerFixture.emptyBox(arena, NEAR_X, 0, BOX_Z);
        ContainerFixture.put(arena, NEAR_X, 0, BOX_Z, 0, Items.WHITE_CONCRETE, SOURCE_COUNT);
        ContainerFixture.emptyBox(arena, FAR_X, 0, BOX_Z);
        ContainerFixture.put(arena, FAR_X, 0, BOX_Z, 0, Items.WHITE_CONCRETE, SOURCE_COUNT);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && ContainerFixture.isBox(arena, NEAR_X, 0, BOX_Z)
                && ContainerFixture.isBox(arena, FAR_X, 0, BOX_Z)
                && ScenarioInventory.countContainer(arena, NEAR_X, 0, BOX_Z, Items.WHITE_CONCRETE) == SOURCE_COUNT
                && ScenarioInventory.countContainer(arena, FAR_X, 0, BOX_Z, Items.WHITE_CONCRETE) == SOURCE_COUNT
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so the near and far boxes could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(arena.at(NEAR_X, 0, BOX_Z));
        world.getRestockBoxes().addBox(arena.at(FAR_X, 0, BOX_Z));
        BetterBlockPos target = arena.at(BUILD_X, 0, 0);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(target.x, target.y, target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        this.sawFar |= arena.ctx().playerFeet().distSqr(arena.at(FAR_X, 0, BOX_Z)) <= 9.0;
        if (!arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE)) {
            return null;
        }
        int near = ScenarioInventory.countContainer(arena, NEAR_X, 0, BOX_Z, Items.WHITE_CONCRETE);
        int far = ScenarioInventory.countContainer(arena, FAR_X, 0, BOX_Z, Items.WHITE_CONCRETE);
        int carried = ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE);
        arena.note("distance evidence at t=%d: target=%s, near white=%d, far white=%d, crossed far=%b, player=%s",
                elapsedTicks, arena.stateAt(BUILD_X, 1, 0).getBlock().getName().getString(),
                near, far, this.sawFar, arena.ctx().playerFeet());
        if (this.sawFar) {
            return Verdict.fail("the player reached the stocked box outside restockMaxDistance");
        }
        arena.note("distance source/inventory delta: near %d -> %d, far %d -> %d, carried 0 -> %d",
                SOURCE_COUNT, near, SOURCE_COUNT, far, carried);
        return near == 0 && far == SOURCE_COUNT && carried == 1
                ? Verdict.pass("the near box supplied the target, its transferred stack was consumed once, and the stocked far box remained untouched")
                : Verdict.fail("distance filtering produced near white=%d, far white=%d, carried white=%d; expected near=0, far=%d, carried=1",
                        near, far, carried, SOURCE_COUNT);
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "target=" + arena.stateAt(BUILD_X, 1, 0).getBlock()
                + ", near white=" + ScenarioInventory.countContainer(arena, NEAR_X, 0, BOX_Z, Items.WHITE_CONCRETE)
                + ", far white=" + ScenarioInventory.countContainer(arena, FAR_X, 0, BOX_Z, Items.WHITE_CONCRETE)
                + ", crossed far=" + this.sawFar;
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][2];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
