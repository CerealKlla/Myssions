package com.github.cerealklla.myssions.registration;

import com.github.cerealklla.myssions.MyssionsMod;
import com.github.cerealklla.myssions.quest.QuestNpcEntity;
import com.github.cerealklla.myssions.quest.entity.CaravanEntity;

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

    /** Never naturally spawned -- only ever created by {@code quest.NpcQuests#registerEscortHook}. Wider tracking range than QUEST_NPC since it moves and the player needs to see it from further away while escorting. */
    public static final DeferredHolder<EntityType<?>, EntityType<CaravanEntity>> CARAVAN = ENTITIES.registerEntityType(
            "caravan",
            CaravanEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.6f, 1.95f).clientTrackingRange(16));
}
