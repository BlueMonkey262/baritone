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

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.utils.Helper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Notices when hostile mobs are actually hurting us.
 * <p>
 * A behavior rather than state inside {@code ShelterProcess}, because a process only ticks while it
 * is in control. Every hit worth reacting to lands while something else -- the builder, the miner --
 * is driving, so the counting has to happen somewhere that runs every tick regardless.
 * <p>
 * Only mob damage counts. Fall damage, lava, drowning and starvation are all things running away
 * would make worse rather than better, and they are exactly what a naive "did our health drop"
 * check would fire on. Any hit whose source cannot be attributed to a hostile is ignored.
 */
public final class ThreatBehavior extends Behavior implements Helper {

    /**
     * Tick at which each recent hostile hit landed, oldest first.
     * <p>
     * This must not have a fixed capacity: settings are live and an otherwise valid
     * {@code shelterMinHits} above that capacity would make sheltering impossible.
     */
    private final Deque<Long> recentHits = new ArrayDeque<>();

    /**
     * Our own tick counter. {@code Level#getGameTime} is not usable here: it comes from the server
     * and jumps when the world reloads, which would make every recorded hit look either ancient or
     * from the future.
     */
    private long tick;

    /**
     * The hurt animation timer as of last tick, so that a single hit is counted once rather than for
     * every tick the red flash lasts.
     */
    private int lastHurtTime;

    public ThreatBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.OUT || ctx.player() == null) {
            // leaving the world; whatever was chasing us is not our problem any more
            reset();
            return;
        }
        this.tick++;
        int hurtTime = ctx.player().hurtTime;
        // hurtTime is set to hurtDuration on damage and counts down from there, so a rise means a
        // fresh hit. Comparing against the previous tick catches back-to-back hits too, which a
        // "hurtTime == hurtDuration" test would miss when two mobs land blows a tick apart.
        boolean freshHit = hurtTime > this.lastHurtTime;
        this.lastHurtTime = hurtTime;
        if (!freshHit) {
            return;
        }
        if (!isHostileDamage(ctx.player().getLastDamageSource())) {
            return;
        }
        record(this.tick);
    }

    /**
     * Whether this damage came from something that will keep hurting us if we stand still.
     * <p>
     * {@link DamageSource#getEntity} already resolves a projectile to whoever fired it, so a
     * skeleton's arrows are attributed to the skeleton.
     */
    private static boolean isHostileDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        Entity attacker = source.getEntity();
        return attacker instanceof Enemy;
    }

    private void record(long at) {
        recordHit(
                this.recentHits,
                at,
                this.tick,
                Baritone.settings().shelterThreatMemoryTicks.value
        );
    }

    private void pruneExpired() {
        pruneExpired(
                this.recentHits,
                this.tick,
                Baritone.settings().shelterThreatMemoryTicks.value
        );
    }

    static void recordHit(Deque<Long> hits, long hitTick, long currentTick, long configuredWindow) {
        pruneExpired(hits, currentTick, configuredWindow);
        hits.addLast(hitTick);
    }

    static void pruneExpired(Deque<Long> hits, long currentTick, long configuredWindow) {
        long window = Math.max(0, configuredWindow);
        while (!hits.isEmpty() && currentTick - hits.peekFirst() > window) {
            hits.removeFirst();
        }
    }

    static boolean hasEnoughHits(int hitCount, int configuredMinimum) {
        return hitCount >= Math.max(1, configuredMinimum);
    }

    /**
     * Whether we are being attacked badly enough to be worth abandoning the job over.
     * <p>
     * Requires {@code shelterMinHits} hits inside {@code shelterThreatMemoryTicks} rather than
     * reacting to the first one: a single zombie punch on the way past is not a reason to walk a
     * clearing job halfway across the world.
     */
    public boolean underAttack() {
        pruneExpired();
        return hasEnoughHits(this.recentHits.size(), Baritone.settings().shelterMinHits.value);
    }

    /**
     * How long ago the last hostile hit landed, in ticks, or {@link Integer#MAX_VALUE} if there has
     * never been one. Used to decide when it is calm enough to go back to work.
     */
    public int ticksSinceLastHit() {
        if (this.recentHits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, this.tick - this.recentHits.peekLast());
    }

    /**
     * Forgets the recent hits. Called once a shelter run has dealt with the threat, so that the old
     * hits don't immediately send us straight back out again.
     */
    public void clearThreat() {
        this.recentHits.clear();
    }

    private void reset() {
        this.recentHits.clear();
        this.lastHurtTime = 0;
        this.tick = 0;
    }
}
