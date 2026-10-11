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

    /** A real-location/distance-scaled kill quest (plus an optional bonus-paying proof delivery), reusing {@link #registerRegionHook}. */
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

    /** An escort/caravan-raid quest, reusing {@link #registerEscortHook}. */
    static Identifier registerEscortQuest(Identifier questId, String name, String description, String zoneType,
                                            int waypointCount, int maxBanditEncounters, List<QuestReward> rewards) {
        QuestRegistry.register(new QuestDefinition(questId, name, description, zoneType,
                List.of(
                        new QuestMilestone("fend_off_bandits", "Fend off any Bandits that attack the caravan",
                                true, maxBanditEncounters, Optional.of(Identifier.fromNamespaceAndPath("minecraft", "pillager")),
                                Optional.empty(), false, false, false, 0, false, false, false),
                        QuestMilestone.escort("escort_caravan", "Escort the caravan safely to the end of the road")
                ),
                rewards, QuestTimeLimit.NONE));
        registerEscortHook(questId, waypointCount);
        return questId;
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
                    String direction = directionWord(origin.pos(), target.pos());
                    String directionPhrase = "to the " + direction + " of " + settlementName;

                    QuestEngine.setHint(player, instanceId, target.pos(), target.dimension().identifier().toString(),
                            Optional.of(directionPhrase));
                    QuestEngine.setRegionKnowledge(player, instanceId, target.placeId(), directionPhrase, target.displayName());

                    double fraction = Math.max(0.0, Math.min(1.0, target.distanceBlocks() / REGION_QUEST_MAX_RADIUS_BLOCKS));
                    double multiplier = REGION_REWARD_MIN_MULTIPLIER + (REGION_REWARD_MAX_MULTIPLIER - REGION_REWARD_MIN_MULTIPLIER) * fraction;
                    QuestEngine.setRewardMultiplier(player, instanceId, multiplier);

                    // Temporary debug logging (2026-10-11, explicit user request while chasing a
                    // "marker never showed up" report) -- remove once that's resolved and confirmed
                    // live, not a standing feature.
                    if (com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.isLoaded()) {
                        int priorFactors = com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.knownFactorCount(player, target.placeId());
                        com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.grantKnowledge(player, target.placeId(), true, true, false);
                        int afterFactors = com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.knownFactorCount(player, target.placeId());
                        MyssionsMod.LOGGER.info(
                                "[QuestDebug] {} accepted region quest {} -- target '{}' (placeId={}), prior knowledge factors={}, after grant={}, direction='{}', activeQuestId will be {}",
                                player.getGameProfile().name(), questId, target.displayName(), target.placeId(),
                                priorFactors, afterFactors, directionPhrase,
                                player.getData(com.github.cerealklla.myssions.registration.ModAttachments.QUEST_DATA).activeQuestId()
                                        .map(Object::toString).orElse("<none set -- this quest's marker will NOT show until 'Set as Active Quest' is clicked>"));
                    } else {
                        MyssionsMod.LOGGER.info("[QuestDebug] {} accepted region quest {} -- Lyfe not loaded, no knowledge/waypoint possible",
                                player.getGameProfile().name(), questId);
                    }
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
            QuestEngine.setHint(player, instanceId, player.blockPosition(), level.dimension().identifier().toString(), Optional.of(itemName));
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
