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
import net.minecraft.world.Difficulty;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Documents the fork shelter policy defect: a hostile hit sends the player to a registered box
 * even when it is far outside the immediate work area.
 */
public final class ShelterRetreatDistanceScenario extends TestScenario {

    private static final int BOX_X = 48;
    private static final int SAFE_RETREAT_DISTANCE = 12;
    private static final int BUILD_X = 6;
    private static final int WALL_X = 20;

    private boolean sawDamage;
    private boolean sawShelter;
    private float initialHealth;
    private BetterBlockPos selectedBox;
    private int selectedDistance;

    @Override
    public String name() {
        return "shelter-retreat-distance";
    }

    @Override
    public String description() {
        return "A hostile hit must not send sheltering toward a far registered box through unrelated terrain";
    }

    @Override
    public int tickBudget() {
        return 20 * 30;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("shelterOnAttack", true);
        settings.put("shelterMinHits", 1);
        settings.put("shelterThreatMemoryTicks", 20 * 20);
        settings.put("shelterUnloadOnRetreat", false);
        settings.put("shelterSleepInBeds", false);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("allowInventory", true);
        settings.put("restockFromBoxes", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.command("difficulty normal");
        arena.fill(-4, -3, -12, 52, -1, 12, "minecraft:stone");
        arena.fill(-4, 0, -12, 52, 6, 12, "minecraft:air");
        // The roof keeps the staged zombie from burning in daylight before it can land the hit
        // that triggers sheltering.
        arena.fill(-4, 3, -12, 52, 3, 12, "minecraft:stone");
        // A direct route through this wall is deliberately mineable: the live defect tunnels
        // through unrelated terrain while fleeing to the remote box.
        arena.fill(WALL_X, 0, -12, WALL_X, 3, 12, "minecraft:stone");
        arena.setBlock(BOX_X, 0, 0, "minecraft:shulker_box[facing=up]");
        BetterBlockPos zombie = arena.at(1, 0, 0);
        arena.command(String.format("summon minecraft:zombie %d %d %d {PersistenceRequired:1b,CanPickUpLoot:0b}",
                zombie.x, zombie.y, zombie.z));
        arena.command("give @s minecraft:white_concrete 1");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(0, 3, 0).is(Blocks.STONE)
                && arena.stateAt(WALL_X, 1, 0).is(Blocks.STONE)
                && arena.stateAt(BOX_X, 0, 0).getBlock() instanceof ShulkerBoxBlock
                && arena.ctx().world().getDifficulty() == Difficulty.NORMAL;
    }

    @Override
    public void start(TestArena arena) {
        BetterBlockPos box = arena.at(BOX_X, 0, 0);
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world != null) {
            world.getRestockBoxes().addBox(box);
        }
        this.initialHealth = arena.ctx().player().getHealth();
        arena.note("registered distant shelter box at %s; safe retreat bound is %d blocks", box, SAFE_RETREAT_DISTANCE);
        BetterBlockPos buildOrigin = arena.at(BUILD_X, 0, 0);
        arena.baritone().getBuilderProcess().build("harness-" + name(), schematic(),
                new Vec3i(buildOrigin.x, buildOrigin.y, buildOrigin.z));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        this.sawDamage |= arena.ctx().player().getHealth() < this.initialHealth;
        this.sawShelter |= arena.baritone().getShelterProcess().isActive();
        BetterBlockPos retreatBox = arena.baritone().getShelterProcess().getRallyBox();
        // Latch the first selection on every poll tick. A failed path can clear rallyBox before a
        // later verdict tick, but the selection itself is the policy decision this scenario tests.
        if (this.selectedBox == null && retreatBox != null) {
            this.selectedBox = retreatBox;
            this.selectedDistance = (int) Math.ceil(Math.sqrt(feet.distSqr(this.selectedBox)));
        }
        if (this.sawDamage && this.selectedBox != null) {
            if (this.selectedDistance > SAFE_RETREAT_DISTANCE) {
                return Verdict.fail("selected a shelter box %d blocks away after hostile damage; bound is %d",
                        this.selectedDistance, SAFE_RETREAT_DISTANCE);
            }
            return Verdict.pass(String.format("selected shelter box %s within the %d-block bound after hostile damage",
                    this.selectedBox, SAFE_RETREAT_DISTANCE));
        }
        if (elapsedTicks >= 20 * 12) {
            if (!this.sawDamage) {
                return Verdict.fail("fixture never received hostile damage");
            }
            return Verdict.fail(this.sawShelter
                    ? "hostile damage engaged shelter but no selected retreat target was observable"
                    : "hostile damage did not engage shelter");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("damage=%b, shelterActive=%b, selectedBox=%s, selectedDistance=%d, wallIntact=%b",
                this.sawDamage, arena.baritone().getShelterProcess().isActive(), this.selectedBox, this.selectedDistance,
                arena.stateAt(WALL_X, 1, 0).is(Blocks.STONE));
    }

    private static StaticSchematic schematic() {
        return new StaticSchematic(new BlockState[][][]{{{ScenarioInventory.block("minecraft:white_concrete").defaultBlockState()}}});
    }
}
