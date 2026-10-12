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
 * client-side. {@code quests} holds every quest instance this player has been assigned, keyed by
 * each instance's own hidden {@link QuestInstance#instanceId()} (not its definition -- see that
 * class's own doc for why a shared definition key can't represent two independent acceptances).
 * Completed/failed instances are deliberately kept (never removed) so "Failed Quests" at the bottom
 * of the Quest Log has something to show -- there's no in-UI retry path at all (removed 2026-10-12,
 * explicit user instruction: "I don't want such a capability"; the issuing NPC offering the same
 * quest again is the only way back in), it's purely a record. The one exception to "never removed"
 * is a player-initiated {@link #withoutQuest}, see {@code QuestEngine#abandonQuest}'s own doc for why
 * that specific path really does delete the instance instead. {@code activeQuestId} is which
 * instance (if any) shows on the Active Quest HUD. {@code completedCountByIssuer} is the running
 * per-issuer total the user's own spec asks for, for a future karma/reputation system to build on.
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

    /** Fully deletes an instance -- see class doc's note on why this is the one exception to "never removed." Also clears {@code activeQuestId} if it pointed at this instance. */
    public PlayerQuestData withoutQuest(UUID instanceId) {
        Map<UUID, QuestInstance> updated = new java.util.HashMap<>(quests);
        updated.remove(instanceId);
        Optional<UUID> newActive = activeQuestId.map(id -> id.equals(instanceId) ? null : id);
        return new PlayerQuestData(Map.copyOf(updated), newActive, completedCountByIssuer);
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
