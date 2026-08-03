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

import baritone.api.utils.BlockOptionalMeta;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/** Get-to-block must not open an incidental chest when arrival interaction is disabled. */
public final class PathRightClickArrivalOffScenario extends TestScenario {

    private static final int CHEST_X = 5;
    private static final int CHEST_Z = 0;

    @Override
    public String name() {
        return "path-right-click-arrival-off";
    }

    @Override
    public String description() {
        return "Reach a chest without opening it when rightClickContainerOnArrival is false";
    }

    @Override
    public int tickBudget() {
        return 20 * 90;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", false);
        settings.put("allowPlace", false);
        settings.put("rightClickContainerOnArrival", false);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.setBlock(CHEST_X, 0, CHEST_Z, "minecraft:chest[facing=south]");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(0, -1, 0).is(Blocks.STONE)
                && arena.stateAt(CHEST_X, 0, CHEST_Z).is(Blocks.CHEST)
                && ScenarioInventory.countNonEmptyContainerSlots(arena, CHEST_X, 0, CHEST_Z) == 0
                && arena.baritone().getContainerInteractionBehavior().openContainer() == null;
    }

    @Override
    public void start(TestArena arena) {
        arena.note("walking to chest at %s with rightClickContainerOnArrival=false; initial menu=%s",
                arena.at(CHEST_X, 0, CHEST_Z),
                arena.baritone().getContainerInteractionBehavior().openContainer());
        arena.baritone().getGetToBlockProcess().getToBlock(new BlockOptionalMeta(Blocks.CHEST));
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        boolean open = arena.baritone().getContainerInteractionBehavior().openContainer() != null;
        boolean near = arena.ctx().playerFeet().distSqr(arena.at(CHEST_X, 0, CHEST_Z)) <= 9.0;
        boolean active = arena.baritone().getGetToBlockProcess().isActive();
        arena.note("arrival evidence at t=%d: near=%b, menu open=%b, get-to active=%b, player=%s",
                elapsedTicks, near, open, active, arena.ctx().playerFeet());
        if (open) {
            return Verdict.fail("the arrival interaction opened the incidental chest despite the setting being off");
        }
        if (near && !active) {
            return Verdict.pass("reached the chest without opening its menu");
        }
        return null;
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return "near=" + (arena.ctx().playerFeet().distSqr(arena.at(CHEST_X, 0, CHEST_Z)) <= 9.0)
                + ", menu open=" + (arena.baritone().getContainerInteractionBehavior().openContainer() != null)
                + ", get-to active=" + arena.baritone().getGetToBlockProcess().isActive()
                + ", player=" + arena.ctx().playerFeet();
    }
}
