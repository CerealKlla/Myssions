package com.github.cerealklla.myssions.registration;

import com.github.cerealklla.myssions.MyssionsMod;
import com.github.cerealklla.myssions.quest.QuestNpcEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(MyssionsMod.MODID);

    /** Never naturally spawned -- only ever created by {@code quest.QuestNpcSpawner}. Same sizing/tracking as Settlemynts' own plain-Villager plot workers. */
    public static final DeferredHolder<EntityType<?>, EntityType<QuestNpcEntity>> QUEST_NPC = ENTITIES.registerEntityType(
            "quest_npc",
            QuestNpcEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.6f, 1.95f).clientTrackingRange(10));
}
