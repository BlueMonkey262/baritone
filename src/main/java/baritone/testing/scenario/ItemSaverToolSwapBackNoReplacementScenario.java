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

import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.Map;

/** A stale tool index must not make a worn tool swap back before a replacement actually arrives. */
public final class ItemSaverToolSwapBackNoReplacementScenario extends AbstractItemSaverScenario {

    private boolean reachedBox;
    private boolean openedBox;
    private int reachedBoxTick;

    @Override
    public String name() {
        return "itemsaver-tool-swap-back-no-replacement";
    }

    @Override
    public String description() {
        return "Keep a spent tool when a stale box index promises a replacement but the live box is empty";
    }

    @Override
    public int tickBudget() {
        return 20 * 180;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = miningSettings(true);
        settings.put("allowInventory", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockIndexBeforeBuild", false);
        settings.put("restockExtraStacks", 0);
        return settings;
    }

    @Override
    protected boolean hasReplacementBox() {
        return true;
    }

    @Override
    protected int stagedBoxPickaxes() {
        return 0;
    }

    @Override
    protected void stageEquipment(TestArena arena) {
        stageSpentPickaxe(arena);
        stageReplacementBox(arena);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        // The shared fixture checks the exact spent tool and world geometry. Keep the live box
        // explicitly empty too: the replacement promise is installed only as a stale index below.
        return super.stagingComplete(arena)
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the stale replacement index at %s could not be installed", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        // This is the persisted observation from an earlier indexing pass. The live container is
        // empty, so the scenario exercises the no-arrival path after the bot really walks there.
        world.getRestockBoxes().updateContents(
                box, Collections.singletonMap(Items.WOODEN_PICKAXE, 1));
        arena.note("registered an empty live box at %s with a stale index claiming one pickaxe", box);
        startMining(arena);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (!this.reachedBox && arena.ctx().playerFeet().distSqr(box) <= 9.0) {
            this.reachedBox = true;
            this.reachedBoxTick = elapsedTicks;
            arena.note("reached the live-empty stale-index box at t=%ds", elapsedTicks / 20);
        }
        if (arena.baritone().getContainerInteractionBehavior().openContainer() != null) {
            this.openedBox = true;
        }
        if (!this.reachedBox || !this.openedBox || elapsedTicks - this.reachedBoxTick < 20
                || arena.baritone().getContainerInteractionBehavior().openContainer() != null) {
            if (elapsedTicks >= tickBudget()) {
                return Verdict.fail("the stale-index restock never produced an observable open-and-close visit to the empty box");
            }
            return null;
        }

        int spentOwned = ScenarioInventory.countPlayerMatching(
                arena, Items.WOODEN_PICKAXE, AbstractItemSaverScenario::isSpentPickaxe);
        int spentInBox = ScenarioInventory.countContainerMatching(
                arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE, AbstractItemSaverScenario::isSpentPickaxe);
        int allPickaxesInBox = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE);
        if (spentOwned != 1 || spentInBox != 0 || allPickaxesInBox != 0) {
            return Verdict.fail("the empty-box tool trip changed the worn tool: spent owned=%d, spent in box=%d, all pickaxes in box=%d",
                    spentOwned, spentInBox, allPickaxesInBox);
        }
        return Verdict.pass("the bot reached the stale indexed box, found no replacement, and kept the worn tool instead of swapping it back");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "reached box=" + this.reachedBox
                + ", spent owned=" + ScenarioInventory.countPlayerMatching(
                arena, Items.WOODEN_PICKAXE, AbstractItemSaverScenario::isSpentPickaxe)
                + ", spent in box=" + ScenarioInventory.countContainerMatching(
                arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE, AbstractItemSaverScenario::isSpentPickaxe)
                + ", all pickaxes in box=" + ScenarioInventory.countContainer(
                arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE);
    }
}
