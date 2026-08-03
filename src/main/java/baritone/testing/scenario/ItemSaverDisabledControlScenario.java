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

/** The off setting retains upstream behavior: the nearly-spent pickaxe is allowed to break. */
public final class ItemSaverDisabledControlScenario extends AbstractItemSaverScenario {

    @Override
    public String name() {
        return "itemsaver-disabled-control";
    }

    @Override
    public String description() {
        return "With itemSaver off, let the spent Efficiency V wooden pickaxe break while mining";
    }

    @Override
    public int tickBudget() {
        return 20 * 20;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = miningSettings(false);
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
        int remaining = countWallBlocks(arena);
        int damage = pickaxeDamage(arena);
        if (remaining < WALL_BLOCKS && damage < 0) {
            noteObservation(arena,
                    "observed after %d ticks: wall=%d/%d, pickaxe broke and is no longer owned, "
                            + "mineActive=%b",
                    elapsedTicks, remaining, WALL_BLOCKS, mineActive(arena));
            return Verdict.pass("with itemSaver off, mining progressed and the spent pickaxe was allowed to break");
        }
        if (elapsedTicks >= tickBudget()) {
            noteObservation(arena,
                    "timed out after %d ticks: wall=%d/%d, pickaxe damage=%d, mineActive=%b",
                    elapsedTicks, remaining, WALL_BLOCKS, damage, mineActive(arena));
            return Verdict.fail("control did not reproduce upstream mining: wall=%d/%d, pickaxe damage=%d",
                    remaining, WALL_BLOCKS, damage);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "mineActive=" + mineActive(arena)
                + ", wall=" + countWallBlocks(arena) + "/" + WALL_BLOCKS
                + ", pickaxe damage=" + pickaxeDamage(arena);
    }
}
