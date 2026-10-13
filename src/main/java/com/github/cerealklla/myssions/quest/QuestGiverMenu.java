package com.github.cerealklla.myssions.quest;

import java.util.List;
import java.util.UUID;

import net.minecraft.resources.Identifier;

/**
 * Everything a quest giver's own chat-menu sub-screen needs to show (2026-10-10, explicit user
 * spec: "you should be able to select your active quest by name, and then in a sub-menu have the
 * option to complete the quest"). Built by {@code quest.QuestTurnIn#getMenu}, consumed by another
 * mod's UI (Settlemynts' "Press H" chat menu) via {@code api.Myssions#getQuestGiverMenu} -- a
 * read-only snapshot, never mutates anything.
 */
public record QuestGiverMenu(List<ActiveEntry> activeQuests, List<NewOffer> newOffers) {

    /**
     * One of the player's own in-progress instances from this issuer's pool, by its hidden
     * instanceId -- never the definition id, since two instances of the same definition are shown
     * as two separate entries. {@code needsTalk} (added 2026-10-11, for the issuer NPC's personal
     * "!" head marker -- see {@code SettlemyntsMod#anchorHasUrgentPersonalBusiness}) is true when a
     * non-optional, not-yet-complete milestone is specifically a talk milestone -- i.e. walking up
     * and talking to this NPC is the player's actual next step, not just "this quest happens to be
     * in progress."
     */
    public record ActiveEntry(UUID instanceId, String questName, boolean readyToComplete, boolean needsTalk,
                               boolean isSpecialAssignment) {
    }

    /** One of the plot's current (unclaimed) board offers, filtered to ones the player doesn't already have a record of. */
    public record NewOffer(Identifier questId, String questName, String description, boolean isSpecialAssignment) {
    }

    public static final QuestGiverMenu EMPTY = new QuestGiverMenu(List.of(), List.of());
}
