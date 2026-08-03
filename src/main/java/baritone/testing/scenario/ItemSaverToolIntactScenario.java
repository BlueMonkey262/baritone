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

import java.util.Map;

/** The protected pickaxe remains intact even when autoTool cannot select a replacement slot. */
public final class ItemSaverToolIntactScenario extends AbstractItemSaverScenario {

    @Override
    public String name() {
        return "itemsaver-tool-intact";
    }

    @Override
    public String description() {
        return "Keep a spent Efficiency V wooden pickaxe intact while mining stone with itemSaver on";
    }

    @Override
    public int tickBudget() {
        return 20 * 12;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = miningSettings(true);
        // This is the pre-fix path where no slot selection happens at all. The break guard must
        // protect the held item, not only the normal auto-tool selection path.
        settings.put("autoTool", false);
        settings.put("restockFromBoxes", false);
        return settings;
    }

    @Override
    protected void stageEquipment(TestArena arena) {
        stageSpentPickaxe(arena);
    }

    @Override
    public void start(TestArena arena) {
        startMining(arena);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (elapsedTicks < 60) {
            return null;
        }
        int damage = pickaxeDamage(arena);
        int remaining = countWallBlocks(arena);
        if (damage == PICKAXE_DAMAGE) {
            noteObservation(arena,
                    "observed after %d ticks: pickaxe damage=%d/%d, wall=%d/%d, mineActive=%b",
                    elapsedTicks, damage, PICKAXE_MAX_DAMAGE, remaining, WALL_BLOCKS,
                    mineActive(arena));
            return Verdict.pass("the spent pickaxe remained in inventory at damage " + damage
                    + " and no durability was spent to mine the wall");
        }
        noteObservation(arena,
                "observed after %d ticks: pickaxe damage=%d, wall=%d/%d, mineActive=%b",
                elapsedTicks, damage, remaining, WALL_BLOCKS, mineActive(arena));
        return Verdict.fail("itemSaver spent or lost the protected pickaxe: damage=%d, wall=%d/%d",
                damage, remaining, WALL_BLOCKS);
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "pickaxe damage=" + pickaxeDamage(arena)
                + ", wall=" + countWallBlocks(arena) + "/" + WALL_BLOCKS;
    }
}
