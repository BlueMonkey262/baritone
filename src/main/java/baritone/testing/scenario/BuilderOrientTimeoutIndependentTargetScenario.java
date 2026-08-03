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

import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import baritone.utils.schematic.StaticSchematic;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.Map;

/** An unreachable oriented target must not poison independent placement work. */
public final class BuilderOrientTimeoutIndependentTargetScenario extends TestScenario {

    private static final int IMPOSSIBLE_X = 4;
    private static final int INDEPENDENT_X = 10;
    private static final int ORIENT_TIMEOUT = 20;

    private boolean sawIndependent;

    @Override
    public String name() {
        return "builder-orient-timeout-independent-target";
    }

    @Override
    public String description() {
        return "Continue an oriented build after one sealed target exceeds its orientation timeout";
    }

    @Override
    public int tickBudget() {
        return 20 * 180;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", false);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        settings.put("buildOrientBeforePlacing", true);
        settings.put("buildIgnoreDirection", false);
        settings.put("buildOrientStandDistance", 2);
        settings.put("buildOrientTimeoutTicks", ORIENT_TIMEOUT);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -12, 20, -1, 12, "minecraft:stone");
        arena.fill(-4, 0, -12, 20, 4, 12, "minecraft:air");
        // Bedrock seals every practical standing and line-of-sight position around the first
        // stair. The floor remains stone, but the player cannot reach a placeable face through the
        // cage while allowBreak is off.
        arena.fill(IMPOSSIBLE_X - 1, 0, 0, IMPOSSIBLE_X + 1, 1, 0, "minecraft:bedrock");
        arena.fill(IMPOSSIBLE_X, 0, -1, IMPOSSIBLE_X, 1, 1, "minecraft:bedrock");
        arena.setBlock(IMPOSSIBLE_X, 0, 0, "minecraft:air");
        arena.setBlock(IMPOSSIBLE_X, 1, 0, "minecraft:bedrock");
        arena.command("clear @s");
        arena.command("give @s minecraft:oak_stairs 2");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(IMPOSSIBLE_X, 0, 0).isAir()
                && arena.stateAt(IMPOSSIBLE_X, 1, 0).is(Blocks.BEDROCK)
                && arena.stateAt(IMPOSSIBLE_X - 1, 0, 0).is(Blocks.BEDROCK)
                && arena.stateAt(IMPOSSIBLE_X + 1, 0, 0).is(Blocks.BEDROCK)
                && arena.stateAt(INDEPENDENT_X, 0, 0).isAir()
                && ScenarioInventory.countPlayer(arena, Items.OAK_STAIRS) == 2;
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos origin = arena.at(IMPOSSIBLE_X, 0, 0);
        arena.note("sealed oriented target=%s; independent target=%s; timeout=%d ticks",
                origin, arena.at(INDEPENDENT_X, 0, 0), ORIENT_TIMEOUT);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), schematic(), new Vec3i(origin.x, origin.y, origin.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BlockState impossible = arena.stateAt(IMPOSSIBLE_X, 0, 0);
        BlockState independent = arena.stateAt(INDEPENDENT_X, 0, 0);
        if (!impossible.isAir()) {
            return Verdict.fail("the sealed oriented target changed to %s instead of remaining a distinct unresolved target",
                    impossible.getBlock().getName().getString());
        }
        if (independent.is(Blocks.OAK_STAIRS)) {
            Direction facing = independent.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (facing != Direction.EAST) {
                return Verdict.fail("the independent stair was placed facing %s instead of east", facing.getName());
            }
            this.sawIndependent = true;
        }
        if (this.sawIndependent && elapsedTicks >= ORIENT_TIMEOUT + 60) {
            return Verdict.pass("the independent stair was placed correctly while the impossible target remained air");
        }
        if (elapsedTicks >= ORIENT_TIMEOUT + 120
                && !arena.baritone().getBuilderProcess().isActive()) {
            return Verdict.fail("builder stopped without placing the independent stair; impossible target remained air");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("impossible=%s, independent=%s, independentPlaced=%b, builderActive=%b, player=%s",
                arena.stateAt(IMPOSSIBLE_X, 0, 0).getBlock().getName().getString(),
                arena.stateAt(INDEPENDENT_X, 0, 0).getBlock().getName().getString(), this.sawIndependent,
                arena.baritone().getBuilderProcess().isActive(), arena.ctx().playerFeet());
    }

    private static StaticSchematic schematic() {
        int width = INDEPENDENT_X - IMPOSSIBLE_X + 1;
        BlockState[][][] states = new BlockState[width][1][1];
        for (int x = 0; x < width; x++) {
            states[x][0][0] = Blocks.AIR.defaultBlockState();
        }
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        states[0][0][0] = stair;
        states[width - 1][0][0] = stair;
        return new StaticSchematic(states);
    }
}
