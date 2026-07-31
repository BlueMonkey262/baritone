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

import java.util.Collections;
import java.util.Map;

/**
 * One end-to-end test: stage an arena, turn Baritone loose on it in survival, decide whether what
 * happened was right.
 * <p>
 * The lifecycle is split across ticks rather than written as a blocking sequence, because there is
 * no thread to block. Everything here runs on the client thread, and a scenario that waited in
 * place for a build to finish would stop the game it is testing.
 * <p>
 * <b>Verdicts come from the world, not from chat.</b> Baritone says "Done building" when it has
 * nothing left it can do, which includes having given up; it logs nothing at all when a path
 * quietly stops making progress. A scenario that scrapes chat is testing the log messages. Read the
 * blocks, the inventory and the player's position instead -- those are the things a user would
 * actually complain about.
 */
public abstract class TestScenario {

    /** Stable identifier, used by {@code #testing <name>} and as the key in the JSON report. */
    public abstract String name();

    /** One line on what the scenario proves, for the report. */
    public abstract String description();

    /**
     * How long the scenario may run before it is called a timeout, in ticks (20 = 1s).
     * <p>
     * Generous is correct here. A budget tight enough to catch a slow run is tight enough to fire
     * on a chunk load, and a suite that cries wolf gets ignored.
     */
    public int tickBudget() {
        return 20 * 120;
    }

    /**
     * Settings to apply for this scenario, by field name. Restored afterwards.
     * <p>
     * Named rather than typed because the harness restores whatever it finds; a scenario that
     * needs {@code allowBreak} off should not also have to remember to put it back.
     */
    public Map<String, Object> settings() {
        return Collections.emptyMap();
    }

    /**
     * Queue the commands that build the arena. Runs in creative.
     * <p>
     * Nothing has been sent yet when this returns -- the harness drains the queue over the ticks
     * that follow, then waits for {@link #stagingComplete} before believing any of it.
     */
    public abstract void stage(TestArena arena);

    /**
     * Whether the staged arena has actually appeared on the client.
     * <p>
     * This is not paranoia. Commands go to the server and the resulting block changes come back
     * asynchronously, so for some number of ticks after staging the client still sees the old
     * world. A scenario that starts before its arena exists fails in a way that looks like a
     * Baritone bug and is not one.
     */
    public abstract boolean stagingComplete(TestArena arena);

    /**
     * Kick off the actual work. Runs in survival, with the player already teleported to the arena.
     */
    public abstract void start(TestArena arena);

    /**
     * Called every tick while the scenario runs. Return {@code null} to keep going, or a verdict to
     * finish. Not returning a verdict by {@link #tickBudget} is a timeout.
     */
    public abstract Verdict poll(TestArena arena, int elapsedTicks);

    /**
     * Called once the scenario is over however it ended, before the arena is abandoned. The harness
     * has already cancelled all Baritone activity by this point; override only if there is
     * scenario-specific state to unwind.
     */
    public void teardown(TestArena arena) {}

    /**
     * What to say when the tick budget runs out. Overriding this is how a scenario turns a bare
     * "timed out" into "timed out with 12 of 48 blocks placed, builder still active".
     */
    public String timeoutDiagnosis(TestArena arena) {
        return "no further detail";
    }

    /** A scenario's answer: pass, or fail with a reason. */
    public static final class Verdict {

        public final boolean pass;
        public final String message;

        private Verdict(boolean pass, String message) {
            this.pass = pass;
            this.message = message;
        }

        public static Verdict pass(String message) {
            return new Verdict(true, message);
        }

        public static Verdict fail(String message) {
            return new Verdict(false, message);
        }

        public static Verdict fail(String format, Object... args) {
            return new Verdict(false, String.format(format, args));
        }
    }
}
