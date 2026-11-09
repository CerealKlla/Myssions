package com.github.cerealklla.myssions.bridge;

import java.util.UUID;

import com.github.cerealklla.audyble.api.Audyble;

import net.neoforged.fml.ModList;

/**
 * Optional soft dependency on Audyble's own NPC conversation tracking -- same isolation convention
 * as every other bridge in this suite: every call site elsewhere in Myssions must check {@link
 * #isLoaded()} itself before touching this class at all. Added 2026-11-06 for the new quest-offer-
 * review prompt (see {@code quest.QuestOfferPromptTicker}), which shows "Press &lt;button&gt; to
 * review quest offer" while a player is actively conversing with an NPC and has at least one
 * available quest -- on an Audyble-less server the prompt simply never fires (an available quest
 * still shows up normally in the Quest Log, there's just no proactive nudge to go look).
 */
public final class AudybleConversationBridge {

    private AudybleConversationBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("audyble");
    }

    public static boolean isInConversation(UUID playerId) {
        return Audyble.isInConversation(playerId);
    }
}
