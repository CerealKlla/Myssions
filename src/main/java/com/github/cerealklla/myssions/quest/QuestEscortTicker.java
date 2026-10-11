package com.github.cerealklla.myssions.quest;

import java.util.List;
import java.util.UUID;

import com.github.cerealklla.myssions.quest.entity.CaravanEntity;
import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives every in-progress escort instance's caravan, added 2026-10-11 for the caravan-raid quest
 * style (see design-document.md's quest taxonomy). Explicit user spec for the raid trigger: "being
 * near the caravan grants players an 'Escort' status... the quest would check at each roadway stake
 * if any players have a status with their UUID, and if not trigger the attack" -- implemented here
 * as a direct presence check at the instant of arrival (no persisted status/grace window yet; see
 * {@link #ESCORT_GUARD_RADIUS_BLOCKS}), since a stake-arrival event is already a single discrete
 * moment, not a continuous window that needs tracking between checks.
 *
 * <p>Each cycle, for every active escort instance: continuously overwrites {@link
 * QuestInstance#hintPos()} with the caravan's live position (via the already-existing {@link
 * QuestEngine#setHint}) -- this alone makes {@code QuestKillListener}'s geofence and {@code
 * QuestMobSpawnTicker}'s spawn-guarantee track the moving caravan correctly, with zero changes to
 * either class. Then checks arrival at the current waypoint: if the route still has more waypoints,
 * checks for a guarding player nearby and (if none) re-arms {@code QuestMobSpawnTicker}'s one-shot
 * gate so it immediately spawns a raid against the caravan's current position; if the route is now
 * empty, completes the escort milestone and despawns the caravan.
 */
public final class QuestEscortTicker {

    private static final int INTERVAL_TICKS = 20; // 1 second.
    private static final double ESCORT_GUARD_RADIUS_BLOCKS = 20.0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
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
        for (QuestInstance instance : List.copyOf(data.quests().values())) {
            if (instance.status() != QuestStatus.IN_PROGRESS || instance.escort().entityUuid().isEmpty()) {
                continue;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            UUID caravanUuid = instance.escort().entityUuid().get();
            Entity entity = level.getEntity(caravanUuid);
            if (!(entity instanceof CaravanEntity caravan) || !caravan.isAlive()) {
                QuestEngine.failQuest(player, instance.instanceId());
                continue;
            }

            QuestEngine.setHint(player, instance.instanceId(), caravan.blockPosition(), level.dimension().identifier().toString());

            if (!caravan.hasArrived()) {
                continue;
            }

            QuestEngine.advanceEscortWaypoint(player, instance.instanceId());
            QuestInstance updated = QuestEngine.getInstance(player, instance.instanceId()).orElse(null);
            if (updated == null) {
                continue;
            }
            List<BlockPos> remaining = updated.escort().remainingRoute();
            if (remaining.isEmpty()) {
                completeEscort(player, caravan, updated);
                continue;
            }

            caravan.setTargetPos(remaining.get(0));
            if (!hasNearbyGuard(level, caravan.blockPosition())) {
                QuestEngine.resetMobsSpawned(player, instance.instanceId());
            }
        }
    }

    private boolean hasNearbyGuard(ServerLevel level, BlockPos caravanPos) {
        double radiusSq = ESCORT_GUARD_RADIUS_BLOCKS * ESCORT_GUARD_RADIUS_BLOCKS;
        for (ServerPlayer online : level.getServer().getPlayerList().getPlayers()) {
            if (online.level() == level && online.blockPosition().distSqr(caravanPos) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private void completeEscort(ServerPlayer player, CaravanEntity caravan, QuestInstance instance) {
        QuestRegistry.get(instance.definitionId()).ifPresent(definition -> {
            for (QuestMilestone milestone : definition.milestones()) {
                if (milestone.isEscortMilestone() && !instance.isMilestoneComplete(milestone)) {
                    QuestEngine.reportMilestoneProgress(player, instance.instanceId(), milestone.id(), 1);
                }
            }
        });
        caravan.discard();
    }
}
