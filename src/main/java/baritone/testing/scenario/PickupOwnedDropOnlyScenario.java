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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A pre-existing cobblestone entity must not be claimed as the new stone-break drop. */
public final class PickupOwnedDropOnlyScenario extends AbstractMiningScenario {

    private static final int TARGET_X = 5;
    private static final int OLD_DROP_X = 5;
    /** Diagonally within the process's association radius, but outside incidental player pickup. */
    private static final int OLD_DROP_Z = 1;
    private UUID oldDrop;

    @Override
    public String name() {
        return "pickup-owned-drop-only";
    }

    @Override
    public String description() {
        return "Pick up a water-displaced fresh drop while leaving an older pickup-eligible drop alone";
    }

    @Override
    public int tickBudget() {
        return 20 * 60;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>(miningSettings());
        settings.put("pickupBlocks", true);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.command("kill @e[type=minecraft:item,distance=..64]");
        arena.fill(TARGET_X, 0, 0, TARGET_X + 6, 0, 0, "minecraft:water");
        arena.setBlock(TARGET_X, 1, 0, "minecraft:stone");
        stagePickupEligibleItem(arena, Items.COBBLESTONE, 1, OLD_DROP_X, 0, OLD_DROP_Z);
        arena.command("give @s minecraft:iron_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(TARGET_X, 1, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.WATER)
                && arena.stateAt(TARGET_X + 6, 0, 0).is(Blocks.WATER)
                && findItemAt(arena, Items.COBBLESTONE, arena.at(OLD_DROP_X, 0, OLD_DROP_Z)) != null
                && countPlayer(arena, Items.IRON_PICKAXE) == 1
                && countPlayer(arena, Items.COBBLESTONE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        ItemEntity old = findItemAt(arena, Items.COBBLESTONE, arena.at(OLD_DROP_X, 0, OLD_DROP_Z));
        this.oldDrop = old == null ? null : old.getUUID();
        arena.baritone().getMineProcess().mine(Blocks.STONE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean targetGone = !arena.stateAt(TARGET_X, 1, 0).is(Blocks.STONE);
        boolean oldAlive = this.oldDrop != null && itemEntityAlive(arena, this.oldDrop);
        int freshOwned = countPlayer(arena, Items.COBBLESTONE);
        if (targetGone && freshOwned == 1 && oldAlive) {
            return Verdict.pass("the fresh cobblestone was collected and the pre-existing entity remained");
        }
        if (targetGone && !oldAlive) {
            return Verdict.fail("the pre-existing cobblestone entity was claimed or lost");
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("owned-drop pickup did not complete: targetGone=%b, freshOwned=%d, oldAlive=%b",
                    targetGone, freshOwned, oldAlive);
        }
        return null;
    }
}
