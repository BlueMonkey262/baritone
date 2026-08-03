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

import baritone.api.cache.IRestockBox;
import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashMap;
import java.util.Map;

/** Re-checking all boxes must replace an old capacity estimate with the live contents. */
public final class IndexboxesAllRefreshScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;

    private boolean initialIndexStarted;
    private boolean liveReplacementSent;
    private boolean refreshStarted;
    private int initialFreeSlots = -1;
    private int liveReplacementAt = -1;

    @Override
    public String name() {
        return "indexboxes-all-refresh";
    }

    @Override
    public String description() {
        return "Refresh an indexed shulker after its contents change and update its capacity estimate";
    }

    @Override
    public int tickBudget() {
        return 20 * 120;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockOpenTimeoutTicks", 100);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.setBlock(BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:1,Slot:0b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so the box could not be indexed");
            return;
        }
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        world.getRestockBoxes().addBox(box);
        this.initialIndexStarted = arena.baritone().getRestockProcess().requestIndexing(false, stack -> true);
        arena.note("started the initial index of %s: %b", box, this.initialIndexStarted);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            return Verdict.fail("world data disappeared before the all-box refresh completed");
        }
        BetterBlockPos pos = arena.at(BOX_X, 0, BOX_Z);
        IRestockBox box = world.getRestockBoxes().getBox(pos);
        if (!this.initialIndexStarted) {
            return Verdict.fail("could not start the initial box indexing run");
        }
        if (box == null) {
            return Verdict.fail("the registered box disappeared from the collection");
        }
        if (!this.liveReplacementSent && !box.isUnindexed()
                && arena.baritone().getContainerInteractionBehavior().openContainer() == null) {
            if (box.countOf(Items.WHITE_CONCRETE) != 1 || box.estimatedFreeSlots() != 26) {
                return Verdict.fail("the initial index did not record one white-concrete stack and 26 free slots");
            }
            this.initialFreeSlots = box.estimatedFreeSlots();
            arena.ctx().player().connection.sendCommand("setblock "
                    + pos.x + " " + pos.y + " " + pos.z + " " + refreshedBox());
            this.liveReplacementSent = true;
            this.liveReplacementAt = elapsedTicks;
            arena.note("changed the live box from one stack to 26 distinct stacks before #indexboxes all");
        }
        if (!this.liveReplacementSent) {
            return null;
        }
        if (!liveReplacementVisible(arena)) {
            return null;
        }
        // Let the initial indexing process finish its close/DEFER handoff before asking the same
        // process to start a second run. The live container contents are the assertion; this small
        // settle window only keeps the two real command-driven visits from racing one another.
        if (elapsedTicks - this.liveReplacementAt < 5
                || arena.baritone().getContainerInteractionBehavior().openContainer() != null) {
            return null;
        }
        if (!this.refreshStarted) {
            boolean accepted = arena.baritone().getCommandManager().execute("indexboxes all");
            if (!accepted) {
                return Verdict.fail("the #indexboxes all command was not accepted");
            }
            this.refreshStarted = true;
            arena.note("requested the all-box refresh after the live contents changed");
            return null;
        }
        if (box.estimatedFreeSlots() == 1
                && box.countOf(Items.RED_TERRACOTTA) == 1
                && box.countOf(Items.WHITE_CONCRETE) == 0) {
            return Verdict.pass("#indexboxes all replaced the old 26-slot estimate with the live one-slot estimate");
        }
        return null;
    }

    @Override
    public String progressMarker(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        IRestockBox box = world == null ? null : world.getRestockBoxes().getBox(arena.at(BOX_X, 0, BOX_Z));
        BetterBlockPos feet = arena.ctx().playerFeet();
        return (box == null ? "none" : box.estimatedFreeSlots() + ":" + box.countOf(Items.WHITE_CONCRETE)
                + ":" + box.countOf(Items.RED_TERRACOTTA))
                + ":" + ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_TERRACOTTA)
                + "@" + feet.x + "," + feet.y + "," + feet.z;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        IRestockBox box = world == null ? null : world.getRestockBoxes().getBox(arena.at(BOX_X, 0, BOX_Z));
        return "initial index started=" + this.initialIndexStarted
                + ", live replacement sent=" + this.liveReplacementSent
                + ", refresh started=" + this.refreshStarted
                + ", indexed free slots=" + (box == null ? "missing" : box.estimatedFreeSlots())
                + ", initial free slots=" + this.initialFreeSlots
                + ", live red terracotta=" + ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_TERRACOTTA);
    }

    private static boolean liveReplacementVisible(TestArena arena) {
        return ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_TERRACOTTA) == 1
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE) == 0;
    }

    private static String refreshedBox() {
        Item[] items = AbstractShulkerDumpScenario.capacityItems();
        StringBuilder nbt = new StringBuilder("minecraft:shulker_box[facing=up]{Items:[");
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                nbt.append(',');
            }
            nbt.append("{id:\"").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(items[i]))
                    .append("\",count:1,Slot:").append(i).append("b}");
        }
        return nbt.append("]}").toString();
    }
}
