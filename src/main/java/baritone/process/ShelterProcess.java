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
import baritone.api.BaritoneAPI;
import baritone.api.cache.IRestockBox;
import baritone.api.cache.IRestockBoxCollection;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.IShelterProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.behavior.ThreatBehavior;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Gets out of a fight, and sleeps through the night if it can.
 * <p>
 * Baritone will happily keep mining while a zombie beats on it, because nothing in the pathing
 * stack has ever looked at whether the player is being hurt. This process is that missing reaction:
 * when {@link ThreatBehavior} reports hostiles are actually landing hits, it abandons the work in
 * progress, walks to the nearest registered shulker box -- which is where a base tends to be, and
 * is somewhere the player has already decided is worth being -- and empties the rubble it's
 * carrying so that dying costs less than it otherwise would.
 * <p>
 * If it's night, it then looks for a bed within {@code shelterBedSearchRadius} and tries to sleep,
 * which is both the safest place to be and the fastest way to make the mobs go away. Sleeping is
 * frequently refused ("you may not rest now, there are monsters nearby"), so a refusal is not an
 * error: it goes back to the box, waits, and tries again, up to {@code shelterMaxSleepAttempts}
 * times, before settling for simply waiting the threat out.
 * <p>
 * Everything here goes through real inputs. The bed is entered by looking at it and forcing a
 * genuine right-click, exactly as {@link RestockProcess} opens a box, so the server applies its own
 * reach and permission checks.
 *
 * @see IShelterProcess
 */
public final class ShelterProcess extends BaritoneProcessHelper implements IShelterProcess {

    /**
     * How long to keep right-clicking a bed before deciding the game has refused us.
     * <p>
     * The refusal arrives as a translated chat message, which is not something worth parsing --
     * servers rewrite it, and it is not the only reason entering a bed can silently fail. A
     * right-click that is going to work does so within a tick or two, so a short timeout is a more
     * robust signal than the message ever would be.
     */
    private static final int BED_ATTEMPT_TICKS = 40;

    /**
     * Close enough to the box to count as having arrived. The box itself is a solid block, so
     * standing on it is not an option; anywhere adjacent will do.
     */
    private static final double ARRIVAL_DIST_SQ = 9.0;

    /**
     * A bed found nearby, remembered as both halves so that whichever one the crosshair lands on
     * counts as having hit it.
     */
    private static final class Bed {

        private final BetterBlockPos head;
        private final BetterBlockPos foot;

        private Bed(BetterBlockPos head, BetterBlockPos foot) {
            this.head = head;
            this.foot = foot;
        }

        private boolean is(BlockPos pos) {
            return this.head.equals(pos) || this.foot.equals(pos);
        }
    }

    private enum State {
        /**
         * Not sheltering. Whatever was working is in control.
         */
        IDLE,
        /**
         * Walking to the box we're using as a rally point.
         */
        RETREATING,
        /**
         * Standing at the box, letting the restock process empty our inventory into it.
         */
        UNLOADING,
        /**
         * Back at the box after a bed turned us away, waiting a moment before trying again.
         */
        REGROUPING,
        /**
         * Looking for a bed worth walking to.
         */
        SEEKING_BED,
        /**
         * Walking to the chosen bed.
         */
        APPROACHING_BED,
        /**
         * In range of the bed; right-clicking it and watching for sleep to actually start.
         */
        ENTERING_BED,
        /**
         * Asleep. Nothing to do but stay still until morning.
         */
        SLEEPING,
        /**
         * Sitting tight at the box until it's calm enough to go back to work.
         */
        WAITING
    }

    private State state = State.IDLE;

    /**
     * The rule supplied by whichever process sent us here, forwarded to the deposit so that the
     * interrupted job's materials aren't unloaded along with the rubble.
     */
    private Predicate<ItemStack> worthKeeping;

    private BetterBlockPos rallyBox;
    private Bed targetBed;
    /**
     * Beds that turned out to be structurally invalid or unreachable during this run. A valid bed
     * that merely refused sleep is deliberately not added: "monsters nearby" is temporary, and the
     * configured retry delay exists to let us try that same bed again.
     */
    private final Set<BetterBlockPos> rejectedBeds = new HashSet<>();
    private int sleepAttempts;
    /**
     * Whether the deposit has already happened this run, so the walk back after a refused bed
     * doesn't re-open the box with nothing to put in it.
     */
    private boolean unloaded;
    /**
     * Set once we've handed off to the restock process, so we know to wait for it to finish rather
     * than asking it again every tick.
     */
    private boolean depositHandedOff;

    private int ticksInState;
    /**
     * The last feet position seen while walking, so a long path isn't timed out merely for being
     * long. Same trick as {@link RestockProcess}.
     */
    private BetterBlockPos lastPathingPosition;

    /**
     * Reported once per run rather than once per tick.
     */
    private String lastNote;

    public ShelterProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        return this.state != State.IDLE;
    }

    /**
     * Above every other process, including the restock process it borrows to do the unloading.
     * Getting away from something that is actively killing us outranks any job, and a restock trip
     * in flight is exactly the sort of thing that needs interrupting rather than finishing.
     */
    @Override
    public double priority() {
        return 6.0;
    }

    /**
     * This process <b>must</b> be temporary. The builder and miner both discard their work in
     * {@code onLostControl}, so taking control non-temporarily would destroy the very job we
     * interrupted to protect.
     */
    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public String displayName0() {
        switch (this.state) {
            case RETREATING:
                return "Retreating from mobs" + (this.rallyBox == null ? "" : " to " + this.rallyBox);
            case UNLOADING:
                return "Unloading before things get worse";
            case REGROUPING:
                return "Waiting before another go at the bed";
            case SEEKING_BED:
            case APPROACHING_BED:
            case ENTERING_BED:
                return "Looking for somewhere to sleep";
            case SLEEPING:
                return "Sleeping through the night";
            default:
                return "Waiting for the mobs to lose interest";
        }
    }

    @Override
    public boolean requestShelter(Predicate<ItemStack> worthKeeping) {
        Objects.requireNonNull(worthKeeping);
        if (!Baritone.settings().shelterOnAttack.value || isActive() || ctx.player() == null) {
            return false;
        }
        ThreatBehavior threat = baritone.getThreatBehavior();
        if (threat == null || !threat.underAttack()) {
            return false;
        }
        BoxSearch search = nearestBoxWithReason();
        BetterBlockPos box = search.box;
        if (box == null) {
            // Nowhere to run to. Say so once and clear the threat, otherwise every tick spent being
            // hit re-asks and re-logs. The next burst of hits will ask again, which is right: the
            // player may have registered a box in the meantime.
            logDirect("Under attack, but I can't retreat: " + search.reason + ".");
            threat.clearThreat();
            return false;
        }
        this.worthKeeping = worthKeeping;
        this.rallyBox = box;
        this.rejectedBeds.clear();
        this.sleepAttempts = 0;
        this.unloaded = false;
        this.depositHandedOff = false;
        this.lastNote = null;
        startPathing(State.RETREATING);
        logDirect("Under attack; retreating to the box at " + box);
        return true;
    }

    /**
     * The box selected for the current retreat, or {@code null} while sheltering is idle. This is
     * exposed for the live-test harness to assert the selection policy before pathing can replace
     * its goal after a calculation failure.
     */
    public BetterBlockPos getRallyBox() {
        return this.rallyBox;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (this.state == State.IDLE) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        if (ctx.player() == null) {
            return finish(null);
        }
        if (this.state != State.ENTERING_BED) {
            releaseRightClick();
        }
        this.ticksInState++;

        switch (this.state) {
            case RETREATING:
                return tickRetreating(calcFailed);
            case UNLOADING:
                return tickUnloading();
            case REGROUPING:
                return tickRegrouping();
            case SEEKING_BED:
                return tickSeekingBed();
            case APPROACHING_BED:
                return tickApproachingBed(calcFailed);
            case ENTERING_BED:
                return tickEnteringBed();
            case SLEEPING:
                return tickSleeping();
            case WAITING:
            default:
                return tickWaiting();
        }
    }

    private PathingCommand tickRetreating(boolean calcFailed) {
        if (calcFailed) {
            return giveUpOnRetreat("couldn't path to the box at " + this.rallyBox);
        }
        notePathingProgress();
        if (this.ticksInState > Baritone.settings().shelterRetreatTimeoutTicks.value) {
            return giveUpOnRetreat("made no progress retreating to " + this.rallyBox);
        }
        if (ctx.playerFeet().distSqr(this.rallyBox) <= ARRIVAL_DIST_SQ) {
            this.state = this.unloaded ? State.REGROUPING : State.UNLOADING;
            this.ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        return new PathingCommand(new GoalGetToBlock(this.rallyBox), PathingCommandType.SET_GOAL_AND_PATH);
    }

    /**
     * Sits at the box for a moment between bed attempts. Walking straight back to a bed that just
     * refused us would only get the same answer -- whatever was standing near it needs a chance to
     * wander off first.
     */
    private PathingCommand tickRegrouping() {
        if (this.ticksInState < Baritone.settings().shelterRetryDelayTicks.value) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        this.state = State.SEEKING_BED;
        this.ticksInState = 0;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    /**
     * Hands the container work to {@link RestockProcess} rather than duplicating it. That process
     * already knows how to open a box with real packets, read it once the server has actually sent
     * its contents, and decide what counts as rubble; and it sits below us in priority, so
     * deferring is all it takes to let it run.
     */
    private PathingCommand tickUnloading() {
        RestockProcess restock = baritone.getRestockProcess();
        if (restock == null || !Baritone.settings().shelterUnloadOnRetreat.value) {
            this.unloaded = true;
            this.state = State.SEEKING_BED;
            this.ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        if (!this.depositHandedOff) {
            // A deposit trip may already have been ruled out for the interrupted job; that's a
            // statement about the boxes being full, and is no reason to stop sheltering. Ask
            // speculatively, because unlike the builder we're here whether or not we're full, and
            // "nothing to unload right now" must not latch the builder out of unloading later.
            if (!restock.isDepositImpossible()
                    && restock.requestDeposit(this.worthKeeping, false, this.rallyBox)) {
                this.depositHandedOff = true;
                // Hold control for this one tick rather than deferring: the active-process list was
                // built before we made that call, so the restock process isn't in it yet and
                // deferring now would hand the builder a tick of work in the middle of a retreat.
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            note("nothing worth unloading");
            this.unloaded = true;
            this.state = State.SEEKING_BED;
            this.ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        if (restock.isActive()) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        this.unloaded = true;
        this.state = State.SEEKING_BED;
        this.ticksInState = 0;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickSeekingBed() {
        if (!Baritone.settings().shelterSleepInBeds.value) {
            return startWaiting("not looking for a bed (shelterSleepInBeds is off)");
        }
        if (!bedsWorkHere()) {
            return startWaiting("beds are no use in this dimension");
        }
        if (!canSleepNow()) {
            return startWaiting("too early to sleep");
        }
        if (this.sleepAttempts >= Baritone.settings().shelterMaxSleepAttempts.value) {
            return startWaiting("gave up on sleeping after " + this.sleepAttempts + " attempt(s)");
        }
        Bed bed = findBed();
        if (bed == null) {
            return startWaiting("no bed within " + Baritone.settings().shelterBedSearchRadius.value + " blocks");
        }
        this.targetBed = bed;
        startPathing(State.APPROACHING_BED);
        logDirect("Heading for the bed at " + bed.head + " to sit the night out");
        return new PathingCommand(bedGoal(bed), PathingCommandType.SET_GOAL_AND_PATH);
    }

    private PathingCommand tickApproachingBed(boolean calcFailed) {
        if (calcFailed) {
            return rejectBed("couldn't path to the bed at " + this.targetBed.head, true);
        }
        notePathingProgress();
        if (this.ticksInState > Baritone.settings().shelterRetreatTimeoutTicks.value) {
            return rejectBed("made no progress towards the bed at " + this.targetBed.head, true);
        }
        if (!isBed(this.targetBed.head) && !isBed(this.targetBed.foot)) {
            return rejectBed("the bed at " + this.targetBed.head + " isn't there any more", true);
        }
        // Both conditions matter: reachableBed gives us something to aim at, withinSleepRange is
        // what the server will actually accept. Starting to click on the strength of the first
        // alone spends a sleep attempt on a position that could never have worked.
        if (withinSleepRange(this.targetBed) && reachableBed(this.targetBed).isPresent()) {
            this.state = State.ENTERING_BED;
            this.ticksInState = 0;
            this.sleepAttempts++;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        return new PathingCommand(bedGoal(this.targetBed), PathingCommandType.SET_GOAL_AND_PATH);
    }

    private PathingCommand tickEnteringBed() {
        if (ctx.player().isSleeping()) {
            releaseRightClick();
            this.state = State.SLEEPING;
            this.ticksInState = 0;
            logDirect("Asleep; the night can happen without us");
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        if (this.ticksInState > BED_ATTEMPT_TICKS) {
            releaseRightClick();
            // Almost always "there are monsters nearby". Go back to the box and try again in a
            // moment rather than standing here clicking at a bed that keeps saying no.
            return rejectBed("couldn't get into the bed at " + this.targetBed.head
                    + " (attempt " + this.sleepAttempts + "); backing off and trying again", false);
        }
        Optional<Rotation> reachable = reachableBed(this.targetBed);
        if (!reachable.isPresent() || !withinSleepRange(this.targetBed)) {
            // drifted out of aim or out of the sleep box; walk back rather than keep clicking
            releaseRightClick();
            startPathing(State.APPROACHING_BED);
            return new PathingCommand(bedGoal(this.targetBed), PathingCommandType.SET_GOAL_AND_PATH);
        }
        baritone.getLookBehavior().updateTarget(reachable.get(), true);
        // Only click once the crosshair is genuinely on the bed, so this can never turn into a
        // right-click on whatever else happens to be in front of us.
        BlockPos selected = ctx.getSelectedBlock().orElse(null);
        if (selected != null && this.targetBed.is(selected)) {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
        }
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickSleeping() {
        if (ctx.player().isSleeping()) {
            // stay put; moving would wake us straight back up
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        return finish("Morning. Back to it.");
    }

    private PathingCommand tickWaiting() {
        ThreatBehavior threat = baritone.getThreatBehavior();
        if (threat == null || threat.ticksSinceLastHit() > Baritone.settings().shelterThreatMemoryTicks.value) {
            return finish("Nothing has hit me for a while; back to it.");
        }
        if (this.ticksInState > Baritone.settings().shelterMaxWaitTicks.value) {
            // Waiting isn't working -- something is still reaching us here. Going back to the job is
            // no worse than standing still being hit, and at least gets the work done.
            return finish("Still being attacked after waiting " + this.ticksInState + " ticks; going back to work anyway.");
        }
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    /**
     * Abandons the current bed for this run and goes back to the rally box, as the player asked for:
     * retreat, try the bed, and if it won't have us, retreat again and try once more.
     */
    private PathingCommand rejectBed(String why, boolean excludeForRun) {
        note(why);
        if (this.targetBed != null) {
            if (excludeForRun) {
                this.rejectedBeds.add(this.targetBed.head);
            }
            this.targetBed = null;
        }
        if (this.sleepAttempts >= Baritone.settings().shelterMaxSleepAttempts.value) {
            return startWaiting("out of sleep attempts");
        }
        startPathing(State.RETREATING);
        return new PathingCommand(new GoalGetToBlock(this.rallyBox), PathingCommandType.SET_GOAL_AND_PATH);
    }

    /**
     * The retreat itself failed. Standing still where we are is strictly worse than working --
     * whatever is hitting us can still reach us either way, and at least one of the two gets the job
     * done -- so hand back rather than settling in somewhere we didn't choose.
     */
    private PathingCommand giveUpOnRetreat(String why) {
        return finish("Sheltering: " + why + "; carrying on with the job instead");
    }

    private PathingCommand startWaiting(String why) {
        note(why);
        this.state = State.WAITING;
        this.ticksInState = 0;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    /**
     * Ends the run and hands control back to whatever was interrupted, which picks up where it left
     * off because this process is temporary.
     */
    private PathingCommand finish(String why) {
        if (why != null) {
            logDirect(why);
        }
        // Forget the hits that sent us out here. Without this the same stale burst re-triggers the
        // moment we hand back, and we'd bounce straight out again having achieved nothing.
        ThreatBehavior threat = baritone.getThreatBehavior();
        if (threat != null) {
            threat.clearThreat();
        }
        onLostControl();
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    @Override
    public void onLostControl() {
        releaseRightClick();
        this.state = State.IDLE;
        this.worthKeeping = null;
        this.rallyBox = null;
        this.targetBed = null;
        this.rejectedBeds.clear();
        this.sleepAttempts = 0;
        this.unloaded = false;
        this.depositHandedOff = false;
        this.ticksInState = 0;
        this.lastPathingPosition = null;
        this.lastNote = null;
    }

    /**
     * The nearest registered box we haven't been told is gone, or {@code null} if there are none.
     */
    private BetterBlockPos nearestBox() {
        return nearestBoxWithReason().box;
    }

    /**
     * The outcome of a shelter box search, carrying why it failed rather than only that it did.
     * <p>
     * Four quite different situations produce no box — nothing registered, everything too far,
     * everything flagged missing, and world data not loaded — and telling a player "no box is
     * registered" when thirty are registered forty blocks too far away sends them to run
     * {@code #addbox}, which then reports that they are all already registered. Under attack is
     * the worst possible moment to hand someone a diagnosis that is not true.
     */
    private static final class BoxSearch {
        final BetterBlockPos box;
        /** Null when a box was found; otherwise a player-facing explanation. */
        final String reason;

        BoxSearch(BetterBlockPos box, String reason) {
            this.box = box;
            this.reason = reason;
        }
    }

    private BoxSearch nearestBoxWithReason() {
        if (baritone.getWorldProvider() == null || baritone.getWorldProvider().getCurrentWorld() == null) {
            return new BoxSearch(null, "world data isn't loaded yet");
        }
        IRestockBoxCollection collection = baritone.getWorldProvider().getCurrentWorld().getRestockBoxes();
        if (collection == null) {
            return new BoxSearch(null, "world data isn't loaded yet");
        }
        BetterBlockPos feet = ctx.playerFeet();
        double shelterMaxDistance = Baritone.settings().shelterMaxRetreatDistance.value;
        double restockMaxDistance = Baritone.settings().restockMaxDistance.value;
        if (!Double.isFinite(shelterMaxDistance) || shelterMaxDistance <= 0) {
            shelterMaxDistance = restockMaxDistance;
        }
        // Restocking is a considered trip made when convenient; sheltering happens while taking
        // damage, so a distance that's merely inefficient for restocking is dangerous in retreat.
        double limit = Math.min(shelterMaxDistance, restockMaxDistance);
        double maxDistSq = Math.pow(limit, 2);
        BetterBlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        int registered = 0;
        int missing = 0;
        // tracked past the limit purely so the failure can name a real distance to compare against
        double nearestAnyDistSq = Double.MAX_VALUE;
        for (IRestockBox box : collection.getAllBoxes()) {
            registered++;
            if (box.isMissing()) {
                missing++;
                continue;
            }
            double distSq = box.getLocation().distSqr(feet);
            nearestAnyDistSq = Math.min(nearestAnyDistSq, distSq);
            if (distSq > maxDistSq || distSq >= bestDistSq) {
                continue;
            }
            bestDistSq = distSq;
            best = box.getLocation();
        }
        if (best != null) {
            return new BoxSearch(best, null);
        }
        if (registered == 0) {
            return new BoxSearch(null, "no shulker box is registered in this dimension. Use #addbox to register one, or #set shelterOnAttack false");
        }
        if (missing == registered) {
            return new BoxSearch(null, String.format(
                    "all %d registered box(es) are marked missing. Check #listboxes", registered));
        }
        return new BoxSearch(null, String.format(
                "the nearest of %d registered box(es) is %d blocks away, past the %d block retreat limit. Raise shelterMaxRetreatDistance, or register a box closer to where you're working",
                registered, Math.round(Math.sqrt(nearestAnyDistSq)), Math.round(limit)));
    }

    /**
     * The nearest bed we haven't already been turned away from.
     * <p>
     * Uses the same chunk scanner {@code #addbox <radius>} uses, so this only sees loaded chunks --
     * which is what we want, since a bed we can't observe is one we can't walk into.
     */
    private Bed findBed() {
        int radius = Math.max(1, Baritone.settings().shelterBedSearchRadius.value);
        List<Block> bedBlocks = BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof BedBlock)
                .collect(Collectors.toList());
        // the scanner works in chunks, so round the block radius up and filter precisely after
        int chunkRadius = (radius >> 4) + 1;
        List<BlockPos> found = BaritoneAPI.getProvider().getWorldScanner()
                .scanChunkRadius(ctx, bedBlocks, 64, -1, chunkRadius);

        BetterBlockPos feet = ctx.playerFeet();
        double maxDistSq = (double) radius * radius;
        // A bed is two blocks and the scan returns both, so normalise everything to the head and
        // dedupe -- the same foot-to-head step WaypointBehavior does when saving a bed waypoint.
        Set<BetterBlockPos> heads = new LinkedHashSet<>();
        for (BlockPos raw : found) {
            BetterBlockPos pos = BetterBlockPos.from(raw);
            if (pos.distSqr(feet) > maxDistSq) {
                continue;
            }
            BetterBlockPos head = headOf(pos);
            if (head != null && !this.rejectedBeds.contains(head)) {
                heads.add(head);
            }
        }
        List<BetterBlockPos> sorted = new ArrayList<>(heads);
        sorted.sort(Comparator.comparingDouble(pos -> pos.distSqr(feet)));
        for (BetterBlockPos head : sorted) {
            BetterBlockPos foot = footOf(head);
            if (foot != null) {
                return new Bed(head, foot);
            }
        }
        return null;
    }

    private BetterBlockPos headOf(BetterBlockPos pos) {
        BlockState state = stateAt(pos);
        if (state == null || !(state.getBlock() instanceof BedBlock)) {
            return null;
        }
        if (state.getValue(BedBlock.PART) == BedPart.HEAD) {
            return pos;
        }
        return BetterBlockPos.from(pos.relative(state.getValue(BedBlock.FACING)));
    }

    private BetterBlockPos footOf(BetterBlockPos head) {
        BlockState state = stateAt(head);
        if (state == null || !(state.getBlock() instanceof BedBlock)) {
            return null;
        }
        return BetterBlockPos.from(head.relative(state.getValue(BedBlock.FACING).getOpposite()));
    }

    private boolean isBed(BetterBlockPos pos) {
        BlockState state = stateAt(pos);
        return state != null && state.getBlock() instanceof BedBlock;
    }

    private BlockState stateAt(BetterBlockPos pos) {
        if (baritone.bsi == null) {
            return null;
        }
        return baritone.bsi.get0(pos);
    }

    /**
     * Whether the game would let us into a bed at all right now.
     * <p>
     * Only an approximation of the server's own rule -- it is the server that decides, and a refusal
     * is picked up by {@link #BED_ATTEMPT_TICKS} regardless. The point of checking is to avoid
     * walking to a bed in broad daylight for no reason.
     */
    private boolean canSleepNow() {
        return !ctx.world().isBrightOutside() || ctx.world().isThundering();
    }

    /**
     * Whether beds are somewhere to sleep here rather than a bomb.
     * <p>
     * There is no longer a {@code bedWorks} flag to read, so this goes on the two properties that
     * distinguish the dimensions where beds explode: a ceiling (the Nether) and a sky that never
     * changes (the End). Being wrong here would be expensive, so it errs towards not trying.
     */
    private boolean bedsWorkHere() {
        return !ctx.world().dimensionType().hasCeiling() && !ctx.world().dimensionType().hasFixedTime();
    }

    private void startPathing(State next) {
        this.state = next;
        this.ticksInState = 0;
        this.lastPathingPosition = null;
    }

    private void notePathingProgress() {
        BetterBlockPos feet = ctx.playerFeet();
        if (!feet.equals(this.lastPathingPosition)) {
            this.lastPathingPosition = feet;
            this.ticksInState = 0;
        }
    }

    private void releaseRightClick() {
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
    }

    /**
     * Somewhere to stand next to the bed. Deliberately not {@code GoalBlock(head)}: that asks to
     * stand <i>inside</i> the bed, which has collision, so the pathfinder can never arrive and we
     * end up stopping wherever we happen to be when something else decides we're close enough.
     * Either half will do, so a bed with only one open side is still approachable.
     */
    private Goal bedGoal(Bed bed) {
        return new GoalComposite(new GoalGetToBlock(bed.head), new GoalGetToBlock(bed.foot));
    }

    /**
     * Finds an interactable half of a bed. From a position alongside the foot, the foot can
     * naturally occlude the head from the ray trace, so requiring the head alone makes a nearby
     * bed look unreachable even though it can be slept in.
     */
    private Optional<Rotation> reachableBed(Bed bed) {
        double reach = ctx.playerController().getBlockReachDistance();
        Optional<Rotation> head = RotationUtils.reachable(ctx, bed.head, reach);
        return head.isPresent() ? head : RotationUtils.reachable(ctx, bed.foot, reach);
    }

    /**
     * Whether we are inside the box the game itself requires for sleeping.
     * <p>
     * This has to be checked separately from {@link #reachableBed}, and cannot be replaced by it.
     * Block reach is a ray trace of about four and a half blocks; the sleep check is an
     * axis-aligned box around the bottom centre of the bed, three blocks horizontally and two
     * vertically, satisfied by either half. Standing four blocks out along an axis passes the ray
     * trace and fails the box, and the server answers that with {@code TOO_FAR_AWAY} however many
     * times we click.
     *
     * @see net.minecraft.server.level.ServerPlayer#startSleepInBed(BlockPos)
     */
    private boolean withinSleepRange(Bed bed) {
        return withinSleepRange(bed.head) || withinSleepRange(bed.foot);
    }

    private boolean withinSleepRange(BetterBlockPos half) {
        Vec3 centre = Vec3.atBottomCenterOf(half);
        return Math.abs(ctx.player().getX() - centre.x) <= 3.0
                && Math.abs(ctx.player().getY() - centre.y) <= 2.0
                && Math.abs(ctx.player().getZ() - centre.z) <= 3.0;
    }

    /**
     * Says what we're doing, or what's stopping us, once per change rather than once per tick.
     */
    private void note(String what) {
        if (!what.equals(this.lastNote)) {
            this.lastNote = what;
            logDebug("shelter: " + what);
        }
    }
}
