package com.github.cerealklla.myssions.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One step of a {@link QuestDefinition} -- a plain description plus an optional flag and a
 * required count (e.g. "Kill 5 Zombies" has {@code requiredCount == 5}; most milestones are
 * {@code 1}). Milestone-TYPE-specific mechanics (kill-tracking, location-arrival, item-delivery)
 * are deliberately NOT modeled here yet -- this first slice only needs milestones to be
 * displayable and completable via {@code api.Myssions#reportMilestoneProgress}; a future pass adds
 * real trigger wiring (mob-death listeners, region-enter listeners, etc.) on top of this same
 * shape without needing to change it.
 */
public record QuestMilestone(String id, String description, boolean optional, int requiredCount) {

    public static final Codec<QuestMilestone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(QuestMilestone::id),
            Codec.STRING.fieldOf("description").forGetter(QuestMilestone::description),
            Codec.BOOL.optionalFieldOf("optional", false).forGetter(QuestMilestone::optional),
            Codec.INT.optionalFieldOf("required_count", 1).forGetter(QuestMilestone::requiredCount)
    ).apply(instance, QuestMilestone::new));

    public QuestMilestone(String id, String description) {
        this(id, description, false, 1);
    }

    public QuestMilestone(String id, String description, boolean optional) {
        this(id, description, optional, 1);
    }
}
