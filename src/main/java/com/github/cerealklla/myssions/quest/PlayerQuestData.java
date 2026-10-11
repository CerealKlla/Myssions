package com.github.cerealklla.myssions.quest;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * One player's full quest state -- a single NeoForge attachment (see {@code
 * registration.ModAttachments#QUEST_DATA}), synced server-authoritative, never predicted
 * client-side. {@code quests} holds every quest instance this player has ever been assigned (in
 * progress, completed, or failed -- never removed, so "Failed Quests" at the bottom of the Quest
 * Log has something to show and a future "retry" button has state to reset), keyed by each
 * instance's own hidden {@link QuestInstance#instanceId()} (not its definition -- see that class's
 * own doc for why a shared definition key can't represent two independent acceptances). {@code
 * activeQuestId} is which instance (if any) shows on the Active Quest HUD. {@code
 * completedCountByIssuer} is the running per-issuer total the user's own spec asks for, for a
 * future karma/reputation system to build on.
 */
public record PlayerQuestData(Map<UUID, QuestInstance> quests, Optional<UUID> activeQuestId,
                               Map<String, Integer> completedCountByIssuer) {

    public static final PlayerQuestData EMPTY = new PlayerQuestData(Map.of(), Optional.empty(), Map.of());

    public static final Codec<PlayerQuestData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, QuestInstance.CODEC).optionalFieldOf("quests", Map.of()).forGetter(PlayerQuestData::quests),
            UUIDUtil.CODEC.optionalFieldOf("active_quest_id").forGetter(PlayerQuestData::activeQuestId),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("completed_count_by_issuer", Map.of()).forGetter(PlayerQuestData::completedCountByIssuer)
    ).apply(instance, PlayerQuestData::new));

    public PlayerQuestData withQuest(QuestInstance instance) {
        Map<UUID, QuestInstance> updated = new java.util.HashMap<>(quests);
        updated.put(instance.instanceId(), instance);
        return new PlayerQuestData(Map.copyOf(updated), activeQuestId, completedCountByIssuer);
    }

    public PlayerQuestData withActiveQuest(UUID instanceId) {
        return new PlayerQuestData(quests, Optional.ofNullable(instanceId), completedCountByIssuer);
    }

    public PlayerQuestData withIssuerCompletionIncremented(String issuerId) {
        Map<String, Integer> updated = new java.util.HashMap<>(completedCountByIssuer);
        updated.merge(issuerId, 1, Integer::sum);
        return new PlayerQuestData(quests, activeQuestId, Map.copyOf(updated));
    }
}
