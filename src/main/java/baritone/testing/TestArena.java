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
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The working surface handed to a scenario: a patch of world it owns outright, a queue for the
 * vanilla commands that shape it, and somewhere to record what it saw.
 * <p>
 * Every scenario gets its own arena, offset from the last, so that nothing one scenario leaves
 * behind can reach the next. Coordinates a scenario deals in are arena-relative and translated
 * here; a scenario never writes an absolute coordinate, which is what lets the same scenario run
 * at a different origin without changing.
 * <p>
 * Staging goes through {@link #command} rather than touching the level directly. In singleplayer
 * the client can reach the integrated server's level object, and writing to it would be quicker --
 * but it would also mean the arena exists on a path no player could take, which is the one thing
 * this fork's container code is careful never to do. Vanilla commands keep staging honest and keep
 * the harness working the same way whether the world is local or remote.
 */
public final class TestArena {

    /**
     * Distance between consecutive scenario arenas. Wide enough that Baritone's chunk caching,
     * render distance and any dropped items from one scenario are irrelevant to the next.
     */
    public static final int ARENA_SPACING = 512;

    /**
     * How many arena positions exist before they start being reused.
     * <p>
     * Without a bound, a run of several hundred scenarios walks several hundred times
     * {@link #ARENA_SPACING} blocks out and generates chunks the whole way: a 22-scenario world was
     * already 92MB. Reuse is safe because {@link #wipe} clears the whole footprint before each
     * scenario stages, and eight is far enough apart that nothing from eight scenarios ago is still
     * relevant.
     */
    public static final int ARENA_SLOTS = 8;

    /**
     * The volume a scenario may use, as offsets from the origin. Bigger than any single scenario
     * needs, because {@link #wipe} has to erase whatever the <i>previous</i> occupant of this slot
     * built, not just what this one is about to.
     */
    private static final int WIPE_MIN_X = -8;
    private static final int WIPE_MAX_X = 52;
    private static final int WIPE_MIN_Y = -8;
    private static final int WIPE_MAX_Y = 20;
    private static final int WIPE_MIN_Z = -20;
    private static final int WIPE_MAX_Z = 20;

    /**
     * X-slices per wipe command. {@code /fill} refuses more than 32768 blocks at once, and the full
     * footprint is about 72000.
     */
    private static final int WIPE_SLICE = 20;

    private final Baritone baritone;
    private final IPlayerContext ctx;
    private final BetterBlockPos origin;
    private final Deque<String> pendingCommands = new ArrayDeque<>();
    private final List<String> notes = new ArrayList<>();

    TestArena(Baritone baritone, BetterBlockPos origin) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
        this.origin = origin;
    }

    public Baritone baritone() {
        return this.baritone;
    }

    public IPlayerContext ctx() {
        return this.ctx;
    }

    /**
     * @return The arena's reference point: a standable air block, with the ground directly below it.
     */
    public BetterBlockPos origin() {
        return this.origin;
    }

    /**
     * @return The absolute position of an arena-relative offset. {@code dy == 0} is standing level.
     */
    public BetterBlockPos at(int dx, int dy, int dz) {
        return new BetterBlockPos(this.origin.x + dx, this.origin.y + dy, this.origin.z + dz);
    }

    public BlockState stateAt(int dx, int dy, int dz) {
        return this.ctx.world().getBlockState(at(dx, dy, dz));
    }

    /**
     * Queues a command to be sent as the player. No leading slash.
     * <p>
     * Queued rather than sent, because the harness drips them out over several ticks. A burst of
     * staging commands sent in one tick is indistinguishable from chat spam to a server, and being
     * kicked mid-staging is a confusing way for a test run to end.
     */
    public void command(String command) {
        this.pendingCommands.add(command);
    }

    /**
     * Erases everything in the arena footprint, so a scenario never inherits the leavings of
     * whichever scenario last used this slot.
     * <p>
     * Queued as several commands because the footprint exceeds what one {@code /fill} will take.
     * Scenarios lay their own floors afterwards, so this leaves bare air on purpose.
     */
    public void wipe() {
        for (int x = WIPE_MIN_X; x <= WIPE_MAX_X; x += WIPE_SLICE) {
            int to = Math.min(x + WIPE_SLICE - 1, WIPE_MAX_X);
            fill(x, WIPE_MIN_Y, WIPE_MIN_Z, to, WIPE_MAX_Y, WIPE_MAX_Z, "minecraft:air");
        }
    }

    /** {@code /fill} over an arena-relative box. */
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        BetterBlockPos a = at(x1, y1, z1);
        BetterBlockPos b = at(x2, y2, z2);
        command(String.format("fill %d %d %d %d %d %d %s", a.x, a.y, a.z, b.x, b.y, b.z, block));
    }

    /** {@code /setblock} at an arena-relative position. */
    public void setBlock(int dx, int dy, int dz, String block) {
        BetterBlockPos pos = at(dx, dy, dz);
        command(String.format("setblock %d %d %d %s", pos.x, pos.y, pos.z, block));
    }

    /** Teleports the player to an arena-relative position, facing along +x. */
    public void teleport(int dx, int dy, int dz) {
        BetterBlockPos pos = at(dx, dy, dz);
        command(String.format("tp @s %d.5 %d %d.5 -90 0", pos.x, pos.y, pos.z));
    }

    /**
     * Records something worth seeing in the report whether or not the scenario passes. Timings,
     * counts, which process ended up in control -- the context that turns a verdict into a finding.
     */
    public void note(String note) {
        this.notes.add(note);
    }

    public void note(String format, Object... args) {
        this.notes.add(String.format(format, args));
    }

    List<String> notes() {
        return this.notes;
    }

    boolean hasPendingCommands() {
        return !this.pendingCommands.isEmpty();
    }

    String nextCommand() {
        return this.pendingCommands.poll();
    }

    void clearPendingCommands() {
        this.pendingCommands.clear();
    }
}
