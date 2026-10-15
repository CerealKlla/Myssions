package com.github.cerealklla.myssions.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import com.github.cerealklla.myssions.MyssionsMod;

/**
 * Shared, reusable {@code QuestAssignmentHooks} implementations for the three taxonomy shapes that
 * don't vary by profession -- real-location/distance-scaled kill, buy-item-from-local-shop, and
 * escort/caravan-raid. Extracted 2026-10-11 out of {@code NpcQuests} (which originally only had
 * Lumberjack/Farmer/Mayor/Traveling Merchant content, each with its own copy) once the quest-content
 * expansion needed the same three hooks from seven more per-profession classes
 * (`GrocerQuests`/`BlacksmithQuests`/etc.) -- a plain shared library, not a mechanic of its own.
 *
 * <p>Package-private on purpose: every call site is another class in this same {@code quest}
 * package, and this has no reason to be part of any other mod's public surface.
 */
final class QuestHooks {

    private QuestHooks() {
    }

    /**
     * High-level quest-authoring helpers (added 2026-10-11 for the 10-quests-per-profession content
     * expansion) -- each registers a real {@link QuestDefinition} plus whatever {@link
     * QuestAssignmentHooks}/{@link QuestEligibility} entries that quest shape needs, in one call, so
     * the per-profession classes (`GrocerQuests`, `BlacksmithQuests`, etc.) read as a plain roster
     * instead of repeating the same wiring every method already does in {@code NpcQuests}.
     */

    /** A "bring me N of a fixed item" quest (the taxonomy's location-agnostic/quantity-scaled shape) -- one required {@link QuestMilestone#gather}. */
    static Identifier registerGatherQuest(Identifier questId, String name, String description, String zoneType,
                                           String milestoneText, Identifier itemId, int count, int goldPerUnit,
                                           List<QuestReward> rewards) {
        QuestRegistry.register(new QuestDefinition(questId, name, description, zoneType,
                List.of(QuestMilestone.gather("gather_items", milestoneText, itemId, count, goldPerUnit)),
                rewards, QuestTimeLimit.NONE));
        return questId;
    }

    /**
     * A real-location/distance-scaled kill quest (plus an optional bonus-paying proof delivery),
     * reusing {@link #registerRegionHook}. {@code killMilestoneText} may use the {@code {location}}
     * token (resolved per-instance once accepted, via {@link QuestText#resolve}) -- {@code
     * description} must NOT: a board offer is previewed via {@code QuestTurnIn#getMenu} before any
     * instance exists to resolve the token against, so it would render as the literal, unresolved
     * string "{location}" right in the Accept Quest screen (real user report, 2026-10-12, "Something
     * in the Dark"). Keep location color in the milestone text only.
     */
    static Identifier registerRegionKillQuest(Identifier questId, String name, String description, String zoneType,
                                                String killMilestoneText, Identifier mobType, int count,
                                                String proofId, String proofName, String proofFlavor,
                                                boolean nightOnly, int bonusGoldPerProof, List<QuestReward> rewards) {
        ProofItemCatalog.register(proofId, proofName, proofFlavor);
        QuestRegistry.register(new QuestDefinition(questId, name, description, zoneType,
                List.of(
                        QuestMilestone.kill("kill_target", killMilestoneText, mobType, count, proofId, nightOnly),
                        QuestMilestone.optionalDeliverWithBonus("deliver_proof", "Deliver " + proofName + " for bonus gold", proofId, count, bonusGoldPerProof)
                ),
                rewards, QuestTimeLimit.NONE));
        registerRegionHook(questId);
        return questId;
    }

    /** A "buy an item from a local shop" quest (the target item resolved from real shop listings at acceptance time), reusing {@link #registerBuyItemHook}. */
    static Identifier registerBuyItemQuest(Identifier questId, String name, String description, String zoneType, List<QuestReward> rewards) {
        QuestRegistry.register(new QuestDefinition(questId, name, description, zoneType,
                List.of(QuestMilestone.buyItem("deliver_item", "Bring me {location}", 1)),
                rewards, QuestTimeLimit.NONE));
        registerBuyItemHook(questId);
        registerBuyItemEligibility(questId);
        return questId;
    }

    /**
     * A "help upgrade this plot" quest (added 2026-10-12, explicit user spec) -- any plot NPC can
     * offer it; the target is always the issuing plot's own real current Wood/Stone shortfall for
     * raising its Tier cap by one (not a Blueprint's building cost -- see {@code api.Settlemynts
     * #getPlotUpgradeShortfall}'s own doc for that distinction), resolved fresh at acceptance time
     * via {@link #registerPlotUpgradeHook}. {@code rewards} should include both a {@code
     * QuestReward#gold} and a {@code QuestReward#xp("mayor", ...)} entry, per the user's own spec
     * ("The reward will include gold, but also a decent amount of Mayor xp") -- both are scaled by
     * the real upgrade's own size via {@link QuestEngine#setRewardMultiplier}, same mechanism the
     * region-kill quests already use for distance scaling.
     *
     * <p><b>Standing instruction</b> (added to design-document.md the same day): every new plot
     * profession/Zone Type added to this suite from now on should get one of these registered
     * alongside its other quests -- see that doc's own "Plot Upgrade quest" section for the full
     * checklist, so this isn't silently forgotten for the next new plot type.
     */
    static Identifier registerPlotUpgradeQuest(Identifier questId, String name, String description, String zoneType, List<QuestReward> rewards) {
        QuestRegistry.register(new QuestDefinition(questId, name, description, zoneType,
                List.of(
                        QuestMilestone.plotUpgradeResource("deliver_wood", "Bring {wood} Logs", "wood"),
                        QuestMilestone.plotUpgradeResource("deliver_stone", "Bring {stone} Stone", "stone")
                ),
                rewards, QuestTimeLimit.NONE));
        registerPlotUpgradeHook(questId);
        registerPlotUpgradeEligibility(questId);
        return questId;
    }

    /**
     * Resolves the issuing plot's real current Tier-upgrade shortfall at acceptance time and packs
     * it onto the instance's own hint (see {@code QuestText}'s own doc), plus sizes {@link
     * QuestInstance#rewardMultiplier()} by how big the upgrade itself is ({@code max(1, nextTier -
     * 1)}, the same multiplier formula {@code construction.PlotTierUpgradeCost} itself uses). Must
     * accompany {@link #registerPlotUpgradeEligibility}, same pairing convention as every other
     * dynamically-resolved quest shape in this file.
     */
    static void registerPlotUpgradeHook(Identifier questId) {
        QuestAssignmentHooks.register(questId, (player, instanceId, assignedId, plotId) -> {
            if (!(player.level() instanceof ServerLevel level)
                    || !com.github.cerealklla.myssions.bridge.SettlemyntsPlotUpgradeBridge.isLoaded()) {
                return;
            }
            com.github.cerealklla.myssions.bridge.SettlemyntsPlotUpgradeBridge.resolveShortfall(level, plotId).ifPresent(shortfall -> {
                if (!shortfall.anyShortfall()) {
                    player.sendSystemMessage(Component.literal(
                            "Actually, never mind -- this plot already has everything it needs for its next upgrade."));
                    return;
                }
                QuestEngine.setHint(player, instanceId, player.blockPosition(), level.dimension().identifier().toString(),
                        Optional.of(QuestText.packPlotUpgradeHint(shortfall.woodNeeded(), shortfall.stoneNeeded())));
                QuestEngine.setRewardMultiplier(player, instanceId, Math.max(1, shortfall.nextTier() - 1));
                // Lets QuestEngine#completePlotUpgradeQuestsForPlot find this instance later if the
                // player pays for the real upgrade directly through Settlemynts instead of this
                // quest's own turn-in -- see that method's own doc for the real live report. Also
                // records which specific Tier this instance's own shortfall was sized for (see
                // PlotUpgradeQuestTargets' own doc, "targetTier added 2026-10-13") -- needed so two
                // players' instances for the same plot can't both apply a real upgrade.
                PlotUpgradeQuestTargets.get(level.getServer()).set(instanceId, plotId, shortfall.nextTier());
                player.sendSystemMessage(Component.literal(
                        "This plot needs more building materials to grow -- bring me what's missing and I'll make it worth your while."));
            });
        });
        // Explicit user spec, 2026-10-13: a quest instance whose real-world upgrade has already
        // happened through some other path (another player's instance for the same plot, the real
        // "Upgrade Plot" button) should fail outright before this quest's own delivery ever consumes
        // anything -- see QuestViabilityHooks' own doc. Checked the same read-only way the acceptance
        // hook above resolves a shortfall in the first place: the plot's real current Tier is still
        // one below this instance's own recorded targetTier.
        QuestViabilityHooks.register(questId, (player, instanceId, viabilityId) -> {
            if (!(player.level() instanceof ServerLevel level)
                    || !com.github.cerealklla.myssions.bridge.SettlemyntsPlotUpgradeBridge.isLoaded()) {
                return true;
            }
            return PlotUpgradeQuestTargets.get(level.getServer()).get(instanceId)
                    .map(target -> com.github.cerealklla.myssions.bridge.SettlemyntsPlotUpgradeBridge.resolveShortfall(level, target.plotId())
                            .map(s -> s.nextTier() == target.targetTier())
                            .orElse(false))
                    .orElse(true);
        });
        // Real live report, 2026-10-13: the quest completed and paid its reward, but the plot's real
        // Tier never went up -- the delivery milestone above only ever consumes real wood/stone out
        // of the player's own inventory, it never touches Settlemynts' Construction Box the real
        // "Upgrade Plot" button funds from. This is the other half: once the quest's own delivery
        // satisfies the shortfall, tell Settlemynts to actually apply the Tier raise (see
        // bridge.SettlemyntsPlotUpgradeBridge#applyUpgrade's own doc for the box-draining accounting
        // this still needs to do). By the time this fires, QuestViabilityHooks above has already
        // guaranteed the plot is still at the right Tier for this instance -- applyUpgrade "failing"
        // here would mean something changed Settlemynts-side within this same single-threaded call,
        // not a real race; nothing user-facing to say about that.
        QuestCompletionHooks.register(questId, (player, instanceId, completedId) -> {
            if (!(player.level() instanceof ServerLevel level)
                    || !com.github.cerealklla.myssions.bridge.SettlemyntsPlotUpgradeBridge.isLoaded()) {
                return;
            }
            PlotUpgradeQuestTargets.get(level.getServer()).get(instanceId).ifPresent(target ->
                    com.github.cerealklla.myssions.bridge.SettlemyntsPlotUpgradeBridge.applyUpgrade(
                            level, target.plotId(), target.targetTier()));
        });
    }

    /** Must accompany every {@link #registerPlotUpgradeHook} call -- never offer the quest when the plot has no real shortfall to fill. */
    static void registerPlotUpgradeEligibility(Identifier questId) {
        QuestEligibility.register(questId, (level, plotId) ->
                com.github.cerealklla.myssions.bridge.SettlemyntsPlotUpgradeBridge.isLoaded()
                        && com.github.cerealklla.myssions.bridge.SettlemyntsPlotUpgradeBridge.resolveShortfall(level, plotId)
                                .map(com.github.cerealklla.myssions.bridge.SettlemyntsPlotUpgradeBridge.Shortfall::anyShortfall)
                                .orElse(false));
    }

    /** An escort/caravan-raid quest, reusing {@link #registerEscortHook}. */
    static Identifier registerEscortQuest(Identifier questId, String name, String description, String zoneType,
                                            int waypointCount, int maxBanditEncounters, List<QuestReward> rewards) {
        QuestRegistry.register(new QuestDefinition(questId, name, description, zoneType,
                List.of(
                        new QuestMilestone("fend_off_bandits", "Fend off any Bandits that attack the caravan",
                                true, maxBanditEncounters, Optional.of(Identifier.fromNamespaceAndPath("minecraft", "pillager")),
                                Optional.empty(), false, false, false, 0, false, false, false, false, Optional.empty()),
                        QuestMilestone.escort("escort_caravan", "Escort the caravan safely to the end of the road")
                ),
                rewards, QuestTimeLimit.NONE));
        registerEscortHook(questId, waypointCount);
        return questId;
    }

    /**
     * Design doc item 4's own worked example, verbatim: "a player takes a quest to go find a troll
     * within a cave... completing some objective within the cave completes a milestone, then the
     * player has to escape." Two milestones -- {@link QuestMilestone#defeatGuardian}/{@link
     * QuestMilestone#escape} -- backed entirely by {@code bridge.TinyWyrldsEntranceBridge}'s own
     * event listeners, not any poll/ticker here. {@code recipeName} must already exist as a saved
     * {@code tinywyrlds.wyrldprint.recipe.WyrldRecipe} (created via {@code /tinywyrlds recipe
     * create/addloot/addenemy/...}) -- this mod never authors one itself.
     */
    static Identifier registerEntranceQuest(Identifier questId, String name, String description, String zoneType,
                                              String guardianMilestoneText, String escapeMilestoneText,
                                              String recipeName, String introMessage, List<QuestReward> rewards) {
        QuestRegistry.register(new QuestDefinition(questId, name, description, zoneType,
                List.of(
                        QuestMilestone.defeatGuardian("defeat_guardian", guardianMilestoneText),
                        QuestMilestone.escape("escape_dungeon", escapeMilestoneText)
                ),
                rewards, QuestTimeLimit.NONE));
        registerEntranceHook(questId, recipeName, introMessage);
        return questId;
    }

    /**
     * Spawns a real {@code WyrldEntranceEntity} "out past the player" (same honest simplification
     * {@link #registerTalkNpcHook} already uses -- the Entrance itself IS the destination marker,
     * unlike a talk-NPC quest's own named place) -- a no-op if TinyWyrlds isn't loaded.
     */
    static void registerEntranceHook(Identifier questId, String recipeName, String introMessage) {
        QuestAssignmentHooks.register(questId, (player, instanceId, assignedId, plotId) -> {
            if (!(player.level() instanceof ServerLevel level)
                    || !com.github.cerealklla.myssions.bridge.TinyWyrldsEntranceBridge.isLoaded()) {
                return;
            }
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double distance = 80 + player.getRandom().nextDouble() * 120;
            BlockPos roughSpot = player.blockPosition().offset(
                    (int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, roughSpot.getX(), roughSpot.getZ());
            BlockPos truePos = new BlockPos(roughSpot.getX(), groundY + 1, roughSpot.getZ());

            QuestEngine.setHint(player, instanceId, truePos, level.dimension().identifier().toString());
            com.github.cerealklla.myssions.bridge.TinyWyrldsEntranceBridge.spawnEntranceForQuest(
                    level, truePos, instanceId, recipeName, player.getUUID());

            if (com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.isLoaded()) {
                long placeId = QuestWaypointTicker.placeIdFor(instanceId);
                com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.grantKnowledge(player, placeId, true, true, false);
            }
            player.sendSystemMessage(Component.literal(introMessage));
        });
    }

    /** A "go talk to a named NPC" quest, reusing {@link #registerTalkNpcHook}. */
    static Identifier registerTalkQuest(Identifier questId, String name, String description, String zoneType,
                                          String milestoneText, String npcName, boolean hidesAtNight,
                                          String introMessage, List<QuestReward> rewards) {
        QuestRegistry.register(new QuestDefinition(questId, name, description, zoneType,
                List.of(QuestMilestone.talk("talk_to_npc", milestoneText)),
                rewards, QuestTimeLimit.NONE));
        registerTalkNpcHook(questId, npcName, hidesAtNight, introMessage);
        return questId;
    }

    // "1 mile from the NPC quest giver" -- explicit user spec, 2026-10-11, matching Cartographyr's
    // own real-world-feet-to-block conversion (1 block ~= 1 meter, 1 mile ~= 1609 blocks).
    private static final double REGION_QUEST_MAX_RADIUS_BLOCKS = 1609.0;
    private static final double REGION_REWARD_MIN_MULTIPLIER = 0.75;
    private static final double REGION_REWARD_MAX_MULTIPLIER = 2.0;

    /**
     * Registers a real, randomized-distance Cartographyr Region/Settlement target for a quest whose
     * threat is "out there somewhere," not at the issuing NPC's own plot -- see {@code NpcQuests}'
     * original doc (2026-10-11) for the full design reasoning. Picks a real nearby Region/Settlement
     * via {@code bridge.CartographyrRegionBridge#pickWeightedNearby} (weighted toward closer
     * candidates, capped at {@link #REGION_QUEST_MAX_RADIUS_BLOCKS}), substitutes its real name into
     * the quest's own {@code {location}}-tokened milestone text, and scales {@link
     * QuestInstance#rewardMultiplier()} linearly by how far the chosen target actually landed. A
     * no-op if Settlemynts/Cartographyr aren't loaded, the plot can't be resolved, or nothing
     * qualifies within range.
     */
    static void registerRegionHook(Identifier questId) {
        QuestAssignmentHooks.register(questId, (player, instanceId, assignedId, plotId) -> {
            if (!(player.level() instanceof ServerLevel level)
                    || !com.github.cerealklla.myssions.bridge.SettlemyntsPlotLocationBridge.isLoaded()
                    || !com.github.cerealklla.myssions.bridge.CartographyrRegionBridge.isLoaded()) {
                return;
            }
            com.github.cerealklla.myssions.bridge.SettlemyntsPlotLocationBridge.resolve(level, plotId).ifPresent(origin -> {
                ServerLevel originLevel = level.getServer().getLevel(origin.dimension());
                if (originLevel == null) {
                    return;
                }
                com.github.cerealklla.myssions.bridge.CartographyrRegionBridge.pickWeightedNearby(
                        originLevel, origin.pos(), REGION_QUEST_MAX_RADIUS_BLOCKS, player.getRandom()
                ).ifPresent(target -> {
                    String settlementName = origin.settlementName().isBlank() ? "here" : origin.settlementName();

                    QuestEngine.setHint(player, instanceId, target.pos(), target.dimension().identifier().toString(), Optional.empty());
                    QuestEngine.setRegionKnowledge(player, instanceId, target.placeId(), target.displayName());

                    double fraction = Math.max(0.0, Math.min(1.0, target.distanceBlocks() / REGION_QUEST_MAX_RADIUS_BLOCKS));
                    double multiplier = REGION_REWARD_MIN_MULTIPLIER + (REGION_REWARD_MAX_MULTIPLIER - REGION_REWARD_MIN_MULTIPLIER) * fraction;
                    QuestEngine.setRewardMultiplier(player, instanceId, multiplier);

                    // Initial hint, anchored to where it was given from (the issuing plot) -- direction
                    // and distance known immediately (matches the old static "to the <dir> of <X>"
                    // phrase's always-visible-from-the-start behavior), name withheld until the
                    // player's own knowledge crosses the full-reveal bar (QuestWaypointTicker/
                    // Lyfe#resolveLocationText decide that live now, not a value cached here).
                    com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.grantPartialLocation(player, target.placeId(),
                            origin.pos(), origin.dimension().identifier().toString(), settlementName, false, true, true);

                    player.sendSystemMessage(Component.literal("You get a rough idea where to look -- check your map."));
                });
            });
        });
    }

    /** Plain 8-point compass direction from {@code origin} to {@code target}, Minecraft's own coordinate convention (-Z is north, +X is east). */
    static String directionWord(BlockPos origin, BlockPos target) {
        String[] points = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
        double dx = target.getX() - origin.getX();
        double dz = target.getZ() - origin.getZ();
        double bearingDegrees = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0;
        int index = ((int) Math.round(bearingDegrees / 45.0)) % 8;
        return points[index];
    }

    /** Reward = the item's real vendor price + 15%, rounded, floored at 1 gold so a free-listed item still pays something. */
    private static final double BUY_ITEM_REWARD_MARKUP = 1.15;

    /**
     * Generic "buy an item from a local shop" assignment hook -- the real target item is resolved
     * from the issuing plot's own settlement shop listings at acceptance time, substituted into the
     * {@code {location}}-tokened milestone text (see {@code NpcQuests#aUsefulErrand}'s own doc for
     * why that token, not a dedicated field). Every call site must also call {@link
     * #registerBuyItemEligibility} so the quest is never offered without a real settlement to
     * resolve a name from.
     */
    static void registerBuyItemHook(Identifier questId) {
        QuestAssignmentHooks.register(questId, (player, instanceId, assignedId, plotId) -> {
            if (!(player.level() instanceof ServerLevel level)
                    || !com.github.cerealklla.myssions.bridge.SettlemyntsShopBridge.isLoaded()) {
                return;
            }
            List<com.github.cerealklla.myssions.bridge.SettlemyntsShopBridge.ResolvedListing> options =
                    com.github.cerealklla.myssions.bridge.SettlemyntsShopBridge.resolvableSellableItems(level, plotId);
            if (options.isEmpty()) {
                player.sendSystemMessage(Component.literal(
                        "Actually, never mind -- nobody in town has anything I need right now."));
                return;
            }
            com.github.cerealklla.myssions.bridge.SettlemyntsShopBridge.ResolvedListing chosen =
                    options.get(player.getRandom().nextInt(options.size()));
            Identifier itemId = chosen.itemId();
            int goldReward = Math.max(1, (int) Math.round(chosen.pricePerUnit() * BUY_ITEM_REWARD_MARKUP));
            QuestEngine.setBuyItemTarget(player, instanceId, itemId, goldReward);
            String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId)
                    .map(ref -> new ItemStack(ref.value()).getHoverName().getString())
                    .orElse("something from the shops");
            QuestEngine.setHint(player, instanceId, player.blockPosition(), level.dimension().identifier().toString(),
                    Optional.of(QuestText.packBuyItemHint(itemName, goldReward)));
            player.sendSystemMessage(Component.literal(
                    "What I could really use is " + itemName + " -- someone in town sells it."));
        });
    }

    /** Must accompany every {@link #registerBuyItemHook} call -- never offer the quest without a resolvable real item. */
    static void registerBuyItemEligibility(Identifier questId) {
        QuestEligibility.register(questId, (level, plotId) ->
                com.github.cerealklla.myssions.bridge.SettlemyntsShopBridge.isLoaded()
                        && !com.github.cerealklla.myssions.bridge.SettlemyntsShopBridge.resolvableSellableItems(level, plotId).isEmpty());
    }

    private static final double ESCORT_WAYPOINT_SPACING_BLOCKS = 45.0;

    /**
     * Spawns a {@code quest.entity.CaravanEntity} at the issuing plot and gives it a straight
     * {@code waypointCount}-point road in one random direction -- {@code quest.QuestEscortTicker}
     * owns everything from here on. A no-op if Settlemynts isn't loaded or the plot can't be
     * resolved. See {@code NpcQuests#merchantCaravanEscort}'s original doc for the full design.
     */
    static void registerEscortHook(Identifier questId, int waypointCount) {
        QuestAssignmentHooks.register(questId, (player, instanceId, assignedId, plotId) -> {
            if (!(player.level() instanceof ServerLevel level)
                    || !com.github.cerealklla.myssions.bridge.SettlemyntsPlotLocationBridge.isLoaded()) {
                return;
            }
            com.github.cerealklla.myssions.bridge.SettlemyntsPlotLocationBridge.resolve(level, plotId).ifPresent(loc -> {
                ServerLevel roadLevel = level.getServer().getLevel(loc.dimension());
                if (roadLevel == null) {
                    return;
                }
                double angle = player.getRandom().nextDouble() * Math.PI * 2;
                List<BlockPos> route = new ArrayList<>();
                for (int i = 1; i <= waypointCount; i++) {
                    double distance = ESCORT_WAYPOINT_SPACING_BLOCKS * i;
                    int x = loc.pos().getX() + (int) Math.round(Math.cos(angle) * distance);
                    int z = loc.pos().getZ() + (int) Math.round(Math.sin(angle) * distance);
                    int groundY = roadLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    route.add(new BlockPos(x, groundY, z));
                }

                com.github.cerealklla.myssions.quest.entity.CaravanEntity caravan =
                        new com.github.cerealklla.myssions.quest.entity.CaravanEntity(
                                com.github.cerealklla.myssions.registration.ModEntities.CARAVAN.get(), roadLevel);
                caravan.setPos(loc.pos().getX() + 0.5, loc.pos().getY(), loc.pos().getZ() + 0.5);
                caravan.configure(player.getUUID(), instanceId);
                caravan.setTargetPos(route.get(0));
                roadLevel.addFreshEntity(caravan);

                QuestEngine.assignEscort(player, instanceId, caravan.getUUID(), route);
                QuestEngine.setHint(player, instanceId, loc.pos(), loc.dimension().identifier().toString());
                player.sendSystemMessage(Component.literal(
                        "The caravan sets out -- stay close, or bandits may see their chance."));
            });
        });
        // Generalized 2026-10-13 out of a special case that used to live inline in
        // QuestEngine#abandonQuest -- a live CaravanEntity has nothing else watching it once its
        // instance stops being IN_PROGRESS. QuestEscortTicker#completeEscort already discards the
        // caravan directly on a real finish, so this only has real work left to do for
        // FAILED/ABANDONED -- but checking isAlive() first makes it a harmless no-op for COMPLETED
        // too rather than needing to filter by reason. This also fixes a real pre-existing gap:
        // failQuest (deadline expiry) never cleaned up an escort's caravan at all before this hook
        // existed.
        QuestEndHooks.register(questId, (player, instanceId, endedId, reason) -> {
            if (player.level() instanceof ServerLevel level) {
                QuestEngine.getInstance(player, instanceId).ifPresent(instance ->
                        instance.escort().entityUuid().ifPresent(caravanId -> {
                            if (level.getEntity(caravanId) instanceof com.github.cerealklla.myssions.quest.entity.CaravanEntity caravan
                                    && caravan.isAlive()) {
                                caravan.discard();
                            }
                        }));
            }
        });
    }

    /**
     * Generic "go talk to a named NPC" assignment hook (design doc items 8/9) -- spawns a {@code
     * QuestNpcEntity} somewhere out past the player (not geo-fenced to any particular location, the
     * same honest simplification {@code NpcQuests#findOldHarlen} originally used) and grants a rough
     * bearing+distance via Lyfe's waypoint knowledge if that bridge is loaded. {@code introMessage}
     * is sent to the player once the spot/NPC are chosen.
     */
    static void registerTalkNpcHook(Identifier questId, String npcName, boolean hidesAtNight, String introMessage) {
        QuestAssignmentHooks.register(questId, (player, instanceId, assignedId, plotId) -> {
            if (player.level() instanceof ServerLevel level) {
                double angle = player.getRandom().nextDouble() * Math.PI * 2;
                double distance = 80 + player.getRandom().nextDouble() * 120;
                BlockPos roughSpot = player.blockPosition().offset(
                        (int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
                int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, roughSpot.getX(), roughSpot.getZ());
                BlockPos truePos = new BlockPos(roughSpot.getX(), groundY, roughSpot.getZ());

                QuestEngine.setHint(player, instanceId, truePos, level.dimension().identifier().toString());
                QuestNpcSpawner.spawn(level, truePos, player.getUUID(), instanceId, hidesAtNight, npcName);

                if (com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.isLoaded()) {
                    long placeId = QuestWaypointTicker.placeIdFor(instanceId);
                    com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.grantKnowledge(player, placeId, true, true, false);
                }
                player.sendSystemMessage(Component.literal(introMessage));
            }
        });
    }
}
