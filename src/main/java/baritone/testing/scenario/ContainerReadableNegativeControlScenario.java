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
import baritone.testing.TestScenario;
import net.minecraft.world.item.Items;

/** A known-negative control: an empty real shulker must read as zero, not as missing. */
public final class ContainerReadableNegativeControlScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;

    @Override
    public String name() {
        return "container-readable-negative-control";
    }

    @Override
    public String description() {
        return "Read an empty real shulker as zero through the server-thread container oracle";
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 12, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 12, 4, 8, "minecraft:air");
        arena.command("clear @s");
        ContainerFixture.emptyBox(arena, BOX_X, 0, BOX_Z);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return ContainerFixture.isBox(arena, BOX_X, 0, BOX_Z)
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE) == 0
                && ScenarioInventory.countNonEmptyContainerSlots(arena, BOX_X, 0, BOX_Z) == 0;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("negative control staged: non-empty slots=%d, white concrete=%d",
                ScenarioInventory.countNonEmptyContainerSlots(arena, BOX_X, 0, BOX_Z),
                ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int slots = ScenarioInventory.countNonEmptyContainerSlots(arena, BOX_X, 0, BOX_Z);
        int white = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE);
        arena.note("negative read at t=%d: non-empty slots=%d, white=%d", elapsedTicks, slots, white);
        return slots == 0 && white == 0
                ? Verdict.pass("the empty container read returned zero without reporting a missing block entity")
                : Verdict.fail("empty container read returned slots=%d and white=%d; expected 0 and 0", slots, white);
    }
}
