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
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package baritone.testing.scenario;

import baritone.testing.TestArena;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** Portable command fixtures for container scenarios. */
final class ContainerFixture {

    private ContainerFixture() {}

    /** Place an empty box, then populate individual slots through the stable item command. */
    static void emptyBox(TestArena arena, int x, int y, int z) {
        arena.setBlock(x, y, z, "minecraft:shulker_box[facing=up]");
    }

    static void put(TestArena arena, int x, int y, int z, int slot, Item item, int count) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        put(arena, x, y, z, slot, id, count);
    }

    static void put(TestArena arena, int x, int y, int z, int slot, String item, int count) {
        var pos = arena.at(x, y, z);
        arena.command(String.format("item replace block %d %d %d container.%d with %s %d",
                pos.x, pos.y, pos.z, slot, item, count));
    }

    static boolean isBox(TestArena arena, int x, int y, int z) {
        return arena.stateAt(x, y, z).getBlock() instanceof ShulkerBoxBlock;
    }

}
