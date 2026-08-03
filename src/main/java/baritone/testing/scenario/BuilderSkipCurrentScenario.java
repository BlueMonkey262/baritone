/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
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
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** A skipped current block must remain intact while other schematic work stays active. */
public final class BuilderSkipCurrentScenario extends TestScenario {

    private static final int TARGET_X = 5;

    @Override
    public String name() {
        return "builder-skip-current";
    }

    @Override
    public String description() {
        return "Keep a skipped clearing target while placing a neighboring required block";
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        settings.put("buildSkipBlocks", List.of(Blocks.STONE));
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, 0, -4, 16, 5, 4, "minecraft:air");
        arena.fill(-4, -3, -4, 16, -1, 4, "minecraft:stone");
        arena.setBlock(TARGET_X, 0, 0, "minecraft:stone");
        arena.command("clear @s");
        arena.command("give @s minecraft:white_concrete 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X + 2, 0, 0).isAir()
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("stone at x+%d is skipped as current work; neighboring concrete target is x+%d",
                TARGET_X, TARGET_X + 2);
        var origin = arena.at(TARGET_X, 0, 0);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(origin.x, origin.y, origin.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean skippedIntact = arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE);
        boolean neighborBuilt = arena.stateAt(TARGET_X + 2, 0, 0).is(Blocks.WHITE_CONCRETE);
        arena.note("skip evidence at t=%d: skipped=%s, neighbor=%s, player=%s",
                elapsedTicks, arena.stateAt(TARGET_X, 0, 0).getBlock().getName().getString(),
                arena.stateAt(TARGET_X + 2, 0, 0).getBlock().getName().getString(), arena.ctx().playerFeet());
        if (!skippedIntact) {
            return Verdict.fail("buildSkipBlocks did not protect the skipped current stone");
        }
        return neighborBuilt
                ? Verdict.pass("left the skipped current block intact and built the independent neighbor")
                : null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "skipped=" + arena.stateAt(TARGET_X, 0, 0).getBlock()
                + ", neighbor=" + arena.stateAt(TARGET_X + 2, 0, 0).getBlock()
                + ", builder active=" + arena.baritone().getBuilderProcess().isActive();
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[3][1][1];
        states[0][0][0] = Blocks.AIR.defaultBlockState();
        states[1][0][0] = Blocks.AIR.defaultBlockState();
        states[2][0][0] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
