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

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the slot estimate behind {@link IRestockBox#estimatedUsedSlots()}.
 * <p>
 * These deliberately go through the static overload with a supplied stack size rather than through
 * the {@code default} method. Asking a real {@code Item} for its stack size needs bound item
 * components, i.e. a Minecraft bootstrap this source set cannot perform -- three of these
 * assertions used to fail for exactly that reason while testing nothing about Minecraft. The
 * rounding, saturation and overflow behaviour is the part worth pinning down, and none of it needs
 * a game.
 */
public class RestockBoxEstimateTest {

    /** Stand-ins for real items. The names carry the stack size so each case reads at a glance. */
    private static final String STACKS_TO_64 = "stacks-to-64";
    private static final String STACKS_TO_16 = "stacks-to-16";
    private static final String UNSTACKABLE = "unstackable";

    private static final ToIntFunction<String> MAX_STACK_SIZE = item -> {
        switch (item) {
            case STACKS_TO_64:
                return 64;
            case STACKS_TO_16:
                return 16;
            case UNSTACKABLE:
                return 1;
            default:
                throw new IllegalArgumentException(item);
        }
    };

    private static int usedSlots(Map<String, Integer> contents) {
        return IRestockBox.estimatedUsedSlots(contents, MAX_STACK_SIZE);
    }

    private static int freeSlots(Map<String, Integer> contents) {
        return IRestockBox.SHULKER_SLOTS - usedSlots(contents);
    }

    @Test
    public void emptyContentsUseNoSlots() {
        Map<String, Integer> contents = Collections.emptyMap();

        assertEquals(0, usedSlots(contents));
        assertEquals(IRestockBox.SHULKER_SLOTS, freeSlots(contents));
    }

    @Test
    public void mixedStackSizesRoundEachItemUpIndependently() {
        Map<String, Integer> contents = new LinkedHashMap<>();
        contents.put(STACKS_TO_64, 65);  // 2 slots: one full, one holding a single item
        contents.put(STACKS_TO_16, 17);  // 2 slots, the same shape at a different stack size
        contents.put(UNSTACKABLE, 2);    // 2 slots, one each

        assertEquals(6, usedSlots(contents));
        assertEquals(21, freeSlots(contents));
    }

    @Test
    public void nonPositiveCountsDoNotUseSlots() {
        Map<String, Integer> contents = new LinkedHashMap<>();
        contents.put(STACKS_TO_64, 0);
        contents.put(STACKS_TO_16, -1);

        assertEquals(0, usedSlots(contents));
        assertEquals(IRestockBox.SHULKER_SLOTS, freeSlots(contents));
    }

    @Test
    public void estimatesSaturateAtShulkerCapacity() {
        // 28 unstackable items want 28 slots; a shulker has 27
        Map<String, Integer> contents = Collections.singletonMap(UNSTACKABLE, 28);

        assertEquals(IRestockBox.SHULKER_SLOTS, usedSlots(contents));
        assertEquals(0, freeSlots(contents));
    }

    @Test
    public void maximumItemCountCannotOverflowSlotRounding() {
        // count + maxStack - 1 overflows int here. If that arithmetic is not widened to long it
        // goes negative and a box holding the maximum possible count reports as empty -- the worst
        // direction for this estimate to be wrong in, since it invites an unload trip to a box with
        // no room.
        Map<String, Integer> contents = Collections.singletonMap(STACKS_TO_64, Integer.MAX_VALUE);

        assertEquals(IRestockBox.SHULKER_SLOTS, usedSlots(contents));
        assertEquals(0, freeSlots(contents));
    }

    @Test
    public void aStackSizeBelowOneIsTreatedAsOne() {
        // A nonsensical stack size must not divide by zero; over-estimating is the safe direction.
        Map<String, Integer> contents = Collections.singletonMap("broken", 5);

        assertEquals(5, IRestockBox.estimatedUsedSlots(contents, item -> 0));
    }
}
