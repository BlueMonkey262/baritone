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
import baritone.testing.TestScenario;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared arena setup and world-state readers for the mining scenarios. */
abstract class AbstractMiningScenario extends TestScenario {

    protected static final int ARENA_MIN_X = -4;
    protected static final int ARENA_MAX_X = 52;
    protected static final int ARENA_MIN_Z = -20;
    protected static final int ARENA_MAX_Z = 20;

    /** Lay down a floor and clear headroom using the harness command path. */
    protected final void stageMiningArena(TestArena arena) {
        arena.fill(ARENA_MIN_X, -3, ARENA_MIN_Z, ARENA_MAX_X, -1, ARENA_MAX_Z,
                "minecraft:obsidian");
        arena.fill(ARENA_MIN_X, 0, ARENA_MIN_Z, ARENA_MAX_X, 5, ARENA_MAX_Z,
                "minecraft:air");
        arena.command("clear @s");
        arena.teleport(0, 0, 0);
    }

    protected final boolean floorAndAirStaged(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.OBSIDIAN)
                && arena.stateAt(0, 0, 0).isAir();
    }

    protected final Map<String, Object> miningSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", true);
        settings.put("allowPlace", false);
        settings.put("allowInventory", false);
        settings.put("restockFromBoxes", false);
        settings.put("exploreForBlocks", false);
        settings.put("mineScanDroppedItems", false);
        return settings;
    }

    protected final int countBlocks(TestArena arena, Block block, int[][] positions) {
        int count = 0;
        for (int[] position : positions) {
            if (arena.stateAt(position[0], position[1], position[2]).is(block)) {
                count++;
            }
        }
        return count;
    }

    protected final int countPlayer(TestArena arena, Item item) {
        return arena.ctx().player().getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    protected final int itemDamage(TestArena arena, Item item) {
        return arena.ctx().player().getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getDamageValue)
                .findFirst()
                .orElse(-1);
    }

    protected final boolean playerHasEnchantment(TestArena arena, Item item, ResourceKey<Enchantment> wanted) {
        return arena.ctx().player().getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item))
                .anyMatch(stack -> stack.getEnchantments().keySet().stream()
                        .anyMatch(enchantment -> enchantment.is(wanted)));
    }

    protected final int countBlockEntities(TestArena arena, Item item) {
        return (int) arena.ctx().entitiesStream()
                .filter(entity -> entity instanceof ItemEntity)
                .map(entity -> (ItemEntity) entity)
                .filter(entity -> entity.getItem().is(item))
                .count();
    }

    protected final ItemEntity findItemAt(TestArena arena, Item item, BlockPos position) {
        return arena.ctx().entitiesStream()
                .filter(entity -> entity instanceof ItemEntity)
                .map(entity -> (ItemEntity) entity)
                .filter(entity -> entity.getItem().is(item))
                .filter(entity -> entity.position().distanceToSqr(position.getCenter()) <= 2.25)
                .findFirst()
                .orElse(null);
    }

    protected final ItemEntity findItem(TestArena arena, UUID id) {
        return arena.ctx().entitiesStream()
                .filter(entity -> entity instanceof ItemEntity)
                .filter(entity -> entity.getUUID().equals(id))
                .map(entity -> (ItemEntity) entity)
                .findFirst()
                .orElse(null);
    }

    protected final boolean itemMovedFrom(TestArena arena, Item item, BlockPos position) {
        return arena.ctx().entitiesStream()
                .filter(entity -> entity instanceof ItemEntity)
                .map(entity -> (ItemEntity) entity)
                .filter(entity -> entity.getItem().is(item))
                .anyMatch(entity -> entity.position().distanceToSqr(position.getCenter()) > 4.0);
    }

    /** Stage a non-player item with pickup disabled, for old-drop association controls. */
    protected final void stageProtectedItem(TestArena arena, Item item, int count, int x, int y, int z) {
        stageItem(arena, item, count, x, y, z, 32767);
    }

    /** Stage a non-player item that vanilla and Baritone are both allowed to pick up. */
    protected final void stagePickupEligibleItem(TestArena arena, Item item, int count,
                                                 int x, int y, int z) {
        stageItem(arena, item, count, x, y, z, 0);
    }

    private void stageItem(TestArena arena, Item item, int count, int x, int y, int z,
                           int pickupDelay) {
        BlockPos position = arena.at(x, y, z);
        arena.command(String.format(
                "summon minecraft:item %.1f %.1f %.1f "
                        + "{Item:{id:\"minecraft:%s\",count:%d},PickupDelay:%ds,Age:-32768s}",
                position.getX() + 0.5, position.getY() + 0.2, position.getZ() + 0.5,
                BuiltInRegistries.ITEM.getKey(item).getPath(), count, pickupDelay));
    }

    protected final boolean mineActive(TestArena arena) {
        return arena.baritone().getMineProcess().isActive();
    }

    protected final boolean itemEntityAlive(TestArena arena, UUID id) {
        Entity entity = findItem(arena, id);
        return entity != null && entity.isAlive();
    }
}
