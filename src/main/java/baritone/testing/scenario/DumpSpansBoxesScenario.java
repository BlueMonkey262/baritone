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

/** One unload trip should continue to the next box while rubble remains. */
public final class DumpSpansBoxesScenario extends AbstractShulkerDumpScenario {

    @Override
    public String name() {
        return "dump-spans-boxes";
    }

    @Override
    public String description() {
        return "Continue one unload trip across three nearly-full boxes while rubble remains";
    }

    @Override
    protected int[] boxXs() {
        return new int[]{2, 4, 6};
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
        return 3;
    }

    @Override
    protected boolean requiresAllBoxes() {
        return true;
    }
}
