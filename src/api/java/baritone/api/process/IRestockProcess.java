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

import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

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
     * @param missing The block states the builder is short of, mapped to how many it wants
     * @return {@code true} if a restock run was started
     */
    boolean requestRestock(Map<BlockState, Integer> missing);

    /**
     * Walks to every registered box whose contents we've never actually observed, opens it, and
     * records what's inside.
     * <p>
     * Done before a build so that material lookups are decided from real contents rather than
     * guesses, which avoids walking to a box speculatively only to find it useless.
     *
     * @param includeAlreadyIndexed {@code true} to re-check every box, not just unindexed ones
     * @return {@code true} if an indexing run was started; {@code false} if there was nothing to do
     */
    boolean requestIndexing(boolean includeAlreadyIndexed);

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
     * Clears the set of materials given up on. Called when a new build starts, and when the player
     * registers or re-indexes a box, since either means a previously hopeless material may now be
     * available.
     */
    void clearUnobtainable();
}
