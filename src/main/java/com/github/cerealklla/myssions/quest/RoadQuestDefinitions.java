package com.github.cerealklla.myssions.quest;

import java.util.List;
import java.util.Optional;

import com.github.cerealklla.myssions.bridge.SettlemyntsRoadQuestBridge;

import net.minecraft.server.level.ServerLevel;

/**
 * Registers the one static {@link QuestDefinition} behind every road-connection quest instance --
 * see {@link RoadQuestLogic}'s own doc for why this quest type is assigned through a standalone
 * flow rather than {@code IssuerQuestPools}. Every instance's actual target settlement, stake-count
 * thresholds, etc. are per-instance data (see {@code QuestInstance}'s road-quest fields) layered on
 * top of this one shared template.
 *
 * <p>Doubles as the Mayor-initiated "Establish Highway" quest (added 2026-10-13) -- there's no
 * separate definition for that flow; Settlemynts' Town Hall sign button just calls the same {@code
 * api.Myssions#postRoadQuestOffer} every other poster already uses, with its own plot/player id. The
 * only thing special about that path is {@code isSpecialAssignment} below, which applies uniformly
 * to every instance of this one shared definition regardless of who posted it.
 */
public final class RoadQuestDefinitions {

    private RoadQuestDefinitions() {
    }

    public static void registerAll() {
        QuestRegistry.register(new QuestDefinition(RoadQuestLogic.DEFINITION_ID, "Connect Settlements by Road",
                "Build a real road connecting the two settlements, end to end -- plant Highway Stakes "
                        + "from just outside one settlement's edge to just outside the other's. Once the road sinks "
                        + "in, the Board pays out -- and anything you didn't end up needing gets bought back.",
                "road_quest_board",
                List.of(
                        QuestMilestone.simple("connect_road", "Connect the two settlements with a road"),
                        // Explicit user spec, 2026-10-13 (correcting an earlier, wrong flat-bonus
                        // read of the same spec): three cascading efficiency tiers, not one -- "a
                        // road can be slightly efficient, slightly more efficient, and very
                        // efficient, and the bonus paid would make sense based on that... spread
                        // across those levels." Each tier pays a flat 100 (300 total "max") via
                        // QuestEngine#addPlainMilestoneBonus; RoadQuestTicker#finalizeConnection
                        // reports ALL tiers whose threshold the real stake count qualifies for, not
                        // just the single tightest one -- achieving the hardest tier (very
                        // efficient) always also satisfies the two looser ones, since the
                        // thresholds nest (tier3 <= tier2 <= tier1). bonusGoldPerProof (10th field)
                        // set directly via the canonical constructor -- QuestMilestone#simple's own
                        // factory always hardcodes this to 0.
                        new QuestMilestone("efficient_road_tier1", "Build it efficiently (bonus reward)", true, 1,
                                java.util.Optional.empty(), java.util.Optional.empty(), false, false, false, 100, false, false, false, false),
                        new QuestMilestone("efficient_road_tier2", "Build it more efficiently (bigger bonus)", true, 1,
                                java.util.Optional.empty(), java.util.Optional.empty(), false, false, false, 100, false, false, false, false),
                        new QuestMilestone("efficient_road_tier3", "Build it very efficiently (max bonus)", true, 1,
                                java.util.Optional.empty(), java.util.Optional.empty(), false, false, false, 100, false, false, false, false)
                ),
                List.of(QuestReward.gold(500)),
                QuestTimeLimit.withinTicksOfAssignment(6L * 60 * 60 * 20),
                List.of(),
                true
        ));
        // Explicit user spec, 2026-10-13: on failure/abandon, remove every granted Highway Stake from
        // the player's inventory, despawn any already-placed stakes (reverting their paved terrain --
        // see HighwayStakeEntity#despawnAllForInstance's own doc), and re-open the same settlement
        // pair's offer rather than wiping it (RoadQuestRegistry#reopenAsPosted) -- "will not... re-
        // charge the mayor." Deliberately NOT registered for COMPLETED: a connected road's stake
        // network is meant to persist, and RoadQuestTicker already runs its own real buyback+reward
        // on that path.
        QuestEndHooks.register(RoadQuestLogic.DEFINITION_ID, (player, instanceId, questId, reason) -> {
            if (reason == QuestEndHooks.EndReason.COMPLETED || !(player.level() instanceof ServerLevel level)
                    || !SettlemyntsRoadQuestBridge.isLoaded()) {
                return;
            }
            QuestEngine.getInstance(player, instanceId).ifPresent(instance -> {
                SettlemyntsRoadQuestBridge.buybackUnusedStakes(player, instanceId); // Discards the count -- no gold payout on a failed/abandoned road.
                instance.hintPos().ifPresent(hint -> SettlemyntsRoadQuestBridge.despawnStakesForInstance(level, instanceId, hint));
                Optional<Long> originPlaceId = instance.roadQuest().originPlaceId();
                Optional<Long> destinationPlaceId = instance.knowledgePlaceId();
                if (originPlaceId.isPresent() && destinationPlaceId.isPresent()) {
                    RoadQuestRegistry.get(level.getServer()).reopenAsPosted(originPlaceId.get(), destinationPlaceId.get());
                }
            });
        });
    }
}
