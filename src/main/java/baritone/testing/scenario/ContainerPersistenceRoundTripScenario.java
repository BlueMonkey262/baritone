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

import baritone.api.cache.IRestockBox;
import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/** Persisted count, key, timestamp and missing flag must survive a collection reload. */
public final class ContainerPersistenceRoundTripScenario extends TestScenario {

    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int COUNT = 9;

    private boolean reloaded;
    private long creationTimestamp;
    private long lastIndexed;

    @Override
    public String name() {
        return "container-persistence-round-trip";
    }

    @Override
    public String description() {
        return "Round-trip a registered box's contents and missing flag through persisted storage";
    }

    @Override
    public int tickBudget() {
        return 20 * 30;
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
                && ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE) == COUNT;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos pos = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the persistence round-trip could not start");
            return;
        }
        world.getRestockBoxes().addBox(pos);
        Map<net.minecraft.world.item.Item, Integer> contents = new HashMap<>();
        contents.put(Items.WHITE_CONCRETE, COUNT);
        world.getRestockBoxes().updateContents(pos, contents);
        world.getRestockBoxes().setMissing(pos, true);
        IRestockBox before = world.getRestockBoxes().getBox(pos);
        this.creationTimestamp = before == null ? -1 : before.getCreationTimestamp();
        this.lastIndexed = before == null ? -1 : before.getLastIndexed();
        world.getRestockBoxes().reloadFromDisk();
        this.reloaded = true;
        arena.note("reloaded persisted box at %s: before count=%d, indexed=%d, missing=%b",
                pos, COUNT, this.lastIndexed, true);
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos pos = arena.at(BOX_X, 0, BOX_Z);
        IRestockBox box = world == null ? null : world.getRestockBoxes().getBox(pos);
        int live = ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE);
        boolean exact = this.reloaded && box != null
                && box.getLocation().equals(pos)
                && box.getCreationTimestamp() == this.creationTimestamp
                && box.getLastIndexed() == this.lastIndexed
                && box.countOf(Items.WHITE_CONCRETE) == COUNT
                && box.isMissing()
                && live == COUNT;
        arena.note("round-trip evidence at t=%d: box=%s, key=%s, indexed=%d/%d, missing=%s, indexed white=%d, live white=%d",
                elapsedTicks, box == null ? "missing" : "present", pos,
                box == null ? -1 : box.getLastIndexed(), this.lastIndexed,
                box != null && box.isMissing(), box == null ? -1 : box.countOf(Items.WHITE_CONCRETE), live);
        return exact
                ? Verdict.pass("the persisted box key, item count, indexed timestamp and missing flag survived reload")
                : Verdict.fail("the persisted box did not round-trip exactly: box=%s, indexed white=%d, live white=%d, missing=%s",
                        box == null ? "missing" : "present", box == null ? -1 : box.countOf(Items.WHITE_CONCRETE),
                        live, box != null && box.isMissing());
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        IRestockBox box = world == null ? null : world.getRestockBoxes().getBox(arena.at(BOX_X, 0, BOX_Z));
        return "reloaded=" + this.reloaded + ", box=" + (box == null ? "missing" : box.getLocation())
                + ", indexed white=" + (box == null ? -1 : box.countOf(Items.WHITE_CONCRETE))
                + ", live white=" + ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WHITE_CONCRETE)
                + ", missing=" + (box != null && box.isMissing());
    }
}
