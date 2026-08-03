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

/** A known-positive control for the server-thread container oracle. */
public final class ContainerReadablePositiveControlScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int COUNT = 7;

    @Override
    public String name() {
        return "container-readable-positive-control";
    }

    @Override
    public String description() {
        return "Read a known non-empty shulker through the server-thread container oracle";
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 12, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 12, 4, 8, "minecraft:air");
        arena.command("clear @s");
        ContainerFixture.emptyBox(arena, BOX_X, 0, BOX_Z);
        ContainerFixture.put(arena, BOX_X, 0, BOX_Z, 0, Items.WHITE_CONCRETE, COUNT);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return ContainerFixture.isBox(arena, BOX_X, 0, BOX_Z)
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE) == COUNT
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE) == 0;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("positive control staged: white concrete in box=%d, red concrete in box=%d",
                ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE),
                ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int white = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE);
        int red = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.RED_CONCRETE);
        arena.note("positive read at t=%d: white=%d, red=%d", elapsedTicks, white, red);
        return white == COUNT && red == 0
                ? Verdict.pass("the positive container read returned the staged item and rejected an absent item")
                : Verdict.fail("positive read returned white=%d and red=%d; expected %d and 0", white, red, COUNT);
    }
}
