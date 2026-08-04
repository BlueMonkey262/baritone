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

import baritone.testing.TestArena;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.Map;

/** A capable unload box must receive the whole eligible load before work resumes. */
public final class DumpWholeLoadBeforeReturnScenario extends AbstractShulkerDumpScenario {

    @Override
    public String name() {
        return "dump-whole-load-before-return";
    }

    @Override
    public String description() {
        return "Empty all eligible rubble into one capable box before returning to the clear job";
    }

    @Override
    protected Item[] dumpItems() {
        // Twenty-four junk stacks plus the three protected items leave nine free slots. The first
        // unload fills 24 slots, then the two different clear drops leave 31 free slots; the
        // threshold below 32 starts the second unload with a 26-stack total that fits in one box.
        return Arrays.copyOf(DUMP_ITEMS, 24);
    }

    @Override
    protected int expectedFreeSlots() {
        return 9;
    }

    @Override
    protected String targetBlockId(int index) {
        return index == 1 ? "minecraft:dirt" : super.targetBlockId(index);
    }

    @Override
    protected boolean targetStaged(TestArena arena, int index) {
        return arena.stateAt(8 + index, 0, 0).is(index == 1 ? Blocks.DIRT : Blocks.STONE);
    }

    @Override
    protected int[] boxXs() {
        return new int[]{2};
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = super.settings();
        // The staged inventory has nine free slots. The threshold deliberately causes an initial
        // unload, then the two different clear drops bring the post-trip free count below 32.
        settings.put("shulkerDumpWhenFreeSlotsBelow", 32);
        return settings;
    }

    private int targetsClearedAt = -1;

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        Verdict verdict = super.poll(arena, elapsedTicks);
        if (verdict == null || !verdict.pass) {
            return verdict;
        }
        if (this.targetsClearedAt < 0
                && arena.stateAt(8, 0, 0).isAir()
                && arena.stateAt(9, 0, 0).isAir()) {
            this.targetsClearedAt = elapsedTicks;
        }
        if (arena.baritone().getRestockProcess() != null
                && arena.baritone().getRestockProcess().isActive()) {
            return null;
        }
        if (this.targetsClearedAt >= 0 && elapsedTicks - this.targetsClearedAt < 40) {
            return null;
        }
        int carried = countCarriedRubble(arena);
        int boxedSlots = ScenarioInventory.countNonEmptyContainerSlots(arena, 2, 0, 4);
        arena.note("whole-load evidence at t=%d: eligible rubble carried=%d, destination occupied slots=%d, targets remaining=0",
                elapsedTicks, carried, boxedSlots);
        return carried == 0
                ? Verdict.pass("the unload returned only after emptying the eligible rubble load")
                : Verdict.fail("the clear resumed while %d eligible rubble items remained carried", carried);
    }
}
