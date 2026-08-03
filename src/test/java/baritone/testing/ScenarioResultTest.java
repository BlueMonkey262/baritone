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

package baritone.testing;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScenarioResultTest {

    private static ScenarioResult of(ScenarioResult.Status status) {
        return new ScenarioResult("scenario", status, "message", 10, 100, Collections.emptyList());
    }

    @Test
    public void everyNonPassingOutcomeExceptSkippedCountsAgainstTheSuite() {
        assertFalse(of(ScenarioResult.Status.PASS).countsAsFailure());
        assertFalse(of(ScenarioResult.Status.SKIPPED).countsAsFailure());
        assertTrue(of(ScenarioResult.Status.FAIL).countsAsFailure());
        assertTrue(of(ScenarioResult.Status.TIMEOUT).countsAsFailure());
        assertTrue(of(ScenarioResult.Status.STAGING_FAILED).countsAsFailure());
        // A harness that threw cannot vouch for its own verdicts, so this must not read as green.
        assertTrue(of(ScenarioResult.Status.ERROR).countsAsFailure());
    }

    @Test
    public void notesAreCopiedSoLaterArenaWritesCannotChangeARecordedResult() {
        java.util.List<String> live = new java.util.ArrayList<>(Arrays.asList("first"));
        ScenarioResult result = new ScenarioResult("s", ScenarioResult.Status.PASS, "m", 1, 2, live);
        live.add("second");
        assertEquals(Collections.singletonList("first"), result.getNotes());
    }
}
