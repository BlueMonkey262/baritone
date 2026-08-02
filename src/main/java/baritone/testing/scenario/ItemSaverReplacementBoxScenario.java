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
 * along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.testing.scenario;

import baritone.testing.TestArena;

import java.util.HashMap;
import java.util.Map;

/** A spent mining tool is replaced from a registered shulker box. */
public final class ItemSaverReplacementBoxScenario extends AbstractItemSaverScenario {

    private static final int BOX_PICKAXES = 2;

    @Override
    public String name() {
        return "itemsaver-replacement-box";
    }

    @Override
    public String description() {
        return "Fetch fresh Efficiency V wooden pickaxes from a registered box and resume mining";
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
        // A spare is deliberate: the replacement that resumes mining becomes damaged, while a
        // second fetched pickaxe lets the verdict prove that the fetched item was undamaged.
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
        int freshOwned = freshPickaxesOwned(arena);
        int inBox = boxPickaxes(arena);
        if (remaining < WALL_BLOCKS && freshOwned > 0 && inBox == 0) {
            noteObservation(arena,
                    "observed after %d ticks: wall=%d/%d, fresh pickaxes owned=%d, box pickaxes=%d, "
                            + "spent-pick damage=%d, mineActive=%b",
                    elapsedTicks, remaining, WALL_BLOCKS, freshOwned, inBox,
                    pickaxeDamage(arena), mineActive(arena));
            return Verdict.pass("mining progressed after the box supplied fresh pickaxes; one "
                    + "undamaged Efficiency V pickaxe remains owned and the box is empty");
        }
        if (elapsedTicks >= tickBudget()) {
            noteObservation(arena,
                    "timed out after %d ticks: wall=%d/%d, fresh pickaxes owned=%d, box pickaxes=%d, "
                            + "pickaxe damage=%d, mineActive=%b",
                    elapsedTicks, remaining, WALL_BLOCKS, freshOwned, inBox,
                    pickaxeDamage(arena), mineActive(arena));
            return Verdict.fail("replacement did not produce resumed mining: wall=%d/%d, fresh owned=%d, box=%d",
                    remaining, WALL_BLOCKS, freshOwned, inBox);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "wall=" + countWallBlocks(arena) + "/" + WALL_BLOCKS
                + ", fresh pickaxes owned=" + freshPickaxesOwned(arena)
                + ", box pickaxes=" + boxPickaxes(arena)
                + ", pickaxe damage=" + pickaxeDamage(arena);
    }
}
