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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.testing.scenario;

import baritone.testing.TestArena;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;

/** All three ice variants are valid mine targets when the tool has Silk Touch. */
public final class MineIceVariantsScenario extends AbstractMiningScenario {

    private static final int[][] TARGETS = {{4, 0, 0}, {6, 0, 0}, {8, 0, 0}};

    @Override
    public String name() {
        return "mine-ice-variants";
    }

    @Override
    public String description() {
        return "Mine normal, packed and blue ice without rejecting a valid variant";
    }

    @Override
    public int tickBudget() {
        return 20 * 90;
    }

    @Override
    public Map<String, Object> settings() {
        return miningSettings();
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.setBlock(4, 0, 0, "minecraft:ice");
        arena.setBlock(6, 0, 0, "minecraft:packed_ice");
        arena.setBlock(8, 0, 0, "minecraft:blue_ice");
        arena.command("give @s minecraft:diamond_pickaxe[enchantments={\"minecraft:silk_touch\":1}] 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(4, 0, 0).is(Blocks.ICE)
                && arena.stateAt(6, 0, 0).is(Blocks.PACKED_ICE)
                && arena.stateAt(8, 0, 0).is(Blocks.BLUE_ICE)
                && countPlayer(arena, Items.DIAMOND_PICKAXE) == 1
                && playerHasEnchantment(arena, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH);
    }

    @Override
    public void start(TestArena arena) {
        arena.baritone().getMineProcess().mine(3, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int ice = arena.stateAt(4, 0, 0).is(Blocks.ICE) ? 1 : 0;
        int packed = arena.stateAt(6, 0, 0).is(Blocks.PACKED_ICE) ? 1 : 0;
        int blue = arena.stateAt(8, 0, 0).is(Blocks.BLUE_ICE) ? 1 : 0;
        int normalDrops = countPlayer(arena, Items.ICE);
        int packedDrops = countPlayer(arena, Items.PACKED_ICE);
        int blueDrops = countPlayer(arena, Items.BLUE_ICE);
        if (ice + packed + blue == 0 && normalDrops == 1 && packedDrops == 1 && blueDrops == 1
                && !mineActive(arena)) {
            return Verdict.pass("normal, packed and blue ice were all mined with Silk Touch");
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("ice variants incomplete: targets=%d/%d/%d, drops=%d/%d/%d, active=%b",
                    ice, packed, blue, normalDrops, packedDrops, blueDrops, mineActive(arena));
        }
        return null;
    }
}
