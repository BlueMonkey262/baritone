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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * The registered restock boxes for a world. Persisted per dimension, alongside waypoints.
 *
 * @see IRestockBox
 */
public interface IRestockBoxCollection {

    /**
     * Registers a box at the given position. If one is already registered there, it is returned
     * unchanged rather than duplicated.
     *
     * @param pos The position of the shulker box
     * @return The registered box
     */
    IRestockBox addBox(BetterBlockPos pos);

    /**
     * Registers several boxes together and returns the number that were new. Implementations that
     * persist registrations should override this so a scan can commit its whole result once; the
     * default keeps older third-party collections source-compatible.
     *
     * @param positions The positions of the shulker boxes
     * @return The number of newly registered boxes
     */
    default int addBoxes(Collection<BetterBlockPos> positions) {
        int added = 0;
        for (BetterBlockPos pos : positions) {
            if (getBox(pos) == null) {
                addBox(pos);
                added++;
            }
        }
        return added;
    }

    /**
     * @param pos The position to deregister
     * @return {@code true} if a box was registered there and has now been removed
     */
    boolean removeBox(BetterBlockPos pos);

    /**
     * @param pos A position
     * @return The box registered at that position, or {@code null}
     */
    IRestockBox getBox(BetterBlockPos pos);

    /**
     * @return Every registered box, including those flagged as missing
     */
    Set<IRestockBox> getAllBoxes();

    /**
     * Records what was actually seen inside a box, replacing any previous index for it.
     *
     * @param pos      The position of the box
     * @param contents The observed contents, as item to total count
     */
    void updateContents(BetterBlockPos pos, Map<Item, Integer> contents);

    /**
     * Flags or unflags a box as no longer existing at its registered position. Never deletes it;
     * see {@link IRestockBox#isMissing()} for why.
     *
     * @param pos     The position of the box
     * @param missing Whether the box is believed to be gone
     */
    void setMissing(BetterBlockPos pos, boolean missing);

    /**
     * Reloads the persisted registrations for this world and dimension.
     * <p>
     * The live harness uses this to exercise the same round-trip as a world-data reload. Other
     * implementations that do not persist registrations may keep their current in-memory view.
     */
    default void reloadFromDisk() {}
}
