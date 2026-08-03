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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;

/** A faster valid tool must win when more than one tool can break the target. */
public final class MineToolSelectionScenario extends AbstractMiningScenario {

    private static final int[][] TARGETS = {{4, 0, 0}, {4, 0, 1}};

    @Override
    public String name() {
        return "mine-tool-selection";
    }

    @Override
    public String description() {
        return "Select the faster diamond pickaxe instead of a competing wooden pickaxe";
    }

    @Override
    public int tickBudget() {
        return 20 * 45;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = miningSettings();
        settings.put("autoTool", true);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        for (int[] target : TARGETS) {
            arena.setBlock(target[0], target[1], target[2], "minecraft:stone");
        }
        arena.command("give @s minecraft:wooden_pickaxe 1");
        arena.command("give @s minecraft:diamond_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && countBlocks(arena, Blocks.STONE, TARGETS) == TARGETS.length
                && countPlayer(arena, Items.WOODEN_PICKAXE) == 1
                && countPlayer(arena, Items.DIAMOND_PICKAXE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.baritone().getMineProcess().mine(1, Blocks.STONE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int remaining = countBlocks(arena, Blocks.STONE, TARGETS);
        int woodenDamage = itemDamage(arena, Items.WOODEN_PICKAXE);
        int diamondDamage = itemDamage(arena, Items.DIAMOND_PICKAXE);
        if (woodenDamage > 0 || (remaining < TARGETS.length - 1 && diamondDamage == 0)) {
            return Verdict.fail("the wooden pickaxe was used or the diamond pickaxe did not break the first block: "
                    + "remaining=%d, wooden damage=%d, diamond damage=%d",
                    remaining, woodenDamage, diamondDamage);
        }
        if (remaining == TARGETS.length - 1 && diamondDamage > 0) {
            return Verdict.pass("the first stone target was broken with the faster diamond pickaxe");
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("no tool-selection evidence: remaining=%d, wooden damage=%d, diamond damage=%d",
                    remaining, woodenDamage, diamondDamage);
        }
        return null;
    }
}
