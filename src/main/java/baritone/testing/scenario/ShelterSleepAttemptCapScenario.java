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
import baritone.api.utils.input.Input;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashMap;
import java.util.Map;

/** Refused beds must stop being clicked after shelterMaxSleepAttempts. */
public final class ShelterSleepAttemptCapScenario extends TestScenario {

    private static final int RALLY_BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int BED_X = 6;
    private static final int BED_Z = 0;
    private static final int WALL_X = 10;

    private static final int MAX_ATTEMPTS = 2;

    private boolean sawDamage;
    private boolean sawRallyBox;
    private boolean lastRightClick;
    private int attemptsObserved;
    private int lastAttemptTick = -1;
    private float initialHealth;

    @Override
    public String name() {
        return "shelter-sleep-attempt-cap";
    }

    @Override
    public String description() {
        return "Stop retrying a bed after the configured number of refused sleep attempts";
    }

    @Override
    public int tickBudget() {
        return 20 * 45;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", true);
        settings.put("allowInventory", true);
        settings.put("shelterOnAttack", true);
        settings.put("shelterMinHits", 1);
        settings.put("shelterThreatMemoryTicks", 200);
        settings.put("shelterMaxRetreatDistance", 32.0D);
        settings.put("shelterUnloadOnRetreat", false);
        settings.put("shelterSleepInBeds", true);
        settings.put("shelterBedSearchRadius", 16);
        settings.put("shelterMaxSleepAttempts", MAX_ATTEMPTS);
        settings.put("shelterRetryDelayTicks", 1);
        settings.put("shelterMaxWaitTicks", 80);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.command("difficulty normal");
        arena.command("time set night");
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        arena.command("give @s minecraft:iron_pickaxe 1");
        arena.setBlock(RALLY_BOX_X, 0, BOX_Z, "minecraft:shulker_box[facing=up]");
        // The bed is valid and reachable, but a persistent hostile stands within the server's
        // sleep exclusion radius, so every genuine click is refused.
        arena.setBlock(BED_X, 0, BED_Z,
                "minecraft:red_bed[facing=east,part=foot]");
        arena.setBlock(BED_X + 1, 0, BED_Z,
                "minecraft:red_bed[facing=east,part=head]");
        arena.fill(WALL_X, 0, -2, WALL_X, 2, 2, "minecraft:stone");
        BetterBlockPos attacker = arena.at(1, 0, 0);
        BetterBlockPos bedGuard = arena.at(BED_X + 2, 0, BED_Z);
        arena.command(String.format(
                "summon minecraft:zombie %d %d %d {PersistenceRequired:1b,CanPickUpLoot:0b}",
                attacker.x, attacker.y, attacker.z));
        arena.command(String.format(
                "summon minecraft:zombie %d %d %d {PersistenceRequired:1b,CanPickUpLoot:0b}",
                bedGuard.x, bedGuard.y, bedGuard.z));
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.ctx().world().getDifficulty() == Difficulty.NORMAL
                && !arena.ctx().world().isBrightOutside()
                && arena.stateAt(RALLY_BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(BED_X, 0, BED_Z).getBlock() instanceof BedBlock
                && arena.stateAt(BED_X + 1, 0, BED_Z).getBlock() instanceof BedBlock
                && arena.stateAt(WALL_X, 1, 0).is(Blocks.STONE)
                && ScenarioInventory.countPlayer(arena, Items.IRON_PICKAXE) == 1;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so the rally box could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(arena.at(RALLY_BOX_X, 0, BOX_Z));
        this.initialHealth = arena.ctx().player().getHealth();
        arena.baritone().getMineProcess().mineByName("minecraft:stone");
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        this.sawDamage |= arena.ctx().player().getHealth() < this.initialHealth;
        this.sawRallyBox |= arena.ctx().playerFeet().distSqr(arena.at(RALLY_BOX_X, 0, BOX_Z)) <= 9.0;
        boolean rightClick = arena.baritone().getInputOverrideHandler().isInputForcedDown(Input.CLICK_RIGHT)
                && nearBed(arena);
        if (rightClick && !this.lastRightClick) {
            this.attemptsObserved++;
            this.lastAttemptTick = elapsedTicks;
        }
        this.lastRightClick = rightClick;

        if (arena.ctx().player().isSleeping()) {
            return Verdict.fail("the bed accepted a sleep attempt; the refusal fixture did not exercise the retry cap");
        }
        if (this.attemptsObserved > MAX_ATTEMPTS) {
            return Verdict.fail("shelter clicked the refused bed %d times despite shelterMaxSleepAttempts=%d",
                    this.attemptsObserved, MAX_ATTEMPTS);
        }
        // Two observed refusal clicks followed by a quiet interval prove the process transitioned
        // to bounded waiting instead of immediately walking back for attempt three.
        if (this.sawDamage && this.sawRallyBox && this.attemptsObserved == MAX_ATTEMPTS
                && elapsedTicks - this.lastAttemptTick > 40
                && arena.baritone().getShelterProcess().isActive()) {
            return Verdict.pass("the valid but refused bed was clicked exactly twice, then shelter remained in bounded waiting");
        }
        if (elapsedTicks >= 20 * 20) {
            if (!this.sawDamage) {
                return Verdict.fail("the hostile fixture never damaged the player");
            }
            if (this.attemptsObserved == 0) {
                return Verdict.fail("shelter reached no refused-bed attempt; rally box reached=%b", this.sawRallyBox);
            }
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("damage=%b,rally=%b,attempts=%d/%d,lastAttempt=%d,shelterActive=%b,player=%s",
                this.sawDamage, this.sawRallyBox, this.attemptsObserved, MAX_ATTEMPTS, this.lastAttemptTick,
                arena.baritone().getShelterProcess().isActive(), arena.ctx().playerFeet());
    }

    private static boolean nearBed(TestArena arena) {
        return arena.ctx().playerFeet().distSqr(arena.at(BED_X, 0, BED_Z)) <= 25.0;
    }
}
