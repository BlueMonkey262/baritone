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
     * How many item slots a shulker box has.
     */
    int SHULKER_SLOTS = 27;

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
     * Roughly how many of the box's slots were in use when we last looked, assuming every item is
     * packed into as few stacks as it will go.
     * <p>
     * This is an estimate and not a measurement. {@link #getContents()} records a total count per
     * item with no slot layout, so a box holding the same item spread across several partial stacks
     * reads as using fewer slots than it really does. In the same way this cannot see the headroom
     * left in a partial stack. It is a <i>hint</i> for choosing which box to walk to, in exactly the
     * sense the class javadoc describes; the live container decides what actually happens on
     * arrival.
     *
     * @return An estimate between {@code 0} and {@link #SHULKER_SLOTS}
     */
    default int estimatedUsedSlots() {
        return estimatedUsedSlots(getContents(), Item::getMaxStackSize);
    }

    /**
     * The arithmetic behind {@link #estimatedUsedSlots()}, with the stack size supplied rather than
     * read off the item.
     * <p>
     * Split out so it can be tested. Asking an {@link Item} for its stack size needs bound item
     * components, which means a full Minecraft bootstrap; the rounding, saturation and overflow
     * behaviour here is what actually warrants testing and none of it needs a game. Callers in the
     * mod pass {@code Item::getMaxStackSize}.
     *
     * @param contents     total count per item, as {@link #getContents()} records it
     * @param maxStackSize the maximum stack size of a given item; values below one are treated as
     *                     one, so a bad answer costs an over-estimate rather than a divide by zero
     * @return An estimate between {@code 0} and {@link #SHULKER_SLOTS}
     */
    static <T> int estimatedUsedSlots(Map<T, Integer> contents, java.util.function.ToIntFunction<T> maxStackSize) {
        long used = 0;
        for (Map.Entry<T, Integer> entry : contents.entrySet()) {
            int count = entry.getValue();
            if (count <= 0) {
                continue;
            }
            int maxStack = Math.max(1, maxStackSize.applyAsInt(entry.getKey()));
            // widened to long before the +maxStack-1, or Integer.MAX_VALUE wraps negative and a
            // full box reads as empty
            used += (count + (long) maxStack - 1L) / maxStack;
            if (used >= SHULKER_SLOTS) {
                return SHULKER_SLOTS;
            }
        }
        return (int) used;
    }

    /**
     * How many free slots this box is believed to have, i.e. roughly how many whole stacks it could
     * still accept. Never observed, always derived -- see {@link #estimatedUsedSlots()}.
     *
     * @return An estimate between {@code 0} and {@link #SHULKER_SLOTS}
     */
    default int estimatedFreeSlots() {
        return SHULKER_SLOTS - estimatedUsedSlots();
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
