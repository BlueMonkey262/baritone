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

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.ForAxis;
import baritone.api.command.datatypes.ForBlockOptionalMeta;
import baritone.api.command.datatypes.ForDirection;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.schematic.*;
import baritone.api.schematic.mask.shape.CylinderMask;
import baritone.api.schematic.mask.shape.SphereMask;
import baritone.api.selection.ISelection;
import baritone.api.selection.ISelectionManager;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.utils.BlockStateInterface;
import baritone.utils.IRenderer;
import baritone.utils.schematic.StaticSchematic;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class SelCommand extends Command {

    private ISelectionManager manager = baritone.getSelectionManager();
    private BetterBlockPos pos1 = null;
    private ISchematic clipboard = null;
    private Vec3i clipboardOffset = null;

    public SelCommand(IBaritone baritone) {
        super(baritone, "sel", "selection", "s");
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onRenderPass(RenderEvent event) {
                if (!Baritone.settings().renderSelectionCorners.value || pos1 == null) {
                    return;
                }
                Color color = Baritone.settings().colorSelectionPos1.value;
                float opacity = Baritone.settings().selectionOpacity.value;
                float lineWidth = Baritone.settings().selectionLineWidth.value;
                boolean ignoreDepth = Baritone.settings().renderSelectionIgnoreDepth.value;
                IRenderer.startLines(color, opacity, lineWidth, ignoreDepth);
                IRenderer.emitAABB(event.getModelViewStack(), new AABB(pos1, pos1.offset(1, 1, 1)));
                IRenderer.endLines(ignoreDepth);
            }
        });
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Action action = Action.getByName(args.getString());
        if (action == null) {
            throw new CommandInvalidTypeException(args.consumed(), "an action");
        }
        if (action == Action.POS1 || action == Action.POS2) {
            if (action == Action.POS2 && pos1 == null) {
                throw new CommandInvalidStateException("Set pos1 first before using pos2");
            }
            BetterBlockPos playerPos = ctx.viewerPos();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
            args.requireMax(0);
            if (action == Action.POS1) {
                pos1 = pos;
                logDirect("Position 1 has been set");
            } else {
                manager.addSelection(pos1, pos);
                pos1 = null;
                logDirect("Selection added");
            }
        } else if (action == Action.CLEAR) {
            args.requireMax(0);
            pos1 = null;
            logDirect(String.format("Removed %d selections", manager.removeAllSelections().length));
        } else if (action == Action.UNDO) {
            args.requireMax(0);
            if (pos1 != null) {
                pos1 = null;
                logDirect("Undid pos1");
            } else {
                ISelection[] selections = manager.getSelections();
                if (selections.length < 1) {
                    throw new CommandInvalidStateException("Nothing to undo!");
                } else {
                    pos1 = manager.removeSelection(selections[selections.length - 1]).pos1();
                    logDirect("Undid pos2");
                }
            }
        } else if (action.isFillAction()) {
            BlockOptionalMeta type = action == Action.CLEARAREA
                    ? new BlockOptionalMeta(Blocks.AIR)
                    : args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);

            final BlockOptionalMetaLookup replaces; // Action.REPLACE
            final Direction.Axis alignment;         // Action.(H)CYLINDER
            if (action == Action.REPLACE) {
                args.requireMin(1);
                List<BlockOptionalMeta> replacesList = new ArrayList<>();
                replacesList.add(type);
                while (args.has(2)) {
                    replacesList.add(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
                }
                type = args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
                replaces = new BlockOptionalMetaLookup(replacesList.toArray(new BlockOptionalMeta[0]));
                alignment = null;
            } else if (action == Action.CYLINDER || action == Action.HCYLINDER) {
                args.requireMax(1);
                alignment = args.hasAny() ? args.getDatatypeFor(ForAxis.INSTANCE) : Direction.Axis.Y;
                replaces = null;
            } else {
                args.requireMax(0);
                replaces = null;
                alignment = null;
            }
            ISelection[] selections = manager.getSelections();
            if (selections.length == 0) {
                throw new CommandInvalidStateException("No selections");
            }
            int torchCount = 0;
            int skippedTorches = 0;
            BetterBlockPos origin = selections[0].min();
            CompositeSchematic composite = new CompositeSchematic(0, 0, 0);
            for (ISelection selection : selections) {
                BetterBlockPos min = selection.min();
                origin = new BetterBlockPos(
                        Math.min(origin.x, min.x),
                        Math.min(origin.y, min.y),
                        Math.min(origin.z, min.z)
                );
            }
            for (ISelection selection : selections) {
                Vec3i size = selection.size();
                BetterBlockPos min = selection.min();

                // Java 8 so no switch expressions 😿
                UnaryOperator<ISchematic> create = fill -> {
                    final int w = fill.widthX();
                    final int h = fill.heightY();
                    final int l = fill.lengthZ();

                    switch (action) {
                        case WALLS:
                            return new WallsSchematic(fill);
                        case SHELL:
                            return new ShellSchematic(fill);
                        case REPLACE:
                            return new ReplaceSchematic(fill, replaces);
                        case SPHERE:
                            return MaskSchematic.create(fill, new SphereMask(w, h, l, true).compute());
                        case HSPHERE:
                            return MaskSchematic.create(fill, new SphereMask(w, h, l, false).compute());
                        case CYLINDER:
                            return MaskSchematic.create(fill, new CylinderMask(w, h, l, true, alignment).compute());
                        case HCYLINDER:
                            return MaskSchematic.create(fill, new CylinderMask(w, h, l, false, alignment).compute());
                        default:
                            // Silent fail
                            return fill;
                    }
                };

                ISchematic schematic = create.apply(new FillSchematic(size.getX(), size.getY(), size.getZ(), type));
                if (action == Action.CLEARAREA && Baritone.settings().torchGrid.value) {
                    int spacing = Baritone.settings().torchGridSpacing.value;
                    // the ungated grid first, purely to enumerate the candidate columns, then the
                    // real one with the ones the world has already lit taken out
                    TorchGridSchematic candidates =
                            new TorchGridSchematic(schematic, Blocks.TORCH.defaultBlockState(), spacing);
                    TorchGridSchematic torched = new TorchGridSchematic(
                            schematic,
                            Blocks.TORCH.defaultBlockState(),
                            spacing,
                            columnsAlreadyLit(candidates, min)
                    );
                    torchCount += torched.countTorches();
                    skippedTorches += candidates.countTorches() - torched.countTorches();
                    schematic = torched;
                }
                composite.put(schematic, min.x - origin.x, min.y - origin.y, min.z - origin.z);
            }
            baritone.getBuilderProcess().build("Fill", composite, origin);
            if (action == Action.CLEARAREA && Baritone.settings().torchGrid.value) {
                logDirect(String.format(
                        "Filling now, with %d torches every %d blocks%s",
                        torchCount, Math.max(1, Baritone.settings().torchGridSpacing.value),
                        skippedTorches > 0
                                ? String.format(" (%d skipped, already lit)", skippedTorches)
                                : ""
                ));
                warnAboutTorchGrid(torchCount, skippedTorches);
            } else {
                logDirect("Filling now");
            }
        } else if (action == Action.COPY) {
            BetterBlockPos playerPos = ctx.viewerPos();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
            args.requireMax(0);
            ISelection[] selections = manager.getSelections();
            if (selections.length < 1) {
                throw new CommandInvalidStateException("No selections");
            }
            BlockStateInterface bsi = new BlockStateInterface(ctx);
            BetterBlockPos origin = selections[0].min();
            CompositeSchematic composite = new CompositeSchematic(0, 0, 0);
            for (ISelection selection : selections) {
                BetterBlockPos min = selection.min();
                origin = new BetterBlockPos(
                        Math.min(origin.x, min.x),
                        Math.min(origin.y, min.y),
                        Math.min(origin.z, min.z)
                );
            }
            for (ISelection selection : selections) {
                Vec3i size = selection.size();
                BetterBlockPos min = selection.min();
                BlockState[][][] blockstates = new BlockState[size.getX()][size.getZ()][size.getY()];
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            blockstates[x][z][y] = bsi.get0(min.x + x, min.y + y, min.z + z);
                        }
                    }
                }
                ISchematic schematic = new StaticSchematic(blockstates);
                composite.put(schematic, min.x - origin.x, min.y - origin.y, min.z - origin.z);
            }
            clipboard = composite;
            clipboardOffset = origin.subtract(pos);
            logDirect("Selection copied");
        } else if (action == Action.PASTE) {
            BetterBlockPos playerPos = ctx.viewerPos();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
            args.requireMax(0);
            if (clipboard == null) {
                throw new CommandInvalidStateException("You need to copy a selection first");
            }
            baritone.getBuilderProcess().build("Fill", clipboard, pos.offset(clipboardOffset));
            logDirect("Building now");
        } else if (action == Action.EXPAND || action == Action.CONTRACT || action == Action.SHIFT) {
            args.requireExactly(3);
            TransformTarget transformTarget = TransformTarget.getByName(args.getString());
            if (transformTarget == null) {
                throw new CommandInvalidStateException("Invalid transform type");
            }
            Direction direction = args.getDatatypeFor(ForDirection.INSTANCE);
            int blocks = args.getAs(Integer.class);
            ISelection[] selections = manager.getSelections();
            if (selections.length < 1) {
                throw new CommandInvalidStateException("No selections found");
            }
            selections = transformTarget.transform(selections);
            for (ISelection selection : selections) {
                if (action == Action.EXPAND) {
                    manager.expand(selection, direction, blocks);
                } else if (action == Action.CONTRACT) {
                    manager.contract(selection, direction, blocks);
                } else {
                    manager.shift(selection, direction, blocks);
                }
            }
            logDirect(String.format("Transformed %d selections", selections.length));
        }
    }

    /**
     * Which grid columns already have a torch near them in the world, and so should be left alone.
     * <p>
     * A schematic cannot see the world, so this is worked out once here rather than per query. Only
     * chunks that are loaded or in Baritone's cache can be read, so a torch in unloaded terrain
     * won't be noticed and that column gets a second torch; the alternative is refusing to run at
     * all away from the player, which is worse.
     *
     * @param candidates The ungated grid, used only to enumerate the positions worth testing
     * @param min        World position of the schematic's (0, 0, 0)
     */
    private Set<Long> columnsAlreadyLit(TorchGridSchematic candidates, BetterBlockPos min) {
        int radius = Baritone.settings().torchGridAvoidExistingRadius.value;
        if (radius <= 0) {
            return Collections.emptySet();
        }
        BlockStateInterface bsi = new BlockStateInterface(ctx);
        Set<Long> skipped = new HashSet<>();
        int radiusSq = radius * radius;
        for (int x = 0; x < candidates.widthX(); x++) {
            for (int z = 0; z < candidates.lengthZ(); z++) {
                if (!candidates.isGridColumn(x, z)) {
                    continue;
                }
                if (hasTorchWithin(bsi, min.x + x, min.y, min.z + z, radius, radiusSq)) {
                    skipped.add(TorchGridSchematic.column(x, z));
                }
            }
        }
        return skipped;
    }

    private static boolean hasTorchWithin(BlockStateInterface bsi, int wx, int wy, int wz, int radius, int radiusSq) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) {
                        continue; // a true radius, not the corner-inclusive cube
                    }
                    if (isTorch(bsi.get0(wx + dx, wy + dy, wz + dz).getBlock())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Torches that light, in both their standing and wall forms. Redstone torches are deliberately
     * excluded: they are usually wiring rather than lighting, and they barely light anything.
     */
    private static boolean isTorch(net.minecraft.world.level.block.Block block) {
        return block == Blocks.TORCH || block == Blocks.WALL_TORCH
                || block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH;
    }

    /**
     * Says up front why a torch grid is going to place nothing, rather than letting the build run to
     * completion and leave the room dark with no explanation. Every one of these is silent inside the
     * builder: a masked-out position never enters the working set, and material we're short of is
     * only reported once there is nothing left to break.
     */
    private void warnAboutTorchGrid(int torchCount, int skippedTorches) {
        if (torchCount == 0) {
            logDirect(skippedTorches > 0
                    ? "...but every grid position is already lit, so no torches are needed."
                    : "...but the selection is too small for any torch. Lower torchGridSpacing.");
            return;
        }
        if (Baritone.settings().buildSkipBlocks.value.contains(Blocks.TORCH)) {
            logDirect("WARNING: buildSkipBlocks contains torch, so every torch counts as already "
                    + "placed and none will be built. Remove it with 'set buildSkipBlocks'.");
        }
        int carried = 0;
        for (ItemStack stack : ctx.player().getInventory().items) {
            if (stack.getItem() == Items.TORCH) {
                carried += stack.getCount();
            }
        }
        if (carried == 0) {
            logDirect("WARNING: no torches in your inventory, so none will be placed.");
        } else if (carried < torchCount) {
            logDirect(String.format("Note: %d torches carried, %d wanted.", carried, torchCount));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .append(Action.getAllNames())
                    .filterPrefix(args.getString())
                    .sortAlphabetically()
                    .stream();
        } else {
            Action action = Action.getByName(args.getString());
            if (action != null) {
                if (action == Action.POS1 || action == Action.POS2) {
                    if (args.hasAtMost(3)) {
                        return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
                    }
                } else if (action.isFillAction()) {
                    if (args.hasExactlyOne() || action == Action.REPLACE) {
                        while (args.has(2)) {
                            args.get();
                        }
                        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
                    } else if (args.hasExactly(2) && (action == Action.CYLINDER || action == Action.HCYLINDER)) {
                        args.get();
                        return args.tabCompleteDatatype(ForAxis.INSTANCE);
                    }
                } else if (action == Action.EXPAND || action == Action.CONTRACT || action == Action.SHIFT) {
                    if (args.hasExactlyOne()) {
                        return new TabCompleteHelper()
                                .append(TransformTarget.getAllNames())
                                .filterPrefix(args.getString())
                                .sortAlphabetically()
                                .stream();
                    } else {
                        TransformTarget target = TransformTarget.getByName(args.getString());
                        if (target != null && args.hasExactlyOne()) {
                            return args.tabCompleteDatatype(ForDirection.INSTANCE);
                        }
                    }
                }
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "WorldEdit-like commands";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The sel command allows you to manipulate Baritone's selections, similarly to WorldEdit.",
                "",
                "Using these selections, you can clear areas, fill them with blocks, or something else.",
                "",
                "The expand/contract/shift commands use a kind of selector to choose which selections to target. Supported ones are a/all, n/newest, and o/oldest.",
                "",
                "Usage:",
                "> sel pos1/p1/1 - Set position 1 to your current position.",
                "> sel pos1/p1/1 <x> <y> <z> - Set position 1 to a relative position.",
                "> sel pos2/p2/2 - Set position 2 to your current position.",
                "> sel pos2/p2/2 <x> <y> <z> - Set position 2 to a relative position.",
                "",
                "> sel clear/c - Clear the selection.",
                "> sel undo/u - Undo the last action (setting positions, creating selections, etc.)",
                "> sel set/fill/s/f [block] - Completely fill all selections with a block.",
                "> sel walls/w [block] - Fill in the walls of the selection with a specified block.",
                "> sel shell/shl [block] - The same as walls, but fills in a ceiling and floor too.",
                "> sel sphere/sph [block] - Fills the selection with a sphere bounded by the sides.",
                "> sel hsphere/hsph [block] - The same as sphere, but hollow.",
                "> sel cylinder/cyl [block] <axis> - Fills the selection with a cylinder bounded by the sides, oriented about the given axis. (default=y)",
                "> sel hcylinder/hcyl [block] <axis> - The same as cylinder, but hollow.",
                "> sel cleararea/ca - Basically 'set air'.",
                "> sel replace/r <blocks...> <with> - Replaces blocks with another block.",
                "> sel copy/cp <x> <y> <z> - Copy the selected area relative to the specified or your position.",
                "> sel paste/p <x> <y> <z> - Build the copied area relative to the specified or your position.",
                "",
                "> sel expand <target> <direction> <blocks> - Expand the targets.",
                "> sel contract <target> <direction> <blocks> - Contract the targets.",
                "> sel shift <target> <direction> <blocks> - Shift the targets (does not resize)."
        );
    }

    enum Action {
        POS1("pos1", "p1", "1"),
        POS2("pos2", "p2", "2"),
        CLEAR("clear", "c"),
        UNDO("undo", "u"),
        SET("set", "fill", "s", "f"),
        WALLS("walls", "w"),
        SHELL("shell", "shl"),
        SPHERE("sphere", "sph"),
        HSPHERE("hsphere", "hsph"),
        CYLINDER("cylinder", "cyl"),
        HCYLINDER("hcylinder", "hcyl"),
        CLEARAREA("cleararea", "ca"),
        REPLACE("replace", "r"),
        EXPAND("expand", "ex"),
        COPY("copy", "cp"),
        PASTE("paste", "p"),
        CONTRACT("contract", "ct"),
        SHIFT("shift", "sh");
        private final String[] names;

        Action(String... names) {
            this.names = names;
        }

        public static Action getByName(String name) {
            for (Action action : Action.values()) {
                for (String alias : action.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return action;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (Action action : Action.values()) {
                names.addAll(Arrays.asList(action.names));
            }
            return names.toArray(new String[0]);
        }

        public final boolean isFillAction() {
            return this == SET
                    || this == WALLS
                    || this == SHELL
                    || this == SPHERE
                    || this == HSPHERE
                    || this == CYLINDER
                    || this == HCYLINDER
                    || this == CLEARAREA
                    || this == REPLACE;
        }
    }

    enum TransformTarget {
        ALL(sels -> sels, "all", "a"),
        NEWEST(sels -> new ISelection[]{sels[sels.length - 1]}, "newest", "n"),
        OLDEST(sels -> new ISelection[]{sels[0]}, "oldest", "o");
        private final Function<ISelection[], ISelection[]> transform;
        private final String[] names;

        TransformTarget(Function<ISelection[], ISelection[]> transform, String... names) {
            this.transform = transform;
            this.names = names;
        }

        public ISelection[] transform(ISelection[] selections) {
            return transform.apply(selections);
        }

        public static TransformTarget getByName(String name) {
            for (TransformTarget target : TransformTarget.values()) {
                for (String alias : target.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return target;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (TransformTarget target : TransformTarget.values()) {
                names.addAll(Arrays.asList(target.names));
            }
            return names.toArray(new String[0]);
        }
    }
}
