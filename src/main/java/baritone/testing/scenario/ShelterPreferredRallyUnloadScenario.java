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
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Shelter unloading must prefer the rally box already reached under attack. */
public final class ShelterPreferredRallyUnloadScenario extends TestScenario {

    private static final int RALLY_BOX_X = 2;
    private static final int ALTERNATE_BOX_X = 10;
    private static final int BOX_Z = 4;
    private static final int BUILD_X = 6;
    private static final int BUILD_BLOCKS = 4;

    private static final Item[] JUNK = {
            Items.RED_CONCRETE, Items.ORANGE_CONCRETE, Items.YELLOW_CONCRETE, Items.LIME_CONCRETE,
            Items.GREEN_CONCRETE, Items.CYAN_CONCRETE, Items.LIGHT_BLUE_CONCRETE, Items.BLUE_CONCRETE,
            Items.PURPLE_CONCRETE, Items.MAGENTA_CONCRETE, Items.PINK_CONCRETE, Items.BROWN_CONCRETE,
            Items.BLACK_CONCRETE, Items.GRAY_CONCRETE, Items.LIGHT_GRAY_CONCRETE, Items.COBBLESTONE,
            Items.RED_WOOL, Items.ORANGE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL,
            Items.GREEN_WOOL, Items.CYAN_WOOL, Items.LIGHT_BLUE_WOOL, Items.BLUE_WOOL,
            Items.PURPLE_WOOL, Items.MAGENTA_WOOL, Items.PINK_WOOL, Items.BROWN_WOOL,
            Items.BLACK_WOOL, Items.GRAY_WOOL
    };

    private boolean sawDamage;
    private boolean sawRallyBox;
    private boolean sawAlternateBox;
    private boolean sawInventoryChange;
    private boolean sawReturnToWork;
    private boolean notedShelterSelection;
    private float initialHealth;
    private int initialJunk;

    @Override
    public String name() {
        return "shelter-preferred-rally-unload";
    }

    @Override
    public String description() {
        return "Unload carried rubble at the rally box chosen during a shelter retreat";
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
        settings.put("restockFromBoxes", true);
        settings.put("restockDumpJunk", false);
        settings.put("shelterOnAttack", true);
        settings.put("shelterMinHits", 1);
        settings.put("shelterThreatMemoryTicks", 200);
        settings.put("shelterMaxRetreatDistance", 32.0D);
        settings.put("shelterUnloadOnRetreat", true);
        settings.put("shelterSleepInBeds", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.command("difficulty normal");
        arena.fill(-4, -3, -12, 20, -1, 12, "minecraft:stone");
        arena.fill(-4, 0, -12, 20, 4, 12, "minecraft:air");
        arena.command("clear @s");
        for (Item item : JUNK) {
            arena.command("give @s " + BuiltInRegistries.ITEM.getKey(item) + " 1");
        }
        arena.command("give @s minecraft:white_concrete " + BUILD_BLOCKS);
        arena.setBlock(RALLY_BOX_X, 0, BOX_Z, "minecraft:shulker_box[facing=up]");
        arena.setBlock(ALTERNATE_BOX_X, 0, BOX_Z, "minecraft:shulker_box[facing=up]");
        BetterBlockPos zombie = arena.at(1, 0, 0);
        arena.command(String.format(
                "summon minecraft:zombie %d %d %d {PersistenceRequired:1b,CanPickUpLoot:0b}",
                zombie.x, zombie.y, zombie.z));
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.ctx().world().getDifficulty() == Difficulty.NORMAL
                && arena.stateAt(RALLY_BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(ALTERNATE_BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countPlayer(arena, Items.WHITE_CONCRETE) == BUILD_BLOCKS
                && countJunk(arena) == JUNK.length
                && arena.stateAt(BUILD_X, 0, 0).isAir();
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos rally = arena.at(RALLY_BOX_X, 0, BOX_Z);
        BetterBlockPos alternate = arena.at(ALTERNATE_BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the rally and alternate boxes could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(rally);
        world.getRestockBoxes().addBox(alternate);
        this.initialHealth = arena.ctx().player().getHealth();
        this.initialJunk = countJunk(arena);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(), schematic(), new Vec3i(arena.at(BUILD_X, 0, 0).x,
                        arena.at(BUILD_X, 0, 0).y, arena.at(BUILD_X, 0, 0).z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        this.sawDamage |= arena.ctx().player().getHealth() < this.initialHealth;
        // Only shelter's retreat is in scope. The ordinary build path can legitimately pass near
        // a depot before the zombie lands its first hit; counting that as a shelter choice makes
        // the negative guard report a false alternate-first failure.
        if (this.sawDamage) {
            this.sawRallyBox |= near(arena, arena.at(RALLY_BOX_X, 0, BOX_Z));
            this.sawAlternateBox |= near(arena, arena.at(ALTERNATE_BOX_X, 0, BOX_Z));
            BetterBlockPos selected = arena.baritone().getShelterProcess().getRallyBox();
            if (!this.notedShelterSelection) {
                this.notedShelterSelection = true;
                arena.note("post-damage shelter evidence: selected=%s, rallyNear=%b, alternateNear=%b",
                        selected, this.sawRallyBox, this.sawAlternateBox);
            }
            if (selected != null && !selected.equals(arena.at(RALLY_BOX_X, 0, BOX_Z))) {
                return Verdict.fail("shelter selected %s instead of the nearer rally box %s", selected,
                        arena.at(RALLY_BOX_X, 0, BOX_Z));
            }
        }
        if (this.sawRallyBox && countJunk(arena) < this.initialJunk) {
            this.sawInventoryChange = true;
        }
        if (this.sawInventoryChange && !arena.baritone().getShelterProcess().isActive()
                && completedTargets(arena) > 0) {
            this.sawReturnToWork = true;
        }

        if (this.sawAlternateBox && !this.sawRallyBox) {
            return Verdict.fail("shelter reached the alternate depot before the selected rally box");
        }
        if (this.sawAlternateBox && this.sawRallyBox && !this.sawInventoryChange) {
            return Verdict.fail("the player visited both boxes but carried rubble was not unloaded at the rally box");
        }
        if (completedTargets(arena) == BUILD_BLOCKS) {
            if (!this.sawDamage || !this.sawRallyBox || !this.sawInventoryChange || !this.sawReturnToWork) {
                return Verdict.fail("the build completed without proving damage, rally arrival, unload, and return: damage=%b rally=%b unload=%b return=%b",
                        this.sawDamage, this.sawRallyBox, this.sawInventoryChange, this.sawReturnToWork);
            }
            if (this.sawAlternateBox) {
                return Verdict.fail("shelter left the selected rally box and visited the alternate depot before resuming");
            }
            return Verdict.pass("hostile damage selected the nearby rally box, unloading happened there, and work resumed");
        }
        if (elapsedTicks >= 20 * 20 && !this.sawDamage) {
            return Verdict.fail("the hostile fixture never damaged the player, so shelter was not exercised");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("damage=%b,rally=%b,alternate=%b,unload=%b,return=%b,targets=%d/%d,junk=%d/%d,player=%s",
                this.sawDamage, this.sawRallyBox, this.sawAlternateBox, this.sawInventoryChange,
                this.sawReturnToWork, completedTargets(arena), BUILD_BLOCKS, countJunk(arena),
                this.initialJunk, arena.ctx().playerFeet());
    }

    private static StaticSchematic schematic() {
        BlockState[][][] states = new BlockState[BUILD_BLOCKS][1][1];
        for (int x = 0; x < BUILD_BLOCKS; x++) {
            states[x][0][0] = Blocks.WHITE_CONCRETE.defaultBlockState();
        }
        return new StaticSchematic(states);
    }

    private static int completedTargets(TestArena arena) {
        int complete = 0;
        for (int x = 0; x < BUILD_BLOCKS; x++) {
            if (arena.stateAt(BUILD_X + x, 0, 0).is(Blocks.WHITE_CONCRETE)) {
                complete++;
            }
        }
        return complete;
    }

    private static int countJunk(TestArena arena) {
        int count = 0;
        for (Item item : JUNK) {
            count += ScenarioInventory.countPlayer(arena, item);
        }
        return count;
    }

    private static boolean near(TestArena arena, BetterBlockPos box) {
        return arena.ctx().playerFeet().distSqr(box) <= 9.0;
    }
}
