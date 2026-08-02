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

/** The default sword fallback must not spend the sword when itemSaver protects a pickaxe. */
public final class ItemSaverSwordSubstituteScenario extends AbstractItemSaverScenario {

    @Override
    public String name() {
        return "itemsaver-no-sword-substitute";
    }

    @Override
    public String description() {
        return "Do not mine stone with a hotbar sword when the pickaxe is spent";
    }

    @Override
    public int tickBudget() {
        return 20 * 15;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = miningSettings(true);
        settings.put("restockFromBoxes", false);
        settings.put("useSwordToMine", true);
        return settings;
    }

    @Override
    protected boolean hasSword() {
        return true;
    }

    @Override
    protected void stageEquipment(TestArena arena) {
        stageSpentPickaxe(arena);
        stageSword(arena);
    }

    @Override
    public void start(TestArena arena) {
        startMining(arena);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int remaining = countWallBlocks(arena);
        int swordDamage = swordDamage(arena);
        int pickaxeDamage = pickaxeDamage(arena);
        if (remaining < WALL_BLOCKS || swordDamage > 0 || pickaxeDamage != PICKAXE_DAMAGE) {
            noteObservation(arena,
                    "observed after %d ticks: wall=%d/%d, sword damage=%d, pickaxe damage=%d, "
                            + "mineActive=%b",
                    elapsedTicks, remaining, WALL_BLOCKS, swordDamage, pickaxeDamage,
                    mineActive(arena));
            return Verdict.fail("spent pickaxe fallback changed protected state: wall=%d/%d, sword damage=%d, pickaxe damage=%d",
                    remaining, WALL_BLOCKS, swordDamage, pickaxeDamage);
        }
        if (elapsedTicks >= 60 && !mineActive(arena)) {
            noteObservation(arena,
                    "observed after %d ticks: mine stopped, wall=%d/%d, sword damage=%d, pickaxe damage=%d",
                    elapsedTicks, remaining, WALL_BLOCKS, swordDamage, pickaxeDamage);
            return Verdict.pass("the spent pickaxe was protected and the sword remained at damage 0");
        }
        if (elapsedTicks >= 20 * 10) {
            noteObservation(arena,
                    "observed after %d ticks: mine still active=%b, wall=%d/%d, sword damage=%d, "
                            + "pickaxe damage=%d",
                    elapsedTicks, mineActive(arena), remaining, WALL_BLOCKS, swordDamage,
                    pickaxeDamage);
            return Verdict.fail("mine did not stop without using the sword: active=%b, wall=%d/%d",
                    mineActive(arena), remaining, WALL_BLOCKS);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "mineActive=" + mineActive(arena)
                + ", wall=" + countWallBlocks(arena) + "/" + WALL_BLOCKS
                + ", sword damage=" + swordDamage(arena)
                + ", pickaxe damage=" + pickaxeDamage(arena);
    }
}
