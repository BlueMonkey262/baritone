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
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.Map;

/** A wrong-facing existing stair is accepted while a missing stair is still placed. */
public final class BuilderIgnoreDirectionScenario extends TestScenario {

    private static final int TARGET_X = 5;

    @Override
    public String name() {
        return "builder-ignore-direction";
    }

    @Override
    public String description() {
        return "Ignore an existing stair's facing while correcting an independent missing stair";
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        settings.put("buildIgnoreDirection", true);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, 0, -4, 16, 5, 4, "minecraft:air");
        arena.fill(-4, -3, -4, 16, -1, 4, "minecraft:stone");
        arena.setBlock(TARGET_X, 0, 0, "minecraft:oak_stairs[facing=east]");
        arena.command("clear @s");
        arena.command("give @s minecraft:oak_stairs 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        BlockState existing = arena.stateAt(TARGET_X, 0, 0);
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && existing.is(Blocks.OAK_STAIRS)
                && existing.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST
                && arena.stateAt(TARGET_X + 2, 0, 0).isAir()
                && ScenarioInventory.countPlayer(arena, Items.OAK_STAIRS) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("existing stair x+%d faces east; schematic wants north; missing control stair is x+%d",
                TARGET_X, TARGET_X + 2);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), schematic(), arena.at(TARGET_X, 0, 0));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BlockState existing = arena.stateAt(TARGET_X, 0, 0);
        boolean unchanged = existing.is(Blocks.OAK_STAIRS)
                && existing.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST;
        boolean controlBuilt = arena.stateAt(TARGET_X + 2, 0, 0).is(Blocks.OAK_STAIRS);
        if (!unchanged) {
            return Verdict.fail("buildIgnoreDirection altered the existing east-facing stair");
        }
        if (controlBuilt) {
            arena.note("observed after %d ticks: existingFacing=%s, control=%s, player=%s",
                    elapsedTicks, existing.getValue(BlockStateProperties.HORIZONTAL_FACING),
                    arena.stateAt(TARGET_X + 2, 0, 0), arena.ctx().playerFeet());
            return Verdict.pass("left the wrong-facing stair intact and placed the missing stair");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("existing=%s, control=%s, builderActive=%b, player=%s",
                arena.stateAt(TARGET_X, 0, 0), arena.stateAt(TARGET_X + 2, 0, 0),
                arena.baritone().getBuilderProcess().isActive(), arena.ctx().playerFeet());
    }

    private static StaticSchematic schematic() {
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        BlockState[][][] states = new BlockState[3][1][1];
        states[0][0][0] = stair;
        states[1][0][0] = Blocks.AIR.defaultBlockState();
        states[2][0][0] = stair;
        return new StaticSchematic(states);
    }
}
