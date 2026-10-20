package com.github.cerealklla.myssions.menu.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.myssions.client.BugReportScreen;
import com.github.cerealklla.myssions.net.AbandonQuestPayload;
import com.github.cerealklla.myssions.net.SetActiveQuestPayload;
import com.github.cerealklla.myssions.quest.QuestDefinition;
import com.github.cerealklla.myssions.quest.QuestInstance;
import com.github.cerealklla.myssions.quest.QuestMilestone;
import com.github.cerealklla.myssions.quest.QuestRegistry;
import com.github.cerealklla.myssions.quest.QuestReward;
import com.github.cerealklla.myssions.quest.QuestStatus;
import com.github.cerealklla.myssions.registration.ModAttachments;
import com.github.cerealklla.nuclyus.client.TextWrap;
import com.github.cerealklla.nuclyus.menu.client.NuclyusMenu;
import com.github.cerealklla.nuclyus.menu.client.NuclyusMenuScreen;
import com.github.cerealklla.nuclyus.menu.client.WidgetHost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * "Quests" tab -- direct port of the former standalone {@code client.QuestLogScreen} into the
 * shared Nuclyus menu, 2026-10-17 (see the root project's own CLAUDE.md for the menu-consolidation
 * feature). Behavior unchanged -- see that class's own removed doc for the full design rationale
 * (two independently-scrollable columns, read entirely from the already-synced {@code
 * quest.PlayerQuestData} attachment, no network round trip needed just to open). {@code
 * client.QuestLogScreen} itself is now a thin host that constructs this same tab with itself as
 * the {@link #attachHost} target, keeping the standalone "J" keybind working unchanged.
 *
 * <p>The one real mechanical difference from being a tab instead of a top-level {@code Screen}:
 * every absolute coordinate comes from the injected {@code area} rect, not this class's own
 * (nonexistent) {@code width}/{@code height} fields, and the action/abandon/report-bug buttons are
 * added onto whatever {@link #attachHost}'s host {@code Screen} is, not {@code this} (which isn't
 * a {@code Screen} at all). {@code confirmAbandon()}'s {@code ConfirmScreen} and the "Report Bug"
 * button's {@code BugReportScreen} both restore that same host reference on close/cancel -- for
 * Nuclyus's shared menu, this is the same {@code NuclyusMenuScreen} instance, which resumes exactly
 * where it was (see that class's own doc for the real re-init state-loss bug this surfaced and
 * fixed); for the standalone "J" path, it's {@code QuestLogScreen} itself, unchanged from before.
 */
public final class QuestLogTab implements NuclyusMenu {

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

    private record ListRow(UUID instanceId, String label, int color, boolean isHeader) {
    }

    private Screen host;
    private List<ListRow> listRows = List.of();
    private UUID selectedId;
    private int listScroll;
    private int detailsScroll;
    private Button actionButton;
    private Button abandonButton;
    private Button reportBugButton;

    // See QuestLogScreen's own removed doc -- fixes the "click Set Active Quest twice" race against
    // the still-stale synced attachment.
    private UUID optimisticActiveQuestId;
    private final java.util.Set<UUID> optimisticallyAbandoned = new java.util.HashSet<>();

    @Override
    public Component title() {
        return Component.literal("Quests");
    }

    @Override
    public int order() {
        return 2;
    }

    @Override
    public void attachHost(Screen host) {
        this.host = host;
    }

    private Rect2i area() {
        return host instanceof NuclyusMenuScreen hostScreen
                ? hostScreen.contentArea()
                : new Rect2i(0, 0, host.width, host.height);
    }

    @Override
    public void onShown() {
        rebuildListRows();
        if (selectedId == null && !listRows.isEmpty()) {
            listRows.stream().filter(r -> !r.isHeader()).findFirst().ifPresent(r -> selectedId = r.instanceId());
        }
        rebuildActionButton();
    }

    @Override
    public void onHidden() {
        removeButtons();
    }

    private UUID effectiveActiveQuestId(com.github.cerealklla.myssions.quest.PlayerQuestData data) {
        if (optimisticActiveQuestId != null) {
            if (data.activeQuestId().map(optimisticActiveQuestId::equals).orElse(false)) {
                optimisticActiveQuestId = null;
            } else {
                return optimisticActiveQuestId;
            }
        }
        return data.activeQuestId().orElse(null);
    }

    private void rebuildListRows() {
        var data = Minecraft.getInstance().player.getData(ModAttachments.QUEST_DATA);
        UUID activeId = effectiveActiveQuestId(data);
        List<ListRow> rows = new ArrayList<>();
        List<QuestInstance> active = new ArrayList<>();
        List<QuestInstance> specialAssignments = new ArrayList<>();
        List<QuestInstance> completed = new ArrayList<>();
        List<QuestInstance> failed = new ArrayList<>();
        for (QuestInstance instance : data.quests().values()) {
            if (optimisticallyAbandoned.contains(instance.instanceId())) {
                continue;
            }
            switch (instance.status()) {
                case FAILED -> failed.add(instance);
                case COMPLETED -> completed.add(instance);
                default -> {
                    boolean isSpecial = QuestRegistry.get(instance.definitionId())
                            .map(QuestDefinition::isSpecialAssignment).orElse(false);
                    (isSpecial ? specialAssignments : active).add(instance);
                }
            }
        }
        for (QuestInstance instance : active) {
            QuestRegistry.get(instance.definitionId()).ifPresent(def -> {
                boolean isActiveQuest = instance.instanceId().equals(activeId);
                String prefix = instance.status() == QuestStatus.AWAITING_REWARD_PICKUP ? "[Ready] " : (isActiveQuest ? "* " : "");
                int color = isActiveQuest ? ACTIVE_TEXT_COLOR : TEXT_COLOR;
                rows.add(new ListRow(instance.instanceId(), prefix + def.name(), color, false));
            });
        }
        if (!specialAssignments.isEmpty()) {
            rows.add(new ListRow(null, "-- Mayor's Special Assignments --", HEADER_COLOR, true));
            for (QuestInstance instance : specialAssignments) {
                QuestRegistry.get(instance.definitionId()).ifPresent(def -> {
                    boolean isActiveQuest = instance.instanceId().equals(activeId);
                    String prefix = instance.status() == QuestStatus.AWAITING_REWARD_PICKUP ? "[Ready] " : (isActiveQuest ? "* " : "");
                    int color = isActiveQuest ? ACTIVE_TEXT_COLOR : TEXT_COLOR;
                    rows.add(new ListRow(instance.instanceId(), prefix + def.name(), color, false));
                });
            }
        }
        if (!completed.isEmpty()) {
            rows.add(new ListRow(null, "-- Completed Quests --", HEADER_COLOR, true));
            for (QuestInstance instance : completed) {
                QuestRegistry.get(instance.definitionId())
                        .ifPresent(def -> rows.add(new ListRow(instance.instanceId(), "[Done] " + def.name(), DIM_TEXT_COLOR, false)));
            }
        }
        if (!failed.isEmpty()) {
            rows.add(new ListRow(null, "-- Failed Quests --", HEADER_COLOR, true));
            for (QuestInstance instance : failed) {
                QuestRegistry.get(instance.definitionId())
                        .ifPresent(def -> rows.add(new ListRow(instance.instanceId(), def.name(), FAILED_TEXT_COLOR, false)));
            }
        }
        listRows = rows;
    }

    private void removeButtons() {
        if (!(host instanceof WidgetHost widgetHost)) {
            return;
        }
        if (actionButton != null) {
            widgetHost.removeMenuWidget(actionButton);
            actionButton = null;
        }
        if (abandonButton != null) {
            widgetHost.removeMenuWidget(abandonButton);
            abandonButton = null;
        }
        if (reportBugButton != null) {
            widgetHost.removeMenuWidget(reportBugButton);
            reportBugButton = null;
        }
    }

    private void rebuildActionButton() {
        removeButtons();
        if (selectedId == null || !(host instanceof WidgetHost widgetHost)) {
            return;
        }
        var data = Minecraft.getInstance().player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(selectedId);
        if (instance == null) {
            return;
        }
        Rect2i area = area();
        int detailsLeft = area.getX() + area.getWidth() * LIST_WIDTH_FRACTION_NUM / FRACTION_DEN + MARGIN * 2;
        int buttonY = area.getY() + MARGIN;
        UUID instanceId = selectedId;
        String questName = QuestRegistry.get(instance.definitionId()).map(QuestDefinition::name).orElse("this quest");

        record Spec(String label, int preferredWidth, Runnable action) {
        }
        List<Spec> specs = new ArrayList<>();
        if (instance.status() == QuestStatus.IN_PROGRESS || instance.status() == QuestStatus.AWAITING_REWARD_PICKUP) {
            boolean isActiveQuest = selectedId.equals(effectiveActiveQuestId(data));
            specs.add(new Spec(isActiveQuest ? "Active Quest" : "Set as Active Quest", 150, this::setActive));
            // Widened 2026-10-19 (real report: a despawned issuing NPC left an AWAITING_REWARD_PICKUP
            // quest with no way to ever collect its reward AND no way to abandon it either, since this
            // was IN_PROGRESS-only) -- see QuestEngine#abandonQuest's own doc for the matching
            // server-side widening this button now relies on.
            specs.add(new Spec("Abandon Quest", 150, this::confirmAbandon));
        }
        specs.add(new Spec("Report Bug", 100, () -> Minecraft.getInstance().setScreen(new BugReportScreen(host, instanceId, questName))));

        int available = Math.max(60, area.getX() + area.getWidth() - MARGIN - detailsLeft);
        int gap = 10;
        int preferredTotal = specs.stream().mapToInt(Spec::preferredWidth).sum() + gap * (specs.size() - 1);
        double scale = preferredTotal > available ? (double) (available - gap * (specs.size() - 1)) / specs.stream().mapToInt(Spec::preferredWidth).sum() : 1.0;
        scale = Math.max(0.0, scale);
        int minWidth = 50;
        int x = detailsLeft;
        List<Button> built = new ArrayList<>();
        for (Spec spec : specs) {
            int w = Math.max(minWidth, (int) Math.floor(spec.preferredWidth() * scale));
            Button button = widgetHost.addMenuWidget(Button.builder(Component.literal(spec.label()), b -> spec.action().run())
                    .bounds(x, buttonY, w, 20).build());
            built.add(button);
            x += w + gap;
        }
        if (!specs.isEmpty() && (instance.status() == QuestStatus.IN_PROGRESS || instance.status() == QuestStatus.AWAITING_REWARD_PICKUP)) {
            actionButton = built.get(0);
            actionButton.active = !selectedId.equals(effectiveActiveQuestId(data));
            if (specs.size() > 1 && specs.get(1).label().equals("Abandon Quest")) {
                abandonButton = built.get(1);
            }
        }
        reportBugButton = built.get(built.size() - 1);
    }

    private void setActive() {
        if (selectedId == null) {
            return;
        }
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new SetActiveQuestPayload(selectedId)));
        optimisticActiveQuestId = selectedId;
        rebuildListRows();
        rebuildActionButton();
    }

    private void confirmAbandon() {
        if (selectedId == null) {
            return;
        }
        UUID instanceId = selectedId;
        var data = Minecraft.getInstance().player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        String questName = instance == null ? "this quest"
                : QuestRegistry.get(instance.definitionId()).map(QuestDefinition::name).orElse("this quest");
        // Reward-forfeit wording added 2026-10-19 alongside widening this button to
        // AWAITING_REWARD_PICKUP (see QuestEngine#abandonQuest's own doc) -- a player in that state
        // has an already-earned reward sitting uncollected, which this action forfeits outright.
        String rewardWarning = instance != null && instance.status() == QuestStatus.AWAITING_REWARD_PICKUP
                ? " You'll also forfeit the reward you already earned." : "";
        Minecraft.getInstance().setScreen(new ConfirmScreen(
                confirmed -> {
                    Minecraft.getInstance().setScreen(host);
                    if (confirmed) {
                        abandon(instanceId);
                    }
                },
                Component.literal("Abandon Quest"),
                Component.literal("Permanently give up on \"" + questName + "\"? This can't be undone, but you'll be free to accept it (or anything else) again." + rewardWarning)));
    }

    private void abandon(UUID instanceId) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new AbandonQuestPayload(instanceId)));
        optimisticallyAbandoned.add(instanceId);
        if (instanceId.equals(selectedId)) {
            selectedId = null;
        }
        rebuildListRows();
        if (selectedId == null && !listRows.isEmpty()) {
            listRows.stream().filter(r -> !r.isHeader()).findFirst().ifPresent(r -> selectedId = r.instanceId());
        }
        rebuildActionButton();
    }

    @Override
    public boolean mouseClicked(Rect2i area, MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int listLeft = area.getX() + MARGIN;
        int listRight = area.getX() + area.getWidth() * LIST_WIDTH_FRACTION_NUM / FRACTION_DEN;
        int top = area.getY() + MARGIN;
        int bottom = area.getY() + area.getHeight() - MARGIN;
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= top && mouseY <= bottom) {
            int clickedIndex = (int) ((mouseY - top + listScroll) / ROW_HEIGHT);
            if (clickedIndex >= 0 && clickedIndex < listRows.size()) {
                ListRow row = listRows.get(clickedIndex);
                if (!row.isHeader()) {
                    selectedId = row.instanceId();
                    detailsScroll = 0;
                    rebuildActionButton();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(Rect2i area, double mouseX, double mouseY, double scrollX, double scrollY) {
        int listRight = area.getX() + area.getWidth() * LIST_WIDTH_FRACTION_NUM / FRACTION_DEN;
        int detailsLeft = listRight + MARGIN;
        if (mouseX <= listRight) {
            int maxScroll = Math.max(0, listRows.size() * ROW_HEIGHT - (area.getHeight() - MARGIN * 2));
            listScroll = Math.max(0, Math.min(maxScroll, listScroll - (int) (scrollY * ROW_HEIGHT * 2)));
            return true;
        } else if (mouseX >= detailsLeft) {
            detailsScroll = Math.max(0, detailsScroll - (int) (scrollY * ROW_HEIGHT * 2));
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Rect2i area, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int listLeft = area.getX() + MARGIN;
        int listRight = area.getX() + area.getWidth() * LIST_WIDTH_FRACTION_NUM / FRACTION_DEN;
        int detailsLeft = listRight + MARGIN;
        int detailsRight = Math.min(area.getX() + area.getWidth() - MARGIN, detailsLeft + area.getWidth() * DETAILS_WIDTH_FRACTION_NUM / FRACTION_DEN);
        int top = area.getY() + MARGIN;
        int bottom = area.getY() + area.getHeight() - MARGIN;

        graphics.fill(listLeft, top, listRight, bottom, 0x40000000);
        graphics.enableScissor(listLeft, top, listRight, bottom);
        int y = top - listScroll;
        for (ListRow row : listRows) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                boolean selected = !row.isHeader() && row.instanceId().equals(selectedId);
                if (selected) {
                    graphics.fill(listLeft, y, listRight, y + ROW_HEIGHT, 0x60FFFFFF);
                }
                graphics.text(font, Component.literal(trimToWidth(font, row.label(), listRight - listLeft - 6)), listLeft + 3, y + 3, row.color(), false);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();

        if (selectedId != null) {
            renderDetails(graphics, font, detailsLeft, detailsRight, top, bottom);
        }
    }

    private void renderDetails(GuiGraphicsExtractor graphics, Font font, int left, int right, int topBound, int bottom) {
        var data = Minecraft.getInstance().player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(selectedId);
        Optional<QuestDefinition> definitionOpt = instance == null ? Optional.empty() : QuestRegistry.get(instance.definitionId());
        if (instance == null || definitionOpt.isEmpty()) {
            return;
        }
        QuestDefinition definition = definitionOpt.get();
        int top = topBound + 26; // leave room for the pinned action button above.

        graphics.enableScissor(left, top, right, bottom);
        int y = top - detailsScroll;
        int lineHeight = font.lineHeight + 2;
        int wrapWidth = right - left - 6;

        y = drawWrapped(graphics, font, definition.name(), left, y, wrapWidth, HEADER_COLOR, lineHeight);
        y += 2;
        y = drawWrapped(graphics, font, definition.description(), left, y, wrapWidth, TEXT_COLOR, lineHeight);
        y += 4;

        y = drawWrapped(graphics, font, "Milestones:", left, y, wrapWidth, HEADER_COLOR, lineHeight);
        for (QuestMilestone milestone : definition.milestones()) {
            boolean complete = instance.isMilestoneComplete(milestone);
            String text = "- " + com.github.cerealklla.myssions.quest.QuestText.resolve(milestone.description(), instance) + (milestone.optional() ? " (optional)" : "")
                    + (milestone.requiredCount() > 1 ? " (" + instance.milestoneProgress().getOrDefault(milestone.id(), 0) + "/" + milestone.requiredCount() + ")" : "");
            if (complete) {
                text = strikethrough(text);
            }
            y = drawWrapped(graphics, font, text, left, y, wrapWidth, complete ? DIM_TEXT_COLOR : TEXT_COLOR, lineHeight);
        }
        y += 4;

        y = drawWrapped(graphics, font, "Rewards:", left, y, wrapWidth, HEADER_COLOR, lineHeight);
        if (definition.milestones().stream().anyMatch(QuestMilestone::isBuyMilestone)) {
            String line = com.github.cerealklla.myssions.quest.QuestText.resolveBuyItemGoldReward(instance)
                    .map(gold -> gold + " Gold Nugget" + (gold == 1 ? "" : "s"))
                    .orElse("Gold Nuggets (amount set once accepted)");
            y = drawWrapped(graphics, font, "- " + line, left, y, wrapWidth, TEXT_COLOR, lineHeight);
        }
        for (QuestMilestone milestone : definition.milestones()) {
            if (milestone.isGatherMilestone() && milestone.bonusGoldPerProof() > 0) {
                int gold = milestone.bonusGoldPerProof() * milestone.requiredCount();
                String line = gold + " Gold Nugget" + (gold == 1 ? "" : "s");
                y = drawWrapped(graphics, font, "- " + line, left, y, wrapWidth, TEXT_COLOR, lineHeight);
            }
        }
        for (QuestReward reward : definition.rewards()) {
            y = drawWrapped(graphics, font, "- " + reward.displayLine(), left, y, wrapWidth, TEXT_COLOR, lineHeight);
        }

        if (instance.status() == QuestStatus.FAILED) {
            y += 4;
            drawWrapped(graphics, font, "This quest was failed.", left, y, wrapWidth, FAILED_TEXT_COLOR, lineHeight);
        } else if (instance.status() == QuestStatus.AWAITING_REWARD_PICKUP) {
            y += 4;
            drawWrapped(graphics, font, "Done -- talk to the issuer to collect your reward.", left, y, wrapWidth, ACTIVE_TEXT_COLOR, lineHeight);
        }
        graphics.disableScissor();
    }

    private int drawWrapped(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int wrapWidth, int color, int lineHeight) {
        for (String line : TextWrap.wrap(font, text, wrapWidth)) {
            graphics.text(font, Component.literal(line), x, y, color, false);
            y += lineHeight;
        }
        return y;
    }

    private static String strikethrough(String text) {
        return net.minecraft.ChatFormatting.STRIKETHROUGH + text;
    }

    private static String trimToWidth(Font font, String text, int maxWidth) {
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
