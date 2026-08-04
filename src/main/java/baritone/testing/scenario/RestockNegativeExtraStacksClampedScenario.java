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
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Negative surplus arithmetic must behave exactly like zero surplus. */
public final class RestockNegativeExtraStacksClampedScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int TARGET_X = 6;
    private static final int SOURCE_COUNT = 2;

    @Override
    public String name() {
        return "restock-negative-extra-stacks-clamped";
    }

    @Override
    public String description() {
        return "Treat a negative restockExtraStacks value as zero while fetching the required blocks";
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
        settings.put("restockExtraStacks", -4);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-8, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-8, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        arena.command("give @s minecraft:white_concrete 1");
        arena.setBlock(TARGET_X, 0, 0, "minecraft:stone");
        ContainerFixture.emptyBox(arena, BOX_X, 0, BOX_Z);
        ContainerFixture.put(arena, BOX_X, 0, BOX_Z, 0, Items.WHITE_CONCRETE, SOURCE_COUNT);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 1, 0).isAir()
                && arena.stateAt(TARGET_X, 2, 0).isAir()
                && ContainerFixture.isBox(arena, BOX_X, 0, BOX_Z)
                && ScenarioInventory.countNonEmptyContainerSlots(arena, BOX_X, 0, BOX_Z) == 1
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE) == SOURCE_COUNT
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        BetterBlockPos target = arena.at(TARGET_X, 0, 0);
        if (world == null) {
            arena.note("no world data, so the negative-surplus source at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        arena.note("registered source %s with white=%d; requested shortfall is exactly two and extraStacks=-4",
                box, SOURCE_COUNT);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(target.x, target.y, target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int first = arena.stateAt(TARGET_X, 1, 0).is(Blocks.WHITE_CONCRETE) ? 1 : 0;
        int second = arena.stateAt(TARGET_X, 2, 0).is(Blocks.WHITE_CONCRETE) ? 1 : 0;
        int built = first + second;
        int source = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE);
        int carried = ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE);
        if (built == 2) {
            arena.note("negative-surplus evidence at t=%d: built=%d/2, source white=%d/%d, carried white=%d, player=%s",
                    elapsedTicks, built, source, SOURCE_COUNT, carried, arena.ctx().playerFeet());
            return source == 0 && carried == 0
                    ? Verdict.pass("clamped negative surplus to zero and fetched only the exact two-block shortfall")
                    : Verdict.fail("negative surplus produced source white=%d and carried white=%d; expected 0 and 0",
                    source, carried);
        }
        if (built > 0 && elapsedTicks > 20 * 25 && !arena.baritone().getBuilderProcess().isActive()) {
            return Verdict.fail("negative surplus stopped after only %d/%d targets with source white=%d and carried white=%d",
                    built, 2, source, carried);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("built=%d/2, source white=%d/%d, carried white=%d, builderActive=%b, player=%s",
                (arena.stateAt(TARGET_X, 1, 0).is(Blocks.WHITE_CONCRETE) ? 1 : 0)
                        + (arena.stateAt(TARGET_X, 2, 0).is(Blocks.WHITE_CONCRETE) ? 1 : 0),
                ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE), SOURCE_COUNT,
                ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE),
                arena.baritone().getBuilderProcess().isActive(), arena.ctx().playerFeet());
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][3];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        states[0][0][2] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
