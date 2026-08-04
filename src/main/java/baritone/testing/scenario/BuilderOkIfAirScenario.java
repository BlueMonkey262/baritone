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
import java.util.List;
import java.util.Map;

/** A desired block in okIfAir is accepted as air while a separate required target is built. */
public final class BuilderOkIfAirScenario extends TestScenario {

    private static final int TARGET_X = 5;

    @Override
    public String name() {
        return "builder-ok-if-air";
    }

    @Override
    public String description() {
        return "Accept an air position listed in okIfAir while placing a separate required concrete target";
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        settings.put("okIfAir", List.of(Blocks.WHITE_CONCRETE));
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, 0, -4, 16, 4, 4, "minecraft:air");
        arena.fill(-4, -3, -4, 16, -1, 4, "minecraft:stone");
        arena.command("clear @s");
        arena.command("give @s minecraft:white_concrete 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 0, 0).isAir()
                && arena.stateAt(TARGET_X + 2, 0, 0).isAir()
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("okIfAir white target at x+%d must remain air; required control target is x+%d",
                TARGET_X, TARGET_X + 2);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(arena.at(TARGET_X, 0, 0).x, arena.at(TARGET_X, 0, 0).y, arena.at(TARGET_X, 0, 0).z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean acceptedAir = arena.stateAt(TARGET_X, 0, 0).isAir();
        boolean controlBuilt = arena.stateAt(TARGET_X + 2, 0, 0).is(Blocks.WHITE_CONCRETE);
        if (!acceptedAir) {
            return Verdict.fail("okIfAir target changed to %s instead of remaining air",
                    arena.stateAt(TARGET_X, 0, 0).getBlock());
        }
        if (controlBuilt) {
            arena.note("okIfAir evidence at t=%d: optional target=%s, control target=%s, carried white=%d, player=%s",
                    elapsedTicks, arena.stateAt(TARGET_X, 0, 0), arena.stateAt(TARGET_X + 2, 0, 0),
                    ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE), arena.ctx().playerFeet());
            return Verdict.pass("left the okIfAir position empty and placed the independent required target");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("optional=%s, control=%s, carried white=%d, builderActive=%b, player=%s",
                arena.stateAt(TARGET_X, 0, 0), arena.stateAt(TARGET_X + 2, 0, 0),
                ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE),
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
