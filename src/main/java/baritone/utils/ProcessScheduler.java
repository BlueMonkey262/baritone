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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Deterministic process arbitration, separated from the pathing side effects performed by
 * {@link PathingControlManager}.
 *
 * <p>This class deliberately knows nothing about Minecraft, {@code Baritone}, or path execution.
 * Keeping the scheduling rules here lets regression tests exercise priority, temporary-process
 * preemption, cancellation, and same-tick handoff without constructing a client.</p>
 */
final class ProcessScheduler {

    private final Set<IBaritoneProcess> processes = new LinkedHashSet<>();
    private final List<IBaritoneProcess> active = new ArrayList<>();
    private IBaritoneProcess inControlLastTick;
    private IBaritoneProcess inControlThisTick;

    void registerProcess(IBaritoneProcess process) {
        process.onLostControl();
        processes.add(process);
    }

    void beginTick() {
        inControlLastTick = inControlThisTick;
        inControlThisTick = null;
    }

    PathingCommand executeProcesses(boolean calcFailedLastTick, boolean isSafeToCancel) {
        return executeProcesses(() -> calcFailedLastTick, () -> isSafeToCancel);
    }

    PathingCommand executeProcesses(BooleanSupplier calcFailedLastTick, BooleanSupplier isSafeToCancel) {
        for (IBaritoneProcess process : processes) {
            if (process.isActive()) {
                if (!active.contains(process)) {
                    // Preserve the existing tie-break: a newly active process goes to the front.
                    active.add(0, process);
                }
            } else {
                active.remove(process);
            }
        }
        active.sort(Comparator.comparingDouble(IBaritoneProcess::priority).reversed());

        Iterator<IBaritoneProcess> iterator = active.iterator();
        while (iterator.hasNext()) {
            IBaritoneProcess process = iterator.next();
            PathingCommand result = process.onTick(
                    Objects.equals(process, inControlLastTick) && calcFailedLastTick.getAsBoolean(),
                    isSafeToCancel.getAsBoolean()
            );
            if (result == null) {
                if (process.isActive()) {
                    throw new IllegalStateException(process.displayName()
                            + " actively returned null PathingCommand");
                }
            } else if (result.commandType != PathingCommandType.DEFER) {
                inControlThisTick = process;
                if (!process.isTemporary()) {
                    iterator.forEachRemaining(IBaritoneProcess::onLostControl);
                }
                return result;
            }
        }
        return null;
    }

    void cancelEverything() {
        inControlLastTick = null;
        inControlThisTick = null;
        active.clear();
        for (IBaritoneProcess process : processes) {
            process.onLostControl();
            if (process.isActive() && !process.isTemporary()) {
                throw new IllegalStateException(process.displayName()
                        + " stayed active after being cancelled");
            }
        }
    }

    IBaritoneProcess inControlLastTick() {
        return inControlLastTick;
    }

    IBaritoneProcess inControlThisTick() {
        return inControlThisTick;
    }
}
