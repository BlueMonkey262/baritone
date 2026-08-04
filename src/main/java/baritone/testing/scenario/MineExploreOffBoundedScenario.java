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

import java.util.Map;

/** No known target and exploration disabled must cancel mining without walking away. */
public final class MineExploreOffBoundedScenario extends AbstractMiningScenario {

    @Override
    public String name() {
        return "mine-explore-off-bounded";
    }

    @Override
    public String description() {
        return "Cancel a stone mine cleanly when no matching block exists and exploration is disabled";
    }

    @Override
    public int tickBudget() {
        return 20 * 45;
    }

    @Override
    public Map<String, Object> settings() {
        return miningSettings();
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.command("give @s minecraft:iron_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && countStone(arena) == 0
                && countPlayer(arena, Items.IRON_PICKAXE) == 1
                && countPlayer(arena, Items.COBBLESTONE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("the mining volume contains no stone targets; exploreForBlocks=false and player starts at %s",
                arena.ctx().playerFeet());
        arena.baritone().getMineProcess().mine(Blocks.STONE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int distance = (int) Math.sqrt(arena.ctx().playerFeet().distSqr(arena.origin()));
        if (distance > 4) {
            return Verdict.fail("the no-target mine explored %d blocks from its staged start with exploration disabled",
                    distance);
        }
        if (elapsedTicks >= 20 && !mineActive(arena)) {
            arena.note("no-target evidence at t=%d: stone targets=%d, cobblestone=%d, distance=%d, mineActive=%b, player=%s",
                    elapsedTicks, countStone(arena), countPlayer(arena, Items.COBBLESTONE), distance,
                    mineActive(arena), arena.ctx().playerFeet());
            return Verdict.pass("cancelled the empty mine locally without starting exploration");
        }
        if (elapsedTicks >= 20 * 30) {
            return Verdict.fail("empty mine did not cancel within the bounded window: distance=%d, active=%b",
                    distance, mineActive(arena));
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("stoneTargets=%d, cobblestone=%d, distance=%d, mineActive=%b, player=%s",
                countStone(arena), countPlayer(arena, Items.COBBLESTONE),
                (int) Math.sqrt(arena.ctx().playerFeet().distSqr(arena.origin())), mineActive(arena),
                arena.ctx().playerFeet());
    }

    private static int countStone(TestArena arena) {
        int count = 0;
        for (int x = -4; x <= 52; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = -20; z <= 20; z++) {
                    if (arena.stateAt(x, y, z).is(Blocks.STONE)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
