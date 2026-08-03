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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** A substitute used by the active schematic is not junk during an unload. */
public final class DumpActiveSubstituteKeptScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int BUILD_X = 6;
    private static final int BUILD_BLOCKS = 2;

    private static final Item[] JUNK = {
            Items.RED_CONCRETE, Items.ORANGE_CONCRETE, Items.YELLOW_CONCRETE, Items.LIME_CONCRETE,
            Items.GREEN_CONCRETE, Items.CYAN_CONCRETE, Items.LIGHT_BLUE_CONCRETE, Items.BLUE_CONCRETE,
            Items.PURPLE_CONCRETE, Items.MAGENTA_CONCRETE, Items.PINK_CONCRETE, Items.BROWN_CONCRETE,
            Items.BLACK_CONCRETE, Items.GRAY_CONCRETE, Items.LIGHT_GRAY_CONCRETE, Items.WHITE_CONCRETE,
            Items.RED_WOOL, Items.ORANGE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL,
            Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL, Items.BLUE_WOOL,
            Items.PURPLE_WOOL, Items.MAGENTA_WOOL, Items.PINK_WOOL, Items.BROWN_WOOL,
            Items.BLACK_WOOL, Items.GRAY_WOOL
    };

    private boolean sawBox;
    private int initialJunk;

    @Override
    public String name() {
        return "dump-active-substitute-kept";
    }

    @Override
    public String description() {
        return "Keep a dirt substitute needed by a later target while unloading unrelated rubble";
    }

    @Override
    public int tickBudget() {
        return 20 * 180;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        Map<Block, java.util.List<Block>> substitutes = new HashMap<>();
        substitutes.put(Blocks.STONE, Arrays.asList(Blocks.DIRT));
        settings.put("buildSubstitutes", substitutes);
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockDumpJunk", false);
        settings.put("shulkerDump", true);
        // Thirty rubble slots plus one dirt stack leaves five free slots, so the dedicated unload
        // starts before the first placement and must still preserve the active substitute.
        settings.put("shulkerDumpWhenFreeSlotsBelow", 6);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        for (Item item : JUNK) {
            arena.command("give @s " + BuiltInRegistries.ITEM.getKey(item) + " 1");
        }
        arena.command("give @s minecraft:dirt 2");
        arena.setBlock(BOX_X, 0, BOX_Z, "minecraft:shulker_box[facing=up]");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(BUILD_X, 0, 0).isAir()
                && ScenarioInventory.countPlayer(arena, Items.DIRT) == 2
                && countJunk(arena) == JUNK.length;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the substitute unload box could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(box);
        this.initialJunk = countJunk(arena);
        BetterBlockPos target = arena.at(BUILD_X, 0, 0);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), schematic(), new Vec3i(target.x, target.y, target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        this.sawBox |= arena.ctx().playerFeet().distSqr(arena.at(BOX_X, 0, BOX_Z)) <= 9.0;
        int first = dirtTargets(arena);
        if (first == BUILD_BLOCKS && ScenarioInventory.countPlayer(arena, Items.DIRT) != 0) {
            return Verdict.fail("both substitute targets are placed, but dirt inventory accounting is inconsistent");
        }
        if (first != BUILD_BLOCKS) {
            if (elapsedTicks >= 20 * 12 && !arena.baritone().getBuilderProcess().isActive()) {
                return Verdict.fail("builder stopped with %d/%d dirt substitute targets placed; box reached=%b, junk reduced=%d",
                        first, BUILD_BLOCKS, this.sawBox, this.initialJunk - countJunk(arena));
            }
            return null;
        }
        int reduced = this.initialJunk - countJunk(arena);
        if (!this.sawBox || reduced <= 0) {
            return Verdict.fail("the substitute build completed without an unload trip: box reached=%b, junk reduced=%d",
                    this.sawBox, reduced);
        }
        return Verdict.pass("the active dirt substitute survived unloading and supplied both later build targets");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("targets=%d/%d,boxReached=%b,junk=%d/%d,dirt=%d,player=%s",
                dirtTargets(arena), BUILD_BLOCKS, this.sawBox, countJunk(arena), this.initialJunk,
                ScenarioInventory.countPlayer(arena, Items.DIRT), arena.ctx().playerFeet());
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[BUILD_BLOCKS][1][1];
        for (int x = 0; x < BUILD_BLOCKS; x++) {
            states[x][0][0] = Blocks.STONE.defaultBlockState();
        }
        return new StaticSchematic(states);
    }

    private static int dirtTargets(TestArena arena) {
        int placed = 0;
        for (int x = 0; x < BUILD_BLOCKS; x++) {
            if (arena.stateAt(BUILD_X + x, 0, 0).is(Blocks.DIRT)) {
                placed++;
            }
        }
        return placed;
    }

    private static int countJunk(TestArena arena) {
        int count = 0;
        for (Item item : JUNK) {
            count += ScenarioInventory.countPlayer(arena, item);
        }
        return count;
    }
}
