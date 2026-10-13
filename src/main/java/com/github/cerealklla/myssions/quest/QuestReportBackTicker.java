package com.github.cerealklla.myssions.quest;

import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Spawns an "unborn town" quest's reporter NPC (see {@code NpcQuests#registerUnbornTownQuest}) once
 * the player actually reaches the quest's real target, instead of at acceptance time.
 *
 * <p><b>Real live report, 2026-10-12</b>: "As soon as I accepted it the game spawned a named NPC on
 * top of me. I right clicked him and instantly completed the quest. There was no milestone to go
 * somewhere or anything like that." Root cause: {@code registerUnbornTownQuest}'s acceptance hook
 * called {@code QuestNpcSpawner.spawn} immediately, at the player's own position -- unlike its
 * sibling pattern ({@code QuestHooks#registerTalkNpcHook}, used by "Find Old Harlen"/"The Hermit's
 * Apprentice"), which correctly spawns 80-200 blocks away, genuinely requiring the player to go find
 * it. An "unborn town" quest's whole premise ("go find it... bring back word") has no fixed spot to
 * spawn the reporter at at acceptance time, since the real target is resolved fresh by seed-math
 * prediction and is often over a mile out -- this ticker closes that gap the same way {@link
 * QuestMobSpawnTicker} already guarantees a kill quest's mobs: don't act until the player is
 * actually there.
 *
 * <p>Deliberately reuses {@link QuestInstance#mobsSpawned()} as the one-shot gate (misleadingly
 * named for this non-mob case, but semantically identical: "has this instance's completability-
 * trigger already fired") rather than adding a new field -- no unborn-town quest has a kill
 * milestone, so there's no conflict, and {@code QuestInstance} is already near Mojang's codec arity
 * limit (see its own doc).
 */
public final class QuestReportBackTicker {

    private static final int INTERVAL_TICKS = 20; // 1 second.
    private static final double TRIGGER_RADIUS_BLOCKS = 64.0; // Matches QuestMobSpawnTicker's own.

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
        for (QuestInstance instance : data.quests().values()) {
            if (instance.status() != QuestStatus.IN_PROGRESS || instance.mobsSpawned()
                    || instance.hintPos().isEmpty() || instance.hintDimensionId().isEmpty()) {
                continue;
            }
            var reporterName = UnbornTownReporters.get(instance.definitionId());
            if (reporterName.isEmpty()) {
                continue; // Not an unborn-town quest -- QuestMobSpawnTicker handles real kill targets.
            }
            Identifier dimensionId = Identifier.tryParse(instance.hintDimensionId().get());
            if (dimensionId == null || !player.level().dimension().identifier().equals(dimensionId)
                    || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            BlockPos truePos = instance.hintPos().get();
            if (player.distanceToSqr(truePos.getX() + 0.5, truePos.getY(), truePos.getZ() + 0.5) > TRIGGER_RADIUS_BLOCKS * TRIGGER_RADIUS_BLOCKS) {
                continue;
            }
            QuestNpcSpawner.spawn(level, player.blockPosition(), player.getUUID(), instance.instanceId(), false, reporterName.get());
            QuestEngine.markMobsSpawned(player, instance.instanceId());
        }
    }
}
