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
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/** An unbreakable target must end the mine instead of causing an endless retry loop. */
public final class MineNoToolFallbackScenario extends AbstractMiningScenario {

    private static final int[][] TARGETS = {{4, 0, 0}};

    @Override
    public String name() {
        return "mine-no-tool-fallback";
    }

    @Override
    public String description() {
        return "Stop cleanly on obsidian when no suitable pickaxe exists";
    }

    @Override
    public int tickBudget() {
        return 20 * 15;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>(miningSettings());
        settings.put("useSwordToMine", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.setBlock(4, 0, 0, "minecraft:obsidian");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && countBlocks(arena, Blocks.OBSIDIAN, TARGETS) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.baritone().getMineProcess().mine(Blocks.OBSIDIAN);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean intact = countBlocks(arena, Blocks.OBSIDIAN, TARGETS) == 1;
        if (!intact) {
            return Verdict.fail("the no-tool mine changed the obsidian target");
        }
        if (!mineActive(arena) && elapsedTicks >= 20) {
            return Verdict.pass("the mine stopped on the unbreakable target instead of retrying forever");
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("mine stayed active for %d ticks on an obsidian target with no tool",
                    elapsedTicks);
        }
        return null;
    }
}
