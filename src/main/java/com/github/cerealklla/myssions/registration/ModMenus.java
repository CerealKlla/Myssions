package com.github.cerealklla.myssions.registration;

import com.github.cerealklla.myssions.MyssionsMod;
import com.github.cerealklla.myssions.quest.QuestRewardMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menu type for the quest-reward pickup GUI (design doc item 1's own "finishes it up" step, 2026-10-11). */
public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MyssionsMod.MODID);

    // No extra client data needed -- both sides build an identical empty QuestRewardMenu container,
    // contents sync through the menu's own slot packets as usual (same shape as Yconomics' own
    // bag.LootBagMenu, which this mirrors). menuType is @Nullable on AbstractContainerMenu's own
    // constructor; the client-side reconstruction here doesn't need it (only server-side menu-button
    // dispatch cares), so this passes null rather than self-referencing QUEST_REWARD from within its
    // own initializer (illegal).
    public static final DeferredHolder<MenuType<?>, MenuType<QuestRewardMenu>> QUEST_REWARD = MENU_TYPES.register(
            "quest_reward",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) ->
                    new QuestRewardMenu(null, windowId, inventory, new SimpleContainer(QuestRewardMenu.SLOT_COUNT), null)));
}
