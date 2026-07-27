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
import baritone.api.cache.IRestockBox;
import baritone.api.cache.IRestockBoxCollection;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.IRestockProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.behavior.ContainerInteractionBehavior;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Fetches build materials from shulker boxes registered with {@code #addbox}.
 * <p>
 * The whole point of this process is that it restocks the way a player would: it walks to the box,
 * looks at it, right-clicks it, waits for the server to send the contents, shift-clicks the stacks
 * it wants, and closes the box. Every one of those steps is a real packet, so the server's own
 * reach check on opening a container applies exactly as it normally would. Nothing is injected
 * into the client's inventory behind the server's back.
 * <p>
 * Box contents are indexed when a box is opened, and that index is used to pick which box to walk
 * to. The index is only ever a hint. If the box turns out not to have what we expected -- because
 * it was emptied since we last looked -- we re-index it to reality and immediately try the next
 * candidate box without walking back to the build first.
 *
 * @see IRestockProcess
 */
public final class RestockProcess extends BaritoneProcessHelper implements IRestockProcess {

    private enum State {
        /**
         * Not restocking. The builder is in control.
         */
        IDLE,
        /**
         * Walking to the target box.
         */
        PATHING,
        /**
         * In range; forcing a right-click until the server opens the container for us.
         */
        OPENING,
        /**
         * Container is open but the server has not sent its contents yet.
         */
        AWAITING_SYNC,
        /**
         * Contents are readable; shift-clicking the stacks we want.
         */
        TRANSFERRING,
        /**
         * Putting unwanted blocks into the box while we're here.
         */
        DEPOSITING,
        /**
         * Sending the close packet and tidying up.
         */
        CLOSING
    }

    private State state = State.IDLE;

    /**
     * Materials we have concluded no registered box can supply. This is <b>per build</b>, not per
     * trip: if it were cleared when a restock run ended we would path to the same empty boxes over
     * and over. It is cleared by the builder when a new build starts, and by {@code #addbox}.
     */
    private final Set<BlockState> unobtainable = new HashSet<>();

    /**
     * Boxes we have already opened during this build and found lacking. Also per build, so that a
     * single restock run doesn't revisit a box it just found empty.
     */
    private final Set<BetterBlockPos> failedThisBuild = new HashSet<>();

    /**
     * So the allowInventory warning is only printed once per session rather than every trip.
     */
    private boolean warnedAboutInventory;

    // --- per-trip state, all reset by resetTrip() ---

    /**
     * The item we are currently trying to fetch, and the block state that wants it.
     */
    private Item wantedItem;
    private BlockState wantedState;
    /**
     * How many blocks the builder is actually short of. Used for reporting.
     */
    private int wantedCount;
    /**
     * How many items to keep pulling until we stop; the shortfall plus a configured surplus.
     */
    private int fetchTarget;

    private BetterBlockPos targetBox;
    private List<BetterBlockPos> candidates = new ArrayList<>();
    /**
     * When true this run is only cataloguing box contents, not fetching anything, so the transfer
     * phase is skipped and every queued box gets visited in turn.
     */
    private boolean indexing;
    private int indexedThisRun;

    private int ticksInState;
    private int lastContentRevision;
    private int playerCountBefore;
    private int transferSlot;
    private int depositSlot = -1;
    private int deposited;
    /**
     * Whether this trip actually obtained anything. Tracked explicitly rather than inferred by
     * comparing inventory counts, because the player may already be carrying some of the item.
     */
    private boolean tookAnything;

    public RestockProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        return this.state != State.IDLE;
    }

    /**
     * Higher than the builder (which runs at the default priority of -1) so that we take control
     * when a restock is in flight, and just above the inventory pauser so that a pending hotbar
     * swap can't preempt us mid-container.
     */
    @Override
    public double priority() {
        return 5.2;
    }

    /**
     * This process <b>must</b> be temporary. {@code BuilderProcess#onLostControl} nulls out the
     * schematic and the set of incorrect positions, so a non-temporary process taking control
     * would silently destroy the build we are trying to help.
     */
    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public String displayName0() {
        if (this.indexing) {
            return "Indexing shulker boxes" + (this.targetBox == null ? "" : " at " + this.targetBox);
        }
        return "Restocking " + (this.wantedItem == null ? "" : itemName(this.wantedItem))
                + (this.targetBox == null ? "" : " from " + this.targetBox);
    }

    @Override
    public boolean isUnobtainable(BlockState state) {
        return this.unobtainable.contains(state);
    }

    @Override
    public void clearUnobtainable() {
        this.unobtainable.clear();
        this.failedThisBuild.clear();
    }

    @Override
    public boolean requestIndexing(boolean includeAlreadyIndexed) {
        if (!Baritone.settings().restockFromBoxes.value || isActive()) {
            return false;
        }
        IRestockBoxCollection collection = boxes();
        if (collection == null) {
            return false;
        }
        BetterBlockPos feet = ctx.playerFeet();
        double maxDistSq = Math.pow(Baritone.settings().restockMaxDistance.value, 2);
        List<BetterBlockPos> queue = new ArrayList<>();
        for (IRestockBox box : collection.getAllBoxes()) {
            if (box.isMissing()) {
                continue;
            }
            if (box.getLocation().distSqr(feet) > maxDistSq) {
                continue;
            }
            if (includeAlreadyIndexed || box.isUnindexed()) {
                queue.add(box.getLocation());
            }
        }
        if (queue.isEmpty()) {
            return false;
        }
        // nearest first, so the walk between boxes is roughly sensible
        queue.sort(Comparator.comparingDouble(pos -> pos.distSqr(feet)));
        this.indexing = true;
        this.indexedThisRun = 0;
        this.candidates = queue;
        this.targetBox = this.candidates.remove(0);
        this.state = State.PATHING;
        this.ticksInState = 0;
        logDirect("Indexing " + (queue.size() + 1) + " shulker box(es) so I know where materials actually are");
        return true;
    }

    @Override
    public boolean requestRestock(Map<BlockState, Integer> missing) {
        if (!Baritone.settings().restockFromBoxes.value || isActive() || missing.isEmpty()) {
            return false;
        }
        IRestockBoxCollection collection = boxes();
        if (collection == null) {
            return false;
        }

        // Try each missing material in turn; the first one some registered box plausibly holds
        // wins. Materials we can't find a source for are only noted here, and given up on at the
        // bottom -- if a later material does start a trip, the earlier ones must stay retryable,
        // because that trip changes the inventory and may well change what's possible next pass.
        Map<BlockState, String> hopeless = new LinkedHashMap<>();
        for (Map.Entry<BlockState, Integer> entry : missing.entrySet()) {
            BlockState desired = entry.getKey();
            if (this.unobtainable.contains(desired)) {
                continue;
            }
            Item item = desired.getBlock().asItem();
            if (item == null || item == ItemStack.EMPTY.getItem()) {
                hopeless.put(desired, "it has no corresponding item");
                continue;
            }
            List<BetterBlockPos> found = findCandidates(collection, item);
            if (found.isEmpty()) {
                hopeless.put(desired, "no registered box has it");
                continue;
            }
            this.wantedState = desired;
            this.wantedItem = item;
            this.wantedCount = entry.getValue();
            // keep clearing slots a bit past the immediate shortfall so we don't have to walk
            // straight back for the next few blocks
            this.fetchTarget = entry.getValue() + (Baritone.settings().restockExtraStacks.value * 64);
            this.candidates = found;
            this.targetBox = this.candidates.remove(0);
            this.state = State.PATHING;
            this.ticksInState = 0;
            logDirect(String.format("Restocking %s from box at %s", itemName(item), this.targetBox));
            // Restocked items land in the main inventory, and the builder can only place from the
            // hotbar. Without allowInventory it has no way to move them across, so it would just
            // sit on a full inventory doing nothing. Warn once rather than silently wasting a trip.
            if (!Baritone.settings().allowInventory.value && !this.warnedAboutInventory) {
                this.warnedAboutInventory = true;
                logDirect("Warning: allowInventory is off, so I can't move restocked items from your inventory to your hotbar. Run: #set allowInventory true");
            }
            return true;
        }
        // nothing could be started, so it's now safe to give up on whatever had no source
        hopeless.forEach(this::markUnobtainable);
        return false;
    }

    /**
     * Registered boxes that might hold the given item, nearest first.
     * <p>
     * Boxes we have never managed to index are included, sorted last, so that a box registered
     * without ever being opened still gets tried speculatively rather than ignored forever.
     */
    private List<BetterBlockPos> findCandidates(IRestockBoxCollection collection, Item item) {
        double maxDistSq = Math.pow(Baritone.settings().restockMaxDistance.value, 2);
        BetterBlockPos feet = ctx.playerFeet();
        List<IRestockBox> viable = new ArrayList<>();
        for (IRestockBox box : collection.getAllBoxes()) {
            if (box.isMissing() || this.failedThisBuild.contains(box.getLocation())) {
                continue;
            }
            if (box.getLocation().distSqr(feet) > maxDistSq) {
                continue;
            }
            if (box.isUnindexed() || box.countOf(item) > 0) {
                viable.add(box);
            }
        }
        viable.sort(Comparator
                // prefer boxes we know hold the item over ones we've never looked inside
                .comparing((IRestockBox box) -> box.isUnindexed())
                .thenComparingDouble(box -> box.getLocation().distSqr(feet)));
        List<BetterBlockPos> result = new ArrayList<>();
        for (IRestockBox box : viable) {
            result.add(box.getLocation());
        }
        return result;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (this.state == State.IDLE) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        this.ticksInState++;

        // Only a pathing failure means the box is unreachable. A stale calc failure arriving while
        // we're mid-container must not tear down a transfer that's going fine.
        if (calcFailed && this.state == State.PATHING) {
            logDirect("Couldn't path to the box at " + this.targetBox);
            return failCurrentBox();
        }

        switch (this.state) {
            case PATHING:
                return tickPathing();
            case OPENING:
                return tickOpening();
            case AWAITING_SYNC:
                return tickAwaitingSync();
            case TRANSFERRING:
                return tickTransferring(isSafeToCancel);
            case DEPOSITING:
                return tickDepositing();
            case CLOSING:
            default:
                return tickClosing();
        }
    }

    private PathingCommand tickPathing() {
        // If the chunk is loaded and there is no shulker box where we registered one, the box is
        // genuinely gone rather than merely unloaded. Flag it, but never delete it -- deciding a
        // registration is dead is the player's call, via #removebox.
        if (baritone.bsi != null && baritone.bsi.worldContainsLoadedChunk(this.targetBox.x, this.targetBox.z)) {
            BlockState atBox = baritone.bsi.get0(this.targetBox);
            if (!(atBox.getBlock() instanceof ShulkerBoxBlock)) {
                logDirect("No shulker box at " + this.targetBox + " any more; flagging it. Use #removebox to forget it, or #listboxes to review.");
                IRestockBoxCollection collection = boxes();
                if (collection != null) {
                    collection.setMissing(this.targetBox, true);
                }
                return failCurrentBox();
            }
        }

        Optional<Rotation> reachable = RotationUtils.reachable(ctx, this.targetBox, ctx.playerController().getBlockReachDistance());
        if (reachable.isPresent()) {
            this.state = State.OPENING;
            this.ticksInState = 0;
            baritone.getContainerInteractionBehavior().resetSync();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        return new PathingCommand(goalForBox(this.targetBox), PathingCommandType.SET_GOAL_AND_PATH);
    }

    /**
     * A shulker box placed facing up needs the block above it to be clear, otherwise the lid has
     * nowhere to open into and the server refuses the interaction outright. When something is in
     * the way we aim to stand in that position, which makes the pathfinder break it on the way,
     * exactly as {@code GetToBlockProcess} does for chests.
     */
    private Goal goalForBox(BetterBlockPos pos) {
        if (baritone.bsi != null && MovementHelper.isBlockNormalCube(baritone.bsi.get0(pos.above()))) {
            return new GoalBlock(pos.above());
        }
        return new GoalGetToBlock(pos);
    }

    private PathingCommand tickOpening() {
        ContainerInteractionBehavior behavior = behavior();
        if (behavior.openContainer() != null) {
            this.state = State.AWAITING_SYNC;
            this.ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        if (this.ticksInState > Baritone.settings().restockOpenTimeoutTicks.value) {
            logDirect("Timed out opening the box at " + this.targetBox);
            return failCurrentBox();
        }
        // Force a genuine right-click. This goes through InputOverrideHandler -> BlockPlaceHelper
        // -> MultiPlayerGameMode#useItemOn, i.e. a real use-item-on packet, so the server applies
        // its own range check. RotationUtils.reachable above already refused to try from too far.
        Optional<Rotation> reachable = RotationUtils.reachable(ctx, this.targetBox, ctx.playerController().getBlockReachDistance());
        if (!reachable.isPresent()) {
            // drifted out of range somehow; walk back
            this.state = State.PATHING;
            this.ticksInState = 0;
            return new PathingCommand(goalForBox(this.targetBox), PathingCommandType.SET_GOAL_AND_PATH);
        }
        baritone.getLookBehavior().updateTarget(reachable.get(), true);
        if (this.targetBox.equals(ctx.getSelectedBlock().orElse(null))) {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
        }
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickAwaitingSync() {
        ContainerInteractionBehavior behavior = behavior();
        if (behavior.openContainer() == null) {
            // the container closed under us before we could read it
            logDirect("Box at " + this.targetBox + " closed before its contents arrived");
            return failCurrentBox();
        }
        if (behavior.isContainerReadable()) {
            AbstractContainerMenu menu = behavior.openContainer();
            Map<Item, Integer> contents = behavior.readContents(menu);
            IRestockBoxCollection collection = boxes();
            if (collection != null) {
                // record what is actually in there, whether or not it's what we came for
                collection.updateContents(this.targetBox, contents);
            }
            if (this.indexing) {
                // cataloguing only -- record and move on without taking anything
                this.indexedThisRun++;
                logDirect(String.format("Indexed %s: %d item type(s)", this.targetBox, contents.size()));
                return afterContainerWork();
            }
            if (contents.getOrDefault(this.wantedItem, 0) <= 0) {
                logDirect("Box at " + this.targetBox + " doesn't have " + itemName(this.wantedItem) + " after all; trying the next one");
                this.failedThisBuild.add(this.targetBox);
                this.state = State.CLOSING;
                this.ticksInState = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            this.playerCountBefore = countInPlayerInventory(this.wantedItem);
            this.lastContentRevision = behavior.getContentRevision();
            this.transferSlot = 0;
            this.state = State.TRANSFERRING;
            this.ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        if (this.ticksInState > Baritone.settings().restockOpenTimeoutTicks.value) {
            logDirect("Timed out waiting for the contents of the box at " + this.targetBox);
            this.failedThisBuild.add(this.targetBox);
            this.state = State.CLOSING;
            this.ticksInState = 0;
        }
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    /**
     * Shift-clicks matching stacks out of the box, one slot per tick.
     * <p>
     * One click per tick rather than a burst: container clicks are client-predicted and confirmed
     * by the server afterwards, so firing many at once risks the server rejecting the tail of them
     * against a stale menu state. A shulker box is 27 slots, so even the worst case is under two
     * seconds -- still far faster than a human, which is all that was asked for.
     */
    private PathingCommand tickTransferring(boolean isSafeToCancel) {
        ContainerInteractionBehavior behavior = behavior();
        AbstractContainerMenu menu = behavior.openContainer();
        if (menu == null) {
            // container vanished mid-transfer (server closed it, or we got disconnected)
            logDirect("Box at " + this.targetBox + " closed mid-transfer");
            return finishTrip();
        }

        int taken = countInPlayerInventory(this.wantedItem) - this.playerCountBefore;
        if (taken >= this.fetchTarget) {
            logDirect(String.format("Took %d %s from %s (needed %d)", taken, itemName(this.wantedItem), this.targetBox, this.wantedCount));
            this.tookAnything = true;
            return afterContainerWork();
        }

        int containerSlots = behavior.containerSlotCount(menu);
        while (this.transferSlot < containerSlots) {
            ItemStack stack = menu.slots.get(this.transferSlot).getItem();
            if (!stack.isEmpty() && stack.getItem() == this.wantedItem) {
                behavior.quickMove(menu, this.transferSlot);
                this.transferSlot++;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            this.transferSlot++;
        }

        // Ran out of slots to click. Give the server a moment to answer the last click before
        // judging whether anything actually arrived -- the client's own optimistic prediction is
        // not proof, and a rejected move gets rolled back a tick or two later.
        if (this.ticksInState < Baritone.settings().restockOpenTimeoutTicks.value
                && behavior.getContentRevision() == this.lastContentRevision) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        if (taken <= 0) {
            // Nothing arrived. Overwhelmingly the cause is a full player inventory, since
            // QUICK_MOVE silently does nothing when there is nowhere to put the stack.
            logDirect("Couldn't take " + itemName(this.wantedItem)
                    + " from " + this.targetBox + " - is your inventory full?");
            markUnobtainable(this.wantedState, "nothing could be transferred");
        } else {
            logDirect(String.format("Took %d %s from %s (needed %d)", taken, itemName(this.wantedItem), this.targetBox, this.wantedCount));
            this.tookAnything = true;
        }
        return afterContainerWork();
    }

    /**
     * Called once we're done taking things out of a box. If the inventory is getting tight, use the
     * open container to offload blocks the schematic has no use for before walking away.
     */
    private PathingCommand afterContainerWork() {
        this.ticksInState = 0;
        this.depositSlot = -1;
        this.deposited = 0;
        this.state = shouldDumpJunk() ? State.DEPOSITING : State.CLOSING;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    /**
     * Only worth opening the inventory up if we're actually running out of room; otherwise this is
     * pointless container traffic on every single restock.
     */
    private boolean shouldDumpJunk() {
        if (!Baritone.settings().restockDumpJunk.value || ctx.player() == null) {
            return false;
        }
        int free = 0;
        for (ItemStack stack : ctx.player().getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) {
                free++;
            }
        }
        return free < Baritone.settings().restockDumpWhenFreeSlotsBelow.value;
    }

    /**
     * Whether this stack is rubble rather than something worth keeping.
     * <p>
     * Deliberately narrow: only block items, only ones the schematic never asks for, and never
     * anything Baritone relies on for scaffolding. Tools, weapons, armour, food and every non-block
     * item fail the first test and are always kept.
     */
    private boolean isJunk(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        if (Baritone.settings().acceptableThrowawayItems.value.contains(stack.getItem())) {
            return false; // needed for pillaring and bridging
        }
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof ShulkerBoxBlock) {
            return false; // never post a shulker box into another shulker box
        }
        return !baritone.getBuilderProcess().schematicWants(block);
    }

    /**
     * Shift-clicks junk out of the player's half of the open menu, one slot per tick, same as the
     * take path. If the box is full the clicks simply do nothing and we move on.
     */
    private PathingCommand tickDepositing() {
        ContainerInteractionBehavior behavior = behavior();
        AbstractContainerMenu menu = behavior.openContainer();
        if (menu == null) {
            return finishTrip();
        }
        int containerSlots = behavior.containerSlotCount(menu);
        if (this.depositSlot < containerSlots) {
            this.depositSlot = containerSlots; // player's own slots start here
        }
        while (this.depositSlot < menu.slots.size()) {
            int slot = this.depositSlot++;
            if (isJunk(menu.slots.get(slot).getItem())) {
                behavior.quickMove(menu, slot);
                this.deposited++;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }
        if (this.deposited > 0) {
            logDirect(String.format("Put %d stack(s) of unwanted blocks into %s", this.deposited, this.targetBox));
            IRestockBoxCollection collection = boxes();
            if (collection != null && behavior.isContainerReadable()) {
                // what we just added changes the box's contents, so re-record them
                collection.updateContents(this.targetBox, behavior.readContents(menu));
            }
        }
        this.state = State.CLOSING;
        this.ticksInState = 0;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickClosing() {
        behavior().closeContainer();
        if (this.indexing) {
            if (!this.candidates.isEmpty()) {
                this.targetBox = this.candidates.remove(0);
                this.state = State.PATHING;
                this.ticksInState = 0;
                return new PathingCommand(goalForBox(this.targetBox), PathingCommandType.SET_GOAL_AND_PATH);
            }
            logDirect("Finished indexing " + this.indexedThisRun + " shulker box(es). Use #listboxes to see what's where.");
            return finishTrip();
        }
        // if this box let us down and there are other candidates, go straight to the next one
        // rather than bouncing back to the build site first
        if (!this.candidates.isEmpty() && !this.tookAnything) {
            this.targetBox = this.candidates.remove(0);
            this.state = State.PATHING;
            this.ticksInState = 0;
            return new PathingCommand(goalForBox(this.targetBox), PathingCommandType.SET_GOAL_AND_PATH);
        }
        return finishTrip();
    }

    /**
     * Abandons the current box and moves to the next candidate, or gives up on the material if
     * there are none left.
     */
    private PathingCommand failCurrentBox() {
        this.failedThisBuild.add(this.targetBox);
        behavior().closeContainer();
        if (!this.candidates.isEmpty()) {
            this.targetBox = this.candidates.remove(0);
            this.state = State.PATHING;
            this.ticksInState = 0;
            return new PathingCommand(goalForBox(this.targetBox), PathingCommandType.SET_GOAL_AND_PATH);
        }
        markUnobtainable(this.wantedState, "every candidate box failed");
        return finishTrip();
    }

    /**
     * Ends the restock run and hands control back to the builder, which will re-scan the player's
     * inventory on its next tick and carry on with whatever it can now place.
     */
    private PathingCommand finishTrip() {
        resetTrip();
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    private void markUnobtainable(BlockState state, String why) {
        if (state != null && this.unobtainable.add(state)) {
            logDirect(String.format("Giving up on %s (%s); skipping those blocks and building the rest", state.getBlock().getName().getString(), why));
        }
    }

    /**
     * A readable name for an item. {@code Item#getName} needs a stack, so use the registry key,
     * matching how {@code PickupCommand} names items.
     */
    private static String itemName(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private int countInPlayerInventory(Item item) {
        if (ctx.player() == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : ctx.player().getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private IRestockBoxCollection boxes() {
        if (baritone.getWorldProvider() == null || baritone.getWorldProvider().getCurrentWorld() == null) {
            return null;
        }
        return baritone.getWorldProvider().getCurrentWorld().getRestockBoxes();
    }

    private ContainerInteractionBehavior behavior() {
        return baritone.getContainerInteractionBehavior();
    }

    private void resetTrip() {
        this.state = State.IDLE;
        this.indexing = false;
        this.indexedThisRun = 0;
        this.wantedItem = null;
        this.wantedState = null;
        this.wantedCount = 0;
        this.fetchTarget = 0;
        this.targetBox = null;
        this.candidates = new ArrayList<>();
        this.ticksInState = 0;
        this.transferSlot = 0;
        this.depositSlot = -1;
        this.deposited = 0;
        this.playerCountBefore = 0;
        this.tookAnything = false;
    }

    /**
     * Note that this deliberately does <b>not</b> clear {@link #unobtainable} or
     * {@link #failedThisBuild}. Those are per-build, and wiping them here would send us back to
     * the same empty boxes on the very next tick.
     */
    @Override
    public void onLostControl() {
        if (this.state != State.IDLE) {
            // whatever happened, don't leave a container hanging open
            behavior().closeContainer();
        }
        resetTrip();
    }
}
