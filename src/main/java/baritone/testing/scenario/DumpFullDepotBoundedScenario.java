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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** A full depot must produce a bounded inventory-full result rather than a trip loop. */
public final class DumpFullDepotBoundedScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int TARGET_X = 8;
    private static final int TARGETS = 2;

    private static final Item[] DEPOT_ITEMS = {
            Items.RED_TERRACOTTA, Items.ORANGE_TERRACOTTA, Items.YELLOW_TERRACOTTA,
            Items.LIME_TERRACOTTA, Items.GREEN_TERRACOTTA, Items.CYAN_TERRACOTTA,
            Items.LIGHT_BLUE_TERRACOTTA, Items.BLUE_TERRACOTTA, Items.PURPLE_TERRACOTTA,
            Items.MAGENTA_TERRACOTTA, Items.PINK_TERRACOTTA, Items.BROWN_TERRACOTTA,
            Items.BLACK_TERRACOTTA, Items.GRAY_TERRACOTTA, Items.LIGHT_GRAY_TERRACOTTA,
            Items.WHITE_TERRACOTTA, Items.OAK_PLANKS, Items.SPRUCE_PLANKS,
            Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS,
            Items.DARK_OAK_PLANKS, Items.CRIMSON_PLANKS, Items.WARPED_PLANKS,
            Items.MANGROVE_PLANKS, Items.BAMBOO, Items.CLOCK
    };

    private static final Item[] CARRIED_RUBBLE = {
            Items.RED_CONCRETE, Items.ORANGE_CONCRETE, Items.YELLOW_CONCRETE, Items.LIME_CONCRETE,
            Items.GREEN_CONCRETE, Items.CYAN_CONCRETE, Items.LIGHT_BLUE_CONCRETE, Items.BLUE_CONCRETE,
            Items.PURPLE_CONCRETE, Items.MAGENTA_CONCRETE, Items.PINK_CONCRETE, Items.BROWN_CONCRETE,
            Items.BLACK_CONCRETE, Items.GRAY_CONCRETE, Items.LIGHT_GRAY_CONCRETE, Items.WHITE_CONCRETE,
            Items.RED_WOOL, Items.ORANGE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL, Items.GREEN_WOOL,
            Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL, Items.BLUE_WOOL, Items.PURPLE_WOOL,
            Items.MAGENTA_WOOL, Items.PINK_WOOL, Items.BROWN_WOOL, Items.BLACK_WOOL,
            Items.GRAY_WOOL
    };

    private boolean reachedDepot;
    private int depotVisits;
    private boolean wasNearDepot;
    private int stoppedSince = -1;

    @Override
    public String name() {
        return "dump-full-depot-bounded";
    }

    @Override
    public String description() {
        return "Stop with carried rubble when every eligible unload box is full instead of retrying forever";
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
        settings.put("restockDumpJunk", false);
        settings.put("shulkerDump", true);
        settings.put("shulkerDumpWhenFreeSlotsBelow", 2);
        settings.put("shulkerDumpMaxBoxesPerTrip", 1);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -10, 20, -1, 10, "minecraft:stone");
        arena.fill(-4, 0, -10, 20, 4, 10, "minecraft:air");
        arena.command("clear @s");
        for (Item item : CARRIED_RUBBLE) {
            arena.command("give @s " + itemId(item) + " 1");
        }
        arena.command("give @s minecraft:diamond 1");
        arena.command("give @s minecraft:totem_of_undying 1");
        arena.command("give @s minecraft:diamond_pickaxe 1");
        arena.fill(TARGET_X, 0, 0, TARGET_X + TARGETS - 1, 0, 0, "minecraft:stone");
        ContainerFixture.emptyBox(arena, BOX_X, 0, BOX_Z);
        for (int slot = 0; slot < DEPOT_ITEMS.length; slot++) {
            ContainerFixture.put(arena, BOX_X, 0, BOX_Z, slot, DEPOT_ITEMS[slot], 1);
        }
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        if (!arena.stateAt(0, -1, 0).is(Blocks.STONE)
                || !arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE)
                || !arena.stateAt(TARGET_X + 1, 0, 0).is(Blocks.STONE)
                || !(arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock)
                || ScenarioInventory.countNonEmptyContainerSlots(arena, BOX_X, 0, BOX_Z) != DEPOT_ITEMS.length) {
            return false;
        }
        for (Item item : DEPOT_ITEMS) {
            if (ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, item) != 1) {
                return false;
            }
        }
        return ScenarioInventory.countPlayer(arena, Items.DIAMOND) == 1
                && ScenarioInventory.countPlayer(arena, Items.TOTEM_OF_UNDYING) == 1
                && ScenarioInventory.countPlayer(arena, Items.DIAMOND_PICKAXE) == 1
                && countCarriedRubble(arena) == CARRIED_RUBBLE.length
                && ScenarioInventory.countPlayer(arena, Items.COBBLESTONE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        BetterBlockPos target = arena.at(TARGET_X, 0, 0);
        if (world == null) {
            arena.note("no world data, so the full depot at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        arena.note("staged one measured full 27-slot depot; two stone targets will create bounded carried rubble");
        arena.baritone().getBuilderProcess().build("harness-" + name(), clearSchematic(),
                new Vec3i(target.x, target.y, target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean near = arena.ctx().playerFeet().distSqr(arena.at(BOX_X, 0, BOX_Z)) <= 9.0;
        if (near && !this.wasNearDepot) {
            this.depotVisits++;
        }
        this.wasNearDepot = near;
        this.reachedDepot |= near;
        boolean targetsGone = arena.stateAt(TARGET_X, 0, 0).isAir()
                && arena.stateAt(TARGET_X + 1, 0, 0).isAir();
        boolean builderActive = arena.baritone().getBuilderProcess().isActive();
        if (!builderActive && targetsGone) {
            if (this.stoppedSince < 0) {
                this.stoppedSince = elapsedTicks;
            }
        } else {
            this.stoppedSince = -1;
        }
        int slots = ScenarioInventory.countNonEmptyContainerSlots(arena, BOX_X, 0, BOX_Z);
        int rubble = countCarriedRubble(arena);
        if (this.stoppedSince >= 0 && elapsedTicks - this.stoppedSince >= 30) {
            arena.note("full-depot evidence at t=%d: targets=0/%d, depot slots=%d/%d, carried rubble=%d, depot visits=%d, player=%s",
                    elapsedTicks, TARGETS, slots, DEPOT_ITEMS.length, rubble, this.depotVisits,
                    arena.ctx().playerFeet());
            if (!this.reachedDepot) {
                return Verdict.fail("the full-depot result stopped without visiting the depot");
            }
            if (slots != DEPOT_ITEMS.length || rubble <= 0) {
                return Verdict.fail("the bounded full-depot result changed the depot to %d slots or left rubble=%d; expected full depot and carried rubble",
                        slots, rubble);
            }
            return this.depotVisits == 1
                    ? Verdict.pass("cleared the targets, observed the full depot, and stopped with rubble carried in one bounded trip")
                    : Verdict.fail("the full depot caused %d depot visits instead of one bounded attempt", this.depotVisits);
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("targets=%s/%s, depot slots=%d/%d, carried rubble=%d, visits=%d, builderActive=%b, player=%s",
                arena.stateAt(TARGET_X, 0, 0).isAir() && arena.stateAt(TARGET_X + 1, 0, 0).isAir() ? "0" : "some",
                TARGETS, ScenarioInventory.countNonEmptyContainerSlots(arena, BOX_X, 0, BOX_Z), DEPOT_ITEMS.length,
                countCarriedRubble(arena), this.depotVisits, arena.baritone().getBuilderProcess().isActive(),
                arena.ctx().playerFeet());
    }

    private static int countCarriedRubble(TestArena arena) {
        int count = ScenarioInventory.countPlayer(arena, Items.COBBLESTONE);
        for (Item item : CARRIED_RUBBLE) {
            count += ScenarioInventory.countPlayer(arena, item);
        }
        return count;
    }

    private static String itemId(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static StaticSchematic clearSchematic() {
        BlockState[][][] states = new BlockState[TARGETS][1][1];
        for (int x = 0; x < TARGETS; x++) {
            states[x][0][0] = Blocks.AIR.defaultBlockState();
        }
        return new StaticSchematic(states);
    }
}
