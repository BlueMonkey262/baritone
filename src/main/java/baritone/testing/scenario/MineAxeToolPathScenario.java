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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/** An axe-required target must select the available axe rather than a pickaxe. */
public final class MineAxeToolPathScenario extends AbstractMiningScenario {

    private static final int TARGET_X = 5;

    @Override
    public String name() {
        return "mine-axe-tool-path";
    }

    @Override
    public String description() {
        return "Break an oak log with the available axe while leaving the competing pickaxe untouched";
    }

    @Override
    public int tickBudget() {
        return 20 * 60;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>(miningSettings());
        settings.put("autoTool", true);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.setBlock(TARGET_X, 0, 0, "minecraft:oak_log[axis=y]");
        arena.command("give @s minecraft:iron_pickaxe 1");
        arena.command("give @s minecraft:diamond_axe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.OAK_LOG)
                && countPlayer(arena, Items.IRON_PICKAXE) == 1
                && countPlayer(arena, Items.DIAMOND_AXE) == 1
                && itemDamage(arena, Items.IRON_PICKAXE) == 0
                && itemDamage(arena, Items.DIAMOND_AXE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("oak-log target=%s; iron pickaxe and diamond axe both start at damage zero",
                arena.at(TARGET_X, 0, 0));
        arena.baritone().getMineProcess().mine(Blocks.OAK_LOG);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int pickDamage = itemDamage(arena, Items.IRON_PICKAXE);
        int axeDamage = itemDamage(arena, Items.DIAMOND_AXE);
        boolean targetGone = !arena.stateAt(TARGET_X, 0, 0).is(Blocks.OAK_LOG);
        if (pickDamage > 0) {
            return Verdict.fail("the axe-required target damaged the iron pickaxe by %d", pickDamage);
        }
        if (targetGone && axeDamage > 0) {
            arena.note("axe evidence at t=%d: log=%s, axeDamage=%d, pickDamage=%d, player=%s",
                    elapsedTicks, arena.stateAt(TARGET_X, 0, 0), axeDamage, pickDamage, arena.ctx().playerFeet());
            return Verdict.pass("selected the diamond axe for the log and left the pickaxe untouched");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("log=%s, axeDamage=%d, pickDamage=%d, mineActive=%b, player=%s",
                arena.stateAt(TARGET_X, 0, 0), itemDamage(arena, Items.DIAMOND_AXE),
                itemDamage(arena, Items.IRON_PICKAXE), mineActive(arena), arena.ctx().playerFeet());
    }
}
