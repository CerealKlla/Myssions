package com.github.cerealklla.myssions.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.github.cerealklla.myssions.registration.ModMenus;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Quest-reward collection GUI (design doc item 1's own "finishes it up" step, explicit user spec
 * 2026-10-11: "take you to a GUI of the players inventory and the reward inventory... a take all
 * button... [that] will transfer all the items as usual and complete the quest [if there's room];
 * if the player does not have space they can cancel out... and come back to collect"). A single-row
 * {@link ChestMenu} -- the same shape as Yconomics' own {@code bag.LootBagMenu}, copied rather than
 * reused since that class bakes in a real world-entity despawn timer and drops overflow on the
 * ground on "Take All," neither of which fits a quest-reward pickup -- plus two buttons (see {@code
 * client.QuestRewardScreen}):
 * <ul>
 *   <li>{@link #TAKE_ALL_BUTTON_ID} ("Take All") -- ordinary item transfer only, no quest-state
 *   side effect. Unlike the Loot Bag's own version, an item that doesn't fit stays in the bag's own
 *   slot instead of dropping on the ground -- the whole point is "go make room, come back," which a
 *   ground-drop would defeat.</li>
 *   <li>{@link #COMPLETE_BUTTON_ID} ("Complete Quest") -- the real completion trigger. Only
 *   finalizes ({@code QuestEngine#finalizeRewardPickup}) once every slot is actually empty; otherwise
 *   just a message, menu stays open.</li>
 * </ul>
 * {@link #removed(Player)} persists whatever's still sitting in the container back onto the quest
 * instance ({@code QuestEngine#syncPendingRewardItems}) if the player closes without finishing, so
 * nothing is lost or duplicated by leaving and coming back later.
 */
public class QuestRewardMenu extends ChestMenu {

    public static final int SLOT_COUNT = 9;
    public static final int TAKE_ALL_BUTTON_ID = 0;
    public static final int COMPLETE_BUTTON_ID = 1;

    private final UUID instanceId;

    public QuestRewardMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, Container container, UUID instanceId) {
        super(menuType, containerId, playerInventory, container, 1);
        this.instanceId = instanceId;
    }

    /** Opens this menu for {@code player}, pre-filled with {@code items} -- the one real entry point, called from {@code QuestEngine#tryCompleteQuest}/{@code #openRewardMenu}, never constructed directly elsewhere. */
    public static void open(ServerPlayer player, UUID instanceId, List<ItemStack> items, String title) {
        SimpleContainer container = new SimpleContainer(SLOT_COUNT);
        for (int i = 0; i < items.size() && i < SLOT_COUNT; i++) {
            container.setItem(i, items.get(i).copy());
        }
        MenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, p) -> new QuestRewardMenu(ModMenus.QUEST_REWARD.get(), containerId, inventory, container, instanceId),
                Component.literal(title));
        player.openMenu(provider);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == TAKE_ALL_BUTTON_ID) {
            takeAll(player);
            return true;
        }
        if (id == COMPLETE_BUTTON_ID) {
            completeQuest(player);
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    private void takeAll(Player player) {
        // Slots 0..SLOT_COUNT-1 are this menu's own container; the rest belong to the player's own
        // inventory/hotbar (ChestMenu's own slot layout convention) and must not be touched here.
        for (int i = 0; i < SLOT_COUNT; i++) {
            Slot slot = getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            // Deliberately does NOT drop overflow on the ground (unlike Yconomics' Loot Bag) -- an
            // item that doesn't fit just stays in its slot, so "go make room and come back" actually
            // has something to come back to.
            if (player.getInventory().add(stack)) {
                slot.set(ItemStack.EMPTY);
            }
        }
        broadcastChanges();
    }

    private void completeQuest(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || instanceId == null) {
            return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!getSlot(i).getItem().isEmpty()) {
                serverPlayer.sendSystemMessage(Component.literal("Take everything out first."));
                return;
            }
        }
        if (QuestEngine.finalizeRewardPickup(serverPlayer, instanceId)) {
            serverPlayer.closeContainer();
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer && instanceId != null) {
            List<ItemStack> remaining = new ArrayList<>();
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack stack = getSlot(i).getItem();
                if (!stack.isEmpty()) {
                    remaining.add(stack.copy());
                }
            }
            QuestEngine.syncPendingRewardItems(serverPlayer, instanceId, remaining);
        }
    }
}
