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

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TorchGridSchematicTest {

    // resolved after the registries are up, not in a static initializer, which would run first
    private static BlockState AIR;
    private static BlockState TORCH;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        AIR = Blocks.AIR.defaultBlockState();
        TORCH = Blocks.TORCH.defaultBlockState();
    }

    private static TorchGridSchematic grid(int width, int height, int length, int spacing) {
        return new TorchGridSchematic(new FillSchematic(width, height, length, AIR), TORCH, spacing);
    }

    private static BlockState desired(ISchematic schematic, int x, int y, int z) {
        return schematic.desiredState(x, y, z, AIR, Collections.emptyList());
    }

    private static int countTorches(TorchGridSchematic schematic) {
        int count = 0;
        for (int x = 0; x < schematic.widthX(); x++) {
            for (int y = 0; y < schematic.heightY(); y++) {
                for (int z = 0; z < schematic.lengthZ(); z++) {
                    if (schematic.isTorch(x, y, z)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Test
    public void countTorchesMatchesAnActualScan() {
        int[][] cases = {{32, 9, 20, 8}, {15, 4, 15, 7}, {5, 2, 5, 7}, {1, 1, 1, 7}, {3, 1, 3, 1}, {40, 3, 7, 8}};
        for (int[] c : cases) {
            TorchGridSchematic schematic = grid(c[0], c[1], c[2], c[3]);
            assertEquals(
                    "counting " + c[0] + "x" + c[1] + "x" + c[2] + " at spacing " + c[3],
                    countTorches(schematic), schematic.countTorches()
            );
        }
    }

    @Test
    public void skippedColumnsGetNoTorchAndAreNotCounted() {
        TorchGridSchematic all = grid(15, 4, 15, 7);
        assertEquals(9, all.countTorches());
        assertEquals(TORCH, desired(all, 7, 0, 7));

        java.util.Set<Long> skip = new java.util.HashSet<>();
        skip.add(TorchGridSchematic.column(7, 7));
        skip.add(TorchGridSchematic.column(0, 0));
        TorchGridSchematic some = new TorchGridSchematic(
                new FillSchematic(15, 4, 15, AIR), TORCH, 7, skip);

        assertEquals(7, some.countTorches());
        assertEquals("skipped column falls through to the child", AIR, desired(some, 7, 0, 7));
        assertEquals(AIR, desired(some, 0, 0, 0));
        assertEquals("untouched grid positions still get torches", TORCH, desired(some, 14, 0, 14));
        assertFalse(some.isTorch(7, 0, 7));
        assertTrue("the raw grid is unchanged, only the exclusion differs", some.isGridColumn(7, 7));
    }

    @Test
    public void skippingEveryColumnLeavesAPlainClear() {
        java.util.Set<Long> skip = new java.util.HashSet<>();
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 15; z++) {
                skip.add(TorchGridSchematic.column(x, z));
            }
        }
        TorchGridSchematic none = new TorchGridSchematic(
                new FillSchematic(15, 4, 15, AIR), TORCH, 7, skip);
        assertEquals(0, none.countTorches());
        assertEquals(AIR, desired(none, 0, 0, 0));
    }

    @Test
    public void keepsTheChildDimensions() {
        TorchGridSchematic schematic = grid(5, 3, 9, 7);
        assertEquals(5, schematic.widthX());
        assertEquals(3, schematic.heightY());
        assertEquals(9, schematic.lengthZ());
    }

    @Test
    public void placesTorchesOnACenteredGridOnTheBottomLayer() {
        TorchGridSchematic schematic = grid(15, 4, 15, 7);
        // (15 - 1) % 7 == 0, so the grid starts flush at the corner
        assertEquals(TORCH, desired(schematic, 0, 0, 0));
        assertEquals(TORCH, desired(schematic, 7, 0, 7));
        assertEquals(TORCH, desired(schematic, 14, 0, 14));
        assertEquals(AIR, desired(schematic, 1, 0, 0));
        assertEquals(AIR, desired(schematic, 0, 0, 1));
        assertEquals(AIR, desired(schematic, 6, 0, 6));
        assertEquals(9, countTorches(schematic));
    }

    @Test
    public void leavesEveryLayerAboveTheFloorAlone() {
        TorchGridSchematic schematic = grid(15, 4, 15, 7);
        assertTrue(schematic.isTorch(0, 0, 0));
        assertFalse(schematic.isTorch(0, 1, 0));
        assertEquals(AIR, desired(schematic, 0, 1, 0));
        assertEquals(AIR, desired(schematic, 7, 3, 7));
    }

    @Test
    public void centersTheGridSoTheLeftoverSpaceIsSplit() {
        // 20 wide at spacing 7: (20 - 1) % 7 == 5, so start at 2 and leave 3 on the far side
        TorchGridSchematic schematic = grid(20, 1, 20, 7);
        assertEquals(TORCH, desired(schematic, 2, 0, 2));
        assertEquals(TORCH, desired(schematic, 9, 0, 16));
        assertEquals(AIR, desired(schematic, 0, 0, 0));
        assertEquals(AIR, desired(schematic, 19, 0, 19));
        assertEquals(9, countTorches(schematic));
    }

    @Test
    public void aRegionNarrowerThanTheSpacingStillGetsOneTorch() {
        TorchGridSchematic schematic = grid(5, 2, 5, 7);
        assertEquals(1, countTorches(schematic));
        assertEquals(TORCH, desired(schematic, 2, 0, 2));
    }

    @Test
    public void aSingleBlockSelectionGetsATorch() {
        TorchGridSchematic schematic = grid(1, 1, 1, 7);
        assertEquals(TORCH, desired(schematic, 0, 0, 0));
    }

    @Test
    public void treatsNonPositiveSpacingAsOne() {
        TorchGridSchematic schematic = grid(3, 1, 3, 0);
        assertEquals(9, countTorches(schematic));
        assertEquals(TORCH, desired(schematic, 1, 0, 2));
    }

    @Test
    public void delegatesToTheChildOutsideTheGrid() {
        TorchGridSchematic schematic = new TorchGridSchematic(
                new FillSchematic(9, 2, 9, Blocks.STONE.defaultBlockState()), TORCH, 7
        );
        assertEquals(Blocks.STONE.defaultBlockState(), desired(schematic, 1, 0, 1));
        assertEquals(Blocks.STONE.defaultBlockState(), desired(schematic, 1, 1, 1));
    }

    @Test
    public void delegatesInSchematicToTheChild() {
        TorchGridSchematic schematic = grid(4, 4, 4, 2);
        assertTrue(schematic.inSchematic(0, 0, 0, AIR));
        assertTrue(schematic.inSchematic(3, 3, 3, AIR));
        assertFalse(schematic.inSchematic(4, 0, 0, AIR));
        assertFalse(schematic.inSchematic(0, -1, 0, AIR));
    }
}
