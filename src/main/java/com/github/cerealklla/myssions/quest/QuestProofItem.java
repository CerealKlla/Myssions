package com.github.cerealklla.myssions.quest;

import java.util.List;
import java.util.function.Consumer;

import com.github.cerealklla.myssions.registration.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The reusable "bring back proof" item for the classic fetch-quest type (design doc item 7) --
 * one real item, metadata-driven per the user's own suggestion ("we may even want to reuse the
 * item and simply change the metadata dynamically to match the quest") rather than one item per
 * quest. Max stack 128 (explicit spec). Model parents directly on vanilla's own {@code
 * item/rabbit_foot} texture (a real vanilla icon, reused as-is per "come up with a real, tangible,
 * custom item with a vanilla icon... you can colorize it or modify the icon however you like" --
 * recoloring deferred, flagged as a nice-to-have, not required for this to be real and functional).
 *
 * <p>Every stack carries a {@link ModItems#QUEST_PROOF_ID} string component naming which quest's
 * proof it is (e.g. {@code "lumberjack_wolf_pelts"}) -- two different proof kinds never stack
 * together, since vanilla's own stacking rule already requires identical components. Display name
 * and flavor-text lore are baked onto the stack at creation time via plain vanilla {@code
 * DataComponents.CUSTOM_NAME}/{@code LORE}, same technique Lyfe's Research/Recipe Note items
 * already use for the same "one item type, many distinct minted instances" shape.
 */
public class QuestProofItem extends Item {

    public QuestProofItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createFor(String proofId, String displayName, String flavorText) {
        ItemStack stack = new ItemStack(ModItems.QUEST_PROOF.get());
        stack.set(ModItems.QUEST_PROOF_ID, proofId);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                List.of(Component.literal(flavorText).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY))));
        return stack;
    }

    public static String proofIdOf(ItemStack stack) {
        return stack.getOrDefault(ModItems.QUEST_PROOF_ID, "");
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        builder.accept(Component.literal("Quest item -- bring this back as proof.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
