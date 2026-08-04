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
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An ignored property is accepted, while a different property remains significant. */
public final class BuilderIgnorePropertiesScenario extends TestScenario {

    private static final int ACCEPTED_X = 5;
    private static final int CORRECTED_X = 7;

    @Override
    public String name() {
        return "builder-ignore-properties";
    }

    @Override
    public String description() {
        return "Ignore stair facing while still correcting a separate non-ignored stair-shape mismatch";
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        settings.put("buildIgnoreProperties", List.of("facing"));
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, 0, -4, 16, 5, 4, "minecraft:air");
        arena.fill(-4, -3, -4, 16, -1, 4, "minecraft:stone");
        arena.setBlock(ACCEPTED_X, 0, 0, "minecraft:oak_stairs[facing=east,shape=straight]");
        arena.setBlock(CORRECTED_X, 0, 0, "minecraft:oak_stairs[facing=east,shape=inner_left]");
        arena.command("clear @s");
        arena.command("give @s minecraft:oak_stairs 2");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        BlockState accepted = arena.stateAt(ACCEPTED_X, 0, 0);
        BlockState currentCorrected = arena.stateAt(CORRECTED_X, 0, 0);
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && accepted.is(Blocks.OAK_STAIRS)
                && accepted.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST
                && accepted.getValue(BlockStateProperties.STAIRS_SHAPE) == StairsShape.STRAIGHT
                && currentCorrected.is(Blocks.OAK_STAIRS)
                && currentCorrected.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST
                && currentCorrected.getValue(BlockStateProperties.STAIRS_SHAPE) == StairsShape.INNER_LEFT
                && ScenarioInventory.countPlayer(arena, Items.OAK_STAIRS) == 2;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("accepted stair x+%d differs only in facing; corrected stair x+%d differs in facing and shape",
                ACCEPTED_X, CORRECTED_X);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(arena.at(ACCEPTED_X, 0, 0).x, arena.at(ACCEPTED_X, 0, 0).y,
                        arena.at(ACCEPTED_X, 0, 0).z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BlockState accepted = arena.stateAt(ACCEPTED_X, 0, 0);
        BlockState currentCorrected = arena.stateAt(CORRECTED_X, 0, 0);
        boolean acceptedUnchanged = accepted.is(Blocks.OAK_STAIRS)
                && accepted.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST
                && accepted.getValue(BlockStateProperties.STAIRS_SHAPE) == StairsShape.STRAIGHT;
        if (!acceptedUnchanged) {
            return Verdict.fail("the facing-only mismatch was needlessly rebuilt: %s", accepted);
        }
        boolean correctedState = currentCorrected.is(Blocks.OAK_STAIRS)
                && currentCorrected.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH
                && currentCorrected.getValue(BlockStateProperties.STAIRS_SHAPE) == StairsShape.STRAIGHT;
        if (correctedState) {
            arena.note("property evidence at t=%d: accepted=%s, corrected=%s, oak stairs carried=%d, player=%s",
                    elapsedTicks, accepted, arena.stateAt(CORRECTED_X, 0, 0),
                    ScenarioInventory.countPlayer(arena, Items.OAK_STAIRS), arena.ctx().playerFeet());
            return Verdict.pass("ignored only facing and corrected the non-ignored stair shape mismatch");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("accepted=%s, corrected=%s, stairs=%d, builderActive=%b, player=%s",
                arena.stateAt(ACCEPTED_X, 0, 0), arena.stateAt(CORRECTED_X, 0, 0),
                ScenarioInventory.countPlayer(arena, Items.OAK_STAIRS),
                arena.baritone().getBuilderProcess().isActive(), arena.ctx().playerFeet());
    }

    private static StaticSchematic schematic() {
        BlockState desired = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT);
        BlockState[][][] states = new BlockState[3][1][1];
        states[0][0][0] = desired;
        states[1][0][0] = Blocks.AIR.defaultBlockState();
        states[2][0][0] = desired;
        return new StaticSchematic(states);
    }
}
