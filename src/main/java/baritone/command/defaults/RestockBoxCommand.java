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

package baritone.command.defaults;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.cache.IRestockBox;
import baritone.api.cache.IRestockBoxCollection;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.utils.BetterBlockPos;
import baritone.Baritone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registers shulker boxes as material sources for {@code #build} to restock from.
 * <p>
 * Registrations are stored per dimension alongside waypoints, so they survive restarts and don't
 * leak between the overworld and the nether.
 */
public class RestockBoxCommand extends Command {

    /**
     * Upper bound on how many boxes a single radius scan will consider.
     */
    private static final int MAX_SCAN_RESULTS = 4096;

    /**
     * Upper bound on the block radius accepted by {@code #addbox}, so a typo cannot ask the
     * chunk scanner to materialise an unreasonably large search area.
     */
    private static final int MAX_SCAN_RADIUS = 512;

    public RestockBoxCommand(IBaritone baritone) {
        super(baritone, "addbox", "removebox", "listboxes", "indexboxes");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        IRestockBoxCollection boxes = boxes();
        switch (label.toLowerCase()) {
            case "addbox":
                addBox(args, boxes);
                break;
            case "removebox":
                removeBox(args, boxes);
                break;
            case "indexboxes":
                indexBoxes(args, boxes);
                break;
            case "listboxes":
            default:
                listBoxes(args, boxes);
                break;
        }
    }

    private void addBox(IArgConsumer args, IRestockBoxCollection boxes) throws CommandException {
        // A single numeric argument means "every shulker box within this many blocks" rather than
        // a coordinate -- a coordinate always needs all three. Handy for registering a whole
        // material depot in one go instead of looking at each box.
        if (args.hasExactlyOne()) {
            Integer radius = args.peekAsOrNull(Integer.class);
            if (radius != null) {
                args.get();
                addBoxesInRadius(boxes, radius);
                return;
            }
        }
        BetterBlockPos pos = resolvePosition(args);
        BlockState state = baritone.getPlayerContext().world().getBlockState(pos);
        if (!(state.getBlock() instanceof ShulkerBoxBlock)) {
            throw new CommandInvalidStateException("There is no shulker box at " + pos + " (found " + state.getBlock().getName().getString() + ")");
        }

        IRestockBox box = boxes.addBox(pos);

        // A newly registered box may hold something we previously gave up on, so allow those
        // materials to be retried.
        baritone.getRestockProcess().clearUnobtainable();

        if (box.isUnindexed()) {
            logDirect(String.format("Registered shulker box at %s (contents unknown; will be checked on first visit)", pos));
        } else {
            logDirect(String.format("Shulker box at %s was already registered", pos));
        }
    }

    /**
     * Kicks off an indexing run: walk to registered boxes and record what's actually inside.
     */
    private void indexBoxes(IArgConsumer args, IRestockBoxCollection boxes) throws CommandException {
        args.requireMax(1);
        boolean all = false;
        if (args.hasAny()) {
            String option = args.getString();
            if (!option.equalsIgnoreCase("all")) {
                throw new CommandInvalidStateException("Expected 'all' or no arguments, not '" + option + "'");
            }
            all = true;
            recheckMissingBoxes(boxes);
        }
        if (baritone.getRestockProcess().requestIndexing(all, this::builderWants)) {
            return; // the process logs its own progress
        }
        if (all) {
            logDirect("Nothing to index - no registered boxes are in range. Check #listboxes and restockMaxDistance.");
        } else {
            logDirect("Every registered box in range has already been indexed. Use '#indexboxes all' to re-check them.");
        }
    }

    /**
     * A manual indexing run can happen while a build is paused, so it uses the same conservative
     * inventory rule as a run the builder started itself rather than assuming every block is
     * disposable.
     */
    private boolean builderWants(ItemStack stack) {
        return !(stack.getItem() instanceof BlockItem)
                || baritone.getBuilderProcess().schematicWants(((BlockItem) stack.getItem()).getBlock());
    }

    /**
     * Registers every shulker box within the given block radius of the player.
     * <p>
     * Uses the same chunk scanner {@code #find} and {@code GetToBlockProcess} use, so this only
     * sees blocks in loaded chunks -- which is what we want, since a box we can't currently
     * observe is one we can't verify is really there.
     */
    private void addBoxesInRadius(IRestockBoxCollection boxes, int radius) throws CommandException {
        if (radius < 1) {
            throw new CommandInvalidStateException("Radius must be at least 1");
        }
        if (radius > MAX_SCAN_RADIUS) {
            logDirect(String.format("Radius %d is too large; scanning only %d blocks.", radius, MAX_SCAN_RADIUS));
            radius = MAX_SCAN_RADIUS;
        }
        // every shulker box variant, including the 16 dyed ones (and any added by mods)
        List<Block> shulkerBoxes = BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof ShulkerBoxBlock)
                .collect(Collectors.toList());

        // the scanner works in chunks, so round the block radius up and filter precisely after
        int chunkRadius = (radius >> 4) + 1;
        List<BlockPos> found = BaritoneAPI.getProvider().getWorldScanner()
                .scanChunkRadius(ctx, shulkerBoxes, MAX_SCAN_RESULTS, -1, chunkRadius);

        BetterBlockPos feet = ctx.playerFeet();
        double maxDistSq = (double) radius * radius;
        int alreadyKnown = 0;
        List<BetterBlockPos> newBoxes = new ArrayList<>();
        for (BlockPos raw : found) {
            BetterBlockPos pos = BetterBlockPos.from(raw);
            if (pos.distSqr(feet) > maxDistSq) {
                continue;
            }
            if (boxes.getBox(pos) != null) {
                alreadyKnown++;
                continue;
            }
            newBoxes.add(pos);
        }
        int added = boxes.addBoxes(newBoxes);

        if (added > 0) {
            // newly registered boxes may hold something we'd previously given up on
            baritone.getRestockProcess().clearUnobtainable();
        }
        if (added == 0 && alreadyKnown == 0) {
            logDirect(String.format("No shulker boxes found within %d blocks. Note that only loaded chunks are scanned.", radius));
            return;
        }
        logDirect(String.format("Registered %d new shulker box(es) within %d blocks (%d already registered).",
                added, radius, alreadyKnown));
        logDirect("Contents will be indexed the first time each one is opened. Use #listboxes to review.");
    }

    /**
     * A missing flag is only meaningful while the registered chunk is unloaded. A manual full
     * indexing run can therefore give a box another chance once its chunk is loaded and the block
     * at the saved position is a shulker box; the indexing process will verify the live menu before
     * recording its contents.
     */
    private void recheckMissingBoxes(IRestockBoxCollection boxes) {
        Baritone implementation = (Baritone) baritone;
        if (implementation.bsi == null) {
            return;
        }
        for (IRestockBox box : boxes.getAllBoxes()) {
            BetterBlockPos pos = box.getLocation();
            if (!box.isMissing() || !implementation.bsi.worldContainsLoadedChunk(pos.x, pos.z)) {
                continue;
            }
            if (implementation.bsi.get0(pos).getBlock() instanceof ShulkerBoxBlock) {
                boxes.setMissing(pos, false);
            }
        }
    }

    private void removeBox(IArgConsumer args, IRestockBoxCollection boxes) throws CommandException {
        BetterBlockPos pos = resolvePosition(args);
        if (boxes.removeBox(pos)) {
            logDirect("Deregistered shulker box at " + pos);
        } else {
            throw new CommandInvalidStateException("No shulker box is registered at " + pos);
        }
    }

    private void listBoxes(IArgConsumer args, IRestockBoxCollection boxes) throws CommandException {
        args.requireMax(0);
        List<IRestockBox> all = new ArrayList<>(boxes.getAllBoxes());
        if (all.isEmpty()) {
            logDirect("No shulker boxes registered in this dimension. Look at one and run #addbox.");
            return;
        }
        BetterBlockPos feet = ctx.playerFeet();
        all.sort(Comparator.comparingDouble(box -> box.getLocation().distSqr(feet)));
        logDirect(all.size() + " registered shulker box(es):");
        for (IRestockBox box : all) {
            StringBuilder line = new StringBuilder();
            line.append(box.getLocation());
            if (box.isMissing()) {
                // flagged rather than deleted on purpose; an unloaded chunk looks the same as a
                // broken box from a distance, so removal is left to the player
                line.append(" - MISSING (box not found at these coords)");
            } else if (box.isUnindexed()) {
                line.append(" - contents unknown");
            } else {
                line.append(" - ");
                line.append(box.getContents().entrySet().stream()
                        .sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
                        .limit(5)
                        .map(e -> e.getValue() + "x " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("empty"));
            }
            logDirect(line.toString());
        }
    }

    /**
     * Resolves the target box: either explicit coordinates, or whatever the player is looking at.
     * Looking at the block is the normal path, and matches how the rest of Baritone identifies a
     * block the player means.
     */
    private BetterBlockPos resolvePosition(IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            args.requireMax(3);
            return args.getDatatypePost(RelativeBlockPos.INSTANCE, ctx.playerFeet());
        }
        BlockPos selected = ctx.getSelectedBlock().orElse(null);
        if (selected == null) {
            throw new CommandInvalidStateException("You aren't looking at a block. Look at a shulker box, or give coordinates.");
        }
        return BetterBlockPos.from(selected);
    }

    private IRestockBoxCollection boxes() throws CommandException {
        if (baritone.getWorldProvider() == null || baritone.getWorldProvider().getCurrentWorld() == null) {
            throw new CommandInvalidStateException("No world data is loaded yet");
        }
        return baritone.getWorldProvider().getCurrentWorld().getRestockBoxes();
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (label.equalsIgnoreCase("listboxes")) {
            return Stream.empty();
        }
        if (label.equalsIgnoreCase("indexboxes")) {
            if (args.hasExactlyOne()) {
                return new TabCompleteHelper()
                        .append("all")
                        .filterPrefix(args.peekString())
                        .stream();
            }
            return args.hasAny() ? Stream.empty() : Stream.of("all");
        }
        return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return "Register shulker boxes to restock from while building";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Registers shulker boxes that #build can automatically restock from when it runs",
                "out of a material, instead of stalling.",
                "",
                "Baritone walks to the box, opens it with a real right-click, takes what it needs,",
                "and closes it again, so the server's normal reach rules apply throughout.",
                "",
                "If a box turns out not to have the material any more, the next registered box that",
                "does is tried instead. If none have it, those blocks are skipped and the rest of",
                "the schematic still gets built.",
                "",
                "Registrations are saved per dimension and survive restarts.",
                "",
                "Usage:",
                "> addbox - Register the shulker box you're looking at",
                "> addbox <radius> - Register every shulker box within <radius> blocks",
                "> addbox <x> <y> <z> - Register the shulker box at a position",
                "> removebox - Deregister the shulker box you're looking at",
                "> removebox <x> <y> <z> - Deregister the shulker box at a position",
                "> listboxes - List every registered box and what it last held",
                "> indexboxes - Visit any box whose contents are unknown and record them",
                "> indexboxes all - Re-check every registered box, even known ones"
        );
    }
}
