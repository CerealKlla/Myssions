package com.github.cerealklla.myssions.quest;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.Classification;
import com.github.cerealklla.cartographyr.geo.EntityId;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.myssions.MyssionsMod;
import com.github.cerealklla.myssions.bridge.LyfeSkillLevelBridge;
import com.github.cerealklla.myssions.bridge.LyfeWaypointBridge;
import com.github.cerealklla.myssions.bridge.SettlemyntsRoadQuestBridge;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The real post/accept logic for the inter-settlement road-connection quest -- deliberately a
 * standalone flow, NOT routed through {@code IssuerQuestPools}/{@code IssuerQuestBoard}/{@code
 * QuestTurnIn} (every other quest type's "walk up to an NPC, press H, pick from a pool" shape).
 * This quest is posted/discovered through Settlemynts' own Message Board UI, keyed by {@link
 * RoadQuestRegistry}'s settlement-pair state machine, not a per-plot board slot -- see that
 * class's own doc and design-document.md's quest taxonomy for the full design.
 */
public final class RoadQuestLogic {

    public static final Identifier DEFINITION_ID = Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "connect_settlements_by_road");

    // "1 mile" -- same real-world-feet-to-block convention as every other distance constant in
    // this suite (1 block ~= 1 meter, 1 mile ~= 1609 blocks).
    public static final double NEIGHBOR_SEARCH_RADIUS_BLOCKS = 1609.0;

    // The real, enforced Highway Stake spacing (see Settlemynts' roadway.HighwayStakeEntity).
    private static final double MAX_STAKE_SPACING_BLOCKS = 50.0;
    // "We may also want to increase the base minimum # of stakes so roads don't have to be
    // perfectly straight" -- explicit user follow-up; bumped up from this feature's own original
    // 1.25x plan. A named, easily-retuned constant, not re-derived from anything else.
    private static final double MIN_STAKE_SLACK_FACTOR = 1.4;
    // "An 'optimal' road would not require more than 30% more stakes than the minimum."
    private static final double OPTIMAL_STAKE_SLACK_FACTOR = 1.3;
    // The granted stack: minimum + full allowance for failing every efficiency milestone (the
    // optimal cap itself) + a further 10% buffer, per the user's own formula.
    private static final double GRANTED_STACK_BUFFER_FACTOR = 1.10;

    public static final String REQUIRED_SKILL_ID = "expeditionist";
    public static final int REQUIRED_SKILL_LEVEL = 1; // Deliberately low for now -- tune once this is live-tested.

    public static final double BUYBACK_GOLD_PER_UNUSED_STAKE = 1.0;
    public static final double EFFICIENCY_REWARD_BONUS_FRACTION = 0.10;

    private RoadQuestLogic() {
    }

    public record StakeThresholds(int minStakes, int optimalStakeCap, int grantedStackCount) {
    }

    public static StakeThresholds computeThresholds(double straightLineDistanceBlocks) {
        int minStakes = (int) Math.ceil(straightLineDistanceBlocks / MAX_STAKE_SPACING_BLOCKS * MIN_STAKE_SLACK_FACTOR);
        int optimalCap = (int) Math.ceil(minStakes * OPTIMAL_STAKE_SLACK_FACTOR);
        int granted = (int) Math.ceil(optimalCap * GRANTED_STACK_BUFFER_FACTOR);
        return new StakeThresholds(Math.max(1, minStakes), Math.max(1, optimalCap), Math.max(1, granted));
    }

    /**
     * Posts a new offer for this settlement pair -- false if the pair already has ANY entry
     * (posted/in-progress/connected), or either settlement can't currently be resolved. Called from
     * Settlemynts' Message Board UI (a real Mayor) or an NPC-settlement auto-post (not yet built).
     */
    public static boolean postOffer(ServerLevel level, long postingSettlementPlaceId, long targetSettlementPlaceId,
                                     Optional<UUID> postingPlotId, Optional<UUID> posterPlayerId) {
        Optional<GeographicEntity> posting = Cartography.getEntity(level, new EntityId(postingSettlementPlaceId));
        Optional<GeographicEntity> target = Cartography.getEntity(level, new EntityId(targetSettlementPlaceId));
        if (posting.isEmpty() || target.isEmpty()) {
            return false;
        }
        double dx = posting.get().geometry().centerBlockX() - target.get().geometry().centerBlockX();
        double dz = posting.get().geometry().centerBlockZ() - target.get().geometry().centerBlockZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        StakeThresholds thresholds = computeThresholds(distance);

        return RoadQuestRegistry.get(level.getServer()).post(postingSettlementPlaceId, targetSettlementPlaceId,
                postingPlotId, posterPlayerId, thresholds.minStakes(), thresholds.optimalStakeCap());
    }

    /**
     * Accepts an existing posted offer -- the real assignment flow. False if the pair has no
     * `POSTED` offer, the player doesn't meet {@link #REQUIRED_SKILL_LEVEL}, or {@link
     * QuestEngine#assignQuest} itself refuses (already in progress, at the quest cap). On success:
     * populates the instance's road-quest fields, transfers knowledge of the destination (the
     * Mayor's own real knowledge if they're online, else a flat 1/3-quality grant -- explicit user
     * spec), sets a waypoint near the destination, grants the Highway Stake stack, and flips the
     * registry pair to `IN_PROGRESS`.
     */
    public static boolean acceptOffer(ServerPlayer player, long postingSettlementPlaceId, long targetSettlementPlaceId) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        RoadQuestRegistry registry = RoadQuestRegistry.get(level.getServer());
        Optional<RoadQuestRegistry.Entry> entryOpt = registry.get(postingSettlementPlaceId, targetSettlementPlaceId);
        if (entryOpt.isEmpty() || !entryOpt.get().isPosted()) {
            return false;
        }
        RoadQuestRegistry.Entry entry = entryOpt.get();

        if (LyfeSkillLevelBridge.isLoaded() && LyfeSkillLevelBridge.getLevel(player, REQUIRED_SKILL_ID) < REQUIRED_SKILL_LEVEL) {
            player.sendSystemMessage(Component.literal(
                    "You need more Expeditionist experience before you're ready to chart a road like this."));
            return false;
        }

        Optional<UUID> instanceIdOpt = QuestEngine.assignQuest(player, DEFINITION_ID);
        if (instanceIdOpt.isEmpty()) {
            return false;
        }
        UUID instanceId = instanceIdOpt.get();

        // The settlement that's NOT the one this player is standing at/accepting from is the
        // destination -- postingSettlementPlaceId is always entry.postingSettlementPlaceId() by
        // construction (the pair's own posted direction), so the target is whichever of the two
        // arguments isn't that.
        long destinationPlaceId = entry.postingSettlementPlaceId() == postingSettlementPlaceId
                ? targetSettlementPlaceId : postingSettlementPlaceId;
        long originPlaceId = entry.postingSettlementPlaceId();

        Optional<GeographicEntity> destination = Cartography.getEntity(level, new EntityId(destinationPlaceId));
        String destinationName = destination.map(com.github.cerealklla.cartographyr.geo.DisplayText::shortForEntity).orElse("the other settlement");
        QuestEngine.setRoadQuestData(player, instanceId, originPlaceId, entry.minStakes(), entry.optimalStakeCap());
        QuestEngine.setRegionKnowledge(player, instanceId, destinationPlaceId, "toward " + destinationName, destinationName);

        grantDestinationKnowledge(level, player, entry, destinationPlaceId);

        if (LyfeWaypointBridge.isLoaded() && destination.isPresent()) {
            net.minecraft.core.BlockPos destPos = new net.minecraft.core.BlockPos(
                    destination.get().geometry().centerBlockX(), player.getBlockY(), destination.get().geometry().centerBlockZ());
            LyfeWaypointBridge.setWaypoint(player, destination.get().dimension(), destPos, 200.0);
            QuestEngine.setHint(player, instanceId, destPos, destination.get().dimension().identifier().toString());
        }

        if (SettlemyntsRoadQuestBridge.isLoaded()) {
            Optional<UUID> coreIdA = SettlemyntsRoadQuestBridge.findCoreIdForPlaceId(level, originPlaceId);
            Optional<UUID> coreIdB = SettlemyntsRoadQuestBridge.findCoreIdForPlaceId(level, destinationPlaceId);
            int tierA = SettlemyntsRoadQuestBridge.getSettlementTier(level, originPlaceId);
            int tierB = SettlemyntsRoadQuestBridge.getSettlementTier(level, destinationPlaceId);
            int roadTier = Math.max(1, Math.round((tierA + tierB) / 2.0f));
            StakeThresholds thresholds = new StakeThresholds(entry.minStakes(), entry.optimalStakeCap(), 0);
            int grantedCount = (int) Math.ceil(entry.optimalStakeCap() * GRANTED_STACK_BUFFER_FACTOR);
            SettlemyntsRoadQuestBridge.grantHighwayStakes(player, instanceId, coreIdA, coreIdB, roadTier, grantedCount);
        }

        registry.accept(postingSettlementPlaceId, targetSettlementPlaceId, instanceId, player.getUUID());
        player.sendSystemMessage(Component.literal(
                "You'll need roughly " + entry.minStakes() + " Highway Stakes to connect this road -- "
                        + "stay under " + entry.optimalStakeCap() + " for a bonus. Check your map for the destination."));
        return true;
    }

    /** "The Mayor who has to the player (or for NPCs gives a 1/3 quality knowledge)" -- explicit user spec. */
    private static void grantDestinationKnowledge(ServerLevel level, ServerPlayer accepting, RoadQuestRegistry.Entry entry, long destinationPlaceId) {
        if (!LyfeWaypointBridge.isLoaded()) {
            return;
        }
        if (entry.posterPlayerId().isPresent()) {
            ServerPlayer mayor = level.getServer().getPlayerList().getPlayer(entry.posterPlayerId().get());
            if (mayor != null) {
                LyfeWaypointBridge.copyKnowledge(mayor, accepting, destinationPlaceId);
                return;
            }
        }
        // NPC posting, or the Mayor isn't currently online to copy from -- flat 1/3-quality grant
        // (one of the three DIRECTION/DISTANCE/POSITION factors).
        LyfeWaypointBridge.grantKnowledge(accepting, destinationPlaceId, true, false, false);
    }
}
