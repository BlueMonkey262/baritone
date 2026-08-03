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

import java.util.HashMap;
import java.util.Map;

/** Legit mining may select the exposed ore, but must not x-ray through the stone shell. */
public final class MineVisibleOnlyScenario extends AbstractMiningScenario {

    private static final int VISIBLE_X = 4;
    private static final int HIDDEN_X = 6;

    @Override
    public String name() {
        return "mine-visible-only";
    }

    @Override
    public String description() {
        return "With legitMine enabled, mine exposed diamond ore without x-raying enclosed ore";
    }

    @Override
    public int tickBudget() {
        return 20 * 60;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>(miningSettings());
        settings.put("legitMine", true);
        settings.put("legitMineYLevel", 0);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.fill(4, 0, -1, 8, 2, 1, "minecraft:stone");
        arena.setBlock(VISIBLE_X, 1, 0, "minecraft:diamond_ore");
        arena.setBlock(HIDDEN_X, 1, 0, "minecraft:diamond_ore");
        arena.command("give @s minecraft:diamond_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(VISIBLE_X, 1, 0).is(Blocks.DIAMOND_ORE)
                && arena.stateAt(HIDDEN_X, 1, 0).is(Blocks.DIAMOND_ORE)
                && arena.stateAt(5, 1, 0).is(Blocks.STONE)
                && countPlayer(arena, Items.DIAMOND_PICKAXE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.baritone().getMineProcess().mine(1, Blocks.DIAMOND_ORE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean visibleStillThere = arena.stateAt(VISIBLE_X, 1, 0).is(Blocks.DIAMOND_ORE);
        boolean hiddenStillThere = arena.stateAt(HIDDEN_X, 1, 0).is(Blocks.DIAMOND_ORE);
        int diamonds = countPlayer(arena, Items.DIAMOND);
        if (!hiddenStillThere) {
            return Verdict.fail("legitMine reached the enclosed ore at x+%d", HIDDEN_X);
        }
        if (!visibleStillThere && diamonds >= 1) {
            return Verdict.pass("mined the exposed ore and left the enclosed ore untouched");
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("visible ore was not completed: visible=%b, hidden=%b, diamonds=%d",
                    visibleStillThere, hiddenStillThere, diamonds);
        }
        return null;
    }
}
