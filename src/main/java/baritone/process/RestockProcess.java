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
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.function.Predicate;

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
         * Not restocking. The process being helped is in control.
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

    /**
     * How long to wait after the last shift-click before deciding what actually moved.
     * <p>
     * The server does not acknowledge a container click it agrees with. It records the slot values
     * the client predicted, and {@code broadcastChanges} then sends a slot packet only where the
     * real state differs. So silence means success, and waiting for a packet before sending the
     * next click deadlocks until the timeout. All this window has to cover is the round trip in
     * which a <i>rejected</i> click gets rolled back.
     */
    private static final int TRANSFER_SETTLE_TICKS = 10;

    private State state = State.IDLE;

    /**
     * Materials we have concluded no registered box can supply. This is <b>per build</b>, not per
     * trip: if it were cleared when a restock run ended we would path to the same empty boxes over
     * and over. It is cleared by the builder when a new build starts, and by {@code #addbox}.
     */
    private final Set<BlockState> unobtainable = new HashSet<>();

    /**
     * Boxes that failed for an operational reason during this piece of work. A missing item is not
     * an operational failure: the same box may still be the right place to look for another item.
     */
    private final Set<BetterBlockPos> failedThisBuild = new HashSet<>();

    /**
     * Items we have checked and not found in each box during this piece of work. This is separate
     * from {@link #failedThisBuild}, because a box that lacks one material remains useful for all
     * the others.
     */
    private final Map<BetterBlockPos, Set<Item>> missingItemsThisBuild = new HashMap<>();

    /**
     * Boxes we found no room in during this piece of work. Deliberately separate from
     * {@link #failedThisBuild}: a box too full to accept a deposit is still a perfectly good box to
     * take materials out of, and conflating the two would break restocking mid-build.
     */
    private final Set<BetterBlockPos> fullThisBuild = new HashSet<>();

    /**
     * Whether we have concluded there is nowhere left to unload to for the current piece of work.
     */
    private boolean depositImpossible;

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
    /**
     * The rule supplied by whichever process sent us here. Keeping it per trip means the deposit
     * code need not know whether it interrupted a build or a mine, or guess which process decides
     * what matters.
     */
    private Predicate<ItemStack> worthKeeping;

    private BetterBlockPos targetBox;
    private List<BetterBlockPos> candidates = new ArrayList<>();
    /**
     * When true this run is only cataloguing box contents, not fetching anything, so the transfer
     * phase is skipped and every queued box gets visited in turn.
     */
    private boolean indexing;
    private int indexedThisRun;
    /**
     * When true this run exists solely to empty the inventory: nothing is fetched, every box is
     * treated as somewhere to put things, and surplus scaffolding counts as rubble.
     */
    private boolean depositOnly;
    /**
     * Slots freed across every box visited this trip, and the free-slot count when we started on
     * the current box. Measured rather than counted from clicks, because a click into a full box
     * succeeds locally and moves nothing.
     */
    private int freedThisTrip;
    private int freeSlotsAtBox;
    /**
     * Boxes opened so far on this unload trip, bounded by {@code shulkerDumpMaxBoxesPerTrip} so a
     * large depot can't turn one trip into a tour of every box in it.
     */
    private int boxesVisitedThisTrip;
    /**
     * How many stacks of each throwaway item we've already decided to hold back at this box.
     */
    private final Map<Item, Integer> keptThrowaway = new HashMap<>();

    private int ticksInState;
    /**
     * True after a targeted use-on packet has been issued. A foreign menu that predates that packet
     * is never evidence that {@link #targetBox} opened.
     */
    private boolean attemptedTargetOpen;
    /**
     * The menu id first observed after the targeted open attempt. Every later sync and transfer
     * must remain in this exact session.
     */
    private int openedContainerId = -1;
    private int playerCountBefore;
    private int transferSlot;
    private int depositSlot = -1;
    private int deposited;
    /**
     * Whether the first transfer attempt found the wanted stack but had no room to receive it.
     * The open box stays current while we make room, then the same transfer is tried again.
     */
    private boolean retryTransferAfterDeposit;
    /**
     * A single capacity recovery is enough for one box. If the same live stack still cannot move,
     * the box must be latched as failed rather than keeping the interrupted process in a loop.
     */
    private boolean capacityRetryAttempted;
    /**
     * Whether this trip actually obtained anything. Tracked explicitly rather than inferred by
     * comparing inventory counts, because the player may already be carrying some of the item.
     */
    private boolean tookAnything;

    /**
     * The last feet position seen while walking to the current box. A moving player must not be
     * timed out merely because a long path takes more ticks than the container timeout.
     */
    private BetterBlockPos lastPathingPosition;

    public RestockProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        return this.state != State.IDLE;
    }

    /**
     * Higher than the builder and miner so that we take control when a trip is in flight, and just
     * above the inventory pauser so that a pending hotbar swap can't preempt us mid-container.
     */
    @Override
    public double priority() {
        return 5.2;
    }

    /**
     * This process <b>must</b> be temporary. Both the builder and miner discard their current work
     * in {@code onLostControl}, so a non-temporary process taking control would silently destroy
     * the job we are trying to help.
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
        if (this.depositOnly) {
            return "Unloading" + (this.targetBox == null ? "" : " into " + this.targetBox);
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
        this.missingItemsThisBuild.clear();
        this.fullThisBuild.clear();
        this.depositImpossible = false;
    }

    @Override
    public boolean isDepositImpossible() {
        return this.depositImpossible;
    }

    @Override
    public boolean requestDeposit(Predicate<ItemStack> worthKeeping, boolean latchIfHopeless) {
        return requestDeposit(worthKeeping, latchIfHopeless, null);
    }

    /**
     * Starts a deposit trip, trying {@code preferredTarget} first when it is a viable registered
     * box. Shelter uses this after it has already retreated to a particular rally box: independently
     * re-sorting by capacity there could send the player straight back out into danger.
     */
    boolean requestDeposit(Predicate<ItemStack> worthKeeping, boolean latchIfHopeless, BetterBlockPos preferredTarget) {
        Objects.requireNonNull(worthKeeping);
        // Deliberately not gated on shulkerDump. That setting governs whether running out of room is
        // worth a trip, which is the caller's decision and one both the builder and the miner
        // already make in their own inventoryIsFull(). Checking it again here would silently
        // disable the shelter process's unload, which is not the same thing at all.
        if (isActive() || this.depositImpossible) {
            return false;
        }
        IRestockBoxCollection collection = boxes();
        if (collection == null) {
            return false;
        }
        // Work out what we'd actually put in a box before walking anywhere. If the answer is
        // nothing, the trip is pointless, and finding that out on arrival costs a round walk.
        int junkStacks = junkStacksCarried(worthKeeping);
        if (junkStacks == 0) {
            if (latchIfHopeless) {
                this.depositImpossible = true;
                logDirect("Inventory is full, but everything I'm carrying is worth keeping, so there's nothing to unload.");
            }
            return false;
        }
        BetterBlockPos feet = ctx.playerFeet();
        double maxDistSq = Math.pow(Baritone.settings().restockMaxDistance.value, 2);
        List<IRestockBox> viable = new ArrayList<>();
        for (IRestockBox box : collection.getAllBoxes()) {
            if (box.isMissing()
                    || this.failedThisBuild.contains(box.getLocation())
                    || this.fullThisBuild.contains(box.getLocation())) {
                continue;
            }
            if (box.getLocation().distSqr(feet) > maxDistSq) {
                continue;
            }
            viable.add(box);
        }
        if (viable.isEmpty()) {
            if (latchIfHopeless) {
                // no point being asked again every tick for the rest of this piece of work
                this.depositImpossible = true;
                logDirect("Inventory is full, but there's no registered box left to unload into. Use #addbox to register one, or #set shulkerDump false.");
            }
            return false;
        }
        // Fit first, distance second. Walking to the nearest box regardless of what the index says
        // is in it is how a trip ends up dumping two stacks into an almost-full box and coming
        // straight back; a box that can swallow the whole load is worth a few more blocks of walk.
        Comparator<IRestockBox> capacityOrder = Comparator
                // one that takes everything beats one that takes most of it, however close
                .comparing((IRestockBox box) -> roomFor(box, junkStacks) < junkStacks)
                .thenComparing(Comparator.comparingInt((IRestockBox box) -> roomFor(box, junkStacks)).reversed())
                // a box we've actually looked inside beats one we're merely hoping about
                .thenComparing(IRestockBox::isUnindexed)
                .thenComparingDouble(box -> box.getLocation().distSqr(feet));
        if (preferredTarget == null) {
            viable.sort(capacityOrder);
        } else {
            viable.sort(Comparator
                    .comparing((IRestockBox box) -> !preferredTarget.equals(box.getLocation()))
                    .thenComparing(capacityOrder));
        }
        List<BetterBlockPos> queue = new ArrayList<>();
        for (IRestockBox box : viable) {
            queue.add(box.getLocation());
        }
        this.worthKeeping = worthKeeping;
        this.depositOnly = true;
        this.freedThisTrip = 0;
        this.boxesVisitedThisTrip = 0;
        this.candidates = queue;
        this.targetBox = this.candidates.remove(0);
        startPathing();
        logDirect(String.format("Inventory is full; unloading %d stack(s) into the box at %s", junkStacks, this.targetBox));
        return true;
    }

    /**
     * How many of the stacks we're carrying the index thinks this box could take, capped at the
     * number we actually have.
     * <p>
     * A box that has never been opened is assumed empty rather than skipped, matching how
     * {@link #findCandidates} treats one: an unindexed box is a real possibility, just a less
     * trustworthy one, and the sort demotes it below an equally-capable indexed box.
     */
    private static int roomFor(IRestockBox box, int junkStacks) {
        if (box.isUnindexed()) {
            return junkStacks;
        }
        return Math.min(box.estimatedFreeSlots(), junkStacks);
    }

    @Override
    public boolean requestIndexing(boolean includeAlreadyIndexed, Predicate<ItemStack> worthKeeping) {
        Objects.requireNonNull(worthKeeping);
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
        // Re-indexing is the player's explicit statement that the old view of the depot may be
        // stale. A box refilled since the last failed build must be allowed to prove that here.
        clearUnobtainable();
        // nearest first, so the walk between boxes is roughly sensible
        queue.sort(Comparator.comparingDouble(pos -> pos.distSqr(feet)));
        this.worthKeeping = worthKeeping;
        this.indexing = true;
        this.indexedThisRun = 0;
        this.candidates = queue;
        this.targetBox = this.candidates.remove(0);
        startPathing();
        logDirect("Indexing " + (queue.size() + 1) + " shulker box(es) so I know where materials actually are");
        return true;
    }

    @Override
    public boolean requestRestock(Map<BlockState, Integer> missing, Predicate<ItemStack> worthKeeping) {
        Objects.requireNonNull(worthKeeping);
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
            this.worthKeeping = worthKeeping;
            // keep clearing slots a bit past the immediate shortfall so we don't have to walk
            // straight back for the next few blocks
            this.fetchTarget = fetchTarget(
                    entry.getValue(),
                    Baritone.settings().restockExtraStacks.value,
                    item.getDefaultMaxStackSize()
            );
            this.candidates = found;
            this.targetBox = this.candidates.remove(0);
            startPathing();
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

    @Override
    public boolean requestTool(Item tool, Predicate<ItemStack> worthKeeping) {
        Objects.requireNonNull(worthKeeping);
        if (!Baritone.settings().restockFromBoxes.value || isActive()) {
            return false;
        }
        IRestockBoxCollection collection = boxes();
        if (collection == null) {
            return false;
        }
        List<BetterBlockPos> found = findCandidates(collection, tool);
        if (found.isEmpty()) {
            return false;
        }
        // A tool has no block form, and unlike a material there is nothing to give up on later:
        // the caller stops the job outright when this declines. markUnobtainable tolerates the null.
        this.wantedState = null;
        this.wantedItem = tool;
        this.wantedCount = 1;
        this.worthKeeping = worthKeeping;
        // Tools stack to one, so restockExtraStacks reads as "spares", which is what we want here --
        // walking back to the box for every single pickaxe is the thing this feature exists to avoid.
        this.fetchTarget = fetchTarget(
                1,
                Baritone.settings().restockExtraStacks.value,
                tool.getDefaultMaxStackSize()
        );
        this.candidates = found;
        this.targetBox = this.candidates.remove(0);
        startPathing();
        logDirect(String.format("Fetching a replacement %s from box at %s", itemName(tool), this.targetBox));
        return true;
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
            if (box.isMissing()
                    || this.failedThisBuild.contains(box.getLocation())
                    || this.missingItemsThisBuild.getOrDefault(box.getLocation(), Collections.emptySet()).contains(item)) {
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

    /**
     * Converts the requested surplus stacks to items without assuming every item stacks to 64 or
     * overflowing when a large configured surplus is added to the immediate shortfall.
     */
    static int fetchTarget(int missing, int extraStacks, int maxStackSize) {
        long target = Math.max(0L, missing)
                + (long) Math.max(0, extraStacks) * Math.max(1, maxStackSize);
        return (int) Math.min(Integer.MAX_VALUE, target);
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (this.state == State.IDLE) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        if (this.state != State.OPENING) {
            clearOpeningInput();
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
        ContainerInteractionBehavior behavior = behavior();
        if (behavior.openContainer() != null) {
            // A menu that was already open cannot belong to the use-on interaction we are about to
            // send. Close it before aiming at the target, even if it happens to be another shulker.
            // Deliberately no ticksInState reset here: closing normally takes a single tick, so if
            // one keeps reappearing (the player reopening it, say) the trip has to give up rather
            // than pause forever.
            if (this.ticksInState > Baritone.settings().restockOpenTimeoutTicks.value) {
                logDirect("Something else keeps a container open; giving up on the box at " + this.targetBox);
                return failCurrentBox();
            }
            behavior.closeContainer();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        notePathingProgress();
        if (this.ticksInState > Baritone.settings().restockOpenTimeoutTicks.value) {
            logDirect("Made no progress walking to the box at " + this.targetBox + "; trying the next one");
            return failCurrentBox();
        }
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
            this.attemptedTargetOpen = false;
            this.openedContainerId = -1;
            behavior.resetSync();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        return new PathingCommand(goalForBox(this.targetBox), PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
    }

    /**
     * A shulker box needs the block in front of its lid to be clear, otherwise the server refuses
     * the interaction outright. The lid direction is part of the block state, not always up. When
     * something is in the way we aim to stand in that position, which makes the pathfinder break
     * it on the way, exactly as {@code GetToBlockProcess} does for chests.
     */
    private Goal goalForBox(BetterBlockPos pos) {
        if (baritone.bsi != null) {
            BlockState atBox = baritone.bsi.get0(pos);
            if (atBox.getBlock() instanceof ShulkerBoxBlock) {
                Direction facing = atBox.getValue(ShulkerBoxBlock.FACING);
                BetterBlockPos clearance = pos.relative(facing);
                if (MovementHelper.isBlockNormalCube(baritone.bsi.get0(clearance))) {
                    return new GoalBlock(clearance);
                }
            }
        }
        return new GoalGetToBlock(pos);
    }

    private PathingCommand tickOpening() {
        clearOpeningInput();
        ContainerInteractionBehavior behavior = behavior();
        AbstractContainerMenu menu = behavior.openContainer();
        if (menu != null) {
            if (!this.attemptedTargetOpen) {
                // Something opened without our targeted click. Its contents must never be indexed,
                // fetched, or deposited under targetBox. Same reasoning as tickPathing: let the
                // timeout run so a menu we can't get rid of ends the box rather than the trip.
                if (this.ticksInState > Baritone.settings().restockOpenTimeoutTicks.value) {
                    logDirect("Something else keeps a container open; giving up on the box at " + this.targetBox);
                    return failCurrentBox();
                }
                behavior.closeContainer();
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            clearOpeningInput();
            this.openedContainerId = menu.containerId;
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
            startPathing();
            return new PathingCommand(goalForBox(this.targetBox), PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
        }
        baritone.getLookBehavior().updateTarget(reachable.get(), true);
        if (this.targetBox.equals(ctx.getSelectedBlock().orElse(null))) {
            this.attemptedTargetOpen = true;
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
        }
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickAwaitingSync() {
        ContainerInteractionBehavior behavior = behavior();
        AbstractContainerMenu menu = behavior.openContainer();
        if (menu == null || menu.containerId != this.openedContainerId) {
            // the container closed under us before we could read it
            logDirect("The container session for the box at " + this.targetBox
                    + " changed before its contents arrived");
            return failCurrentBox();
        }
        if (behavior.isContainerReadable()) {
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
            if (this.depositOnly) {
                // nothing to fetch; the box is open, so go straight to filling it
                this.boxesVisitedThisTrip++;
                return afterContainerWork();
            }
            if (contents.getOrDefault(this.wantedItem, 0) <= 0) {
                logDirect("Box at " + this.targetBox + " doesn't have " + itemName(this.wantedItem) + " after all; trying the next one");
                markItemMissing(this.targetBox, this.wantedItem);
                this.state = State.CLOSING;
                this.ticksInState = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            this.playerCountBefore = countInPlayerInventory(this.wantedItem);
            this.transferSlot = 0;
            this.capacityRetryAttempted = false;
            this.state = State.TRANSFERRING;
            this.ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        if (this.ticksInState > Baritone.settings().restockOpenTimeoutTicks.value) {
            logDirect("Timed out waiting for the contents of the box at " + this.targetBox);
            return failCurrentBox();
        }
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    /**
     * Shift-clicks matching stacks out of the box, one slot per tick.
     * <p>
     * {@code handleContainerInput} applies each click to the local menu before sending it, so a
     * click never leaves the menu stale and the next slot can be judged immediately. What the click
     * count cannot tell us is what the <i>server</i> did with it, so the decision at the end is made
     * from the inventory after {@link #TRANSFER_SETTLE_TICKS} rather than from the clicks sent.
     */
    private PathingCommand tickTransferring(boolean isSafeToCancel) {
        ContainerInteractionBehavior behavior = behavior();
        AbstractContainerMenu menu = behavior.openContainer();
        if (menu == null || menu.containerId != this.openedContainerId) {
            // container vanished mid-transfer (server closed it, or we got disconnected)
            logDirect("The container session for the box at " + this.targetBox
                    + " changed mid-transfer");
            return failCurrentBox();
        }

        int taken = countInPlayerInventory(this.wantedItem) - this.playerCountBefore;
        if (taken >= this.fetchTarget) {
            logDirect(String.format("Took %d %s from %s (needed %d)", taken, itemName(this.wantedItem), this.targetBox, this.wantedCount));
            this.tookAnything = true;
            return afterContainerWork();
        }

        int containerSlots = behavior.containerSlotCount(menu);
        while (this.transferSlot < containerSlots) {
            int slot = this.transferSlot++;
            ItemStack stack = menu.slots.get(slot).getItem();
            if (!stack.isEmpty() && stack.getItem() == this.wantedItem) {
                behavior.quickMove(menu, slot);
                this.ticksInState = 0;
                // One click per tick. The click is applied to the local menu synchronously, so
                // the next slot is judged against up-to-date state on the following tick.
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }

        // Every matching slot has been clicked. There is nothing to wait for in the success case:
        // the server absorbs our predicted slot values and only answers a container click when it
        // disagrees with them. So settle briefly to let a correction land, then judge by what is
        // actually in the inventory rather than by how many clicks we sent.
        if (this.ticksInState < TRANSFER_SETTLE_TICKS) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        taken = countInPlayerInventory(this.wantedItem) - this.playerCountBefore;

        if (taken <= 0) {
            // Nothing arrived. QUICK_MOVE silently does nothing when there is nowhere to put the
            // stack, so only the live box contents can distinguish a capacity failure from a
            // box that was emptied after the sync we used to choose it.
            if (hasItemInContainer(menu, containerSlots)) {
                if (this.capacityRetryAttempted) {
                    logDirect("Still couldn't take " + itemName(this.wantedItem)
                            + " from " + this.targetBox + " after making room");
                    return failCurrentBox();
                }
                logDirect("Couldn't take " + itemName(this.wantedItem)
                        + " from " + this.targetBox + " - making room and trying again");
                this.capacityRetryAttempted = true;
                this.retryTransferAfterDeposit = true;
            } else {
                logDirect("Box at " + this.targetBox + " no longer has " + itemName(this.wantedItem)
                        + "; trying the next one");
                markItemMissing(this.targetBox, this.wantedItem);
            }
        } else {
            logDirect(String.format("Took %d %s from %s (needed %d)", taken, itemName(this.wantedItem), this.targetBox, this.wantedCount));
            this.tookAnything = true;
        }
        return afterContainerWork();
    }

    /**
     * Called once we're done taking things out of a box. If the inventory is getting tight, use the
     * open container to offload blocks the interrupted work has no use for before walking away.
     */
    private PathingCommand afterContainerWork() {
        this.ticksInState = 0;
        this.depositSlot = -1;
        this.deposited = 0;
        this.keptThrowaway.clear();
        this.freeSlotsAtBox = freeSlots();
        // a deposit trip has no other reason to be here, so it never skips the dump
        this.state = this.retryTransferAfterDeposit || this.depositOnly || shouldDumpJunk()
                ? State.DEPOSITING
                : State.CLOSING;
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
        return freeSlots() < Baritone.settings().restockDumpWhenFreeSlotsBelow.value;
    }

    private int freeSlots() {
        if (ctx.player() == null) {
            return 0;
        }
        int free = 0;
        for (ItemStack stack : ctx.player().getInventory().items) {
            if (stack.isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /**
     * Whether this stack is rubble rather than something worth keeping.
     * <p>
     * Deliberately narrow: only block items which are not food, equipment, tools or weapons, only
     * ones the interrupted work does not want, and never anything Baritone relies on for
     * scaffolding. Every non-block item is always kept as well.
     * <p>
     * The one place this loosens is a deposit trip, where the whole point is to make room: there we
     * hold back {@code shulkerDumpKeepThrowawayStacks} stacks of each scaffolding block and
     * treat the surplus as rubble, since otherwise a cleararea over stone fills up with cobblestone
     * that nothing is ever allowed to deposit.
     * <p>
     * Note this counts as it goes, so it must only ever be called once per slot, walking the
     * player's half of the menu in order -- which is exactly what {@link #tickDepositing} does.
     */
    private boolean isJunk(ItemStack stack) {
        return isJunk(stack, this.worthKeeping, this.depositOnly, this.keptThrowaway);
    }

    /**
     * The rule itself, with every input passed in rather than read from the trip's fields.
     * <p>
     * Split out so that a survey of what we're carrying and the deposit that follows it can be
     * guaranteed to agree. They must: the trip only ends when the survey says no junk is left, so a
     * survey that counts a stack the deposit pass would refuse to move is an infinite loop.
     *
     * @param keptThrowaway The throwaway budget spent so far by <i>this</i> pass. Each caller owns
     *                      its own map; sharing one between a survey and a deposit would double-count.
     */
    private static boolean isJunk(ItemStack stack, Predicate<ItemStack> worthKeeping,
                                  boolean depositOnly, Map<Item, Integer> keptThrowaway) {
        if (worthKeeping == null
                || stack.isEmpty()
                || !(stack.getItem() instanceof BlockItem)
                || stack.get(DataComponents.FOOD) != null
                || stack.get(DataComponents.TOOL) != null
        ) {
            return false;
        }
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof CakeBlock || block instanceof ShulkerBoxBlock) {
            return false;
        }
        if (Baritone.settings().acceptableThrowawayItems.value.contains(stack.getItem())) {
            if (!depositOnly) {
                return false; // needed for pillaring and bridging
            }
            int kept = keptThrowaway.getOrDefault(stack.getItem(), 0);
            if (kept < Baritone.settings().shulkerDumpKeepThrowawayStacks.value) {
                keptThrowaway.put(stack.getItem(), kept + 1);
                return false;
            }
            // everything past the stacks we held back is rubble like any other
        }
        return !worthKeeping.test(stack);
    }

    /**
     * How many stacks in the player's inventory a deposit trip would be willing to put in a box.
     * <p>
     * Uses the same rule as {@link #tickDepositing} with a fresh throwaway budget, so the answer
     * matches what a deposit would actually move. The iteration order differs from the menu's, which
     * does not matter: the budget holds back a fixed number of stacks per throwaway item, so the
     * total is the same whichever stacks happen to be the ones kept. Always evaluated with
     * deposit-trip rules, because that is the only thing this number is used to decide.
     */
    private int junkStacksCarried(Predicate<ItemStack> worthKeeping) {
        if (ctx.player() == null || worthKeeping == null) {
            return 0;
        }
        Map<Item, Integer> scratch = new HashMap<>();
        int junk = 0;
        for (ItemStack stack : ctx.player().getInventory().items) {
            if (isJunk(stack, worthKeeping, true, scratch)) {
                junk++;
            }
        }
        return junk;
    }

    /**
     * Shift-clicks junk out of the player's half of the open menu, one slot per tick. If the box is
     * full the click is accepted and moves nothing, which the local slot count detects, and we
     * continue to the next slot.
     */
    private PathingCommand tickDepositing() {
        ContainerInteractionBehavior behavior = behavior();
        AbstractContainerMenu menu = behavior.openContainer();
        if (menu == null || menu.containerId != this.openedContainerId) {
            // The box shut on us before we'd finished. Treat it as a failed box rather than trusting
            // or writing through a replacement menu; for deposit-only trips this also prevents an
            // immediate walk back to the same unusable box.
            logDirect("The container session for the box at " + this.targetBox
                    + " changed while I was unloading");
            return failCurrentBox();
        }
        int containerSlots = behavior.containerSlotCount(menu);
        if (this.depositSlot < containerSlots) {
            this.depositSlot = containerSlots; // player's own slots start here
        }
        while (this.depositSlot < menu.slots.size()) {
            int slot = this.depositSlot++;
            ItemStack stack = menu.slots.get(slot).getItem();
            if (isJunk(stack)) {
                int countBefore = stack.getCount();
                behavior.quickMove(menu, slot);
                // The click is applied to the local menu synchronously, so the source slot itself
                // says whether the stack went anywhere. Shift-clicking into a full box is accepted
                // and moves nothing, which is exactly the case this has to distinguish.
                if (menu.slots.get(slot).getItem().getCount() < countBefore) {
                    this.deposited++;
                }
                this.ticksInState = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }
        // Settle before judging, for the same reason as the transfer loop: a click the server
        // rejects is rolled back a tick or two later, and a click it accepts is answered with
        // silence.
        if (this.ticksInState < TRANSFER_SETTLE_TICKS) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        // Clicks are not proof: shift-clicking into a full box succeeds locally and moves nothing.
        // What actually happened is the change in free slots.
        int freed = freeSlots() - this.freeSlotsAtBox;
        if (this.deposited > 0) {
            logDirect(String.format("Put %d stack(s) of unwanted blocks into %s", this.deposited, this.targetBox));
            IRestockBoxCollection collection = boxes();
            if (collection != null && behavior.isContainerReadable()) {
                // what we just added changes the box's contents, so re-record them
                collection.updateContents(this.targetBox, behavior.readContents(menu));
            }
        }
        if (this.depositOnly) {
            this.freedThisTrip += Math.max(0, freed);
            if (freed <= 0) {
                // either it's full or it rejected everything; don't come back to it for this work
                this.fullThisBuild.add(this.targetBox);
            }
        }
        if (this.retryTransferAfterDeposit) {
            if (freed <= 0) {
                this.retryTransferAfterDeposit = false;
                logDirect("Couldn't make room in the inventory to take " + itemName(this.wantedItem)
                        + " from " + this.targetBox + "; trying the next box");
                return failCurrentBox();
            }
            this.retryTransferAfterDeposit = false;
            this.playerCountBefore = countInPlayerInventory(this.wantedItem);
            this.transferSlot = 0;
            this.state = State.TRANSFERRING;
            this.ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
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
                startPathing();
                return new PathingCommand(goalForBox(this.targetBox), PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
            }
            logDirect("Finished indexing " + this.indexedThisRun + " shulker box(es). Use #listboxes to see what's where.");
            return finishTrip();
        }
        if (this.depositOnly) {
            // Keep going while there is still rubble to put down and somewhere to put it. Stopping
            // at a free-slot count is what made these trips useless: two slots is enough to satisfy
            // the threshold and nowhere near enough to get back to work for any length of time.
            // Note the box we just filled has had its index refreshed by the deposit, so the next
            // trip's capacity sort will already know it has no room left.
            int stillCarrying = junkStacksCarried(this.worthKeeping);
            if (stillCarrying > 0 && !this.candidates.isEmpty()) {
                if (this.boxesVisitedThisTrip >= Baritone.settings().shulkerDumpMaxBoxesPerTrip.value) {
                    logDirect(String.format(
                            "Stopping after %d box(es) this trip with %d stack(s) still on me; raise shulkerDumpMaxBoxesPerTrip to visit more",
                            this.boxesVisitedThisTrip, stillCarrying));
                } else {
                    this.targetBox = this.candidates.remove(0);
                    startPathing();
                    return new PathingCommand(goalForBox(this.targetBox), PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
                }
            }
            if (this.freedThisTrip == 0) {
                // every box we could reach took nothing, so asking again would just repeat the walk
                this.depositImpossible = true;
                logDirect("Couldn't unload anything - the registered boxes are full, or everything I'm carrying is worth keeping.");
            } else {
                logDirect(String.format("Freed %d inventory slot(s); back to it", this.freedThisTrip));
            }
            return finishTrip();
        }
        // if this box let us down and there are other candidates, go straight to the next one
        // rather than bouncing back to the build site first
        if (!this.candidates.isEmpty() && !this.tookAnything) {
            this.targetBox = this.candidates.remove(0);
            startPathing();
            return new PathingCommand(goalForBox(this.targetBox), PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
        }
        return finishTrip();
    }

    /**
     * Abandons the current box and moves to the next candidate, or gives up on the material if
     * there are none left.
     */
    private PathingCommand failCurrentBox() {
        this.failedThisBuild.add(this.targetBox);
        if (this.depositOnly) {
            // a box we can't reach or open is no use as somewhere to put things either
            this.fullThisBuild.add(this.targetBox);
        }
        clearOpeningInput();
        behavior().closeContainer();
        if (!this.candidates.isEmpty()) {
            this.targetBox = this.candidates.remove(0);
            startPathing();
            return new PathingCommand(goalForBox(this.targetBox), PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
        }
        if (this.depositOnly && this.freedThisTrip == 0) {
            // nothing was unloaded anywhere, so let the interrupted process stop asking
            this.depositImpossible = true;
            logDirect("Couldn't unload anything - none of the registered boxes could be used.");
        }
        markUnobtainable(this.wantedState, "every candidate box failed");
        return finishTrip();
    }

    /**
     * Ends the trip and hands control back to the interrupted process, which will see the changed
     * inventory on its next tick and carry on with the room that was made.
     */
    private PathingCommand finishTrip() {
        // Shelter deliberately defers while this lower-priority helper owns the container. Consume
        // the completion tick when handing back to it; otherwise the scheduler continues past both
        // temporary processes and can let the interrupted builder or miner act once mid-retreat.
        boolean returningToShelter = baritone.getShelterProcess() != null
                && baritone.getShelterProcess().isActive();
        resetTrip();
        return new PathingCommand(
                null,
                returningToShelter ? PathingCommandType.REQUEST_PAUSE : PathingCommandType.DEFER
        );
    }

    private void markUnobtainable(BlockState state, String why) {
        if (state != null && this.unobtainable.add(state)) {
            logDirect(String.format("Giving up on %s (%s); skipping those blocks and building the rest", state.getBlock().getName().getString(), why));
        }
    }

    private void markItemMissing(BetterBlockPos box, Item item) {
        this.missingItemsThisBuild.computeIfAbsent(box, ignored -> new HashSet<>()).add(item);
    }

    private boolean hasItemInContainer(AbstractContainerMenu menu, int containerSlots) {
        for (int slot = 0; slot < containerSlots; slot++) {
            ItemStack stack = menu.slots.get(slot).getItem();
            if (!stack.isEmpty() && stack.getItem() == this.wantedItem) {
                return true;
            }
        }
        return false;
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
        for (ItemStack stack : ctx.player().getInventory().items) {
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

    private void startPathing() {
        this.state = State.PATHING;
        this.ticksInState = 0;
        this.lastPathingPosition = null;
        this.attemptedTargetOpen = false;
        this.openedContainerId = -1;
    }

    private void notePathingProgress() {
        BetterBlockPos feet = ctx.playerFeet();
        if (!feet.equals(this.lastPathingPosition)) {
            this.lastPathingPosition = feet;
            this.ticksInState = 0;
        }
    }

    private void clearOpeningInput() {
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
    }

    private void resetTrip() {
        clearOpeningInput();
        this.state = State.IDLE;
        this.indexing = false;
        this.indexedThisRun = 0;
        this.depositOnly = false;
        this.freedThisTrip = 0;
        this.freeSlotsAtBox = 0;
        this.boxesVisitedThisTrip = 0;
        this.keptThrowaway.clear();
        this.wantedItem = null;
        this.wantedState = null;
        this.wantedCount = 0;
        this.fetchTarget = 0;
        this.worthKeeping = null;
        this.targetBox = null;
        this.candidates = new ArrayList<>();
        this.ticksInState = 0;
        this.attemptedTargetOpen = false;
        this.openedContainerId = -1;
        this.transferSlot = 0;
        this.depositSlot = -1;
        this.deposited = 0;
        this.retryTransferAfterDeposit = false;
        this.capacityRetryAttempted = false;
        this.playerCountBefore = 0;
        this.tookAnything = false;
        this.lastPathingPosition = null;
    }

    /**
     * Note that this deliberately does <b>not</b> clear {@link #unobtainable} or
     * {@link #failedThisBuild}. They outlive a single trip, and wiping them here would send us back
     * to the same empty or unusable boxes on the very next tick.
     */
    @Override
    public void onLostControl() {
        clearOpeningInput();
        if (this.state != State.IDLE) {
            // whatever happened, don't leave a container hanging open
            behavior().closeContainer();
        }
        resetTrip();
    }
}
