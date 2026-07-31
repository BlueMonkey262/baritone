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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.api.process;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Fetches build materials from shulker boxes registered with {@code #addbox}.
 * <p>
 * This is driven by {@link IBuilderProcess}: when the builder finds it has nothing it can place or
 * break, it offers the list of materials it is short of. If a registered box plausibly holds one
 * of them, this process takes temporary control, walks over, opens the box with a genuine
 * right-click, takes what it needs, and hands control back.
 */
public interface IRestockProcess extends IBaritoneProcess {

    /**
     * Asks this process to go and fetch one of the given materials.
     * <p>
     * Called by the builder at the point where it would otherwise give up. If this returns
     * {@code true} the process has become active and will take control on the next tick.
     *
     * @param missing      The block states the builder is short of, mapped to how many it wants
     * @param worthKeeping Whether an inventory stack belongs to the work being done
     * @return {@code true} if a restock run was started
     */
    boolean requestRestock(Map<BlockState, Integer> missing, Predicate<ItemStack> worthKeeping);

    /**
     * Walks to every registered box whose contents we've never actually observed, opens it, and
     * records what's inside.
     * <p>
     * Done before a build so that material lookups are decided from real contents rather than
     * guesses, which avoids walking to a box speculatively only to find it useless.
     *
     * @param includeAlreadyIndexed {@code true} to re-check every box, not just unindexed ones
     * @param worthKeeping           Whether an inventory stack belongs to the work being done
     * @return {@code true} if an indexing run was started; {@code false} if there was nothing to do
     */
    boolean requestIndexing(boolean includeAlreadyIndexed, Predicate<ItemStack> worthKeeping);

    /**
     * Asks this process to go and empty the inventory into a registered box.
     * <p>
     * The mirror image of {@link #requestRestock}: called when a process has run out of room rather
     * than out of materials. The caller supplies the rule for recognising its useful output, since
     * a builder cares about its schematic while a miner cares about the drops from its target
     * blocks.
     *
     * @param worthKeeping Whether an inventory stack belongs to the work being done
     * @return {@code true} if a deposit run was started
     */
    default boolean requestDeposit(Predicate<ItemStack> worthKeeping) {
        return requestDeposit(worthKeeping, true);
    }

    /**
     * As {@link #requestDeposit(Predicate)}, but able to decline without giving up.
     * <p>
     * The {@link #isDepositImpossible} latch exists so that a process asking on every tick, because
     * it is full and cannot continue, stops asking once the answer is hopeless. That reasoning only
     * holds for a caller that is actually out of room: "nothing worth unloading right now" means
     * something quite different when there are still twenty free slots, and latching on it would
     * stop the build unloading for the rest of the job.
     *
     * @param worthKeeping Whether an inventory stack belongs to the work being done
     * @param latchIfHopeless {@code false} for a speculative ask, which should leave
     *                        {@link #isDepositImpossible} alone when it declines
     * @return {@code true} if a deposit run was started
     */
    default boolean requestDeposit(Predicate<ItemStack> worthKeeping, boolean latchIfHopeless) {
        /*
         * This overload was added after the original one-argument API. Declining is the only safe
         * compatibility behavior for an older implementation: delegating back to the one-argument
         * default would recurse, while latching or starting work on its behalf would invent state
         * that implementation does not have. Implementations that understand the latch override
         * this method; the built-in RestockProcess does.
         */
        return false;
    }

    /**
     * Whether we have already concluded that there is nowhere to unload to.
     * <p>
     * Set when a deposit run visits every candidate box and manages to deposit nothing at all,
     * because every box is full or everything we're carrying is worth keeping. The calling process
     * checks this so that a hopeless situation doesn't turn into a walk to the same full boxes on
     * every subsequent tick. Cleared alongside {@link #clearUnobtainable}.
     *
     * @return {@code true} if unloading has been given up on for the current piece of work
     */
    boolean isDepositImpossible();

    /**
     * Whether we have already concluded that no registered box can supply this material.
     * <p>
     * The builder uses this to permanently skip positions it can never fill, so that it keeps
     * building everything else instead of stalling, and finishes cleanly when only unobtainable
     * work is left.
     *
     * @param state The desired block state
     * @return {@code true} if this material has been given up on for the current build
     */
    boolean isUnobtainable(BlockState state);

    /**
     * Clears the set of materials given up on, and the {@link #isDepositImpossible} flag. Called
     * when a new build or mine starts, and when the player registers or re-indexes a box, since
     * either means a previously hopeless material -- or a previously full box -- may now be
     * available.
     */
    void clearUnobtainable();
}
