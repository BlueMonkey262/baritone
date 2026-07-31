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

package baritone.process;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RestockProcessTest {

    @Test
    public void fetchTargetUsesTheItemsOwnStackSize() {
        assertEquals(10, RestockProcess.fetchTarget(7, 3, 1));
        assertEquals(55, RestockProcess.fetchTarget(7, 3, 16));
        assertEquals(103, RestockProcess.fetchTarget(7, 3, 32));
        assertEquals(199, RestockProcess.fetchTarget(7, 3, 64));
    }

    @Test
    public void fetchTargetTreatsNegativeInputsAsZero() {
        assertEquals(64, RestockProcess.fetchTarget(-5, 1, 64));
        assertEquals(5, RestockProcess.fetchTarget(5, -1, 64));
        assertEquals(7, RestockProcess.fetchTarget(5, 2, -64));
        assertEquals(0, RestockProcess.fetchTarget(-1, -1, -1));
    }

    @Test
    public void fetchTargetSaturatesAtTheIntegerLimit() {
        assertEquals(
                Integer.MAX_VALUE,
                RestockProcess.fetchTarget(Integer.MAX_VALUE - 63, 1, 64)
        );
        assertEquals(
                Integer.MAX_VALUE,
                RestockProcess.fetchTarget(0, Integer.MAX_VALUE, Integer.MAX_VALUE)
        );
    }

    @Test
    public void fetchTargetPreservesTheLargestUnsaturatedResult() {
        assertEquals(
                Integer.MAX_VALUE - 1,
                RestockProcess.fetchTarget(Integer.MAX_VALUE - 65, 1, 64)
        );
    }
}
