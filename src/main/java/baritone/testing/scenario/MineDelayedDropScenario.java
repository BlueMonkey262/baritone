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

/** A water-displaced drop must remain pending before a quantity-one mine can complete. */
public final class MineDelayedDropScenario extends AbstractMiningScenario {

    private static final int TARGET_X = 4;
    private boolean observedPendingPickup;

    @Override
    public String name() {
        return "mine-delayed-drop";
    }

    @Override
    public String description() {
        return "Observe a pending water-displaced stone drop before completing a quantity-one mine";
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
        arena.command("kill @e[type=minecraft:item,distance=..64]");
        arena.fill(TARGET_X, 0, 0, TARGET_X + 10, 0, 0, "minecraft:water");
        arena.setBlock(TARGET_X, 1, 0, "minecraft:stone");
        arena.command("give @s minecraft:iron_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(TARGET_X, 1, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.WATER)
                && arena.stateAt(TARGET_X + 10, 0, 0).is(Blocks.WATER)
                && countBlockEntities(arena, Items.COBBLESTONE) == 0
                && countPlayer(arena, Items.COBBLESTONE) == 0
                && countPlayer(arena, Items.IRON_PICKAXE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        this.observedPendingPickup = false;
        arena.baritone().getMineProcess().mine(1, Blocks.STONE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean targetIntact = arena.stateAt(TARGET_X, 1, 0).is(Blocks.STONE);
        int cobblestone = countPlayer(arena, Items.COBBLESTONE);
        int dropsInWorld = countBlockEntities(arena, Items.COBBLESTONE);
        boolean pickupActive = arena.baritone().getPickupBlocksProcess().isActive();
        if (!targetIntact && cobblestone == 0 && dropsInWorld >= 1 && pickupActive) {
            this.observedPendingPickup = true;
        }
        if (!targetIntact && this.observedPendingPickup && cobblestone >= 1 && !mineActive(arena)) {
            return Verdict.pass("the water-displaced cobblestone was observed pending before mine completion");
        }
        if (!targetIntact && elapsedTicks >= 20 * 20 && !this.observedPendingPickup) {
            return Verdict.fail("target broke without an observed pending pickup: cobblestone=%d, drops=%d, pickupActive=%b",
                    cobblestone, dropsInWorld, pickupActive);
        }
        if (!targetIntact && elapsedTicks >= 20 * 20) {
            return Verdict.fail("target broke but the pending delayed drop was not collected: cobblestone=%d, drops=%d, active=%b",
                    cobblestone, dropsInWorld, mineActive(arena));
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("did not mine the water-displaced target: targetIntact=%b, cobblestone=%d, pending=%b",
                    targetIntact, cobblestone, this.observedPendingPickup);
        }
        return null;
    }
}
