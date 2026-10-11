package com.github.cerealklla.myssions.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A quest's own time limit -- "complete this within an hour", "before tomorrow" (an in-game day
 * boundary), "by a real-world date", or (the common case) no limit at all, per the user's own
 * spec. {@code NONE} is the default every {@link QuestDefinition} gets unless a kind is chosen
 * explicitly. Enforcement lives in {@code quest.QuestDeadlineTicker} -- a periodic scan (same
 * shape as every other "check every known X" ticker in this suite) that fails any in-progress
 * {@link QuestInstance} whose deadline has passed.
 */
public record QuestTimeLimit(Kind kind, long ticksFromAssignment, long realWorldEpochMillis) {

    public enum Kind { NONE, GAME_TICKS_FROM_ASSIGNMENT, NEXT_GAME_DAY_BOUNDARY, REAL_WORLD_DATE }

    public static final QuestTimeLimit NONE = new QuestTimeLimit(Kind.NONE, 0, 0);

    public static final Codec<QuestTimeLimit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(QuestTimeLimit::kind),
            Codec.LONG.optionalFieldOf("ticks_from_assignment", 0L).forGetter(QuestTimeLimit::ticksFromAssignment),
            Codec.LONG.optionalFieldOf("real_world_epoch_millis", 0L).forGetter(QuestTimeLimit::realWorldEpochMillis)
    ).apply(instance, QuestTimeLimit::new));

    /** "Complete this within {@code ticks} game ticks of being assigned" -- e.g. 72000 ticks (1 hour of real time at 20 TPS). */
    public static QuestTimeLimit withinTicksOfAssignment(long ticks) {
        return new QuestTimeLimit(Kind.GAME_TICKS_FROM_ASSIGNMENT, ticks, 0);
    }

    /** "Complete this before the next in-game day boundary" ("before tomorrow"). */
    public static QuestTimeLimit nextGameDayBoundary() {
        return new QuestTimeLimit(Kind.NEXT_GAME_DAY_BOUNDARY, 0, 0);
    }

    /** "Complete this by a fixed real-world wall-clock date." */
    public static QuestTimeLimit byRealWorldDate(long epochMillis) {
        return new QuestTimeLimit(Kind.REAL_WORLD_DATE, 0, epochMillis);
    }

    public boolean hasLimit() {
        return kind != Kind.NONE;
    }
}
