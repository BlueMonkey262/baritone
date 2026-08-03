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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Shared clear-build fixture for the dedicated shulker unload scenarios. */
abstract class AbstractShulkerDumpScenario extends TestScenario {

    private static final int TARGET_X = 8;
    private static final int TARGET_Z = 0;
    private static final int FLOOR_MIN_X = -4;
    private static final int FLOOR_MAX_X = 24;
    private static final int FLOOR_MIN_Z = -8;
    private static final int FLOOR_MAX_Z = 8;

    /** All of these are block items absent from the air schematic. */
    static final Item[] DUMP_ITEMS = {
            Items.RED_CONCRETE, Items.ORANGE_CONCRETE, Items.YELLOW_CONCRETE, Items.LIME_CONCRETE,
            Items.GREEN_CONCRETE, Items.CYAN_CONCRETE, Items.LIGHT_BLUE_CONCRETE, Items.BLUE_CONCRETE,
            Items.PURPLE_CONCRETE, Items.MAGENTA_CONCRETE, Items.PINK_CONCRETE, Items.BROWN_CONCRETE,
            Items.BLACK_CONCRETE, Items.GRAY_CONCRETE, Items.LIGHT_GRAY_CONCRETE, Items.WHITE_CONCRETE,
            Items.RED_WOOL, Items.ORANGE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL,
            Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL, Items.BLUE_WOOL,
            Items.PURPLE_WOOL, Items.MAGENTA_WOOL, Items.PINK_WOOL, Items.BROWN_WOOL,
            Items.BLACK_WOOL, Items.GRAY_WOOL, Items.LIGHT_GRAY_WOOL
    };

    /** Twenty-six distinct, non-carried block items make a box look almost full in the index. */
    private static final Item[] CAPACITY_ITEMS = {
            Items.RED_TERRACOTTA, Items.ORANGE_TERRACOTTA, Items.YELLOW_TERRACOTTA,
            Items.LIME_TERRACOTTA, Items.GREEN_TERRACOTTA, Items.CYAN_TERRACOTTA,
            Items.LIGHT_BLUE_TERRACOTTA, Items.BLUE_TERRACOTTA, Items.PURPLE_TERRACOTTA,
            Items.MAGENTA_TERRACOTTA, Items.PINK_TERRACOTTA, Items.BROWN_TERRACOTTA,
            Items.BLACK_TERRACOTTA, Items.GRAY_TERRACOTTA, Items.LIGHT_GRAY_TERRACOTTA,
            Items.WHITE_TERRACOTTA, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
            Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.CRIMSON_PLANKS,
            Items.WARPED_PLANKS, Items.MANGROVE_PLANKS, Items.BAMBOO_PLANKS
    };

    /**
     * The boxes used by a scenario, in the order in which its assertions describe them. A box
     * with no initial contents is left unindexed; a filled box is indexed from the same contents
     * that staging puts in the live container.
     */
    protected abstract int[] boxXs();

    protected Item[] initialBoxItems(int index) {
        return new Item[0];
    }

    protected int maxBoxesPerTrip() {
        return 4;
    }

    protected int keepThrowawayStacks() {
        return 0;
    }

    protected boolean indexInitialBoxes() {
        return false;
    }

    protected int targetBlocks() {
        // The first break must be followed by a dump before the second break completes the job.
        return 2;
    }

    protected boolean requiresCapacityPreference() {
        return false;
    }

    protected boolean requiresBoxLimit() {
        return false;
    }

    protected boolean requiresAllBoxes() {
        return false;
    }

    protected boolean requiresKeptThrowaway() {
        return false;
    }

    private int[] initialOccupancy;
    private int firstChangedBox = -1;
    private boolean sawDepositWhileWorkRemained;

    @Override
    public int tickBudget() {
        return 20 * 120;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockIndexBeforeBuild", false);
        settings.put("restockDumpJunk", false);
        settings.put("shulkerDump", true);
        settings.put("shulkerDumpWhenFreeSlotsBelow", 2);
        settings.put("shulkerDumpKeepThrowawayStacks", keepThrowawayStacks());
        settings.put("shulkerDumpMaxBoxesPerTrip", maxBoxesPerTrip());
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(FLOOR_MIN_X, -3, FLOOR_MIN_Z, FLOOR_MAX_X, -1, FLOOR_MAX_Z, "minecraft:stone");
        arena.fill(FLOOR_MIN_X, 0, FLOOR_MIN_Z, FLOOR_MAX_X, 4, FLOOR_MAX_Z, "minecraft:air");
        arena.command("clear @s");
        for (Item item : DUMP_ITEMS) {
            arena.command("give @s " + BuiltInRegistries.ITEM.getKey(item) + " 1");
        }
        // These three are the independent control items. Diamonds and a totem specifically close
        // the hole where a loosened BlockItem check would treat non-block inventory as rubble.
        arena.command("give @s minecraft:diamond 1");
        arena.command("give @s minecraft:totem_of_undying 1");
        arena.command("give @s minecraft:diamond_pickaxe 1");
        arena.fill(TARGET_X, 0, TARGET_Z, TARGET_X + targetBlocks() - 1, 0, TARGET_Z, "minecraft:stone");
        int[] boxes = boxXs();
        for (int i = 0; i < boxes.length; i++) {
            arena.setBlock(boxes[i], 0, 4, shulkerWithItems(initialBoxItems(i)));
        }
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        for (int x = TARGET_X; x < TARGET_X + targetBlocks(); x++) {
            if (!arena.stateAt(x, 0, TARGET_Z).is(Blocks.STONE)) {
                return false;
            }
        }
        int[] boxes = boxXs();
        this.initialOccupancy = new int[boxes.length];
        for (int i = 0; i < boxes.length; i++) {
            if (!(arena.stateAt(boxes[i], 0, 4).getBlock() instanceof ShulkerBoxBlock)) {
                return false;
            }
            this.initialOccupancy[i] = occupiedSlots(arena, boxes[i], 0, 4);
            Map<Item, Integer> expected = contentsOf(initialBoxItems(i));
            if (this.initialOccupancy[i] != expected.size()) {
                return false;
            }
            for (Map.Entry<Item, Integer> entry : expected.entrySet()) {
                if (ScenarioInventory.countContainer(arena, boxes[i], 0, 4, entry.getKey()) != entry.getValue()) {
                    return false;
                }
            }
        }
        for (Item item : DUMP_ITEMS) {
            if (ScenarioInventory.countPlayer(arena, item) != 1) {
                return false;
            }
        }
        return ScenarioInventory.countPlayer(arena, Items.DIAMOND) == 1
                && ScenarioInventory.countPlayer(arena, Items.TOTEM_OF_UNDYING) == 1
                && ScenarioInventory.countPlayer(arena, Items.DIAMOND_PICKAXE) == 1
                && freeSlots(arena) == 2
                && ScenarioInventory.countPlayer(arena, Items.COBBLESTONE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so no unload boxes could be registered");
            return;
        }
        int[] boxes = boxXs();
        for (int i = 0; i < boxes.length; i++) {
            BetterBlockPos pos = arena.at(boxes[i], 0, 4);
            world.getRestockBoxes().addBox(pos);
            if (indexInitialBoxes()) {
                world.getRestockBoxes().updateContents(pos, contentsOf(initialBoxItems(i)));
            }
        }
        arena.note("started a %d-block clear build with %d free slots and %d registered unload box(es)",
                targetBlocks(), freeSlots(arena), boxes.length);
        BetterBlockPos origin = arena.at(TARGET_X, 0, TARGET_Z);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), clearSchematic(targetBlocks()),
                new Vec3i(origin.x, origin.y, origin.z)
        );
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int remaining = remainingTargets(arena);
        int[] boxes = boxXs();
        for (int i = 0; i < boxes.length; i++) {
            int now = occupiedSlots(arena, boxes[i], 0, 4);
            if (now > initialOccupancy[i]) {
                if (this.firstChangedBox < 0) {
                    this.firstChangedBox = i;
                }
                if (remaining > 0) {
                    this.sawDepositWhileWorkRemained = true;
                }
            }
        }

        if (this.requiresCapacityPreference() && occupiedSlots(arena, boxes[0], 0, 4) > initialOccupancy[0]
                && occupiedSlots(arena, boxes[1], 0, 4) == initialOccupancy[1]) {
            return Verdict.fail("the nearly-full nearer box accepted rubble before the farther empty box");
        }
        if (this.requiresBoxLimit() && boxes.length > 1
                && occupiedSlots(arena, boxes[1], 0, 4) > initialOccupancy[1]) {
            return Verdict.fail("the unload trip opened a second box despite shulkerDumpMaxBoxesPerTrip=1");
        }

        if (remaining != 0) {
            return null;
        }

        if (!this.sawDepositWhileWorkRemained) {
            return Verdict.fail("the clear build finished without a deposit while work was still outstanding");
        }
        if (ScenarioInventory.countPlayer(arena, Items.DIAMOND) != 1
                || ScenarioInventory.countPlayer(arena, Items.TOTEM_OF_UNDYING) != 1
                || ScenarioInventory.countPlayer(arena, Items.DIAMOND_PICKAXE) != 1) {
            return Verdict.fail("unwanted unloading changed the protected diamond, totem, or pickaxe inventory");
        }
        if (this.requiresCapacityPreference() && this.firstChangedBox != 1) {
            return Verdict.fail("the farther empty box was not the first box to receive rubble");
        }
        if (this.requiresAllBoxes()) {
            for (int i = 0; i < boxes.length; i++) {
                if (occupiedSlots(arena, boxes[i], 0, 4) <= initialOccupancy[i]) {
                    return Verdict.fail("unloading did not continue into box %d after the preceding box filled", i + 1);
                }
            }
        }
        if (this.requiresBoxLimit()) {
            if (this.firstChangedBox != 0 || countCarriedRubble(arena) <= 0) {
                return Verdict.fail("the capped trip did not stop after one partly filled box with rubble still carried");
            }
        }
        if (this.requiresKeptThrowaway()
                && (ScenarioInventory.countPlayer(arena, Items.COBBLESTONE) <= 0
                || ScenarioInventory.countContainer(arena, boxes[0], 0, 4, Items.COBBLESTONE) != 0)) {
            return Verdict.fail("the configured throwaway stack was not retained in inventory");
        }
        boolean dumped = false;
        for (int i = 0; i < boxes.length; i++) {
            dumped |= occupiedSlots(arena, boxes[i], 0, 4) > initialOccupancy[i];
        }
        if (!dumped) {
            return Verdict.fail("the clear build finished but no registered box received rubble");
        }
        return Verdict.pass("cleared the targets and unloaded rubble while preserving protected inventory");
    }

    @Override
    public String progressMarker(TestArena arena) {
        StringBuilder marker = new StringBuilder().append(remainingTargets(arena));
        for (int x : boxXs()) {
            marker.append(':').append(occupiedSlots(arena, x, 0, 4));
        }
        BetterBlockPos feet = arena.ctx().playerFeet();
        return marker.append('@').append(feet.x).append(',').append(feet.y).append(',').append(feet.z).toString();
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        StringBuilder boxes = new StringBuilder();
        for (int x : boxXs()) {
            if (boxes.length() > 0) {
                boxes.append(", ");
            }
            boxes.append(occupiedSlots(arena, x, 0, 4));
        }
        return "targets remaining=" + remainingTargets(arena)
                + ", box occupied slots=" + boxes
                + ", protected items=" + ScenarioInventory.countPlayer(arena, Items.DIAMOND)
                + "/" + ScenarioInventory.countPlayer(arena, Items.TOTEM_OF_UNDYING)
                + "/" + ScenarioInventory.countPlayer(arena, Items.DIAMOND_PICKAXE)
                + ", player=" + arena.ctx().playerFeet();
    }

    protected static int countCarriedRubble(TestArena arena) {
        int count = ScenarioInventory.countPlayer(arena, Items.COBBLESTONE);
        for (Item item : DUMP_ITEMS) {
            count += ScenarioInventory.countPlayer(arena, item);
        }
        return count;
    }

    protected static Item[] capacityItems() {
        return CAPACITY_ITEMS.clone();
    }

    private int remainingTargets(TestArena arena) {
        int remaining = 0;
        for (int x = TARGET_X; x < TARGET_X + targetBlocks(); x++) {
            if (!arena.stateAt(x, 0, TARGET_Z).isAir()) {
                remaining++;
            }
        }
        return remaining;
    }

    private static int freeSlots(TestArena arena) {
        int free = 0;
        for (ItemStack stack : arena.ctx().player().getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private static int occupiedSlots(TestArena arena, int x, int y, int z) {
        return ScenarioInventory.countNonEmptyContainerSlots(arena, x, y, z);
    }

    private static String shulkerWithItems(Item[] items) {
        if (items.length == 0) {
            return "minecraft:shulker_box[facing=up]";
        }
        StringBuilder nbt = new StringBuilder("minecraft:shulker_box[facing=up]{Items:[");
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                nbt.append(',');
            }
            nbt.append("{id:\"").append(BuiltInRegistries.ITEM.getKey(items[i]))
                    .append("\",count:1,Slot:").append(i).append("b}");
        }
        return nbt.append("]}").toString();
    }

    private static Map<Item, Integer> contentsOf(Item[] items) {
        Map<Item, Integer> contents = new HashMap<>();
        for (Item item : items) {
            contents.merge(item, 1, Integer::sum);
        }
        return contents;
    }

    private static StaticSchematic clearSchematic(int width) {
        BlockState[][][] states = new BlockState[width][1][1];
        for (int x = 0; x < width; x++) {
            states[x][0][0] = Blocks.AIR.defaultBlockState();
        }
        return new StaticSchematic(states);
    }
}
