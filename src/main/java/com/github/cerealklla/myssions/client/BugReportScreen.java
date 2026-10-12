package com.github.cerealklla.myssions.client;

import java.util.UUID;

import com.github.cerealklla.myssions.net.SubmitBugReportPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * "Report Bug" (explicit user request, 2026-10-12) -- opened from {@code QuestLogScreen}'s own
 * "Report Bug" button on whatever quest is currently selected. A plain free-text box; everything
 * else useful for debugging (quest definition/instance state, the player's own knowledge of the
 * quest's location, their current position) is gathered entirely server-side by {@code
 * quest.BugReportWriter} once submitted -- this screen only ever needs to carry the written text
 * and which instance it's about.
 */
public final class BugReportScreen extends Screen {

    private static final int MARGIN = 20;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final UUID instanceId;
    private final String questName;
    private MultiLineEditBox textBox;
    private boolean submitted;

    public BugReportScreen(Screen parent, UUID instanceId, String questName) {
        super(Component.literal("Report Bug"));
        this.parent = parent;
        this.instanceId = instanceId;
        this.questName = questName;
    }

    @Override
    protected void init() {
        int boxLeft = MARGIN;
        int boxRight = width - MARGIN;
        int boxTop = 50;
        int boxBottom = height - MARGIN - BUTTON_HEIGHT - 10;

        textBox = MultiLineEditBox.builder()
                .setX(boxLeft)
                .setY(boxTop)
                .setPlaceholder(Component.literal("Describe what went wrong..."))
                .build(font, boxRight - boxLeft, boxBottom - boxTop, Component.literal("Bug report text"));
        addRenderableWidget(textBox);
        setInitialFocus(textBox);

        int buttonY = height - MARGIN - BUTTON_HEIGHT;
        addRenderableWidget(Button.builder(Component.literal("Submit"), b -> submit())
                .bounds(boxLeft, buttonY, 100, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> Minecraft.getInstance().setScreen(parent))
                .bounds(boxLeft + 110, buttonY, 100, BUTTON_HEIGHT).build());
    }

    private void submit() {
        if (submitted) {
            return; // Guard against a double-click firing two reports for the same submission.
        }
        submitted = true;
        Minecraft.getInstance().getConnection().send(
                new ServerboundCustomPayloadPacket(new SubmitBugReportPayload(instanceId, textBox.getValue())));
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, "Report Bug: " + questName, MARGIN, MARGIN, 0xFFFFFFFF);
    }
}
