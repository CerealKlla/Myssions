package com.github.cerealklla.myssions.client;

import com.github.cerealklla.myssions.MyssionsModClient;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * "Press &lt;key&gt; to review quest offer" -- floating text centered over the player's XP bar
 * while actively conversing with an NPC who has offered them a quest (request #3, 2026-11-06).
 * Driven entirely by {@link ClientQuestOfferPromptState} (updated from {@code
 * QuestOfferPromptPayload}, sent by {@code quest.QuestOfferPromptTicker}) -- no per-frame server
 * round-trip. Same render-from-client-state shape as Settlemynts' own {@code
 * plotsign.client.ShopPromptOverlay}, which this was modeled on directly.
 */
public final class QuestOfferPromptOverlay implements GuiLayer {

    private static final int MARGIN_FROM_BOTTOM = 36; // just above vanilla's XP bar
    private static final int PADDING = 3;
    private static final int TEXT_COLOR = 0xFFFFFF55;
    private static final int BACKGROUND_COLOR = 0xA0101010;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        if (!ClientQuestOfferPromptState.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return; // Don't draw over an open menu/screen.
        }

        String keyName = MyssionsModClient.OPEN_QUEST_OFFER_REVIEW.getTranslatedKeyMessage().getString();
        String text = "Press " + keyName + " to review quest offer";

        Font font = minecraft.font;
        int textWidth = font.width(text);
        int left = guiGraphics.guiWidth() / 2 - textWidth / 2 - PADDING;
        int right = left + textWidth + PADDING * 2;
        int bottom = guiGraphics.guiHeight() - MARGIN_FROM_BOTTOM;
        int top = bottom - font.lineHeight - PADDING * 2;

        guiGraphics.fill(left, top, right, bottom, BACKGROUND_COLOR);
        guiGraphics.text(font, text, left + PADDING, top + PADDING, TEXT_COLOR);
    }
}
