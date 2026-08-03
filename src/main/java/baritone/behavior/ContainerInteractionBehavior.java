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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.PacketEvent;
import baritone.api.event.events.type.EventState;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the state of a container the player has open, so that other code can tell the difference
 * between "the server let us open the box" and "we can actually read what is inside it".
 * <p>
 * Those are two distinct packets and conflating them is the main correctness trap here. The
 * container menu is swapped as soon as {@code ClientboundOpenScreenPacket} arrives, but every slot
 * is still empty at that instant. The contents only become real once
 * {@link ClientboundContainerSetContentPacket} lands. Reading too early makes a full box look
 * empty, which would then wrongly flag it as not having the material we came for.
 * <p>
 * This behavior exists purely to observe; it never opens or closes anything itself. Opening is
 * driven by a genuine right-click through {@code InputOverrideHandler}, exactly as
 * {@code GetToBlockProcess} does it, so the server's own reach check applies normally.
 */
public final class ContainerInteractionBehavior extends Behavior {

    /**
     * The container id we have most recently seen a full content sync for, or -1.
     */
    private int syncedContainerId = -1;

    public ContainerInteractionBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onReceivePacket(PacketEvent event) {
        if (event.getState() != EventState.POST) {
            return;
        }
        if (event.getPacket() instanceof ClientboundContainerSetContentPacket) {
            ClientboundContainerSetContentPacket packet = event.cast();
            this.syncedContainerId = packet.getContainerId();
        }
    }

    /**
     * @return The container the player currently has open, or {@code null} if that is just their
     * own inventory
     */
    public AbstractContainerMenu openContainer() {
        if (ctx.player() == null) {
            return null;
        }
        AbstractContainerMenu menu = ctx.player().containerMenu;
        if (menu == null || menu instanceof InventoryMenu || menu == ctx.player().inventoryMenu) {
            return null;
        }
        return menu;
    }

    /**
     * @return {@code true} if a foreign container is open <i>and</i> the server has sent us its
     * contents, meaning the slots can be trusted
     */
    public boolean isContainerReadable() {
        AbstractContainerMenu menu = openContainer();
        return menu != null && menu.containerId == this.syncedContainerId;
    }

    /**
     * Resets sync tracking. Called when starting a new container interaction so that a stale sync
     * from a previously opened container can't be mistaken for this one.
     */
    public void resetSync() {
        this.syncedContainerId = -1;
    }

    /**
     * The number of slots at the start of the menu that belong to the container itself rather than
     * to the player. A shulker box menu is 27 container slots followed by the player's 36.
     * <p>
     * This must be computed from the live menu rather than assumed, because slot indices are
     * per-menu; the {@code +36} convention used for the player's own inventory menu elsewhere in
     * Baritone does not apply here.
     *
     * @param menu The open container menu
     * @return The number of container-owned slots
     */
    public int containerSlotCount(AbstractContainerMenu menu) {
        int count = 0;
        for (Slot slot : menu.slots) {
            // the player's own inventory is appended after the container's slots
            if (slot.container == ctx.player().getInventory()) {
                break;
            }
            count++;
        }
        return count;
    }

    /**
     * Reads the container-owned slots of the open menu.
     *
     * @param menu The open container menu
     * @return Total count per item across the container's own slots
     */
    public Map<Item, Integer> readContents(AbstractContainerMenu menu) {
        Map<Item, Integer> contents = new HashMap<>();
        int containerSlots = containerSlotCount(menu);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return contents;
    }

    /**
     * Shift-clicks a single container slot, moving that whole stack into the player's inventory.
     * <p>
     * This goes through {@code MultiPlayerGameMode#handleClickType}, which is what a real
     * shift-click does, so a genuine {@code ServerboundContainerClickPacket} is sent and the
     * server validates it normally. The menu's {@code stateId} is filled in by that method, so
     * there is nothing extra to track here.
     * <p>
     * Note that the move is applied to the local menu <i>before</i> the packet goes out, and that a
     * click the server agrees with is never acknowledged: it records the slot values we predicted
     * and {@code broadcastChanges} then reports only the ones that differ. Callers must therefore
     * read the outcome from the local menu and treat silence as success. Waiting for a server
     * packet before sending the next click deadlocks.
     *
     * @param menu      The open container menu
     * @param slotIndex The container slot to move
     */
    public void quickMove(AbstractContainerMenu menu, int slotIndex) {
        ctx.playerController().windowClick(menu.containerId, slotIndex, 0, ClickType.QUICK_MOVE, ctx.player());
    }

    /**
     * Closes the open container, sending a real {@code ServerboundContainerClosePacket}. Safe to
     * call when nothing is open.
     * <p>
     * This has to go through {@code LocalPlayer#closeContainer()}, which sends the close packet and
     * then resets {@code containerMenu} to the inventory menu and dismisses the screen for us.
     * Dismissing the screen with {@code Minecraft#setScreen(null)} is <i>not</i> equivalent: that
     * only fires {@code Screen#removed()}, and {@code AbstractContainerScreen#removed()} merely
     * calls {@code AbstractContainerMenu#removed(Player)}. It neither sends the close packet nor
     * clears {@code containerMenu}, so the container would stay open on both sides while the GUI
     * disappeared, and the next box we walked up to would look like it was already open.
     */
    public void closeContainer() {
        if (ctx.player() == null) {
            resetSync();
            return;
        }
        if (openContainer() != null) {
            ctx.player().closeContainer();
        } else if (ctx.minecraft().screen instanceof AbstractContainerScreen) {
            // the menu is already gone but its screen lingered
            ctx.minecraft().setScreen(null);
        }
        resetSync();
    }
}
