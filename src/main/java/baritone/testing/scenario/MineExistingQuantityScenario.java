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

import baritone.testing.TestArena;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;

/** An already-satisfied quantity must cancel mining before an extra target is broken. */
public final class MineExistingQuantityScenario extends AbstractMiningScenario {

    private static final int[][] TARGETS = {{4, 0, -1}, {4, 0, 0}, {4, 0, 1}};
    private static final int REQUESTED = 2;

    @Override
    public String name() {
        return "mine-existing-quantity";
    }

    @Override
    public String description() {
        return "Stop a quantity-two mine immediately when two matching drops are already held";
    }

    @Override
    public int tickBudget() {
        return 20 * 50;
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
        arena.command("give @s minecraft:cobblestone " + REQUESTED);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && countBlocks(arena, Blocks.STONE, TARGETS) == TARGETS.length
                && countPlayer(arena, Items.IRON_PICKAXE) == 1
                && countPlayer(arena, Items.COBBLESTONE) == REQUESTED;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("starting quantity=%d with cobblestone already held=%d; target stones=%d",
                REQUESTED, countPlayer(arena, Items.COBBLESTONE), countBlocks(arena, Blocks.STONE, TARGETS));
        arena.baritone().getMineProcess().mine(REQUESTED, Blocks.STONE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int remaining = countBlocks(arena, Blocks.STONE, TARGETS);
        int cobblestone = countPlayer(arena, Items.COBBLESTONE);
        boolean active = mineActive(arena);
        arena.note("quantity evidence at t=%d: stones remaining=%d/%d, cobblestone=%d, mine active=%b, player=%s",
                elapsedTicks, remaining, TARGETS.length, cobblestone, active, arena.ctx().playerFeet());
        if (remaining != TARGETS.length || cobblestone != REQUESTED) {
            return Verdict.fail("the already-satisfied mine changed a target or drop count: stones=%d, cobblestone=%d",
                    remaining, cobblestone);
        }
        if (!active) {
            return Verdict.pass("the quantity request stopped with all target stones intact and the existing two drops held");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "stones remaining=" + countBlocks(arena, Blocks.STONE, TARGETS)
                + ", cobblestone=" + countPlayer(arena, Items.COBBLESTONE)
                + ", mine active=" + mineActive(arena);
    }
}
