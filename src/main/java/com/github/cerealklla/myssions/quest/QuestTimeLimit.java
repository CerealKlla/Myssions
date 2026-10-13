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

    private static final long TICKS_PER_DAY = 24000L;
    private static final long MILLIS_PER_TICK = 50L; // 20 TPS.

    /**
     * The game-time instant this instance actually expires at, for {@link Kind#GAME_TICKS_FROM_ASSIGNMENT}/
     * {@link Kind#NEXT_GAME_DAY_BOUNDARY} only -- the one shared formula {@code QuestDeadlineTicker}'s
     * own expiry check and {@code client.ActiveQuestOverlay}'s countdown bar both call, added
     * 2026-10-13 specifically so the two can never silently drift apart. Meaningless ({@code
     * Long.MAX_VALUE}, "never") for {@link Kind#NONE}/{@link Kind#REAL_WORLD_DATE} -- the latter has
     * its own absolute {@link #realWorldEpochMillis()} instead, not anchored to game time at all.
     *
     * <p><b>{@link Kind#GAME_TICKS_FROM_ASSIGNMENT} only, prefer {@link #hasExpiredReal}/{@link
     * #remainingRealMillis} instead whenever a real {@code assignedAtRealEpochMillis} is available</b>
     * (added 2026-10-13) -- this tick-based formula silently pauses for the real minutes a dedicated
     * server process itself is down (a deploy restart, a crash), which a real live report caught
     * directly: "this quest remaining time seems to have not decreased while I was away... it's
     * supposed to be clock time, not in-game." {@link Kind#NEXT_GAME_DAY_BOUNDARY} is deliberately
     * NOT given a real-time equivalent -- "before the next in-game day" is an in-game-calendar
     * concept by its own definition, correctly tied to the tick clock.
     */
    public long deadlineGameTime(long assignedAtGameTime) {
        return switch (kind) {
            case GAME_TICKS_FROM_ASSIGNMENT -> assignedAtGameTime + ticksFromAssignment;
            case NEXT_GAME_DAY_BOUNDARY -> ((assignedAtGameTime / TICKS_PER_DAY) + 1) * TICKS_PER_DAY;
            default -> Long.MAX_VALUE;
        };
    }

    /** The real total game-time span this instance's deadline was given (varies per-instance for {@link Kind#NEXT_GAME_DAY_BOUNDARY}, since it depends where in the day it was assigned) -- the countdown bar's own denominator. {@code -1} ("no meaningful total") for {@link Kind#NONE}/{@link Kind#REAL_WORLD_DATE}. */
    public long totalGameTicks(long assignedAtGameTime) {
        return switch (kind) {
            case GAME_TICKS_FROM_ASSIGNMENT -> ticksFromAssignment;
            case NEXT_GAME_DAY_BOUNDARY -> deadlineGameTime(assignedAtGameTime) - assignedAtGameTime;
            default -> -1L;
        };
    }

    /** True only for {@link Kind#GAME_TICKS_FROM_ASSIGNMENT} -- the one kind meant as a genuine real-world duration (see this class's own "e.g. 72000 ticks (1 hour of real time at 20 TPS)" doc), as opposed to {@link Kind#NEXT_GAME_DAY_BOUNDARY}'s deliberately in-game-calendar meaning. */
    public boolean isRealTimeDuration() {
        return kind == Kind.GAME_TICKS_FROM_ASSIGNMENT;
    }

    /** The real wall-clock instant this instance expires at -- {@link #isRealTimeDuration()} only. */
    public long deadlineRealEpochMillis(long assignedAtRealEpochMillis) {
        return assignedAtRealEpochMillis + ticksFromAssignment * MILLIS_PER_TICK;
    }

    /** The real total wall-clock span this instance's deadline was given -- {@link #isRealTimeDuration()} only, the real-time countdown bar's own denominator. */
    public long totalRealMillis() {
        return ticksFromAssignment * MILLIS_PER_TICK;
    }
}
