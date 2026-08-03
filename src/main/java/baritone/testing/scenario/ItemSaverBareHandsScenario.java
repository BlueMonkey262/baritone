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

/** Disabling sword fallback must still stop rather than mining stone bare-handed. */
public final class ItemSaverBareHandsScenario extends AbstractItemSaverScenario {

    @Override
    public String name() {
        return "itemsaver-no-bare-hands";
    }

    @Override
    public String description() {
        return "Do not slowly mine stone bare-handed when the spent pickaxe has no replacement";
    }

    @Override
    public int tickBudget() {
        return 20 * 15;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = miningSettings(true);
        settings.put("restockFromBoxes", false);
        settings.put("useSwordToMine", false);
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
        if (remaining < WALL_BLOCKS || damage != PICKAXE_DAMAGE) {
            noteObservation(arena,
                    "observed after %d ticks: wall=%d/%d, pickaxe damage=%d, mineActive=%b",
                    elapsedTicks, remaining, WALL_BLOCKS, damage, mineActive(arena));
            return Verdict.fail("spent-pickaxe mine used a fallback: wall=%d/%d, pickaxe damage=%d",
                    remaining, WALL_BLOCKS, damage);
        }
        if (elapsedTicks >= 60 && !mineActive(arena)) {
            noteObservation(arena,
                    "observed after %d ticks: mine stopped, wall=%d/%d, pickaxe damage=%d",
                    elapsedTicks, remaining, WALL_BLOCKS, damage);
            return Verdict.pass("the mine stopped instead of breaking stone at bare-hand speed");
        }
        if (elapsedTicks >= 20 * 10) {
            noteObservation(arena,
                    "observed after %d ticks: still active=%b, wall=%d/%d, pickaxe damage=%d",
                    elapsedTicks, mineActive(arena), remaining, WALL_BLOCKS, damage);
            return Verdict.fail("mine remained active instead of stopping without a tool: wall=%d/%d",
                    remaining, WALL_BLOCKS);
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
