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
import baritone.utils.schematic.StaticSchematic;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** A configured valid substitute is accepted without suppressing other build work. */
public final class BuilderValidSubstituteScenario extends TestScenario {

    private static final int TARGET_X = 5;

    @Override
    public String name() {
        return "builder-valid-substitute";
    }

    @Override
    public String description() {
        return "Accept a pre-existing dirt substitute and place a separate concrete target";
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        Map<net.minecraft.world.level.block.Block, java.util.List<net.minecraft.world.level.block.Block>> valid = new HashMap<>();
        valid.put(Blocks.WHITE_CONCRETE, Arrays.asList(Blocks.DIRT));
        settings.put("buildValidSubstitutes", valid);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, 0, -4, 16, 5, 4, "minecraft:air");
        arena.fill(-4, -3, -4, 16, -1, 4, "minecraft:stone");
        arena.setBlock(TARGET_X, 0, 0, "minecraft:dirt");
        arena.command("clear @s");
        arena.command("give @s minecraft:white_concrete 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.DIRT)
                && arena.stateAt(TARGET_X + 2, 0, 0).isAir()
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("dirt at x+%d is the configured valid substitute; concrete target is x+%d",
                TARGET_X, TARGET_X + 2);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), schematic(), arena.at(TARGET_X, 0, 0));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean substituteIntact = arena.stateAt(TARGET_X, 0, 0).is(Blocks.DIRT);
        boolean targetBuilt = arena.stateAt(TARGET_X + 2, 0, 0).is(Blocks.WHITE_CONCRETE);
        if (!substituteIntact) {
            return Verdict.fail("the valid substitute was replaced instead of accepted");
        }
        if (targetBuilt) {
            arena.note("observed after %d ticks: substitute=%s, target=%s, player=%s",
                    elapsedTicks, arena.stateAt(TARGET_X, 0, 0), arena.stateAt(TARGET_X + 2, 0, 0),
                    arena.ctx().playerFeet());
            return Verdict.pass("accepted the configured substitute and built the missing target");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("substitute=%s, target=%s, builderActive=%b, player=%s",
                arena.stateAt(TARGET_X, 0, 0), arena.stateAt(TARGET_X + 2, 0, 0),
                arena.baritone().getBuilderProcess().isActive(), arena.ctx().playerFeet());
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[3][1][1];
        states[0][0][0] = Blocks.WHITE_CONCRETE.defaultBlockState();
        states[1][0][0] = Blocks.AIR.defaultBlockState();
        states[2][0][0] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
