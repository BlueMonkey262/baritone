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

package baritone.testing.scenario;

import baritone.testing.TestArena;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/** A replacement fetch must return the worn tool to the same box, not classify it as ordinary junk. */
public final class ItemSaverToolSwapBackScenario extends AbstractItemSaverScenario {

    private static final int BOX_PICKAXES = 2;

    @Override
    public String name() {
        return "itemsaver-tool-swap-back";
    }

    @Override
    public String description() {
        return "Put a spent pickaxe back in its replacement box while holding the fresh replacement";
    }

    @Override
    public int tickBudget() {
        return 20 * 300;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = miningSettings(true);
        settings.put("allowInventory", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockIndexBeforeBuild", false);
        // Fetch two replacements so one can be in hand while the other remains fresh enough for
        // the verdict to observe the replacement phase before another break wears it out.
        settings.put("restockExtraStacks", 1);
        return settings;
    }

    @Override
    protected boolean hasReplacementBox() {
        return true;
    }

    @Override
    protected int stagedBoxPickaxes() {
        return BOX_PICKAXES;
    }

    @Override
    protected void stageEquipment(TestArena arena) {
        stageSpentPickaxe(arena);
        stageReplacementBox(arena);
    }

    @Override
    public void start(TestArena arena) {
        registerReplacementBox(arena);
        startMining(arena);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int remaining = countWallBlocks(arena);
        int spentInBox = ScenarioInventory.countContainerMatching(
                arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE, AbstractItemSaverScenario::isSpentPickaxe);
        int freshInBox = ScenarioInventory.countContainerMatching(
                arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE, AbstractItemSaverScenario::isFreshPickaxe);
        ItemStack held = arena.ctx().player().getMainHandItem();

        // The wall proves the mining job resumed; the damage-qualified box count distinguishes
        // swap-back from an unrelated junk deposit, and the held item proves a replacement arrived.
        if (remaining < WALL_BLOCKS && spentInBox == 1 && freshInBox == 0 && replacementInHand(held)) {
            noteObservation(arena,
                    "observed after %d ticks: wall=%d/%d, spent pickaxe in box=%d, fresh in box=%d, held damage=%d",
                    elapsedTicks, remaining, WALL_BLOCKS, spentInBox, freshInBox, held.getDamageValue());
            return Verdict.pass("mining resumed with a replacement in hand and the spent pickaxe returned to its source box");
        }
        if (elapsedTicks >= tickBudget()) {
            noteObservation(arena,
                    "timed out after %d ticks: wall=%d/%d, spent pickaxe in box=%d, fresh in box=%d, held=%s",
                    elapsedTicks, remaining, WALL_BLOCKS, spentInBox, freshInBox,
                    held.isEmpty() ? "empty" : held.getItem().toString());
            return Verdict.fail("tool swap-back was not proven: wall=%d/%d, spent in box=%d, fresh in box=%d, replacement in hand=%b",
                    remaining, WALL_BLOCKS, spentInBox, freshInBox, replacementInHand(held));
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        ItemStack held = arena.ctx().player().getMainHandItem();
        return "wall=" + countWallBlocks(arena) + "/" + WALL_BLOCKS
                + ", spent pickaxes in box=" + ScenarioInventory.countContainerMatching(
                arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE, AbstractItemSaverScenario::isSpentPickaxe)
                + ", fresh pickaxes in box=" + ScenarioInventory.countContainerMatching(
                arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE, AbstractItemSaverScenario::isFreshPickaxe)
                + ", held=" + (held.isEmpty() ? "empty" : held.getItem() + "/damage=" + held.getDamageValue());
    }

    private static boolean replacementInHand(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(Items.WOODEN_PICKAXE)
                && stack.getDamageValue() < PICKAXE_DAMAGE
                && stack.getMaxDamage() == PICKAXE_MAX_DAMAGE
                && efficiencyLevel(stack) == EFFICIENCY_LEVEL;
    }
}
