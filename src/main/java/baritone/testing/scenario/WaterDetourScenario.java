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

import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import baritone.pathing.path.PathExecutor;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * A pool across the route with a dry way around it, and the question of which one gets taken.
 * <p>
 * The two options are deliberately close together. Crossing is eleven blocks of water; going around
 * is about sixteen blocks of extra walking. That closeness is the point: a pool with no way around
 * tests nothing, and one where the detour is obviously shorter would pass no matter what the costs
 * said.
 * <p>
 * <b>By arithmetic the detour should already win</b> at {@code waterCostMultiplier} 2.0 -- roughly
 * 166 against 288 -- and in the run of 2026-07-31 it did not: the player walked straight at the pool
 * and entered it on the direct line. So the interesting question is no longer "is 2.0 enough" but
 * "does this setting decide the route at all", which is why the same course also runs at 8 and 30,
 * and why {@code recordFirstPath} records whether the path reached the goal or stopped short.
 */
public final class WaterDetourScenario extends TestScenario {

    private static final int GOAL_X = 30;

    /**
     * How far into the pool counts as crossing it rather than clipping its corner.
     * <p>
     * The direct route runs along z=0; the dry way round starts at z=7. A path that takes the
     * detour and cuts the corner while sprinting gets wet in the z=5..6 range, which is not the
     * behaviour under test. Distinguishing the two by position is the only way to tell a bad
     * decision from a sloppy one -- a plain wet-tick count reads them identically, which is how
     * the first version of this scenario produced a failure nobody could interpret.
     */
    private static final int DEEP_Z = 2;

    /** Ticks properly inside the pool before it counts as having chosen to swim. */
    private static final int DEEP_TOLERANCE = 10;

    /** Ticks wet anywhere at all, before something is wrong regardless of where. */
    private static final int SPLASH_TOLERANCE = 60;

    /**
     * The multiplier this variant runs at, or {@code null} to run at whatever the shipped default
     * is. Several variants exist so that one game run can answer whether the setting has any
     * authority over the decision at all, rather than only whether one particular value does.
     */
    private final Double multiplier;

    private boolean recordedPath;
    private BetterBlockPos goalPos;
    private GoalBlock goal;
    private int ticksInWater;
    private int ticksDeepInWater;
    private String firstWetAt;
    private int lastProgressNote;
    private int bestDistance = Integer.MAX_VALUE;

    public WaterDetourScenario() {
        this(null);
    }

    public WaterDetourScenario(Double multiplier) {
        this.multiplier = multiplier;
    }

    @Override
    public String name() {
        return this.multiplier == null
                ? "water-detour"
                : "water-detour-x" + this.multiplier.intValue();
    }

    @Override
    public String description() {
        return this.multiplier == null
                ? "Prefer a longer dry route over a shorter swim, at the default waterCostMultiplier"
                : "The same crossing at waterCostMultiplier " + this.multiplier
                        + ", to find out whether the setting has any authority over the decision";
    }

    @Override
    public int tickBudget() {
        return 20 * 120;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        // No waterCostMultiplier here on purpose: this scenario exists to hold the shipped default
        // honest, so it has to run with whatever that default currently is.
        settings.put("allowBreak", false);
        settings.put("allowPlace", false);
        settings.put("allowParkour", false);
        if (this.multiplier != null) {
            settings.put("waterCostMultiplier", this.multiplier);
        }
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, 0, -16, 40, 12, 16, "minecraft:air");
        arena.fill(-4, -4, -16, 40, -1, 16, "minecraft:stone");

        // A pool two deep, so crossing it means swimming rather than wading. It stops at z=6,
        // leaving a dry strip along the far side wide enough to walk.
        arena.fill(12, -2, -16, 22, -1, 6, "minecraft:water");

        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(17, -1, 0).is(Blocks.WATER)
                && arena.stateAt(17, -1, 10).is(Blocks.STONE)
                && arena.stateAt(GOAL_X, -1, 0).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        this.goalPos = arena.at(GOAL_X, 0, 0);
        this.goal = new GoalBlock(this.goalPos);
        arena.note("goal is %s, across an 11 block pool with a dry strip at z+7 and beyond", this.goalPos);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public TestScenario.Verdict poll(TestArena arena, int elapsedTicks) {
        recordFirstPath(arena);

        BetterBlockPos feet = arena.ctx().playerFeet();
        int relativeZ = feet.z - arena.origin().z;
        if (arena.ctx().player().isInWater()) {
            this.ticksInWater++;
            if (this.firstWetAt == null) {
                this.firstWetAt = String.format("x+%d z+%d at t=%ds",
                        feet.x - arena.origin().x, relativeZ, elapsedTicks / 20);
                arena.note("first got wet at %s", this.firstWetAt);
            }
            if (relativeZ <= DEEP_Z) {
                this.ticksDeepInWater++;
            }
        }

        int distance = (int) Math.sqrt(this.goalPos.distSqr(feet));
        this.bestDistance = Math.min(this.bestDistance, distance);

        if (this.ticksDeepInWater > DEEP_TOLERANCE) {
            return Verdict.fail(
                    "swam the pool: %d ticks inside it (z+%d or less) by t=%ds, first wet at %s -- "
                            + "the crossing was priced as cheaper than the dry route around it",
                    this.ticksDeepInWater, DEEP_Z, elapsedTicks / 20, this.firstWetAt
            );
        }
        if (this.ticksInWater > SPLASH_TOLERANCE) {
            return Verdict.fail(
                    "wet for %d ticks without ever being properly inside the pool (first at %s) -- "
                            + "this is the dry route being taken badly, not the wrong route being chosen",
                    this.ticksInWater, this.firstWetAt
            );
        }
        if (this.goal.isInGoal(feet)) {
            return Verdict.pass(String.format(
                    "went around, reaching the goal in %d ticks (%.1fs); %d ticks wet, %d of them "
                            + "inside the pool%s",
                    elapsedTicks, elapsedTicks / 20.0, this.ticksInWater, this.ticksDeepInWater,
                    this.firstWetAt == null ? "" : ", first at " + this.firstWetAt
            ));
        }
        if (elapsedTicks - this.lastProgressNote >= 20 * 20) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %s (z+%d), %d blocks out, %d ticks wet",
                    elapsedTicks / 20, feet, relativeZ, distance, this.ticksInWater);
        }
        return null;
    }

    /**
     * Records what the first computed path actually was.
     * <p>
     * This distinguishes the two ways the swim can win, which the verdict alone cannot.
     * {@code AbstractNodeCostSearch} scores fallback nodes as
     * {@code estimatedCostToGoal + cost / COEFFICIENT} with coefficients up to 10, so a search that
     * runs out of time returns whatever got nearest the goal with accumulated cost heavily
     * discounted. If the path stops short of the goal, the route was picked by that fallback and no
     * value of {@code waterCostMultiplier} would have changed it. If the path reaches the goal, the
     * search really did compare the two routes and preferred to swim, which is a tuning question.
     */
    private void recordFirstPath(TestArena arena) {
        if (this.recordedPath) {
            return;
        }
        PathExecutor executor = arena.baritone().getPathingBehavior().getCurrent();
        if (executor == null || executor.getPath() == null) {
            return;
        }
        this.recordedPath = true;
        IPath path = executor.getPath();
        BetterBlockPos dest = path.getDest();
        boolean reachesGoal = path.getGoal().isInGoal(dest);
        arena.note("first path: %d blocks, %d nodes considered, ends at x+%d z+%d, reaches goal: %b",
                path.length(),
                path.getNumNodesConsidered(),
                dest.x - arena.origin().x,
                dest.z - arena.origin().z,
                reachesGoal);
        if (!reachesGoal) {
            arena.note("the path stops short, so its route came from the timed-out best-so-far "
                    + "fallback, where accumulated cost is divided by up to 10 -- the water penalty "
                    + "is largely bypassed rather than outweighed");
        }
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format(
                "closest approach %d blocks, %d ticks wet (%d inside the pool, first at %s); ended at %s",
                this.bestDistance, this.ticksInWater, this.ticksDeepInWater,
                this.firstWetAt == null ? "never" : this.firstWetAt,
                arena.ctx().playerFeet()
        );
    }
}
