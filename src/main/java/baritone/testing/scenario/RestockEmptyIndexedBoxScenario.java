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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.testing.scenario;

import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A recorded index is only a candidate-selection hint. The farther stale box is known to contain
 * the material, while the nearer fallback is unindexed. Candidate ordering must pick the indexed
 * stale entry first; live slots must then correct it and continue to the fallback rather than
 * returning to the build.
 */
public final class RestockEmptyIndexedBoxScenario extends AbstractBoxBuildScenario {

    private static final int EMPTY_BOX_X = -4;
    private static final int EMPTY_BOX_Z = 10;
    private static final int STOCKED_BOX_X = 2;
    private static final int STOCKED_BOX_Z = 4;
    private static final int RECORDED_COUNT = 64;

    private boolean arrivedAtEmptyBox;
    private boolean targetedStockedBox;
    private boolean returnedToBuildBeforeFallback;

    @Override
    public String name() {
        return "restock-empty-indexed-box";
    }

    @Override
    public String description() {
        return "A farther stale indexed box is corrected live and falls through to a nearer unindexed box without returning to the build";
    }

    @Override
    public int tickBudget() {
        return 20 * 300;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", true);
        // Leave the nearer fallback unindexed. Pre-build indexing would convert it into an indexed
        // candidate and let distance select it before the stale recorded entry.
        settings.put("restockIndexBeforeBuild", false);
        return settings;
    }

    @Override
    protected void stageSupplies(TestArena arena) {
        // The stocked form documents the old observation. The later /setblock is deliberately the
        // live box the restock process will open, while beforeBuild installs that earlier snapshot
        // in the persistent collection exactly as a previous indexing run would have done.
        arena.setBlock(EMPTY_BOX_X, 0, EMPTY_BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"" + MATERIAL_ID + "\",count:"
                        + RECORDED_COUNT + ",Slot:0b}]}");
        arena.setBlock(STOCKED_BOX_X, 0, STOCKED_BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"" + MATERIAL_ID + "\",count:"
                        + RECORDED_COUNT + ",Slot:0b}]}");
        // This is intentionally after the stocked observation: the final live box is empty even
        // though its registered index says it previously held the material.
        arena.setBlock(EMPTY_BOX_X, 0, EMPTY_BOX_Z, "minecraft:shulker_box[facing=up]");
    }

    @Override
    protected boolean suppliesStaged(TestArena arena) {
        return arena.stateAt(EMPTY_BOX_X, 0, EMPTY_BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(STOCKED_BOX_X, 0, STOCKED_BOX_Z).getBlock() instanceof ShulkerBoxBlock;
    }

    @Override
    protected void beforeBuild(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so the stale and fallback boxes could not be registered");
            return;
        }
        BetterBlockPos empty = arena.at(EMPTY_BOX_X, 0, EMPTY_BOX_Z);
        BetterBlockPos stocked = arena.at(STOCKED_BOX_X, 0, STOCKED_BOX_Z);
        world.getRestockBoxes().addBox(empty);
        world.getRestockBoxes().addBox(stocked);
        // updateContents is the collection's persisted representation of a completed indexing run.
        // Candidate ordering puts indexed contents before unindexed boxes, then breaks ties by
        // distance: this farther stale entry must therefore be selected before the nearer fallback.
        world.getRestockBoxes().updateContents(empty,
                Collections.singletonMap(Blocks.WHITE_CONCRETE.asItem(), RECORDED_COUNT));
        arena.note("registered farther stale index %s -> %d %s; its live box is empty; nearer fallback %s remains unindexed",
                empty, RECORDED_COUNT, MATERIAL_ID, stocked);
    }

    @Override
    public TestScenario.Verdict poll(TestArena arena, int elapsedTicks) {
        BetterBlockPos empty = arena.at(EMPTY_BOX_X, 0, EMPTY_BOX_Z);
        BetterBlockPos stocked = arena.at(STOCKED_BOX_X, 0, STOCKED_BOX_Z);
        String controller = inControl(arena);
        boolean restocking = controller.toLowerCase().contains("restock");

        if (restocking && near(arena, empty)) {
            this.arrivedAtEmptyBox = true;
            arena.note("arrived at the live-empty indexed box at t=%ds", elapsedTicks / 20);
        }
        if (this.arrivedAtEmptyBox && restocking && near(arena, stocked)) {
            this.targetedStockedBox = true;
            arena.note("selected the nearer unindexed fallback at t=%ds", elapsedTicks / 20);
        }
        if (this.arrivedAtEmptyBox && !this.targetedStockedBox && near(arena, targets(arena).get(0))) {
            this.returnedToBuildBeforeFallback = true;
            return Verdict.fail("after reaching the live-empty indexed box, restocking returned to the build before trying the fallback box");
        }

        TestScenario.Verdict verdict = super.poll(arena, elapsedTicks);
        if (verdict != null && verdict.pass) {
            if (!this.arrivedAtEmptyBox) {
                return Verdict.fail("the ring completed without reaching the stale indexed box, so live-index correction was not exercised");
            }
            if (!this.targetedStockedBox) {
                return Verdict.fail("the ring completed without restocking from the unindexed fallback box after the stale index was disproved");
            }
        }
        return verdict;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return super.timeoutDiagnosis(arena)
                + "; arrived at empty box=" + this.arrivedAtEmptyBox
                + ", targeted fallback=" + this.targetedStockedBox
                + ", returned to build=" + this.returnedToBuildBeforeFallback;
    }

    private static boolean near(TestArena arena, BetterBlockPos position) {
        return arena.ctx().playerFeet().distSqr(position) <= 16.0;
    }
}
