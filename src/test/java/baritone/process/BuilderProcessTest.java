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

import baritone.api.schematic.CompositeSchematic;
import baritone.api.schematic.FillSchematic;
import baritone.api.schematic.ISchematic;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BuilderProcessTest {

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void skipsCurrentProtectedBlocksWhenClearing() {
        assertTrue(BuilderProcess.shouldSkip(
                Blocks.CHEST.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                Collections.singleton(Blocks.CHEST),
                Collections.emptySet()
        ));
        assertTrue(BuilderProcess.shouldSkip(
                Blocks.FURNACE.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                Collections.emptySet(),
                Collections.singleton(Blocks.FURNACE)
        ));
    }

    @Test
    public void retainsDesiredBlockSkipBehaviorAndAvoidBreakingDoesNotProtect() {
        assertTrue(BuilderProcess.shouldSkip(
                Blocks.STONE.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(),
                Collections.singleton(Blocks.CHEST),
                Collections.emptySet()
        ));
        assertFalse(BuilderProcess.shouldSkip(
                Blocks.CHEST.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                Collections.emptySet(),
                Collections.emptySet()
        ));
    }

    @Test
    public void protectsSkippedBlocksOnlyInsideThePreSkipCompositeShape() {
        CompositeSchematic selections = new CompositeSchematic(0, 0, 0);
        selections.put(new FillSchematic(1, 1, 1, Blocks.AIR.defaultBlockState()), 0, 0, 0);
        selections.put(new FillSchematic(1, 1, 1, Blocks.AIR.defaultBlockState()), 3, 0, 0);

        BlockState skipped = Blocks.CHEST.defaultBlockState();
        assertTrue(BuilderProcess.isBuildSkipBlockProtected(
                selections, 10, 20, 30, 10, 20, 30, skipped, Collections.singleton(Blocks.CHEST)
        ));
        assertFalse(BuilderProcess.isBuildSkipBlockProtected(
                selections, 10, 20, 30, 11, 20, 30, skipped, Collections.singleton(Blocks.CHEST)
        ));
        assertFalse(BuilderProcess.isBuildSkipBlockProtected(
                selections, 10, 20, 30, 14, 20, 30, skipped, Collections.singleton(Blocks.CHEST)
        ));
    }

    @Test
    public void protectsSkippedBlocksOnlyInTheActiveLayer() {
        ISchematic activeLayer = new ISchematic() {
            @Override
            public BlockState desiredState(int x, int y, int z, BlockState current, java.util.List<BlockState> approxPlaceable) {
                return Blocks.AIR.defaultBlockState();
            }

            @Override
            public boolean inSchematic(int x, int y, int z, BlockState currentState) {
                return ISchematic.super.inSchematic(x, y, z, currentState) && y == 0;
            }

            @Override
            public int widthX() {
                return 1;
            }

            @Override
            public int heightY() {
                return 2;
            }

            @Override
            public int lengthZ() {
                return 1;
            }
        };

        assertTrue(BuilderProcess.isBuildSkipBlockProtected(
                activeLayer, 0, 0, 0, 0, 0, 0, Blocks.CHEST.defaultBlockState(), Collections.singleton(Blocks.CHEST)
        ));
        assertFalse(BuilderProcess.isBuildSkipBlockProtected(
                activeLayer, 0, 0, 0, 0, 1, 0, Blocks.CHEST.defaultBlockState(), Collections.singleton(Blocks.CHEST)
        ));
    }

}
