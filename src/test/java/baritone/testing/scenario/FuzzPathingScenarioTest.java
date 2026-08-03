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

import baritone.testing.scenario.FuzzPathingScenario.Feature;
import baritone.testing.scenario.FuzzPathingScenario.Kind;
import org.junit.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The fuzz generator's own tests. A generator nobody checks is a suite that looks like coverage.
 */
public class FuzzPathingScenarioTest {

    private static final int SEEDS = 240;

    private static Map<Kind, Integer> histogram() {
        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            counts.put(kind, 0);
        }
        for (int seed = 1; seed <= SEEDS; seed++) {
            for (Feature feature : FuzzPathingScenario.plan(seed)) {
                counts.merge(feature.kind, 1, Integer::sum);
            }
        }
        return counts;
    }

    @Test
    public void everyFeatureKindActuallyGetsGenerated() {
        // The bug this guards against: the first smoke run drew sixteen features from six
        // consecutive seeds and produced no walls at all, because java.util.Random correlates
        // across small sequential seeds. Silent, and it makes the whole suite less varied than it
        // claims to be.
        Map<Kind, Integer> counts = histogram();
        for (Kind kind : Kind.values()) {
            assertTrue(kind + " never generated across " + SEEDS + " seeds", counts.get(kind) > 0);
        }
    }

    @Test
    public void noFeatureKindDominatesOrVanishes() {
        Map<Kind, Integer> counts = histogram();
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        for (Kind kind : Kind.values()) {
            double share = counts.get(kind) / (double) total;
            // Four kinds drawn uniformly is 25% each; this is a wide band that still catches a
            // generator quietly favouring one kind.
            assertTrue(
                    String.format("%s is %.1f%% of %d features", kind, share * 100, total),
                    share > 0.15 && share < 0.35
            );
        }
    }

    @Test
    public void coursesAreActuallyDifferentFromEachOther() {
        Set<String> shapes = new HashSet<>();
        for (int seed = 1; seed <= SEEDS; seed++) {
            StringBuilder shape = new StringBuilder();
            for (Feature feature : FuzzPathingScenario.plan(seed)) {
                shape.append(feature.kind).append(feature.x).append(':')
                        .append(feature.a).append(',').append(feature.b).append(' ');
            }
            shapes.add(shape.toString());
        }
        // Duplicates are the failure mode that matters: running the same course under two names
        // costs a minute of game time each and buys nothing.
        assertTrue("only " + shapes.size() + " distinct courses from " + SEEDS + " seeds",
                shapes.size() >= SEEDS - 2);
    }

    @Test
    public void everyCourseHasEnoughToBeWorthRunning() {
        for (int seed = 1; seed <= SEEDS; seed++) {
            List<Feature> features = FuzzPathingScenario.plan(seed);
            assertTrue("seed " + seed + " generated only " + features.size() + " features",
                    features.size() >= 3);
            // Features must stay inside the corridor the arena wipe covers, or a course would build
            // into ground the next scenario never clears.
            for (Feature feature : features) {
                assertTrue("seed " + seed + " places a feature at x=" + feature.x,
                        feature.x >= 0 && feature.x <= 40);
            }
        }
    }

    @Test
    public void planningIsDeterministic() {
        for (int seed : new int[]{1, 7, 42, 199, 240}) {
            List<Feature> first = FuzzPathingScenario.plan(seed);
            List<Feature> second = FuzzPathingScenario.plan(seed);
            assertEquals(first.size(), second.size());
            for (int i = 0; i < first.size(); i++) {
                assertEquals("seed " + seed + " feature " + i, first.get(i).kind, second.get(i).kind);
                assertEquals("seed " + seed + " feature " + i, first.get(i).x, second.get(i).x);
                assertEquals("seed " + seed + " feature " + i, first.get(i).a, second.get(i).a);
                assertEquals("seed " + seed + " feature " + i, first.get(i).b, second.get(i).b);
            }
        }
    }
}
