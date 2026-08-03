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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.behavior;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThreatBehaviorTest {

    @Test
    public void retainsEnoughHitsForConfiguredThresholdsAboveThree() {
        Deque<Long> hits = new ArrayDeque<>();
        for (long tick = 1; tick <= 5; tick++) {
            ThreatBehavior.recordHit(hits, tick, tick, 20);
        }

        assertEquals(5, hits.size());
        assertTrue(ThreatBehavior.hasEnoughHits(hits.size(), 5));
        assertFalse(ThreatBehavior.hasEnoughHits(hits.size(), 6));
    }

    @Test
    public void retainsBoundaryHitAndExpiresItOneTickLater() {
        Deque<Long> hits = new ArrayDeque<>();
        ThreatBehavior.recordHit(hits, 10, 10, 20);

        ThreatBehavior.pruneExpired(hits, 30, 20);
        assertEquals(1, hits.size());

        ThreatBehavior.pruneExpired(hits, 31, 20);
        assertTrue(hits.isEmpty());
    }

    @Test
    public void appliesChangedWindowToAlreadyRecordedHits() {
        Deque<Long> hits = new ArrayDeque<>();
        ThreatBehavior.recordHit(hits, 10, 10, 100);
        ThreatBehavior.recordHit(hits, 30, 30, 100);

        ThreatBehavior.pruneExpired(hits, 35, 10);

        assertEquals(1, hits.size());
        assertEquals(Long.valueOf(30), hits.peekFirst());
    }

    @Test
    public void clampsInvalidWindowAndMinimumSettings() {
        Deque<Long> hits = new ArrayDeque<>();
        ThreatBehavior.recordHit(hits, 4, 4, -10);
        ThreatBehavior.pruneExpired(hits, 5, -10);

        assertTrue(hits.isEmpty());
        assertFalse(ThreatBehavior.hasEnoughHits(0, 0));
        assertTrue(ThreatBehavior.hasEnoughHits(1, 0));
    }
}
