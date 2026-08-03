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

/** The miner must honor both bounds while continuing to mine the in-range target. */
public final class MineYRangeScenario extends AbstractMiningScenario {

    private static final int TARGET_X = 5;
    private int minimumSetting;
    private int maximumSetting;

    @Override
    public String name() {
        return "mine-y-range";
    }

    @Override
    public String description() {
        return "Mine the target at arena level while pruning the same block above the Y range";
    }

    @Override
    public int tickBudget() {
        return 20 * 60;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>(miningSettings());
        settings.put("minYLevelWhileMining", this.minimumSetting);
        settings.put("maxYLevelWhileMining", this.maximumSetting);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.setBlock(TARGET_X, 0, 0, "minecraft:stone");
        arena.setBlock(TARGET_X, 2, 0, "minecraft:stone");
        arena.command("give @s minecraft:iron_pickaxe 1");
        int dimensionMinimum = arena.ctx().world().dimensionType().minY();
        int y = arena.origin().y;
        this.minimumSetting = y - dimensionMinimum;
        this.maximumSetting = y;
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 2, 0).is(Blocks.STONE)
                && countPlayer(arena, Items.IRON_PICKAXE) == 1
                && countPlayer(arena, Items.COBBLESTONE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("mining Y range is absolute %d..%d: in-range target y=%d, excluded target y=%d",
                this.minimumSetting + arena.ctx().world().dimensionType().minY(), this.maximumSetting,
                arena.at(TARGET_X, 0, 0).y, arena.at(TARGET_X, 2, 0).y);
        arena.baritone().getMineProcess().mine(Blocks.STONE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean lowerGone = !arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE);
        boolean upperIntact = arena.stateAt(TARGET_X, 2, 0).is(Blocks.STONE);
        int drops = countPlayer(arena, Items.COBBLESTONE);
        if (!upperIntact) {
            return Verdict.fail("the miner broke the stone target above maxYLevelWhileMining");
        }
        if (lowerGone && drops >= 1 && !mineActive(arena)) {
            arena.note("observed after %d ticks: lower=%s, upper=%s, cobblestone=%d, player=%s",
                    elapsedTicks, arena.stateAt(TARGET_X, 0, 0), arena.stateAt(TARGET_X, 2, 0),
                    drops, arena.ctx().playerFeet());
            return Verdict.pass("mined the in-range target and left the out-of-range target intact");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("lower=%s, upper=%s, cobblestone=%d, range=%d..%d, mineActive=%b, player=%s",
                arena.stateAt(TARGET_X, 0, 0), arena.stateAt(TARGET_X, 2, 0),
                countPlayer(arena, Items.COBBLESTONE), this.minimumSetting, this.maximumSetting,
                mineActive(arena), arena.ctx().playerFeet());
    }
}
