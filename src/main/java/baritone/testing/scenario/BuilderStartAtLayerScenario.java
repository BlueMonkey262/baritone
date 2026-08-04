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
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Starting at layer one must leave lower-layer work untouched while building the selected layer. */
public final class BuilderStartAtLayerScenario extends TestScenario {

    private static final int TARGET_X = 6;

    @Override
    public String name() {
        return "builder-start-at-layer";
    }

    @Override
    public String description() {
        return "Build only the selected upper layer while leaving the lower layer unchanged";
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        settings.put("buildInLayers", true);
        settings.put("layerOrder", false);
        settings.put("startAtLayer", 1);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, 0, -4, 16, 5, 4, "minecraft:air");
        arena.fill(-4, -3, -4, 16, -1, 4, "minecraft:stone");
        // The lower stone is a physical support for the selected upper placement and is also the
        // world-state oracle that proves startAtLayer did not rewrite layer zero.
        arena.setBlock(TARGET_X, 0, 0, "minecraft:stone");
        arena.command("clear @s");
        arena.command("give @s minecraft:white_concrete 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 1, 0).isAir()
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("layer zero at %s starts as stone; layer one at %s is the selected white target",
                arena.at(TARGET_X, 0, 0), arena.at(TARGET_X, 1, 0));
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(arena.at(TARGET_X, 0, 0).x, arena.at(TARGET_X, 0, 0).y,
                        arena.at(TARGET_X, 0, 0).z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean lowerIntact = arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE);
        boolean upperBuilt = arena.stateAt(TARGET_X, 1, 0).is(Blocks.WHITE_CONCRETE);
        if (!lowerIntact) {
            return Verdict.fail("startAtLayer=1 changed the lower layer to " + arena.stateAt(TARGET_X, 0, 0));
        }
        if (upperBuilt) {
            arena.note("layer evidence at t=%d: lower=%s, upper=%s, carried white=%d, player=%s",
                    elapsedTicks, arena.stateAt(TARGET_X, 0, 0), arena.stateAt(TARGET_X, 1, 0),
                    ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE), arena.ctx().playerFeet());
            return Verdict.pass("built the selected upper layer while leaving the lower support unchanged");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("lower=%s, upper=%s, white=%d, builderActive=%b, player=%s",
                arena.stateAt(TARGET_X, 0, 0), arena.stateAt(TARGET_X, 1, 0),
                ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE),
                arena.baritone().getBuilderProcess().isActive(), arena.ctx().playerFeet());
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][2];
        states[0][0][0] = Blocks.WHITE_CONCRETE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
