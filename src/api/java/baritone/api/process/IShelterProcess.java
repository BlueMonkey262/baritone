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

import java.util.function.Predicate;

/**
 * Retreats to the registered shulker boxes when hostile mobs start landing hits, and sleeps the
 * night away in a nearby bed if it can find one.
 * <p>
 * Driven the same way {@link IRestockProcess#requestDeposit} is: the process doing the work offers
 * control rather than having it taken. That keeps the decision about what is worth keeping with the
 * process that knows -- a clearing job wants none of what it breaks, a mine wants all of it -- and
 * means sheltering only ever interrupts Baritone's own work, never someone playing by hand.
 */
public interface IShelterProcess extends IBaritoneProcess {

    /**
     * Offers control so we can get out of the fight.
     * <p>
     * Returns {@code false}, cheaply, unless {@code shelterOnAttack} is on and hostile mobs have
     * actually been hurting us, so callers can put this at the top of their tick without guarding it
     * themselves. If it returns {@code true} the process is active and takes control next tick.
     *
     * @param worthKeeping Whether an inventory stack belongs to the work being done, used when
     *                     unloading at the box so the interrupted job's materials survive
     * @return {@code true} if a shelter run was started
     */
    boolean requestShelter(Predicate<ItemStack> worthKeeping);
}
