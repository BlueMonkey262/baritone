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
 * along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.testing.scenario;

import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.testing.TestArena;
import baritone.testing.TestScenario;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashMap;
import java.util.Map;

/** Common staging and world-state readers for the itemSaver mining scenarios. */
abstract class AbstractItemSaverScenario extends TestScenario {

    /** The command component spelling is checked against 26.1.2's DataComponents registry. */
    protected static final String SPENT_PICKAXE =
            "minecraft:wooden_pickaxe[damage=50,enchantments={\"minecraft:efficiency\":5}]";
    protected static final String FRESH_PICKAXE =
            "minecraft:wooden_pickaxe[damage=0,enchantments={\"minecraft:efficiency\":5}]";

    protected static final int PICKAXE_DAMAGE = 50;
    protected static final int PICKAXE_MAX_DAMAGE = 59;
    protected static final int EFFICIENCY_LEVEL = 5;

    protected static final int WALL_X = 4;
    protected static final int WALL_MIN_Z = -2;
    protected static final int WALL_MAX_Z = 2;
    protected static final int WALL_MIN_Y = 0;
    protected static final int WALL_MAX_Y = 2;
    protected static final int WALL_BLOCKS = 15;

    protected static final int BOX_X = 2;
    protected static final int BOX_Z = 4;

    private boolean stagedNoteWritten;
    private boolean observationNoteWritten;

    /** Whether this fixture must have a registered replacement box. */
    protected boolean hasReplacementBox() {
        return false;
    }

    /** How many fresh pickaxes the replacement fixture puts in its box. */
    protected int stagedBoxPickaxes() {
        return 0;
    }

    /** Whether this fixture deliberately puts a sword in the hotbar. */
    protected boolean hasSword() {
        return false;
    }

    /** Equipment commands run after the arena has been cleared. */
    protected abstract void stageEquipment(TestArena arena);

    @Override
    public void stage(TestArena arena) {
        // Keep the mining target distinct from the floor: mining minecraft:stone must not select
        // the blocks the player is standing on as an accidental second fixture.
        arena.fill(-4, -3, -8, 16, -1, 8, "minecraft:obsidian");
        arena.fill(-4, 0, -8, 16, 4, 8, "minecraft:air");
        arena.fill(WALL_X, WALL_MIN_Y, WALL_MIN_Z, WALL_X, WALL_MAX_Y, WALL_MAX_Z,
                "minecraft:stone");
        arena.command("clear @s");
        stageEquipment(arena);
        arena.teleport(0, 0, 0);
    }

    @Override
    public boolean stagingComplete(TestArena arena) {
        ItemStack pickaxe = ScenarioInventory.firstHotbar(
                arena, Items.WOODEN_PICKAXE, AbstractItemSaverScenario::isSpentPickaxe
        );
        boolean pickaxeStaged = ScenarioInventory.countPlayer(arena, Items.WOODEN_PICKAXE) == 1
                && isSpentPickaxe(pickaxe);
        boolean wallStaged = arena.stateAt(0, -1, 0).is(Blocks.OBSIDIAN)
                && countWallBlocks(arena) == WALL_BLOCKS;
        boolean boxStaged = !hasReplacementBox()
                || (arena.stateAt(BOX_X, 0, BOX_Z).getBlock() instanceof ShulkerBoxBlock
                && ScenarioInventory.countContainerMatching(
                arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE,
                AbstractItemSaverScenario::isFreshPickaxe
        ) == stagedBoxPickaxes());
        boolean swordStaged = !hasSword()
                || !ScenarioInventory.firstHotbar(
                arena, Items.DIAMOND_SWORD, stack -> stack.getDamageValue() == 0
        ).isEmpty();
        boolean complete = pickaxeStaged && wallStaged && boxStaged && swordStaged;
        if (complete && !stagedNoteWritten) {
            stagedNoteWritten = true;
            arena.note(
                    "staged stone wall=%d blocks; spent wooden pickaxe damage=%d/%d, Efficiency=%d; "
                            + "replacement box pickaxes=%d; sword damage=%d",
                    countWallBlocks(arena), pickaxe.getDamageValue(), pickaxe.getMaxDamage(),
                    efficiencyLevel(pickaxe),
                    hasReplacementBox() ? ScenarioInventory.countContainer(
                            arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE
                    ) : 0,
                    swordDamage(arena)
            );
        }
        return complete;
    }

    protected void stageSpentPickaxe(TestArena arena) {
        arena.command("give @s " + SPENT_PICKAXE + " 1");
    }

    protected void stageSword(TestArena arena) {
        arena.command("give @s minecraft:diamond_sword 1");
    }

    protected void stageReplacementBox(TestArena arena) {
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        arena.setBlock(BOX_X, 0, BOX_Z, "minecraft:shulker_box[facing=up]");
        for (int slot = 0; slot < stagedBoxPickaxes(); slot++) {
            arena.command(String.format(
                    "item replace block %d %d %d container.%d with %s",
                    box.x, box.y, box.z, slot, FRESH_PICKAXE
            ));
        }
    }

    protected void registerReplacementBox(TestArena arena) {
        IWorldData world = arena.baritone().getWorldProvider().getCurrentWorld();
        BetterBlockPos box = arena.at(BOX_X, 0, BOX_Z);
        if (world == null) {
            arena.note("no world data, so the replacement box at %s could not be registered", box);
            return;
        }
        world.getRestockBoxes().addBox(box);
        arena.note("registered unindexed replacement box at %s with %d fresh Efficiency V pickaxes",
                box, stagedBoxPickaxes());
    }

    protected void startMining(TestArena arena) {
        ItemStack pickaxe = ScenarioInventory.firstPlayer(
                arena, Items.WOODEN_PICKAXE, AbstractItemSaverScenario::isSpentPickaxe
        );
        arena.note("starting stone mining with pickaxe damage=%d/%d; wall has %d blocks",
                pickaxe.isEmpty() ? -1 : pickaxe.getDamageValue(),
                pickaxe.isEmpty() ? -1 : pickaxe.getMaxDamage(),
                countWallBlocks(arena));
        arena.baritone().getMineProcess().mineByName("minecraft:stone");
    }

    protected int countWallBlocks(TestArena arena) {
        int count = 0;
        for (int y = WALL_MIN_Y; y <= WALL_MAX_Y; y++) {
            for (int z = WALL_MIN_Z; z <= WALL_MAX_Z; z++) {
                if (arena.stateAt(WALL_X, y, z).is(Blocks.STONE)) {
                    count++;
                }
            }
        }
        return count;
    }

    protected int pickaxeDamage(TestArena arena) {
        ItemStack pickaxe = ScenarioInventory.firstPlayer(
                arena, Items.WOODEN_PICKAXE, stack -> true
        );
        return pickaxe.isEmpty() ? -1 : pickaxe.getDamageValue();
    }

    protected int freshPickaxesOwned(TestArena arena) {
        return ScenarioInventory.countPlayerMatching(
                arena, Items.WOODEN_PICKAXE, AbstractItemSaverScenario::isFreshPickaxe
        );
    }

    protected int boxPickaxes(TestArena arena) {
        return ScenarioInventory.countContainer(arena, BOX_X, 0, BOX_Z, Items.WOODEN_PICKAXE);
    }

    protected int swordDamage(TestArena arena) {
        ItemStack sword = ScenarioInventory.firstPlayer(
                arena, Items.DIAMOND_SWORD, stack -> true
        );
        return sword.isEmpty() ? -1 : sword.getDamageValue();
    }

    protected boolean mineActive(TestArena arena) {
        return arena.baritone().getMineProcess().isActive();
    }

    protected void noteObservation(TestArena arena, String format, Object... args) {
        if (!observationNoteWritten) {
            observationNoteWritten = true;
            arena.note(format, args);
        }
    }

    protected static boolean isSpentPickaxe(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getDamageValue() == PICKAXE_DAMAGE
                && stack.getMaxDamage() == PICKAXE_MAX_DAMAGE
                && efficiencyLevel(stack) == EFFICIENCY_LEVEL;
    }

    protected static boolean isFreshPickaxe(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getDamageValue() == 0
                && stack.getMaxDamage() == PICKAXE_MAX_DAMAGE
                && efficiencyLevel(stack) == EFFICIENCY_LEVEL;
    }

    protected static int efficiencyLevel(ItemStack stack) {
        for (var enchantment : stack.getEnchantments().keySet()) {
            if (enchantment.is(Enchantments.EFFICIENCY)) {
                return stack.getEnchantments().getLevel(enchantment);
            }
        }
        return 0;
    }

    protected static Map<String, Object> miningSettings(boolean itemSaver) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("allowBreak", true);
        settings.put("itemSaver", itemSaver);
        return settings;
    }
}
