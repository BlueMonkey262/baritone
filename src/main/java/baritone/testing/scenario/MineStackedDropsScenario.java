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
import net.minecraft.world.level.block.Blocks;

import java.util.Map;

/** A single snow block produces four snowballs; quantity is an item count, not a block count. */
public final class MineStackedDropsScenario extends AbstractMiningScenario {

    private static final int[][] TARGETS = {{4, 0, 0}};

    @Override
    public String name() {
        return "mine-stacked-drops";
    }

    @Override
    public String description() {
        return "Count all four snowballs from one snow block when satisfying the mine quantity";
    }

    @Override
    public int tickBudget() {
        return 20 * 45;
    }

    @Override
    public Map<String, Object> settings() {
        return miningSettings();
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.setBlock(4, 0, 0, "minecraft:snow_block");
        arena.command("give @s minecraft:iron_shovel 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && countBlocks(arena, Blocks.SNOW_BLOCK, TARGETS) == 1
                && countPlayer(arena, Items.IRON_SHOVEL) == 1
                && countPlayer(arena, Items.SNOWBALL) == 0;
    }

    @Override
    public void start(TestArena arena) {
        arena.baritone().getMineProcess().mine(4, Blocks.SNOW_BLOCK);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int snow = countBlocks(arena, Blocks.SNOW_BLOCK, TARGETS);
        int snowballs = countPlayer(arena, Items.SNOWBALL);
        if (snowballs > 4) {
            return Verdict.fail("stacked-drop accounting overshot: %d snowballs", snowballs);
        }
        if (snow == 0 && snowballs == 4 && !mineActive(arena)) {
            return Verdict.pass("one snow block produced four counted snowballs");
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("did not satisfy quantity four from one block: snow=%d, snowballs=%d, active=%b",
                    snow, snowballs, mineActive(arena));
        }
        return null;
    }
}
