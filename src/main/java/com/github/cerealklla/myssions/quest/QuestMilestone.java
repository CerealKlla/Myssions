package com.github.cerealklla.myssions.quest;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One step of a {@link QuestDefinition} -- a plain description plus an optional flag and a
 * required count (e.g. "Kill 5 Zombies" has {@code requiredCount == 5}; most milestones are
 * {@code 1}). Three real trigger types are modeled directly (added 2026-10-10):
 *
 * <ul>
 *   <li>{@link #killTargetEntityType()} present -- {@code quest.QuestKillListener} increments this
 *   milestone on a matching {@code LivingDeathEvent} killed by the player, and grants one
 *   {@code quest.QuestProofItem} (tagged {@link #proofId()}) per qualifying kill, up to
 *   {@code requiredCount} -- so by the time the kill count is done, the player is already holding
 *   exactly enough proof to turn in. Item 7.</li>
 *   <li>{@link #isDeliverMilestone()} true (with {@link #proofId()} present) -- completed by
 *   physically handing that many proof items back to the quest's issuer (an NPC or a sign
 *   interaction, see {@code quest.QuestTurnIn}), not by any automatic event. Item 7.</li>
 *   <li>{@link #isTalkMilestone()} true -- completed simply by interacting with the one {@code
 *   quest.QuestNpcEntity} spawned for this quest (design doc items 8/9 -- a named, possibly
 *   day/night-conditional, per-player-visible NPC), see that entity's own doc.</li>
 * </ul>
 *
 * A milestone with none of these is a plain manual one, completed only via a direct {@code
 * api.Myssions#reportMilestoneProgress}/{@code #completeMilestone} call. {@link #proofId()} is
 * shared by both a kill milestone (what it grants) and the matching deliver milestone (what it
 * consumes) within the same quest -- deliberately a bare string, not a whole {@link QuestReward},
 * since it names a *kind* of proof item, not an amount to grant as a reward.
 */
public record QuestMilestone(String id, String description, boolean optional, int requiredCount,
                              Optional<Identifier> killTargetEntityType, Optional<String> proofId,
                              boolean isDeliverMilestone, boolean isTalkMilestone) {

    public static final Codec<QuestMilestone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(QuestMilestone::id),
            Codec.STRING.fieldOf("description").forGetter(QuestMilestone::description),
            Codec.BOOL.optionalFieldOf("optional", false).forGetter(QuestMilestone::optional),
            Codec.INT.optionalFieldOf("required_count", 1).forGetter(QuestMilestone::requiredCount),
            Identifier.CODEC.optionalFieldOf("kill_target_entity_type").forGetter(QuestMilestone::killTargetEntityType),
            Codec.STRING.optionalFieldOf("proof_id").forGetter(QuestMilestone::proofId),
            Codec.BOOL.optionalFieldOf("is_deliver_milestone", false).forGetter(QuestMilestone::isDeliverMilestone),
            Codec.BOOL.optionalFieldOf("is_talk_milestone", false).forGetter(QuestMilestone::isTalkMilestone)
    ).apply(instance, QuestMilestone::new));

    public static QuestMilestone simple(String id, String description) {
        return new QuestMilestone(id, description, false, 1, Optional.empty(), Optional.empty(), false, false);
    }

    public static QuestMilestone simple(String id, String description, boolean optional) {
        return new QuestMilestone(id, description, optional, 1, Optional.empty(), Optional.empty(), false, false);
    }

    public static QuestMilestone kill(String id, String description, Identifier entityType, int count, String proofId) {
        return new QuestMilestone(id, description, false, count, Optional.of(entityType), Optional.of(proofId), false, false);
    }

    public static QuestMilestone deliver(String id, String description, String proofId, int count) {
        return new QuestMilestone(id, description, false, count, Optional.empty(), Optional.of(proofId), true, false);
    }

    public static QuestMilestone talk(String id, String description) {
        return new QuestMilestone(id, description, false, 1, Optional.empty(), Optional.empty(), false, true);
    }
}
