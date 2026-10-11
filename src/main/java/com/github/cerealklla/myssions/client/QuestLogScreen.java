package com.github.cerealklla.myssions.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.cerealklla.myssions.net.RetryQuestPayload;
import com.github.cerealklla.myssions.net.SetActiveQuestPayload;
import com.github.cerealklla.myssions.quest.QuestDefinition;
import com.github.cerealklla.myssions.quest.QuestInstance;
import com.github.cerealklla.myssions.quest.QuestMilestone;
import com.github.cerealklla.myssions.quest.QuestRegistry;
import com.github.cerealklla.myssions.quest.QuestReward;
import com.github.cerealklla.myssions.quest.QuestStatus;
import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;

/**
 * The Quest Log (design doc item 1) -- opened via {@code MyssionsModClient#OPEN_QUEST_LOG}. Two
 * independently-scrollable columns: a left list (~30% width, quest names, Failed Quests grouped at
 * the bottom per item 6) and a right details pane (~60% width, full quest info + "Set as Active
 * Quest"/"Retry"). Reads its quest list entirely from client-local state -- {@link QuestRegistry}
 * (identical content on both physical sides, see {@code MyssionsMod#commonSetup}'s own doc) joined
 * against the local player's own synced {@code quest.PlayerQuestData} attachment -- no network
 * round trip needed just to open this screen.
 *
 * <p>Deliberately hand-rolled scrolling (manual offset + scissor), not a shared list widget --
 * matches this suite's own established convention (see {@code construction.BlueprintPickerScreen}'s
 * own doc: "no list widget exists anywhere in this codebase").
 */
public final class QuestLogScreen extends Screen {

    private static final int MARGIN = 12;
    private static final int LIST_WIDTH_FRACTION_NUM = 3; // 30%
    private static final int DETAILS_WIDTH_FRACTION_NUM = 6; // 60%, leaving a 10% gutter
    private static final int FRACTION_DEN = 10;
    private static final int ROW_HEIGHT = 14;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int DIM_TEXT_COLOR = 0xFFA0A0A0;
    private static final int FAILED_TEXT_COLOR = 0xFFCC5555;
    private static final int ACTIVE_TEXT_COLOR = 0xFFFFD700;
    private static final int HEADER_COLOR = 0xFFFFFFFF;

    private record ListRow(Identifier definitionId, String label, int color, boolean isHeader) {
    }

    private List<ListRow> listRows = List.of();
    private Identifier selectedId;
    private int listScroll;
    private int detailsScroll;
    private Button actionButton;

    public QuestLogScreen() {
        super(Component.literal("Quests"));
    }

    @Override
    protected void init() {
        rebuildListRows();
        if (selectedId == null && !listRows.isEmpty()) {
            listRows.stream().filter(r -> !r.isHeader()).findFirst().ifPresent(r -> selectedId = r.definitionId());
        }
        rebuildActionButton();
    }

    private void rebuildListRows() {
        var data = Minecraft.getInstance().player.getData(ModAttachments.QUEST_DATA);
        List<ListRow> rows = new ArrayList<>();
        List<QuestInstance> active = new ArrayList<>();
        List<QuestInstance> failed = new ArrayList<>();
        for (QuestInstance instance : data.quests().values()) {
            if (instance.status() == QuestStatus.FAILED) {
                failed.add(instance);
            } else {
                active.add(instance);
            }
        }
        for (QuestInstance instance : active) {
            QuestRegistry.get(instance.definitionId()).ifPresent(def -> {
                boolean isActiveQuest = data.activeQuestId().map(id -> id.equals(instance.definitionId())).orElse(false);
                int color = instance.status() == QuestStatus.COMPLETED ? DIM_TEXT_COLOR : (isActiveQuest ? ACTIVE_TEXT_COLOR : TEXT_COLOR);
                String prefix = instance.status() == QuestStatus.COMPLETED ? "[Done] " : (isActiveQuest ? "* " : "");
                rows.add(new ListRow(instance.definitionId(), prefix + def.name(), color, false));
            });
        }
        if (!failed.isEmpty()) {
            rows.add(new ListRow(null, "-- Failed Quests --", HEADER_COLOR, true));
            for (QuestInstance instance : failed) {
                QuestRegistry.get(instance.definitionId())
                        .ifPresent(def -> rows.add(new ListRow(instance.definitionId(), def.name(), FAILED_TEXT_COLOR, false)));
            }
        }
        listRows = rows;
    }

    private void rebuildActionButton() {
        if (actionButton != null) {
            removeWidget(actionButton);
            actionButton = null;
        }
        if (selectedId == null) {
            return;
        }
        var data = Minecraft.getInstance().player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(selectedId);
        if (instance == null) {
            return;
        }
        int detailsLeft = width * LIST_WIDTH_FRACTION_NUM / FRACTION_DEN + MARGIN * 2;
        int buttonY = MARGIN;
        if (instance.status() == QuestStatus.IN_PROGRESS) {
            boolean isActiveQuest = data.activeQuestId().map(id -> id.equals(selectedId)).orElse(false);
            actionButton = addRenderableWidget(Button.builder(
                            Component.literal(isActiveQuest ? "Active Quest" : "Set as Active Quest"),
                            b -> setActive())
                    .bounds(detailsLeft, buttonY, 150, 20).build());
            actionButton.active = !isActiveQuest;
        } else if (instance.status() == QuestStatus.FAILED) {
            actionButton = addRenderableWidget(Button.builder(Component.literal("Retry"), b -> retry())
                    .bounds(detailsLeft, buttonY, 150, 20).build());
        }
    }

    private void setActive() {
        if (selectedId == null) {
            return;
        }
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new SetActiveQuestPayload(selectedId)));
        // Optimistic local refresh -- the server-authoritative attachment sync will confirm this
        // shortly after, but rebuilding immediately off the click avoids a visible round-trip delay.
        rebuildListRows();
        rebuildActionButton();
    }

    private void retry() {
        if (selectedId == null) {
            return;
        }
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new RetryQuestPayload(selectedId)));
        rebuildListRows();
        rebuildActionButton();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int listLeft = MARGIN;
        int listRight = width * LIST_WIDTH_FRACTION_NUM / FRACTION_DEN;
        int top = MARGIN + 20;
        int bottom = height - MARGIN;
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= top && mouseY <= bottom) {
            int clickedIndex = (int) ((mouseY - top + listScroll) / ROW_HEIGHT);
            if (clickedIndex >= 0 && clickedIndex < listRows.size()) {
                ListRow row = listRows.get(clickedIndex);
                if (!row.isHeader()) {
                    selectedId = row.definitionId();
                    detailsScroll = 0;
                    rebuildActionButton();
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listRight = width * LIST_WIDTH_FRACTION_NUM / FRACTION_DEN;
        int detailsLeft = listRight + MARGIN;
        if (mouseX <= listRight) {
            int maxScroll = Math.max(0, listRows.size() * ROW_HEIGHT - (height - MARGIN * 2 - 20));
            listScroll = Math.max(0, Math.min(maxScroll, listScroll - (int) (scrollY * ROW_HEIGHT * 2)));
            return true;
        } else if (mouseX >= detailsLeft) {
            detailsScroll = Math.max(0, detailsScroll - (int) (scrollY * ROW_HEIGHT * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int listLeft = MARGIN;
        int listRight = width * LIST_WIDTH_FRACTION_NUM / FRACTION_DEN;
        int detailsLeft = listRight + MARGIN;
        int detailsRight = Math.min(width - MARGIN, detailsLeft + width * DETAILS_WIDTH_FRACTION_NUM / FRACTION_DEN);
        int top = MARGIN + 20;
        int bottom = height - MARGIN;

        graphics.text(font, "Quests", MARGIN, MARGIN, HEADER_COLOR);

        graphics.fill(listLeft, top, listRight, bottom, 0x40000000);
        graphics.enableScissor(listLeft, top, listRight, bottom);
        int y = top - listScroll;
        for (ListRow row : listRows) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                boolean selected = !row.isHeader() && row.definitionId().equals(selectedId);
                if (selected) {
                    graphics.fill(listLeft, y, listRight, y + ROW_HEIGHT, 0x60FFFFFF);
                }
                graphics.text(font, trimToWidth(row.label(), listRight - listLeft - 6), listLeft + 3, y + 3, row.color());
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();

        if (selectedId != null) {
            renderDetails(graphics, detailsLeft, detailsRight, top, bottom);
        }
    }

    private void renderDetails(GuiGraphicsExtractor graphics, int left, int right, int topBound, int bottom) {
        var data = Minecraft.getInstance().player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(selectedId);
        Optional<QuestDefinition> definitionOpt = QuestRegistry.get(selectedId);
        if (instance == null || definitionOpt.isEmpty()) {
            return;
        }
        QuestDefinition definition = definitionOpt.get();
        int top = topBound + 26; // leave room for the pinned action button above.

        graphics.enableScissor(left, top, right, bottom);
        int y = top - detailsScroll;
        int lineHeight = font.lineHeight + 2;
        int wrapWidth = right - left - 6;

        y = drawWrapped(graphics, "ID: " + definition.id(), left, y, wrapWidth, DIM_TEXT_COLOR, lineHeight);
        y = drawWrapped(graphics, definition.name(), left, y, wrapWidth, HEADER_COLOR, lineHeight);
        y += 2;
        y = drawWrapped(graphics, definition.description(), left, y, wrapWidth, TEXT_COLOR, lineHeight);
        y += 4;

        y = drawWrapped(graphics, "Milestones:", left, y, wrapWidth, HEADER_COLOR, lineHeight);
        for (QuestMilestone milestone : definition.milestones()) {
            boolean complete = instance.isMilestoneComplete(milestone);
            String text = "- " + com.github.cerealklla.myssions.quest.QuestText.resolve(milestone.description(), instance) + (milestone.optional() ? " (optional)" : "")
                    + (milestone.requiredCount() > 1 ? " (" + instance.milestoneProgress().getOrDefault(milestone.id(), 0) + "/" + milestone.requiredCount() + ")" : "");
            if (complete) {
                text = strikethrough(text);
            }
            y = drawWrapped(graphics, text, left, y, wrapWidth, complete ? DIM_TEXT_COLOR : TEXT_COLOR, lineHeight);
        }
        y += 4;

        y = drawWrapped(graphics, "Rewards:", left, y, wrapWidth, HEADER_COLOR, lineHeight);
        for (QuestReward reward : definition.rewards()) {
            y = drawWrapped(graphics, "- " + reward.displayLine(), left, y, wrapWidth, TEXT_COLOR, lineHeight);
        }

        if (instance.status() == QuestStatus.FAILED) {
            y += 4;
            drawWrapped(graphics, "This quest was failed.", left, y, wrapWidth, FAILED_TEXT_COLOR, lineHeight);
        }
        graphics.disableScissor();
    }

    private int drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int wrapWidth, int color, int lineHeight) {
        for (String line : wrapToWidth(font, text, wrapWidth)) {
            graphics.text(font, line, x, y, color);
            y += lineHeight;
        }
        return y;
    }

    /** Greedy word-wrap against the font's own measured width -- {@code Font#split} returns formatted
     * sequences that are awkward to get plain strings back out of for this kind of per-line color
     * control, so this wraps words manually instead. */
    private static List<String> wrapToWidth(net.minecraft.client.gui.Font font, String text, int wrapWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) > wrapWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty() || lines.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static String strikethrough(String text) {
        return net.minecraft.ChatFormatting.STRIKETHROUGH + text;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String candidate = result.toString() + text.charAt(i);
            if (font.width(candidate) + ellipsisWidth > maxWidth) {
                break;
            }
            result.append(text.charAt(i));
        }
        return result + "...";
    }
}
