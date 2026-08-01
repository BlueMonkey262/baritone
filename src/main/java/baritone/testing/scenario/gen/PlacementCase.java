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

package baritone.testing.scenario.gen;

import baritone.utils.schematic.StaticSchematic;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * One isolated placement assertion: one target state and only the blocks needed to click it.
 *
 * <p>Offsets are relative to the target. The support blocks are deliberately part of the
 * schematic as well as the staged world, so the builder sees them as already-correct work.</p>
 */
public final class PlacementCase {

    private final String family;
    private final String name;
    private final String description;
    private final String itemId;
    private final int targetY;
    private final BlockState targetState;
    private final List<Support> supports;

    public PlacementCase(String family, String name, String description, String itemId,
                         BlockState targetState, List<Support> supports) {
        this(family, name, description, itemId, 0, targetState, supports);
    }

    public PlacementCase(String family, String name, String description, String itemId, int targetY,
                         BlockState targetState, List<Support> supports) {
        this.family = family;
        this.name = name;
        this.description = description;
        this.itemId = itemId;
        this.targetY = targetY;
        this.targetState = targetState;
        this.supports = List.copyOf(supports);
    }

    public String family() {
        return this.family;
    }

    public String name() {
        return this.name;
    }

    public String description() {
        return this.description;
    }

    public String itemId() {
        return this.itemId;
    }

    /** Arena-relative target height; zero is the ordinary standing-level target. */
    public int targetY() {
        return this.targetY;
    }

    public BlockState targetState() {
        return this.targetState;
    }

    public List<Support> supports() {
        return this.supports;
    }

    /**
     * Builds a schematic whose local origin is its minimum support/target corner. The caller must
     * place that origin at target + (minX, minY, minZ).
     */
    public StaticSchematic schematic() {
        int minX = minX();
        int maxX = maxX();
        int minY = minY();
        int maxY = maxY();
        int minZ = minZ();
        int maxZ = maxZ();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[][][] states = new BlockState[maxX - minX + 1][maxZ - minZ + 1][maxY - minY + 1];
        for (int x = 0; x < states.length; x++) {
            for (int z = 0; z < states[x].length; z++) {
                for (int y = 0; y < states[x][z].length; y++) {
                    states[x][z][y] = air;
                }
            }
        }
        states[-minX][-minZ][-minY] = this.targetState;
        for (Support support : this.supports) {
            Vec3i offset = support.offset();
            states[offset.getX() - minX][offset.getZ() - minZ][offset.getY() - minY] = support.state();
        }
        return new StaticSchematic(states);
    }

    public int minX() {
        return Math.min(0, this.supports.stream().mapToInt(s -> s.offset().getX()).min().orElse(0));
    }

    public int maxX() {
        return Math.max(0, this.supports.stream().mapToInt(s -> s.offset().getX()).max().orElse(0));
    }

    public int minY() {
        return Math.min(0, this.supports.stream().mapToInt(s -> s.offset().getY()).min().orElse(0));
    }

    public int maxY() {
        return Math.max(0, this.supports.stream().mapToInt(s -> s.offset().getY()).max().orElse(0));
    }

    public int minZ() {
        return Math.min(0, this.supports.stream().mapToInt(s -> s.offset().getZ()).min().orElse(0));
    }

    public int maxZ() {
        return Math.max(0, this.supports.stream().mapToInt(s -> s.offset().getZ()).max().orElse(0));
    }

    /** A block that must remain present for a real right-click placement. */
    public record Support(Vec3i offset, BlockState state) {
        public Support {
            if (offset.equals(Vec3i.ZERO)) {
                throw new IllegalArgumentException("a support cannot occupy the target cell");
            }
        }
    }
}
