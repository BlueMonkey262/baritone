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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Cancelling an open restock must close the real menu and leave no movement behind. */
public final class RestockCancelCleanupScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int SOURCE_COUNT = 64;

    private boolean requestStarted;
    private boolean cancelIssued;
    private int cancelTick;
    private BetterBlockPos feetAtCancel;

    @Override
    public String name() {
        return "restock-cancel-cleanup";
    }

    @Override
    public String description() {
        return "Cancel an open restock container and verify the menu closes without leaving control behind";
    }

    @Override
    public int tickBudget() {
        return 20 * 180;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockIndexBeforeBuild", false);
        settings.put("restockExtraStacks", 0);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        arena.setBlock(BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:"
                        + SOURCE_COUNT + ",Slot:0b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 1, 0).isAir()
                && arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z,
                Blocks.WHITE_CONCRETE.asItem()) == SOURCE_COUNT
                && ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem()) == 0
                && arena.baritone().getContainerInteractionBehavior().openContainer() == null;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the cancellation box at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        this.requestStarted = arena.baritone().getRestockProcess().requestRestock(
                Collections.singletonMap(Blocks.WHITE_CONCRETE.defaultBlockState(), 1),
                stack -> stack.is(Blocks.WHITE_CONCRETE.asItem())
        );
        arena.note("started a direct restock request for the cancellation test: %b", this.requestStarted);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        if (!this.cancelIssued
                && this.requestStarted
                && arena.baritone().getContainerInteractionBehavior().openContainer() != null) {
            this.cancelIssued = true;
            this.cancelTick = elapsedTicks;
            this.feetAtCancel = arena.ctx().playerFeet();
            arena.baritone().getPathingBehavior().cancelEverything();
            return null;
        }
        if (!this.cancelIssued) {
            if (elapsedTicks >= tickBudget()) {
                return Verdict.fail("never observed the requested restock with a real open container to cancel");
            }
            return null;
        }
        if (arena.baritone().getContainerInteractionBehavior().openContainer() != null) {
            return Verdict.fail("the restock menu remained open after cancellation");
        }
        if (elapsedTicks - this.cancelTick < 20) {
            return null;
        }
        if (!arena.ctx().playerFeet().equals(this.feetAtCancel)) {
            return Verdict.fail("the player moved after cancellation, so restock still controlled movement");
        }
        int source = ScenarioInventory.countContainer(
                arena, BOX_X, 0, BOX_Z, Blocks.WHITE_CONCRETE.asItem());
        int carried = ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem());
        if (source + carried != SOURCE_COUNT) {
            return Verdict.fail("cancelling the container interaction lost white concrete: source=%d, inventory=%d",
                    source, carried);
        }
        return Verdict.pass("cancellation closed the container, stopped movement, and preserved the material state");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "request started=" + this.requestStarted
                + ", cancel issued=" + this.cancelIssued
                + ", menu open=" + (arena.baritone().getContainerInteractionBehavior().openContainer() != null)
                + ", source white=" + ScenarioInventory.countContainer(
                arena, BOX_X, 0, BOX_Z, Blocks.WHITE_CONCRETE.asItem())
                + ", carried=" + ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem());
    }
}
