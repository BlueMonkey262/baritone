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
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** A failed first restock candidate must release the path before the next candidate is tried. */
public final class RestockSwitchCancelsOldPathScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int FIRST_BOX_X = 2;
    private static final int FIRST_BOX_Z = 4;
    private static final int FALLBACK_BOX_X = -4;
    private static final int FALLBACK_BOX_Z = 8;

    private int startingFirstBoxWhite;
    private int startingFallbackBoxWhite;

    @Override
    public String name() {
        return "restock-switch-cancels-old-path";
    }

    @Override
    public String description() {
        return "Switch from an unusable first restock box to a later box without retaining the old path";
    }

    @Override
    public int tickBudget() {
        return 20 * 180;
    }

    @Override
    public Map<String, Object> settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowInventory", true);
        settings.put("allowBreak", true);
        settings.put("allowPlace", true);
        settings.put("restockFromBoxes", true);
        settings.put("restockIndexBeforeBuild", false);
        settings.put("restockExtraStacks", 0);
        settings.put("restockOpenTimeoutTicks", 20);
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-8, -3, -12, 16, -1, 12, "minecraft:stone");
        arena.fill(-8, 0, -12, 16, 4, 12, "minecraft:air");
        arena.command("clear @s");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        // The bottom slab occupies the lid clearance without looking like a full cube to the
        // pathing goal. The bot can reach this box, but the server refuses the open interaction.
        arena.setBlock(FIRST_BOX_X, 0, FIRST_BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:8,Slot:0b}]}");
        arena.setBlock(FIRST_BOX_X, 1, FIRST_BOX_Z, "minecraft:stone_slab[type=bottom]");
        arena.setBlock(FALLBACK_BOX_X, 0, FALLBACK_BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:8,Slot:0b}]}");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 1, 0).isAir()
                && arena.stateAt(FIRST_BOX_X, 0, FIRST_BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(FIRST_BOX_X, 1, FIRST_BOX_Z).is(Blocks.STONE_SLAB)
                && arena.stateAt(FALLBACK_BOX_X, 0, FALLBACK_BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countContainer(arena, FIRST_BOX_X, 0, FIRST_BOX_Z,
                        Blocks.WHITE_CONCRETE.asItem()) == 8
                && ScenarioInventory.countContainer(arena, FALLBACK_BOX_X, 0, FALLBACK_BOX_Z,
                        Blocks.WHITE_CONCRETE.asItem()) == 8
                && ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem()) == 0;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos first = arena.at(FIRST_BOX_X, 0, FIRST_BOX_Z);
        BetterBlockPos fallback = arena.at(FALLBACK_BOX_X, 0, FALLBACK_BOX_Z);
        if (world == null) {
            arena.note("no world data, so the two candidate boxes could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(first);
        world.getRestockBoxes().addBox(fallback);
        this.startingFirstBoxWhite = ScenarioInventory.countContainer(arena, FIRST_BOX_X, 0, FIRST_BOX_Z,
                Blocks.WHITE_CONCRETE.asItem());
        this.startingFallbackBoxWhite = ScenarioInventory.countContainer(arena, FALLBACK_BOX_X, 0, FALLBACK_BOX_Z,
                Blocks.WHITE_CONCRETE.asItem());
        arena.note("candidate-switch sources start at first=%d and fallback=%d; first must stay unchanged while fallback decreases",
                this.startingFirstBoxWhite, this.startingFallbackBoxWhite);
        BetterBlockPos target = arena.at(BUILD_X, 0, 0);
        arena.baritone().getBuilderProcess().build(
                "harness-" + name(),
                new StaticSchematic(new BlockState[][][]{{{
                        Blocks.STONE.defaultBlockState(), Blocks.WHITE_CONCRETE.defaultBlockState()
                }}}),
                new Vec3i(target.x, target.y, target.z)
        );
    }

    @Override
    public Verdict poll(TestArena arena, int elapsedTicks) {
        int firstWhite = ScenarioInventory.countContainer(arena, FIRST_BOX_X, 0, FIRST_BOX_Z,
                Blocks.WHITE_CONCRETE.asItem());
        int fallbackWhite = ScenarioInventory.countContainer(arena, FALLBACK_BOX_X, 0, FALLBACK_BOX_Z,
                Blocks.WHITE_CONCRETE.asItem());
        if (firstWhite != this.startingFirstBoxWhite) {
            return Verdict.fail("the blocked first candidate changed: first source white=%d (start=%d)",
                    firstWhite, this.startingFirstBoxWhite);
        }

        boolean built = arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE);
        if (!built) {
            if (elapsedTicks >= 20 * 12 && !arena.baritone().getBuilderProcess().isActive()) {
                return Verdict.fail("the fallback build stopped before placing its target; first source white=%d, fallback source white=%d",
                        firstWhite, fallbackWhite);
            }
            return null;
        }
        arena.note("candidate-switch evidence at t=%d: target complete, first source white=%d, fallback source white=%d (start=%d)",
                elapsedTicks, firstWhite, fallbackWhite, this.startingFallbackBoxWhite);
        if (fallbackWhite < 0 || fallbackWhite >= this.startingFallbackBoxWhite) {
            return Verdict.fail("the target completed without a fallback source decrease: fallback source white=%d (start=%d)",
                    fallbackWhite, this.startingFallbackBoxWhite);
        }
        return Verdict.pass("the unchanged first candidate was skipped after its failed interaction, the fallback source changed, and the build resumed");
    }

    @Override
    public String progressMarker(TestArena arena) {
        BetterBlockPos feet = arena.ctx().playerFeet();
        return String.format("target=%s,firstWhite=%d,fallbackWhite=%d,feet=%d,%d,%d",
                arena.stateAt(BUILD_X, 1, 0).getBlock().getName().getString(),
                ScenarioInventory.countContainer(arena, FIRST_BOX_X, 0, FIRST_BOX_Z, Blocks.WHITE_CONCRETE.asItem()),
                ScenarioInventory.countContainer(arena, FALLBACK_BOX_X, 0, FALLBACK_BOX_Z, Blocks.WHITE_CONCRETE.asItem()),
                feet.x, feet.y, feet.z);
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("firstSourceWhite=%d/%d, fallbackSourceWhite=%d/%d, target=%s, player=%s",
                ScenarioInventory.countContainer(arena, FIRST_BOX_X, 0, FIRST_BOX_Z, Blocks.WHITE_CONCRETE.asItem()),
                this.startingFirstBoxWhite,
                ScenarioInventory.countContainer(arena, FALLBACK_BOX_X, 0, FALLBACK_BOX_Z, Blocks.WHITE_CONCRETE.asItem()),
                this.startingFallbackBoxWhite,
                arena.stateAt(BUILD_X, 1, 0).getBlock().getName().getString(), arena.ctx().playerFeet());
    }
}
