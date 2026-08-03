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
import baritone.testing.TestScenario;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Only the block type explicitly allowed by allowBreakAnyway may be mined. */
public final class MineAllowBreakAnywayScenario extends AbstractMiningScenario {

    private static final int STONE_X = 5;
    private static final int DEEPSLATE_X = 7;

    @Override
    public String name() {
        return "mine-allow-break-anyway";
    }

    @Override
    public String description() {
        return "Mine an allowBreakAnyway target while leaving an unlisted requested block intact";
    }

    @Override
    public int tickBudget() {
        return 20 * 60;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>(miningSettings());
        settings.put("allowBreak", false);
        settings.put("allowBreakAnyway", Arrays.asList(Blocks.STONE));
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.setBlock(STONE_X, 0, 0, "minecraft:stone");
        arena.setBlock(DEEPSLATE_X, 0, 0, "minecraft:deepslate");
        arena.command("give @s minecraft:iron_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(STONE_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(DEEPSLATE_X, 0, 0).is(Blocks.DEEPSLATE)
                && countPlayer(arena, Items.IRON_PICKAXE) == 1
                && countPlayer(arena, Items.COBBLESTONE) == 0
                && countPlayer(arena, Items.COBBLED_DEEPSLATE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("mining filter includes stone and deepslate; allowBreakAnyway contains only stone");
        arena.baritone().getMineProcess().mine(Blocks.STONE, Blocks.DEEPSLATE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean stoneGone = !arena.stateAt(STONE_X, 0, 0).is(Blocks.STONE);
        boolean deepslateIntact = arena.stateAt(DEEPSLATE_X, 0, 0).is(Blocks.DEEPSLATE);
        int cobblestone = countPlayer(arena, Items.COBBLESTONE);
        if (!deepslateIntact) {
            return Verdict.fail("mining broke the requested deepslate that was not in allowBreakAnyway");
        }
        if (stoneGone && cobblestone >= 1 && !mineActive(arena)) {
            arena.note("observed after %d ticks: stone=%s, deepslate=%s, cobblestone=%d, player=%s",
                    elapsedTicks, arena.stateAt(STONE_X, 0, 0), arena.stateAt(DEEPSLATE_X, 0, 0),
                    cobblestone, arena.ctx().playerFeet());
            return Verdict.pass("mined the allowed stone and left unlisted deepslate intact");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("stone=%s, deepslate=%s, cobblestone=%d, mineActive=%b, player=%s",
                arena.stateAt(STONE_X, 0, 0), arena.stateAt(DEEPSLATE_X, 0, 0),
                countPlayer(arena, Items.COBBLESTONE), mineActive(arena), arena.ctx().playerFeet());
    }
}
