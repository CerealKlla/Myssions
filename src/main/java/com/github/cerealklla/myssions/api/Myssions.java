package com.github.cerealklla.myssions.api;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.myssions.quest.IssuerQuestBoard;
import com.github.cerealklla.myssions.quest.QuestDefinition;
import com.github.cerealklla.myssions.quest.QuestEngine;
import com.github.cerealklla.myssions.quest.QuestGiverPreview;
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

    /** Assigns a registered quest to a player. False if the definition doesn't exist or they already have it in progress. */
    public static boolean assignQuest(ServerPlayer player, Identifier definitionId) {
        return QuestEngine.assignQuest(player, definitionId);
    }

    /** Re-assigns a previously-failed quest fresh. False if they don't have a failed instance of it. */
    public static boolean retryQuest(ServerPlayer player, Identifier definitionId) {
        return QuestEngine.retryQuest(player, definitionId);
    }

    public static Optional<QuestInstance> getQuestInstance(ServerPlayer player, Identifier definitionId) {
        return QuestEngine.getInstance(player, definitionId);
    }

    /**
     * Adds {@code amount} progress toward one milestone of a quest the player already has
     * in-progress -- automatically completes (and applies the rewards of) the quest once every
     * non-optional milestone is done. The real entry point for any mod wiring up a quest's actual
     * trigger logic (a mob-death listener, a region-enter check, an NPC dialogue, etc.).
     */
    public static void reportMilestoneProgress(ServerPlayer player, Identifier definitionId, String milestoneId, int amount) {
        QuestEngine.reportMilestoneProgress(player, definitionId, milestoneId, amount);
    }

    /** Convenience for a milestone that's simply done/not-done (e.g. "talk to the NPC"), rather than a count. */
    public static void completeMilestone(ServerPlayer player, Identifier definitionId, String milestoneId) {
        QuestEngine.reportMilestoneProgress(player, definitionId, milestoneId, Integer.MAX_VALUE / 2);
    }

    /** Attempts to complete a "deliver proof" milestone from the player's own held/inventory items. See {@code QuestEngine#tryDeliverProof}'s own doc. */
    public static boolean tryDeliverProof(ServerPlayer player, Identifier definitionId, String milestoneId) {
        return QuestEngine.tryDeliverProof(player, definitionId, milestoneId);
    }

    public static void setActiveQuest(ServerPlayer player, Identifier definitionId) {
        QuestEngine.setActiveQuest(player, definitionId);
    }

    /** Running total of quests completed for a given Issuer id -- groundwork for a future karma/reputation system. */
    public static int getCompletedCountForIssuer(ServerPlayer player, String issuerId) {
        return QuestEngine.getCompletedCountForIssuer(player, issuerId);
    }

    /**
     * Read-only preview of what talking to the quest giver on plot {@code plotId} (zone type {@code
     * zoneTypeId}, matching {@code IssuerQuestPools}' own plain-string-literal convention) would do
     * right now -- for another mod's own UI to show quest availability (2026-10-10, built for
     * Settlemynts' "Press H" wishlist screen) without mutating anything.
     */
    public static QuestGiverPreview previewQuestGiver(ServerPlayer player, UUID plotId, String zoneTypeId) {
        return QuestTurnIn.preview(player, plotId, zoneTypeId);
    }

    /** Runs the real accept-new-quest-or-turn-in-current-one action -- same mutation a shift+right-click on the quest giver itself triggers. */
    public static void interactWithQuestGiver(ServerPlayer player, UUID plotId, String zoneTypeId) {
        QuestTurnIn.interact(player, plotId, zoneTypeId);
    }

    /**
     * Player-independent check: does this plot's board currently have an unclaimed quest offer
     * sitting on it. Used for a global "!" nameplate (visible to every player, not just whoever's
     * asking) -- unlike {@link #previewQuestGiver}, which is per-player (an in-progress/ready-to-
     * deliver quest only means something to the specific player who accepted it).
     */
    public static boolean hasPendingBoardOffer(MinecraftServer server, UUID plotId) {
        return IssuerQuestBoard.get(server).get(plotId).isPresent();
    }
}
