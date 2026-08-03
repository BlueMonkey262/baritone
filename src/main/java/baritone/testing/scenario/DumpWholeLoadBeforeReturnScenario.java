/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
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

/** A capable unload box must receive the whole eligible load before work resumes. */
public final class DumpWholeLoadBeforeReturnScenario extends AbstractShulkerDumpScenario {

    @Override
    public String name() {
        return "dump-whole-load-before-return";
    }

    @Override
    public String description() {
        return "Empty all eligible rubble into one capable box before returning to the clear job";
    }

    @Override
    protected int[] boxXs() {
        return new int[]{2};
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        Verdict verdict = super.poll(arena, elapsedTicks);
        if (verdict == null || !verdict.pass) {
            return verdict;
        }
        int carried = countCarriedRubble(arena);
        int boxed = 0;
        for (var item : DUMP_ITEMS) {
            boxed += ScenarioInventory.countContainer(arena, 2, 0, 4, item);
        }
        arena.note("whole-load evidence at t=%d: eligible rubble carried=%d, boxed=%d, targets remaining=0",
                elapsedTicks, carried, boxed);
        return carried == 0
                ? Verdict.pass("the unload returned only after emptying the eligible rubble load")
                : Verdict.fail("the clear resumed while %d eligible rubble items remained carried", carried);
    }
}
