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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;

/**
 * A randomly generated obstacle course, reproducible from its seed.
 * <p>
 * This is the volume engine: one generator yields as many distinct courses as there are seeds, and a
 * failure is reported as a seed number that rebuilds the identical arena on demand. Hand-written
 * scenarios test the situations someone thought of; this tests the ones nobody did.
 * <p>
 * <b>Every course is solvable by construction.</b> Breaking and placing are off, so a generator free
 * to drop a solid wall across the corridor would produce failures that say nothing about Baritone --
 * an unreachable goal and a pathfinder that gave up look identical in a report, as the very first
 * run of {@code pathing-course} demonstrated. So every feature here is built around a lane that is
 * left clear: walls get a gap, pits get a ramp out, pools leave a dry margin. If Baritone cannot
 * finish one of these, a route existed and it failed to find or follow it.
 */
public final class FuzzPathingScenario extends TestScenario {

    private static final int GOAL_X = 40;

    /** Half-width of the corridor. Features span this; the arena wipe covers +/-20. */
    private static final int HALF_WIDTH = 12;

    /** Width of the lane a feature must leave passable. */
    private static final int LANE = 2;

    private final int seed;

    private BetterBlockPos goalPos;
    private GoalBlock goal;
    private int bestDistance = Integer.MAX_VALUE;
    private int ticksWithoutProgress;
    private int lastProgressNote;
    private String featureSummary = "";

    public FuzzPathingScenario(int seed) {
        this.seed = seed;
    }

    @Override
    public String name() {
        return "fuzz-" + this.seed;
    }

    @Override
    public String description() {
        return "Randomly generated obstacle course, seed " + this.seed + " (solvable by construction)";
    }

    @Override
    public int tickBudget() {
        return 20 * 150;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        // No breaking or placing: a course that can be tunnelled through is not being navigated.
        settings.put("allowBreak", false);
        settings.put("allowPlace", false);
        return settings;
    }

    /** What a course is made of, decided without touching the world so it can be unit tested. */
    public enum Kind { WALL, PIT, POOL, PILLARS }

    /**
     * One obstacle. {@code a}/{@code b} carry the per-kind parameters: height and gap for a wall,
     * width and depth for a pit, width and dry lane for a pool, count for pillars.
     */
    public static final class Feature {
        public final Kind kind;
        public final int x;
        public final int a;
        public final int b;
        public final int pillarSeed;

        Feature(Kind kind, int x, int a, int b, int pillarSeed) {
            this.kind = kind;
            this.x = x;
            this.a = a;
            this.b = b;
            this.pillarSeed = pillarSeed;
        }
    }

    /**
     * Decides a course from its seed. Pure, so the distribution of what it generates can be
     * checked without a running game -- see {@code FuzzPathingScenarioTest}.
     * <p>
     * {@link SplittableRandom} rather than {@link Random}: seeds here are the small consecutive
     * integers 1..N, and {@code Random} correlates badly across those. The first smoke run produced
     * no walls at all in sixteen features drawn from six consecutive seeds, which is a one-percent
     * event by chance and a much likelier sign that the seeds were not really independent. A
     * generator whose courses secretly resemble each other is worse than a smaller suite, because
     * it looks like coverage.
     */
    public static List<Feature> plan(int seed) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Feature> features = new ArrayList<>();
        int x = 6;
        while (x < GOAL_X - 6) {
            int gapZ = random.nextInt(2 * HALF_WIDTH - LANE - 1) - HALF_WIDTH;
            Kind kind = Kind.values()[random.nextInt(Kind.values().length)];
            switch (kind) {
                case WALL -> {
                    int height = 2 + random.nextInt(4);
                    features.add(new Feature(Kind.WALL, x, height, gapZ, 0));
                    x += 4 + random.nextInt(4);
                }
                case PIT -> {
                    int width = 2 + random.nextInt(3);
                    int depth = 1 + random.nextInt(3);
                    features.add(new Feature(Kind.PIT, x, width, depth, 0));
                    x += width + 3 + random.nextInt(3);
                }
                case POOL -> {
                    int width = 2 + random.nextInt(4);
                    features.add(new Feature(Kind.POOL, x, width, gapZ, 0));
                    x += width + 3 + random.nextInt(3);
                }
                default -> {
                    int count = 2 + random.nextInt(4);
                    features.add(new Feature(Kind.PILLARS, x, count, 0, random.nextInt()));
                    x += 4 + random.nextInt(4);
                }
            }
        }
        return features;
    }

    @Override
    public void stage(TestArena arena) {
        StringBuilder summary = new StringBuilder();

        // Floor deep enough that pits never reach through it.
        arena.fill(-4, -6, -HALF_WIDTH, GOAL_X + 6, -1, HALF_WIDTH, "minecraft:stone");

        for (Feature feature : plan(this.seed)) {
            switch (feature.kind) {
                case WALL -> {
                    wall(arena, feature.x, feature.a, feature.b);
                    summary.append(String.format("wall(x=%d,h=%d,gap=%d) ", feature.x, feature.a, feature.b));
                }
                case PIT -> {
                    pit(arena, feature.x, feature.a, feature.b);
                    summary.append(String.format("pit(x=%d,w=%d,d=%d) ", feature.x, feature.a, feature.b));
                }
                case POOL -> {
                    pool(arena, feature.x, feature.a, feature.b);
                    summary.append(String.format("pool(x=%d,w=%d,dry=%d) ", feature.x, feature.a, feature.b));
                }
                default -> {
                    pillars(arena, feature.x, feature.a, new Random(feature.pillarSeed));
                    summary.append(String.format("pillars(x=%d,n=%d) ", feature.x, feature.a));
                }
            }
        }

        this.featureSummary = summary.toString().trim();
        arena.teleport(0, 0, 0);
    }

    /** A barrier across the corridor with one gap left open. */
    private void wall(TestArena arena, int x, int height, int gapZ) {
        if (gapZ > -HALF_WIDTH) {
            arena.fill(x, 0, -HALF_WIDTH, x, height, gapZ - 1, "minecraft:stone");
        }
        if (gapZ + LANE <= HALF_WIDTH) {
            arena.fill(x, 0, gapZ + LANE, x, height, HALF_WIDTH, "minecraft:stone");
        }
    }

    /**
     * A trench with a staircase out of the far side, so it can always be climbed back up without
     * placing blocks.
     */
    private void pit(TestArena arena, int x, int width, int depth) {
        arena.fill(x, -depth, -HALF_WIDTH, x + width - 1, 0, HALF_WIDTH, "minecraft:air");
        for (int step = 0; step < depth; step++) {
            // One block per level on the way out: a climbable staircase, never a sheer face.
            arena.fill(x + width + step, -depth + step, -HALF_WIDTH,
                    x + width + step, -depth + step, HALF_WIDTH, "minecraft:stone");
            arena.fill(x + width + step, -depth + step + 1, -HALF_WIDTH,
                    x + width + step, 0, HALF_WIDTH, "minecraft:air");
        }
    }

    /** Shallow water with a dry lane beside it, so wading is a choice rather than a requirement. */
    private void pool(TestArena arena, int x, int width, int dryZ) {
        if (dryZ > -HALF_WIDTH) {
            arena.fill(x, -1, -HALF_WIDTH, x + width - 1, -1, dryZ - 1, "minecraft:water");
        }
        if (dryZ + LANE <= HALF_WIDTH) {
            arena.fill(x, -1, dryZ + LANE, x + width - 1, -1, HALF_WIDTH, "minecraft:water");
        }
    }

    /** Scattered columns: never blocking, but enough to make the route non-trivial. */
    private void pillars(TestArena arena, int x, int count, Random random) {
        for (int i = 0; i < count; i++) {
            int px = x + random.nextInt(3);
            int pz = random.nextInt(2 * HALF_WIDTH - 1) - HALF_WIDTH + 1;
            int height = 1 + random.nextInt(4);
            arena.fill(px, 0, pz, px, height, pz, "minecraft:stone");
        }
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(GOAL_X, -1, 0).is(Blocks.STONE);
    }

    @Override
    public void start(TestArena arena) {
        this.goalPos = arena.at(GOAL_X, 0, 0);
        this.goal = new GoalBlock(this.goalPos);
        arena.note("seed %d: %s", this.seed, this.featureSummary);
        arena.baritone().getCustomGoalProcess().setGoalAndPath(this.goal);
    }

    @Override
    public TestScenario.Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        if (this.goal.isInGoal(feet)) {
            return Verdict.pass(String.format("solved seed %d in %d ticks (%.1fs)",
                    this.seed, elapsedTicks, elapsedTicks / 20.0));
        }

        int distance = (int) Math.sqrt(this.goalPos.distSqr(feet));
        if (distance < this.bestDistance) {
            this.bestDistance = distance;
            this.ticksWithoutProgress = 0;
        } else {
            this.ticksWithoutProgress++;
        }

        if (this.ticksWithoutProgress > 20 * 25
                && !arena.baritone().getPathingBehavior().isPathing()
                && !arena.baritone().getPathingBehavior().getInProgress().isPresent()) {
            return Verdict.fail(
                    "gave up %d blocks short on seed %d, not pathing and not calculating; features: %s",
                    distance, this.seed, this.featureSummary
            );
        }

        if (elapsedTicks - this.lastProgressNote >= 20 * 40) {
            this.lastProgressNote = elapsedTicks;
            arena.note("t=%ds: %s, %d blocks out", elapsedTicks / 20, feet, distance);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("closest approach %d blocks on seed %d; ended at %s; features: %s",
                this.bestDistance, this.seed, arena.ctx().playerFeet(), this.featureSummary);
    }
}
