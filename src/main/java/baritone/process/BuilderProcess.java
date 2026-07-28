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
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.IBuilderProcess;
import baritone.api.process.IRestockProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.schematic.*;
import baritone.api.schematic.format.ISchematicFormat;
import baritone.api.utils.*;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.api.utils.interfaces.IGoalRenderPos;
import baritone.pathing.movement.CalculationContext;
import baritone.api.pathing.movement.IMovement;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.pathing.path.PathExecutor;
import baritone.utils.BlockStateInterface;
import baritone.utils.PathingCommandContext;
import baritone.utils.schematic.MapArtSchematic;
import baritone.utils.schematic.SchematicSystem;
import baritone.utils.schematic.SelectionSchematic;
import baritone.utils.schematic.litematica.LitematicaHelper;
import baritone.utils.schematic.schematica.SchematicaHelper;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static baritone.api.pathing.movement.ActionCosts.COST_INF;

public final class BuilderProcess extends BaritoneProcessHelper implements IBuilderProcess {

    /**
     * Schematics bigger than this are not scanned for their block palette; the scan is one pass over
     * every position, which is fine for a house and silly for a city.
     */
    private static final long MAX_PALETTE_SCAN_VOLUME = 8_000_000L;

    /**
     * Properties the player picks when placing a block, by where they stand and which face they
     * click. Ignored only when {@code buildIgnoreDirection} is on.
     */
    private static final Set<Property<?>> ORIENTATION_PROPS =
            ImmutableSet.of(
                    RotatedPillarBlock.AXIS, HorizontalDirectionalBlock.FACING,
                    StairBlock.FACING, StairBlock.HALF,
                    TrapDoorBlock.OPEN, TrapDoorBlock.HALF
            );

    /**
     * The per-direction booleans a fence or a pane works out from what it's touching. Shared with
     * blocks that do let you pick a face, glow lichen and the mushroom blocks, so which block we're
     * looking at decides whether they're derived -- see {@link #derivedProperty}.
     */
    private static final Set<Property<?>> CONNECTION_PROPS =
            ImmutableSet.of(
                    PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN
            );

    /**
     * Whether vanilla works this property out from the neighbours (see {@code Block#updateShape})
     * rather than it being something we can choose when placing.
     * <p>
     * Derived properties are <i>always</i> ignored when comparing states. Requiring them to match
     * means a corner stair or a fence can never be "correct" the instant it's placed, so the builder
     * breaks the block it just placed, places it again, and loops there forever.
     */
    private static boolean derivedProperty(Block block, Property<?> prop) {
        if (prop == StairBlock.SHAPE) {
            return true;
        }
        // glow lichen and the mushroom blocks use the same properties but you do choose their faces
        return CONNECTION_PROPS.contains(prop) && (block instanceof CrossCollisionBlock || block instanceof ChorusPlantBlock);
    }

    /**
     * Properties decided by where on the face we click rather than by where we stand, so they're
     * waived while working out which way we need to be facing.
     */
    private static final Set<Property<?>> HIT_VECTOR_PROPS = ImmutableSet.of(StairBlock.HALF, TrapDoorBlock.HALF);

    private static final Direction[] ORIENT_CANDIDATE_FACINGS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /**
     * Standing further back than this puts the block out of reach.
     */
    private static final int ORIENT_MAX_STAND_DISTANCE = 4;

    /**
     * How close we have to be to a block before the give-up timer for its stand position starts.
     */
    private static final int ORIENT_TIMER_RANGE = 16;

    private HashSet<BetterBlockPos> incorrectPositions;
    private LongOpenHashSet observedCompleted; // positions that are completed even if they're out of render distance and we can't make sure right now
    private String name;
    private ISchematic realSchematic;
    private ISchematic schematic;
    private Vec3i origin;
    private int ticks;
    private boolean paused;
    private int layer;
    private int numRepeats;
    private List<BlockState> approxPlaceable;
    public int stopAtHeight = 0;
    /**
     * The materials the last call to {@link #assemble} found it was short of. Carried out of
     * assemble so the restock logic can see what we actually need without recomputing it.
     */
    private Map<BlockState, Integer> missingMaterials = new HashMap<>();
    /**
     * Whether we've already offered the restock process a chance to index boxes for this build.
     * Only tried once, so a failed or declined indexing run doesn't loop.
     */
    private boolean triedIndexingThisBuild;
    /**
     * Every block the schematic asks for anywhere, computed once per build. Null until computed,
     * and left null if the schematic is too big to scan, in which case nothing is treated as junk.
     */
    private Set<Block> schematicPalette;
    private boolean paletteTooLarge;
    /**
     * Whether this build asks for air and nothing else, i.e. it only ever breaks. Recognised from
     * the shape of the schematic rather than by scanning it, because the clearing case is exactly
     * the one where the area is routinely too big to scan.
     */
    private boolean clearingOnly;
    /**
     * The goal we handed to the pathing behaviour last tick, and how many times in a row it has
     * changed. Used to detect the builder dithering between targets instead of committing to one.
     */
    private Goal lastGoal;
    private int consecutiveReroutes;
    /**
     * While positive, we stop re-planning and let the current path run. Counted down per tick.
     */
    private int rerouteCommitTicks;
    /**
     * Our own tick counter, used only for churn bookkeeping.
     */
    private int builderTick;
    /**
     * Per-position record of the builder alternating between breaking and placing.
     */
    private final Map<Long, ChurnRecord> churn = new HashMap<>();
    /**
     * Positions being left alone because they were detected churning, mapped to the tick at which
     * they become eligible again.
     */
    private final Map<Long, Integer> churnBlacklist = new HashMap<>();
    /**
     * When we first started trying to reach a facing-specific stand position for a block, so we can
     * give up on it if the spot turns out to be unreachable instead of stalling the build.
     */
    private final Map<Long, Integer> orientFirstSeen = new HashMap<>();
    /**
     * Which way we have to be facing to place each desired state, worked out by simulation once per
     * state rather than once per position.
     */
    private final Map<BlockState, Set<Direction>> orientationCache = new HashMap<>();

    public BuilderProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void build(String name, ISchematic schematic, Vec3i origin) {
        this.name = name;
        this.schematic = schematic;
        this.realSchematic = null;
        boolean buildingSelectionSchematic = schematic instanceof SelectionSchematic;
        if (!Baritone.settings().buildSubstitutes.value.isEmpty()) {
            this.schematic = new SubstituteSchematic(this.schematic, Baritone.settings().buildSubstitutes.value);
        }
        if (Baritone.settings().buildSchematicMirror.value != net.minecraft.world.level.block.Mirror.NONE) {
            this.schematic = new MirroredSchematic(this.schematic, Baritone.settings().buildSchematicMirror.value);
        }
        if (Baritone.settings().buildSchematicRotation.value != net.minecraft.world.level.block.Rotation.NONE) {
            this.schematic = new RotatedSchematic(this.schematic, Baritone.settings().buildSchematicRotation.value);
        }
        // TODO this preserves the old behavior, but maybe we should bake the setting value right here
        this.schematic = new MaskSchematic(this.schematic) {
            @Override
            public boolean partOfMask(int x, int y, int z, BlockState current) {
                // partOfMask is only called inside the schematic so desiredState is not null
                return !Baritone.settings().buildSkipBlocks.value.contains(this.desiredState(x, y, z, current, Collections.emptyList()).getBlock());
            }
        };
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        if (Baritone.settings().schematicOrientationX.value) {
            x += schematic.widthX();
        }
        if (Baritone.settings().schematicOrientationY.value) {
            y += schematic.heightY();
        }
        if (Baritone.settings().schematicOrientationZ.value) {
            z += schematic.lengthZ();
        }
        this.origin = new Vec3i(x, y, z);
        this.paused = false;
        // a new build gets a clean slate; materials given up on last time may well be available now
        clearRestockGiveUps();
        this.triedIndexingThisBuild = false;
        this.schematicPalette = null;
        this.paletteTooLarge = false;
        // Checked against the schematic as passed in, before the wrappers above: mirroring and
        // rotation can't turn air into a block, and the mask only ever removes positions. A
        // substitution for air could, so that one case bows out.
        this.clearingOnly = isPureAir(schematic)
                && !Baritone.settings().buildSubstitutes.value.containsKey(Blocks.AIR);
        this.lastGoal = null;
        this.consecutiveReroutes = 0;
        this.rerouteCommitTicks = 0;
        this.churn.clear();
        this.churnBlacklist.clear();
        this.orientFirstSeen.clear();
        this.orientationCache.clear();
        this.layer = Baritone.settings().startAtLayer.value;
        this.stopAtHeight = schematic.heightY();
        if (Baritone.settings().buildOnlySelection.value && buildingSelectionSchematic) {  // currently redundant but safer maybe
            if (baritone.getSelectionManager().getSelections().length == 0) {
                logDirect("Poor little kitten forgot to set a selection while BuildOnlySelection is true");
                this.stopAtHeight = 0;
            } else if (Baritone.settings().buildInLayers.value) {
                OptionalInt minim = Stream.of(baritone.getSelectionManager().getSelections()).mapToInt(sel -> sel.min().y).min();
                OptionalInt maxim = Stream.of(baritone.getSelectionManager().getSelections()).mapToInt(sel -> sel.max().y).max();
                if (minim.isPresent() && maxim.isPresent()) {
                    int startAtHeight = Baritone.settings().layerOrder.value ? y + schematic.heightY() - maxim.getAsInt() : minim.getAsInt() - y;
                    this.stopAtHeight = (Baritone.settings().layerOrder.value ? y + schematic.heightY() - minim.getAsInt() : maxim.getAsInt() - y) + 1;
                    this.layer = Math.max(this.layer, startAtHeight / Baritone.settings().layerHeight.value);  // startAtLayer or startAtHeight, whichever is highest
                    logDebug(String.format("Schematic starts at y=%s with height %s", y, schematic.heightY()));
                    logDebug(String.format("Selection starts at y=%s and ends at y=%s", minim.getAsInt(), maxim.getAsInt()));
                    logDebug(String.format("Considering relevant height %s - %s", startAtHeight, this.stopAtHeight));
                }
            }
        }

        this.numRepeats = 0;
        this.observedCompleted = new LongOpenHashSet();
        this.incorrectPositions = null;
    }

    public void resume() {
        paused = false;
    }

    public void pause() {
        paused = true;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public boolean build(String name, File schematic, Vec3i origin) {
        Optional<ISchematicFormat> format = SchematicSystem.INSTANCE.getByFile(schematic);
        if (!format.isPresent()) {
            return false;
        }
        IStaticSchematic parsed;
        try {
            parsed = format.get().parse(new FileInputStream(schematic));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        ISchematic schem = applyMapArtAndSelection(origin, parsed);
        build(name, schem, origin);
        return true;
    }

    private ISchematic applyMapArtAndSelection(Vec3i origin, IStaticSchematic parsed) {
        ISchematic schematic = parsed;
        if (Baritone.settings().mapArtMode.value) {
            schematic = new MapArtSchematic(parsed);
        }
        if (Baritone.settings().buildOnlySelection.value) {
            schematic = new SelectionSchematic(schematic, origin, baritone.getSelectionManager().getSelections());
        }
        return schematic;
    }

    @Override
    public void buildOpenSchematic() {
        if (SchematicaHelper.isSchematicaPresent()) {
            Optional<Tuple<IStaticSchematic, BlockPos>> schematic = SchematicaHelper.getOpenSchematic();
            if (schematic.isPresent()) {
                IStaticSchematic raw = schematic.get().getA();
                BlockPos origin = schematic.get().getB();
                ISchematic schem = applyMapArtAndSelection(origin, raw);
                this.build(raw.toString(), schem, origin);
            } else {
                logDirect("No schematic currently open");
            }
        } else {
            logDirect("Schematica is not present");
        }
    }

    @Override
    public void buildOpenLitematic(int i) {
        if (LitematicaHelper.isLitematicaPresent()) {
            //if java.lang.NoSuchMethodError is thrown see comment in SchematicPlacementManager
            if (LitematicaHelper.hasLoadedSchematic(i)) {
                Tuple<IStaticSchematic, Vec3i> schematic = LitematicaHelper.getSchematic(i);
                Vec3i correctedOrigin = schematic.getB();
                ISchematic schematic2 = applyMapArtAndSelection(correctedOrigin, schematic.getA());
                build(schematic.getA().toString(), schematic2, correctedOrigin);
            } else {
                logDirect(String.format("List of placements has no entry %s", i + 1));
            }
        } else {
            logDirect("Litematica is not present");
        }
    }

    public void clearArea(BlockPos corner1, BlockPos corner2) {
        BlockPos origin = new BlockPos(Math.min(corner1.getX(), corner2.getX()), Math.min(corner1.getY(), corner2.getY()), Math.min(corner1.getZ(), corner2.getZ()));
        int widthX = Math.abs(corner1.getX() - corner2.getX()) + 1;
        int heightY = Math.abs(corner1.getY() - corner2.getY()) + 1;
        int lengthZ = Math.abs(corner1.getZ() - corner2.getZ()) + 1;
        build("clear area", new FillSchematic(widthX, heightY, lengthZ, Blocks.AIR.defaultBlockState()), origin);
    }

    @Override
    public List<BlockState> getApproxPlaceable() {
        return new ArrayList<>(approxPlaceable);
    }

    @Override
    public boolean isActive() {
        return schematic != null;
    }

    public BlockState placeAt(int x, int y, int z, BlockState current) {
        if (!isActive()) {
            return null;
        }
        if (!schematic.inSchematic(x - origin.getX(), y - origin.getY(), z - origin.getZ(), current)) {
            return null;
        }
        BlockState state = schematic.desiredState(x - origin.getX(), y - origin.getY(), z - origin.getZ(), current, this.approxPlaceable);
        if (state.getBlock() instanceof AirBlock) {
            return null;
        }
        return state;
    }

    private Optional<Tuple<BetterBlockPos, Rotation>> toBreakNearPlayer(BuilderCalculationContext bcc) {
        BetterBlockPos center = ctx.playerFeet();
        BetterBlockPos pathStart = baritone.getPathingBehavior().pathStart();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = Baritone.settings().breakFromAbove.value ? -1 : 0; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    int x = center.x + dx;
                    int y = center.y + dy;
                    int z = center.z + dz;
                    if (dy == -1 && x == pathStart.x && z == pathStart.z) {
                        continue; // dont mine what we're supported by, but not directly standing on
                    }
                    BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
                    if (desired == null) {
                        continue; // irrelevant
                    }
                    BlockState curr = bcc.bsi.get0(x, y, z);
                    if (!(curr.getBlock() instanceof AirBlock) && !(curr.getBlock() == Blocks.WATER || curr.getBlock() == Blocks.LAVA) && !valid(curr, desired, false)) {
                        BetterBlockPos pos = new BetterBlockPos(x, y, z);
                        if (isChurnBlacklisted(pos)) {
                            continue; // detected looping on this block; leave it be
                        }
                        Optional<Rotation> rot = RotationUtils.reachable(ctx, pos, ctx.playerController().getBlockReachDistance());
                        if (rot.isPresent()) {
                            return Optional.of(new Tuple<>(pos, rot.get()));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static class Placement {

        private final int hotbarSelection;
        private final BlockPos placeAgainst;
        private final Direction side;
        private final Rotation rot;
        /**
         * What the schematic wants here, kept so the placement can be re-checked against the
         * rotation we actually end up at, not the one we planned for.
         */
        private final BlockState desired;

        public Placement(int hotbarSelection, BlockPos placeAgainst, Direction side, Rotation rot, BlockState desired) {
            this.hotbarSelection = hotbarSelection;
            this.placeAgainst = placeAgainst;
            this.side = side;
            this.rot = rot;
            this.desired = desired;
        }
    }

    private Optional<Placement> searchForPlacables(BuilderCalculationContext bcc, List<BlockState> desirableOnHotbar) {
        BetterBlockPos center = ctx.playerFeet();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 1; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    int x = center.x + dx;
                    int y = center.y + dy;
                    int z = center.z + dz;
                    BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
                    if (desired == null) {
                        continue; // irrelevant
                    }
                    BlockState curr = bcc.bsi.get0(x, y, z);
                    if (MovementHelper.isReplaceable(x, y, z, curr, bcc.bsi) && !valid(curr, desired, false)) {
                        if (dy == 1 && bcc.bsi.get0(x, y + 1, z).getBlock() instanceof AirBlock) {
                            continue;
                        }
                        if (pathNeedsOpen(new BetterBlockPos(x, y, z))) {
                            continue; // we're about to walk through here; don't wall ourselves in
                        }
                        if (isChurnBlacklisted(new BetterBlockPos(x, y, z))) {
                            continue; // detected looping on this block; leave it be
                        }
                        desirableOnHotbar.add(desired);
                        Optional<Placement> opt = possibleToPlace(desired, x, y, z, bcc.bsi);
                        if (opt.isPresent()) {
                            return opt;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Tracks whether a position is being repeatedly broken and re-placed.
     */
    private static final class ChurnRecord {
        boolean lastWasPlace;
        int alternations;
        int lastTick;
    }

    /**
     * Records that we're about to break or place at a position, and reports whether that position
     * is stuck in a break/place loop.
     * <p>
     * Only <i>alternations</i> count. Breaking a block holds the attack input down for many
     * consecutive ticks, so counting raw actions would trip immediately on completely normal
     * mining. A genuine loop looks like place, break, place, break at one position in quick
     * succession.
     *
     * @param pos     The position being acted on
     * @param placing {@code true} if placing, {@code false} if breaking
     * @return {@code true} if this position has just been detected as looping
     */
    private boolean noteBlockAction(BetterBlockPos pos, boolean placing) {
        if (!Baritone.settings().builderChurnDetection.value) {
            return false;
        }
        long hash = BetterBlockPos.longHash(pos);
        ChurnRecord record = churn.get(hash);
        if (record == null) {
            record = new ChurnRecord();
            record.lastWasPlace = placing;
            record.lastTick = builderTick;
            churn.put(hash, record);
            return false;
        }
        if (builderTick - record.lastTick > Baritone.settings().builderChurnWindowTicks.value) {
            // too long ago to be part of the same loop; start counting again
            record.alternations = 0;
        } else if (record.lastWasPlace != placing) {
            record.alternations++;
        }
        record.lastWasPlace = placing;
        record.lastTick = builderTick;

        if (record.alternations >= Baritone.settings().builderChurnThreshold.value) {
            int until = builderTick + Baritone.settings().builderChurnCooldownTicks.value;
            churnBlacklist.put(hash, until);
            churn.remove(hash);
            logDirect("Stuck breaking and replacing " + pos + "; leaving it alone for a bit and re-routing");
            return true;
        }
        return false;
    }

    /**
     * Whether this position is currently being left alone because it was detected churning.
     */
    private boolean isChurnBlacklisted(BetterBlockPos pos) {
        if (churnBlacklist.isEmpty()) {
            return false;
        }
        Integer until = churnBlacklist.get(BetterBlockPos.longHash(pos));
        if (until == null) {
            return false;
        }
        if (builderTick >= until) {
            churnBlacklist.remove(BetterBlockPos.longHash(pos));
            return false;
        }
        return true;
    }

    /**
     * Abandons the current plan so the next tick picks different work, skipping whatever we just
     * blacklisted.
     */
    private PathingCommand forceRerouteAfterChurn(boolean calcFailed, boolean isSafeToCancel, int recursions) {
        incorrectPositions = null;
        lastGoal = null;
        consecutiveReroutes = 0;
        rerouteCommitTicks = 0;
        churn.clear();
        churnBlacklist.clear();
        return onTick(calcFailed, isSafeToCancel, recursions + 1);
    }

    /**
     * How many upcoming movements to protect from being built over.
     */
    private static final int PATH_LOOKAHEAD_MOVEMENTS = 10;

    /**
     * Whether the path we're currently walking needs this position to stay open.
     * <p>
     * Without this the builder will happily fill in a hole that pathing just dug to get through:
     * the movement breaks a block, the builder sees the schematic wants one there and immediately
     * places it back, the movement breaks it again, and the bot stands there thrashing. Positions
     * the path still needs are simply deferred -- they get built once we're no longer walking
     * through them.
     */
    private boolean pathNeedsOpen(BlockPos pos) {
        PathExecutor exec = baritone.getPathingBehavior().getCurrent();
        if (exec == null || exec.finished() || exec.failed()) {
            return false;
        }
        List<IMovement> movements = exec.getPath().movements();
        int from = exec.getPosition();
        int to = Math.min(movements.size(), from + PATH_LOOKAHEAD_MOVEMENTS);
        for (int i = from; i < to; i++) {
            if (Arrays.asList(((Movement) movements.get(i)).toBreakAll()).contains(pos)) {
                return true;
            }
        }
        return false;
    }

    public boolean placementPlausible(BlockPos pos, BlockState state) {
        VoxelShape voxelshape = state.getCollisionShape(ctx.world(), pos);
        return voxelshape.isEmpty() || ctx.world().isUnobstructed(null, voxelshape.move(pos.getX(), pos.getY(), pos.getZ()));
    }

    private Optional<Placement> possibleToPlace(BlockState toPlace, int x, int y, int z, BlockStateInterface bsi) {
        for (Direction against : Direction.values()) {
            BetterBlockPos placeAgainstPos = new BetterBlockPos(x, y, z).relative(against);
            BlockState placeAgainstState = bsi.get0(placeAgainstPos);
            if (MovementHelper.isReplaceable(placeAgainstPos.x, placeAgainstPos.y, placeAgainstPos.z, placeAgainstState, bsi)) {
                continue;
            }
            if (!toPlace.canSurvive(ctx.world(), new BetterBlockPos(x, y, z))) {
                continue;
            }
            if (!placementPlausible(new BetterBlockPos(x, y, z), toPlace)) {
                continue;
            }
            VoxelShape shape = placeAgainstState.getShape(ctx.world(), placeAgainstPos);
            if (shape.isEmpty()) {
                continue;
            }
            AABB aabb = shape.bounds();
            for (Vec3 placementMultiplier : aabbSideMultipliers(against)) {
                double placeX = placeAgainstPos.x + aabb.minX * placementMultiplier.x + aabb.maxX * (1 - placementMultiplier.x);
                double placeY = placeAgainstPos.y + aabb.minY * placementMultiplier.y + aabb.maxY * (1 - placementMultiplier.y);
                double placeZ = placeAgainstPos.z + aabb.minZ * placementMultiplier.z + aabb.maxZ * (1 - placementMultiplier.z);
                Rotation rot = RotationUtils.calcRotationFromVec3d(RayTraceUtils.inferSneakingEyePosition(ctx.player()), new Vec3(placeX, placeY, placeZ), ctx.playerRotations());
                Rotation actualRot = baritone.getLookBehavior().getAimProcessor().peekRotation(rot);
                HitResult result = RayTraceUtils.rayTraceTowards(ctx.player(), actualRot, ctx.playerController().getBlockReachDistance(), true);
                if (result != null && result.getType() == HitResult.Type.BLOCK && ((BlockHitResult) result).getBlockPos().equals(placeAgainstPos) && ((BlockHitResult) result).getDirection() == against.getOpposite()) {
                    OptionalInt hotbar = hasAnyItemThatWouldPlace(toPlace, result, actualRot);
                    if (hotbar.isPresent()) {
                        return Optional.of(new Placement(hotbar.getAsInt(), placeAgainstPos, against.getOpposite(), rot, toPlace));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private OptionalInt hasAnyItemThatWouldPlace(BlockState desired, HitResult result, Rotation rot) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = ctx.player().getInventory().getNonEquipmentItems().get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
                continue;
            }
            BlockState wouldBePlaced = wouldPlace(stack, (BlockHitResult) result, rot);
            if (wouldBePlaced == null) {
                continue;
            }
            if (valid(wouldBePlaced, desired, true)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * The state vanilla would actually create if we clicked {@code result} while aimed at
     * {@code rot} holding {@code stack}, or {@code null} if nothing would be placed.
     * <p>
     * The rotation matters: many blocks (stairs, furnaces, observers, logs...) read the player's
     * facing out of the placement context, so the answer differs depending on where we're looking
     * when the click lands.
     */
    private BlockState wouldPlace(ItemStack stack, BlockHitResult result, Rotation rot) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return null;
        }
        float originalYaw = ctx.player().getYRot();
        float originalPitch = ctx.player().getXRot();
        // the state depends on the facing of the player sometimes
        ctx.player().setYRot(rot.getYaw());
        ctx.player().setXRot(rot.getPitch());
        BlockPlaceContext meme = new BlockPlaceContext(new UseOnContext(
                ctx.world(),
                ctx.player(),
                InteractionHand.MAIN_HAND,
                stack,
                result
        ) {}); // that {} gives us access to a protected constructor lmfao
        BlockState wouldBePlaced = ((BlockItem) stack.getItem()).getBlock().getStateForPlacement(meme);
        boolean canPlace = meme.canPlace();
        ctx.player().setYRot(originalYaw);
        ctx.player().setXRot(originalPitch);
        return canPlace ? wouldBePlaced : null;
    }

    /**
     * Whether clicking right now, from wherever we happen to be looking this instant, would produce
     * the block the schematic asked for.
     * <p>
     * {@link #possibleToPlace} checks this against the rotation it <i>plans</i> to aim at, but the
     * look behaviour applies its own per-tick jitter and the click fires on whatever rotation we
     * actually have. A yaw that lands a fraction of a degree the wrong side of a quadrant boundary
     * flips {@code getHorizontalDirection}, and the stairs come out backwards. Re-checking against
     * the live rotation turns that into a missed tick instead of a wrong block.
     */
    private boolean placementStillCorrect(Placement place) {
        HitResult result = RayTraceUtils.rayTraceTowards(ctx.player(), ctx.playerRotations(), ctx.playerController().getBlockReachDistance(), true);
        if (result == null || result.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockHitResult hit = (BlockHitResult) result;
        if (!hit.getBlockPos().equals(place.placeAgainst) || hit.getDirection() != place.side) {
            return false;
        }
        ItemStack stack = ctx.player().getInventory().getNonEquipmentItems().get(place.hotbarSelection);
        BlockState wouldBePlaced = wouldPlace(stack, hit, ctx.playerRotations());
        return wouldBePlaced != null && valid(wouldBePlaced, place.desired, true);
    }

    private static Vec3[] aabbSideMultipliers(Direction side) {
        switch (side) {
            case UP:
                return new Vec3[]{new Vec3(0.5, 1, 0.5), new Vec3(0.1, 1, 0.5), new Vec3(0.9, 1, 0.5), new Vec3(0.5, 1, 0.1), new Vec3(0.5, 1, 0.9)};
            case DOWN:
                return new Vec3[]{new Vec3(0.5, 0, 0.5), new Vec3(0.1, 0, 0.5), new Vec3(0.9, 0, 0.5), new Vec3(0.5, 0, 0.1), new Vec3(0.5, 0, 0.9)};
            case NORTH:
            case SOUTH:
            case EAST:
            case WEST:
                double x = side.getStepX() == 0 ? 0.5 : (1 + side.getStepX()) / 2D;
                double z = side.getStepZ() == 0 ? 0.5 : (1 + side.getStepZ()) / 2D;
                return new Vec3[]{new Vec3(x, 0.25, z), new Vec3(x, 0.75, z)};
            default: // null
                throw new IllegalStateException("Unexpected side " + side);
        }
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        return onTick(calcFailed, isSafeToCancel, 0);
    }

    private PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel, int recursions) {
        if (recursions > 100) { // onTick calls itself, don't crash
            return new PathingCommand(null, PathingCommandType.SET_GOAL_AND_PATH);
        }
        approxPlaceable = approxPlaceable(36);
        builderTick++;
        if (baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)) {
            ticks = 5;
        } else {
            ticks--;
        }
        baritone.getInputOverrideHandler().clearAllKeys();
        if (paused) {
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        // Before laying a single block, go and look inside any registered box we've never opened.
        // Knowing the real contents up front means material lookups pick the right box first time
        // instead of walking to one speculatively and finding it useless.
        if (!triedIndexingThisBuild
                && Baritone.settings().restockFromBoxes.value
                && Baritone.settings().restockIndexBeforeBuild.value) {
            triedIndexingThisBuild = true; // only ever attempted once per build
            IRestockProcess restock = restockProcess();
            if (restock != null && restock.requestIndexing(false)) {
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }
        // Out of room rather than out of materials: go and empty the inventory into a registered
        // box before carrying on. Mostly for clearing jobs, where everything broken is rubble and
        // the inventory fills long before the area is clear -- past that point blocks just drop on
        // the floor. isDepositImpossible stops this being re-asked once there's nowhere to unload.
        if (inventoryIsFull()) {
            IRestockProcess restock = restockProcess();
            if (restock != null && !restock.isDepositImpossible() && restock.requestDeposit()) {
                // the restock process outranks us and takes control next tick
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }
        if (Baritone.settings().buildInLayers.value) {
            if (realSchematic == null) {
                realSchematic = schematic;
            }
            ISchematic realSchematic = this.realSchematic; // wrap this properly, dont just have the inner class refer to the builderprocess.this
            int minYInclusive;
            int maxYInclusive;
            // layer = 0 should be nothing
            // layer = realSchematic.heightY() should be everything
            if (Baritone.settings().layerOrder.value) { // top to bottom
                maxYInclusive = realSchematic.heightY() - 1;
                minYInclusive = realSchematic.heightY() - layer * Baritone.settings().layerHeight.value;
            } else {
                maxYInclusive = layer * Baritone.settings().layerHeight.value - 1;
                minYInclusive = 0;
            }
            schematic = new ISchematic() {
                @Override
                public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
                    return realSchematic.desiredState(x, y, z, current, BuilderProcess.this.approxPlaceable);
                }

                @Override
                public boolean inSchematic(int x, int y, int z, BlockState currentState) {
                    return ISchematic.super.inSchematic(x, y, z, currentState) && y >= minYInclusive && y <= maxYInclusive && realSchematic.inSchematic(x, y, z, currentState);
                }

                @Override
                public void reset() {
                    realSchematic.reset();
                }

                @Override
                public int widthX() {
                    return realSchematic.widthX();
                }

                @Override
                public int heightY() {
                    return realSchematic.heightY();
                }

                @Override
                public int lengthZ() {
                    return realSchematic.lengthZ();
                }
            };
        }
        BuilderCalculationContext bcc = new BuilderCalculationContext();
        if (!recalc(bcc)) {
            if (Baritone.settings().buildInLayers.value && layer * Baritone.settings().layerHeight.value < stopAtHeight) {
                logDirect("Starting layer " + layer);
                layer++;
                return onTick(calcFailed, isSafeToCancel, recursions + 1);
            }
            Vec3i repeat = Baritone.settings().buildRepeat.value;
            int max = Baritone.settings().buildRepeatCount.value;
            numRepeats++;
            if (repeat.equals(new Vec3i(0, 0, 0)) || (max != -1 && numRepeats >= max)) {
                logDirect("Done building");
                if (Baritone.settings().notificationOnBuildFinished.value) {
                    logNotification("Done building", false);
                }
                onLostControl();
                return null;
            }
            // build repeat time
            layer = 0;
            origin = new BlockPos(origin).offset(repeat);
            if (!Baritone.settings().buildRepeatSneaky.value) {
                schematic.reset();
            }
            logDirect("Repeating build in vector " + repeat + ", new origin is " + origin);
            return onTick(calcFailed, isSafeToCancel, recursions + 1);
        }
        if (Baritone.settings().distanceTrim.value) {
            trim();
        }

        Optional<Tuple<BetterBlockPos, Rotation>> toBreak = toBreakNearPlayer(bcc);
        if (toBreak.isPresent() && isSafeToCancel && ctx.player().onGround()) {
            // we'd like to pause to break this block
            // only change look direction if it's safe (don't want to fuck up an in progress parkour for example
            Rotation rot = toBreak.get().getB();
            BetterBlockPos pos = toBreak.get().getA();
            baritone.getLookBehavior().updateTarget(rot, true);
            MovementHelper.switchToBestToolFor(ctx, bcc.get(pos));
            if (ctx.player().isCrouching()) {
                // really horrible bug where a block is visible for breaking while sneaking but not otherwise
                // so you can't see it, it goes to place something else, sneaks, then the next tick it tries to break
                // and is unable since it's unsneaked in the intermediary tick
                baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
            }
            if (ctx.isLookingAt(pos) || ctx.playerRotations().isReallyCloseTo(rot)) {
                if (noteBlockAction(pos, false)) {
                    return forceRerouteAfterChurn(calcFailed, isSafeToCancel, recursions);
                }
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
            }
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        List<BlockState> desirableOnHotbar = new ArrayList<>();
        Optional<Placement> toPlace = searchForPlacables(bcc, desirableOnHotbar);
        if (toPlace.isPresent() && isSafeToCancel && ctx.player().onGround() && ticks <= 0) {
            Rotation rot = toPlace.get().rot;
            baritone.getLookBehavior().updateTarget(rot, true);
            ctx.player().getInventory().setSelectedSlot(toPlace.get().hotbarSelection);
            baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
            boolean aimed = (ctx.isLookingAt(toPlace.get().placeAgainst) && ((BlockHitResult) ctx.objectMouseOver()).getDirection().equals(toPlace.get().side)) || ctx.playerRotations().isReallyCloseTo(rot);
            // don't click until the block we'd actually create matches the one we want; both arms
            // above can be true while the live yaw has drifted into the neighbouring quadrant
            if (aimed && placementStillCorrect(toPlace.get())) {
                // the block actually being created is the one on the far side of the face we click
                BetterBlockPos placedAt = BetterBlockPos.from(toPlace.get().placeAgainst.relative(toPlace.get().side));
                if (noteBlockAction(placedAt, true)) {
                    return forceRerouteAfterChurn(calcFailed, isSafeToCancel, recursions);
                }
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            }
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        if (Baritone.settings().allowInventory.value) {
            ArrayList<Integer> usefulSlots = new ArrayList<>();
            List<BlockState> noValidHotbarOption = new ArrayList<>();
            outer:
            for (BlockState desired : desirableOnHotbar) {
                for (int i = 0; i < 9; i++) {
                    if (couldProduce(approxPlaceable.get(i), desired)) {
                        usefulSlots.add(i);
                        continue outer;
                    }
                }
                noValidHotbarOption.add(desired);
            }

            outer:
            for (int i = 9; i < 36; i++) {
                for (BlockState desired : noValidHotbarOption) {
                    if (couldProduce(approxPlaceable.get(i), desired)) {
                        if (!baritone.getInventoryBehavior().attemptToPutOnHotbar(i, usefulSlots::contains)) {
                            // awaiting inventory move, so pause
                            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                        }
                        break outer;
                    }
                }
            }
        }

        Goal goal = assemble(bcc, approxPlaceable.subList(0, 9));
        if (goal == null) {
            goal = assemble(bcc, approxPlaceable, true); // we're far away, so assume that we have our whole inventory to recalculate placeable properly
            if (goal == null) {
                // Nothing in the current working set can be placed or broken. If that's because
                // we're out of a material, try to restock from a registered shulker box before
                // giving anything up.
                //
                // Note this is NOT the same as "the build is finished": incorrectPositions is a
                // bounded window (trimmed to nearby positions by distanceTrim, and capped at
                // incorrectSize), so plenty of the schematic may remain further away. That's why
                // the empty-missing case below still falls through to the original pause.
                if (Baritone.settings().restockFromBoxes.value && !missingMaterials.isEmpty()) {
                    Map<BlockState, Integer> missing = new HashMap<>(missingMaterials);
                    IRestockProcess restock = restockProcess();
                    if (restock == null) {
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                    // drop anything we've already concluded is unavailable, so we don't keep asking
                    missing.keySet().removeIf(restock::isUnobtainable);
                    if (!missing.isEmpty() && restock.requestRestock(missing)) {
                        // the restock process outranks us and will take control next tick;
                        // just hold still until it hands back
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                    // Either everything we're short of has already been given up on, or
                    // requestRestock just gave up on the rest. Either way some of the positions
                    // sitting in our working set can never be filled.
                    //
                    // recalcNearby only rescans a few blocks around the player, so those positions
                    // would otherwise linger in incorrectPositions forever and keep us stuck here.
                    // Dropping the working set forces a fullRecalc, which now skips unobtainable
                    // materials entirely -- so we either pick up other work elsewhere in the
                    // schematic, or the set comes back empty and the normal "Done building" path
                    // finishes the build cleanly.
                    if (missingMaterials.keySet().stream().anyMatch(restock::isUnobtainable)) {
                        incorrectPositions = null;
                        return onTick(calcFailed, isSafeToCancel, recursions + 1);
                    }
                }
                if (Baritone.settings().skipFailedLayers.value && Baritone.settings().buildInLayers.value && layer * Baritone.settings().layerHeight.value < realSchematic.heightY()) {
                    logDirect("Skipping layer that I cannot construct! Layer #" + layer);
                    layer++;
                    return onTick(calcFailed, isSafeToCancel, recursions + 1);
                }
                logDirect("Unable to do it. Pausing. resume to resume, cancel to cancel");
                paused = true;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }
        return commitAwarePathingCommand(goal, bcc);
    }

    private boolean recalc(BuilderCalculationContext bcc) {
        if (incorrectPositions == null) {
            incorrectPositions = new HashSet<>();
            fullRecalc(bcc);
            if (incorrectPositions.isEmpty()) {
                return false;
            }
        }
        recalcNearby(bcc);
        if (incorrectPositions.isEmpty()) {
            fullRecalc(bcc);
        }
        return !incorrectPositions.isEmpty();
    }

    @Override
    public synchronized boolean managesPosition(BlockPos pos) {
        ISchematic target = realSchematic != null ? realSchematic : schematic;
        if (target == null || origin == null) {
            return false; // not building, so we don't own anything
        }
        int x = pos.getX() - origin.getX();
        int y = pos.getY() - origin.getY();
        int z = pos.getZ() - origin.getZ();
        return x >= 0 && y >= 0 && z >= 0
                && x < target.widthX() && y < target.heightY() && z < target.lengthZ();
    }

    /**
     * Normally the builder forces a full goal revalidation every tick, which is what lets it react
     * to the world changing. The downside is there's no loop detection anywhere in the builder: if
     * the goal it picks is unreachable, it will re-pick it forever and stand still recalculating.
     * <p>
     * So count how many ticks in a row the chosen goal actually changed. Past the threshold, stop
     * re-planning for a short while and let whatever path is already in flight run to completion.
     * The commit is deliberately temporary -- permanently pinning the goal would stop the builder
     * reacting to anything at all.
     */
    private PathingCommand commitAwarePathingCommand(Goal goal, BuilderCalculationContext bcc) {
        int maxReroutes = Baritone.settings().builderMaxReroutes.value;
        if (maxReroutes <= 0) {
            return new PathingCommandContext(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH, bcc);
        }

        if (rerouteCommitTicks > 0) {
            rerouteCommitTicks--;
            if (rerouteCommitTicks == 0) {
                // give normal planning another chance now that we've had time to actually move
                consecutiveReroutes = 0;
                lastGoal = null;
            }
            // null goal with SET_GOAL_AND_PATH means "carry on with the current goal and path"
            return new PathingCommandContext(null, PathingCommandType.SET_GOAL_AND_PATH, bcc);
        }

        if (goal.equals(lastGoal)) {
            consecutiveReroutes = 0;
        } else {
            lastGoal = goal;
            consecutiveReroutes++;
            if (consecutiveReroutes > maxReroutes) {
                rerouteCommitTicks = Baritone.settings().builderRerouteCommitTicks.value;
                consecutiveReroutes = 0;
                logDebug("Builder changed its mind " + maxReroutes + " times in a row; sticking with the current path for "
                        + rerouteCommitTicks + " ticks");
                return new PathingCommandContext(null, PathingCommandType.SET_GOAL_AND_PATH, bcc);
            }
        }
        return new PathingCommandContext(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH, bcc);
    }

    /**
     * The set of blocks this schematic uses anywhere. Scanned once and cached, in the same shape as
     * {@link #fullRecalc}. Very large schematics are skipped rather than scanned, and report every
     * block as wanted, so we never throw away materials just because the check was too expensive.
     */
    @Override
    public synchronized boolean schematicWants(Block block) {
        ISchematic target = realSchematic != null ? realSchematic : schematic;
        if (target == null) {
            return true; // not building; assume everything matters
        }
        if (clearingOnly) {
            // a clearing build wants nothing at all, so everything mined out of it is rubble. Worth
            // answering up front: these are the builds too big to scan, and #sel cleararea is the
            // whole reason the question gets asked in the first place
            return block == Blocks.AIR;
        }
        if (schematicPalette == null && !paletteTooLarge) {
            long volume = (long) target.widthX() * target.heightY() * target.lengthZ();
            if (volume > MAX_PALETTE_SCAN_VOLUME) {
                paletteTooLarge = true;
                logDirect("Schematic is too large to work out which blocks are junk, so I won't discard anything.");
            } else {
                Set<Block> palette = new HashSet<>();
                for (int y = 0; y < target.heightY(); y++) {
                    for (int z = 0; z < target.lengthZ(); z++) {
                        for (int x = 0; x < target.widthX(); x++) {
                            BlockState desired = target.desiredState(x, y, z, Blocks.AIR.defaultBlockState(), Collections.emptyList());
                            if (desired != null) {
                                palette.add(desired.getBlock());
                            }
                        }
                    }
                }
                schematicPalette = palette;
            }
        }
        return paletteTooLarge || schematicPalette == null || schematicPalette.contains(block);
    }

    /**
     * Whether this schematic asks for air everywhere, decided structurally rather than by reading
     * every position.
     * <p>
     * Only the two shapes that clearing actually produces are recognised -- a fill of air, and a
     * composite of those, which is what {@code #sel cleararea} builds for a multi-part selection.
     * Anything else answers no and falls back to the palette scan, so a false negative costs
     * nothing beyond the scan we would have done anyway.
     */
    private static boolean isPureAir(ISchematic schematic) {
        if (schematic instanceof FillSchematic) {
            return ((FillSchematic) schematic).getBom().matches(Blocks.AIR.defaultBlockState());
        }
        if (schematic instanceof CompositeSchematic) {
            List<CompositeSchematicEntry> parts = ((CompositeSchematic) schematic).getSchematics();
            if (parts.isEmpty()) {
                return false;
            }
            for (CompositeSchematicEntry entry : parts) {
                if (!isPureAir(entry.schematic)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Whether we've run out of room to put what we're breaking.
     * <p>
     * Only asked while {@code shulkerDump} is on. Counts the whole inventory rather than the
     * hotbar, since anything mined lands wherever there's space.
     */
    private boolean inventoryIsFull() {
        if (!Baritone.settings().shulkerDump.value || ctx.player() == null) {
            return false;
        }
        int free = 0;
        for (ItemStack stack : ctx.player().getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) {
                free++;
            }
        }
        return free < Baritone.settings().shulkerDumpWhenFreeSlotsBelow.value;
    }

    /**
     * Whether the restock logic has concluded this material can't be sourced from any registered
     * shulker box during this build.
     */
    private boolean isUnobtainable(BlockState desired) {
        IRestockProcess restock = restockProcess();
        return desired != null
                && restock != null
                && Baritone.settings().restockFromBoxes.value
                && restock.isUnobtainable(desired);
    }

    /**
     * The restock process, or null if it isn't constructed yet. Registration calls
     * {@link #onLostControl()} before every process exists, so this must never be assumed present.
     */
    private IRestockProcess restockProcess() {
        return baritone.getRestockProcess();
    }

    private void clearRestockGiveUps() {
        IRestockProcess restock = restockProcess();
        if (restock != null) {
            restock.clearUnobtainable();
        }
    }

    private void trim() {
        HashSet<BetterBlockPos> copy = new HashSet<>(incorrectPositions);
        copy.removeIf(pos -> pos.distSqr(ctx.player().blockPosition()) > 200);
        if (!copy.isEmpty()) {
            incorrectPositions = copy;
        }
    }

    private void recalcNearby(BuilderCalculationContext bcc) {
        BetterBlockPos center = ctx.playerFeet();
        int radius = Baritone.settings().builderTickScanRadius.value;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = center.x + dx;
                    int y = center.y + dy;
                    int z = center.z + dz;
                    BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
                    if (desired != null && !isUnobtainable(desired) && !isChurnBlacklisted(new BetterBlockPos(x, y, z))) {
                        // we care about this position
                        BetterBlockPos pos = new BetterBlockPos(x, y, z);
                        if (valid(bcc.bsi.get0(x, y, z), desired, false)) {
                            incorrectPositions.remove(pos);
                            observedCompleted.add(BetterBlockPos.longHash(pos));
                        } else {
                            incorrectPositions.add(pos);
                            observedCompleted.remove(BetterBlockPos.longHash(pos));
                        }
                    }
                }
            }
        }
    }

    private void fullRecalc(BuilderCalculationContext bcc) {
        incorrectPositions = new HashSet<>();
        for (int y = 0; y < schematic.heightY(); y++) {
            for (int z = 0; z < schematic.lengthZ(); z++) {
                for (int x = 0; x < schematic.widthX(); x++) {
                    int blockX = x + origin.getX();
                    int blockY = y + origin.getY();
                    int blockZ = z + origin.getZ();
                    BlockState current = bcc.bsi.get0(blockX, blockY, blockZ);
                    if (!schematic.inSchematic(x, y, z, current)) {
                        continue;
                    }
                    // Materials we've given up on stop counting as incorrect entirely. That makes
                    // the builder keep planning around them instead of stalling, stops them eating
                    // the incorrectSize budget, and lets the normal "Done building" path fire once
                    // only unobtainable work is left.
                    if (isUnobtainable(schematic.desiredState(x, y, z, current, this.approxPlaceable))) {
                        continue;
                    }
                    if (isChurnBlacklisted(new BetterBlockPos(blockX, blockY, blockZ))) {
                        continue; // temporarily left alone after a break/place loop
                    }
                    if (bcc.bsi.worldContainsLoadedChunk(blockX, blockZ)) { // check if its in render distance, not if its in cache
                        // we can directly observe this block, it is in render distance
                        if (valid(bcc.bsi.get0(blockX, blockY, blockZ), schematic.desiredState(x, y, z, current, this.approxPlaceable), false)) {
                            observedCompleted.add(BetterBlockPos.longHash(blockX, blockY, blockZ));
                        } else {
                            incorrectPositions.add(new BetterBlockPos(blockX, blockY, blockZ));
                            observedCompleted.remove(BetterBlockPos.longHash(blockX, blockY, blockZ));
                            if (incorrectPositions.size() > Baritone.settings().incorrectSize.value) {
                                return;
                            }
                        }
                        continue;
                    }
                    // this is not in render distance
                    if (!observedCompleted.contains(BetterBlockPos.longHash(blockX, blockY, blockZ))) {
                        // and we've never seen this position be correct
                        // therefore mark as incorrect
                        incorrectPositions.add(new BetterBlockPos(blockX, blockY, blockZ));
                        if (incorrectPositions.size() > Baritone.settings().incorrectSize.value) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private Goal assemble(BuilderCalculationContext bcc, List<BlockState> approxPlaceable) {
        return assemble(bcc, approxPlaceable, false);
    }

    private Goal assemble(BuilderCalculationContext bcc, List<BlockState> approxPlaceable, boolean logMissing) {
        List<BetterBlockPos> placeable = new ArrayList<>();
        List<BetterBlockPos> breakable = new ArrayList<>();
        List<BetterBlockPos> sourceLiquids = new ArrayList<>();
        List<BetterBlockPos> flowingLiquids = new ArrayList<>();
        Map<BlockState, Integer> missing = new HashMap<>();
        List<BetterBlockPos> outOfBounds = new ArrayList<>();
        incorrectPositions.forEach(pos -> {
            BlockState state = bcc.bsi.get0(pos);
            if (state.getBlock() instanceof AirBlock) {
                BlockState desired = bcc.getSchematic(pos.x, pos.y, pos.z, state);
                if (desired == null) {
                    outOfBounds.add(pos);
                } else if (containsCouldProduce(approxPlaceable, desired)) {
                    placeable.add(pos);
                } else {
                    missing.put(desired, 1 + missing.getOrDefault(desired, 0));
                }
            } else {
                if (state.getBlock() instanceof LiquidBlock) {
                    // if the block itself is JUST a liquid (i.e. not just a waterlogged block), we CANNOT break it
                    // TODO for 1.13 make sure that this only matches pure water, not waterlogged blocks
                    if (!MovementHelper.possiblyFlowing(state)) {
                        // if it's a source block then we want to replace it with a throwaway
                        sourceLiquids.add(pos);
                    } else {
                        flowingLiquids.add(pos);
                    }
                } else {
                    breakable.add(pos);
                }
            }
        });
        incorrectPositions.removeAll(outOfBounds);
        List<Goal> toBreak = new ArrayList<>();
        breakable.forEach(pos -> toBreak.add(breakGoal(pos, bcc)));
        List<Goal> toPlace = new ArrayList<>();
        // NB: deliberately NOT filtered by pathNeedsOpen. These are goals to path towards, not
        // blocks placed this instant, and filtering here can empty the list entirely while a path
        // runs through the build -- which looks to the caller like "nothing is placeable", skips
        // the restock hook (it needs a non-empty missing map) and wrongly pauses the whole build.
        // The re-placement loop this guards against happens in searchForPlacables, not here.
        placeable.forEach(pos -> {
            if (!placeable.contains(pos.below()) && !placeable.contains(pos.below(2))) {
                toPlace.add(placementGoal(pos, bcc));
            }
        });
        sourceLiquids.forEach(pos -> toPlace.add(new GoalBlock(pos.above())));

        if (!toPlace.isEmpty()) {
            return new JankyGoalComposite(new GoalComposite(toPlace.toArray(new Goal[0])), new GoalComposite(toBreak.toArray(new Goal[0])));
        }
        if (toBreak.isEmpty()) {
            this.missingMaterials = missing;
            if (logMissing && !missing.isEmpty()) {
                logDirect("Missing materials for at least:");
                logDirect(missing.entrySet().stream()
                        .map(e -> String.format("%sx %s", e.getValue(), e.getKey()))
                        .collect(Collectors.joining("\n")));
            }
            if (logMissing && !flowingLiquids.isEmpty()) {
                logDirect("Unreplaceable liquids at at least:");
                logDirect(flowingLiquids.stream()
                        .map(p -> String.format("%s %s %s", p.x, p.y, p.z))
                        .collect(Collectors.joining("\n")));
            }
            return null;
        }
        return new GoalComposite(toBreak.toArray(new Goal[0]));
    }

    public static class JankyGoalComposite implements Goal {

        private final Goal primary;
        private final Goal fallback;

        public JankyGoalComposite(Goal primary, Goal fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }


        @Override
        public boolean isInGoal(int x, int y, int z) {
            return primary.isInGoal(x, y, z) || fallback.isInGoal(x, y, z);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            return primary.heuristic(x, y, z);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            JankyGoalComposite goal = (JankyGoalComposite) o;
            return Objects.equals(primary, goal.primary)
                    && Objects.equals(fallback, goal.fallback);
        }

        @Override
        public int hashCode() {
            int hash = -1701079641;
            hash = hash * 1196141026 + primary.hashCode();
            hash = hash * -80327868 + fallback.hashCode();
            return hash;
        }

        @Override
        public String toString() {
            return "JankyComposite Primary: " + primary + " Fallback: " + fallback;
        }
    }

    public static class GoalBreak extends GoalGetToBlock {

        public GoalBreak(BlockPos pos) {
            super(pos);
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            // can't stand right on top of a block, that might not work (what if it's unsupported, can't break then)
            if (y > this.y) {
                return false;
            }
            // but any other adjacent works for breaking, including inside or below
            return super.isInGoal(x, y, z);
        }

        @Override
        public String toString() {
            return String.format(
                    "GoalBreak{x=%s,y=%s,z=%s}",
                    SettingsUtil.maybeCensor(x),
                    SettingsUtil.maybeCensor(y),
                    SettingsUtil.maybeCensor(z)
            );
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 1636324008;
        }
    }

    private Goal placementGoal(BlockPos pos, BuilderCalculationContext bcc) {
        if (!(ctx.world().getBlockState(pos).getBlock() instanceof AirBlock)) {  // TODO can this even happen?
            return new GoalPlace(pos);
        }
        boolean allowSameLevel = !(ctx.world().getBlockState(pos.above()).getBlock() instanceof AirBlock);
        BlockState current = ctx.world().getBlockState(pos);
        for (Direction facing : Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP) {
            //noinspection ConstantConditions
            if (MovementHelper.canPlaceAgainst(ctx, pos.relative(facing)) && placementPlausible(pos, bcc.getSchematic(pos.getX(), pos.getY(), pos.getZ(), current))) {
                // if which way we're facing decides how this block comes out, go and stand somewhere
                // that gets it right rather than placing from whichever side we walked up to
                Goal oriented = orientedPlacementGoal(pos, bcc.getSchematic(pos.getX(), pos.getY(), pos.getZ(), current));
                return oriented != null ? oriented : new GoalAdjacent(pos, pos.relative(facing), allowSameLevel);
            }
        }
        return new GoalPlace(pos);
    }

    private Goal breakGoal(BlockPos pos, BuilderCalculationContext bcc) {
        if (Baritone.settings().goalBreakFromAbove.value && bcc.bsi.get0(pos.above()).getBlock() instanceof AirBlock && bcc.bsi.get0(pos.above(2)).getBlock() instanceof AirBlock) { // TODO maybe possible without the up(2) check?
            return new JankyGoalComposite(new GoalBreak(pos), new GoalGetToBlock(pos.above()) {
                @Override
                public boolean isInGoal(int x, int y, int z) {
                    if (y > this.y || (x == this.x && y == this.y && z == this.z)) {
                        return false;
                    }
                    return super.isInGoal(x, y, z);
                }
            });
        }
        return new GoalBreak(pos);
    }

    /**
     * A goal for a block whose state depends on which way we're facing when we place it, e.g. stairs.
     * <p>
     * The state vanilla creates is read out of the placement context, so the aim rotation and the
     * resulting facing are the same thing -- from a given eye position we cannot choose one without
     * choosing the other. The only lever we have is where we stand. So instead of walking to any
     * adjacent block and hoping, we walk a couple of blocks onto the side that makes looking at the
     * target produce the facing the schematic asked for.
     * <p>
     * Returns {@code null} when this doesn't apply: the setting is off, the block doesn't care which
     * way we're facing, no facing works (a six-way block wanting up/down, say), or we've already
     * spent long enough trying and it's time to just place the thing.
     */
    private Goal orientedPlacementGoal(BlockPos pos, BlockState desired) {
        if (!Baritone.settings().buildOrientBeforePlacing.value || desired == null) {
            return null;
        }
        if (Baritone.settings().buildIgnoreDirection.value) {
            return null; // they've said they don't care how it comes out; don't walk around for it
        }
        long hash = BetterBlockPos.longHash(pos.getX(), pos.getY(), pos.getZ());
        // only start the clock once we're actually in the neighbourhood, otherwise a long walk
        // across the build counts against a block we haven't started on yet
        if (ctx.playerFeet().distSqr(pos) < ORIENT_TIMER_RANGE * ORIENT_TIMER_RANGE) {
            Integer firstSeen = orientFirstSeen.get(hash);
            if (firstSeen == null) {
                orientFirstSeen.put(hash, builderTick);
            } else if (builderTick - firstSeen > Baritone.settings().buildOrientTimeoutTicks.value) {
                // the spot we want may be walled in or otherwise unreachable; don't stall the build
                // over it forever. placementStillCorrect still keeps us from placing it wrong.
                return null;
            }
        }
        Set<Direction> facings = acceptableFacings(pos, desired);
        if (facings.isEmpty() || facings.size() == 4) {
            return null; // impossible, or the block doesn't care -- either way, nothing to constrain
        }
        int min = Math.max(1, Baritone.settings().buildOrientStandDistance.value);
        return new GoalPlaceOriented(pos, facings, min, Math.max(min, ORIENT_MAX_STAND_DISTANCE));
    }

    /**
     * Which directions we'd have to be facing for vanilla to give us {@code desired}.
     * <p>
     * Worked out by simulating the placement at each of the four horizontal yaws rather than by
     * assuming a sign: stairs face the way you look, furnaces and chests face the opposite way, and
     * pillars key off the clicked face entirely. Simulating covers all of them without a table of
     * special cases. {@code HALF} is waived because the hit vector picks that, not where we stand.
     */
    private Set<Direction> acceptableFacings(BlockPos pos, BlockState desired) {
        Set<Direction> cached = orientationCache.get(desired);
        if (cached != null) {
            return cached;
        }
        Set<Direction> result = EnumSet.noneOf(Direction.class);
        ItemStack stack = ItemStack.EMPTY;
        if (approxPlaceable != null) {
            for (int i = 0; i < Math.min(9, approxPlaceable.size()); i++) {
                if (couldProduce(approxPlaceable.get(i), desired)) {
                    stack = ctx.player().getInventory().getNonEquipmentItems().get(i);
                    break;
                }
            }
        }
        if (stack.isEmpty()) {
            return result; // nothing on the hotbar to simulate with; try again once there is
        }
        // pretend to click the top of the block below, which is the placement we'd normally make
        BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), Direction.UP, pos.below(), false);
        for (Direction d : ORIENT_CANDIDATE_FACINGS) {
            BlockState wouldBePlaced = wouldPlace(stack, hit, new Rotation(d.toYRot(), 0));
            if (wouldBePlaced != null && sameBlockstate(wouldBePlaced, desired, HIT_VECTOR_PROPS)) {
                result.add(d);
            }
        }
        orientationCache.put(desired, result);
        return result;
    }

    public static class GoalPlaceOriented implements Goal, IGoalRenderPos {

        private final int x;
        private final int y;
        private final int z;
        /**
         * Directions we must be facing when we click, i.e. we have to stand on the opposite side.
         */
        private final Set<Direction> facings;
        private final int minDistance;
        private final int maxDistance;

        public GoalPlaceOriented(BlockPos pos, Set<Direction> facings, int minDistance, int maxDistance) {
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
            this.facings = EnumSet.copyOf(facings);
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
        }

        @Override
        public BlockPos getGoalPos() {
            return new BlockPos(x, y, z);
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            if (y < this.y - 1 || y > this.y + 1) {
                return false;
            }
            int dx = this.x - x;
            int dz = this.z - z;
            for (Direction facing : facings) {
                // how far the target is in front of us, and how far off to the side
                int along = dx * facing.getStepX() + dz * facing.getStepZ();
                int lateral = Math.abs(dx * facing.getStepZ() - dz * facing.getStepX());
                // a lateral block or less at two blocks out keeps the yaw within 27 degrees of the
                // axis, comfortably inside the 45 degree quadrant that decides the facing, so the
                // look behaviour's jitter can't flip us into the neighbouring one
                if (along >= minDistance && along <= maxDistance && lateral <= 1) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double heuristic(int x, int y, int z) {
            double best = Double.POSITIVE_INFINITY;
            for (Direction facing : facings) {
                // the dead-centre spot for this facing: straight back along it, at the block's level
                int idealX = this.x - facing.getStepX() * minDistance;
                int idealZ = this.z - facing.getStepZ() * minDistance;
                best = Math.min(best, GoalBlock.calculate(x - idealX, y - this.y, z - idealZ));
            }
            // prioritize lower y coordinates, as GoalAdjacent and GoalPlace do
            return this.y * 100 + best;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            GoalPlaceOriented goal = (GoalPlaceOriented) o;
            return x == goal.x
                    && y == goal.y
                    && z == goal.z
                    && minDistance == goal.minDistance
                    && maxDistance == goal.maxDistance
                    && facings.equals(goal.facings);
        }

        @Override
        public int hashCode() {
            int hash = 1731894227;
            hash = hash * 1412661222 + (int) BetterBlockPos.longHash(x, y, z);
            hash = hash * 1730799370 + facings.hashCode();
            hash = hash * 260592149 + minDistance * 31 + maxDistance;
            return hash;
        }

        @Override
        public String toString() {
            return String.format(
                    "GoalPlaceOriented{x=%s,y=%s,z=%s,facings=%s}",
                    SettingsUtil.maybeCensor(x),
                    SettingsUtil.maybeCensor(y),
                    SettingsUtil.maybeCensor(z),
                    facings
            );
        }
    }

    public static class GoalAdjacent extends GoalGetToBlock {

        private boolean allowSameLevel;
        private BlockPos no;

        public GoalAdjacent(BlockPos pos, BlockPos no, boolean allowSameLevel) {
            super(pos);
            this.no = no;
            this.allowSameLevel = allowSameLevel;
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            if (x == this.x && y == this.y && z == this.z) {
                return false;
            }
            if (x == no.getX() && y == no.getY() && z == no.getZ()) {
                return false;
            }
            if (!allowSameLevel && y == this.y - 1) {
                return false;
            }
            if (y < this.y - 1) {
                return false;
            }
            return super.isInGoal(x, y, z);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            // prioritize lower y coordinates
            return this.y * 100 + super.heuristic(x, y, z);
        }

        @Override
        public boolean equals(Object o) {
            if (!super.equals(o)) {
                return false;
            }

            GoalAdjacent goal = (GoalAdjacent) o;
            return allowSameLevel == goal.allowSameLevel
                    && Objects.equals(no, goal.no);
        }

        @Override
        public int hashCode() {
            int hash = 806368046;
            hash = hash * 1412661222 + super.hashCode();
            hash = hash * 1730799370 + (int) BetterBlockPos.longHash(no.getX(), no.getY(), no.getZ());
            hash = hash * 260592149 + (allowSameLevel ? -1314802005 : 1565710265);
            return hash;
        }

        @Override
        public String toString() {
            return String.format(
                    "GoalAdjacent{x=%s,y=%s,z=%s}",
                    SettingsUtil.maybeCensor(x),
                    SettingsUtil.maybeCensor(y),
                    SettingsUtil.maybeCensor(z)
            );
        }
    }

    public static class GoalPlace extends GoalBlock {

        public GoalPlace(BlockPos placeAt) {
            super(placeAt.above());
        }

        @Override
        public double heuristic(int x, int y, int z) {
            // prioritize lower y coordinates
            return this.y * 100 + super.heuristic(x, y, z);
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 1910811835;
        }

        @Override
        public String toString() {
            return String.format(
                    "GoalPlace{x=%s,y=%s,z=%s}",
                    SettingsUtil.maybeCensor(x),
                    SettingsUtil.maybeCensor(y),
                    SettingsUtil.maybeCensor(z)
            );
        }
    }

    @Override
    public void onLostControl() {
        clearRestockGiveUps();
        missingMaterials = new HashMap<>();
        lastGoal = null;
        consecutiveReroutes = 0;
        rerouteCommitTicks = 0;
        incorrectPositions = null;
        name = null;
        schematic = null;
        realSchematic = null;
        layer = Baritone.settings().startAtLayer.value;
        numRepeats = 0;
        paused = false;
        observedCompleted = null;
        orientFirstSeen.clear();
        orientationCache.clear();
    }

    @Override
    public String displayName0() {
        return paused ? "Builder Paused" : "Building " + name;
    }

    @Override
    public Optional<Integer> getMinLayer() {
        if (Baritone.settings().buildInLayers.value) {
            return Optional.of(this.layer);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Integer> getMaxLayer() {
        if (Baritone.settings().buildInLayers.value) {
            return Optional.of(this.stopAtHeight);
        }
        return Optional.empty();
    }

    private List<BlockState> approxPlaceable(int size) {
        List<BlockState> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack stack = ctx.player().getInventory().getNonEquipmentItems().get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
                result.add(Blocks.AIR.defaultBlockState());
                continue;
            }
            // <toxic cloud>
            BlockState itemState = ((BlockItem) stack.getItem())
                .getBlock()
                .getStateForPlacement(
                    new BlockPlaceContext(
                        new UseOnContext(ctx.world(), ctx.player(), InteractionHand.MAIN_HAND, stack, new BlockHitResult(new Vec3(ctx.player().position().x, ctx.player().position().y, ctx.player().position().z), Direction.UP, ctx.playerFeet(), false)) {}
                    )
                );
            if (itemState != null) {
                result.add(itemState);
            } else {
                result.add(Blocks.AIR.defaultBlockState());
            }
            // </toxic cloud>
        }
        return result;
    }

    private static boolean sameBlockstate(BlockState first, BlockState second) {
        return sameBlockstate(first, second, Baritone.settings().buildIgnoreDirection.value ? ORIENTATION_PROPS : Collections.<Property<?>>emptySet());
    }

    /**
     * @param alsoIgnore properties to waive on top of the {@link #derivedProperty derived} ones and
     *                   the ones named by {@code buildIgnoreProperties}
     */
    private static boolean sameBlockstate(BlockState first, BlockState second, Set<Property<?>> alsoIgnore) {
        if (first.getBlock() != second.getBlock()) {
            return false;
        }
        List<String> ignoredProps = Baritone.settings().buildIgnoreProperties.value;
        for (Property<?> prop : first.getProperties()) {
            if (!Objects.equals(first.getValue(prop), second.getValue(prop))
                    && !derivedProperty(first.getBlock(), prop)
                    && !alsoIgnore.contains(prop)
                    && !ignoredProps.contains(prop.getName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether an item that we estimate places {@code placeable} could produce {@code desired} if we
     * stood in the right spot and clicked the right face. Deliberately blind to orientation: this
     * answers "do I have the material", not "is the world correct". {@link #approxPlaceable} guesses
     * every item's state from one fixed pretend click, so it gets orientation wrong by construction,
     * and comparing it strictly makes the builder claim it is out of stairs while holding a stack.
     */
    private static boolean couldProduce(BlockState placeable, BlockState desired) {
        return sameBlockstate(placeable, desired, ORIENTATION_PROPS);
    }

    private static boolean containsCouldProduce(Collection<BlockState> states, BlockState desired) {
        if (states.contains(desired)) {
            return true; // block states are interned, so the common exact match costs a reference compare
        }
        for (BlockState testee : states) {
            if (couldProduce(testee, desired)) {
                return true;
            }
        }
        return false;
    }

    private static boolean valid(BlockState current, BlockState desired, boolean itemVerify) {
        if (desired == null) {
            return true;
        }
        if (current.getBlock() instanceof LiquidBlock && Baritone.settings().okIfWater.value) {
            return true;
        }
        if (current.getBlock() instanceof AirBlock && desired.getBlock() instanceof AirBlock) {
            return true;
        }
        if (current.getBlock() instanceof AirBlock && Baritone.settings().okIfAir.value.contains(desired.getBlock())) {
            return true;
        }
        if (desired.getBlock() instanceof AirBlock && Baritone.settings().buildIgnoreBlocks.value.contains(current.getBlock())) {
            return true;
        }
        if (!(current.getBlock() instanceof AirBlock) && Baritone.settings().buildIgnoreExisting.value && !itemVerify) {
            return true;
        }
        if (Baritone.settings().buildValidSubstitutes.value.getOrDefault(desired.getBlock(), Collections.emptyList()).contains(current.getBlock()) && !itemVerify) {
            return true;
        }
        if (current.equals(desired)) {
            return true;
        }
        return sameBlockstate(current, desired);
    }

    public class BuilderCalculationContext extends CalculationContext {

        private final List<BlockState> placeable;
        private final ISchematic schematic;
        private final int originX;
        private final int originY;
        private final int originZ;

        public BuilderCalculationContext() {
            super(BuilderProcess.this.baritone, true); // wew lad
            this.placeable = approxPlaceable(9);
            this.schematic = BuilderProcess.this.schematic;
            this.originX = origin.getX();
            this.originY = origin.getY();
            this.originZ = origin.getZ();

            this.jumpPenalty += 10;
            this.backtrackCostFavoringCoefficient = 1;
        }

        private BlockState getSchematic(int x, int y, int z, BlockState current) {
            if (schematic.inSchematic(x - originX, y - originY, z - originZ, current)) {
                return schematic.desiredState(x - originX, y - originY, z - originZ, current, BuilderProcess.this.approxPlaceable);
            } else {
                return null;
            }
        }

        @Override
        public double costOfPlacingAt(int x, int y, int z, BlockState current) {
            if (isPossiblyProtected(x, y, z) || !worldBorder.canPlaceAt(x, z)) { // make calculation fail properly if we can't build
                return COST_INF;
            }
            BlockState sch = getSchematic(x, y, z, current);
            if (sch != null) {
                // TODO this can return true even when allowPlace is off.... is that an issue?
                if (sch.getBlock() instanceof AirBlock) {
                    // we want this to be air, but they're asking if they can place here
                    // this won't be a schematic block, this will be a throwaway
                    return placeBlockCost * Baritone.settings().placeIncorrectBlockPenaltyMultiplier.value; // we're going to have to break it eventually
                }
                if (containsCouldProduce(placeable, sch)) {
                    return 0; // thats right we gonna make it FREE to place a block where it should go in a structure
                    // no place block penalty at all 😎
                    // i'm such an idiot that i just tried to copy and paste the epic gamer moment emoji too
                    // get added to unicode when?
                }
                if (!hasThrowaway) {
                    return COST_INF;
                }
                // we want it to be something that we don't have
                // even more of a pain to place something wrong
                return placeBlockCost * 1.5 * Baritone.settings().placeIncorrectBlockPenaltyMultiplier.value;
            } else {
                if (hasThrowaway) {
                    return placeBlockCost;
                } else {
                    return COST_INF;
                }
            }
        }

        @Override
        public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
            if ((!allowBreak && !allowBreakAnyway.contains(current.getBlock())) || isPossiblyProtected(x, y, z)) {
                return COST_INF;
            }
            BlockState sch = getSchematic(x, y, z, current);
            if (sch != null) {
                if (sch.getBlock() instanceof AirBlock) {
                    // it should be air
                    // regardless of current contents, we can break it
                    return 1;
                }
                // it should be a real block
                // is it already that block?
                if (valid(bsi.get0(x, y, z), sch, false)) {
                    return Baritone.settings().breakCorrectBlockPenaltyMultiplier.value;
                } else {
                    // can break if it's wrong
                    // would be great to return less than 1 here, but that would actually make the cost calculation messed up
                    // since we're breaking a block, if we underestimate the cost, then it'll fail when it really takes the correct amount of time
                    return 1;

                }
                // TODO do blocks in render distace only?
                // TODO allow breaking blocks that we have a tool to harvest and immediately place back?
            } else {
                return 1; // why not lol
            }
        }
    }
}
