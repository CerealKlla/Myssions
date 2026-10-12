package com.github.cerealklla.myssions.quest;

import java.util.ArrayList;
import java.util.List;
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
    public static void setRegionKnowledge(ServerPlayer player, UUID instanceId, long knowledgePlaceId, String directionPhrase, String realLocationName) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance != null) {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withRegionKnowledge(knowledgePlaceId, directionPhrase, realLocationName)));
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
    public static void setRoadQuestData(ServerPlayer player, UUID instanceId, long roadOriginPlaceId, int minStakes, int optimalStakeCap) {
        PlayerQuestData data = player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        if (instance != null) {
            player.setData(ModAttachments.QUEST_DATA, data.withQuest(instance.withRoadQuestData(roadOriginPlaceId, minStakes, optimalStakeCap)));
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
        int newProgress = Math.min(milestone.requiredCount(), instance.milestoneProgress().getOrDefault(milestoneId, 0) + amount);
        QuestInstance updated = instance.withProgress(milestoneId, newProgress);

        boolean allRequiredComplete = definition.milestones().stream()
                .filter(m -> !m.optional())
                .allMatch(updated::isMilestoneComplete);

        if (allRequiredComplete) {
            beginRewardPickup(player, definition, updated);
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
    }

    /** {@code multiplier} scales XP same as Gold/Item -- see {@link QuestInstance#rewardMultiplier()}'s own doc ("scale the reward with the difficulty/distance from starting point"). */
    private static void applyImmediateRewards(ServerPlayer player, QuestDefinition definition, double multiplier) {
        for (QuestReward reward : definition.rewards()) {
            if (reward.kind() == QuestReward.Kind.XP && LyfeXpBridge.isLoaded()) {
                LyfeXpBridge.grantXp(player, reward.skillId(), scaledAmount(reward.amount(), multiplier));
            }
            // INTANGIBLE: no mechanical effect yet (see QuestReward's own doc), nothing to apply.
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
                    // XP/INTANGIBLE have no physical item -- handled by applyImmediateRewards instead.
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

    /** {@code "wood"} -> Oak Log, {@code "stone"} -> Cobblestone -- the same representative concrete items Blueprynts' own {@code GenericResource} enum uses for these two categories, hardcoded here rather than taking a compile dependency on Blueprynts just for two item ids. */
    private static Identifier plotUpgradeRepresentativeItemId(String resourceKey) {
        return "stone".equals(resourceKey)
                ? Identifier.fromNamespaceAndPath("minecraft", "cobblestone")
                : Identifier.fromNamespaceAndPath("minecraft", "oak_log");
    }

    private static int plotUpgradeRequiredAmount(QuestInstance instance, QuestMilestone milestone) {
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
        for (QuestMilestone milestone : definitionOpt.get().milestones()) {
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
        // An escort instance's caravan is a real live entity with nothing else watching it once the
        // quest stops being IN_PROGRESS (QuestEscortTicker only ever acts on in-progress instances) --
        // same cleanup QuestEscortTicker#completeEscort already does on a normal finish.
        if (instance.escort().entityUuid().isPresent() && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            net.minecraft.world.entity.Entity entity = level.getEntity(instance.escort().entityUuid().get());
            if (entity instanceof com.github.cerealklla.myssions.quest.entity.CaravanEntity caravan) {
                caravan.discard();
            }
        }
        player.setData(ModAttachments.QUEST_DATA, data.withoutQuest(instanceId));
        QuestRegistry.get(instance.definitionId()).ifPresent(def ->
                player.sendSystemMessage(Component.literal("Quest abandoned: " + def.name())));
    }
}
