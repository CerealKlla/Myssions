package com.github.cerealklla.myssions.client;

import java.util.Optional;

import com.github.cerealklla.myssions.quest.QuestDefinition;
import com.github.cerealklla.myssions.quest.QuestInstance;
import com.github.cerealklla.myssions.quest.QuestMilestone;
import com.github.cerealklla.myssions.quest.QuestRegistry;
import com.github.cerealklla.myssions.quest.QuestStatus;
import com.github.cerealklla.myssions.quest.QuestText;
import com.github.cerealklla.myssions.quest.QuestTimeLimit;
import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
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
    // Added 2026-10-13, explicit user spec: "it should show the milestones as crossed out successful
    // or failed too." An optional milestone that's still incomplete once the quest itself is done
    // (AWAITING_REWARD_PICKUP/COMPLETED -- no more progress is possible) is struck through in this
    // color instead of DONE_COLOR, so "missed this bonus" reads visually distinct from "completed
    // this bonus" rather than both looking identical to a still-pending milestone.
    private static final int FAILED_COLOR = 0xFFE05050;
    private static final int BACKGROUND_COLOR = 0x80101010;

    // Time-remaining countdown bar (2026-10-13, explicit user spec: "starts Green becomes red...
    // slides from 100% down to 0%"). Only shown for a time-limited quest whose deadline is anchored
    // to game time (GAME_TICKS_FROM_ASSIGNMENT/NEXT_GAME_DAY_BOUNDARY, see QuestTimeLimit#deadlineGameTime's
    // own doc) -- REAL_WORLD_DATE has no stored "started at" real timestamp to normalize a fraction
    // against, and no quest in this mod actually uses that kind today.
    //
    // GAME_TICKS_FROM_ASSIGNMENT specifically now prefers QuestInstance#assignedAtRealEpochMillis
    // when present (added later the same day, real live report: the tick-based version silently
    // pauses for every real minute the server process itself is down) -- see render()'s own comment.
    private static final int BAR_HEIGHT = 4;
    private static final int BAR_GAP_ABOVE = 3;
    private static final int BAR_TRACK_COLOR = 0xFF303030;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.level == null) {
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

        QuestTimeLimit limit = definition.timeLimit();
        boolean showCountdown = limit.hasLimit() && limit.kind() != QuestTimeLimit.Kind.REAL_WORLD_DATE
                && instance.status() == QuestStatus.IN_PROGRESS;
        float remainingFraction = 0f;
        String remainingText = "";
        if (showCountdown) {
            // GAME_TICKS_FROM_ASSIGNMENT prefers a real assignedAtRealEpochMillis when the instance has
            // one (added 2026-10-13, real live report: "this quest remaining time seems to have not
            // decreased while I was away... it's supposed to be clock time, not in-game" -- the tick
            // clock silently pauses for every real minute the server process itself is down, e.g. a
            // deploy restart). Expressed back in "effective ticks" (remaining real millis / 50ms-per-
            // tick) purely so #formatTicks stays the one shared formatter either way.
            long remaining;
            long total;
            if (limit.isRealTimeDuration() && instance.assignedAtRealEpochMillis().isPresent()) {
                long nowMillis = System.currentTimeMillis();
                long deadlineMillis = limit.deadlineRealEpochMillis(instance.assignedAtRealEpochMillis().get());
                total = limit.totalRealMillis() / 50L;
                remaining = (deadlineMillis - nowMillis) / 50L;
            } else {
                long now = minecraft.level.getGameTime();
                long deadline = limit.deadlineGameTime(instance.assignedAtGameTime());
                total = limit.totalGameTicks(instance.assignedAtGameTime());
                remaining = deadline - now;
            }
            remainingFraction = total > 0 ? Mth.clamp((float) remaining / total, 0f, 1f) : 0f;
            remainingText = "Time Remaining: " + formatTicks(Math.max(0, remaining));
        }

        Font font = minecraft.font;
        int maxTextWidth = font.width(definition.name());
        for (QuestMilestone milestone : definition.milestones()) {
            maxTextWidth = Math.max(maxTextWidth, font.width(milestoneLine(milestone, instance)));
        }
        if (showCountdown) {
            maxTextWidth = Math.max(maxTextWidth, font.width(remainingText));
        }
        int lineCount = 1 + definition.milestones().size();
        int left = MARGIN;
        int top = MARGIN;
        int right = left + maxTextWidth + PADDING * 2;
        int bottom = top + font.lineHeight * lineCount + PADDING * 2;
        if (showCountdown) {
            bottom += font.lineHeight + BAR_GAP_ABOVE + BAR_HEIGHT;
        }

        graphics.fill(left, top, right, bottom, BACKGROUND_COLOR);
        int y = top + PADDING;
        graphics.text(font, definition.name(), left + PADDING, y, TITLE_COLOR);
        y += font.lineHeight;
        boolean questDone = instance.status() != QuestStatus.IN_PROGRESS;
        for (QuestMilestone milestone : definition.milestones()) {
            boolean complete = instance.isMilestoneComplete(milestone);
            boolean failed = !complete && questDone && milestone.optional();
            String line = milestoneLine(milestone, instance);
            int color = TEXT_COLOR;
            if (complete) {
                line = ChatFormatting.STRIKETHROUGH + line;
                color = DONE_COLOR;
            } else if (failed) {
                line = ChatFormatting.STRIKETHROUGH + line;
                color = FAILED_COLOR;
            }
            graphics.text(font, line, left + PADDING, y, color);
            y += font.lineHeight;
        }
        if (showCountdown) {
            graphics.text(font, remainingText, left + PADDING, y, TEXT_COLOR);
            y += font.lineHeight + BAR_GAP_ABOVE;
            int barLeft = left + PADDING;
            int barRight = right - PADDING;
            graphics.fill(barLeft, y, barRight, y + BAR_HEIGHT, BAR_TRACK_COLOR);
            int filledRight = barLeft + Math.round((barRight - barLeft) * remainingFraction);
            if (filledRight > barLeft) {
                graphics.fill(barLeft, y, filledRight, y + BAR_HEIGHT, 0xFF000000 | countdownColor(remainingFraction));
            }
        }
    }

    /** Green at full time remaining, sliding to red as the deadline approaches -- plain linear RGB interpolation, no need for anything fancier at this size. */
    private static int countdownColor(float remainingFraction) {
        int red = Math.round((1f - remainingFraction) * 255f);
        int green = Math.round(remainingFraction * 255f);
        return (red << 16) | (green << 8);
    }

    /** "5h 32m" / "47m" / "12s" -- whichever units are actually relevant, never more than two. */
    private static String formatTicks(long ticks) {
        long totalSeconds = ticks / 20L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /**
     * One milestone's HUD line, kept as succinct as possible. First shortened 2026-10-11 (a
     * deliver-type milestone was running off-screen, fixed by moving the target count into a live
     * "(current/required)" suffix instead of spelling it out in prose). Shortened further 2026-10-12
     * (real report: a region-kill milestone's full resolved location phrase -- "near to the
     * northeast of Starterville, in The Boughs" -- ran the whole box into the minimap): {@link
     * QuestText#resolveShort} substitutes just the short real place name once it's actually known,
     * instead of the full direction+name phrase {@code client.QuestLogScreen}'s detail pane still
     * uses. The optional bonus-proof-delivery milestone every region-kill quest has (added
     * 2026-10-11) gets its own even terser line -- "(Optional) Retrieve &lt;item&gt;" instead of
     * "Deliver &lt;item&gt; for bonus gold ... (optional)" -- since its verbose prose form was the
     * other half of the overflow on the exact quest reported.
     */
    private static String milestoneLine(QuestMilestone milestone, QuestInstance instance) {
        String line;
        Optional<Integer> roadExtra = roadEfficiencyExtraStakes(milestone, instance);
        if (isBonusProofMilestone(milestone)) {
            String proofName = milestone.proofId().flatMap(com.github.cerealklla.myssions.quest.ProofItemCatalog::getDisplayName).orElse("Proof");
            line = "- (Optional) Retrieve " + proofName;
        } else if (roadExtra.isPresent()) {
            line = "- Have " + roadExtra.get() + " Extra when done";
        } else {
            line = "- " + QuestText.resolveShort(milestone.description(), instance);
        }
        if (milestone.requiredCount() > 1) {
            line += " (" + instance.milestoneProgress().getOrDefault(milestone.id(), 0) + "/" + milestone.requiredCount() + ")";
        }
        if (milestone.optional() && !isBonusProofMilestone(milestone)) {
            line += " (optional)";
        }
        return line;
    }

    /**
     * The road-connection quest's own three cascading efficiency milestones -- explicit user spec,
     * 2026-10-13: "the milestones need to say 'Have # Extra when done'... and they can pick up
     * unnecessary stakes to be more efficient." Returns how many of the granted Highway Stake stack
     * would still be unused (returned to inventory) if this tier's own stake-count threshold is met,
     * i.e. {@code grantedCount - threshold} -- empty for every other quest's milestones, or a road
     * quest instance with no real thresholds recorded yet (shouldn't normally happen post-acceptance).
     */
    private static Optional<Integer> roadEfficiencyExtraStakes(QuestMilestone milestone, QuestInstance instance) {
        int minStakes = instance.roadQuest().minStakes();
        int optimalCap = instance.roadQuest().optimalStakeCap();
        if (minStakes <= 0 || optimalCap <= 0) {
            return Optional.empty();
        }
        int granted = com.github.cerealklla.myssions.quest.RoadQuestLogic.grantedCountFor(optimalCap);
        int midpoint = Math.round((minStakes + optimalCap) / 2.0f);
        return switch (milestone.id()) {
            case "efficient_road_tier1" -> Optional.of(granted - optimalCap);
            case "efficient_road_tier2" -> Optional.of(granted - midpoint);
            case "efficient_road_tier3" -> Optional.of(granted - minStakes);
            default -> Optional.empty();
        };
    }

    /** The region-kill quest taxonomy's own "bring back proof for bonus gold" milestone -- see {@code QuestHooks#registerRegionKillQuest}. */
    private static boolean isBonusProofMilestone(QuestMilestone milestone) {
        return milestone.isDeliverMilestone() && milestone.optional() && milestone.bonusGoldPerProof() > 0 && milestone.proofId().isPresent();
    }
}
