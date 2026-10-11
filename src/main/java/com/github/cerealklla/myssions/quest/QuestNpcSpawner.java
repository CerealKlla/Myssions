package com.github.cerealklla.myssions.quest;

import java.util.UUID;

import com.github.cerealklla.myssions.registration.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class QuestNpcSpawner {

    private QuestNpcSpawner() {
    }

    /** Spawns a configured {@link QuestNpcEntity} at {@code pos}, owned (visible only to) {@code ownerPlayerId}, linked to a specific quest instance (not just a definition -- see {@link QuestNpcEntity#linkedInstanceId()}'s own doc). */
    public static QuestNpcEntity spawn(ServerLevel level, BlockPos pos, UUID ownerPlayerId, UUID linkedInstanceId, boolean hidesAtNight, String npcName) {
        QuestNpcEntity npc = new QuestNpcEntity(ModEntities.QUEST_NPC.get(), level);
        npc.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        npc.configure(ownerPlayerId, linkedInstanceId, hidesAtNight, npcName);
        level.addFreshEntity(npc);
        return npc;
    }
}
