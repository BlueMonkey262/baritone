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

/** A fresh block drop must merge into an existing player stack without losing a protected item. */
public final class PickupDroppedStackMergeScenario extends AbstractMiningScenario {

    private static final int TARGET_X = 5;

    @Override
    public String name() {
        return "pickup-dropped-stack-merge";
    }

    @Override
    public String description() {
        return "Merge a water-displaced fresh cobblestone drop into a nearly full player stack";
    }

    @Override
    public int tickBudget() {
        return 20 * 90;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>(miningSettings());
        settings.put("pickupBlocks", true);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        stageMiningArena(arena);
        arena.command("kill @e[type=minecraft:item,distance=..64]");
        // Keep the target above the water, as an actual block drop falls into the current. A
        // second stone target on the floor made the miner choose an unrelated, obstructed target.
        arena.fill(TARGET_X, 0, 0, TARGET_X + 6, 0, 0, "minecraft:water");
        arena.setBlock(TARGET_X, 1, 0, "minecraft:stone");
        arena.command("give @s minecraft:cobblestone 63");
        arena.command("give @s minecraft:diamond 1");
        arena.command("give @s minecraft:iron_pickaxe 1");
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return floorAndAirStaged(arena)
                && arena.stateAt(TARGET_X, 1, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.WATER)
                && arena.stateAt(TARGET_X + 6, 0, 0).is(Blocks.WATER)
                && countPlayer(arena, Items.COBBLESTONE) == 63
                && countPlayer(arena, Items.DIAMOND) == 1
                && countPlayer(arena, Items.IRON_PICKAXE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("starting with cobblestone=63 and protected diamond=1; stone target is displaced over water");
        arena.baritone().getMineProcess().mine(Blocks.STONE);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean targetGone = !arena.stateAt(TARGET_X, 1, 0).is(Blocks.STONE);
        int cobblestone = countPlayer(arena, Items.COBBLESTONE);
        int diamond = countPlayer(arena, Items.DIAMOND);
        if (diamond != 1) {
            return Verdict.fail("the protected diamond was lost during pickup: diamond=%d", diamond);
        }
        if (targetGone && cobblestone == 64 && countBlockEntities(arena, Items.COBBLESTONE) == 0) {
            arena.note("observed after %d ticks: targetGone=%b, cobblestone=%d, looseCobblestoneEntities=%d, diamond=%d, player=%s",
                    elapsedTicks, targetGone, cobblestone, countBlockEntities(arena, Items.COBBLESTONE), diamond,
                    arena.ctx().playerFeet());
            return Verdict.pass("merged the fresh drop into the existing stack without losing protected inventory");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("targetGone=%b, cobblestone=%d, looseCobblestoneEntities=%d, diamond=%d, player=%s",
                !arena.stateAt(TARGET_X, 1, 0).is(Blocks.STONE), countPlayer(arena, Items.COBBLESTONE),
                countBlockEntities(arena, Items.COBBLESTONE), countPlayer(arena, Items.DIAMOND),
                arena.ctx().playerFeet());
    }
}
