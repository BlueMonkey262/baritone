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

/** Zero extra stacks must fetch only the non-stack-aligned shortfall. */
public final class RestockExtraStacksZeroScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int BUILD_X = 6;
    private static final int TARGETS = 3;

    private boolean sawBox;
    private int maximumWhite;

    @Override
    public String name() {
        return "restock-extra-stacks-zero";
    }

    @Override
    public String description() {
        return "Fetch exactly a three-block shortfall when restockExtraStacks is zero";
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
        arena.setBlock(BUILD_X + 1, 0, 0, "minecraft:stone");
        arena.setBlock(BUILD_X + 2, 0, 0, "minecraft:stone");
        arena.setBlock(BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:3,Slot:0b},{id:\"minecraft:white_concrete\",count:64,Slot:1b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X + TARGETS - 1, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 1, 0).isAir()
                && arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem()) == 0;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the exact-shortfall box could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(box);
        BetterBlockPos target = arena.at(BUILD_X, 0, 0);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), schematic(), new Vec3i(target.x, target.y, target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        this.sawBox |= arena.ctx().playerFeet().distSqr(arena.at(BOX_X, 0, BOX_Z)) <= 9.0;
        this.maximumWhite = Math.max(this.maximumWhite,
                ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem()));
        int missing = missingTargets(arena);
        if (missing != 0) {
            if (elapsedTicks >= 20 * 12 && !arena.baritone().getBuilderProcess().isActive()) {
                return Verdict.fail("the exact-shortfall build stopped with %d target(s) missing; box reached=%b",
                        missing, this.sawBox);
            }
            return null;
        }
        if (!this.sawBox || this.maximumWhite != TARGETS) {
            return Verdict.fail("the three targets completed with box reached=%b and maximum white-concrete inventory=%d; expected exactly %d",
                    this.sawBox, this.maximumWhite, TARGETS);
        }
        if (ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem()) != 0) {
            return Verdict.fail("the build completed with surplus white concrete still carried: %d",
                    ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem()));
        }
        return Verdict.pass("the unaligned three-block shortfall was fetched without a speculative extra stack");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("missing=%d, boxReached=%b, maximumWhite=%d, carriedWhite=%d, player=%s",
                missingTargets(arena), this.sawBox, this.maximumWhite,
                ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem()), arena.ctx().playerFeet());
    }

    private static int missingTargets(TestArena arena) {
        int missing = 0;
        for (int i = 0; i < TARGETS; i++) {
            if (!arena.stateAt(BUILD_X + i, 1, 0).is(Blocks.WHITE_CONCRETE)) {
                missing++;
            }
        }
        return missing;
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[TARGETS][1][2];
        for (int x = 0; x < TARGETS; x++) {
            states[x][0][0] = Blocks.STONE.defaultBlockState();
            states[x][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        }
        return new StaticSchematic(states);
    }
}
