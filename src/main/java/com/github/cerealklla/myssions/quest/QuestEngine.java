package com.github.cerealklla.myssions.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /** "Players can only have 5 quests active [accepted] at once" -- explicit user spec, 2026-10-10. Counts only IN_PROGRESS instances; completed/failed/awaiting-pickup ones don't count against the cap. */
    public static final int MAX_ACCEPTED_QUESTS = 5;

    private QuestEngine() {
    }

    /** Assigns a fresh instance of {@code definitionId} to the player, with no issuing-plot context -- see the 3-arg overload's own doc. */
    public static Optional<UUID> assignQuest(ServerPlayer player, Identifier definitionId) {
        return assignQuest(player, definitionId, Optional.empty());
    }

    /**
     * Assigns a fresh instance of {@code definitionId} to the player. Returns its new instanceId, or
     * empty if the definition doesn't exist, they're already at the quest cap, or they already have
     * an in-progress instance of this exact definition.
     *
     * <p>{@code plotId} (added 2026-10-13) is the issuing plot, when one exists -- passed straight
     * through to {@link QuestAssignmentHooks#fire}, now called from inside this method rather than by
     * each individual caller. Real gap found and fixed the same day: the fire call used to live only
     * in {@code QuestTurnIn#acceptBoardOffer}, so {@code api.Myssions#assignQuest} (and anything else
     * calling this method directly, like the debug {@code /myssions givetest} command) silently never
     * fired assignment hooks at all. Calling this from a context with no real plot (the 2-arg overload
     * above) is safe even for a definition that *does* have a hook registered -- in practice, only
     * quest ids accepted through a real plot (the board, or a quest-specific flow like {@code
     * RoadQuestLogic#acceptOffer}) ever have one, so {@code Optional.empty()} here never actually
     * reaches a hook that needs a real plot id.
     */
    public static Optional<UUID> assignQuest(ServerPlayer player, Identifier definitionId, Optional<UUID> plotId) {
        Optional<QuestDefinition> definition = QuestRegistry.get(definitionId);
        if (definition.isEmpty()) {
            return Optional.empty();
        }
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        // Auto-clean orphaned instances whose definition no longer exists at all (e.g. a quest type
        // removed in a later update, like the six on-site kill quests deleted 2026-10-11) -- these
        // used to sit invisible forever (QuestRegistry.get returns empty, so client.QuestLogScreen's
        // own row-building silently drops them from the list) while still permanently occupying a
        // slot against MAX_ACCEPTED_QUESTS, with no way for the player to ever see or abandon them.
        // Real report, 2026-10-12: "I have 3 quests right now but got blocked... citing the limit was
        // 5." Cleaned up here (the one place that actually cares about the real count) rather than
        // added as a one-off manual fix, since any future quest removal can reproduce this the same way.
        List<UUID> orphaned = data.quests().entrySet().stream()
                .filter(e -> e.getValue().status() == QuestStatus.IN_PROGRESS && QuestRegistry.get(e.getValue().definitionId()).isEmpty())
                .map(Map.Entry::getKey)
                .toList();
        for (UUID orphanId : orphaned) {
            data = data.withoutQuest(orphanId);
        }
        if (!orphaned.isEmpty()) {
            player.setData(ModAttachments.QUEST_DATA, data);
        }
        boolean alreadyInProgress = data.quests().values().stream()
                .anyMatch(i -> i.definitionId().equals(definitionId) && i.status() == QuestStatus.IN_PROGRESS);
        if (alreadyInProgress) {
            return Optional.empty(); // Already has this exact definition in progress -- assigning again would just duplicate it.
        }
        // Mayor's Special Assignments (2026-10-13, explicit user spec: "will not be subject to that
        // NPCs 5 quest limit... house an unlimited number of quests") are exempt from this cap
        // entirely -- both as the quest being accepted right now (skip the check outright) and as
        // already-held instances (excluded from the tally below, so holding several doesn't eat into
        // the 5 ordinary slots either).
        if (!definition.get().isSpecialAssignment()) {
            long acceptedCount = data.quests().values().stream()
                    .filter(i -> i.status() == QuestStatus.IN_PROGRESS)
                    .filter(i -> !QuestRegistry.get(i.definitionId()).map(QuestDefinition::isSpecialAssignment).orElse(false))
                    .count();
            if (acceptedCount >= MAX_ACCEPTED_QUESTS) {
                player.sendSystemMessage(Component.literal("You can't accept any more quests right now (max " + MAX_ACCEPTED_QUESTS + " at once)."));
                return Optional.empty();
            }
        }
        QuestInstance instance = QuestInstance.freshlyAssigned(definitionId, player.level().getGameTime());
        player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance));
        player.sendSystemMessage(Component.literal("New quest: " + definition.get().name()));
        grantAcceptanceItems(player, definition.get());
        QuestAssignmentHooks.fire(player, instance.instanceId(), definitionId, plotId.orElse(null));
        return Optional.of(instance.instanceId());
    }

    /** {@link QuestDefinition#acceptanceGrants()} -- a fixed item handed over the instant an instance is created, regardless of which path created it (see this method's own call site's doc). */
    private static void grantAcceptanceItems(ServerPlayer player, QuestDefinition definition) {
        for (QuestReward grant : definition.acceptanceGrants()) {
            if (grant.kind() != QuestReward.Kind.ITEM) {
                continue; // Only ITEM makes sense to hand over at acceptance -- gold/XP belong to completion rewards.
            }
            Item item = BuiltInRegistries.ITEM.getOptional(grant.itemId()).orElse(null);
            if (item == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item, grant.amount());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
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

    /** Scales this instance's eventual Gold/Item/XP rewards -- see {@link QuestInstance#rewardMultiplier()}'s own doc. Must be called before the quest actually completes to have any effect. */
    public static void setRewardMultiplier(ServerPlayer player, UUID instanceId, double multiplier) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance != null) {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withRewardMultiplier(multiplier)));
        }
    }

    /** Wires a region quest up to real Cartographyr-backed knowledge -- see {@link QuestInstance#withRegionKnowledge}'s own doc. Called once, right after {@link #setHint}, from {@code NpcQuests#registerRegionHook}. */
    public static void setRegionKnowledge(ServerPlayer player, UUID instanceId, long knowledgePlaceId, String realLocationName) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance != null) {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withRegionKnowledge(knowledgePlaceId, realLocationName)));
        }
    }

    /** Upgrades the displayed location phrase once the player's real knowledge is good enough -- see {@code QuestWaypointTicker}. No-op if the instance is gone or already shows this exact phrase. */
    public static void upgradeLocationName(ServerPlayer player, UUID instanceId, String upgradedLocationName) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance != null && !instance.locationName().map(upgradedLocationName::equals).orElse(false)) {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withUpgradedLocationName(upgradedLocationName)));
        }
    }

    /** Records the real item (and its cost-sized gold reward) this instance's buy milestone resolved to -- see {@code BuyItemTargets}' own doc for why this lives in a side table, not a new {@link QuestInstance} field, and {@code NpcQuests#aUsefulErrand} for the call site. */
    public static void setBuyItemTarget(ServerPlayer player, UUID instanceId, Identifier itemId, int goldReward) {
        BuyItemTargets.get(player.level().getServer()).set(instanceId, itemId, goldReward);
    }

    /** Marks this instance's completability-ensuring spawn as already done -- see {@code QuestMobSpawnTicker}. */
    public static void markMobsSpawned(ServerPlayer player, UUID instanceId) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance != null) {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withMobsSpawned()));
        }
    }

    /** Binds a freshly-spawned caravan and its full route to an escort instance -- called once, right after assignment. See {@code NpcQuests#registerEscortHook}. */
    public static void assignEscort(ServerPlayer player, UUID instanceId, UUID caravanUuid, List<net.minecraft.core.BlockPos> route) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance != null) {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withEscort(caravanUuid, route)));
        }
    }

    /** Pops the just-reached waypoint off the escort route -- see {@code QuestEscortTicker}. */
    public static void advanceEscortWaypoint(ServerPlayer player, UUID instanceId) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance != null) {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withEscortWaypointReached()));
        }
    }

    /** Re-arms the completability spawn-guarantee for one more cycle -- used when a caravan arrives at an unguarded stake, so {@code QuestMobSpawnTicker}'s existing logic fires again against the updated hint position. */
    public static void resetMobsSpawned(ServerPlayer player, UUID instanceId) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance != null) {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withMobsSpawnedReset()));
        }
    }

    /** Sets a road-connection instance's origin settlement and stake-count thresholds -- see {@code RoadQuestLogic#acceptOffer}. */
    public static void setRoadQuestData(ServerPlayer player, UUID instanceId, long roadOriginPlaceId, int minStakes, int optimalStakeCap, String roadOriginName) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance != null) {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withRoadQuestData(roadOriginPlaceId, minStakes, optimalStakeCap, roadOriginName)));
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
     * every non-optional milestone is now complete -- if so, begins reward pickup (see {@link
     * #beginRewardPickup}). Silently no-ops for an instance the player doesn't have, isn't {@code
     * IN_PROGRESS}, or a milestone id that doesn't belong to its definition.
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
        // A plot-upgrade milestone's real required amount is resolved per-instance (see
        // #plotUpgradeRequiredAmount's own doc) -- milestone.requiredCount() is a meaningless 0
        // placeholder for this kind. Real live report, 2026-10-13: using the static requiredCount()
        // here clamped every plot-upgrade delivery's progress straight back down to 0, so materials
        // were genuinely consumed by tryDeliverProof but the quest could never register as complete
        // (isMilestoneComplete already resolved the real dynamic amount correctly -- only this clamp
        // didn't match it).
        int cap = milestone.isPlotUpgradeMilestone() ? plotUpgradeRequiredAmount(instance, milestone) : milestone.requiredCount();
        int newProgress = Math.min(cap, instance.milestoneProgress().getOrDefault(milestoneId, 0) + amount);
        QuestInstance updated = instance.withProgress(milestoneId, newProgress);

        boolean allRequiredComplete = definition.milestones().stream()
                .filter(m -> !m.optional())
                .allMatch(updated::isMilestoneComplete);

        if (allRequiredComplete) {
            beginRewardPickup(player, definition, updated);
            // See QuestCompletionHooks' own doc -- fired only on this, the genuine quest-driven
            // completion path. NOT fired from completePlotUpgradeQuestsForPlot's own call to
            // beginRewardPickup below -- that path exists specifically because Settlemynts already
            // applied the real upgrade itself and is just telling the quest to catch up; firing this
            // hook there would call straight back into Settlemynts to "apply" an upgrade a second time.
            QuestCompletionHooks.fire(player, instanceId, definition.id());
        } else {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(updated));
        }
    }

    /**
     * Every non-optional milestone just became complete -- explicit user spec, 2026-10-11: turning
     * in a quest should open a reward-collection GUI ({@code quest.QuestRewardMenu}) with a "Take
     * All" button, only actually flagging the quest complete once everything's been collected.
     * Applies XP/Intangible rewards immediately (no physical item to collect, no reason to wait),
     * builds the Gold/Item rewards into real {@link ItemStack}s and parks them on the instance as
     * {@link QuestInstance#pendingRewardItems()}, transitioning to {@link
     * QuestStatus#AWAITING_REWARD_PICKUP}. A quest with no Gold/Item reward at all has nothing to
     * collect, so it skips the GUI step entirely and finalizes immediately. Deliberately does NOT
     * open the reward GUI itself -- this can fire from a passive event (a kill mid-combat via {@code
     * QuestKillListener}), so popping a screen open here would be jarring; the GUI only opens when
     * the player actually clicks "Complete Quest" (see {@link #tryCompleteQuest}).
     */
    private static void beginRewardPickup(ServerPlayer player, QuestDefinition definition, QuestInstance updated) {
        double multiplier = updated.rewardMultiplier();
        applyImmediateRewards(player, definition, multiplier);
        List<ItemStack> rewardItems = buildRewardItemStacks(definition, multiplier);
        addBonusProofGold(rewardItems, definition, updated, multiplier);
        addBuyItemReward(rewardItems, definition, updated, multiplier, player.level().getServer());
        addGatherReward(rewardItems, definition, updated, multiplier);
        addPlainMilestoneBonus(rewardItems, definition, updated, multiplier);
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        if (rewardItems.isEmpty()) {
            finalizeInstance(player, data, definition, updated);
            return;
        }
        QuestInstance awaiting = updated.withPendingRewardItems(rewardItems);
        player.setData(ModAttachments.QUEST_DATA, data.withQuest(awaiting));
        player.sendSystemMessage(Component.literal(definition.name() + " is ready to turn in -- go collect your reward."));
    }

    private static void finalizeInstance(ServerPlayer player, PlayerQuestData data, QuestDefinition definition, QuestInstance instance) {
        QuestInstance completed = instance.withStatus(QuestStatus.COMPLETED);
        PlayerQuestData updatedData = data.withQuest(completed).withIssuerCompletionIncremented(definition.issuerId());
        // Real report, 2026-10-11: a completed quest stayed the Active Quest HUD/waypoint target
        // forever, since nothing ever cleared activeQuestId on completion -- only PlayerQuestData's
        // own doc ("which instance (if any) shows on the Active Quest HUD") implies it should only
        // ever point at something still in progress. Only clear it if THIS instance was the one
        // active, not unconditionally.
        if (updatedData.activeQuestId().map(instance.instanceId()::equals).orElse(false)) {
            updatedData = updatedData.withActiveQuest(null);
        }
        player.setData(ModAttachments.QUEST_DATA, updatedData);
        player.sendSystemMessage(Component.literal("Quest complete: " + definition.name()));
        QuestEndHooks.fire(player, instance.instanceId(), definition.id(), QuestEndHooks.EndReason.COMPLETED);
    }

    /** {@code multiplier} scales XP same as Gold/Item -- see {@link QuestInstance#rewardMultiplier()}'s own doc ("scale the reward with the difficulty/distance from starting point"). */
    private static void applyImmediateRewards(ServerPlayer player, QuestDefinition definition, double multiplier) {
        for (QuestReward reward : definition.rewards()) {
            if (reward.kind() == QuestReward.Kind.XP && LyfeXpBridge.isLoaded()) {
                LyfeXpBridge.grantXp(player, reward.skillId(), scaledAmount(reward.amount(), multiplier));
            }
        }
    }

    /** Builds real, max-stack-respecting, {@code multiplier}-scaled {@link ItemStack}s for every Gold/Item reward -- what ends up in {@code quest.QuestRewardMenu}. */
    private static List<ItemStack> buildRewardItemStacks(QuestDefinition definition, double multiplier) {
        List<ItemStack> stacks = new ArrayList<>();
        for (QuestReward reward : definition.rewards()) {
            switch (reward.kind()) {
                case GOLD -> addStacks(stacks, Items.GOLD_NUGGET, scaledAmount(reward.amount(), multiplier));
                case ITEM -> {
                    Item item = BuiltInRegistries.ITEM.getOptional(reward.itemId()).orElse(null);
                    if (item != null) {
                        addStacks(stacks, item, scaledAmount(reward.amount(), multiplier));
                    }
                }
                default -> {
                    // XP has no physical item -- handled by applyImmediateRewards instead.
                }
            }
        }
        return stacks;
    }

    /**
     * Appends extra Gold Nugget stacks on top of {@code rewardItems} for every optional deliver
     * milestone with a nonzero {@link QuestMilestone#bonusGoldPerProof()} -- "pays extra per returned
     * proof item (up to the max)" (2026-10-11). Reads {@code updated.milestoneProgress()} directly
     * rather than any separate tally -- for a deliver milestone, progress IS "how many proof items
     * were actually turned in," already correct whether that's the full {@code requiredCount} or a
     * genuine partial delivery (see {@code tryDeliverProof}'s own doc).
     */
    private static void addBonusProofGold(List<ItemStack> rewardItems, QuestDefinition definition, QuestInstance updated, double multiplier) {
        for (QuestMilestone milestone : definition.milestones()) {
            if (!milestone.isDeliverMilestone() || milestone.bonusGoldPerProof() <= 0) {
                continue;
            }
            int delivered = updated.milestoneProgress().getOrDefault(milestone.id(), 0);
            if (delivered <= 0) {
                continue;
            }
            addStacks(rewardItems, Items.GOLD_NUGGET, scaledAmount(milestone.bonusGoldPerProof() * delivered, multiplier));
        }
    }

    /**
     * Pays the "Useful Errand" buy-item quest's cost-sized gold reward (explicit user spec,
     * 2026-10-11: "the reward needs to match the cost of actually buying the item, + say, 15%? that
     * way there is an actual reward") -- the amount was computed once at acceptance time from the
     * real vendor's listed price (see {@code NpcQuests#registerUsefulErrandHook}) and stored on
     * {@link BuyItemTargets}, since the target item (and so its price) varies per-instance, not
     * per-definition. Only pays once the buy milestone is actually complete.
     */
    private static void addBuyItemReward(List<ItemStack> rewardItems, QuestDefinition definition, QuestInstance updated, double multiplier, net.minecraft.server.MinecraftServer server) {
        for (QuestMilestone milestone : definition.milestones()) {
            if (!milestone.isBuyMilestone() || !updated.isMilestoneComplete(milestone)) {
                continue;
            }
            BuyItemTargets.get(server).get(updated.instanceId()).ifPresent(target -> {
                if (target.goldReward() > 0) {
                    addStacks(rewardItems, Items.GOLD_NUGGET, scaledAmount(target.goldReward(), multiplier));
                }
            });
        }
    }

    /**
     * Pays a gather milestone's quantity-scaled reward -- {@link QuestMilestone#bonusGoldPerProof()}
     * reused as "gold per unit" (see that class's own doc), multiplied by {@link
     * QuestMilestone#requiredCount()} directly since the target quantity is fixed on the
     * definition itself, unlike the buy-item quest's per-instance-resolved price.
     */
    private static void addGatherReward(List<ItemStack> rewardItems, QuestDefinition definition, QuestInstance updated, double multiplier) {
        for (QuestMilestone milestone : definition.milestones()) {
            if (!milestone.isGatherMilestone() || milestone.bonusGoldPerProof() <= 0 || !updated.isMilestoneComplete(milestone)) {
                continue;
            }
            addStacks(rewardItems, Items.GOLD_NUGGET, scaledAmount(milestone.bonusGoldPerProof() * milestone.requiredCount(), multiplier));
        }
    }

    /**
     * Flat bonus gold for a plain milestone (none of deliver/gather/buy/plot-upgrade, each of which
     * already has its own dedicated bonus-payment method above) that's both optional and complete --
     * added 2026-10-13 for the road-connection quest's three cascading "efficient_road_tier1/2/3"
     * bonuses (explicit user spec: "500 base + 300 more max from the optional milestones... spread
     * across" three efficiency levels, 100 each). {@code requiredCount()} is always 1 for this kind
     * of milestone (a plain done-or-not completion, no partial-delivery concept), so this pays a
     * flat {@code bonusGoldPerProof} once per qualifying tier, same shape as {@link #addGatherReward}'s
     * own "flat amount if complete" logic -- just without restricting to one specific milestone kind.
     */
    private static void addPlainMilestoneBonus(List<ItemStack> rewardItems, QuestDefinition definition, QuestInstance updated, double multiplier) {
        for (QuestMilestone milestone : definition.milestones()) {
            if (milestone.isDeliverMilestone() || milestone.isGatherMilestone() || milestone.isBuyMilestone()
                    || milestone.isPlotUpgradeMilestone() || milestone.bonusGoldPerProof() <= 0 || !updated.isMilestoneComplete(milestone)) {
                continue;
            }
            addStacks(rewardItems, Items.GOLD_NUGGET, scaledAmount(milestone.bonusGoldPerProof() * milestone.requiredCount(), multiplier));
        }
    }

    private static int scaledAmount(int baseAmount, double multiplier) {
        return Math.max(1, (int) Math.round(baseAmount * multiplier));
    }

    private static void addStacks(List<ItemStack> stacks, Item item, int count) {
        int remaining = count;
        while (remaining > 0) {
            int maxStack = new ItemStack(item).getMaxStackSize();
            int take = Math.min(maxStack, remaining);
            stacks.add(new ItemStack(item, take));
            remaining -= take;
        }
    }

    /**
     * Completes (or, for an optional bonus-paying milestone, partially advances) a "deliver"
     * milestone (design doc item 7) if the player holds any of its named proof item. A required
     * milestone (the common case -- our own quest content always grants exactly {@link
     * QuestMilestone#requiredCount()} proof items via {@code QuestKillListener}, so a partial turn-in
     * never makes sense there) still needs the FULL amount in hand, all-or-nothing, same as always.
     * An *optional* milestone with {@link QuestMilestone#bonusGoldPerProof()} set (2026-10-11, "pays
     * extra per returned proof item up to the max") instead accepts whatever the player currently
     * holds, up to {@link QuestMilestone#requiredCount()} -- turning in 2 of a possible 4 is a real,
     * useful partial delivery, not a failure. False if the milestone isn't a deliver milestone, the
     * instance isn't in progress, it's already done, or (required milestones only) there isn't enough
     * proof yet.
     *
     * <p><b>Buy-item milestones</b> ({@link QuestMilestone#isBuyMilestone()}, added 2026-10-11) are
     * a deliver milestone too, but the "proof" is a real item, resolved per-instance via {@link
     * BuyItemTargets} rather than a {@link QuestMilestone#proofId()}-keyed synthetic one -- the
     * player just needs to be holding one real unit of that item, consumed on turn-in exactly like
     * any other proof, however they actually obtained it (buying it from the vendor the flavor text
     * names is a suggestion, never enforced).
     *
     * <p><b>Gather milestones</b> ({@link QuestMilestone#isGatherMilestone()}, added 2026-10-11) are
     * the static sibling of a buy milestone -- also a real {@code ItemStack}, but the target item is
     * fixed per-definition (parsed back out of {@link QuestMilestone#proofId()}, see that class's own
     * doc), not resolved per-instance, so no side table is consulted. Always required, never
     * optional/partial -- same all-or-nothing behavior the generic branch below already gives any
     * non-bonus-paying deliver milestone.
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
        if (milestone == null || !milestone.isDeliverMilestone() || instance.isMilestoneComplete(milestone)) {
            return false;
        }

        Optional<Identifier> buyItemId = milestone.isBuyMilestone()
                ? BuyItemTargets.get(player.level().getServer()).get(instanceId).map(BuyItemTargets.Target::itemId) : Optional.empty();
        if (milestone.isBuyMilestone() && buyItemId.isEmpty()) {
            return false; // Target never resolved (e.g. the eligibility re-check failed at accept time) -- nothing to deliver.
        }
        Optional<Identifier> gatherItemId = milestone.isGatherMilestone()
                ? milestone.proofId().flatMap(s -> Optional.ofNullable(Identifier.tryParse(s))) : Optional.empty();
        if (milestone.isGatherMilestone() && gatherItemId.isEmpty()) {
            return false; // Malformed/missing item id on the definition itself -- nothing to deliver.
        }
        Optional<Identifier> plotUpgradeItemId = milestone.isPlotUpgradeMilestone()
                ? milestone.proofId().map(QuestEngine::plotUpgradeRepresentativeItemId) : Optional.empty();
        if (milestone.isPlotUpgradeMilestone() && plotUpgradeItemId.isEmpty()) {
            return false; // Malformed/missing resource key on the definition itself -- nothing to deliver.
        }
        if (!milestone.isBuyMilestone() && !milestone.isGatherMilestone() && !milestone.isPlotUpgradeMilestone() && milestone.proofId().isEmpty()) {
            return false;
        }

        int requiredCount = milestone.isPlotUpgradeMilestone() ? plotUpgradeRequiredAmount(instance, milestone) : milestone.requiredCount();
        int remaining = requiredCount - instance.milestoneProgress().getOrDefault(milestoneId, 0);
        int held = milestone.isBuyMilestone() ? countRealItem(player, buyItemId.get())
                : milestone.isGatherMilestone() ? countRealItem(player, gatherItemId.get())
                : milestone.isPlotUpgradeMilestone() ? countRealItem(player, plotUpgradeItemId.get())
                : countProof(player, milestone.proofId().get());

        int toDeliver;
        if (milestone.optional() && milestone.bonusGoldPerProof() > 0) {
            toDeliver = Math.min(held, remaining);
            if (toDeliver <= 0) {
                return false;
            }
        } else {
            if (remaining <= 0 || held < remaining) {
                return false;
            }
            toDeliver = remaining;
        }
        if (milestone.isBuyMilestone()) {
            consumeRealItem(player, buyItemId.get(), toDeliver);
        } else if (milestone.isGatherMilestone() || milestone.isPlotUpgradeMilestone()) {
            consumeRealItem(player, (milestone.isGatherMilestone() ? gatherItemId : plotUpgradeItemId).get(), toDeliver);
        } else {
            consumeProof(player, milestone.proofId().get(), toDeliver);
        }
        reportMilestoneProgress(player, instanceId, milestoneId, toDeliver);
        return true;
    }

    /**
     * Read-only pre-check for {@link #tryCompleteQuest} -- see that method's own doc for why this
     * exists. Mirrors {@link #tryDeliverProof}'s own held/required resolution exactly, but never
     * consumes anything; returns one human-readable line (via {@link QuestText#resolve} against the
     * milestone's own description, so a plot-upgrade milestone reads "Bring 12 Logs" rather than a
     * raw item id) per required-and-not-yet-complete deliver milestone the player can't fully afford
     * right now, skipping any optional bonus-paying one.
     */
    private static List<String> findMissingRequiredDeliverables(ServerPlayer player, QuestInstance instance, QuestDefinition definition) {
        List<String> missing = new ArrayList<>();
        for (QuestMilestone milestone : definition.milestones()) {
            if (!milestone.isDeliverMilestone() || instance.isMilestoneComplete(milestone)
                    || (milestone.optional() && milestone.bonusGoldPerProof() > 0)) {
                continue;
            }
            Optional<Identifier> buyItemId = milestone.isBuyMilestone()
                    ? BuyItemTargets.get(player.level().getServer()).get(instance.instanceId()).map(BuyItemTargets.Target::itemId) : Optional.empty();
            Optional<Identifier> gatherItemId = milestone.isGatherMilestone()
                    ? milestone.proofId().flatMap(s -> Optional.ofNullable(Identifier.tryParse(s))) : Optional.empty();
            Optional<Identifier> plotUpgradeItemId = milestone.isPlotUpgradeMilestone()
                    ? milestone.proofId().map(QuestEngine::plotUpgradeRepresentativeItemId) : Optional.empty();
            int requiredCount = milestone.isPlotUpgradeMilestone() ? plotUpgradeRequiredAmount(instance, milestone) : milestone.requiredCount();
            int remaining = requiredCount - instance.milestoneProgress().getOrDefault(milestone.id(), 0);
            if (remaining <= 0) {
                continue;
            }
            int held = milestone.isBuyMilestone() ? buyItemId.map(id -> countRealItem(player, id)).orElse(0)
                    : milestone.isGatherMilestone() ? gatherItemId.map(id -> countRealItem(player, id)).orElse(0)
                    : milestone.isPlotUpgradeMilestone() ? plotUpgradeItemId.map(id -> countRealItem(player, id)).orElse(0)
                    : milestone.proofId().map(id -> countProof(player, id)).orElse(0);
            if (held < remaining) {
                missing.add(QuestText.resolve(milestone.description(), instance));
            }
        }
        return missing;
    }

    /** {@code "wood"} -> Oak Log, {@code "stone"} -> Cobblestone -- the same representative concrete items Blueprynts' own {@code GenericResource} enum uses for these two categories, hardcoded here rather than taking a compile dependency on Blueprynts just for two item ids. Package-private -- also used by {@code QuestTurnIn#isReadyToComplete}. */
    static Identifier plotUpgradeRepresentativeItemId(String resourceKey) {
        return "stone".equals(resourceKey)
                ? Identifier.fromNamespaceAndPath("minecraft", "cobblestone")
                : Identifier.fromNamespaceAndPath("minecraft", "oak_log");
    }

    /** Package-private -- also used by {@code QuestTurnIn#isReadyToComplete}. */
    static int plotUpgradeRequiredAmount(QuestInstance instance, QuestMilestone milestone) {
        QuestText.PlotUpgradeAmounts amounts = QuestText.resolvePlotUpgradeAmounts(instance).orElse(new QuestText.PlotUpgradeAmounts(0, 0));
        return "stone".equals(milestone.proofId().orElse("")) ? amounts.stoneNeeded() : amounts.woodNeeded();
    }

    /**
     * "Complete Quest" on {@code client.QuestCompleteScreen} (2026-10-10/11, explicit user spec: "a
     * sub-menu [with] the option to complete the quest (which checks if all required milestones are
     * complete) and finishes it up"). Two cases:
     * <ul>
     *   <li>Already {@link QuestStatus#AWAITING_REWARD_PICKUP} (milestones were already satisfied by
     *   an earlier call, or a kill/delivery) -- just (re)opens the reward GUI; it's safe to call this
     *   repeatedly, nothing is re-rolled or duplicated.</li>
     *   <li>Still {@link QuestStatus#IN_PROGRESS} -- attempts to deliver proof for every not-yet-
     *   complete deliver milestone (best effort, a milestone without enough proof on hand just stays
     *   incomplete), then opens the reward GUI if that was enough to finish every milestone.</li>
     * </ul>
     * A kill-count milestone that's still short can never be completed this way, only real kills (via
     * {@code QuestKillListener}) advance those. Returns true if the reward GUI was (re)opened or the
     * quest was already fully {@code COMPLETED}; false if there's genuinely still more to do.
     */
    public static boolean tryCompleteQuest(ServerPlayer player, UUID instanceId) {
        QuestInstance instance = player.getData(ModAttachments.QUEST_DATA).quests().get(instanceId);
        if (instance == null) {
            return false;
        }
        if (instance.status() == QuestStatus.AWAITING_REWARD_PICKUP) {
            openRewardMenu(player, instance);
            return true;
        }
        if (instance.status() != QuestStatus.IN_PROGRESS) {
            return instance.status() == QuestStatus.COMPLETED;
        }
        Optional<QuestDefinition> definitionOpt = QuestRegistry.get(instance.definitionId());
        if (definitionOpt.isEmpty()) {
            return false;
        }
        QuestDefinition definition = definitionOpt.get();

        // See QuestViabilityHooks' own doc -- must run before anything below consumes a single item.
        // Explicit user spec, 2026-10-13: if this instance's real-world effect has already happened
        // through some other path (another player's instance for the same plot, say), fail it outright
        // -- a full delete, same as a player-chosen Abandon Quest, not a lingering FAILED entry.
        if (!QuestViabilityHooks.isStillViable(player, instanceId, definition.id())) {
            PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
            player.setData(ModAttachments.QUEST_DATA, data.withoutQuest(instanceId));
            player.sendSystemMessage(Component.literal(
                    "Someone already completed \"" + definition.name() + "\" before you could turn it in."));
            return false;
        }

        // Real live report, 2026-10-12: "it seems to take the materials, but then leaves the quest
        // active and doesn't upgrade the plot." A quest with more than one REQUIRED deliver
        // milestone (e.g. a Plot Upgrade quest's wood + stone) used to consume whatever the player
        // held for whichever milestone they could afford, even when another required milestone
        // couldn't be met at all -- the player lost real materials for a quest that stayed stuck
        // IN_PROGRESS with no explanation of what was still missing. Required milestones are now
        // all-or-nothing as a GROUP: nothing is consumed for any of them unless every one of them can
        // be fully delivered this call. An *optional* bonus-paying deliver milestone (e.g. "return
        // Phantom Membranes for bonus gold") is deliberately excluded from this check -- partial
        // delivery there is a real, intended feature (see tryDeliverProof's own doc), not something
        // that should block or be blocked by the quest's actual completion.
        List<String> missing = findMissingRequiredDeliverables(player, instance, definition);
        if (!missing.isEmpty()) {
            player.sendSystemMessage(Component.literal("Still missing: " + String.join("; ", missing)));
            for (QuestMilestone milestone : definition.milestones()) {
                if (milestone.isDeliverMilestone() && milestone.optional() && milestone.bonusGoldPerProof() > 0
                        && !instance.isMilestoneComplete(milestone)) {
                    tryDeliverProof(player, instanceId, milestone.id());
                }
            }
            return false;
        }

        for (QuestMilestone milestone : definition.milestones()) {
            if (milestone.isDeliverMilestone() && !instance.isMilestoneComplete(milestone)) {
                tryDeliverProof(player, instanceId, milestone.id());
            }
        }
        QuestInstance updated = player.getData(ModAttachments.QUEST_DATA).quests().get(instanceId);
        if (updated == null) {
            return false;
        }
        if (updated.status() == QuestStatus.AWAITING_REWARD_PICKUP) {
            openRewardMenu(player, updated);
            return true;
        }
        return updated.status() == QuestStatus.COMPLETED;
    }

    /**
     * Real live report, 2026-10-12: "Every time I try to turn this in it just takes some portion of
     * the materials from my inventory and doesn't complete the mission" -- confirmed via direct
     * inventory inspection that the player's materials really were gone, yet {@code
     * milestone_progress} stayed at zero. Root cause: the player had used Settlemynts' own real
     * "Upgrade Plot" action (a genuine, separate Tier-upgrade-funding feature -- see {@code
     * construction.PlotTierUpgradeFunding} in that mod) to pay for and complete the actual plot
     * upgrade their quest was themed around. Real materials spent, the plot's real Tier went up, but
     * this quest was never told, since that action has no connection to Myssions at all.
     *
     * <p>Called from Settlemynts' own successful-upgrade path (via {@code bridge
     * .MyssionsQuestBridge#notifyPlotUpgraded}) right after a real Tier upgrade succeeds -- finds
     * every one of the player's IN_PROGRESS instances whose {@link PlotUpgradeQuestTargets} entry
     * matches {@code plotId} and marks their plot-upgrade milestones complete directly (never
     * touching the player's inventory a second time, since the real payment already happened through
     * Settlemynts' own flow), then runs the normal "all required complete" check exactly like {@link
     * #reportMilestoneProgress} does.
     */
    public static void completePlotUpgradeQuestsForPlot(ServerPlayer player, UUID plotId) {
        PlotUpgradeQuestTargets targets = PlotUpgradeQuestTargets.get(player.level().getServer());
        for (QuestInstance instance : List.copyOf(player.getData(ModAttachments.QUEST_DATA).quests().values())) {
            if (instance.status() != QuestStatus.IN_PROGRESS
                    || targets.get(instance.instanceId()).map(t -> !t.plotId().equals(plotId)).orElse(true)) {
                continue;
            }
            Optional<QuestDefinition> definitionOpt = QuestRegistry.get(instance.definitionId());
            if (definitionOpt.isEmpty()) {
                continue;
            }
            QuestDefinition definition = definitionOpt.get();
            QuestInstance updated = instance;
            for (QuestMilestone milestone : definition.milestones()) {
                if (milestone.isPlotUpgradeMilestone()) {
                    updated = updated.withProgress(milestone.id(), plotUpgradeRequiredAmount(updated, milestone));
                }
            }
            boolean allRequiredComplete = definition.milestones().stream()
                    .filter(m -> !m.optional())
                    .allMatch(updated::isMilestoneComplete);
            if (allRequiredComplete) {
                beginRewardPickup(player, definition, updated);
            } else {
                player.setData(ModAttachments.QUEST_DATA, player.getData(ModAttachments.QUEST_DATA).withQuest(updated));
            }
        }
    }

    private static void openRewardMenu(ServerPlayer player, QuestInstance instance) {
        String title = QuestRegistry.get(instance.definitionId()).map(QuestDefinition::name).orElse("Quest Reward");
        QuestRewardMenu.open(player, instance.instanceId(), instance.pendingRewardItems(), title);
    }

    /**
     * "Complete Quest" button inside {@code quest.QuestRewardMenu} itself -- called once the
     * container is confirmed empty (see that class's own doc). Finalizes the instance: marks it
     * {@code COMPLETED}, increments the per-issuer count, and sends the real completion message.
     * False (a no-op) if the instance isn't actually awaiting pickup, which the menu itself should
     * never allow to happen, but defended here anyway since this crosses a UI boundary.
     */
    public static boolean finalizeRewardPickup(ServerPlayer player, UUID instanceId) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance == null || instance.status() != QuestStatus.AWAITING_REWARD_PICKUP) {
            return false;
        }
        Optional<QuestDefinition> definitionOpt = QuestRegistry.get(instance.definitionId());
        if (definitionOpt.isEmpty()) {
            return false;
        }
        finalizeInstance(player, data, definitionOpt.get(), instance);
        return true;
    }

    /** Persists whatever reward items are still sitting in the GUI when it's closed without finishing -- see {@code quest.QuestRewardMenu#removed}. Never mutates a non-awaiting instance (e.g. one that was juuust finalized the same tick the screen closed). */
    public static void syncPendingRewardItems(ServerPlayer player, UUID instanceId, List<ItemStack> remaining) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance == null || instance.status() != QuestStatus.AWAITING_REWARD_PICKUP) {
            return;
        }
        player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withPendingRewardItems(remaining)));
    }

    /** Read-only check used by {@code QuestTurnIn} to tell "waiting on player" apart from "ready to complete" without consuming anything. */
    public static boolean hasEnoughProof(ServerPlayer player, String proofId, int needed) {
        return countProof(player, proofId) >= needed;
    }

    /** Buy-milestone equivalent of {@link #hasEnoughProof} -- real inventory count of a real item, not a synthetic proof. */
    public static boolean hasEnoughRealItem(ServerPlayer player, Identifier itemId, int needed) {
        return countRealItem(player, itemId) >= needed;
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

    private static int countRealItem(ServerPlayer player, Identifier itemId) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeRealItem(ServerPlayer player, Identifier itemId, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) {
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
        PlayerQuestData updatedData = data.withQuest(instance.withStatus(QuestStatus.FAILED));
        // Same reasoning as finalizeInstance's own completion fix -- a failed quest shouldn't keep
        // squatting on the Active Quest HUD/waypoint either.
        if (updatedData.activeQuestId().map(instanceId::equals).orElse(false)) {
            updatedData = updatedData.withActiveQuest(null);
        }
        player.setData(ModAttachments.QUEST_DATA, updatedData);
        QuestRegistry.get(instance.definitionId()).ifPresent(def ->
                player.sendSystemMessage(Component.literal("Quest failed: " + def.name())));
        QuestEndHooks.fire(player, instanceId, instance.definitionId(), QuestEndHooks.EndReason.FAILED);
    }

    /**
     * Player-initiated give-up, from {@code client.QuestLogScreen}'s new "Abandon Quest" button.
     * Real correction, 2026-10-11, right after the first version shipped (which reused {@link
     * QuestStatus#FAILED}, landing it in the still-visible Failed Quests list): "Abandoning a quest
     * should fully abandon it. Meaning delete it from the player J menu, so they can replace it with
     * a different quest. This also is a way to remove bugged quests without needing special server
     * commands." A genuine full delete via {@link PlayerQuestData#withoutQuest} -- the one
     * deliberate exception to that record's own "never removed" instance-retention rule -- not a
     * third status alongside FAILED/COMPLETED. {@code failQuest} (deadline expiry) is untouched and
     * still uses FAILED/Retry; this is a different, player-chosen "pretend this never happened" path.
     */
    public static void abandonQuest(ServerPlayer player, UUID instanceId) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance == null || instance.status() != QuestStatus.IN_PROGRESS) {
            return;
        }
        // Fired BEFORE the delete below -- a hook (e.g. the escort caravan cleanup registered by
        // QuestHooks#registerEscortHook, generalized 2026-10-13 out of what used to be inline here)
        // may need to look this instance back up via QuestEngine#getInstance, which would find
        // nothing once withoutQuest has actually removed it.
        QuestEndHooks.fire(player, instanceId, instance.definitionId(), QuestEndHooks.EndReason.ABANDONED);
        player.setData(ModAttachments.QUEST_DATA, data.withoutQuest(instanceId));
        QuestRegistry.get(instance.definitionId()).ifPresent(def ->
                player.sendSystemMessage(Component.literal("Quest abandoned: " + def.name())));
    }
}
