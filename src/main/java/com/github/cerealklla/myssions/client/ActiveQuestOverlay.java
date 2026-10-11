package com.github.cerealklla.myssions.client;

import com.github.cerealklla.myssions.quest.QuestDefinition;
import com.github.cerealklla.myssions.quest.QuestInstance;
import com.github.cerealklla.myssions.quest.QuestMilestone;
import com.github.cerealklla.myssions.quest.QuestRegistry;
import com.github.cerealklla.myssions.quest.QuestStatus;
import com.github.cerealklla.myssions.quest.QuestText;
import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * "Active Quest" floating text, top-left corner (design doc item 3) -- quest name plus its
 * bullet-pointed milestone list, completed ones struck through. Driven entirely by the local,
 * already-synced {@code quest.PlayerQuestData} attachment + {@link QuestRegistry} (identical
 * content both physical sides, see {@code MyssionsMod#commonSetup}) -- no per-frame server round
 * trip, same shape as every other client-state-driven overlay in this suite (e.g. Settlemynts'
 * {@code ShopPromptOverlay}).
 */
public final class ActiveQuestOverlay implements GuiLayer {

    private static final int MARGIN = 6;
    private static final int PADDING = 4;
    private static final int TITLE_COLOR = 0xFFFFD700;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int DONE_COLOR = 0xFFA0A0A0;
    private static final int BACKGROUND_COLOR = 0x80101010;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        var data = minecraft.player.getData(ModAttachments.QUEST_DATA);
        if (data.activeQuestId().isEmpty()) {
            return;
        }
        QuestInstance instance = data.quests().get(data.activeQuestId().get());
        if (instance == null || (instance.status() != QuestStatus.IN_PROGRESS && instance.status() != QuestStatus.AWAITING_REWARD_PICKUP)) {
            return;
        }
        QuestDefinition definition = QuestRegistry.get(instance.definitionId()).orElse(null);
        if (definition == null) {
            return;
        }

        Font font = minecraft.font;
        int maxTextWidth = font.width(definition.name());
        for (QuestMilestone milestone : definition.milestones()) {
            maxTextWidth = Math.max(maxTextWidth, font.width("- " + QuestText.resolve(milestone.description(), instance)));
        }
        int lineCount = 1 + definition.milestones().size();
        int left = MARGIN;
        int top = MARGIN;
        int right = left + maxTextWidth + PADDING * 2;
        int bottom = top + font.lineHeight * lineCount + PADDING * 2;

        graphics.fill(left, top, right, bottom, BACKGROUND_COLOR);
        int y = top + PADDING;
        graphics.text(font, definition.name(), left + PADDING, y, TITLE_COLOR);
        y += font.lineHeight;
        for (QuestMilestone milestone : definition.milestones()) {
            boolean complete = instance.isMilestoneComplete(milestone);
            String line = "- " + QuestText.resolve(milestone.description(), instance) + (milestone.optional() ? " (optional)" : "");
            if (complete) {
                line = ChatFormatting.STRIKETHROUGH + line;
            }
            graphics.text(font, line, left + PADDING, y, complete ? DONE_COLOR : TEXT_COLOR);
            y += font.lineHeight;
        }
    }
}
