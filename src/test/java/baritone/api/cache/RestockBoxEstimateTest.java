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
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.api.cache;

import baritone.api.utils.BetterBlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RestockBoxEstimateTest {

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void emptyContentsUseNoSlots() {
        IRestockBox box = box(Collections.emptyMap());

        assertEquals(0, box.estimatedUsedSlots());
        assertEquals(IRestockBox.SHULKER_SLOTS, box.estimatedFreeSlots());
    }

    @Test
    public void mixedStackSizesRoundEachItemUpIndependently() {
        Map<Item, Integer> contents = new LinkedHashMap<>();
        contents.put(Items.STONE, 65);
        contents.put(Items.ENDER_PEARL, 17);
        contents.put(Items.DIAMOND_SWORD, 2);

        IRestockBox box = box(contents);

        assertEquals(6, box.estimatedUsedSlots());
        assertEquals(21, box.estimatedFreeSlots());
    }

    @Test
    public void nonPositiveCountsDoNotUseSlots() {
        Map<Item, Integer> contents = new LinkedHashMap<>();
        contents.put(Items.STONE, 0);
        contents.put(Items.ENDER_PEARL, -1);

        IRestockBox box = box(contents);

        assertEquals(0, box.estimatedUsedSlots());
        assertEquals(IRestockBox.SHULKER_SLOTS, box.estimatedFreeSlots());
    }

    @Test
    public void estimatesSaturateAtShulkerCapacity() {
        IRestockBox box = box(Collections.singletonMap(Items.DIAMOND_SWORD, 28));

        assertEquals(IRestockBox.SHULKER_SLOTS, box.estimatedUsedSlots());
        assertEquals(0, box.estimatedFreeSlots());
    }

    @Test
    public void maximumItemCountCannotOverflowSlotRounding() {
        IRestockBox box = box(Collections.singletonMap(Items.STONE, Integer.MAX_VALUE));

        assertEquals(IRestockBox.SHULKER_SLOTS, box.estimatedUsedSlots());
        assertEquals(0, box.estimatedFreeSlots());
    }

    private static IRestockBox box(Map<Item, Integer> contents) {
        return new IRestockBox() {
            @Override
            public BetterBlockPos getLocation() {
                return null;
            }

            @Override
            public long getCreationTimestamp() {
                return 0;
            }

            @Override
            public long getLastIndexed() {
                return 0;
            }

            @Override
            public Map<Item, Integer> getContents() {
                return contents;
            }

            @Override
            public boolean isMissing() {
                return false;
            }
        };
    }
}
