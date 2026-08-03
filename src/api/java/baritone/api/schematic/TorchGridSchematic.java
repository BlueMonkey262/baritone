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

package baritone.api.schematic;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wraps another schematic and overrides a regular grid of positions on its bottom layer with a
 * light source, so that clearing out a big room also lights it.
 * <p>
 * The grid is anchored per schematic rather than to world coordinates, and is centred within each
 * horizontal dimension. That means a selection smaller than the spacing still gets one torch in the
 * middle instead of none at all, which is what you want when you clear a small room and expect it
 * not to be pitch black.
 * <p>
 * Only the bottom layer is used, and only standing torches: the builder works out a block's
 * orientation by simulating a click on the top face of the block below, so a wall torch is a state
 * it can never produce and would retry forever.
 */
public class TorchGridSchematic extends AbstractSchematic {

    private final ISchematic child;
    private final BlockState torch;
    private final int spacing;
    private final int startX;
    private final int startZ;
    /** Grid columns dropped because the world already has a torch near them. */
    private final Set<Long> skippedColumns;

    /**
     * @param child   The schematic to decorate; its dimensions are used as-is
     * @param torch   The state to place at grid positions
     * @param spacing Blocks between torches along each horizontal axis, at least 1
     */
    public TorchGridSchematic(ISchematic child, BlockState torch, int spacing) {
        this(child, torch, spacing, Collections.emptySet());
    }

    /**
     * @param skippedColumns Grid columns to leave alone, keyed by {@link #column}. The caller works
     *                       these out against the world, which a schematic cannot see; passing them
     *                       in keeps the decision made once, up front, rather than per query.
     */
    public TorchGridSchematic(ISchematic child, BlockState torch, int spacing, Set<Long> skippedColumns) {
        super(child.widthX(), child.heightY(), child.lengthZ());
        this.child = child;
        this.torch = torch;
        this.spacing = Math.max(1, spacing);
        this.startX = start(child.widthX(), this.spacing);
        this.startZ = start(child.lengthZ(), this.spacing);
        this.skippedColumns = skippedColumns.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(skippedColumns);
    }

    /**
     * @return A key identifying a horizontal position, for {@link #skippedColumns}
     */
    public static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * Whether this horizontal position is on the raw grid, before any world-derived exclusions.
     * Callers use this to enumerate the candidate positions they then test against the world.
     */
    public boolean isGridColumn(int x, int z) {
        return x >= startX && (x - startX) % spacing == 0
                && z >= startZ && (z - startZ) % spacing == 0;
    }

    /**
     * The offset of the first torch along an axis, chosen so the leftover space is split evenly
     * between the two ends. Always within the schematic when {@code size >= 1}, so every selection
     * gets at least one torch.
     */
    private static int start(int size, int spacing) {
        if (size < 1) {
            return 0;
        }
        return ((size - 1) % spacing) / 2;
    }

    /**
     * @return Whether this position is a grid position on the bottom layer
     */
    public boolean isTorch(int x, int y, int z) {
        return y == 0
                && isGridColumn(x, z)
                && !skippedColumns.contains(column(x, z));
    }

    /**
     * @return How many torches this grid asks for, so the caller can say so up front rather than
     * leaving you to work out from an unlit room that nothing was placed
     */
    public int countTorches() {
        if (widthX() < 1 || heightY() < 1 || lengthZ() < 1) {
            return 0;
        }
        int count = 0;
        for (int x = startX; x < widthX(); x += spacing) {
            for (int z = startZ; z < lengthZ(); z += spacing) {
                if (!skippedColumns.contains(column(x, z))) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public boolean inSchematic(int x, int y, int z, BlockState currentState) {
        return child.inSchematic(x, y, z, currentState);
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        if (isTorch(x, y, z)) {
            return torch;
        }
        return child.desiredState(x, y, z, current, approxPlaceable);
    }

    @Override
    public void reset() {
        child.reset();
    }
}
