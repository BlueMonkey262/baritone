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
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashMap;
import java.util.Map;

/**
 * The fork's headline feature, end to end: start a build with less material than it needs and a
 * stocked shulker box registered nearby, and expect it to finish anyway.
 * <p>
 * The inventory is deliberately short by a third. Anything less and the builder might scrape
 * through without ever asking for a restock, and the scenario would pass while testing nothing --
 * which is why finishing is not sufficient here. The restock process must also have taken control
 * at some point; a ring that goes up without it means the material came from somewhere the test
 * did not intend.
 */
public final class RestockFromBoxScenario extends AbstractBoxBuildScenario {

    /** Where the shulker box goes, relative to the arena origin. */
    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;

    /** Short of {@link #TOTAL_BLOCKS} by design. */
    private static final int STARTING_MATERIAL = 32;

    private boolean sawRestock;
    private boolean sawRestockMidBuild;

    @Override
    public String name() {
        return "restock-from-box";
    }

    @Override
    public String description() {
        return "Finish a build that starts " + (TOTAL_BLOCKS - STARTING_MATERIAL)
                + " blocks short, by fetching from a registered shulker box";
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
        settings.put("restockIndexBeforeBuild", true);
        return settings;
    }

    @Override
    protected void stageSupplies(TestArena arena) {
        arena.command("give @s " + MATERIAL_ID + " " + STARTING_MATERIAL);
        // Block entity contents via setblock is the most version-sensitive line in the harness: the
        // item NBT shape has changed before and will again. If this scenario reports the box as
        // missing, check this command against the current format before suspecting Baritone.
        arena.setBlock(BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"" + MATERIAL_ID + "\",Count:64b,Slot:0b}]}");
    }

    @Override
    protected boolean suppliesStaged(TestArena arena) {
        return arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock;
    }

    @Override
    protected void beforeBuild(TestArena arena) {
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so the box at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        // Contents are left unrecorded on purpose: an unknown box is what a freshly registered one
        // looks like, and it makes the run exercise indexing rather than skipping straight past it.
        arena.note("registered an unindexed box at %s holding 64 %s", box, MATERIAL_ID);
    }

    @Override
    public TestScenario.Verdict poll(TestArena arena, int elapsedTicks) {
        if (inControl(arena).toLowerCase().contains("restock")) {
            int placed = TOTAL_BLOCKS - countMissing(arena);
            if (!this.sawRestock) {
                this.sawRestock = true;
                arena.note("restock took control at t=%ds, with %d blocks placed", elapsedTicks / 20, placed);
            }
            // Restocking before a single block is placed is the pre-build indexing trip, which
            // happens whether or not the builder ever runs short. Only a trip taken mid-build is
            // evidence that running short was handled.
            if (placed > 0 && !this.sawRestockMidBuild) {
                this.sawRestockMidBuild = true;
                arena.note("restock took control mid-build at t=%ds, with %d of %d placed",
                        elapsedTicks / 20, placed, TOTAL_BLOCKS);
            }
        }
        TestScenario.Verdict verdict = super.poll(arena, elapsedTicks);
        if (verdict != null && verdict.pass && !this.sawRestockMidBuild) {
            return Verdict.fail(
                    "the ring went up starting %d blocks short, but restocking only ever ran before "
                            + "the first block was placed (indexing), so the shortfall was not what triggered it",
                    TOTAL_BLOCKS - STARTING_MATERIAL
            );
        }
        return verdict;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return super.timeoutDiagnosis(arena)
                + "; restock took control: " + this.sawRestock
                + ", mid-build: " + this.sawRestockMidBuild;
    }
}
