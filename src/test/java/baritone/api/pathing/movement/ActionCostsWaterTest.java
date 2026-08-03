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

package baritone.api.pathing.movement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static baritone.api.pathing.movement.ActionCosts.waterWalkCost;
import static org.junit.Assert.assertTrue;

public class ActionCostsWaterTest {

    private static final double EPSILON = 1e-9;

    @Test
    public void aMultiplierOfOneIsExactlyUpstream() {
        assertEquals(ActionCosts.WALK_ONE_IN_WATER_COST, ActionCosts.waterWalkCost(0, 1), EPSILON);
        assertEquals(
                ActionCosts.WALK_ONE_IN_WATER_COST * 0.5 + ActionCosts.WALK_ONE_BLOCK_COST * 0.5,
                ActionCosts.waterWalkCost(0.5, 1),
                EPSILON
        );
    }

    @Test
    public void thePenaltyAppliesOnlyToTheDistanceActuallySpentSwimming() {
        // Enchanted all the way to dry-land speed: there is no swimming left to penalise, so the
        // multiplier must not change the answer.
        assertEquals(ActionCosts.WALK_ONE_BLOCK_COST, ActionCosts.waterWalkCost(1, 1), EPSILON);
        assertEquals(ActionCosts.WALK_ONE_BLOCK_COST, ActionCosts.waterWalkCost(1, 5), EPSILON);

        // Halfway there: half the cost is walking and untouched, half is swimming and doubled.
        assertEquals(
                ActionCosts.WALK_ONE_IN_WATER_COST + ActionCosts.WALK_ONE_BLOCK_COST * 0.5,
                ActionCosts.waterWalkCost(0.5, 2),
                EPSILON
        );
    }

    @Test
    public void theDefaultMakesWaterAboutFourTimesTheCostOfWalking() {
        double ratio = ActionCosts.waterWalkCost(0, 2) / ActionCosts.WALK_ONE_BLOCK_COST;
        assertTrue("expected roughly 4x, got " + ratio, ratio > 3.8 && ratio < 4.0);
    }

    @Test
    public void nonsenseMultipliersFallBackToUpstreamRatherThanPoisoningTheSearch() {
        // A zero or negative edge cost is not a cheap path, it is a search that does not terminate.
        for (double bad : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(
                    "multiplier " + bad,
                    ActionCosts.WALK_ONE_IN_WATER_COST,
                    ActionCosts.waterWalkCost(0, bad),
                    EPSILON
            );
        }
    }

    @Test
    public void anUnenchantedPlayerIsChargedTheFullSwimCost() {
        // Regression guard for the bug this whole setting was chasing. A player with no depth
        // strider has water_movement_efficiency 0 -- vanilla registers it as a RangedAttribute with
        // default 0, maximum 1 -- so the "how far towards dry-land speed" figure for the common
        // case is 0, not 1. CalculationContext initialised it to 1, which collapsed water to the
        // cost of dry land and made every penalty here multiply by zero.
        double unenchanted = waterWalkCost(0, 1);
        assertEquals(ActionCosts.WALK_ONE_IN_WATER_COST, unenchanted, EPSILON);
        assertTrue("water must cost more than dry land for an unenchanted player",
                unenchanted > ActionCosts.WALK_ONE_BLOCK_COST);
        // And the multiplier must still have authority in that case, which it does not if the
        // efficiency figure is wrongly 1.
        assertTrue(waterWalkCost(0, 4) > unenchanted);
    }

    @Test
    public void aHigherMultiplierIsAlwaysMoreExpensive() {
        double previous = 0;
        for (double multiplier = 1; multiplier <= 6; multiplier += 0.5) {
            double cost = ActionCosts.waterWalkCost(0, multiplier);
            assertTrue("not monotonic at " + multiplier, cost > previous);
            previous = cost;
        }
    }
}
