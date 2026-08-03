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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.testing.scenario;

import net.minecraft.world.item.Item;

/** Capacity in the index must outrank walking distance when selecting an unload box. */
public final class DumpPrefersCapacityScenario extends AbstractShulkerDumpScenario {

    @Override
    public String name() {
        return "dump-prefers-capacity";
    }

    @Override
    public String description() {
        return "Choose a farther empty shulker over a nearer nearly-full one for an unload trip";
    }

    @Override
    protected int[] boxXs() {
        return new int[]{4, 14};
    }

    @Override
    protected Item[] initialBoxItems(int index) {
        return index == 0 ? capacityItems() : new Item[0];
    }

    @Override
    protected boolean indexInitialBoxes() {
        return true;
    }

    @Override
    protected boolean requiresCapacityPreference() {
        return true;
    }
}
