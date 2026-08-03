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

import java.util.HashMap;
import java.util.Map;

/** A water-displaced drop must be collected before a quantity-one mine is considered complete. */
public final class MineDelayedDropScenario extends AbstractMiningScenario {

    private static final int TARGET_X = 4;

    @Override
    public String name() {
        return "mine-delayed-drop";
    }

    @Override
    public String description() {
        return "Wait for a stone drop displaced by water before completing a quantity-one mine";
    }

    @Override
    public int tickBudget() {
        return 20 * 60;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>(miningSettings());
        settings.put("pickupBlocks", true);
        settings.put("mineScanDroppedItems", true);
        settings.put("mineDropLoiterDurationMSThanksLouca", 1000L);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.fill(TARGET_X, 0, 0, TARGET_X + 6, 0, 0, "minecraft:water");
        arena.setBlock(TARGET_X, 1, 0, "minecraft:stone");
        arena.command("give @s minecraft:iron_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(TARGET_X, 1, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.WATER)
                && countPlayer(arena, Items.IRON_PICKAXE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.baritone().getMineProcess().mine(1, Blocks.STONE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean targetIntact = arena.stateAt(TARGET_X, 1, 0).is(Blocks.STONE);
        int cobblestone = countPlayer(arena, Items.COBBLESTONE);
        if (!targetIntact && cobblestone >= 1) {
            return Verdict.pass("completed only after the water-displaced cobblestone reached the inventory");
        }
        if (!targetIntact && elapsedTicks >= 20 * 20) {
            return Verdict.fail("target broke but the delayed drop was not collected: cobblestone=%d, active=%b",
                    cobblestone, mineActive(arena));
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("did not mine the water-displaced target: targetIntact=%b, cobblestone=%d",
                    targetIntact, cobblestone);
        }
        return null;
    }
}
