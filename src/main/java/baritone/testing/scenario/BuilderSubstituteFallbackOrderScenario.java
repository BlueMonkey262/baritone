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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Substitute placement must skip an unavailable first alternative and use the next one. */
public final class BuilderSubstituteFallbackOrderScenario extends TestScenario {

    private static final int TARGET_X = 6;

    @Override
    public String name() {
        return "builder-substitute-fallback-order";
    }

    @Override
    public String description() {
        return "Place the first available configured substitute when the preferred alternative is absent";
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        Map<Block, List<Block>> substitutes = new HashMap<>();
        substitutes.put(Blocks.STONE, Arrays.asList(Blocks.WHITE_CONCRETE, Blocks.DIRT));
        settings.put("buildSubstitutes", substitutes);
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, 0, -4, 16, 4, 4, "minecraft:air");
        arena.fill(-4, -3, -4, 16, -1, 4, "minecraft:stone");
        arena.command("clear @s");
        arena.command("give @s minecraft:dirt 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 0, 0).isAir()
                && ScenarioInventory.countPlayer(arena, Items.DIRT) == 1
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("stone target x+%d has alternatives white_concrete then dirt; only dirt is staged",
                TARGET_X);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(arena.at(TARGET_X, 0, 0).x, arena.at(TARGET_X, 0, 0).y, arena.at(TARGET_X, 0, 0).z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BlockState target = arena.stateAt(TARGET_X, 0, 0);
        if (target.is(Blocks.WHITE_CONCRETE)) {
            return Verdict.fail("the unavailable first substitute was placed despite white concrete count=0");
        }
        if (target.is(Blocks.DIRT)) {
            arena.note("substitute evidence at t=%d: target=%s, carried dirt=%d, carried white=%d, player=%s",
                    elapsedTicks, target, ScenarioInventory.countPlayer(arena, Items.DIRT),
                    ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE), arena.ctx().playerFeet());
            return Verdict.pass("skipped the unavailable first alternative and placed the available dirt substitute");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("target=%s, dirt=%d, white=%d, builderActive=%b, player=%s",
                arena.stateAt(TARGET_X, 0, 0), ScenarioInventory.countPlayer(arena, Items.DIRT),
                ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE),
                arena.baritone().getBuilderProcess().isActive(), arena.ctx().playerFeet());
    }

    private static StaticSchematic schematic() {
        return new StaticSchematic(new BlockState[][][]{{{
                Blocks.STONE.defaultBlockState()
        }}});
    }
}
