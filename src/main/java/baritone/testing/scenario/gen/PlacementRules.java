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

package baritone.testing.scenario.gen;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/** Vanilla-derived placement rules used by {@link PlacementCatalog}. */
final class PlacementRules {

    private PlacementRules() {}

    /**
     * StairBlock#getStateForPlacement takes horizontal facing from the player's horizontal look.
     * The ray therefore has to travel through the target into a support on the stair's facing
     * side: support = target + facing. A side-face click below/above its midpoint selects bottom /
     * top half. Stair shape is recomputed from neighbours and waterlogging comes from the fluid at
     * the target, so only shape=straight and waterlogged=false are catalogued.
     *
     * <p>Excluded: the four corner {@code shape} values because they are neighbour-derived, and
     * {@code waterlogged=true} because it requires a water-filled target at placement time.</p>
     */
    static final class StairsRule implements PlacementRule {

        @Override
        public String family() {
            return "stairs";
        }

        @Override
        public boolean accepts(Block block) {
            return block instanceof StairBlock && vanillaPath(block).endsWith("_stairs");
        }

        @Override
        public List<PlacementCase> enumerate(Block block) {
            List<PlacementCase> cases = new ArrayList<>();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (state.getValue(StairBlock.SHAPE) != StairsShape.STRAIGHT
                        || state.getValue(StairBlock.WATERLOGGED)
                        || state.getValue(StairBlock.FACING).getAxis() != Direction.Axis.X
                        && state.getValue(StairBlock.FACING).getAxis() != Direction.Axis.Z) {
                    continue;
                }
                Direction facing = state.getValue(StairBlock.FACING);
                Half half = state.getValue(StairBlock.HALF);
                cases.add(caseFor(block, state,
                        "facing_" + facing.getName() + "-half_" + half.getSerializedName(),
                        List.of(sideSupport(facing))));
            }
            return cases;
        }
    }

    /**
     * RotatedPillarBlock#getStateForPlacement sets axis to the axis of the clicked face. A support
     * one block on the negative side of each horizontal axis gives the corresponding positive
     * face to click; a support directly below the target supplies the clicked UP face for axis=y.
     * The support is declared in the schematic in every case, including the below-target support.
     */
    static final class PillarsRule implements PlacementRule {

        @Override
        public String family() {
            return "logs";
        }

        @Override
        public boolean accepts(Block block) {
            String path = vanillaPath(block);
            return block instanceof RotatedPillarBlock
                    && (path.endsWith("_log") || path.endsWith("_wood")
                    || path.endsWith("_stem") || path.equals("bamboo_block"));
        }

        @Override
        public List<PlacementCase> enumerate(Block block) {
            List<PlacementCase> cases = new ArrayList<>();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
                cases.add(caseFor(block, state, "axis_" + axis.getName(), List.of(pillarSupport(axis))));
            }
            return cases;
        }
    }

    /**
     * ObserverBlock#getStateForPlacement calls getNearestLookingDirection().getOpposite().
     * getOpposite(), so the two inversions cancel: desired facing F requires looking F. The ray
     * therefore travels through the target into a support at target + F. DOWN uses the floor below
     * the target; UP uses an above-target support and is raised to the builder's reachable vertical
     * stand region. Excluded: {@code POWERED=true}, because redstone/world updates drive it rather
     * than the placement click; the generated state fixes it false.
     */
    static final class ObserversRule implements PlacementRule {

        @Override
        public String family() {
            return "observers";
        }

        @Override
        public boolean accepts(Block block) {
            return block == Blocks.OBSERVER;
        }

        @Override
        public List<PlacementCase> enumerate(Block block) {
            List<PlacementCase> cases = new ArrayList<>();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (state.getValue(ObserverBlock.POWERED)) {
                    continue;
                }
                Direction facing = state.getValue(BlockStateProperties.FACING);
                cases.add(caseFor(block, state, "facing_" + facing.getName(),
                        targetYForLook(facing), List.of(sideSupport(facing))));
            }
            return cases;
        }
    }

    /**
     * HopperBlock#getStateForPlacement uses the opposite of the clicked face, except that either
     * vertical click produces facing=down. Horizontal facing F therefore clicks a support at
     * target + F, while the DOWN case clicks the bottom face of the support directly above. The
     * above-support variant gives a player standing below the target a real upward ray. Excluded:
     * {@code ENABLED=false}, because redstone drives it rather than placement. Hopper has no legal
     * UP value in its state definition.
     */
    static final class HoppersRule implements PlacementRule {

        @Override
        public String family() {
            return "hoppers";
        }

        @Override
        public boolean accepts(Block block) {
            return block == Blocks.HOPPER;
        }

        @Override
        public List<PlacementCase> enumerate(Block block) {
            List<PlacementCase> cases = new ArrayList<>();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (!state.getValue(HopperBlock.ENABLED)) {
                    continue;
                }
                Direction facing = state.getValue(HopperBlock.FACING);
                PlacementCase.Support support = facing == Direction.DOWN
                        ? aboveSupport()
                        : new PlacementCase.Support(offset(facing), Blocks.STONE.defaultBlockState());
                cases.add(caseFor(block, state, "facing_" + facing.getName(), List.of(support)));
            }
            return cases;
        }
    }

    /**
     * PistonBaseBlock#getStateForPlacement sets facing to the inverse nearest-looking direction.
     * The ray consequently travels through the target into a support at target + facing.opposite.
     * For facing=down, the required look is up, so the target is raised and the support is directly
     * above it. The target's declared stone column lets a player stand on the arena floor while
     * looking upward at that support's bottom face.
     * Excluded: {@code EXTENDED=true}, because redstone/world events drive it; only false cases are
     * generated for both piston variants.
     */
    static final class PistonsRule implements PlacementRule {

        @Override
        public String family() {
            return "pistons";
        }

        @Override
        public boolean accepts(Block block) {
            return block == Blocks.PISTON || block == Blocks.STICKY_PISTON;
        }

        @Override
        public List<PlacementCase> enumerate(Block block) {
            List<PlacementCase> cases = new ArrayList<>();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (state.getValue(BlockStateProperties.EXTENDED)) {
                    continue;
                }
                Direction facing = state.getValue(BlockStateProperties.FACING);
                Direction look = facing.getOpposite();
                cases.add(caseFor(block, state, "facing_" + facing.getName(),
                        targetYForLook(look), List.of(sideSupport(look))));
            }
            return cases;
        }
    }

    /**
     * DispenserBlock#getStateForPlacement uses the inverse nearest-looking direction; DropperBlock
     * inherits that method. Each target is therefore approached through a support at target +
     * facing.opposite. For facing=down, the target is raised and the above-target support's bottom
     * face is clicked while looking upward; its declared stone column reaches the arena floor.
     * Excluded: {@code TRIGGERED=true}, because a redstone pulse drives it; both blocks otherwise
     * enumerate all six facings.
     */
    static final class DispensersRule implements PlacementRule {

        @Override
        public String family() {
            return "dispensers";
        }

        @Override
        public boolean accepts(Block block) {
            return (block == Blocks.DISPENSER || block == Blocks.DROPPER)
                    && (block instanceof DispenserBlock || block instanceof DropperBlock);
        }

        @Override
        public List<PlacementCase> enumerate(Block block) {
            List<PlacementCase> cases = new ArrayList<>();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (state.getValue(DispenserBlock.TRIGGERED)) {
                    continue;
                }
                Direction facing = state.getValue(DispenserBlock.FACING);
                Direction look = facing.getOpposite();
                cases.add(caseFor(block, state, "facing_" + facing.getName(),
                        targetYForLook(look), List.of(sideSupport(look))));
            }
            return cases;
        }
    }

    private static PlacementCase.Support sideSupport(Direction direction) {
        return new PlacementCase.Support(offset(direction), Blocks.STONE.defaultBlockState());
    }

    private static PlacementCase.Support aboveSupport() {
        return new PlacementCase.Support(new Vec3i(0, 1, 0), Blocks.STONE.defaultBlockState());
    }

    /**
     * GoalPlaceOriented requires an UP-facing stand position at targetY-3 or lower. Raising these
     * targets to y=3 leaves the player at y=0 on the arena floor and puts an above-target support's
     * lower face above the player's eye, so the upward ray is physically possible.
     */
    private static int targetYForLook(Direction look) {
        return look == Direction.UP ? 3 : 0;
    }

    private static Vec3i offset(Direction direction) {
        return new Vec3i(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static PlacementCase.Support pillarSupport(Direction.Axis axis) {
        return new PlacementCase.Support(axis == Direction.Axis.X
                ? new Vec3i(-1, 0, 0)
                : axis == Direction.Axis.Z
                ? new Vec3i(0, 0, -1)
                : new Vec3i(0, -1, 0), Blocks.STONE.defaultBlockState());
    }

    private static PlacementCase caseFor(Block block, BlockState state, String suffix,
                                         List<PlacementCase.Support> supports) {
        return caseFor(block, state, suffix, 0, supports);
    }

    private static PlacementCase caseFor(Block block, BlockState state, String suffix, int targetY,
                                         List<PlacementCase.Support> supports) {
        ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(block.asItem());
        if (blockId == null || itemId == null) {
            throw new IllegalStateException("placement catalog block has no registry item: " + block);
        }
        String blockName = blockId.getPath();
        String name = "place-" + blockName + "-" + suffix;
        return new PlacementCase(familyFor(block), name,
                "Place " + state + " from a survival right-click", itemId.toString(), targetY, state,
                schematicSupports(targetY, supports));
    }

    /**
     * Every target gets a declared stone column down to the arena floor. A horizontal click-support
     * gets the same column beneath it; otherwise the builder would correctly mine that cell as
     * schematic air and leave the support floating. Above-target click supports remain deliberate
     * placement faces rather than parts of those columns.
     */
    private static List<PlacementCase.Support> schematicSupports(int targetY,
                                                                 List<PlacementCase.Support> supports) {
        List<PlacementCase.Support> complete = new ArrayList<>();
        addGroundColumn(complete, 0, 0, targetY);
        for (PlacementCase.Support support : supports) {
            addSupport(complete, support);
            Vec3i offset = support.offset();
            if (offset.getY() == 0) {
                addGroundColumn(complete, offset.getX(), offset.getZ(), targetY);
            }
        }
        return complete;
    }

    private static void addGroundColumn(List<PlacementCase.Support> supports, int x, int z, int targetY) {
        for (int y = -1; targetY + y >= -1; y--) {
            addSupport(supports, new PlacementCase.Support(
                    new Vec3i(x, y, z), Blocks.STONE.defaultBlockState()));
        }
    }

    private static void addSupport(List<PlacementCase.Support> supports, PlacementCase.Support candidate) {
        for (PlacementCase.Support existing : supports) {
            if (existing.offset().equals(candidate.offset())) {
                if (!existing.state().equals(candidate.state())) {
                    throw new IllegalStateException("conflicting generated supports at " + candidate.offset());
                }
                return;
            }
        }
        supports.add(candidate);
    }

    private static String familyFor(Block block) {
        if (block instanceof StairBlock) {
            return "stairs";
        }
        if (block instanceof RotatedPillarBlock) {
            return "logs";
        }
        if (block == Blocks.OBSERVER) {
            return "observers";
        }
        if (block == Blocks.HOPPER) {
            return "hoppers";
        }
        if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON) {
            return "pistons";
        }
        return "dispensers";
    }

    private static String vanillaPath(Block block) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return id != null && "minecraft".equals(id.getNamespace()) ? id.getPath() : "";
    }
}
