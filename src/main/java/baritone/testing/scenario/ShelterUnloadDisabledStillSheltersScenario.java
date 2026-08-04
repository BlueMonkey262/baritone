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
import net.minecraft.core.Vec3i;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import baritone.utils.schematic.StaticSchematic;

import java.util.HashMap;
import java.util.Map;

/** Disabling shelter unloading must not disable the hostile-hit retreat itself. */
public final class ShelterUnloadDisabledStillSheltersScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int TARGET_X = 6;
    private static final int TARGETS = 3;

    private float initialHealth;
    private boolean sawDamage;
    private boolean sawRally;

    @Override
    public String name() {
        return "shelter-unload-disabled-still-shelters";
    }

    @Override
    public String description() {
        return "Retreat to the registered rally box after a hostile hit without opening it when unloading is disabled";
    }

    @Override
    public int tickBudget() {
        return 20 * 150;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", true);
        settings.put("shelterOnAttack", true);
        settings.put("shelterMinHits", 1);
        settings.put("shelterThreatMemoryTicks", 200);
        settings.put("shelterMaxRetreatDistance", 32.0D);
        settings.put("shelterUnloadOnRetreat", false);
        settings.put("shelterSleepInBeds", false);
        settings.put("shelterMaxWaitTicks", 80);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.command("difficulty normal");
        arena.fill(-4, -3, -10, 16, -1, 10, "minecraft:stone");
        arena.fill(-4, 0, -10, 16, 4, 10, "minecraft:air");
        arena.command("clear @s");
        arena.command("give @s minecraft:white_concrete " + TARGETS);
        arena.setBlock(BOX_X, 0, BOX_Z, "minecraft:shulker_box[facing=up]");
        BetterBlockPos attacker = arena.at(1, 0, 0);
        arena.command(String.format(
                "summon minecraft:zombie %d %d %d {PersistenceRequired:1b,CanPickUpLoot:0b}",
                attacker.x, attacker.y, attacker.z));
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.ctx().world().getDifficulty() == Difficulty.NORMAL
                && arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countNonEmptyContainerSlots(arena, BOX_X, 0, BOX_Z) == 0
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == TARGETS
                && arena.stateAt(TARGET_X, 0, 0).isAir();
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        BetterBlockPos target = arena.at(TARGET_X, 0, 0);
        if (world == null) {
            arena.note("no world data, so the rally box at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        this.initialHealth = arena.ctx().player().getHealth();
        arena.note("rally box=%s starts empty; shelterUnloadOnRetreat=false; hostile is at %s",
                box, arena.at(1, 0, 0));
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(target.x, target.y, target.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        this.sawDamage |= arena.ctx().player().getHealth() < this.initialHealth;
        this.sawRally |= arena.ctx().playerFeet().distSqr(arena.at(BOX_X, 0, BOX_Z)) <= 9.0;
        int boxSlots = ScenarioInventory.countNonEmptyContainerSlots(arena, BOX_X, 0, BOX_Z);
        boolean shelterActive = arena.baritone().getShelterProcess().isActive();
        if (boxSlots != 0) {
            return Verdict.fail("the disabled-unload retreat changed the empty rally box to %d occupied slots", boxSlots);
        }
        if (this.sawDamage && this.sawRally) {
            arena.note("shelter evidence at t=%d: health %.1f->%.1f, rally=%s, shelterActive=%b, box slots=%d, player=%s",
                    elapsedTicks, this.initialHealth, arena.ctx().player().getHealth(), this.sawRally,
                    shelterActive, boxSlots, arena.ctx().playerFeet());
            return Verdict.pass("hostile damage engaged shelter and reached the rally while the unload handoff stayed disabled");
        }
        if (elapsedTicks >= 20 * 25 && !this.sawDamage) {
            return Verdict.fail("the hostile fixture never reduced player health, so shelter was not exercised");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("health=%.1f->%.1f,damage=%b,rally=%b,shelterActive=%b,boxSlots=%d,player=%s",
                this.initialHealth, arena.ctx().player().getHealth(), this.sawDamage, this.sawRally,
                arena.baritone().getShelterProcess().isActive(),
                ScenarioInventory.countNonEmptyContainerSlots(arena, BOX_X, 0, BOX_Z), arena.ctx().playerFeet());
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[TARGETS][1][1];
        for (int x = 0; x < TARGETS; x++) {
            states[x][0][0] = Blocks.WHITE_CONCRETE.defaultBlockState();
        }
        return new StaticSchematic(states);
    }
}
