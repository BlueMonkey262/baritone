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

package baritone.api.cache;

import baritone.api.utils.BetterBlockPos;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * A shulker box that has been registered with {@code #addbox} as a source of build materials.
 * <p>
 * The item index is a <i>hint</i> only. It records what was in the box the last time we actually
 * looked inside it, so that the restock logic can pick a plausible box without opening every one.
 * Anything acted upon is always re-verified against the live container contents after opening,
 * because the box may have been emptied by the player or another process in the meantime.
 *
 * @see IRestockBoxCollection
 */
public interface IRestockBox {

    /**
     * @return The position of the shulker box
     */
    BetterBlockPos getLocation();

    /**
     * @return When this box was registered, in milliseconds since epoch
     */
    long getCreationTimestamp();

    /**
     * @return When the contents were last actually observed, in milliseconds since epoch, or
     * {@code 0} if the box has never been successfully opened
     */
    long getLastIndexed();

    /**
     * @return {@code true} if this box has never been successfully opened and indexed
     */
    default boolean isUnindexed() {
        return getLastIndexed() == 0;
    }

    /**
     * @return The last observed contents, as item to total count across all slots
     */
    Map<Item, Integer> getContents();

    /**
     * @return The last observed count of the given item, or {@code 0}
     */
    default int countOf(Item item) {
        return getContents().getOrDefault(item, 0);
    }

    /**
     * Whether this box was observed to be absent (broken or moved) at its registered position.
     * <p>
     * This is only ever set when the containing chunk was actually loaded, so that an unloaded
     * chunk is never mistaken for a broken box. Flagged boxes are skipped during selection but
     * deliberately <i>not</i> deleted -- removing them is a manual decision, made with
     * {@code #removebox}.
     *
     * @return {@code true} if the box is believed to no longer exist
     */
    boolean isMissing();
}
