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

package baritone.utils;

import baritone.Baritone;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A cached list of the best tools on the hotbar for any block
 *
 * @author Avery, Brady, leijurv
 */
public class ToolSet {

    /**
     * A cache mapping a {@link Block} to how long it will take to break
     * with this toolset, given the optimum tool is used.
     */
    private final Map<Block, Double> breakStrengthCache;

    /**
     * My buddy leijurv owned me so we have this to not create a new lambda instance.
     */
    private final Function<Block, Double> backendCalculation;

    private final LocalPlayer player;

    public ToolSet(LocalPlayer player) {
        breakStrengthCache = new HashMap<>();
        this.player = player;

        if (Baritone.settings().considerPotionEffects.value) {
            double amplifier = potionAmplifier();
            Function<Double, Double> amplify = x -> amplifier * x;
            backendCalculation = amplify.compose(this::getBestDestructionTime);
        } else {
            backendCalculation = this::getBestDestructionTime;
        }
    }

    /**
     * Using the best tool on the hotbar, how fast we can mine this block
     *
     * @param state the blockstate to be mined
     * @return the speed of how fast we'll mine it. 1/(time in ticks)
     */
    public double getStrVsBlock(BlockState state) {
        return breakStrengthCache.computeIfAbsent(state.getBlock(), backendCalculation);
    }

    private int getMaterialCost(ItemStack itemStack) {
        if (itemStack.getItem() instanceof TieredItem) {
            TieredItem tool = (TieredItem) itemStack.getItem();
            return tool.getTier().getLevel();
        } else {
            return -1;
        }
    }

    /**
     * Whether {@code itemSaver} considers this stack too damaged to keep using.
     * <p>
     * Note that this is about the item, not about whether anything else is available: a spent tool
     * is still returned by {@link #getBestSlot} when every hotbar slot holds one, because that
     * method's contract is to name a slot. Refusing to actually swing it is
     * {@link BlockBreakHelper}'s job, which is the only place every block break passes through.
     *
     * @param stack the stack to test, possibly empty
     * @return {@code true} if the stack is damageable and within the configured threshold of breaking
     */
    public static boolean isSpent(ItemStack stack) {
        return Baritone.settings().itemSaver.value
                && isSpent(stack.getDamageValue(), stack.getMaxDamage(), Baritone.settings().itemSaverThreshold.value);
    }

    /**
     * The arithmetic behind {@link #isSpent(ItemStack)}, split out so it can be tested without a
     * Minecraft bootstrap -- constructing an {@link ItemStack} needs bound item components.
     * <p>
     * {@code maxDamage > 1} is what distinguishes a damageable item from everything else: stacks
     * that cannot take damage report a max of 0, and a hypothetical one-use item has no headroom
     * for a threshold to protect anyway.
     *
     * @param damageValue how much damage the stack has already taken
     * @param maxDamage   the stack's durability, or 0 if it is not damageable
     * @param threshold   how much durability {@code itemSaver} wants left over
     * @return {@code true} if the stack is damageable and within {@code threshold} of breaking
     */
    public static boolean isSpent(int damageValue, int maxDamage, int threshold) {
        return maxDamage > 1 && damageValue + threshold >= maxDamage;
    }

    public boolean hasSilkTouch(ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, stack) > 0;
    }

    /**
     * Calculate which tool on the hotbar is best for mining, depending on an override setting,
     * related to auto tool movement cost, it will either return current selected slot, or the best slot.
     *
     * @param b the blockstate to be mined
     * @return An int containing the index in the tools array that worked best
     */

    public int getBestSlot(Block b, boolean preferSilkTouch) {
        return getBestSlot(b, preferSilkTouch, false);
    }

    public int getBestSlot(Block b, boolean preferSilkTouch, boolean pathingCalculation) {
        return getBestSlot(b, preferSilkTouch, pathingCalculation, true);
    }

    /**
     * Whether {@code itemSaver} is currently costing us speed on this block.
     * <p>
     * True when setting the rule aside would name a strictly faster slot, which is exactly the
     * situation where honouring it means mining with a worse tool or with a bare hand. That is the
     * condition worth escalating on -- fetching a replacement, or stopping and saying so -- because
     * it covers the quiet case as well as the obvious one: with a spare empty hotbar slot the bot
     * does not sit still when its last pickaxe is spent, it chews through stone barehanded at a
     * hundredth of the speed and looks like it is working.
     *
     * @param b the block we are about to break
     * @return {@code true} if a spent tool is the only good option for this block
     */
    public boolean isBlockedByItemSaver(Block b) {
        return spentToolFor(b) != null;
    }

    /**
     * The nearly-broken tool {@code itemSaver} is keeping us from using on this block.
     * <p>
     * This is the item a replacement should be fetched of, and it is deliberately <i>not</i>
     * "whatever is in hand". When the rule skips a spent pickaxe, selection falls through to
     * whatever else scores best — with {@code useSwordToMine} on, that is typically a sword, which
     * costs two durability per block and is usually worth more than the pickaxe being protected.
     * Asking what is held would then request a spare sword, which is not the problem.
     *
     * @param b the block we are about to break
     * @return the spent tool being withheld, or {@code null} if the rule is costing us nothing here
     */
    public ItemStack spentToolFor(Block b) {
        if (!Baritone.settings().itemSaver.value) {
            return null;
        }
        ItemStack saved = player.getInventory().getItem(getBestSlot(b, false, false, true));
        ItemStack ignoring = player.getInventory().getItem(getBestSlot(b, false, false, false));
        BlockState state = b.defaultBlockState();
        if (calculateSpeedVsBlock(ignoring, state) <= calculateSpeedVsBlock(saved, state)) {
            return null;
        }
        // The faster option was withheld, so it is the spent one by construction -- that is the only
        // reason honouring the rule can pick something slower.
        return isSpent(ignoring) ? ignoring : null;
    }

    private int getBestSlot(Block b, boolean preferSilkTouch, boolean pathingCalculation, boolean honorItemSaver) {

        /*
        If we actually want know what efficiency our held item has instead of the best one
        possible, this lets us make pathing depend on the actual tool to be used (if auto tool is disabled)
        */
        if (!Baritone.settings().autoTool.value && pathingCalculation) {
            return player.getInventory().selected;
        }

        int best = 0;
        double highestSpeed = Double.NEGATIVE_INFINITY;
        int lowestCost = Integer.MIN_VALUE;
        boolean bestSilkTouch = false;
        BlockState blockState = b.defaultBlockState();
        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = player.getInventory().getItem(i);
            if (!Baritone.settings().useSwordToMine.value && itemStack.is(ItemTags.SWORDS)) {
                continue;
            }

            if (honorItemSaver && isSpent(itemStack)) {
                continue;
            }
            double speed = calculateSpeedVsBlock(itemStack, blockState);
            boolean silkTouch = hasSilkTouch(itemStack);
            if (speed > highestSpeed) {
                highestSpeed = speed;
                best = i;
                lowestCost = getMaterialCost(itemStack);
                bestSilkTouch = silkTouch;
            } else if (speed == highestSpeed) {
                int cost = getMaterialCost(itemStack);
                if ((cost < lowestCost && (silkTouch || !bestSilkTouch)) ||
                        (preferSilkTouch && !bestSilkTouch && silkTouch)) {
                    highestSpeed = speed;
                    best = i;
                    lowestCost = cost;
                    bestSilkTouch = silkTouch;
                }
            }
        }
        return best;
    }

    /**
     * Calculate how effectively a block can be destroyed
     *
     * @param b the blockstate to be mined
     * @return A double containing the destruction ticks with the best tool
     */
    private double getBestDestructionTime(Block b) {
        ItemStack stack = player.getInventory().getItem(getBestSlot(b, false, true));
        return calculateSpeedVsBlock(stack, b.defaultBlockState()) * avoidanceMultiplier(b);
    }

    private double avoidanceMultiplier(Block b) {
        return Baritone.settings().blocksToAvoidBreaking.value.contains(b) ? Baritone.settings().avoidBreakingMultiplier.value : 1;
    }

    /**
     * Calculates how long would it take to mine the specified block given the best tool
     * in this toolset is used. A negative value is returned if the specified block is unbreakable.
     *
     * @param item  the item to mine it with
     * @param state the blockstate to be mined
     * @return how long it would take in ticks
     */
    public static double calculateSpeedVsBlock(ItemStack item, BlockState state) {
        float hardness;
        try {
            hardness = state.getDestroySpeed(null, null);
        } catch (NullPointerException npe) {
            // can't easily determine the hardness so treat it as unbreakable
            return -1;
        }
        if (hardness < 0) {
            return -1;
        }

        float speed = item.getDestroySpeed(state);
        if (speed > 1) {
            int effLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, item);
            if (effLevel > 0 && !item.isEmpty()) {
                speed += effLevel * effLevel + 1;
            }
        }

        speed /= hardness;
        if (!state.requiresCorrectToolForDrops() || (!item.isEmpty() && item.isCorrectToolForDrops(state))) {
            return speed / 30;
        } else {
            return speed / 100;
        }
    }

    /**
     * Calculates any modifier to breaking time based on status effects.
     *
     * @return a double to scale block breaking speed.
     */
    private double potionAmplifier() {
        double speed = 1;
        if (player.hasEffect(MobEffects.DIG_SPEED)) {
            speed *= 1 + (player.getEffect(MobEffects.DIG_SPEED).getAmplifier() + 1) * 0.2;
        }
        if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            switch (player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0:
                    speed *= 0.3;
                    break;
                case 1:
                    speed *= 0.09;
                    break;
                case 2:
                    speed *= 0.0027; // you might think that 0.09*0.3 = 0.027 so that should be next, that would make too much sense. it's 0.0027.
                    break;
                default:
                    speed *= 0.00081;
                    break;
            }
        }
        return speed;
    }
}
