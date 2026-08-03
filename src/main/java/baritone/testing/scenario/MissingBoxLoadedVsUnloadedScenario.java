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
import baritone.utils.schematic.StaticSchematic;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** A loaded broken registration is missing; an unobserved unloaded chunk is not. */
public final class MissingBoxLoadedVsUnloadedScenario extends TestScenario {

    private static final int BROKEN_X = 2;
    private static final int BOX_Z = 4;
    private static final int UNLOADED_X = 1024;
    private static final int TARGET_X = 8;

    private boolean missingObserved;
    private int missingObservedAt = -1;

    @Override
    public String name() {
        return "missing-box-loaded-vs-unloaded";
    }

    @Override
    public String description() {
        return "Flag a broken loaded box without mistaking an unloaded registered position for a missing box";
    }

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
        settings.put("shulkerDumpKeepThrowawayStacks", 0);
        settings.put("shulkerDumpMaxBoxesPerTrip", 1);
        settings.put("restockMaxDistance", 2000);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 24, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 24, 4, 8, "minecraft:air");
        arena.command("clear @s");
        for (Item item : AbstractShulkerDumpScenario.DUMP_ITEMS) {
            arena.command("give @s " + BuiltInRegistries.ITEM.getKey(item) + " 1");
        }
        arena.command("give @s minecraft:diamond 1");
        arena.command("give @s minecraft:totem_of_undying 1");
        arena.command("give @s minecraft:diamond_pickaxe 1");
        arena.fill(TARGET_X, 0, 0, TARGET_X + 1, 0, 0, "minecraft:stone");
        // The position is deliberately loaded but is not a shulker box.
        arena.setBlock(BROKEN_X, 0, BOX_Z, "minecraft:stone");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(TARGET_X + 1, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BROKEN_X, 0, BOX_Z).is(Blocks.STONE)
                && !arena.ctx().world().getChunkSource().hasChunk(UNLOADED_X >> 4, 0)
                && freeSlots(arena) == 2
                && ScenarioInventory.countPlayer(arena, Items.DIAMOND) == 1
                && ScenarioInventory.countPlayer(arena, Items.TOTEM_OF_UNDYING) == 1
                && ScenarioInventory.countPlayer(arena, Items.DIAMOND_PICKAXE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so the loaded and unloaded registrations could not be made");
            return;
        }
        world.getRestockBoxes().addBox(arena.at(BROKEN_X, 0, BOX_Z));
        world.getRestockBoxes().addBox(arena.at(UNLOADED_X, 0, 0));
        BetterBlockPos origin = arena.at(TARGET_X, 0, 0);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), clearSchematic(), new Vec3i(origin.x, origin.y, origin.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            return Verdict.fail("world data disappeared while checking missing-box flags");
        }
        IRestockBox broken = world.getRestockBoxes().getBox(arena.at(BROKEN_X, 0, BOX_Z));
        IRestockBox unloaded = world.getRestockBoxes().getBox(arena.at(UNLOADED_X, 0, 0));
        if (broken == null || unloaded == null) {
            return Verdict.fail("one of the two registered positions disappeared from the collection");
        }
        if (broken.isMissing() && !this.missingObserved) {
            this.missingObserved = true;
            this.missingObservedAt = elapsedTicks;
            arena.note("the loaded non-shulker position was flagged missing at t=%ds", elapsedTicks / 20);
        }
        if (!this.missingObserved) {
            return null;
        }
        if (unloaded.isMissing()) {
            return Verdict.fail("the still-unloaded registered position was flagged missing");
        }
        if (arena.ctx().world().getChunkSource().hasChunk(UNLOADED_X >> 4, 0)) {
            return Verdict.fail("the control position became loaded before the unloaded-vs-loaded distinction was observed");
        }
        if (!arena.stateAt(TARGET_X, 0, 0).isAir()) {
            return Verdict.fail("the bot never cleared the first target, so the unload request was not independently exercised");
        }
        if (elapsedTicks - this.missingObservedAt >= 40) {
            return Verdict.pass("the loaded broken registration was flagged while the unloaded registration remained untouched");
        }
        return null;
    }

    @Override
    public String progressMarker(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        IRestockBox broken = world == null ? null : world.getRestockBoxes().getBox(arena.at(BROKEN_X, 0, BOX_Z));
        IRestockBox unloaded = world == null ? null : world.getRestockBoxes().getBox(arena.at(UNLOADED_X, 0, 0));
        BetterBlockPos feet = arena.ctx().playerFeet();
        return (arena.stateAt(TARGET_X, 0, 0).isAir() ? "air" : "stone")
                + ":" + (broken != null && broken.isMissing())
                + ":" + (unloaded != null && unloaded.isMissing())
                + "@" + feet.x + "," + feet.y + "," + feet.z;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        IRestockBox broken = world == null ? null : world.getRestockBoxes().getBox(arena.at(BROKEN_X, 0, BOX_Z));
        IRestockBox unloaded = world == null ? null : world.getRestockBoxes().getBox(arena.at(UNLOADED_X, 0, 0));
        return "loaded missing=" + (broken != null && broken.isMissing())
                + ", unloaded missing=" + (unloaded != null && unloaded.isMissing())
                + ", unloaded chunk=" + arena.ctx().world().getChunkSource().hasChunk(UNLOADED_X >> 4, 0)
                + ", first target air=" + arena.stateAt(TARGET_X, 0, 0).isAir()
                + ", player=" + arena.ctx().playerFeet();
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

    private static StaticSchematic clearSchematic() {
        BlockState[][][] states = new BlockState[2][1][1];
        states[0][0][0] = Blocks.AIR.defaultBlockState();
        states[1][0][0] = Blocks.AIR.defaultBlockState();
        return new StaticSchematic(states);
    }
}
