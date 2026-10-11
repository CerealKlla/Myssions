package com.github.cerealklla.myssions.quest;

import java.util.Random;

import com.github.cerealklla.myssions.bridge.LyfeWaypointBridge;
import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives the live, knowledge-scaled "vague circle" quest hint (design doc item 2) -- only
 * registered when Lyfe is loaded (see {@code MyssionsMod}'s own constructor), since it only ever
 * does anything through {@code bridge.LyfeWaypointBridge}. Every {@link #INTERVAL_TICKS}, for each
 * online player's own in-progress quests that have a {@link QuestInstance#hintPos()} set: recomputes
 * a deterministically-seeded (stable across ticks, not re-rolled) vague center + radius scaled by
 * how many {@code knowledge.KnowledgeFactor}s the player currently has for this hint, and pushes it.
 * Getting genuinely close to the true position grants the final {@code POSITION} factor, sharpening
 * the hint to (near-)exact -- the live "recalculated to be more exact" behavior the user's own spec
 * asked for.
 *
 * <p><b>Known simplification</b>: the spec also asked for the marker to auto-hide once the player is
 * far enough away (scaled by knowledge quality) and reappear identically on return. This pass always
 * shows the vague circle for the whole quest duration instead -- flagged in context/decisions.md,
 * not an oversight; the core "vagueness scales with knowledge and sharpens as you learn more" value
 * is what this slice actually delivers.
 */
public final class QuestWaypointTicker {

    private static final int INTERVAL_TICKS = 20; // 1 second -- this drives live visuals, not a cheap background scan.
    private static final double MAX_RADIUS_BLOCKS = 150.0;
    private static final double MIN_RADIUS_BLOCKS = 8.0;
    private static final double DISCOVERY_RADIUS_BLOCKS = 20.0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!LyfeWaypointBridge.isLoaded()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(ServerPlayer player) {
        var data = player.getData(ModAttachments.QUEST_DATA);
        boolean anyShown = false;
        for (QuestInstance instance : data.quests().values()) {
            if (instance.status() != QuestStatus.IN_PROGRESS || instance.hintPos().isEmpty() || instance.hintDimensionId().isEmpty()) {
                continue;
            }
            Identifier dimensionId = Identifier.tryParse(instance.hintDimensionId().get());
            if (dimensionId == null || !player.level().dimension().identifier().equals(dimensionId)) {
                continue;
            }
            BlockPos truePos = instance.hintPos().get();
            long placeId = placeIdFor(instance.instanceId());

            double distance = Math.sqrt(player.distanceToSqr(truePos.getX() + 0.5, truePos.getY(), truePos.getZ() + 0.5));
            if (distance <= DISCOVERY_RADIUS_BLOCKS) {
                LyfeWaypointBridge.grantKnowledge(player, placeId, true, true, true);
            }

            int knownFactors = LyfeWaypointBridge.knownFactorCount(player, placeId);
            double fraction = knownFactors / 3.0;
            double radius = MAX_RADIUS_BLOCKS - (MAX_RADIUS_BLOCKS - MIN_RADIUS_BLOCKS) * fraction;
            BlockPos center = seededOffset(truePos, placeId, radius);

            LyfeWaypointBridge.setWaypoint(player, player.level().dimension(), center, radius);
            anyShown = true;
            break; // Only one quest waypoint shown at a time -- matches the user's own single-active-waypoint precedent.
        }
        if (!anyShown) {
            LyfeWaypointBridge.clearWaypoint(player);
        }
    }

    /**
     * Stable per quest *instance* (2026-10-10, switched from a (definition, player) hash once
     * instances gained their own real {@code UUID} -- see {@link QuestInstance}'s own doc) so
     * repeated calls (every tick interval) don't jitter the circle around, and so a retried quest
     * (a brand-new instanceId) never reuses a previous attempt's stale knowledge, closing a
     * previously-flagged edge case. Public so a quest's own assignment hook (e.g. {@code
     * NpcQuests#findOldHarlen}/{@code #registerOnSiteHook}) can grant initial knowledge factors
     * against the exact same key this ticker will later read.
     */
    public static long placeIdFor(java.util.UUID instanceId) {
        return instanceId.getMostSignificantBits() ^ instanceId.getLeastSignificantBits();
    }

    /** A deterministic point within {@code radius} blocks of {@code truePos}, seeded so it's identical every call for the same inputs -- recomputed (and so changes) only when {@code radius} itself changes. */
    private static BlockPos seededOffset(BlockPos truePos, long seed, double radius) {
        if (radius <= 0) {
            return truePos;
        }
        Random random = new Random(seed ^ Double.doubleToLongBits(radius));
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = random.nextDouble() * radius * 0.6; // Stays well inside the circle, not pinned to its edge.
        int dx = (int) Math.round(Math.cos(angle) * distance);
        int dz = (int) Math.round(Math.sin(angle) * distance);
        return truePos.offset(dx, 0, dz);
    }
}
