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

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PickupBlocksProcessTest {

    @Test
    public void excludesItemsThatExistedWhenTheBreakWasRecorded() {
        UUID preExisting = new UUID(0, 1);

        assertNull(PickupBlocksProcess.bindEligibleDrop(
                preExisting,
                true,
                Collections.singleton(preExisting),
                null
        ));
    }

    @Test
    public void excludesNewItemsOutsideTheMatchRadius() {
        assertNull(PickupBlocksProcess.bindEligibleDrop(
                new UUID(0, 2),
                false,
                Collections.emptySet(),
                null
        ));
    }

    @Test
    public void bindsFirstEligibleItemAndDoesNotReuseExpectation() {
        UUID firstDrop = new UUID(0, 3);
        UUID unrelatedLaterDrop = new UUID(0, 4);
        Set<UUID> noPreExistingItems = Collections.emptySet();

        UUID binding = PickupBlocksProcess.bindEligibleDrop(
                firstDrop,
                true,
                noPreExistingItems,
                null
        );
        assertEquals(firstDrop, binding);

        binding = PickupBlocksProcess.bindEligibleDrop(
                unrelatedLaterDrop,
                true,
                noPreExistingItems,
                binding
        );
        assertEquals(firstDrop, binding);

        binding = PickupBlocksProcess.bindEligibleDrop(
                firstDrop,
                false,
                noPreExistingItems,
                binding
        );
        assertEquals(firstDrop, binding);
    }
}
