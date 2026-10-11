package com.github.cerealklla.myssions.registration;

import com.github.cerealklla.myssions.MyssionsMod;
import com.github.cerealklla.myssions.quest.QuestProofItem;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MyssionsMod.MODID);

    /** The one reusable "bring back as proof" quest item -- see {@link QuestProofItem}'s own doc. Stack 128, explicit spec. */
    public static final DeferredItem<QuestProofItem> QUEST_PROOF = ITEMS.register(
            "quest_proof",
            id -> new QuestProofItem(new Item.Properties()
                    .stacksTo(128)
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MyssionsMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> QUEST_PROOF_ID =
            DATA_COMPONENTS.registerComponentType("quest_proof_id", builder -> builder
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));
}
