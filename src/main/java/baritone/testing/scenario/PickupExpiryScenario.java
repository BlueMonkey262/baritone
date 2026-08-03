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

/** A recorded break with no resulting item must not leave pickup pursuing an expired expectation. */
public final class PickupExpiryScenario extends AbstractMiningScenario {

    private static final int TARGET_X = 5;
    private final int oldDropZ = 1;
    private UUID oldDrop;

    @Override
    public String name() {
        return "pickup-expiry";
    }

    @Override
    public String description() {
        return "Expire a pickup expectation when the glass break produces no item drop";
    }

    @Override
    public int tickBudget() {
        return 20 * 15;
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
        arena.setBlock(TARGET_X, 0, 0, "minecraft:glass");
        stageProtectedItem(arena, Items.GLASS, 1, TARGET_X, 0, oldDropZ);
        arena.command("give @s minecraft:diamond_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.GLASS)
                && findItemAt(arena, Items.GLASS, arena.at(TARGET_X, 0, oldDropZ)) != null
                && countPlayer(arena, Items.DIAMOND_PICKAXE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        ItemEntity old = findItemAt(arena, Items.GLASS, arena.at(TARGET_X, 0, oldDropZ));
        this.oldDrop = old == null ? null : old.getUUID();
        arena.baritone().getMineProcess().mine(Blocks.GLASS);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean broken = !arena.stateAt(TARGET_X, 0, 0).is(Blocks.GLASS);
        boolean oldAlive = this.oldDrop != null && itemEntityAlive(arena, this.oldDrop);
        boolean pickupActive = arena.baritone().getPickupBlocksProcess().isActive();
        if (broken && elapsedTicks >= 20 * 7 && oldAlive && !pickupActive
                && countBlockEntities(arena, Items.GLASS) == 1) {
            return Verdict.pass("the no-drop break expired its expectation and left the old entity alone");
        }
        if (broken && !oldAlive) {
            return Verdict.fail("the pre-existing glass item was claimed while waiting for expiry");
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("pickup expectation did not expire: broken=%b, oldAlive=%b, pickupActive=%b, entities=%d",
                    broken, oldAlive, pickupActive, countBlockEntities(arena, Items.GLASS));
        }
        return null;
    }
}
