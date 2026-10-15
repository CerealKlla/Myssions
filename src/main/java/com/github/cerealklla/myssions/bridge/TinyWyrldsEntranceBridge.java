package com.github.cerealklla.myssions.bridge;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.myssions.quest.EntranceQuestLink;
import com.github.cerealklla.myssions.quest.QuestEngine;
import com.github.cerealklla.myssions.quest.QuestInstance;
import com.github.cerealklla.myssions.quest.QuestMilestone;
import com.github.cerealklla.myssions.quest.QuestRegistry;
import com.github.cerealklla.myssions.quest.QuestStatus;
import com.github.cerealklla.myssions.registration.ModAttachments;
import com.github.cerealklla.tinywyrlds.api.TinyWyrlds;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.EntranceEnterResult;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Design doc item 4's quest-triggered Entrance variant -- optional soft dependency, only ever
 * constructed/registered from {@code MyssionsMod}'s constructor behind {@code
 * ModList.get().isLoaded("tinywyrlds")}, same isolation convention as every other optional bridge
 * in this suite (see {@code LyfeXpBridge}'s own doc). {@code entranceId} and a quest instance's own
 * {@code instanceId} are always the same value, by construction (see {@code
 * QuestHooks#registerEntranceHook}) -- there's never a separate mapping to maintain between them.
 *
 * <p>Guardian-tracking is polling, not event-driven ({@code TinyWyrldsEntranceGuardianTicker} calls
 * {@link #pollForGuardian}) -- a deliberate redesign, 2026-10-15, after TinyWyrlds' own Entrance
 * trigger changed from a right-click ({@code PlayerInteractEvent.EntityInteract}, which this bridge
 * used to piggyback on at a lower {@code EventPriority}) to a walk-in proximity check (explicit user
 * request: "I want the entrance to be similar to actually enter the Wyrld"). There's no shared
 * interact event left to observe, so this mod now just asks TinyWyrlds directly (via {@code
 * api.TinyWyrlds#getEnterResult}) whether a given entrance has produced a result yet -- the same
 * "listening mod polls/calls through a bridge" convention this suite already uses everywhere else,
 * arguably a better fit than the event-ordering trick it replaces.
 *
 * <ul>
 * <li>{@link #pollForGuardian} -- records the freshly-generated dungeon's own guardian entity (if
 * the recipe's ENEMY pool spawned one) against this instance, the first time a result appears.</li>
 * <li>{@link #onLivingDeath} -- completes {@code isDefeatGuardianMilestone()} the moment a tracked
 * guardian dies, regardless of who/what killed it (same "any means counts" simplification the
 * original spec's own "completing some objective" phrasing allows).</li>
 * <li>{@link #onDimensionChange} -- completes {@code isEscapeMilestone()} for every one of that
 * player's own entrance-linked quest instances the moment they leave {@code
 * tinywyrlds:wyrld_space} -- found by scanning their own active quests for ones with an {@link
 * EntranceQuestLink} entry, not a separately-tracked "last entrance used" map (removed 2026-10-15
 * -- correctly handles a player juggling more than one entrance quest at once, which a single
 * last-used value never could).</li>
 * </ul>
 */
public final class TinyWyrldsEntranceBridge {

    private static final String WYRLD_SPACE_DIMENSION_ID = "tinywyrlds:wyrld_space";

    public static boolean isLoaded() {
        return ModList.get().isLoaded("tinywyrlds");
    }

    /** Called from {@code quest.QuestHooks#registerEntranceHook} on quest acceptance. */
    public static void spawnEntranceForQuest(ServerLevel level, BlockPos pos, UUID instanceId, String recipeName, UUID ownerPlayerId) {
        TinyWyrlds.spawnEntrance(level, pos, instanceId, recipeName, ownerPlayerId);
        EntranceQuestLink.get(level.getServer()).create(instanceId, recipeName);
    }

    /** Called from {@code TinyWyrldsEntranceGuardianTicker} for every still-guardian-less tracked
     *  entrance -- a no-op until {@code api.TinyWyrlds#getEnterResult} actually has something
     *  (nobody's walked in yet, or the recipe's ENEMY pool was empty). */
    public static void pollForGuardian(ServerLevel level, UUID instanceId) {
        Optional<EntranceEnterResult> result = TinyWyrlds.getEnterResult(instanceId);
        if (result.isEmpty() || result.get().spawnedEnemyIds().isEmpty()) {
            return;
        }
        EntranceQuestLink.get(level.getServer()).setGuardian(instanceId, result.get().spawnedEnemyIds().get(0));
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        EntranceQuestLink.get(level.getServer()).findByGuardian(event.getEntity().getUUID()).ifPresent(instanceId ->
                completeMilestoneKind(level, instanceId, QuestMilestone::isDefeatGuardianMilestone));
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        ResourceKey<Level> from = event.getFrom();
        if (from == null || !WYRLD_SPACE_DIMENSION_ID.equals(from.identifier().toString())) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        EntranceQuestLink links = EntranceQuestLink.get(level.getServer());
        for (QuestInstance instance : player.getData(ModAttachments.QUEST_DATA).quests().values()) {
            if (instance.status() == QuestStatus.IN_PROGRESS && links.get(instance.instanceId()).isPresent()) {
                completeMilestoneForInstance(player, instance, QuestMilestone::isEscapeMilestone);
            }
        }
    }

    private static void completeMilestoneKind(ServerLevel level, UUID instanceId, java.util.function.Predicate<QuestMilestone> kind) {
        ServerPlayer player = findOwningPlayer(level, instanceId);
        if (player == null) {
            return;
        }
        QuestEngine.getInstance(player, instanceId).ifPresent(instance -> {
            if (instance.status() == QuestStatus.IN_PROGRESS) {
                completeMilestoneForInstance(player, instance, kind);
            }
        });
    }

    private static void completeMilestoneForInstance(ServerPlayer player, QuestInstance instance, java.util.function.Predicate<QuestMilestone> kind) {
        QuestRegistry.get(instance.definitionId()).ifPresent(definition -> {
            for (QuestMilestone milestone : definition.milestones()) {
                if (kind.test(milestone) && !instance.isMilestoneComplete(milestone)) {
                    QuestEngine.reportMilestoneProgress(player, instance.instanceId(), milestone.id(), 1);
                }
            }
        });
    }

    /** {@code QuestEngine#getInstance} needs the owning player, not just the instance id -- {@link
     *  #onLivingDeath} only ever hands us the dying guardian, not the player, so the player must be
     *  found via whoever actually holds this instance. Only the owning player ever can (an
     *  entrance's own {@code ownerPlayerId}), so checking the currently-online player list for
     *  whoever has this instance is correct and avoids needing yet another side table. */
    private static ServerPlayer findOwningPlayer(ServerLevel level, UUID instanceId) {
        for (ServerPlayer candidate : level.getServer().getPlayerList().getPlayers()) {
            Optional<QuestInstance> instance = QuestEngine.getInstance(candidate, instanceId);
            if (instance.isPresent()) {
                return candidate;
            }
        }
        return null;
    }
}
