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
import baritone.api.event.events.PathEvent;
import baritone.api.pathing.goals.Goal;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import baritone.utils.schematic.StaticSchematic;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Documents an unreachable build target that should settle to a bounded result rather than keep
 * re-searching an unchanged path after its first movement is unreachable.
 */
public final class UnreachableBuildTargetScenario extends TestScenario {

    private static final int TARGET_X = 12;
    private static final int TARGET_Z = 0;
    /** More than four starts per second for an unchanged target is a tight replan loop. */
    private static final int MAX_SEARCHES_PER_SECOND = 4;
    private static final int OBSERVATION_WINDOW_TICKS = 20;
    private static final int BOUNDED_RESULT_TICKS = 20 * 12;

    private BetterBlockPos target;
    private int calculationStarts;
    private int windowStartTicks;
    private int windowStartCalculations;
    private String goalSignature;
    private boolean goalChanged;

    @Override
    public String name() {
        return "unreachable-build-target";
    }

    @Override
    public String description() {
        return "An enclosed build target reaches a bounded result instead of repeatedly replanning";
    }

    @Override
    public int tickBudget() {
        return 20 * 30;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", false);
        settings.put("allowPlace", true);
        settings.put("allowInventory", true);
        settings.put("restockFromBoxes", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -12, 24, -1, 12, "minecraft:stone");
        arena.fill(-4, 0, -12, 24, 6, 12, "minecraft:air");
        // The target has its normal floor support but is otherwise sealed in bedrock, so neither a
        // stand position nor a line of sight for placement exists.
        arena.fill(TARGET_X - 1, 0, TARGET_Z - 1, TARGET_X + 1, 2, TARGET_Z + 1, "minecraft:bedrock");
        arena.setBlock(TARGET_X, 0, TARGET_Z, "minecraft:air");
        arena.command("give @s minecraft:white_concrete 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(TARGET_X, 0, TARGET_Z).isAir()
                && arena.stateAt(TARGET_X, -1, TARGET_Z).is(Blocks.STONE)
                && arena.stateAt(TARGET_X - 1, 1, TARGET_Z).is(Blocks.BEDROCK);
    }

    @Override
    public void start(TestArena arena) {
        this.target = arena.at(TARGET_X, 0, TARGET_Z);
        this.calculationStarts = 0;
        this.windowStartTicks = 0;
        this.windowStartCalculations = 0;
        this.goalSignature = null;
        this.goalChanged = false;
        arena.note("sealed build target is %s; breaking is disabled", this.target);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(this.target.x, this.target.y, this.target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!arena.stateAt(TARGET_X, 0, TARGET_Z).isAir()) {
            return Verdict.fail("sealed target unexpectedly changed to %s", arena.stateAt(TARGET_X, 0, TARGET_Z));
        }
        Goal goal = arena.baritone().getPathingBehavior().getGoal();
        if (goal != null) {
            String currentGoal = goal.toString();
            if (this.goalSignature == null) {
                this.goalSignature = currentGoal;
            } else if (!this.goalSignature.equals(currentGoal)) {
                this.goalChanged = true;
            }
        }
        if (arena.baritone().getBuilderProcess().isPaused() || !arena.baritone().getBuilderProcess().isActive()) {
            return Verdict.pass(String.format("builder reached a bounded result after %d ticks with target still unreachable", elapsedTicks));
        }
        if (elapsedTicks - this.windowStartTicks >= OBSERVATION_WINDOW_TICKS) {
            int calculationsInWindow = this.calculationStarts - this.windowStartCalculations;
            if (!this.goalChanged && calculationsInWindow > MAX_SEARCHES_PER_SECOND) {
                return Verdict.fail("started %d path searches in %.1fs for the identical sealed target with no world-state change",
                        calculationsInWindow, OBSERVATION_WINDOW_TICKS / 20.0);
            }
            this.windowStartTicks = elapsedTicks;
            this.windowStartCalculations = this.calculationStarts;
        }
        if (elapsedTicks >= BOUNDED_RESULT_TICKS) {
            return Verdict.fail("builder remained active for %d ticks, but started only %d path searches per second%s",
                    elapsedTicks, this.calculationStarts / (BOUNDED_RESULT_TICKS / 20),
                    this.goalChanged ? " while its goal changed" : "");
        }
        return null;
    }

    @Override
    public String progressMarker(TestArena arena) {
        return String.format("targetAir=%b, builderActive=%b, paused=%b, calculationStarts=%d",
                arena.stateAt(TARGET_X, 0, TARGET_Z).isAir(),
                arena.baritone().getBuilderProcess().isActive(),
                arena.baritone().getBuilderProcess().isPaused(), this.calculationStarts);
    }

    @Override
    public void onPathEvent(PathEvent event) {
        if (event == PathEvent.CALC_STARTED) {
            this.calculationStarts++;
        }
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "target=" + this.target + ", builderActive=" + arena.baritone().getBuilderProcess().isActive()
                + ", pathing=" + arena.baritone().getPathingBehavior().isPathing()
                + ", calculationStarts=" + this.calculationStarts + ", goalChanged=" + this.goalChanged;
    }

    private static StaticSchematic schematic() {
        return new StaticSchematic(new BlockState[][][]{{{Blocks.WHITE_CONCRETE.defaultBlockState()}}});
    }
}
