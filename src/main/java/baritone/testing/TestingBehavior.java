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

package baritone.testing;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.cache.IRestockBox;
import baritone.api.cache.IWorldData;
import baritone.api.event.events.PacketEvent;
import baritone.api.event.events.PathEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.behavior.Behavior;
import baritone.testing.scenario.DirectionalBuildScenario;
import baritone.testing.scenario.FuzzPathingScenario;
import baritone.testing.scenario.LogsAxesScenario;
import baritone.testing.scenario.ObserverBuildScenario;
import baritone.testing.scenario.PathingCourseScenario;
import baritone.testing.scenario.PistonObserverPairScenario;
import baritone.testing.scenario.RestockFromBoxScenario;
import baritone.testing.scenario.SchematicBuildScenario;
import baritone.testing.scenario.ShelterRetreatDistanceScenario;
import baritone.testing.scenario.StairsHalvesScenario;
import baritone.testing.scenario.UnreachableBuildTargetScenario;
import baritone.testing.scenario.WaterDetourScenario;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Runs test scenarios against a live world and writes up what happened.
 * <p>
 * A behavior rather than a process for the same reason {@code ThreatBehavior} is one, only more so:
 * this thing supervises processes. Registering it with {@code ProcessScheduler} would put it in
 * competition with the very code it is trying to observe, and the first scenario to hand control to
 * the builder would be the last tick the harness ever ran.
 * <p>
 * <b>Scope.</b> Everything here is inert unless someone types {@code #testing} or leaves an autorun
 * flag file in place, and every scenario refuses to start outside singleplayer. The harness rewrites
 * terrain, wipes the inventory and changes gamemode; running it in a world you care about would be
 * a bad afternoon.
 */
public final class TestingBehavior extends Behavior implements Helper {

    /**
     * Staging commands sent per tick. A whole arena arrives as one burst of {@code /fill}s
     * otherwise, and a burst of chat packets is what spam kicks are for.
     */
    private static final int COMMANDS_PER_TICK = 4;

    /** How long to wait for a staged arena to appear on the client before giving up on it. */
    private static final int STAGING_TIMEOUT_TICKS = 20 * 30;

    /** Pause between phases, for gamemode changes and block updates to land. */
    private static final int SETTLE_TICKS = 20;

    /**
     * Blocks of clearance an arena needs below its origin. The deepest thing any scenario digs is
     * the pathing course's trench, at four; the rest is margin against a flat preset that puts the
     * surface somewhere else.
     */
    private static final int ARENA_FLOOR_MARGIN = 8;

    /** Ticks to stay in a freshly joined world before an autorun starts. */
    private static final int AUTORUN_JOIN_DELAY = 20 * 10;

    /** Presence of this file in {@code baritone/testing/} means: run the suite, then quit. */
    public static final String AUTORUN_FLAG = "autorun.flag";

    /**
     * How many seeded fuzz courses exist. They are registered so they can be run by name or named in
     * a shard file, but deliberately kept out of {@code #testing all}: someone typing that wants the
     * curated suite, not four hours of generated courses.
     */
    private static final int FUZZ_COUNT = 240;

    private static final Map<String, Supplier<TestScenario>> SCENARIOS = new LinkedHashMap<>();

    /** The scenarios {@code #testing all} and a bare autorun run. */
    private static final List<String> CURATED = new ArrayList<>();

    static {
        register(PathingCourseScenario::new);
        register(WaterDetourScenario::new);
        // Two stronger variants, so a single run distinguishes "this value is too low" from
        // "this setting cannot decide the route".
        register(() -> new WaterDetourScenario(8.0));
        register(() -> new WaterDetourScenario(30.0));
        register(SchematicBuildScenario::new);
        register(DirectionalBuildScenario::new);
        register(StairsHalvesScenario::new);
        register(LogsAxesScenario::new);
        register(ObserverBuildScenario::new);
        register(RestockFromBoxScenario::new);
        // Uncurated because it cannot yet fail for the right reason: its facing is horizontal and
        // its target stands on a floor, so Baritone can satisfy either orientation rule and a pass
        // proves nothing about them. Promote it once it has a vertical pair.
        registerUncurated(PistonObserverPairScenario::new);
        // These deliberately reproduce open defects and must not make #testing all fail until the
        // corresponding behavior fixes land.
        registerUncurated(ShelterRetreatDistanceScenario::new);
        registerUncurated(UnreachableBuildTargetScenario::new);
        for (int seed = 1; seed <= FUZZ_COUNT; seed++) {
            final int captured = seed;
            registerUncurated(() -> new FuzzPathingScenario(captured));
        }
    }

    private static void register(Supplier<TestScenario> supplier) {
        CURATED.add(registerUncurated(supplier));
    }

    private static String registerUncurated(Supplier<TestScenario> supplier) {
        String name = supplier.get().name();
        SCENARIOS.put(name, supplier);
        return name;
    }

    private enum State {
        IDLE,
        /** Sending the commands that shape the arena. */
        STAGING,
        /** Commands sent; waiting for the client to actually see the result. */
        AWAIT_STAGING,
        /** Switching to survival and letting it take effect. */
        ARMING,
        /** The scenario is running. */
        RUNNING,
        /** Unwinding one scenario before the next. */
        TEARDOWN,
        /** All done; possibly on the way out of the game. */
        SHUTDOWN
    }

    /** Enough rejections to diagnose the problem, few enough not to bury the report. */
    private static final int MAX_RECORDED_REJECTIONS = 8;

    /** A build that makes no observable progress for this long is a useful failure, not a timeout. */
    private static final int STALL_TIMEOUT_TICKS = 20 * 30;

    private State state = State.IDLE;
    private final List<String> rejectedCommands = new ArrayList<>();
    private final List<String> queue = new ArrayList<>();
    private final List<ScenarioResult> results = new ArrayList<>();
    private final Map<String, Object> savedSettings = new HashMap<>();

    private TestScenario scenario;
    private TestArena arena;
    private BetterBlockPos suiteOrigin;
    private int scenarioIndex;
    private int phaseTicks;
    private int runTicks;
    private int ticksInWorld;
    /** Last client-tick event handled by this behavior; guards against duplicate dispatch. */
    private int lastHandledTick = Integer.MIN_VALUE;
    private String lastProgressMarker;
    private int progressMarkerSince;
    private boolean autoQuit;
    private int shutdownTicks;
    private int rejectionMark;
    private boolean autorunChecked;
    private Path lastReport;

    public TestingBehavior(Baritone baritone) {
        super(baritone);
    }

    /** Every registered scenario, curated and generated alike. */
    public static List<String> scenarioNames() {
        return new ArrayList<>(SCENARIOS.keySet());
    }

    /** Just the hand-written suite: what {@code all} means. */
    public static List<String> curatedNames() {
        return new ArrayList<>(CURATED);
    }

    public static int fuzzCount() {
        return FUZZ_COUNT;
    }

    public static String describe(String name) {
        Supplier<TestScenario> supplier = SCENARIOS.get(name);
        return supplier == null ? null : supplier.get().description();
    }

    public boolean isRunning() {
        return this.state != State.IDLE;
    }

    @Override
    public void onPathEvent(PathEvent event) {
        if (this.state == State.RUNNING && this.scenario != null) {
            this.scenario.onPathEvent(event);
        }
    }

    public String currentScenarioName() {
        return this.scenario == null ? "none" : this.scenario.name();
    }

    /**
     * @param names Scenarios to run, in order
     * @return An error to show the user, or {@code null} if the suite started
     */
    public String start(List<String> names, boolean quitWhenDone) {
        if (this.state != State.IDLE) {
            return "already running " + currentScenarioName();
        }
        if (ctx.player() == null) {
            return "not in a world";
        }
        if (!ctx.minecraft().hasSingleplayerServer()) {
            // The harness assumes it can rewrite terrain and change gamemode at will. On a server
            // it would either be refused permission or, worse, not be.
            return "the harness only runs in singleplayer, with cheats enabled";
        }
        for (String name : names) {
            if (!SCENARIOS.containsKey(name)) {
                return "no scenario named '" + name + "'";
            }
        }
        this.queue.clear();
        this.queue.addAll(names);
        this.results.clear();
        this.rejectedCommands.clear();
        this.scenarioIndex = 0;
        this.autoQuit = quitWhenDone;
        this.suiteOrigin = chooseSuiteOrigin();
        this.lastReport = null;

        logDirect(String.format("Testing: %d scenario(s) from %s", names.size(), this.suiteOrigin));
        prepareWorld();
        beginScenario();
        return null;
    }

    public void cancel() {
        if (this.state == State.IDLE) {
            return;
        }
        logDirect("Testing: cancelled");
        if (this.scenario != null) {
            finishScenario(ScenarioResult.Status.SKIPPED, "cancelled by the user");
        }
        this.queue.clear();
        endSuite();
    }

    @Override
    public void onTick(TickEvent event) {
        // The harness measures its budgets in client ticks. Keep accounting idempotent should a
        // caller ever dispatch the same PRE event twice.
        if (event.getState() != EventState.PRE || event.getCount() == this.lastHandledTick) {
            return;
        }
        this.lastHandledTick = event.getCount();
        // Before every other check, including the player-null guard below. Shutting down means
        // leaving the world, which makes the player null and the tick type OUT -- so a shutdown
        // driven from inside those guards stops advancing at exactly the moment it needs to run,
        // and the game sits on the saving screen forever with its work already done.
        if (this.state == State.SHUTDOWN) {
            tickShutdown();
            return;
        }
        if (event.getType() == TickEvent.Type.OUT) {
            // Leaving the world mid-suite invalidates everything after it; there is no arena to
            // come back to and no honest verdict to record.
            if (this.state != State.IDLE && this.state != State.SHUTDOWN) {
                this.state = State.IDLE;
                this.scenario = null;
                this.arena = null;
                restoreSettings();
            }
            this.ticksInWorld = 0;
            this.autorunChecked = false;
            return;
        }
        if (ctx.player() == null) {
            return;
        }
        this.ticksInWorld++;
        this.phaseTicks++;

        switch (this.state) {
            case IDLE:
                maybeAutorun();
                return;
            case STAGING:
                tickStaging();
                return;
            case AWAIT_STAGING:
                tickAwaitStaging();
                return;
            case ARMING:
                tickArming();
                return;
            case RUNNING:
                tickRunning();
                return;
            case TEARDOWN:
                tickTeardown();
                return;
            case SHUTDOWN:
                tickShutdown();
                return;
            default:
        }
    }

    private void maybeAutorun() {
        // Once per world join, not on one exact tick. The tick-equality version missed its chance
        // whenever anything else held the harness busy at that instant -- typing #testing manually
        // in the first ten seconds was enough -- and then sat there armed but inert for the rest of
        // the session. The flag guards against repeating: it is deleted before the suite starts.
        if (this.autorunChecked || this.ticksInWorld < AUTORUN_JOIN_DELAY) {
            return;
        }
        this.autorunChecked = true;
        Path flag = reportDirectory().resolve(AUTORUN_FLAG);
        if (!Files.exists(flag)) {
            return;
        }
        List<String> shard = readShard(flag);
        if (shard.isEmpty()) {
            logDirect("Testing: the shard list named nothing this build knows about, not starting");
            return;
        }
        try {
            Files.delete(flag);
        } catch (IOException e) {
            // Deleting it first is what stops a crash mid-suite turning into a boot loop: the run
            // that cannot clear its own flag is the run that must not start.
            logDirect("Testing: could not clear the autorun flag, so not starting: " + e);
            return;
        }
        String error = start(shard, true);
        if (error != null) {
            logDirect("Testing: autorun refused: " + error);
        }
    }

    /**
     * The scenarios an autorun should run, taken from the flag file's contents.
     * <p>
     * One name per line; blank means everything. This is how a run is sharded across parallel
     * instances -- each clone gets a flag file listing its own slice -- and it has to be read before
     * the file is deleted, which is why it is not simply {@code scenarioNames()} at the call site.
     * <p>
     * Unknown names are dropped with a warning rather than failing the run. A shard file is written
     * by tooling against a possibly older jar, and losing one scenario is better than losing the
     * whole instance's contribution to a several-hundred-test sweep.
     */
    private List<String> readShard(Path flag) {
        List<String> requested = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(flag)) {
                String name = line.trim();
                if (!name.isEmpty() && !name.startsWith("#")) {
                    requested.add(name);
                }
            }
        } catch (IOException e) {
            logDirect("Testing: could not read the shard list, running the curated suite: " + e);
            return curatedNames();
        }
        if (requested.isEmpty()) {
            return curatedNames();
        }
        List<String> known = new ArrayList<>();
        for (String name : requested) {
            if (SCENARIOS.containsKey(name)) {
                known.add(name);
            } else {
                logDirect("Testing: shard names an unknown scenario, skipping it: " + name);
            }
        }
        return known;
    }

    private void prepareWorld() {
        // A deterministic world, once, for the whole suite. Weather, mobs and time of day all
        // change what Baritone does, and none of them are what these scenarios are measuring.
        //
        // These ids are 26.1.2's, and they are not the ones anyone has memorised: the rules were
        // renamed and moved to net.minecraft.world.level.gamerules, and doDaylightCycle became
        // advance_time rather than merely changing case. The first two suite runs sent the old
        // camelCase names, every one was rejected, and nothing noticed -- see checkForCommandError.
        sendNow("gamerule advance_time false");
        sendNow("gamerule advance_weather false");
        sendNow("gamerule spawn_mobs false");
        sendNow("gamerule random_tick_speed 0");
        sendNow("gamerule keep_inventory true");
        sendNow("gamerule send_command_feedback false");
        sendNow("time set noon");
        sendNow("weather clear");
        sendNow("difficulty peaceful");
        sendNow("kill @e[type=!player,distance=..128]");
    }

    private void beginScenario() {
        if (this.scenarioIndex >= this.queue.size()) {
            endSuite();
            return;
        }
        String name = this.queue.get(this.scenarioIndex);
        this.scenario = SCENARIOS.get(name).get();
        this.arena = new TestArena(this.baritone, new BetterBlockPos(
                this.suiteOrigin.x
                        + (this.scenarioIndex % TestArena.ARENA_SLOTS) * TestArena.ARENA_SPACING,
                this.suiteOrigin.y,
                this.suiteOrigin.z
        ));
        this.runTicks = 0;
        this.phaseTicks = 0;
        this.lastProgressMarker = null;
        this.progressMarkerSince = 0;
        this.rejectionMark = this.rejectedCommands.size();
        this.state = State.STAGING;

        clearRestockBoxRegistrations();

        logDirect(String.format("Testing [%d/%d]: %s -- %s",
                this.scenarioIndex + 1, this.queue.size(), name, this.scenario.description()));

        // Creative for staging only. The scenarios themselves run in survival, which is the point:
        // reach, block hardness, hunger and fall damage are all part of what is under test.
        sendNow("gamemode creative");
        // Sent immediately rather than queued, because nothing else can be sent until it has taken
        // effect: /fill refuses to touch an unloaded chunk, and each arena is far enough from the
        // last that nothing there is loaded until the player is standing in it.
        sendNow(String.format("tp @s %d.5 %d %d.5 -90 0",
                this.arena.origin().x, this.arena.origin().y, this.arena.origin().z));
        try {
            // Before the scenario stages anything: arena slots are reused, so whatever the last
            // occupant left is still standing until this clears it.
            this.arena.wipe();
            this.scenario.stage(this.arena);
        } catch (RuntimeException e) {
            finishScenario(ScenarioResult.Status.ERROR, "staging threw: " + e);
        }
    }

    private void tickStaging() {
        if (!arenaChunkLoaded()) {
            if (this.phaseTicks > STAGING_TIMEOUT_TICKS) {
                finishScenario(ScenarioResult.Status.STAGING_FAILED,
                        "the arena's chunks never loaded, so nothing could be staged there; the "
                                + "teleport probably failed, which means cheats are off");
            }
            return;
        }
        for (int i = 0; i < COMMANDS_PER_TICK && this.arena.hasPendingCommands(); i++) {
            sendNow(this.arena.nextCommand());
        }
        if (!this.arena.hasPendingCommands()) {
            this.state = State.AWAIT_STAGING;
            this.phaseTicks = 0;
        }
    }

    private void tickAwaitStaging() {
        // Checked before stagingComplete, because a rejected command can still leave the arena
        // looking right -- the world-setup gamerules are the case in point -- and "the blocks are
        // there" is not the same as "everything we asked for happened".
        List<String> rejections = rejectionsThisScenario();
        if (!rejections.isEmpty()) {
            rejections.forEach(rejection -> this.arena.note("rejected: %s", rejection));
            finishScenario(ScenarioResult.Status.STAGING_FAILED, String.format(
                    "the server rejected %d staging command(s), so the arena is not what the "
                            + "scenario asked for; first was: %s",
                    rejections.size(), rejections.get(0)
            ));
            return;
        }
        if (this.scenario.stagingComplete(this.arena)) {
            this.state = State.ARMING;
            this.phaseTicks = 0;
            sendNow("gamemode survival");
            applySettings(this.scenario.settings());
            return;
        }
        if (this.phaseTicks > STAGING_TIMEOUT_TICKS) {
            finishScenario(ScenarioResult.Status.STAGING_FAILED,
                    "the arena never appeared on the client; check the staging commands against this "
                            + "Minecraft version, and that cheats are enabled");
        }
    }

    private void tickArming() {
        if (this.phaseTicks < SETTLE_TICKS) {
            return;
        }
        if (ctx.playerController().getGameType() != GameType.SURVIVAL) {
            if (this.phaseTicks > SETTLE_TICKS * 4) {
                finishScenario(ScenarioResult.Status.STAGING_FAILED,
                        "could not switch to survival; are cheats enabled?");
            }
            return;
        }
        this.state = State.RUNNING;
        this.runTicks = 0;
        try {
            this.scenario.start(this.arena);
        } catch (RuntimeException e) {
            finishScenario(ScenarioResult.Status.ERROR, "start threw: " + e);
        }
    }

    private void tickRunning() {
        this.runTicks++;
        TestScenario.Verdict verdict;
        try {
            verdict = this.scenario.poll(this.arena, this.runTicks);
        } catch (RuntimeException e) {
            finishScenario(ScenarioResult.Status.ERROR, "poll threw: " + e);
            return;
        }
        if (verdict != null) {
            finishScenario(verdict.pass ? ScenarioResult.Status.PASS : ScenarioResult.Status.FAIL,
                    verdict.message);
            return;
        }
        String progress = this.scenario.progressMarker(this.arena);
        if (progress != null) {
            if (!progress.equals(this.lastProgressMarker)) {
                this.lastProgressMarker = progress;
                this.progressMarkerSince = this.runTicks;
            } else if (this.runTicks - this.progressMarkerSince >= STALL_TIMEOUT_TICKS) {
                String diagnosis;
                try {
                    // Keep the scenario's block-by-block detail: it is what turns "stalled"
                    // into an actionable statement of the missing position and wanted state.
                    diagnosis = this.scenario.timeoutDiagnosis(this.arena);
                } catch (RuntimeException e) {
                    diagnosis = "diagnosis threw: " + e;
                }
                finishScenario(ScenarioResult.Status.FAIL, String.format(
                        "stalled at t=%.1fs after %.1fs with no progress: %s",
                        this.progressMarkerSince / 20.0,
                        (this.runTicks - this.progressMarkerSince) / 20.0,
                        diagnosis
                ));
                return;
            }
        }
        if (this.runTicks >= this.scenario.tickBudget()) {
            String diagnosis;
            try {
                diagnosis = this.scenario.timeoutDiagnosis(this.arena);
            } catch (RuntimeException e) {
                diagnosis = "diagnosis threw: " + e;
            }
            finishScenario(ScenarioResult.Status.TIMEOUT, String.format(
                    "ran out of its %d tick budget: %s", this.scenario.tickBudget(), diagnosis));
        }
    }

    private void finishScenario(ScenarioResult.Status status, String message) {
        this.baritone.getPathingBehavior().cancelEverything();
        if (this.arena != null) {
            this.arena.clearPendingCommands();
        }
        try {
            if (this.scenario != null && this.arena != null) {
                this.scenario.teardown(this.arena);
            }
        } catch (RuntimeException e) {
            this.arena.note("teardown threw: " + e);
        }
        clearRestockBoxRegistrations();
        restoreSettings();

        List<String> notes = this.arena == null ? new ArrayList<>() : this.arena.notes();
        this.results.add(new ScenarioResult(
                this.scenario == null ? "unknown" : this.scenario.name(),
                status,
                message,
                this.runTicks,
                this.scenario == null ? 0 : this.scenario.tickBudget(),
                notes
        ));
        logDirect(String.format("Testing: %s %s -- %s",
                this.scenario == null ? "?" : this.scenario.name(), status, message));

        // Rewritten after every scenario, not just at the end. A shard of sixty scenarios is twenty
        // minutes of game time, and a crash at scenario fifty-nine should not throw away the other
        // fifty-eight -- nor leave the parallel runner waiting out its timeout for a report that is
        // never coming.
        writeReportQuietly();

        this.scenario = null;
        this.scenarioIndex++;
        this.state = State.TEARDOWN;
        this.phaseTicks = 0;
    }

    /**
     * Registered boxes persist in world data, unlike the command-staged arena. Clear them on both
     * sides of every scenario so a restock fixture cannot silently become input to another test.
     */
    private void clearRestockBoxRegistrations() {
        IWorldData world = this.baritone.getWorldProvider().getCurrentWorld();
        if (world == null) {
            return;
        }
        for (IRestockBox box : new ArrayList<>(world.getRestockBoxes().getAllBoxes())) {
            world.getRestockBoxes().removeBox(box.getLocation());
        }
    }

    private void tickTeardown() {
        if (this.phaseTicks < SETTLE_TICKS) {
            return;
        }
        beginScenario();
    }

    private void endSuite() {
        this.scenario = null;
        this.arena = null;
        restoreSettings();
        sendNow("gamemode creative");

        if (!this.results.isEmpty() && writeReportQuietly(true)) {
            logDirect("Testing: report written to " + this.lastReport);
        }
        long failures = this.results.stream().filter(ScenarioResult::countsAsFailure).count();
        logDirect(String.format("Testing: finished, %d of %d passed",
                this.results.size() - failures, this.results.size()));

        if (this.autoQuit) {
            this.state = State.SHUTDOWN;
            this.shutdownTicks = 0;
            logDirect("Testing: autorun complete, leaving the world");
        } else {
            this.state = State.IDLE;
        }
    }

    private void tickShutdown() {
        this.shutdownTicks++;
        // Never let a failed disconnect strand the game on a screen it will never leave. Quitting
        // without the world unloaded is worse than quitting cleanly, but it is better than hanging.
        if (this.shutdownTicks > SETTLE_TICKS * 20) {
            logDirect("Testing: the world did not unload; quitting anyway");
            this.state = State.IDLE;
            ctx.minecraft().stop();
            return;
        }
        // Save and leave first, then quit. Calling stop() straight from a loaded world skips the
        // save, and the next run would stage its arenas onto a world that had rolled back.
        if (this.shutdownTicks == SETTLE_TICKS) {
            ctx.minecraft().disconnectWithSavingScreen();
        } else if (this.shutdownTicks > SETTLE_TICKS && ctx.minecraft().level == null) {
            this.state = State.IDLE;
            ctx.minecraft().stop();
        }
    }

    /**
     * Where to put the first arena.
     * <p>
     * Scenarios dig below their own floor -- the pathing course's trench bottoms out four blocks
     * down -- and on a default superflat the player is standing close enough to the world floor
     * that those excavations land on or below it. A {@code /fill} that reaches outside the build
     * height is refused, which would surface as a staging timeout blaming the wrong thing.
     * <p>
     * Lifting the origin is safe where lowering it would not be: every arena lays its own floor, and
     * the player teleported to a raised origin simply falls to the natural ground and is teleported
     * back up once that floor exists.
     */
    private BetterBlockPos chooseSuiteOrigin() {
        BetterBlockPos feet = ctx.playerFeet();
        return new BetterBlockPos(feet.x, Math.max(feet.y, ctx.world().getMinY() + ARENA_FLOOR_MARGIN), feet.z);
    }

    /**
     * Whether the player has landed in the arena and the whole staging area has reached the client.
     * <p>
     * The client having a chunk means the server sent it, which means the server has it loaded --
     * which is the thing {@code /fill} actually cares about. Checking it directly on the server
     * would be more honest but is only possible in singleplayer, and this is already the tighter
     * constraint of the two.
     */
    private boolean arenaChunkLoaded() {
        BetterBlockPos origin = this.arena.origin();
        if (origin.distSqr(ctx.playerFeet()) > 64 * 64) {
            return false;
        }
        for (int dx = -16; dx <= 64; dx += 16) {
            for (int dz = -16; dz <= 16; dz += 16) {
                if (!ctx.world().getChunkSource().hasChunk((origin.x + dx) >> 4, (origin.z + dz) >> 4)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Phrases that mean the server rejected a command outright.
     * <p>
     * Deliberately only the parse failures. {@code /fill} reports "No blocks were filled" when it
     * matched nothing to change, which happens constantly and harmlessly while clearing an arena
     * that is already clear -- treating every red message as fatal would fail every run.
     * <p>
     * Matching English text is not something to be proud of, but the alternative is inspecting
     * translation keys of brigadier exceptions that are assembled client-side, and a missed error
     * costs a whole game run. The offending message is recorded verbatim either way, so a
     * non-English client still shows something usable in the report.
     */
    private static final String[] REJECTION_PHRASES = {
            "Incorrect argument for command",
            "Unknown or incomplete command",
            "Unknown command",
    };

    /**
     * Notices commands the server threw out.
     * <p>
     * This exists because the first two suite runs sent six gamerule commands with pre-26.1.2 names,
     * every one was rejected, and the harness reported four scenarios' worth of results as though
     * the world had been configured. Staging that silently does nothing is worse than staging that
     * fails, because it produces confident numbers about the wrong world.
     */
    @Override
    public void onReceivePacket(PacketEvent event) {
        if (event.getState() != EventState.POST || this.state == State.IDLE) {
            return;
        }
        if (!(event.getPacket() instanceof ClientboundSystemChatPacket)) {
            return;
        }
        ClientboundSystemChatPacket packet = event.cast();
        String text = packet.content().getString();
        for (String phrase : REJECTION_PHRASES) {
            if (text.contains(phrase)) {
                if (this.rejectedCommands.size() < MAX_RECORDED_REJECTIONS) {
                    this.rejectedCommands.add(text);
                }
                return;
            }
        }
    }

    /** Rejections that arrived since the current scenario began staging. */
    private List<String> rejectionsThisScenario() {
        if (this.rejectedCommands.size() <= this.rejectionMark) {
            return new ArrayList<>();
        }
        return new ArrayList<>(this.rejectedCommands.subList(this.rejectionMark, this.rejectedCommands.size()));
    }

    private void sendNow(String command) {
        if (ctx.player() != null) {
            ctx.player().connection.sendCommand(command);
        }
    }

    @SuppressWarnings("unchecked")
    private void applySettings(Map<String, Object> settings) {
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            Settings.Setting<?> setting = Baritone.settings().byLowerName.get(entry.getKey().toLowerCase());
            if (setting == null) {
                this.arena.note("no setting named '%s'; scenario may not be testing what it thinks", entry.getKey());
                continue;
            }
            this.savedSettings.put(entry.getKey().toLowerCase(), setting.value);
            ((Settings.Setting<Object>) setting).value = entry.getValue();
        }
        if (!settings.isEmpty()) {
            this.arena.note("settings for this scenario: %s", settings);
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreSettings() {
        for (Map.Entry<String, Object> entry : this.savedSettings.entrySet()) {
            Settings.Setting<?> setting = Baritone.settings().byLowerName.get(entry.getKey());
            if (setting != null) {
                ((Settings.Setting<Object>) setting).value = entry.getValue();
            }
        }
        this.savedSettings.clear();
    }

    /**
     * Writes the report as it stands. Silent on the happy path, because this runs after every
     * scenario and a line of chat each time would drown the results it is recording.
     *
     * @return {@code true} if it was written
     */
    private boolean writeReportQuietly() {
        return writeReportQuietly(false);
    }

    private boolean writeReportQuietly(boolean complete) {
        if (this.results.isEmpty()) {
            return false;
        }
        try {
            this.lastReport = TestReport.write(reportDirectory(), this.results, this.suiteOrigin, complete);
            return true;
        } catch (IOException e) {
            logDirect("Testing: could not write the report: " + e);
            return false;
        }
    }

    public Path reportDirectory() {
        return this.baritone.getDirectory().resolve("testing");
    }

    public Path lastReport() {
        return this.lastReport;
    }
}
