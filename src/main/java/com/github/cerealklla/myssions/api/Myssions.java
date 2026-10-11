package com.github.cerealklla.myssions.api;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.myssions.quest.IssuerQuestBoard;
import com.github.cerealklla.myssions.quest.QuestDefinition;
import com.github.cerealklla.myssions.quest.QuestEngine;
import com.github.cerealklla.myssions.quest.QuestGiverMenu;
import com.github.cerealklla.myssions.quest.QuestInstance;
import com.github.cerealklla.myssions.quest.QuestRegistry;
import com.github.cerealklla.myssions.quest.QuestTurnIn;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public, stable facade for other mods -- the only supported entry point into Myssions from
 * outside this mod, mirroring every other sibling mod's own {@code api.*} facade (Settlemynts,
 * Blueprynts, Kyt, Lyfe). Reference this class via a {@code compileOnly} dependency guarded behind
 * {@code ModList.get().isLoaded("myssions")} at the actual call site -- same optional-dependency
 * convention as every cross-mod integration in this suite.
 *
 * <p>Every per-instance method here takes a real {@link QuestInstance#instanceId()} (a hidden
 * {@code UUID}, added 2026-10-10), not a bare quest {@code Identifier} -- a player can hold two
 * independent instances of the same quest definition at once (e.g. the same quest offered by two
 * different NPCs), so the definition id alone can't name a specific one. {@link #assignQuest}
 * still takes the definition id (there's nothing to disambiguate when creating a new instance) and
 * hands back the new instance's id.
 */
public final class Myssions {

    private Myssions() {
    }

    /** Registers a quest definition so it can later be assigned to a player. Safe to call repeatedly with the same id -- re-registers (overwrites) it. */
    public static void registerQuestDefinition(QuestDefinition definition) {
        QuestRegistry.register(definition);
    }

    public static Optional<QuestDefinition> getQuestDefinition(Identifier definitionId) {
        return QuestRegistry.get(definitionId);
    }

    /** Assigns a fresh instance of this definition to a player, returning its new instanceId -- empty if the definition doesn't exist, they already have it in progress, or they're at the quest cap. */
    public static Optional<UUID> assignQuest(ServerPlayer player, Identifier definitionId) {
        return QuestEngine.assignQuest(player, definitionId);
    }

    /** Re-assigns a previously-failed instance fresh, returning its new instanceId. Empty if {@code instanceId} isn't a failed instance they actually have. */
    public static Optional<UUID> retryQuest(ServerPlayer player, UUID instanceId) {
        return QuestEngine.retryQuest(player, instanceId);
    }

    public static Optional<QuestInstance> getQuestInstance(ServerPlayer player, UUID instanceId) {
        return QuestEngine.getInstance(player, instanceId);
    }

    /**
     * Adds {@code amount} progress toward one milestone of an instance the player already has
     * in-progress -- automatically completes (and applies the rewards of) the quest once every
     * non-optional milestone is done. The real entry point for any mod wiring up a quest's actual
     * trigger logic (a mob-death listener, a region-enter check, an NPC dialogue, etc.).
     */
    public static void reportMilestoneProgress(ServerPlayer player, UUID instanceId, String milestoneId, int amount) {
        QuestEngine.reportMilestoneProgress(player, instanceId, milestoneId, amount);
    }

    /** Convenience for a milestone that's simply done/not-done (e.g. "talk to the NPC"), rather than a count. */
    public static void completeMilestone(ServerPlayer player, UUID instanceId, String milestoneId) {
        QuestEngine.reportMilestoneProgress(player, instanceId, milestoneId, Integer.MAX_VALUE / 2);
    }

    /** Attempts to complete a "deliver proof" milestone from the player's own held/inventory items. See {@code QuestEngine#tryDeliverProof}'s own doc. */
    public static boolean tryDeliverProof(ServerPlayer player, UUID instanceId, String milestoneId) {
        return QuestEngine.tryDeliverProof(player, instanceId, milestoneId);
    }

    /** Attempts to fully complete an in-progress instance right now -- see {@code QuestEngine#tryCompleteQuest}'s own doc. */
    public static boolean tryCompleteQuest(ServerPlayer player, UUID instanceId) {
        return QuestEngine.tryCompleteQuest(player, instanceId);
    }

    public static void setActiveQuest(ServerPlayer player, UUID instanceId) {
        QuestEngine.setActiveQuest(player, instanceId);
    }

    /** Running total of quests completed for a given Issuer id -- groundwork for a future karma/reputation system. */
    public static int getCompletedCountForIssuer(ServerPlayer player, String issuerId) {
        return QuestEngine.getCompletedCountForIssuer(player, issuerId);
    }

    /**
     * Read-only snapshot of plot {@code plotId}'s (zone type {@code zoneTypeId}, matching {@code
     * IssuerQuestPools}' own plain-string-literal convention) quest-giver menu for {@code player}
     * right now -- their own active quests from this issuer's pool plus any unclaimed board offer.
     * Built for Settlemynts' "Press H" chat menu (2026-10-10, explicit user spec: "select your
     * active quest by name, and then in a sub-menu have the option to complete the quest").
     */
    public static QuestGiverMenu getQuestGiverMenu(ServerPlayer player, UUID plotId, String zoneTypeId) {
        return QuestTurnIn.getMenu(player, plotId, zoneTypeId);
    }

    /** Accepts plot {@code plotId}'s current board offer, returning the new instanceId. See {@code QuestTurnIn#acceptBoardOffer}'s own doc. */
    public static Optional<UUID> acceptBoardOffer(ServerPlayer player, UUID plotId) {
        return QuestTurnIn.acceptBoardOffer(player, plotId);
    }

    /** The real accept-new-quest-or-complete-current-one shift+right-click action. See {@code QuestTurnIn#quickInteract}'s own doc. */
    public static void quickInteractWithQuestGiver(ServerPlayer player, UUID plotId, String zoneTypeId) {
        QuestTurnIn.quickInteract(player, plotId, zoneTypeId);
    }

    /**
     * Player-independent check: does this plot's board currently have an unclaimed quest offer
     * sitting on it. Used for a global "!" nameplate (visible to every player, not just whoever's
     * asking) -- unlike {@link #getQuestGiverMenu}, which is per-player.
     */
    public static boolean hasPendingBoardOffer(MinecraftServer server, UUID plotId) {
        return IssuerQuestBoard.get(server).get(plotId).isPresent();
    }

    /**
     * Player-specific check: does {@code player} have urgent, actionable business with this specific
     * issuer right now -- an active quest with an incomplete talk milestone (walking up and talking
     * is their actual next step), or one that's fully ready to turn in. Deliberately narrower than
     * "has any active quest from this issuer at all" (which {@code getQuestGiverMenu} already covers
     * for the Press-H prompt) -- used for the issuer NPC's personal "!" head marker (2026-10-11 user
     * spec), which should only light up when there's something to actually walk over and do, not for
     * every quest that merely happens to be in progress (e.g. "go kill 5 zombies," with nothing left
     * to say to this NPC until that's done).
     */
    public static boolean hasUrgentPersonalBusiness(ServerPlayer player, UUID plotId, String zoneTypeId) {
        for (QuestGiverMenu.ActiveEntry entry : getQuestGiverMenu(player, plotId, zoneTypeId).activeQuests()) {
            if (entry.needsTalk() || entry.readyToComplete()) {
                return true;
            }
        }
        return false;
    }

    // --- Inter-settlement road-connection quests (added 2026-10-11) -- see
    // quest.RoadQuestLogic/quest.RoadQuestRegistry's own doc. Settlements are identified by their
    // Cartographyr placeId, not any Settlemynts-specific id, since that's the one identity every
    // settlement (player-founded or natural) already has.

    /** Current state of the (unordered) pair, if any -- `empty` means free for either side to post. */
    public static Optional<com.github.cerealklla.myssions.quest.RoadQuestRegistry.Entry> getRoadQuestState(
            MinecraftServer server, long placeIdA, long placeIdB) {
        return com.github.cerealklla.myssions.quest.RoadQuestRegistry.get(server).get(placeIdA, placeIdB);
    }

    /** Every settlement placeId already {@code CONNECTED} to this one -- used to gate the escort/caravan quest style on a real connection existing. */
    public static java.util.List<Long> getConnectedNeighbors(MinecraftServer server, long placeId) {
        return com.github.cerealklla.myssions.quest.RoadQuestRegistry.get(server).connectedNeighborsOf(placeId);
    }

    /** Posts a new road-connection offer for this settlement pair -- see {@code RoadQuestLogic#postOffer}'s own doc. False if the pair already has any entry, or either settlement can't be resolved. */
    public static boolean postRoadQuestOffer(net.minecraft.server.level.ServerLevel level, long postingSettlementPlaceId,
                                              long targetSettlementPlaceId, Optional<UUID> postingPlotId, Optional<UUID> posterPlayerId) {
        return com.github.cerealklla.myssions.quest.RoadQuestLogic.postOffer(level, postingSettlementPlaceId, targetSettlementPlaceId, postingPlotId, posterPlayerId);
    }

    /** Accepts an existing posted road-connection offer -- see {@code RoadQuestLogic#acceptOffer}'s own doc. */
    public static boolean acceptRoadQuestOffer(ServerPlayer player, long postingSettlementPlaceId, long targetSettlementPlaceId) {
        return com.github.cerealklla.myssions.quest.RoadQuestLogic.acceptOffer(player, postingSettlementPlaceId, targetSettlementPlaceId);
    }
}
