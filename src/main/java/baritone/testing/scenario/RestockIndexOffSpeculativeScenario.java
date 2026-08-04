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

/** A build with indexing disabled must discover its source only after placement starts. */
public final class RestockIndexOffSpeculativeScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int TARGET_X = 6;
    private static final int SOURCE_COUNT = 2;

    private boolean placedBeforeBox;
    private boolean visitedBox;

    @Override
    public String name() {
        return "restock-index-off-speculative";
    }

    @Override
    public String description() {
        return "Discover an unindexed source after the first target is placed with pre-build indexing off";
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
            arena.note("no world data, so the unindexed source at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        arena.note("registered unindexed source %s with white=%d; starting carried white=1 and two targets",
                box, SOURCE_COUNT);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(target.x, target.y, target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int placed = placedTargets(arena);
        this.placedBeforeBox |= placed > 0 && !near(arena, arena.at(BOX_X, 0, BOX_Z));
        this.visitedBox |= near(arena, arena.at(BOX_X, 0, BOX_Z));
        if (placed == 2) {
            int source = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE);
            int carried = ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE);
            arena.note("speculative-index evidence at t=%d: placed=%d/2, source white=%d/%d, carried white=%d, player=%s",
                    elapsedTicks, placed, source, SOURCE_COUNT, carried, arena.ctx().playerFeet());
            if (!this.placedBeforeBox || !this.visitedBox) {
                return Verdict.fail("the build completed without proving a post-placement visit to the unindexed source: placedBeforeBox=%b, visitedBox=%b",
                        this.placedBeforeBox, this.visitedBox);
            }
            return source == SOURCE_COUNT - 1 && carried == 0
                    ? Verdict.pass("placed before visiting the unindexed box, then fetched the exact shortfall")
                    : Verdict.fail("the post-placement fetch left source white=%d and carried white=%d; expected %d and 0",
                    source, carried, SOURCE_COUNT - 1);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("placed=%d/2, source white=%d/%d, carried white=%d, placedBeforeBox=%b, visitedBox=%b, player=%s",
                placedTargets(arena), ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE),
                SOURCE_COUNT, ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE), this.placedBeforeBox,
                this.visitedBox, arena.ctx().playerFeet());
    }

    private static int placedTargets(TestArena arena) {
        int placed = 0;
        if (arena.stateAt(TARGET_X, 1, 0).is(Blocks.WHITE_CONCRETE)) {
            placed++;
        }
        if (arena.stateAt(TARGET_X, 2, 0).is(Blocks.WHITE_CONCRETE)) {
            placed++;
        }
        return placed;
    }

    private static boolean near(TestArena arena, BetterBlockPos pos) {
        return arena.ctx().playerFeet().distSqr(pos) <= 9.0;
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][3];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        states[0][0][2] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
