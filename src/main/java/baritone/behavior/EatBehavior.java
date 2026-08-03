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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.event.events.TickEvent;
import baritone.api.utils.Helper;
import baritone.api.utils.input.Input;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Eats when we're hungry and there's food on the hotbar.
 * <p>
 * Vanilla only keeps an item in use while the use key is held: as soon as it isn't, the client tells
 * the server to cancel, and eating never finishes. So we start the use through the player controller
 * like any other right click, then hold the key down ourselves for the whole meal.
 * <p>
 * The key must stay down until the use genuinely ends, because the food is consumed by
 * {@code LivingEntity#completeUsingItem}, which only runs server side. Letting go even one tick
 * early sends the cancel before the server's own counter has run out, and the meal is thrown away
 * having eaten nothing.
 * <p>
 * Holding a vanilla key down is only safe because of where we sit in the tick. Baritone's tick event
 * fires before {@code Minecraft#handleKeybinds}, so the first tick on which we notice the use has
 * ended is also a tick where we let go before the game looks at the key. It therefore never sees the
 * use key held while nothing is being used, which is the state that would make it right click
 * whatever happens to be under the crosshair.
 * <p>
 * Registered last, after the processes have had their turn, so the selected slot we set is the one
 * the player tick sees -- otherwise the builder switching to its building material every tick would
 * cancel the meal.
 */
public final class EatBehavior extends Behavior implements Helper {

    /**
     * The longest we'll stay committed to a meal. Vanilla's slowest food takes 32 ticks, so this is
     * pure insurance: if the client and server ever disagree about whether we're still eating, it
     * stops us holding the use key and the hotbar hostage indefinitely.
     */
    private static final int MAX_EATING_TICKS = 100;

    /**
     * The slot we're eating out of, or -1 if we aren't eating.
     */
    private int eatingSlot = -1;

    /**
     * How long the current meal has been going on, for {@link #MAX_EATING_TICKS}.
     */
    private int eatingTicks;

    /**
     * The last thing that stopped us eating, so that {@code chatDebug} says it once when it changes
     * rather than twenty times a second.
     */
    private String lastNote;

    public EatBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.OUT) {
            stopEating();
            return;
        }
        if (!Baritone.settings().autoEat.value || !shouldRun()) {
            stopEating();
            return;
        }
        if (eatingSlot >= 0) {
            continueEating();
            return;
        }
        if (ctx.player().getFoodData().getFoodLevel() > Baritone.settings().autoEatFoodLevel.value) {
            note("not hungry yet (" + ctx.player().getFoodData().getFoodLevel()
                    + " > autoEatFoodLevel " + Baritone.settings().autoEatFoodLevel.value + ")");
            return;
        }
        if (ctx.player().isUsingItem()) {
            note("something is already being used"); // whether ours or the player's own
            return;
        }
        if (baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)) {
            note("mid-mine, waiting for a gap"); // eating now would throw away the breaking progress
            return;
        }
        int slot = bestFoodSlot();
        if (slot < 0) {
            note("nothing edible on the hotbar");
            return;
        }
        int previousSlot = ctx.player().getInventory().selected;
        ctx.player().getInventory().selected = slot;
        InteractionResult result = ctx.playerController().processRightClick(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND);
        if (!ctx.player().isUsingItem()) {
            // didn't take, e.g. the server hasn't caught up with the slot switch yet. Put the hotbar
            // back where we found it rather than leaving whoever is building holding a carrot
            ctx.player().getInventory().selected = previousSlot;
            note("right clicked " + ctx.player().getInventory().items.get(slot).getItem()
                    + " in slot " + slot + " but no use started, result was " + result);
            return;
        }
        note("eating " + ctx.player().getInventory().items.get(slot).getItem() + " from slot " + slot);
        eatingSlot = slot;
        eatingTicks = 0;
        holdUseKey(true);
    }

    /**
     * Only feed the player while Baritone is the one driving, and only from the primary instance:
     * the use key is a single global, so a second instance forcing it down would be reaching into
     * controls it doesn't own, and someone playing by hand shouldn't have their hotbar taken away
     * mid-fight just because Baritone is installed.
     */
    private boolean shouldRun() {
        if (baritone != BaritoneAPI.getProvider().getPrimaryBaritone()) {
            return false;
        }
        return baritone.getPathingBehavior().isPathing()
                || baritone.getPathingControlManager().mostRecentInControl().isPresent();
    }

    private void continueEating() {
        if (!ctx.player().isUsingItem()) {
            note("meal ended after " + eatingTicks + " ticks, food level now "
                    + ctx.player().getFoodData().getFoodLevel());
            stopEating();
            return;
        }
        if (++eatingTicks > MAX_EATING_TICKS) {
            note("giving up on the meal after " + MAX_EATING_TICKS + " ticks");
            stopEating();
            return;
        }
        if (!isEdible(ctx.player().getInventory().items.get(eatingSlot))) {
            note("what we were eating is no longer in slot " + eatingSlot);
            stopEating();
            return;
        }
        // keep hold of both the key and the slot; a process switching hotbar slots under us would
        // change the held item, and vanilla cancels the use when that happens
        ctx.player().getInventory().selected = eatingSlot;
        // hold on all the way to the end. the server is the one that finishes the meal, so letting
        // go early cancels it having eaten nothing
        holdUseKey(true);
    }

    private void stopEating() {
        if (eatingSlot < 0) {
            return;
        }
        eatingSlot = -1;
        eatingTicks = 0;
        holdUseKey(false);
    }

    private void holdUseKey(boolean down) {
        ctx.minecraft().options.keyUse.setDown(down);
    }

    /**
     * Says what we're doing, or what's stopping us, once per change rather than once per tick. Only
     * visible with {@code chatDebug} on, since none of it is interesting when this works.
     */
    private void note(String what) {
        if (!what.equals(lastNote)) {
            lastNote = what;
            logDebug("autoEat: " + what);
        }
    }

    /**
     * The hotbar slot holding the food that wastes the least, or -1 if there isn't any.
     * <p>
     * "Least wasted" is the smallest nutrition that still fills us up; failing that, the largest,
     * so a stack of bread gets eaten before a steak but a steak is better than staying hungry.
     */
    private int bestFoodSlot() {
        int missing = 20 - ctx.player().getFoodData().getFoodLevel();
        int best = -1;
        int bestWaste = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = ctx.player().getInventory().items.get(i);
            if (!isEdible(stack)) {
                continue;
            }
            FoodProperties food = stack.get(DataComponents.FOOD);
            // overeating counts double against a food, so a smaller one that fits wins, but
            // something too small still beats nothing
            int waste = food.nutrition() > missing ? (food.nutrition() - missing) * 2 : missing - food.nutrition();
            if (waste < bestWaste) {
                bestWaste = waste;
                best = i;
            }
        }
        return best;
    }

    private boolean isEdible(ItemStack stack) {
        if (stack.isEmpty() || stack.get(DataComponents.FOOD) == null) {
            return false;
        }
        return !Baritone.settings().autoEatExclude.value.contains(stack.getItem());
    }
}
