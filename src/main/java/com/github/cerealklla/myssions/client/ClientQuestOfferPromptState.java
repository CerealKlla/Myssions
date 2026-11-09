package com.github.cerealklla.myssions.client;

/**
 * Client-side holder for "should the 'Press &lt;key&gt; to review quest offer' prompt be showing
 * right now," updated whenever a {@code QuestOfferPromptPayload} arrives (2026-11-06). Same shape
 * as Settlemynts' {@code plotsign.client.ClientShopPromptState} -- harmless if classloaded on a
 * dedicated server, it just never gets written to there.
 */
public final class ClientQuestOfferPromptState {

    private static volatile boolean show = false;

    private ClientQuestOfferPromptState() {
    }

    public static void set(boolean value) {
        show = value;
    }

    public static boolean get() {
        return show;
    }
}
