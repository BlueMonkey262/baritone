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

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Crosses a sealed corridor whose one opening is a closed wooden fence gate.
 */
public final class PathFenceGateInteractionScenario extends TestScenario {

    private GoalBlock goal;
    private BetterBlockPos goalPos;
    private int lastProgressNote;
    private int bestDistance = Integer.MAX_VALUE;

    @Override
    public String name() {
        return "path-fence-gate-interaction";
    }

    @Override
    public String description() {
        return "Open and pass through the only fence gate in a sealed corridor";
    }

    @Override
    public int tickBudget() {
        return 20 * 180;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", false);
        settings.put("allowPlace", false);
        settings.put("allowParkour", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -4, -3, 28, -1, 3, "minecraft:stone");
        // A complete rectangular corridor means getting to the other side is possible only by
        // opening this gate, not by wandering around the ends of a decorative wall.
        arena.fill(-4, 0, -3, 28, 3, -3, "minecraft:stone");
        arena.fill(-4, 0, 3, 28, 3, 3, "minecraft:stone");
        arena.fill(-4, 0, -3, -4, 3, 3, "minecraft:stone");
        arena.fill(28, 0, -3, 28, 3, 3, "minecraft:stone");
        arena.fill(10, 0, -2, 10, 2, -1, "minecraft:stone");
        arena.fill(10, 0, 1, 10, 2, 2, "minecraft:stone");
        arena.setBlock(10, 0, 0, "minecraft:oak_fence_gate[facing=north,open=false]");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(10, 0, 0).is(Blocks.OAK_FENCE_GATE)
                && !arena.stateAt(10, 0, 0).getValue(BlockStateProperties.OPEN)
                && arena.stateAt(10, 1, 1).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        this.goalPos = arena.at(22, 0, 0);
        this.goal = new GoalBlock(this.goalPos);
        arena.note("goal is %s, beyond the corridor's closed gate at x+10", this.goalPos);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        int distance = (int) Math.sqrt(this.goalPos.distSqr(feet));
        this.bestDistance = Math.min(this.bestDistance, distance);
        if (this.goal.isInGoal(feet)) {
            return arena.stateAt(10, 0, 0).getValue(BlockStateProperties.OPEN)
                    ? Verdict.pass(String.format("opened the gate and crossed the corridor in %d ticks", elapsedTicks))
                    : Verdict.fail("reached the far side but the only gate is still closed");
        }
        if (elapsedTicks - this.lastProgressNote >= 20 * 30) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %s, %d blocks out, gate open=%b", elapsedTicks / 20, feet, distance,
                    arena.stateAt(10, 0, 0).getValue(BlockStateProperties.OPEN));
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("closest approach was %d blocks; gate open=%b; ended at %s", this.bestDistance,
                arena.stateAt(10, 0, 0).getValue(BlockStateProperties.OPEN), arena.ctx().playerFeet());
    }
}
