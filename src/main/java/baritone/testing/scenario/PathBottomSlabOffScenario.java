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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.HashMap;
import java.util.Map;

/** A bottom-slab shortcut must be rejected when the setting is disabled. */
public final class PathBottomSlabOffScenario extends TestScenario {

    private static final int GOAL_X = 6;
    private GoalBlock goal;
    private boolean sawDetour;
    private boolean enteredSlabRoute;

    @Override
    public String name() {
        return "path-bottom-slab-off";
    }

    @Override
    public String description() {
        return "Take the full-block side route instead of walking across a bottom-slab shortcut";
    }

    @Override
    public int tickBudget() {
        return 20 * 150;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", false);
        settings.put("allowPlace", false);
        settings.put("allowParkour", false);
        settings.put("allowWalkOnBottomSlab", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -4, 12, -2, 4, "minecraft:stone");
        arena.fill(0, -1, 0, 0, -1, 1, "minecraft:stone");
        arena.fill(5, -1, 0, 5, -1, 1, "minecraft:stone");
        arena.fill(GOAL_X, -1, 0, GOAL_X, -1, 1, "minecraft:stone");
        arena.fill(0, -1, 1, GOAL_X, -1, 1, "minecraft:stone");
        for (int x = 1; x < 5; x++) {
            arena.setBlock(x, -1, 0, "minecraft:stone_slab[type=bottom]");
        }
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        if (!arena.stateAt(0, -2, 0).is(Blocks.STONE)
                || !arena.stateAt(0, -1, 0).is(Blocks.STONE)
                || !arena.stateAt(GOAL_X, -1, 0).is(Blocks.STONE)
                || !arena.stateAt(0, -1, 1).is(Blocks.STONE)) {
            return false;
        }
        for (int x = 1; x < 5; x++) {
            if (!(arena.stateAt(x, -1, 0).getBlock() instanceof SlabBlock)
                    || arena.stateAt(x, -1, 0).getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos goalPos = arena.at(GOAL_X, 0, 0);
        this.goal = new GoalBlock(goalPos);
        arena.note("goal=%s; bottom slabs span x+1..4 at z=0 and full blocks provide the z=1 detour",
                goalPos);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        int x = feet.x - arena.origin().x;
        int z = feet.z - arena.origin().z;
        this.sawDetour |= x >= 1 && x <= 5 && z == 1;
        this.enteredSlabRoute |= x >= 1 && x <= 4 && z == 0;
        if (this.enteredSlabRoute) {
            return Verdict.fail("the disabled bottom-slab route was entered at %s", feet);
        }
        if (this.goal.isInGoal(feet)) {
            arena.note("slab evidence at t=%d: goal=%s, detour=%b, slabRoute=%b, player=%s",
                    elapsedTicks, feet, this.sawDetour, this.enteredSlabRoute, feet);
            return this.sawDetour
                    ? Verdict.pass("reached the goal by the full-block detour with the bottom-slab route untouched")
                    : Verdict.fail("reached the goal without taking the measured full-block detour");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("goal=%s, detour=%b, slabRoute=%b, slabsIntact=%b, player=%s",
                this.goal != null && this.goal.isInGoal(arena.ctx().playerFeet()), this.sawDetour,
                this.enteredSlabRoute, slabsIntact(arena), arena.ctx().playerFeet());
    }

    private static boolean slabsIntact(TestArena arena) {
        for (int x = 1; x < 5; x++) {
            if (!(arena.stateAt(x, -1, 0).getBlock() instanceof SlabBlock)) {
                return false;
            }
        }
        return true;
    }
}
