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

/** The quantity argument counts drops, and must stop before a third target is broken. */
public final class MineExactQuantityScenario extends AbstractMiningScenario {

    private static final int[][] TARGETS = {{4, 0, -1}, {4, 0, 0}, {4, 0, 1}};

    @Override
    public String name() {
        return "mine-exact-quantity";
    }

    @Override
    public String description() {
        return "Mine exactly two stone drops from three targets and leave the third target intact";
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
        for (int[] target : TARGETS) {
            arena.setBlock(target[0], target[1], target[2], "minecraft:stone");
        }
        arena.command("give @s minecraft:iron_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && countBlocks(arena, Blocks.STONE, TARGETS) == TARGETS.length
                && countPlayer(arena, Items.IRON_PICKAXE) == 1
                && countPlayer(arena, Items.COBBLESTONE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        arena.baritone().getMineProcess().mine(2, Blocks.STONE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int remaining = countBlocks(arena, Blocks.STONE, TARGETS);
        int cobblestone = countPlayer(arena, Items.COBBLESTONE);
        if (remaining < 1 || cobblestone > 2) {
            return Verdict.fail("quantity overshot: %d stone targets remain and inventory has %d cobblestone",
                    remaining, cobblestone);
        }
        if (remaining == 1 && cobblestone == 2 && !mineActive(arena)) {
            return Verdict.pass("stopped at exactly two cobblestone drops with one stone target untouched");
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("did not stop at quantity two: %d stone targets remain, %d cobblestone, active=%b",
                    remaining, cobblestone, mineActive(arena));
        }
        return null;
    }
}
