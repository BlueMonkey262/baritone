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
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** A material shortage with no registered source must settle instead of retrying forever. */
public final class RestockNoBoxBoundedScenario extends TestScenario {

    private static final int TARGET_X = 5;
    private int inactiveSince = -1;
    private boolean sawPlacement;

    @Override
    public String name() {
        return "restock-no-boxes-bounded";
    }

    @Override
    public String description() {
        return "Latch a missing-material result when no restock box is registered";
    }

    @Override
    public int tickBudget() {
        return 20 * 45;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockIndexBeforeBuild", false);
        settings.put("restockExtraStacks", 0);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, 0, -4, 16, 5, 4, "minecraft:air");
        arena.fill(-4, -3, -4, 16, -1, 4, "minecraft:stone");
        arena.command("clear @s");
        arena.command("give @s minecraft:white_concrete 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 0, 0).isAir()
                && arena.stateAt(TARGET_X + 1, 0, 0).isAir()
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("starting a two-block build with one white concrete and no registered box");
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), schematic(), arena.at(TARGET_X, 0, 0));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int placed = placed(arena);
        if (placed > 0) {
            this.sawPlacement = true;
        }
        if (placed == 2) {
            return Verdict.fail("the shortage was not exercised: both targets were placed without a box");
        }
        if (this.sawPlacement && !arena.baritone().getBuilderProcess().isActive()) {
            if (this.inactiveSince < 0) {
                this.inactiveSince = elapsedTicks;
            } else if (elapsedTicks - this.inactiveSince >= 20) {
                arena.note("observed after %d ticks: placed=%d/2, builderActive=%b, player=%s",
                        elapsedTicks, placed, arena.baritone().getBuilderProcess().isActive(),
                        arena.ctx().playerFeet());
                return Verdict.pass("placed the available block and latched the no-box shortage");
            }
        } else {
            this.inactiveSince = -1;
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("placed=%d/2, sawPlacement=%b, builderActive=%b, player=%s",
                placed(arena), this.sawPlacement,
                arena.baritone().getBuilderProcess().isActive(), arena.ctx().playerFeet());
    }

    private static int placed(TestArena arena) {
        int placed = 0;
        if (arena.stateAt(TARGET_X, 0, 0).is(Blocks.WHITE_CONCRETE)) {
            placed++;
        }
        if (arena.stateAt(TARGET_X + 1, 0, 0).is(Blocks.WHITE_CONCRETE)) {
            placed++;
        }
        return placed;
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[2][1][1];
        states[0][0][0] = Blocks.WHITE_CONCRETE.defaultBlockState();
        states[1][0][0] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
