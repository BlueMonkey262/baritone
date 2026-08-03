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
import baritone.api.schematic.MaskSchematic;
import baritone.api.schematic.TorchGridSchematic;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Reproduces the exact schematic chain that {@code #sel cleararea} with torchGrid builds, all the
 * way through the wrappers {@link BuilderProcess#build} applies, so we can see what the builder
 * actually asks for at a grid position without starting a client.
 */
public class TorchGridChainTest {

    private static BlockState AIR;
    private static BlockState TORCH;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        AIR = Blocks.AIR.defaultBlockState();
        TORCH = Blocks.TORCH.defaultBlockState();
    }

    /** The chain SelCommand builds, then the buildSkipBlocks mask BuilderProcess.build wraps it in. */
    private static ISchematic chain(int w, int h, int l, int spacing, List<net.minecraft.world.level.block.Block> skip) {
        ISchematic fill = new FillSchematic(w, h, l, AIR);
        ISchematic torched = new TorchGridSchematic(fill, TORCH, spacing);
        CompositeSchematic composite = new CompositeSchematic(0, 0, 0);
        composite.put(torched, 0, 0, 0);
        return new MaskSchematic(composite) {
            @Override
            protected boolean partOfMask(int x, int y, int z, BlockState current) {
                return !BuilderProcess.shouldSkip(
                        current,
                        this.desiredState(x, y, z, current, Collections.emptyList()),
                        skip,
                        Collections.emptyList()
                );
            }
        };
    }

    @Test
    public void gridPositionSurvivesTheWholeChain() {
        // the user's actual selection: roughly 32 x 9 x 20, spacing 8
        ISchematic s = chain(32, 9, 20, 8, Collections.emptyList());
        int startX = ((32 - 1) % 8) / 2; // 3
        int startZ = ((20 - 1) % 8) / 2; // 1
        assertEquals(TORCH, s.desiredState(startX, 0, startZ, AIR, Collections.emptyList()));
        assertTrue("grid position must be inside the schematic",
                s.inSchematic(startX, 0, startZ, AIR));
    }

    @Test
    public void buildSkipBlocksContainingTorchRemovesGridPositions() {
        ISchematic s = chain(32, 9, 20, 8, Collections.singletonList(Blocks.TORCH));
        int startX = ((32 - 1) % 8) / 2;
        int startZ = ((20 - 1) % 8) / 2;
        assertTrue("this is what broke it originally",
                !s.inSchematic(startX, 0, startZ, AIR));
    }

    /**
     * The deadlock: a placed torch is protected by blocksToDisallowBreaking, which is evaluated
     * against the current block, so the position it occupies leaves the schematic the instant it is
     * built. assemble() must treat that as out of bounds rather than as something to break.
     */
    @Test
    public void aPlacedTorchLeavesTheSchematicWhenItIsProtectedFromBreaking() {
        ISchematic s = new MaskSchematic(new TorchGridSchematic(
                new FillSchematic(32, 9, 20, AIR), TORCH, 8)) {
            @Override
            protected boolean partOfMask(int x, int y, int z, BlockState current) {
                return !BuilderProcess.shouldSkip(
                        current,
                        this.desiredState(x, y, z, current, Collections.emptyList()),
                        Collections.emptyList(),
                        Collections.singletonList(Blocks.TORCH) // blocksToDisallowBreaking
                );
            }
        };
        int startX = ((32 - 1) % 8) / 2;
        int startZ = ((20 - 1) % 8) / 2;
        // before it is built the position is ours to work on...
        assertTrue(s.inSchematic(startX, 0, startZ, AIR));
        // ...and the moment the torch exists it is not, which is what stranded the working set
        assertTrue("a built torch must drop out of the schematic here",
                !s.inSchematic(startX, 0, startZ, TORCH));
    }

    @Test
    public void airPositionsAreUnaffected() {
        ISchematic s = chain(32, 9, 20, 8, Collections.emptyList());
        assertEquals(AIR, s.desiredState(0, 0, 0, AIR, Collections.emptyList()));
        assertTrue(s.inSchematic(0, 0, 0, AIR));
    }
}
