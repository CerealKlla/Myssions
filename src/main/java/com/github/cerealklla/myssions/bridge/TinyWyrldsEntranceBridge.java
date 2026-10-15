package com.github.cerealklla.myssions.bridge;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.cerealklla.myssions.quest.EntranceQuestLink;
import com.github.cerealklla.myssions.quest.QuestEngine;
import com.github.cerealklla.myssions.quest.QuestInstance;
import com.github.cerealklla.myssions.quest.QuestMilestone;
import com.github.cerealklla.myssions.quest.QuestRegistry;
import com.github.cerealklla.tinywyrlds.api.TinyWyrlds;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.EntranceEnterResult;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.WyrldEntranceEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Design doc item 4's quest-triggered Entrance variant -- optional soft dependency, only ever
 * constructed/registered from {@code MyssionsMod}'s constructor behind {@code
 * ModList.get().isLoaded("tinywyrlds")}, same isolation convention as every other optional bridge
 * in this suite (see {@code LyfeXpBridge}'s own doc). {@code entranceId} and a quest instance's own
 * {@code instanceId} are always the same value, by construction (see {@code
 * QuestHooks#registerEntranceHook}) -- there's never a separate mapping to maintain between them.
 *
 * <p>Two real events this listens for, beyond the spawn-on-accept call:
 * <ul>
 * <li>{@link #onEntranceInteract} -- reads {@link WyrldEntranceEntity#lastEnterResult()} right
 * after TinyWyrlds' own higher-priority {@code WyrldEntranceInteractListener} populated it (same
 * interaction, same event instance, ordering guaranteed by {@code EventPriority}, not a race),
 * and records the freshly-generated dungeon's own guardian entity (if the recipe's enemy pool
 * spawned one) against this instance.</li>
 * <li>{@link #onLivingDeath} -- completes {@code isDefeatGuardianMilestone()} the moment a tracked
 * guardian dies, regardless of who/what killed it (same "any means counts" simplification the
 * original spec's own "completing some objective" phrasing allows).</li>
 * <li>{@link #onDimensionChange} -- completes {@code isEscapeMilestone()} once that player leaves
 * {@code tinywyrlds:wyrld_space} for an entrance quest whose guardian step is already done (or
 * never had a guardian to defeat at all, e.g. an empty enemy pool) -- "the player then has to
 * escape" realized literally as "get out of the dungeon dimension."</li>
 * </ul>
 * {@link #recentEntrance} is deliberately in-memory only (lost on a server restart mid-dungeon) --
 * the same "accepted minor debris, not solved with new persistence" tradeoff {@link
 * com.github.cerealklla.myssions.quest.BuyItemTargets}'s own doc already applies elsewhere in this
 * mod, scoped here to "which entrance did this player most recently use," needed only to connect
 * {@link #onDimensionChange} back to a specific instance.
 */
public final class TinyWyrldsEntranceBridge {

    private static final String WYRLD_SPACE_DIMENSION_ID = "tinywyrlds:wyrld_space";

    private static final Map<UUID, UUID> recentEntrance = new ConcurrentHashMap<>();

    public static boolean isLoaded() {
        return ModList.get().isLoaded("tinywyrlds");
    }

    /** Called from {@code quest.QuestHooks#registerEntranceHook} on quest acceptance. */
    public static void spawnEntranceForQuest(ServerLevel level, BlockPos pos, UUID instanceId, String recipeName, UUID ownerPlayerId) {
        TinyWyrlds.spawnEntrance(level, pos, instanceId, recipeName, ownerPlayerId);
        EntranceQuestLink.get(level.getServer()).create(instanceId, recipeName);
    }

    @SubscribeEvent
    public void onEntranceInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getTarget() instanceof WyrldEntranceEntity entrance)) {
            return;
        }
        UUID instanceId = entrance.entranceId();
        if (instanceId == null) {
            return;
        }
        recentEntrance.put(player.getUUID(), instanceId);
        EntranceEnterResult result = entrance.lastEnterResult();
        if (result != null && result.freshlyGenerated() && !result.spawnedEnemyIds().isEmpty()) {
            EntranceQuestLink.get(player.level().getServer()).setGuardian(instanceId, result.spawnedEnemyIds().get(0));
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        EntranceQuestLink.get(level.getServer()).findByGuardian(dead.getUUID()).ifPresent(instanceId ->
                completeMilestoneKind(level, instanceId, QuestMilestone::isDefeatGuardianMilestone));
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        ResourceKey<Level> from = event.getFrom();
        if (from == null || !WYRLD_SPACE_DIMENSION_ID.equals(from.identifier().toString())) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID instanceId = recentEntrance.get(player.getUUID());
        if (instanceId == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        completeMilestoneKind(level, instanceId, QuestMilestone::isEscapeMilestone);
    }

    private static void completeMilestoneKind(ServerLevel level, UUID instanceId, java.util.function.Predicate<QuestMilestone> kind) {
        ServerPlayer player = findOwningPlayer(level, instanceId);
        if (player == null) {
            return;
        }
        QuestEngine.getInstance(player, instanceId).ifPresent(instance -> {
            if (instance.status() != com.github.cerealklla.myssions.quest.QuestStatus.IN_PROGRESS) {
                return;
            }
            QuestRegistry.get(instance.definitionId()).ifPresent(definition -> {
                for (QuestMilestone milestone : definition.milestones()) {
                    if (kind.test(milestone) && !instance.isMilestoneComplete(milestone)) {
                        QuestEngine.reportMilestoneProgress(player, instanceId, milestone.id(), 1);
                    }
                }
            });
        });
    }

    /** {@code QuestEngine#getInstance} needs the owning player, not just the instance id -- a
     *  dimension-change/death event hands us a specific player already (the one who died, the one
     *  who left), so this only exists for {@link #onLivingDeath}'s case, where the dying entity is
     *  the guardian, not the player; the player must be found via the player this guardian was
     *  originally spawned for. Since only the owning player can ever hold this instance (an
     *  entrance's own {@code ownerPlayerId}), checking the currently-online player list for whoever
     *  actually has this instance is correct and avoids needing yet another side table. */
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
