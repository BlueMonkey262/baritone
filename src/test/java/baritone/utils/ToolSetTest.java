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

package baritone.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@code itemSaver} durability predicate.
 * <p>
 * Only the arithmetic overload is covered: the {@code ItemStack} overload reads the live settings
 * and cannot be constructed without a Minecraft bootstrap, which is why the arithmetic was split
 * out in the first place.
 */
public class ToolSetTest {

    private static final int THRESHOLD = 10;

    @Test
    public void notDamageableIsNeverSpent() {
        // stacks that can't take damage report a max of 0, and must not be filtered out of tool
        // selection -- a bare hand is a legitimate choice and a block of dirt is not a spent tool
        assertFalse(ToolSet.isSpent(0, 0, THRESHOLD));
        assertFalse(ToolSet.isSpent(0, 1, THRESHOLD));
    }

    @Test
    public void freshToolIsNotSpent() {
        assertFalse(ToolSet.isSpent(0, 250, THRESHOLD));
    }

    @Test
    public void wornButUsableToolIsNotSpent() {
        // 11 durability left, one more than the threshold wants to keep
        assertFalse(ToolSet.isSpent(239, 250, THRESHOLD));
    }

    @Test
    public void toolAtExactlyTheThresholdIsSpent() {
        // exactly 10 left: the threshold is what we refuse to spend, so this is already too far
        assertTrue(ToolSet.isSpent(240, 250, THRESHOLD));
    }

    @Test
    public void nearlyBrokenToolIsSpent() {
        assertTrue(ToolSet.isSpent(249, 250, THRESHOLD));
    }

    @Test
    public void zeroThresholdStillProtectsTheLastUse() {
        // with no reserve requested, a tool is spent only once it has nothing left at all
        assertFalse(ToolSet.isSpent(248, 250, 0));
        assertTrue(ToolSet.isSpent(250, 250, 0));
    }

    @Test
    public void thresholdLargerThanDurabilityMakesEverythingSpent() {
        // a threshold above the item's total durability is a user error rather than a crash: the
        // tool simply never qualifies for use, which is visible immediately rather than silently
        assertTrue(ToolSet.isSpent(0, 59, 100));
    }
}
