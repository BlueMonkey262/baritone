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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.Map;

/** A restock open must release the use key after the one targeted container click. */
public final class RestockOpenContainerOneShotScenario extends TestScenario {

    private static final int BUILD_X = 6;
    private static final int BOX_X = 2;
    private static final int BOX_Z = 4;
    private static final int DOOR_X = 3;
    private static final int DOOR_Z = 4;

    private boolean sawRestock;
    private boolean sawBox;
    private int maximumWhite;

    @Override
    public String name() {
        return "restock-open-container-one-shot";
    }

    @Override
    public String description() {
        return "Open one registered box once while leaving an adjacent interactable block unchanged";
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
        return settings;
    }

    @Override
    public void stage(TestArena arena) {
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:stone");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.command("clear @s");
        arena.setBlock(BUILD_X, 0, 0, "minecraft:stone");
        arena.setBlock(BOX_X, 0, BOX_Z,
                "minecraft:shulker_box[facing=up]{Items:[{id:\"minecraft:white_concrete\",count:8,Slot:0b}]}");
        arena.setBlock(DOOR_X, 0, DOOR_Z,
                "minecraft:oak_door[facing=south,half=lower,hinge=left,open=false,powered=false]");
        arena.setBlock(DOOR_X, 1, DOOR_Z,
                "minecraft:oak_door[facing=south,half=upper,hinge=left,open=false,powered=false]");
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        return arena.stateAt(BUILD_X, 0, 0).is(Blocks.STONE)
                && arena.stateAt(BUILD_X, 1, 0).isAir()
                && arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && arena.stateAt(DOOR_X, 0, DOOR_Z).getBlock() == Blocks.OAK_DOOR
                && !arena.stateAt(DOOR_X, 0, DOOR_Z).getValue(BlockStateProperties.OPEN)
                && ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem()) == 0;
    }

    @Override
    public void start(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the source box could not be registered");
            return;
        }
        world.getRestockBoxes().addBox(box);
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
        this.sawRestock |= AbstractBoxBuildScenario.inControl(arena).toLowerCase().contains("restock");
        this.sawBox |= arena.ctx().playerFeet().distSqr(arena.at(BOX_X, 0, BOX_Z)) <= 9.0;
        this.maximumWhite = Math.max(this.maximumWhite,
                ScenarioInventory.countPlayer(arena, Blocks.WHITE_CONCRETE.asItem()));

        if (arena.stateAt(DOOR_X, 0, DOOR_Z).getValue(BlockStateProperties.OPEN)) {
            return Verdict.fail("the neighboring oak door opened while the restock interaction was in progress");
        }
        if (!arena.stateAt(BUILD_X, 1, 0).is(Blocks.WHITE_CONCRETE)) {
            if (elapsedTicks >= 20 * 12 && !arena.baritone().getBuilderProcess().isActive()) {
                return Verdict.fail("the build stopped without proving a completed transfer; box reached=%b, restock control=%b",
                        this.sawBox, this.sawRestock);
            }
            return null;
        }
        if (!this.sawRestock || !this.sawBox || this.maximumWhite <= 0) {
            return Verdict.fail("the target completed without independent evidence of a box visit and player inventory gain");
        }
        return Verdict.pass("the source was reached, material entered the player inventory, and the adjacent door stayed closed");
    }

    @Override
    public String timeoutDiagnosis(TestArena arena) {
        return String.format("target=%s,boxReached=%b,restockControl=%b,maxWhite=%d,doorOpen=%b,player=%s",
                arena.stateAt(BUILD_X, 1, 0).getBlock().getName().getString(), this.sawBox, this.sawRestock,
                this.maximumWhite, arena.stateAt(DOOR_X, 0, DOOR_Z).getValue(BlockStateProperties.OPEN), arena.ctx().playerFeet());
    }
}
