/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashMap;
import java.util.Map;

/** A deposit timeout must quarantine a box before any stale-menu quick-move can mutate it. */
public final class ContainerDepositTimeoutNoMutationScenario extends TestScenario {

    private static final int BLOCKED_X = 2;
    private static final int FALLBACK_X = -4;
    private static final int BOX_Z = 4;
    private static final int FIRST_TARGET_X = 6;
    private static final int SECOND_TARGET_X = 7;

    private boolean sawBlocked;
    private boolean sawFallback;
    private int targetsClearedAt = -1;

    @Override
    public String name() {
        return "container-deposit-timeout-no-mutation";
    }

    @Override
    public String description() {
        return "Leave a timed-out deposit box unchanged before using a fallback box";
    }

    @Override
    public int tickBudget() {
        return 20 * 260;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockDumpJunk", false);
        settings.put("shulkerDump", true);
        settings.put("shulkerDumpWhenFreeSlotsBelow", 4);
        settings.put("shulkerDumpMaxBoxesPerTrip", 2);
        settings.put("restockOpenTimeoutTicks", 20);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-8, -3, -12, 16, -1, 12, "minecraft:stone");
        arena.fill(-8, 0, -12, 16, 4, 12, "minecraft:air");
        arena.command("clear @s");
        RestockInventoryFixture.stage(arena, 33);
        arena.setBlock(FIRST_TARGET_X, 0, 0, "minecraft:stone");
        arena.setBlock(SECOND_TARGET_X, 0, 0, "minecraft:dirt");
        ContainerFixture.emptyBox(arena, BLOCKED_X, 0, BOX_Z);
        // A top slab makes this a genuine open/sync failure without changing the box's marker.
        ContainerFixture.put(arena, BLOCKED_X, 0, BOX_Z, 0, Items.WHITE_CONCRETE, 1);
        arena.setBlock(BLOCKED_X, 1, BOX_Z, "minecraft:stone_slab[type=bottom]");
        ContainerFixture.emptyBox(arena, FALLBACK_X, 0, BOX_Z);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(FIRST_TARGET_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(SECOND_TARGET_X, 0, 0).is(Blocks.DIRT)
                && arena.stateAt(BLOCKED_X, 1, BOX_Z).is(Blocks.STONE_SLAB)
                && arena.stateAt(BLOCKED_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(FALLBACK_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countContainer(arena, BLOCKED_X, 0, BOX_Z, Items.WHITE_CONCRETE) == 1
                && ScenarioInventory.countContainer(arena, BLOCKED_X, 0, BOX_Z, Items.RED_CONCRETE) == 0
                && ScenarioInventory.countContainer(arena, FALLBACK_X, 0, BOX_Z, Items.RED_CONCRETE) == 0
                && RestockInventoryFixture.staged(arena, 33);
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so the timeout and fallback boxes could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(arena.at(BLOCKED_X, 0, BOX_Z));
        world.getRestockBoxes().addBox(arena.at(FALLBACK_X, 0, BOX_Z));
        arena.note("starting two-target clear with blocked marker white=%d and carried red=%d",
                ScenarioInventory.countContainer(arena, BLOCKED_X, 0, BOX_Z, Items.WHITE_CONCRETE),
                ScenarioInventory.countPlayer(arena, Items.RED_CONCRETE));
        arena.baritone().getBuilderProcess().clearArea(arena.at(FIRST_TARGET_X, 0, 0),
                arena.at(SECOND_TARGET_X, 0, 0));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        this.sawBlocked |= arena.ctx().playerFeet().distSqr(arena.at(BLOCKED_X, 0, BOX_Z)) <= 9.0;
        this.sawFallback |= arena.ctx().playerFeet().distSqr(arena.at(FALLBACK_X, 0, BOX_Z)) <= 9.0;
        if (!arena.stateAt(SECOND_TARGET_X, 0, 0).isAir()) {
            return null;
        }
        if (this.targetsClearedAt < 0) {
            this.targetsClearedAt = elapsedTicks;
            arena.note("both clear targets became air before the deposit handoff settled; waiting for the box result");
        }
        if (arena.baritone().getRestockProcess() != null
                && arena.baritone().getRestockProcess().isActive()) {
            return null;
        }
        if (elapsedTicks - this.targetsClearedAt < 40) {
            return null;
        }
        int blockedWhite = ScenarioInventory.countContainer(arena, BLOCKED_X, 0, BOX_Z, Items.WHITE_CONCRETE);
        int blockedRed = ScenarioInventory.countContainer(arena, BLOCKED_X, 0, BOX_Z, Items.RED_CONCRETE);
        int fallbackRed = ScenarioInventory.countContainer(arena, FALLBACK_X, 0, BOX_Z, Items.RED_CONCRETE);
        int carriedRed = ScenarioInventory.countPlayer(arena, Items.RED_CONCRETE);
        arena.note("timeout evidence at t=%d: targets=clear, visited blocked=%b/fallback=%b, blocked white=%d, blocked red=%d, fallback red=%d, carried red=%d",
                elapsedTicks, this.sawBlocked, this.sawFallback, blockedWhite, blockedRed, fallbackRed, carriedRed);
        if (!this.sawBlocked || !this.sawFallback) {
            return Verdict.fail("both targets cleared without proving visits to blocked=%b and fallback=%b",
                    this.sawBlocked, this.sawFallback);
        }
        if (blockedWhite != 1 || blockedRed != 0) {
            return Verdict.fail("the timed-out box mutated: white marker=%d, red deposit=%d; expected 1 and 0",
                    blockedWhite, blockedRed);
        }
        return fallbackRed > 0 && carriedRed == 0
                ? Verdict.pass("the timed-out box stayed unchanged and the fallback received the carried junk")
                : Verdict.fail("fallback deposit did not complete: fallback red=%d, carried red=%d", fallbackRed, carriedRed);
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "visited blocked=" + this.sawBlocked + ", fallback=" + this.sawFallback
                + ", target2=" + arena.stateAt(SECOND_TARGET_X, 0, 0).getBlock()
                + ", blocked white=" + ScenarioInventory.countContainer(arena, BLOCKED_X, 0, BOX_Z, Items.WHITE_CONCRETE)
                + ", blocked red=" + ScenarioInventory.countContainer(arena, BLOCKED_X, 0, BOX_Z, Items.RED_CONCRETE)
                + ", fallback red=" + ScenarioInventory.countContainer(arena, FALLBACK_X, 0, BOX_Z, Items.RED_CONCRETE);
    }
}
