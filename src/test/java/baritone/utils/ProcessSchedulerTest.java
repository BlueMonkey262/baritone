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

package baritone.utils;

import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProcessSchedulerTest {

    @Test
    public void registrationResetsProcess() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess process = new FakeProcess("process", 1, false);

        scheduler.registerProcess(process);

        assertEquals(1, process.lostControlCalls);
    }

    @Test
    public void highestPriorityActiveProcessWins() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess low = registered(scheduler, "low", 1, false);
        FakeProcess high = registered(scheduler, "high", 2, false);
        low.active = true;
        high.active = true;

        scheduler.beginTick();
        PathingCommand result = scheduler.executeProcesses(false, true);

        assertSame(high.command, result);
        assertSame(high, scheduler.inControlThisTick());
        assertEquals(1, high.tickCalls);
        assertEquals(0, low.tickCalls);
    }

    @Test
    public void deferFallsThroughToNextProcess() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess low = registered(scheduler, "low", 1, false);
        FakeProcess high = registered(scheduler, "high", 2, true);
        high.command = command(PathingCommandType.DEFER);
        low.active = true;
        high.active = true;

        scheduler.beginTick();
        PathingCommand result = scheduler.executeProcesses(false, true);

        assertSame(low.command, result);
        assertEquals(1, high.tickCalls);
        assertEquals(1, low.tickCalls);
        assertSame(low, scheduler.inControlThisTick());
    }

    @Test
    public void temporaryWinnerPreservesInterruptedProcess() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess work = registered(scheduler, "work", 1, false);
        FakeProcess pause = registered(scheduler, "pause", 2, true);
        work.active = true;
        pause.active = true;

        scheduler.beginTick();
        scheduler.executeProcesses(false, true);

        assertEquals(0, work.lostControlCalls);
        assertTrue(work.active);
    }

    @Test
    public void nonTemporaryWinnerCancelsLowerPriorityProcesses() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess low = registered(scheduler, "low", 1, false);
        FakeProcess high = registered(scheduler, "high", 2, false);
        low.active = true;
        high.active = true;

        scheduler.beginTick();
        scheduler.executeProcesses(false, true);

        assertEquals(1, low.lostControlCalls);
        assertFalse(low.active);
    }

    @Test
    public void activeProcessReturningNullIsRejected() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess broken = registered(scheduler, "broken", 1, false);
        broken.active = true;
        broken.command = null;

        scheduler.beginTick();
        try {
            scheduler.executeProcesses(false, true);
            fail("Expected active null command to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("actively returned null"));
        }
    }

    @Test
    public void processMayDeactivateAndReturnNull() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess lower = registered(scheduler, "lower", 1, false);
        FakeProcess finishing = registered(scheduler, "finishing", 2, false);
        lower.active = true;
        finishing.active = true;
        finishing.deactivateOnTick = true;
        finishing.command = null;

        scheduler.beginTick();
        PathingCommand result = scheduler.executeProcesses(false, true);

        assertSame(lower.command, result);
        assertEquals(1, finishing.tickCalls);
    }

    @Test
    public void calcFailureIsReportedOnlyToLastController() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess last = registered(scheduler, "last", 2, true);
        FakeProcess next = registered(scheduler, "next", 1, false);
        last.active = true;
        next.active = true;

        scheduler.beginTick();
        scheduler.executeProcesses(false, true);
        assertSame(last, scheduler.inControlThisTick());

        last.command = command(PathingCommandType.DEFER);
        scheduler.beginTick();
        scheduler.executeProcesses(true, true);

        assertTrue(last.lastCalcFailed);
        assertFalse(next.lastCalcFailed);
        assertSame(next, scheduler.inControlThisTick());
    }

    @Test
    public void safeToCancelIsPassedToEveryTickedProcess() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess deferring = registered(scheduler, "deferring", 2, true);
        FakeProcess winner = registered(scheduler, "winner", 1, false);
        deferring.active = true;
        deferring.command = command(PathingCommandType.DEFER);
        winner.active = true;

        scheduler.beginTick();
        scheduler.executeProcesses(false, false);

        assertFalse(deferring.lastSafeToCancel);
        assertFalse(winner.lastSafeToCancel);
    }

    @Test
    public void newlyActiveProcessWinsEqualPriorityTie() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess existing = registered(scheduler, "existing", 1, true);
        FakeProcess newcomer = registered(scheduler, "newcomer", 1, true);
        existing.active = true;

        scheduler.beginTick();
        scheduler.executeProcesses(false, true);
        assertSame(existing, scheduler.inControlThisTick());

        newcomer.active = true;
        scheduler.beginTick();
        scheduler.executeProcesses(false, true);

        assertSame(newcomer, scheduler.inControlThisTick());
    }

    @Test
    public void laterRegisteredProcessWinsSimultaneousEqualPriorityTie() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess first = registered(scheduler, "first", 1, true);
        FakeProcess second = registered(scheduler, "second", 1, true);
        first.active = true;
        second.active = true;

        scheduler.beginTick();
        scheduler.executeProcesses(false, true);

        assertSame(second, scheduler.inControlThisTick());
    }

    @Test
    public void allProcessesMayDeferWithoutAnOwner() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess first = registered(scheduler, "first", 2, true);
        FakeProcess second = registered(scheduler, "second", 1, true);
        first.active = true;
        second.active = true;
        first.command = command(PathingCommandType.DEFER);
        second.command = command(PathingCommandType.DEFER);

        scheduler.beginTick();
        PathingCommand result = scheduler.executeProcesses(false, true);

        assertNull(result);
        assertNull(scheduler.inControlThisTick());
        assertEquals(1, first.tickCalls);
        assertEquals(1, second.tickCalls);
    }

    @Test
    public void pauseCommandConsumesCompletionTickBeforeHandback() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess work = registered(scheduler, "work", 1, false);
        FakeProcess helper = registered(scheduler, "helper", 2, true);
        work.active = true;
        helper.active = true;
        helper.deactivateOnTick = true;
        helper.command = command(PathingCommandType.REQUEST_PAUSE);

        scheduler.beginTick();
        PathingCommand completion = scheduler.executeProcesses(false, true);

        assertEquals(PathingCommandType.REQUEST_PAUSE, completion.commandType);
        assertSame(helper, scheduler.inControlThisTick());
        assertEquals(0, work.tickCalls);

        scheduler.beginTick();
        PathingCommand resumed = scheduler.executeProcesses(false, true);

        assertSame(work.command, resumed);
        assertSame(work, scheduler.inControlThisTick());
    }

    @Test
    public void shelterRestockBuilderHandshakeConsumesRestockCompletionTick() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess builder = registered(scheduler, "builder", -1, false);
        FakeProcess restock = registered(scheduler, "restock", 5.2, true);
        FakeProcess shelter = registered(scheduler, "shelter", 6.0, true);
        builder.active = true;
        restock.active = true;
        shelter.active = true;
        shelter.command = command(PathingCommandType.DEFER);
        restock.deactivateOnTick = true;
        restock.command = command(PathingCommandType.REQUEST_PAUSE);

        scheduler.beginTick();
        PathingCommand completion = scheduler.executeProcesses(false, true);

        assertEquals(PathingCommandType.REQUEST_PAUSE, completion.commandType);
        assertSame(restock, scheduler.inControlThisTick());
        assertEquals(1, shelter.tickCalls);
        assertEquals(1, restock.tickCalls);
        assertEquals(0, builder.tickCalls);
    }

    @Test
    public void processActivatedDuringCallbackWaitsUntilNextSchedulerPass() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess restock = registered(scheduler, "restock", 5.2, true);
        FakeProcess shelter = registered(scheduler, "shelter", 6.0, true);
        shelter.active = true;
        shelter.activateOnTick = restock;
        shelter.command = command(PathingCommandType.REQUEST_PAUSE);

        scheduler.beginTick();
        scheduler.executeProcesses(false, true);

        assertTrue(restock.active);
        assertEquals(0, restock.tickCalls);

        shelter.command = command(PathingCommandType.DEFER);
        scheduler.beginTick();
        scheduler.executeProcesses(false, true);

        assertEquals(1, restock.tickCalls);
        assertSame(restock, scheduler.inControlThisTick());
    }

    @Test
    public void cancelEverythingAllowsOnlyTemporaryProcessToRemainActive() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess normal = registered(scheduler, "normal", 1, false);
        FakeProcess temporary = registered(scheduler, "temporary", 2, true);
        normal.active = true;
        temporary.active = true;
        temporary.cancelOnLostControl = false;

        scheduler.cancelEverything();

        assertFalse(normal.active);
        assertTrue(temporary.active);
        assertNull(scheduler.inControlLastTick());
        assertNull(scheduler.inControlThisTick());
    }

    @Test
    public void cancelEverythingRejectsStubbornNonTemporaryProcess() {
        ProcessScheduler scheduler = new ProcessScheduler();
        FakeProcess stubborn = registered(scheduler, "stubborn", 1, false);
        stubborn.active = true;
        stubborn.cancelOnLostControl = false;

        try {
            scheduler.cancelEverything();
            fail("Expected stubborn process to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("stayed active"));
        }
    }

    private static FakeProcess registered(ProcessScheduler scheduler, String name,
                                          double priority, boolean temporary) {
        FakeProcess process = new FakeProcess(name, priority, temporary);
        scheduler.registerProcess(process);
        process.lostControlCalls = 0;
        return process;
    }

    private static PathingCommand command(PathingCommandType type) {
        return new PathingCommand(null, type);
    }

    private static final class FakeProcess implements IBaritoneProcess {

        private final String name;
        private final double priority;
        private final boolean temporary;
        private boolean active;
        private boolean cancelOnLostControl = true;
        private boolean deactivateOnTick;
        private FakeProcess activateOnTick;
        private PathingCommand command = command(PathingCommandType.REQUEST_PAUSE);
        private int tickCalls;
        private int lostControlCalls;
        private boolean lastCalcFailed;
        private boolean lastSafeToCancel;

        private FakeProcess(String name, double priority, boolean temporary) {
            this.name = name;
            this.priority = priority;
            this.temporary = temporary;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
            tickCalls++;
            lastCalcFailed = calcFailed;
            lastSafeToCancel = isSafeToCancel;
            if (deactivateOnTick) {
                active = false;
            }
            if (activateOnTick != null) {
                activateOnTick.active = true;
            }
            return command;
        }

        @Override
        public boolean isTemporary() {
            return temporary;
        }

        @Override
        public void onLostControl() {
            lostControlCalls++;
            if (cancelOnLostControl) {
                active = false;
            }
        }

        @Override
        public double priority() {
            return priority;
        }

        @Override
        public String displayName0() {
            return name;
        }
    }
}
