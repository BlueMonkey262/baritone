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

package baritone.testing.scenario;

import baritone.api.cache.IRestockBox;
import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps one shulker menu open through an explicit-coordinate {@code #addbox} for another shulker.
 * The only acceptable registration result is an unindexed second box: its coordinates must never
 * borrow the currently open menu's contents.
 */
public final class AddBoxWrongOpenMenuScenario extends TestScenario {

    private static final int OPEN_BOX_X = 2;
    private static final int REGISTERED_BOX_X = 4;
    private static final int BOX_Z = 0;

    private boolean indexingStarted;
    private boolean registeredWhileOpen;

    @Override
    public String name() {
        return "addbox-wrong-open-menu";
    }

    @Override
    public String description() {
        return "Registering a second box while another shulker is open must leave the second box unindexed";
    }

    @Override
    public int tickBudget() {
        return 20 * 300;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockIndexBeforeBuild", true);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -4, 8, -1, 4, "minecraft:stone");
        arena.fill(-4, 0, -4, 8, 4, 4, "minecraft:air");
        // Different contents make an accidental borrowed index unambiguous.
        arena.setBlock(OPEN_BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:dirt\",count:17,Slot:0b}]}");
        arena.setBlock(REGISTERED_BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:31,Slot:0b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(OPEN_BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(REGISTERED_BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so no box can be opened for the ownership check");
            return;
        }
        BetterBlockPos openBox = arena.at(OPEN_BOX_X, 0, BOX_Z);
        world.getRestockBoxes().addBox(openBox);
        // This uses the ordinary restock path to open a real, synced shulker menu. Once it is open,
        // poll executes the same addbox command path a player uses for the other coordinates.
        this.indexingStarted = arena.baritone().getRestockProcess().requestIndexing(false, stack -> true);
        arena.note("started indexing %s to open its real container menu: %b", openBox, this.indexingStarted);
    }

    @Override
    public TestScenario.Verdict poll(TestArena arena, int elapsedTicks) {
        if (!this.indexingStarted) {
            return Verdict.fail("could not start indexing the first box, so no foreign menu was opened");
        }
        if (!this.registeredWhileOpen
                && arena.baritone().getContainerInteractionBehavior().openContainer() != null
                && arena.baritone().getContainerInteractionBehavior().isContainerReadable()) {
            BetterBlockPos registered = arena.at(REGISTERED_BOX_X, 0, BOX_Z);
            // ICommandManager is the existing implementation of the player's #addbox command;
            // TestArena command() is intentionally vanilla-server commands only.
            boolean accepted = arena.baritone().getCommandManager().execute(
                    "addbox " + registered.x + " " + registered.y + " " + registered.z);
            if (!accepted) {
                return Verdict.fail("the explicit-coordinate addbox command was not accepted while the first menu was open");
            }
            this.registeredWhileOpen = true;
            arena.note("ran explicit-coordinate #addbox for %s while the other shulker menu was readable", registered);
        }
        if (!this.registeredWhileOpen) {
            return null;
        }

        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos registered = arena.at(REGISTERED_BOX_X, 0, BOX_Z);
        IRestockBox box = world == null ? null : world.getRestockBoxes().getBox(registered);
        if (box == null) {
            return Verdict.fail("#addbox did not register the requested second-box coordinates");
        }
        if (!box.isUnindexed() || box.countOf(Blocks.DIRT.asItem()) != 0) {
            return Verdict.fail("the newly registered box inherited the open box's dirt index instead of remaining unindexed");
        }
        return Verdict.pass("the explicit-coordinate box remained unindexed while a different container menu was open");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "indexing started=" + this.indexingStarted
                + ", explicit addbox executed while readable=" + this.registeredWhileOpen
                + ", open menu=" + (arena.baritone().getContainerInteractionBehavior().openContainer() != null);
    }
}
