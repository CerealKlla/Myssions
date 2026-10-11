package com.github.cerealklla.myssions.quest;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.myssions.bridge.LyfeXpBridge;
import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * All real quest-state mutation logic -- {@code api.Myssions} is a thin public facade over this
 * class, same "api.* is a thin facade over an internal engine" shape every other mod in this suite
 * uses. Never called directly from outside this mod; always go through {@code api.Myssions}. Every
 * per-instance method here takes a {@link QuestInstance#instanceId()}, not a bare {@code
 * Identifier definitionId} -- see that class's own doc for why (two independent acceptances of the
 * same definition are two different instances).
 */
public final class QuestEngine {

    /** "Players can only have 5 quests active [accepted] at once" -- explicit user spec, 2026-10-10. Counts only IN_PROGRESS instances; completed/failed ones don't count against the cap. */
    public static final int MAX_ACCEPTED_QUESTS = 5;

    private QuestEngine() {
    }

    /** Assigns a fresh instance of {@code definitionId} to the player. Returns its new instanceId, or empty if the definition doesn't exist, they're already at the quest cap, or they already have an in-progress instance of this exact definition. */
    public static Optional<UUID> assignQuest(ServerPlayer player, Identifier definitionId) {
        Optional<QuestDefinition> definition = QuestRegistry.get(definitionId);
        if (definition.isEmpty()) {
            return Optional.empty();
        }
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        boolean alreadyInProgress = data.quests().values().stream()
                .anyMatch(i -> i.definitionId().equals(definitionId) && i.status() == QuestStatus.IN_PROGRESS);
        if (alreadyInProgress) {
            return Optional.empty(); // Already has this exact definition in progress -- assigning again would just duplicate it.
        }
        long acceptedCount = data.quests().values().stream().filter(i -> i.status() == QuestStatus.IN_PROGRESS).count();
        if (acceptedCount >= MAX_ACCEPTED_QUESTS) {
            player.sendSystemMessage(Component.literal("You can't accept any more quests right now (max " + MAX_ACCEPTED_QUESTS + " at once)."));
            return Optional.empty();
        }
        QuestInstance instance = QuestInstance.freshlyAssigned(definitionId, player.level().getGameTime());
        player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance));
        player.sendSystemMessage(Component.literal("New quest: " + definition.get().name()));
        return Optional.of(instance.instanceId());
    }

    /** Retries a previously-failed instance -- resets its progress and re-assigns it fresh under a brand-new instanceId (its own hint/location, if any, is forgotten; a quest's own assignment hook re-runs to set fresh ones). Returns the new instanceId, or empty if it's not a failed instance the player actually has, or they're at the quest cap. */
    public static Optional<UUID> retryQuest(ServerPlayer player, UUID instanceId) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance existing = data.quests().get(instanceId);
        if (existing == null || existing.status() != QuestStatus.FAILED) {
            return Optional.empty();
        }
        long acceptedCount = data.quests().values().stream().filter(i -> i.status() == QuestStatus.IN_PROGRESS).count();
        if (acceptedCount >= MAX_ACCEPTED_QUESTS) {
            player.sendSystemMessage(Component.literal("You can't accept any more quests right now (max " + MAX_ACCEPTED_QUESTS + " at once)."));
            return Optional.empty();
        }
        QuestInstance fresh = QuestInstance.freshlyAssigned(existing.definitionId(), player.level().getGameTime());
        player.setData(ModAttachments.QUEST_DATA, data.withQuest(fresh));
        return Optional.of(fresh.instanceId());
    }

    /** Sets an in-progress instance's hidden true hint position -- see {@link QuestInstance#hintPos()}'s own doc. */
    public static void setHint(ServerPlayer player, UUID instanceId, net.minecraft.core.BlockPos pos, String dimensionId) {
        setHint(player, instanceId, pos, dimensionId, Optional.empty());
    }

    /** As above, also recording a real place name for the quest's {@code {location}}-tokened text -- see {@link QuestInstance#locationName()}'s own doc. */
    public static void setHint(ServerPlayer player, UUID instanceId, net.minecraft.core.BlockPos pos, String dimensionId, Optional<String> locationName) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance != null) {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withHint(pos, dimensionId, locationName)));
        }
    }

    public static void setActiveQuest(ServerPlayer player, UUID instanceId) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        if (!data.quests().containsKey(instanceId)) {
            return;
        }
        player.setData(ModAttachments.QUEST_DATA, data.withActiveQuest(instanceId));
    }

    public static Optional<QuestInstance> getInstance(ServerPlayer player, UUID instanceId) {
        return Optional.ofNullable(player.getData(ModAttachments.QUEST_DATA).quests().get(instanceId));
    }

    public static int getCompletedCountForIssuer(ServerPlayer player, String issuerId) {
        return player.getData(ModAttachments.QUEST_DATA).completedCountByIssuer().getOrDefault(issuerId, 0);
    }

    /**
     * Adds {@code amount} progress to one milestone of an in-progress instance, then checks whether
     * every non-optional milestone is now complete -- if so, completes the quest and applies its
     * rewards. Silently no-ops for an instance the player doesn't have, isn't {@code IN_PROGRESS},
     * or a milestone id that doesn't belong to its definition.
     */
    public static void reportMilestoneProgress(ServerPlayer player, UUID instanceId, String milestoneId, int amount) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance == null || instance.status() != QuestStatus.IN_PROGRESS) {
            return;
        }
        Optional<QuestDefinition> definitionOpt = QuestRegistry.get(instance.definitionId());
        if (definitionOpt.isEmpty()) {
            return;
        }
        QuestDefinition definition = definitionOpt.get();
        QuestMilestone milestone = definition.milestones().stream().filter(m -> m.id().equals(milestoneId)).findFirst().orElse(null);
        if (milestone == null) {
            return;
        }
        int newProgress = Math.min(milestone.requiredCount(), instance.milestoneProgress().getOrDefault(milestoneId, 0) + amount);
        QuestInstance updated = instance.withProgress(milestoneId, newProgress);

        boolean allRequiredComplete = definition.milestones().stream()
                .filter(m -> !m.optional())
                .allMatch(updated::isMilestoneComplete);

        if (allRequiredComplete) {
            completeQuest(player, definition, updated);
        } else {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(updated));
        }
    }

    private static void completeQuest(ServerPlayer player, QuestDefinition definition, QuestInstance updated) {
        QuestInstance completed = updated.withStatus(QuestStatus.COMPLETED);
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA)
                .withQuest(completed)
                .withIssuerCompletionIncremented(definition.issuerId());
        player.setData(ModAttachments.QUEST_DATA, data);
        player.sendSystemMessage(Component.literal("Quest complete: " + definition.name()));
        applyRewards(player, definition);
    }

    private static void applyRewards(ServerPlayer player, QuestDefinition definition) {
        for (QuestReward reward : definition.rewards()) {
            switch (reward.kind()) {
                case GOLD -> giveItem(player, Items.GOLD_NUGGET, reward.amount());
                case ITEM -> {
                    Item item = BuiltInRegistries.ITEM.getOptional(reward.itemId()).orElse(null);
                    if (item != null) {
                        giveItem(player, item, reward.amount());
                    }
                }
                case XP -> {
                    if (LyfeXpBridge.isLoaded()) {
                        LyfeXpBridge.grantXp(player, reward.skillId(), reward.amount());
                    }
                }
                case INTANGIBLE -> {
                    // No mechanical effect yet (see QuestReward's own doc) -- already shown as a
                    // reward line in the Quest Log, nothing further to apply here.
                }
            }
        }
    }

    private static void giveItem(ServerPlayer player, Item item, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Completes a "deliver" milestone (design doc item 7) if the player holds enough of its named
     * proof item -- consumes exactly {@link QuestMilestone#requiredCount()} units in one go (our
     * own quest content always grants exactly that many via {@code QuestKillListener}, so a
     * partial turn-in never makes sense here). False if the milestone isn't a deliver milestone,
     * the instance isn't in progress, it's already done, or the player doesn't have enough proof yet.
     */
    public static boolean tryDeliverProof(ServerPlayer player, UUID instanceId, String milestoneId) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance == null || instance.status() != QuestStatus.IN_PROGRESS) {
            return false;
        }
        Optional<QuestDefinition> definitionOpt = QuestRegistry.get(instance.definitionId());
        if (definitionOpt.isEmpty()) {
            return false;
        }
        QuestDefinition definition = definitionOpt.get();
        QuestMilestone milestone = definition.milestones().stream().filter(m -> m.id().equals(milestoneId)).findFirst().orElse(null);
        if (milestone == null || milestone.proofId().isEmpty() || instance.isMilestoneComplete(milestone)) {
            return false;
        }
        String proofId = milestone.proofId().get();
        int needed = milestone.requiredCount();
        if (countProof(player, proofId) < needed) {
            return false;
        }
        consumeProof(player, proofId, needed);
        reportMilestoneProgress(player, instanceId, milestoneId, needed);
        return true;
    }

    /**
     * "Complete Quest" on {@code client.QuestOfferScreen}'s active-quest sub-menu (2026-10-10,
     * explicit user spec: "a sub-menu [with] the option to complete the quest (which checks if all
     * required milestones are complete) and finishes it up"). Attempts to deliver proof for every
     * not-yet-complete deliver milestone (best effort -- a milestone without enough proof on hand
     * just stays incomplete, no error), then reports whether the instance is now actually {@code
     * COMPLETED} -- a kill-count milestone that's still short can never be completed this way, only
     * real kills (via {@code QuestKillListener}) advance those.
     */
    public static boolean tryCompleteQuest(ServerPlayer player, UUID instanceId) {
        QuestInstance instance = player.getData(ModAttachments.QUEST_DATA).quests().get(instanceId);
        if (instance == null || instance.status() != QuestStatus.IN_PROGRESS) {
            return false;
        }
        Optional<QuestDefinition> definitionOpt = QuestRegistry.get(instance.definitionId());
        if (definitionOpt.isEmpty()) {
            return false;
        }
        for (QuestMilestone milestone : definitionOpt.get().milestones()) {
            if (milestone.isDeliverMilestone() && !instance.isMilestoneComplete(milestone)) {
                tryDeliverProof(player, instanceId, milestone.id());
            }
        }
        QuestInstance updated = player.getData(ModAttachments.QUEST_DATA).quests().get(instanceId);
        return updated != null && updated.status() == QuestStatus.COMPLETED;
    }

    /** Read-only check used by {@code QuestTurnIn} to tell "waiting on player" apart from "ready to complete" without consuming anything. */
    public static boolean hasEnoughProof(ServerPlayer player, String proofId, int needed) {
        return countProof(player, proofId) >= needed;
    }

    private static int countProof(ServerPlayer player, String proofId) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof QuestProofItem && QuestProofItem.proofIdOf(stack).equals(proofId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeProof(ServerPlayer player, String proofId, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof QuestProofItem && QuestProofItem.proofIdOf(stack).equals(proofId)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    /** Called by {@code quest.QuestDeadlineTicker} once a quest's own time limit has passed. */
    public static void failQuest(ServerPlayer player, UUID instanceId) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance == null || instance.status() != QuestStatus.IN_PROGRESS) {
            return;
        }
        player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withStatus(QuestStatus.FAILED)));
        QuestRegistry.get(instance.definitionId()).ifPresent(def ->
                player.sendSystemMessage(Component.literal("Quest failed: " + def.name())));
    }
}
