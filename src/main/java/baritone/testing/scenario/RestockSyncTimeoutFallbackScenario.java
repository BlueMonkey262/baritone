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

package baritone.testing.scenario;

import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import baritone.utils.schematic.StaticSchematic;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** A shulker that cannot open must time out and fall through to a later candidate. */
public final class RestockSyncTimeoutFallbackScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int BLOCKED_BOX_X = 2;
    private static final int BLOCKED_BOX_Z = 4;
    private static final int FALLBACK_BOX_X = -4;
    private static final int FALLBACK_BOX_Z = 8;
    private static final int SOURCE_COUNT = 8;

    private boolean reachedBlockedBox;

    @Override
    public String name() {
        return "restock-sync-timeout-fallback";
    }

    @Override
    public String description() {
        return "Skip a shulker that never opens and fetch the material from a later box";
    }

    @Override
    public int tickBudget() {
        return 20 * 180;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockIndexBeforeBuild", false);
        settings.put("restockExtraStacks", 0);
        // The first box is deliberately blocked above by an unbreakable block, so keep the
        // fallback bounded and quick.
        settings.put("restockOpenTimeoutTicks", 20);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-8, -3, -12, 16, -1, 12, "minecraft:stone");
        arena.fill(-8, 0, -12, 16, 4, 12, "minecraft:air");
        arena.command("clear @s");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        // A shulker facing up cannot open through an unbreakable lid. The obstruction must remain
        // in place: allowBreak is enabled, so a breakable lid would only repair this fixture.
        arena.setBlock(BLOCKED_BOX_X, 0, BLOCKED_BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:"
                        + SOURCE_COUNT + ",Slot:0b}]}");
        arena.setBlock(BLOCKED_BOX_X, 1, BLOCKED_BOX_Z, "minecraft:bedrock");
        arena.setBlock(FALLBACK_BOX_X, 0, FALLBACK_BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:"
                        + SOURCE_COUNT + ",Slot:0b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 1, 0).isAir()
                && arena.stateAt(BLOCKED_BOX_X, 0, BLOCKED_BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(BLOCKED_BOX_X, 1, BLOCKED_BOX_Z).is(Blocks.BEDROCK)
                && arena.stateAt(FALLBACK_BOX_X, 0, FALLBACK_BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countContainer(arena, BLOCKED_BOX_X, 0, BLOCKED_BOX_Z,
                Blocks.WHITE_CONCRETE.asItem()) == SOURCE_COUNT
                && ScenarioInventory.countContainer(arena, FALLBACK_BOX_X, 0, FALLBACK_BOX_Z,
                Blocks.WHITE_CONCRETE.asItem()) == SOURCE_COUNT
                && ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem()) == 0;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos blocked = arena.at(BLOCKED_BOX_X, 0, BLOCKED_BOX_Z);
        BetterBlockPos fallback = arena.at(FALLBACK_BOX_X, 0, FALLBACK_BOX_Z);
        if (world == null) {
            arena.note("no world data, so the timeout and fallback boxes could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(blocked);
        world.getRestockBoxes().addBox(fallback);
        BetterBlockPos origin = arena.at(BUILD_X, 0, 0);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(),
                schematic(),
                new Vec3i(origin.x, origin.y, origin.z)
        );
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        // The blocked box is off the direct route to the build and the radius excludes the initial
        // spawn position. Reaching this position is therefore world-state evidence that the first
        // candidate was actually attempted, without making the verdict depend on process labels.
        if (near(arena, arena.at(BLOCKED_BOX_X, 0, BLOCKED_BOX_Z))) {
            this.reachedBlockedBox = true;
        }
        if (!arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE)) {
            return null;
        }
        int blocked = ScenarioInventory.countContainer(
                arena, BLOCKED_BOX_X, 0, BLOCKED_BOX_Z, Blocks.WHITE_CONCRETE.asItem());
        int fallback = ScenarioInventory.countContainer(
                arena, FALLBACK_BOX_X, 0, FALLBACK_BOX_Z, Blocks.WHITE_CONCRETE.asItem());
        if (!this.reachedBlockedBox) {
            return Verdict.fail("the build completed without reaching the blocked first box, so no sync timeout was exercised");
        }
        if (blocked != SOURCE_COUNT || fallback != SOURCE_COUNT - 1) {
            return Verdict.fail("the fallback build completed with blocked-box stock=%d and fallback stock=%d; expected %d and %d",
                    blocked, fallback, SOURCE_COUNT, SOURCE_COUNT - 1);
        }
        return Verdict.pass("the blocked first box timed out without changing its contents, then the later box supplied the material");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "reached blocked box=" + this.reachedBlockedBox
                + ", target=" + arena.stateAt(BUILD_X, 1, 0).getBlock()
                + ", blocked stock=" + ScenarioInventory.countContainer(
                arena, BLOCKED_BOX_X, 0, BLOCKED_BOX_Z, Blocks.WHITE_CONCRETE.asItem())
                + ", fallback stock=" + ScenarioInventory.countContainer(
                arena, FALLBACK_BOX_X, 0, FALLBACK_BOX_Z, Blocks.WHITE_CONCRETE.asItem());
    }

    private static boolean near(TestArena arena, BetterBlockPos box) {
        return arena.ctx().playerFeet().distSqr(box) <= 9.0;
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[1][1][2];
        states[0][0][0] = Blocks.STONE.defaultBlockState();
        states[0][0][1] = Blocks.WHITE_CONCRETE.defaultBlockState();
        return new StaticSchematic(states);
    }
}
