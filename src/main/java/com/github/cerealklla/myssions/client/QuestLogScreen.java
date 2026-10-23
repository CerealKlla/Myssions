package com.github.cerealklla.myssions.client;

import com.github.cerealklla.myssions.menu.client.QuestLogTab;
import com.github.cerealklla.nuclyus.menu.client.WidgetHost;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/**
 * The Quest Log's standalone top-level entry point -- opened via {@code
 * MyssionsModClient#OPEN_QUEST_LOG} ("J"). Thin host around {@link QuestLogTab}, kept so the Quest
 * Log still works exactly as before even without Nuclyus's shared "K" menu actually open (e.g. a
 * server without Lyfe/Coryspondance installed) -- see {@code menu.client.QuestLogTab}'s own doc for
 * the full design rationale and why this is a direct port, not a rewrite. Implements {@code
 * WidgetHost} for the same reason {@code NuclyusMenuScreen} does: the tab's own real widgets
 * (action/abandon/report-bug buttons) are added here, onto whichever concrete {@code Screen}
 * {@link QuestLogTab#attachHost} was given, not hardcoded to assume Nuclyus's host specifically.
 */
public final class QuestLogScreen extends Screen implements WidgetHost {

    private final QuestLogTab tab = new QuestLogTab();

    public QuestLogScreen() {
        super(Component.literal("Quests"));
        tab.attachHost(this);
    }

    private Rect2i area() {
        return new Rect2i(0, 0, width, height);
    }

    @Override
    public Rect2i contentArea() {
        return area();
    }

    @Override
    protected void init() {
        tab.onShown();
    }

    @Override
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addMenuWidget(T widget) {
        return addRenderableWidget(widget);
    }

    @Override
    public void removeMenuWidget(GuiEventListener widget) {
        removeWidget(widget);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tab.mouseClicked(area(), event, doubleClick)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return tab.mouseScrolled(area(), mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (tab.keyPressed(area(), event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        tab.render(graphics, area(), mouseX, mouseY, partialTick);
    }
}
