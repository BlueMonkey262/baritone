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

import baritone.testing.TestArena;


/** Zero configured throwaway reserves makes every eligible rubble stack unloadable. */
public final class DumpKeepThrowawayZeroScenario extends AbstractShulkerDumpScenario {

    @Override
    public String name() {
        return "dump-keep-throwaway-zero";
    }

    @Override
    public String description() {
        return "Unload all eligible rubble when shulkerDumpKeepThrowawayStacks is zero";
    }

    @Override
    protected int[] boxXs() {
        return new int[]{2};
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        Verdict verdict = super.poll(arena, elapsedTicks);
        if (verdict != null && verdict.pass && countCarriedRubble(arena) != 0) {
            return Verdict.fail("the zero-reserve unload completed with %d eligible rubble items still carried",
                    countCarriedRubble(arena));
        }
        return verdict;
    }
}
