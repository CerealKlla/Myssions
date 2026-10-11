package com.github.cerealklla.myssions.quest;

import net.minecraft.resources.Identifier;

/**
 * Read-only summary of what talking to a quest giver (NPC or sign) would currently do -- lets
 * another mod's own UI (e.g. Settlemynts' "Press H" wishlist screen, 2026-10-10) show quest
 * availability without mutating any state. Mirrors {@link QuestTurnIn#interact}'s own branching
 * exactly; see {@link QuestTurnIn#preview}.
 */
public record QuestGiverPreview(Kind kind, Identifier questId, String questName, String blurb) {

    public enum Kind {
        /** No pool for this zone type, or an empty/already-claimed board slot -- nothing to show. */
        NOTHING,
        /** Player has this quest in progress but isn't holding enough proof to turn it in yet. */
        WAITING_ON_PLAYER,
        /** Player has this quest in progress and can turn it in right now. */
        READY_TO_TURN_IN,
        /** Nothing in progress from this issuer -- the plot's board is offering a fresh quest. */
        NEW_OFFER
    }

    public static final QuestGiverPreview NOTHING = new QuestGiverPreview(Kind.NOTHING, null, null, null);
}
