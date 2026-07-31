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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of one scenario, as it will appear in the report.
 * <p>
 * Notes are as important as the verdict. A scenario that fails tells you almost nothing on its own
 * -- "the build did not finish" is true of a stuck builder, a missing material, a failed staging
 * command and a pathfinder that never got there. The notes are where the scenario records what it
 * observed along the way, so a failure in the report is diagnosable without re-running it.
 */
public final class ScenarioResult {

    public enum Status {
        /** Every assertion held. */
        PASS,
        /** The scenario ran to completion and an assertion did not hold. */
        FAIL,
        /** The scenario did not finish within its tick budget. */
        TIMEOUT,
        /** Setting the arena up did not work, so the scenario never really ran. */
        STAGING_FAILED,
        /** The harness itself threw. Distinct from FAIL: it says nothing about Baritone. */
        ERROR,
        /** Not run. */
        SKIPPED
    }

    public final String name;
    public final Status status;
    public final String message;
    public final int ticks;
    public final int tickBudget;
    private final List<String> notes;

    ScenarioResult(String name, Status status, String message, int ticks, int tickBudget, List<String> notes) {
        this.name = name;
        this.status = status;
        this.message = message;
        this.ticks = ticks;
        this.tickBudget = tickBudget;
        this.notes = new ArrayList<>(notes);
    }

    public List<String> getNotes() {
        return Collections.unmodifiableList(this.notes);
    }

    public boolean isPass() {
        return this.status == Status.PASS;
    }

    /**
     * @return {@code true} if this result should count against the suite. A skipped scenario should
     * not turn the suite red, but everything else should -- including {@link Status#ERROR}, because
     * a harness that throws is a harness whose other verdicts cannot be trusted either.
     */
    public boolean countsAsFailure() {
        return this.status != Status.PASS && this.status != Status.SKIPPED;
    }
}
