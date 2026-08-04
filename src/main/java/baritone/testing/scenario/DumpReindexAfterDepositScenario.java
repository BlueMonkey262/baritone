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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** The first box filled by an unload must be re-indexed before the next trip is sorted. */
public final class DumpReindexAfterDepositScenario extends TestScenario {

    private static final int FIRST_BOX_X = 2;
    private static final int SECOND_BOX_X = -4;
    private static final int BOX_Z = 4;
    private static final int FIRST_TARGET_X = 6;
    private static final int SECOND_TARGET_X = 7;

    private static final Item[] JUNK = {
            Items.RED_CONCRETE, Items.ORANGE_CONCRETE, Items.YELLOW_CONCRETE, Items.LIME_CONCRETE,
            Items.GREEN_CONCRETE, Items.CYAN_CONCRETE, Items.LIGHT_BLUE_CONCRETE, Items.BLUE_CONCRETE,
            Items.PURPLE_CONCRETE, Items.MAGENTA_CONCRETE, Items.PINK_CONCRETE, Items.BROWN_CONCRETE,
            Items.BLACK_CONCRETE, Items.GRAY_CONCRETE, Items.LIGHT_GRAY_CONCRETE, Items.WHITE_CONCRETE,
            Items.RED_WOOL, Items.ORANGE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL,
            Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL, Items.BLUE_WOOL,
            Items.PURPLE_WOOL, Items.MAGENTA_WOOL, Items.PINK_WOOL, Items.BROWN_WOOL
    };

    /* Twenty-six existing stacks leave one live slot in each 27-slot shulker. */
    private static final Item[] INITIAL_BOX_ITEMS = {
            Items.DIAMOND, Items.GOLD_INGOT, Items.IRON_INGOT, Items.COPPER_INGOT,
            Items.REDSTONE, Items.LAPIS_LAZULI, Items.QUARTZ, Items.COAL,
            Items.STICK, Items.PAPER, Items.BOOK, Items.GLASS, Items.BRICK,
            Items.BOWL, Items.BUCKET, Items.FLINT, Items.LEATHER, Items.STRING,
            Items.FEATHER, Items.BONE, Items.GUNPOWDER, Items.SLIME_BALL,
            Items.EGG, Items.SNOWBALL, Items.ARROW, Items.MILK_BUCKET
    };

    private boolean sawFirstBox;
    private boolean sawSecondBox;
    private boolean firstTargetCleared;
    private int initialJunk;
    private int minimumJunk;
    private int targetsClearedAt = -1;

    @Override
    public String name() {
        return "dump-reindex-after-deposit";
    }

    @Override
    public String description() {
        return "Use a second unload box after the first box is filled and its live index is refreshed";
    }

    @Override
    public int tickBudget() {
        return 20 * 240;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("pickupBlocks", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockDumpJunk", false);
        settings.put("shulkerDump", true);
        // Exactly eight slots are free initially. Each cleared block produces a new item stack,
        // so the first and second target create two separate unload requests at the boundary.
        settings.put("shulkerDumpWhenFreeSlotsBelow", 8);
        settings.put("shulkerDumpMaxBoxesPerTrip", 1);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-8, -3, -12, 16, -1, 12, "minecraft:stone");
        arena.fill(-8, 0, -12, 16, 4, 12, "minecraft:air");
        arena.command("clear @s");
        for (Item item : JUNK) {
            arena.command("give @s " + BuiltInRegistries.ITEM.getKey(item) + " 1");
        }
        arena.setBlock(FIRST_TARGET_X, 0, 0, "minecraft:stone");
        arena.setBlock(SECOND_TARGET_X, 0, 0, "minecraft:dirt");
        arena.setBlock(FIRST_BOX_X, 0, BOX_Z, boxWithItems());
        arena.setBlock(SECOND_BOX_X, 0, BOX_Z, boxWithItems());
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(FIRST_TARGET_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(SECOND_TARGET_X, 0, 0).is(Blocks.DIRT)
                && arena.stateAt(FIRST_BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(SECOND_BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && countJunk(arena) == JUNK.length
                && freeSlots(arena) == 8;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos first = arena.at(FIRST_BOX_X, 0, BOX_Z);
        BetterBlockPos second = arena.at(SECOND_BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the two indexed unload boxes could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(first);
        world.getRestockBoxes().addBox(second);
        // This is the index fixture, not a container read. The live deposit path must refresh it
        // after the first box accepts its final free slot.
        world.getRestockBoxes().updateContents(first, indexedCapacity());
        world.getRestockBoxes().updateContents(second, indexedCapacity());
        this.initialJunk = countJunk(arena);
        this.minimumJunk = this.initialJunk;
        BetterBlockPos target = arena.at(FIRST_TARGET_X, 0, 0);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), clearSchematic(), new Vec3i(target.x, target.y, target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        this.sawFirstBox |= ScenarioInventory.countNonEmptyContainerSlots(arena, FIRST_BOX_X, 0, BOX_Z)
                > INITIAL_BOX_ITEMS.length;
        this.sawSecondBox |= ScenarioInventory.countNonEmptyContainerSlots(arena, SECOND_BOX_X, 0, BOX_Z)
                > INITIAL_BOX_ITEMS.length;
        this.firstTargetCleared |= arena.stateAt(FIRST_TARGET_X, 0, 0).isAir();
        this.minimumJunk = Math.min(this.minimumJunk, countJunk(arena));

        if (this.sawSecondBox && !this.firstTargetCleared) {
            return Verdict.fail("the second depot was visited before the first work segment completed");
        }
        if (!arena.stateAt(SECOND_TARGET_X, 0, 0).isAir()) {
            if (elapsedTicks >= 20 * 20 && !arena.baritone().getBuilderProcess().isActive()) {
                return Verdict.fail("the clear stopped with targets remaining; first box=%b, second box=%b, junk reduced=%d",
                        this.sawFirstBox, this.sawSecondBox, this.initialJunk - this.minimumJunk);
            }
            return null;
        }
        if (this.targetsClearedAt < 0) {
            this.targetsClearedAt = elapsedTicks;
            arena.note("both clear targets became air before the unload handoff settled; waiting for both deposits");
        }
        if (arena.baritone().getRestockProcess() != null
                && arena.baritone().getRestockProcess().isActive()) {
            return null;
        }
        if (elapsedTicks - this.targetsClearedAt < 40) {
            return null;
        }
        int reduced = this.initialJunk - countJunk(arena);
        arena.note("reindex evidence at t=%d: first occupied=%d, second occupied=%d, junk=%d/%d, targets=clear",
                elapsedTicks,
                ScenarioInventory.countNonEmptyContainerSlots(arena, FIRST_BOX_X, 0, BOX_Z),
                ScenarioInventory.countNonEmptyContainerSlots(arena, SECOND_BOX_X, 0, BOX_Z),
                countJunk(arena), this.initialJunk);
        if (!this.sawFirstBox || !this.sawSecondBox) {
            return Verdict.fail("both clear segments completed, but the unload deposited into first=%b second=%b",
                    this.sawFirstBox, this.sawSecondBox);
        }
        if (reduced < 2) {
            return Verdict.fail("both targets cleared without two independent inventory reductions; junk reduced by %d", reduced);
        }
        return Verdict.pass("the first unload filled its chosen box, the live index changed, and the next unload used the second box");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("targets=%s/%s, firstBoxDeposit=%b, secondBoxDeposit=%b, junk=%d/%d, player=%s",
                arena.stateAt(FIRST_TARGET_X, 0, 0).getBlock().getName().getString(),
                arena.stateAt(SECOND_TARGET_X, 0, 0).getBlock().getName().getString(),
                this.sawFirstBox, this.sawSecondBox, countJunk(arena), this.initialJunk, arena.ctx().playerFeet());
    }

    private static Map<Item, Integer> indexedCapacity() {
        Map<Item, Integer> contents = new HashMap<>();
        for (Item item : INITIAL_BOX_ITEMS) {
            contents.put(item, 1);
        }
        return contents;
    }

    private static String boxWithItems() {
        StringBuilder command = new StringBuilder("minecraft:shulker_box[facing=up]{Items:[");
        for (int i = 0; i < INITIAL_BOX_ITEMS.length; i++) {
            if (i > 0) {
                command.append(',');
            }
            command.append("{id:\"").append(BuiltInRegistries.ITEM.getKey(INITIAL_BOX_ITEMS[i]))
                    .append("\",count:1,Slot:").append(i).append('b').append('}');
        }
        return command.append("]}").toString();
    }

    private static StaticSchematic clearSchematic() {
        return new StaticSchematic(new BlockState[][][]{
                {{Blocks.AIR.defaultBlockState()}},
                {{Blocks.AIR.defaultBlockState()}}
        });
    }

    private static int countJunk(TestArena arena) {
        int count = 0;
        for (Item item : JUNK) {
            count += ScenarioInventory.countPlayer(arena, item);
        }
        return count;
    }

    private static int freeSlots(TestArena arena) {
        int free = 0;
        for (var stack : arena.ctx().player().getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) {
                free++;
            }
        }
        return free;
    }

}
