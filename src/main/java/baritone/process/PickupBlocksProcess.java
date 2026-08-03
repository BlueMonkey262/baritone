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

package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Temporarily walks into nearby block drops without replacing the process that created them.
 */
public final class PickupBlocksProcess extends BaritoneProcessHelper {

    private static final double PICKUP_RADIUS_SQ = 10 * 10;
    private static final double DROP_MATCH_RADIUS_SQ = 4;
    private static final long DROP_WAIT_MILLIS = 5_000;

    private ItemEntity target;
    private final List<ExpectedDrop> expectedDrops = new ArrayList<>();

    private static final class ExpectedDrop {
        private final BlockPos pos;
        private final long expiresAt;
        /**
         * Item entities that already existed when the break completed. They cannot be this break's
         * drop even if they later drift through the match radius.
         */
        private final Set<UUID> preExistingItems;
        /**
         * Once an expectation claims a newly spawned entity it remains bound to that entity. This
         * consumes the spatial association, so an unrelated later drop cannot reuse it.
         */
        private UUID matchedEntity;

        private ExpectedDrop(BlockPos pos, Set<UUID> preExistingItems) {
            this.pos = pos.immutable();
            this.expiresAt = System.currentTimeMillis() + DROP_WAIT_MILLIS;
            this.preExistingItems = preExistingItems;
        }
    }

    public PickupBlocksProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        if (!Baritone.settings().pickupBlocks.value || ctx.player() == null || ctx.world() == null) {
            target = null;
            return false;
        }
        target = closestBlockDrop();
        return target != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        target = closestBlockDrop();
        if (target == null) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        // Walk up to the drop, not into it. Items are collected from about a block away, so
        // GoalBlock asked to stand exactly where the item is, and the pathfinder would dig through
        // whatever was in the way to manage it.
        return new PathingCommand(new GoalNear(target.blockPosition(), 1), PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
    }

    private ItemEntity closestBlockDrop() {
        removeExpiredDrops();
        return ctx.entitiesStream()
                .filter(entity -> entity instanceof ItemEntity)
                .map(entity -> (ItemEntity) entity)
                .filter(entity -> entity.isAlive())
                .filter(entity -> entity.getItem().getItem() instanceof BlockItem)
                .filter(this::matchesExpectedDrop)
                .filter(entity -> entity.distanceToSqr(ctx.player()) <= PICKUP_RADIUS_SQ)
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(ctx.player())))
                .orElse(null);
    }

    private boolean matchesExpectedDrop(ItemEntity entity) {
        Vec3 dropPos = entity.position();
        UUID entityId = entity.getUUID();
        for (ExpectedDrop expected : expectedDrops) {
            expected.matchedEntity = bindEligibleDrop(
                    entityId,
                    dropPos.distanceToSqr(Vec3.atCenterOf(expected.pos)) <= DROP_MATCH_RADIUS_SQ,
                    expected.preExistingItems,
                    expected.matchedEntity
            );
            if (entityId.equals(expected.matchedEntity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the entity bound to an expectation after considering one candidate.
     * <p>
     * The inputs make this independently testable without constructing Minecraft entities. A
     * non-null existing binding is deliberately sticky: only that UUID remains eligible.
     */
    static UUID bindEligibleDrop(
            UUID candidate,
            boolean withinMatchRadius,
            Set<UUID> preExistingItems,
            UUID matchedEntity
    ) {
        if (matchedEntity != null) {
            return matchedEntity;
        }
        if (!withinMatchRadius || preExistingItems.contains(candidate)) {
            return null;
        }
        return candidate;
    }

    /**
     * Forgets the expectations that can no longer lead anywhere.
     * <p>
     * The wait is a window for the drop to <i>appear</i> in, not a deadline for collecting it. This
     * process runs below the miner and the builder, so a drop recorded mid-job is not fetched until
     * they have finished, which is routinely a good deal longer than {@link #DROP_WAIT_MILLIS}. An
     * expectation that has bound an entity therefore lives exactly as long as that entity does.
     */
    private void removeExpiredDrops() {
        if (expectedDrops.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<UUID> liveItems = new HashSet<>();
        ctx.entitiesStream()
                .filter(entity -> entity instanceof ItemEntity && entity.isAlive())
                .map(Entity::getUUID)
                .forEach(liveItems::add);
        expectedDrops.removeIf(expected -> expected.matchedEntity == null
                ? expected.expiresAt < now
                : !liveItems.contains(expected.matchedEntity));
    }

    /**
     * Records a completed Baritone block break so only its resulting drops are collected.
     */
    public void recordBreak(BlockPos pos) {
        if (Baritone.settings().pickupBlocks.value) {
            Set<UUID> preExistingItems = new HashSet<>();
            ctx.entitiesStream()
                    .filter(entity -> entity instanceof ItemEntity)
                    .map(Entity::getUUID)
                    .forEach(preExistingItems::add);
            expectedDrops.add(new ExpectedDrop(pos, preExistingItems));
        }
    }

    @Override
    public void onLostControl() {
        target = null;
    }

    @Override
    public String displayName0() {
        return "Picking up blocks";
    }

    /**
     * Below {@link IBaritoneProcess#DEFAULT_PRIORITY}, so the miner and the builder both outrank
     * this and it only gets a turn once they have nothing to ask for.
     * <p>
     * Collecting a drop is never worth abandoning the job to do. Anything above the default meant
     * every log that hit the ground pulled the bot off a half-chopped tree, and the walk back was
     * itself enough to break more scenery. Most drops are picked up in passing anyway, because
     * items are collected simply by walking near them; this exists for the ones that end up
     * somewhere the work never happens to go.
     */
    @Override
    public double priority() {
        return DEFAULT_PRIORITY - 1;
    }

    @Override
    public boolean isTemporary() {
        return true;
    }
}
