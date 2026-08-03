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

/** A full inventory during a clear build should cause one real unload trip. */
public final class DumpSingleShulkerTripScenario extends AbstractShulkerDumpScenario {

    @Override
    public String name() {
        return "dump-single-shulker-trip";
    }

    @Override
    public String description() {
        return "Unload rubble into a registered shulker during a clear build, then finish the work";
    }

    @Override
    protected int[] boxXs() {
        return new int[]{2};
    }
}
