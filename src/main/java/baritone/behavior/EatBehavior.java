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
import baritone.api.utils.input.Input;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Eats when we're hungry and there's food on the hotbar.
 * <p>
 * Vanilla only keeps an item in use while the use key is held: as soon as it isn't, the client tells
 * the server to cancel, and eating never finishes. So we start the use through the player controller
 * like any other right click, then hold the key down ourselves until the food is gone. The key is
 * only ever held while we're actually mid-use, so the game's "held use key" handling can't run off
 * and interact with whatever we happen to be looking at.
 * <p>
 * Registered last, after the processes have had their turn, so the selected slot we set is the one
 * the player tick sees -- otherwise the builder switching to its building material every tick would
 * cancel the meal.
 */
public final class EatBehavior extends Behavior {

    /**
     * The slot we're eating out of, or -1 if we aren't eating.
     */
    private int eatingSlot = -1;

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
            return;
        }
        if (ctx.player().isUsingItem()) {
            return; // already busy with something, whether ours or the player's own
        }
        if (baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)) {
            return; // mid-mine; eating now would throw away the breaking progress
        }
        int slot = bestFoodSlot();
        if (slot < 0) {
            return;
        }
        ctx.player().getInventory().setSelectedSlot(slot);
        ctx.playerController().processRightClick(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND);
        if (!ctx.player().isUsingItem()) {
            return; // didn't take, e.g. the server hasn't caught up with the slot switch yet
        }
        eatingSlot = slot;
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
        if (!ctx.player().isUsingItem() || !isEdible(ctx.player().getInventory().getNonEquipmentItems().get(eatingSlot))) {
            stopEating();
            return;
        }
        // keep hold of both the key and the slot; a process switching hotbar slots under us would
        // change the held item, and vanilla cancels the use when that happens
        ctx.player().getInventory().setSelectedSlot(eatingSlot);
        // let go on the last tick of the meal. the food still lands -- the client only cancels a use
        // it's still in the middle of -- and it means the key is never down on a tick where we
        // aren't eating, which is when the game's held-use handling would fire at whatever is under
        // the crosshair
        holdUseKey(ctx.player().getUseItemRemainingTicks() > 1);
    }

    private void stopEating() {
        if (eatingSlot < 0) {
            return;
        }
        eatingSlot = -1;
        holdUseKey(false);
    }

    private void holdUseKey(boolean down) {
        ctx.minecraft().options.keyUse.setDown(down);
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
            ItemStack stack = ctx.player().getInventory().getNonEquipmentItems().get(i);
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
