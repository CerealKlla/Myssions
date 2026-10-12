package com.github.cerealklla.myssions.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.myssions.net.AbandonQuestPayload;
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

/**
 * The Quest Log (design doc item 1) -- opened via {@code MyssionsModClient#OPEN_QUEST_LOG}. Two
 * independently-scrollable columns: a left list (~30% width, quest names, Failed Quests grouped at
 * the bottom per item 6) and a right details pane (~60% width, full quest info + "Set as Active
 * Quest"). A failed quest has no in-UI retry path at all (removed 2026-10-12, explicit user
 * instruction: "I don't want such a capability" -- the only way to get it again is the issuing NPC
 * offering it fresh). Reads its quest list entirely from client-local state -- {@link QuestRegistry}
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

    private record ListRow(UUID instanceId, String label, int color, boolean isHeader) {
    }

    private List<ListRow> listRows = List.of();
    private UUID selectedId;
    private int listScroll;
    private int detailsScroll;
    private Button actionButton;
    private Button abandonButton;

    // Set the instant "Set as Active Quest" is clicked, read in place of the (possibly still-stale)
    // synced attachment until it catches up -- fixes a real 2026-10-11 user report ("I have to click
    // the Make Active Quest 2x before it actually clicks"). Root cause: setActive() used to rebuild
    // the list/button by re-reading the client's own QUEST_DATA attachment immediately after sending
    // the request packet, but that attachment only updates once the server's resync round-trips back
    // -- so the first click silently appeared to do nothing (button stayed enabled, label unchanged)
    // even though it had already taken effect server-side, and the user's second click was actually
    // a redundant repeat of the first that just happened to land after the real sync caught up.
    private UUID optimisticActiveQuestId;

    // Same stale-attachment race as optimisticActiveQuestId above, for "Abandon Quest" instead of
    // "Set as Active Quest" -- real 2026-10-12 report: an abandoned quest stayed visible in the list
    // (clicking it showed a blank details pane once the real sync eventually landed). Root cause:
    // abandon() used to rebuild the list by re-reading the still-stale client attachment immediately
    // after sending the request packet, so the just-abandoned instance's row was regenerated right
    // back into the list; nothing ever re-rebuilds the list later when the real resync actually
    // arrives, so the stale row just sat there until the screen was closed and reopened. Every id
    // added here is filtered out of rebuildListRows() regardless of what the (possibly still-stale)
    // attachment says -- never needs clearing, since this whole screen instance is discarded and
    // recreated fresh each time the Quest Log is reopened.
    private final java.util.Set<UUID> optimisticallyAbandoned = new java.util.HashSet<>();

    public QuestLogScreen() {
        super(Component.literal("Quests"));
    }

    @Override
    protected void init() {
        rebuildListRows();
        if (selectedId == null && !listRows.isEmpty()) {
            listRows.stream().filter(r -> !r.isHeader()).findFirst().ifPresent(r -> selectedId = r.instanceId());
        }
        rebuildActionButton();
    }

    /** The effective active-quest id for rendering purposes -- prefers a just-clicked optimistic
     * value over the real synced attachment until that sync catches up and matches it. See {@link
     * #optimisticActiveQuestId}'s own doc. */
    private UUID effectiveActiveQuestId(com.github.cerealklla.myssions.quest.PlayerQuestData data) {
        if (optimisticActiveQuestId != null) {
            if (data.activeQuestId().map(optimisticActiveQuestId::equals).orElse(false)) {
                // The real sync has caught up -- drop the optimistic override, the authoritative
                // value now agrees with it anyway.
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
        List<QuestInstance> completed = new ArrayList<>();
        List<QuestInstance> failed = new ArrayList<>();
        for (QuestInstance instance : data.quests().values()) {
            if (optimisticallyAbandoned.contains(instance.instanceId())) {
                continue;
            }
            switch (instance.status()) {
                case FAILED -> failed.add(instance);
                // Grouped to the bottom with Failed, 2026-10-11 user request -- previously mixed in
                // with everything else in insertion order instead of standing out as "done."
                case COMPLETED -> completed.add(instance);
                default -> active.add(instance);
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

    private void rebuildActionButton() {
        if (actionButton != null) {
            removeWidget(actionButton);
            actionButton = null;
        }
        if (abandonButton != null) {
            removeWidget(abandonButton);
            abandonButton = null;
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
        if (instance.status() == QuestStatus.IN_PROGRESS || instance.status() == QuestStatus.AWAITING_REWARD_PICKUP) {
            boolean isActiveQuest = selectedId.equals(effectiveActiveQuestId(data));
            actionButton = addRenderableWidget(Button.builder(
                            Component.literal(isActiveQuest ? "Active Quest" : "Set as Active Quest"),
                            b -> setActive())
                    .bounds(detailsLeft, buttonY, 150, 20).build());
            actionButton.active = !isActiveQuest;
            // Only while genuinely IN_PROGRESS, 2026-10-11 explicit request -- an AWAITING_REWARD_PICKUP
            // instance already has a real reward waiting; abandoning it would just throw that away with
            // no warning beyond the confirm dialog, and QuestEngine#abandonQuest itself only accepts
            // IN_PROGRESS anyway, so don't offer a button that would silently no-op.
            if (instance.status() == QuestStatus.IN_PROGRESS) {
                abandonButton = addRenderableWidget(Button.builder(Component.literal("Abandon Quest"), b -> confirmAbandon())
                        .bounds(detailsLeft + 160, buttonY, 150, 20).build());
            }
        }
        // A FAILED instance gets no action button at all -- no in-UI retry, see this class's own doc.
    }

    private void setActive() {
        if (selectedId == null) {
            return;
        }
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new SetActiveQuestPayload(selectedId)));
        // Real optimistic update (2026-10-11 fix, see optimisticActiveQuestId's own doc) -- the
        // previous version's comment claimed this already happened, but it only re-read the same
        // still-stale attachment, so the button never visually updated until the server's resync
        // round-tripped back, making the first click look like it had done nothing.
        optimisticActiveQuestId = selectedId;
        rebuildListRows();
        rebuildActionButton();
    }

    /** "Abandon Quest" -- confirms first via vanilla's own {@code ConfirmScreen} (same dialog type
     * used for e.g. "Delete World") since this is a real, irreversible deletion, not a status change
     * (2026-10-11 correction: abandoning now fully deletes the instance -- see {@code
     * QuestEngine#abandonQuest}'s own doc -- rather than parking it in Failed Quests/Retry). */
    private void confirmAbandon() {
        if (selectedId == null) {
            return;
        }
        UUID instanceId = selectedId;
        var data = Minecraft.getInstance().player.getData(ModAttachments.QUEST_DATA);
        QuestInstance instance = data.quests().get(instanceId);
        String questName = instance == null ? "this quest"
                : QuestRegistry.get(instance.definitionId()).map(QuestDefinition::name).orElse("this quest");
        Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                confirmed -> {
                    Minecraft.getInstance().setScreen(this);
                    if (confirmed) {
                        abandon(instanceId);
                    }
                },
                Component.literal("Abandon Quest"),
                Component.literal("Permanently give up on \"" + questName + "\"? This can't be undone, but you'll be free to accept it (or anything else) again.")));
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
                    selectedId = row.instanceId();
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
                boolean selected = !row.isHeader() && row.instanceId().equals(selectedId);
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

        // No id line here on purpose -- both the definition id and the real per-instance UUID are
        // internal plumbing, never shown to the player (explicit user correction, 2026-10-10: "The
        // Unique ID was meant to be ... hidden from the user").
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
        // A buy-item milestone's real reward is a per-instance resolved gold amount (the item's own
        // vendor price + 15%, set at acceptance) -- never part of the static definition.rewards()
        // list below, which for this quest kind is flavor text only. See QuestText's own doc (real
        // user report, 2026-10-12: the list showed only that flavor line, no real reward at all).
        if (definition.milestones().stream().anyMatch(QuestMilestone::isBuyMilestone)) {
            String line = com.github.cerealklla.myssions.quest.QuestText.resolveBuyItemGoldReward(instance)
                    .map(gold -> gold + " Gold Nugget" + (gold == 1 ? "" : "s"))
                    .orElse("Gold Nuggets (amount set once accepted)");
            y = drawWrapped(graphics, "- " + line, left, y, wrapWidth, TEXT_COLOR, lineHeight);
        }
        // Same gap for a gather milestone's real reward -- goldPerUnit * requiredCount is already
        // granted on completion (QuestEngine#addGatherReward), but both values are static per
        // definition (unlike a buy milestone's per-instance resolve), so no instance lookup is
        // needed here at all.
        for (QuestMilestone milestone : definition.milestones()) {
            if (milestone.isGatherMilestone() && milestone.bonusGoldPerProof() > 0) {
                int gold = milestone.bonusGoldPerProof() * milestone.requiredCount();
                String line = gold + " Gold Nugget" + (gold == 1 ? "" : "s");
                y = drawWrapped(graphics, "- " + line, left, y, wrapWidth, TEXT_COLOR, lineHeight);
            }
        }
        for (QuestReward reward : definition.rewards()) {
            y = drawWrapped(graphics, "- " + reward.displayLine(), left, y, wrapWidth, TEXT_COLOR, lineHeight);
        }

        if (instance.status() == QuestStatus.FAILED) {
            y += 4;
            drawWrapped(graphics, "This quest was failed.", left, y, wrapWidth, FAILED_TEXT_COLOR, lineHeight);
        } else if (instance.status() == QuestStatus.AWAITING_REWARD_PICKUP) {
            y += 4;
            drawWrapped(graphics, "Done -- talk to the issuer to collect your reward.", left, y, wrapWidth, ACTIVE_TEXT_COLOR, lineHeight);
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
