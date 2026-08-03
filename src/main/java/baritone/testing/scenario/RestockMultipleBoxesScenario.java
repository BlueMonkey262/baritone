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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Makes one material shortage require material from two separate boxes. Before the build, a separate
 * orange-concrete request proves that the nearer white-concrete source lacks that one item. That
 * box must remain eligible for white concrete later: treating the orange miss as an operational
 * box failure makes the final 16 blocks unobtainable. One restock operation may visit both boxes,
 * so the assertion is about both live boxes being used after the build has started, not about two
 * separate process activations.
 */
public final class RestockMultipleBoxesScenario extends AbstractBoxBuildScenario {

    /** This nearer box is first tried for orange and supplies part of the white-concrete shortfall. */
    private static final int SECOND_WHITE_BOX_X = 2;
    private static final int SECOND_WHITE_BOX_Z = 4;
    /** This box holds the orange probe item and supplies the rest of the white-concrete shortfall. */
    private static final int FIRST_WHITE_BOX_X = -4;
    private static final int FIRST_WHITE_BOX_Z = 4;
    private static final int STARTING_MATERIAL = 8;
    private static final int FIRST_WHITE_COUNT = 24;
    private static final int SECOND_WHITE_COUNT = 16;

    private boolean sawFirstBox;
    private boolean sawSecondBox;

    @Override
    public String name() {
        return "restock-multiple-boxes";
    }

    @Override
    public String description() {
        return "Finish a ring from two undersized material boxes after one box misses a different requested item";
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
        // The orange probe must speculatively visit the nearer box; indexing would correctly know
        // it lacks orange and would skip the per-item failure this scenario protects.
        settings.put("restockIndexBeforeBuild", false);
        // A full extra stack would mask the deliberately split material supply.
        settings.put("restockExtraStacks", 0);
        return settings;
    }

    @Override
    protected void stageSupplies(TestArena arena) {
        // AbstractBoxBuildScenario has already issued 26.1.2's unqualified `clear @s`. Do not send
        // a second item-predicate clear here: this fixture only needs the known-empty inventory
        // created by the shared stage, followed by its deliberate eight-block starting stack.
        arena.command("give @s " + MATERIAL_ID + " " + STARTING_MATERIAL);
        // Keep this item-NBT shape in lockstep with RestockFromBoxScenario; it is intentionally
        // the harness's known version-sensitive shulker form.
        arena.setBlock(SECOND_WHITE_BOX_X, 0, SECOND_WHITE_BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"" + MATERIAL_ID
                        + "\",Count:" + SECOND_WHITE_COUNT + "b,Slot:0b}]}");
        arena.setBlock(FIRST_WHITE_BOX_X, 0, FIRST_WHITE_BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"" + MATERIAL_ID + "\",Count:"
                        + FIRST_WHITE_COUNT + "b,Slot:0b},{id:\"minecraft:orange_concrete\",Count:1b,Slot:1b}]}");
    }

    @Override
    protected boolean suppliesStaged(TestArena arena) {
        return arena.stateAt(FIRST_WHITE_BOX_X, 0, FIRST_WHITE_BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(SECOND_WHITE_BOX_X, 0, SECOND_WHITE_BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && countPlayerMaterial(arena) == STARTING_MATERIAL;
    }

    @Override
    protected void beforeBuild(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        if (world == null) {
            arena.note("no world data, so the two restock boxes could not be registered");
            return;
        }
        BetterBlockPos first = arena.at(FIRST_WHITE_BOX_X, 0, FIRST_WHITE_BOX_Z);
        BetterBlockPos second = arena.at(SECOND_WHITE_BOX_X, 0, SECOND_WHITE_BOX_Z);
        world.getRestockBoxes().addBox(first);
        world.getRestockBoxes().addBox(second);
        arena.note("confirmed %d white concrete in inventory and split %d + %d across %s and %s; "
                        + "each box alone is short of the %d-block build",
                STARTING_MATERIAL, FIRST_WHITE_COUNT, SECOND_WHITE_COUNT, first, second, TOTAL_BLOCKS);
    }

    @Override
    public void start(TestArena arena) {
        super.start(arena);
        // The nearer box holds white but no orange. On the old H5 behavior, that one miss put the
        // whole box in failedThisBuild, so it could not later supply its white concrete.
        boolean requested = arena.baritone().getRestockProcess().requestRestock(
                Collections.singletonMap(Blocks.ORANGE_CONCRETE.defaultBlockState(), 1), stack -> true);
        arena.note("requested orange-concrete probe before the white build: %b", requested);
    }

    @Override
    public TestScenario.Verdict poll(TestArena arena, int elapsedTicks) {
        String controller = inControl(arena);
        boolean restocking = controller.toLowerCase().contains("restock");
        int placed = TOTAL_BLOCKS - countMissing(arena);

        if (restocking && placed > 0) {
            BetterBlockPos first = arena.at(FIRST_WHITE_BOX_X, 0, FIRST_WHITE_BOX_Z);
            BetterBlockPos second = arena.at(SECOND_WHITE_BOX_X, 0, SECOND_WHITE_BOX_Z);
            if (near(arena, first) && !this.sawFirstBox) {
                this.sawFirstBox = true;
                arena.note("restocking reached the first white-concrete box mid-build at t=%ds", elapsedTicks / 20);
            }
            if (near(arena, second) && !this.sawSecondBox) {
                this.sawSecondBox = true;
                arena.note("restocking reached the second white-concrete box mid-build at t=%ds", elapsedTicks / 20);
            }
        }

        TestScenario.Verdict verdict = super.poll(arena, elapsedTicks);
        if (verdict != null && verdict.pass) {
            if (!this.sawFirstBox) {
                return Verdict.fail("the ring completed without restocking from the first undersized material box after blocks were placed");
            }
            if (!this.sawSecondBox) {
                return Verdict.fail("the ring completed without restocking from the second undersized material box after blocks were placed");
            }
        }
        return verdict;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return super.timeoutDiagnosis(arena)
                + "; first box=" + this.sawFirstBox
                + ", second box=" + this.sawSecondBox;
    }

    private static boolean near(TestArena arena, BetterBlockPos position) {
        return arena.ctx().playerFeet().distSqr(position) <= 16.0;
    }

    private static int countPlayerMaterial(TestArena arena) {
        return arena.ctx().player().getInventory().items.stream()
                .filter(stack -> stack.is(Blocks.WHITE_CONCRETE.asItem()))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

}
