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

package baritone.utils;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.utils.accessor.IPlayerControllerMP;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * @author Brady
 * @since 8/25/2018
 */
public final class BlockBreakHelper {
    // base ticks between block breaks caused by tick logic
    private static final int BASE_BREAK_DELAY = 1;
    /** Ticks between "not mining, item in use" reports. */
    private static final int USING_ITEM_LOG_INTERVAL_TICKS = 60;
    /** Ticks between "not mining, tool nearly broken" reports. */
    private static final int SPENT_TOOL_LOG_INTERVAL_TICKS = 60;

    private final Baritone baritone;
    private final IPlayerContext ctx;
    private boolean wasHitting;
    private int breakDelayTimer = 0;
    /** Consecutive ticks mining has been suppressed by an item being in use. */
    private int usingItemTicks = 0;
    /** Consecutive ticks mining has been suppressed because the held tool is nearly broken. */
    private int spentToolTicks = 0;

    /**
     * How long mining has been blocked by {@code itemSaver} refusing to swing the held tool.
     * <p>
     * Read by the processes that drive mining so they can escalate -- fetch a replacement, or give
     * up and say why -- rather than leaving the bot pointed at a block it has decided not to hit.
     *
     * @return consecutive ticks suppressed, or {@code 0} if mining is not currently blocked
     */
    public int getSpentToolTicks() {
        return spentToolTicks;
    }

    BlockBreakHelper(Baritone baritone) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
    }

    public void stopBreakingBlock() {
        // The player controller will never be null, but the player can be
        if (!wasHitting) {
            return;
        }
        if (ctx.player() != null) {
            ctx.playerController().setHittingBlock(false);
            ctx.playerController().resetBlockRemoving();
        }
        // Do not carry a half-finished break across a disconnect/death where player() is null.
        wasHitting = false;
    }

    public void tick(boolean isLeftClick) {
        if (ctx.player() != null && ctx.player().isUsingItem()) {
            // Nothing below this point runs, so a use that never ends stops all mining for good and
            // says nothing about it. Report it while it is happening, throttled, rather than leaving
            // a stuck right-click looking like the builder having quietly finished.
            usingItemTicks++;
            if (isLeftClick && Baritone.settings().chatDebug.value
                    && usingItemTicks % USING_ITEM_LOG_INTERVAL_TICKS == 1) {
                Helper.HELPER.logDirect(String.format(
                        "Not mining: %s is in use (%d ticks). Mining resumes when the use ends.",
                        ctx.player().getUseItem().getItem().getName(ctx.player().getUseItem()).getString(),
                        usingItemTicks
                ));
            }
            // Vanilla refuses to attack while an item is in use, but it enforces that in the keybind
            // handling we deliberately bypass by driving the player controller directly. Mining
            // anyway sends a held item packet from the middle of the tick, at a point where a
            // process has selected its tool rather than whatever is being used, and the server
            // stops a main hand use the moment the held slot changes. The meal, or the bow being
            // drawn, is thrown away without anything client side noticing until the round trip
            // completes a few ticks later.
            stopBreakingBlock();
            return;
        }
        if (usingItemTicks > 0) {
            if (isLeftClick && Baritone.settings().chatDebug.value
                    && usingItemTicks >= USING_ITEM_LOG_INTERVAL_TICKS) {
                Helper.HELPER.logDirect("Mining again after " + usingItemTicks + " ticks of item use");
            }
            usingItemTicks = 0;
        }
        if (isLeftClick && ctx.player() != null && ToolSet.isSpent(ctx.player().getMainHandItem())) {
            // Every block break in Baritone reaches this method -- the eight places that force
            // CLICK_LEFT only raise a flag, and two of them (MovementTraverse's "something in the
            // way" branch and MovementPillar's break-above) never select a tool at all, so they
            // swing whatever is held regardless of autoTool. Refusing here is therefore the only
            // check that covers all of them, and the only one autoTool cannot bypass.
            spentToolTicks++;
            if (Baritone.settings().chatDebug.value && spentToolTicks % SPENT_TOOL_LOG_INTERVAL_TICKS == 1) {
                Helper.HELPER.logDirect(String.format(
                        "Not mining: %s is nearly broken and itemSaver is on (%d ticks).",
                        ctx.player().getMainHandItem().getItem().getName(ctx.player().getMainHandItem()).getString(),
                        spentToolTicks
                ));
            }
            stopBreakingBlock();
            return;
        }
        spentToolTicks = 0;
        if (breakDelayTimer > 0) {
            breakDelayTimer--;
            return;
        }
        HitResult trace = ctx.objectMouseOver();
        boolean isBlockTrace = trace != null && trace.getType() == HitResult.Type.BLOCK;

        if (isLeftClick && isBlockTrace) {
            BlockPos target = ((BlockHitResult) trace).getBlockPos();
            ctx.playerController().setHittingBlock(wasHitting);
            if (ctx.playerController().hasBrokenBlock()) {
                ctx.playerController().syncHeldItem();
                boolean started = ctx.playerController().clickBlock(target, ((BlockHitResult) trace).getDirection());
                ctx.player().swing(InteractionHand.MAIN_HAND);
                // Multi-tick breaks are recorded below when continueDestroyBlock finishes. An
                // instant break finishes inside startDestroyBlock, so without this branch pickup
                // never learns about creative/fragile-block drops.
                if (started && ctx.playerController().hasBrokenBlock()) {
                    recordBreak(target);
                }
            } else {
                if (ctx.playerController().onPlayerDamageBlock(target, ((BlockHitResult) trace).getDirection())) {
                    ctx.player().swing(InteractionHand.MAIN_HAND);
                }
                if (ctx.playerController().hasBrokenBlock()) { // block broken this tick
                    recordBreak(target);
                    // break delay timer only applies for multi-tick block breaks like vanilla
                    breakDelayTimer = BaritoneAPI.getSettings().blockBreakSpeed.value - BASE_BREAK_DELAY;
                    // must reset controller's destroy delay to prevent the client from delaying itself unnecessarily
                    ((IPlayerControllerMP) ctx.minecraft().gameMode).setDestroyDelay(0);
                }
            }
            // if true, we're breaking a block. if false, we broke the block this tick
            wasHitting = !ctx.playerController().hasBrokenBlock();
            // this value will be reset by the MC client handling mouse keys
            // since we're not spoofing the click keybind to the client, the client will stop the break if isDestroyingBlock is true
            // we store and restore this value on the next tick to determine if we're breaking a block
            ctx.playerController().setHittingBlock(false);
        } else {
            stopBreakingBlock();
        }
    }

    /**
     * Notes a break as one worth collecting the drop of.
     * <p>
     * Only blocks we were actually going for count. Baritone breaks a great deal of scenery simply
     * to get from A to B, and treating that as a wanted drop is self-feeding: every block cleared
     * on the way somewhere spawns another thing to go and fetch, clearing more blocks on the way to
     * that. Nothing about the break itself distinguishes the two, so the process that chose the
     * destination is asked.
     *
     * @see baritone.api.process.IBaritoneProcess#wantsDropsFrom(BlockPos)
     */
    private void recordBreak(BlockPos pos) {
        if (!BaritoneAPI.getSettings().pickupBlocks.value) {
            return;
        }
        boolean wanted = baritone.getPathingControlManager()
                .mostRecentInControl()
                .map(process -> process.wantsDropsFrom(pos))
                .orElse(false);
        if (wanted) {
            baritone.getPickupBlocksProcess().recordBreak(pos);
        }
    }
}
