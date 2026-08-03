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

/** The per-trip box cap should bound an unload without silently claiming all rubble was stored. */
public final class DumpMaxBoxLimitScenario extends AbstractShulkerDumpScenario {

    @Override
    public String name() {
        return "dump-max-box-limit";
    }

    @Override
    public String description() {
        return "Stop an unload trip at its configured one-box limit while leaving later rubble carried";
    }

    @Override
    protected int[] boxXs() {
        return new int[]{2, 14};
    }

    @Override
    protected Item[] initialBoxItems(int index) {
        return capacityItems();
    }

    @Override
    protected boolean indexInitialBoxes() {
        return true;
    }

    @Override
    protected int maxBoxesPerTrip() {
        return 1;
    }

    @Override
    protected boolean requiresBoxLimit() {
        return true;
    }
}
