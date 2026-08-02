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

package baritone.testing.scenario.gen;

import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/** Runs exactly one catalog entry and judges the exact resulting world BlockState. */
public final class PlacementCaseScenario extends TestScenario {

    private static final int TARGET_X = 6;
    private static final int TARGET_Z = 0;

    private final PlacementCase placementCase;
    private int builderInactiveSince = -1;

    public PlacementCaseScenario(PlacementCase placementCase) {
        this.placementCase = placementCase;
    }

    @Override
    public String name() {
        return this.placementCase.name();
    }

    @Override
    public String description() {
        return this.placementCase.description();
    }

    @Override
    public int tickBudget() {
        return 20 * 240;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", false);
        settings.put("buildOrientBeforePlacing", true);
        settings.put("buildIgnoreDirection", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        int targetY = this.placementCase.targetY();
        // Ordinary targets use the proven standing-level floor. UP-look targets are raised to y=3;
        // their schematic includes a stone column down to the arena floor, leaving the player at
        // y=0 with the above-target click face safely above the eye.
        arena.fill(-4, -3, -12, 24, -1, 12, "minecraft:stone");
        for (PlacementCase.Support support : this.placementCase.supports()) {
            Vec3i offset = support.offset();
            if (!support.state().is(Blocks.STONE)) {
                throw new IllegalStateException("generated support is not stone: " + support.state());
            }
            arena.setBlock(TARGET_X + offset.getX(), targetY + offset.getY(), TARGET_Z + offset.getZ(),
                    "minecraft:stone");
        }
        arena.command("clear @s");
        arena.command("give @s " + this.placementCase.itemId() + " 64");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        int targetY = this.placementCase.targetY();
        if (!arena.stateAt(TARGET_X, targetY, TARGET_Z).isAir()
                || !arena.stateAt(TARGET_X, targetY - 1, TARGET_Z).is(Blocks.STONE)
                || !arena.stateAt(0, -1, 0).is(Blocks.STONE)) {
            return false;
        }
        for (PlacementCase.Support support : this.placementCase.supports()) {
            Vec3i offset = support.offset();
            if (!arena.stateAt(TARGET_X + offset.getX(), targetY + offset.getY(), TARGET_Z + offset.getZ())
                    .equals(support.state())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start(TestArena arena) {
        int targetY = this.placementCase.targetY();
        BetterBlockPos origin = arena.at(
                TARGET_X + this.placementCase.minX(),
                targetY + this.placementCase.minY(),
                TARGET_Z + this.placementCase.minZ());
        arena.note("placing %s; target=%s; supports=%s", this.placementCase.name(),
                this.placementCase.targetState(), this.placementCase.supports());
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), this.placementCase.schematic(),
                new Vec3i(origin.x, origin.y, origin.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BlockStateView actual = actual(arena);
        if (actual.matches(this.placementCase)) {
            return Verdict.pass("world target reached " + this.placementCase.targetState()
                    + " in " + elapsedTicks + " ticks");
        }
        if (arena.baritone().getBuilderProcess().isPaused()) {
            return Verdict.fail("builder paused; wanted " + this.placementCase.targetState()
                    + ", found " + actual.state());
        }
        if (!arena.baritone().getBuilderProcess().isActive()) {
            if (this.builderInactiveSince < 0) {
                this.builderInactiveSince = elapsedTicks;
            } else if (elapsedTicks - this.builderInactiveSince > 40) {
                return Verdict.fail("builder stopped; wanted " + this.placementCase.targetState()
                        + ", found " + actual.state());
            }
        } else {
            this.builderInactiveSince = -1;
        }
        return null;
    }

    @Override
    public String progressMarker(TestArena arena) {
        return actual(arena).state().toString();
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "wanted " + this.placementCase.targetState() + ", found " + actual(arena).state();
    }

    private BlockStateView actual(TestArena arena) {
        return new BlockStateView(arena.stateAt(TARGET_X, this.placementCase.targetY(), TARGET_Z));
    }

    private record BlockStateView(net.minecraft.world.level.block.state.BlockState state) {
        boolean matches(PlacementCase placementCase) {
            return this.state.equals(placementCase.targetState());
        }
    }
}
