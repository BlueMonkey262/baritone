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

import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/** The only short route to the exposed ore crosses a water opening in a solid barrier. */
public final class MineOreBehindWaterScenario extends AbstractMiningScenario {

    private static final int TARGET_X = 7;
    private boolean crossedWater;

    @Override
    public String name() {
        return "mine-ore-behind-water";
    }

    @Override
    public String description() {
        return "Cross a controlled water opening to reach and mine the exposed ore behind it";
    }

    @Override
    public int tickBudget() {
        return 20 * 90;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>(miningSettings());
        settings.put("allowPlace", false);
        settings.put("waterCostMultiplier", 1.0D);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.fill(3, 0, -8, 3, 1, 8, "minecraft:stone");
        arena.setBlock(3, 0, 0, "minecraft:water");
        arena.setBlock(3, 1, 0, "minecraft:air");
        arena.setBlock(4, 0, 0, "minecraft:water");
        arena.setBlock(TARGET_X, 0, 0, "minecraft:diamond_ore");
        arena.command("give @s minecraft:diamond_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(3, 1, -1).is(Blocks.STONE)
                && arena.stateAt(3, 1, 0).isAir()
                && arena.stateAt(3, 0, 0).is(Blocks.WATER)
                && arena.stateAt(4, 0, 0).is(Blocks.WATER)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.DIAMOND_ORE)
                && countPlayer(arena, Items.DIAMOND_PICKAXE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        this.crossedWater = false;
        arena.baritone().getMineProcess().mine(1, Blocks.DIAMOND_ORE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        int x = feet.x - arena.origin().x;
        int z = feet.z - arena.origin().z;
        if (x >= 3 && x <= 4 && Math.abs(z) <= 1
                && (arena.stateAt(x, 0, z).is(Blocks.WATER)
                || arena.stateAt(x, -1, z).is(Blocks.WATER))) {
            this.crossedWater = true;
        }
        boolean targetStillThere = arena.stateAt(TARGET_X, 0, 0).is(Blocks.DIAMOND_ORE);
        if (!targetStillThere && !this.crossedWater) {
            return Verdict.fail("ore was mined without the player crossing the staged water opening");
        }
        if (!targetStillThere && this.crossedWater && countPlayer(arena, Items.DIAMOND) >= 1) {
            return Verdict.pass("crossed the water opening and mined the ore beyond it");
        }
        if (elapsedTicks >= tickBudget()) {
            return Verdict.fail("water-crossing mine did not complete: target=%b, crossedWater=%b, diamonds=%d",
                    targetStillThere, this.crossedWater, countPlayer(arena, Items.DIAMOND));
        }
        return null;
    }
}
