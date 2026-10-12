package com.github.cerealklla.myssions.quest;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import com.github.cerealklla.myssions.MyssionsMod;
import com.github.cerealklla.myssions.bridge.LyfeWaypointBridge;
import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Writes one plain-text file per in-game "Report Bug" submission (explicit user request,
 * 2026-10-12: a button on a selected quest in {@code client.QuestLogScreen} opening a text-entry
 * screen, bundling the player's own written description together with "all the quest details and
 * their player knowledge related to the quest... and anything else [helpful] for debugging"). Plain
 * files rather than a {@code SavedData}/world-save structure on purpose -- this is a one-off,
 * human-read artifact meant to be opened directly in a text editor while triaging a report, the same
 * reasoning {@code ReleaseNotes.md}/{@code workHistory.md} already follow, not state the game itself
 * needs to read back.
 *
 * <p>Deliberately dumps everything {@link QuestInstance}/{@link QuestDefinition} carry rather than a
 * hand-picked subset -- a bug report is exactly the situation where an "unimportant" field (a raw
 * hint position, a reward multiplier, whether mobs were ever topped up) can turn out to be the one
 * fact that explains the report, and this is cheap to generate either way.
 *
 * <p><b>Triage workflow (explicit user request, 2026-10-12)</b>: a fresh report lands directly in
 * {@code myssions/bugreports/}. Once its underlying bug is actually fixed and deployed, move the
 * file into {@code myssions/bugreports/fixed/} and prepend a short {@code === FIXED ===} section
 * naming the Build number it shipped in and a one-line root-cause summary (see that first subfolder
 * entry, from this feature's own first real submission, for the exact shape) -- this keeps the top-
 * level folder showing only reports still genuinely open, while the fixed ones stay around as a
 * dated record of what each report actually turned out to be. Purely a manual filing step done
 * alongside the normal fix-and-deploy work, not something this class itself automates.
 *
 * <p><b>Thanking the reporter (explicit user request, same day)</b> -- the reporter's identity is
 * already captured above (name + UUID in the "Reporter" section), so no extra plumbing was needed
 * for that part. The reward itself is Gold Nuggets, delivered via Yconomics' existing {@code
 * /yconomics purse add <count> <player>} debug command (requires the target to be online -- no
 * offline-queued delivery exists). The actual trigger/amount/automation for this is still an open
 * design question (explicit user answer: "I'll figure that out later") -- for now, reward manually
 * via that command as part of the same triage pass that marks a report fixed, once the reporter is
 * next seen online.
 */
public final class BugReportWriter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private BugReportWriter() {
    }

    public static void write(ServerPlayer player, QuestInstance instance, QuestDefinition definition, String reportText) {
        Path dir = FMLPaths.GAMEDIR.get().resolve("myssions").resolve("bugreports");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            MyssionsMod.LOGGER.error("Failed to create bug report directory {}", dir, e);
            return;
        }

        String timestamp = TIMESTAMP_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault()));
        String fileName = timestamp + "_" + sanitize(player.getName().getString()) + "_" + sanitize(definition.id().getPath()) + ".txt";
        Path file = dir.resolve(fileName);

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writeReport(writer, player, instance, definition, reportText);
        } catch (IOException e) {
            MyssionsMod.LOGGER.error("Failed to write bug report {}", file, e);
            return;
        }
        MyssionsMod.LOGGER.info("Bug report written: {}", file);
    }

    private static void writeReport(Writer out, ServerPlayer player, QuestInstance instance, QuestDefinition definition, String reportText) throws IOException {
        line(out, "=== Player Report ===");
        line(out, reportText.isBlank() ? "(no description entered)" : reportText);
        line(out, "");

        line(out, "=== Reporter ===");
        line(out, "Player: " + player.getName().getString() + " (" + player.getUUID() + ")");
        line(out, "Current position: " + player.blockPosition() + " in " + player.level().dimension().identifier());
        line(out, "Server time (game ticks): " + player.level().getGameTime());
        line(out, "");

        line(out, "=== Quest Definition ===");
        line(out, "Id: " + definition.id());
        line(out, "Name: " + definition.name());
        line(out, "Description: " + definition.description());
        line(out, "Milestones:");
        for (QuestMilestone milestone : definition.milestones()) {
            line(out, "  - id=" + milestone.id()
                    + " text=\"" + milestone.description() + "\""
                    + " requiredCount=" + milestone.requiredCount()
                    + " optional=" + milestone.optional()
                    + " nightOnly=" + milestone.nightOnly()
                    + " killTargetEntityType=" + milestone.killTargetEntityType()
                    + " proofId=" + milestone.proofId()
                    + " isGatherMilestone=" + milestone.isGatherMilestone()
                    + " isBuyMilestone=" + milestone.isBuyMilestone()
                    + " isEscortMilestone=" + milestone.isEscortMilestone()
                    + " isPlotUpgradeMilestone=" + milestone.isPlotUpgradeMilestone());
        }
        line(out, "Rewards: " + definition.rewards());
        line(out, "");

        line(out, "=== Quest Instance ===");
        line(out, "Instance id: " + instance.instanceId());
        line(out, "Status: " + instance.status());
        line(out, "Assigned at game time: " + instance.assignedAtGameTime());
        line(out, "Milestone progress: " + instance.milestoneProgress());
        line(out, "Resolved milestone text:");
        for (QuestMilestone milestone : definition.milestones()) {
            line(out, "  - " + milestone.id() + ": \"" + QuestText.resolve(milestone.description(), instance) + "\"");
        }
        line(out, "Reward multiplier: " + instance.rewardMultiplier());
        line(out, "Mobs spawned: " + instance.mobsSpawned());
        line(out, "Hint position: " + instance.hintPos() + " (dimension " + instance.hintDimensionId() + ")");
        line(out, "Location name (currently displayed): " + instance.locationName());
        line(out, "Knowledge place id: " + instance.knowledgePlaceId());
        line(out, "Real location name (ground truth): " + instance.realLocationName());
        if (instance.escort() != QuestInstance.EscortData.NONE) {
            line(out, "Escort data: entityUuid=" + instance.escort().entityUuid() + " remainingRoute=" + instance.escort().remainingRoute());
        }
        if (instance.roadQuest() != QuestInstance.RoadQuestData.NONE) {
            line(out, "Road quest data: originPlaceId=" + instance.roadQuest().originPlaceId()
                    + " minStakes=" + instance.roadQuest().minStakes() + " optimalStakeCap=" + instance.roadQuest().optimalStakeCap());
        }
        line(out, "");

        line(out, "=== Player Knowledge of this Quest's Location ===");
        if (instance.knowledgePlaceId().isEmpty()) {
            line(out, "(this quest has no real Cartographyr-backed location -- nothing to report)");
        } else if (!LyfeWaypointBridge.isLoaded()) {
            line(out, "(Lyfe isn't loaded -- no knowledge data available)");
        } else {
            long placeId = instance.knowledgePlaceId().get();
            int knownFactors = LyfeWaypointBridge.knownFactorCount(player, placeId);
            line(out, "Known factors: " + knownFactors + "/3");
            if (instance.hintPos().isPresent()) {
                String resolved = LyfeWaypointBridge.resolveLocationText(player, placeId, instance.realLocationName().orElse("?"), instance.hintPos().get());
                line(out, "Live-resolved location text right now: \"" + resolved + "\"");
            }
        }
        line(out, "");

        line(out, "=== All In-Progress/Awaiting-Reward Quests for This Player (context) ===");
        var allInstances = player.getData(ModAttachments.QUEST_DATA).quests();
        List<QuestInstance> others = allInstances.values().stream()
                .filter(i -> !i.instanceId().equals(instance.instanceId()))
                .filter(i -> i.status() == QuestStatus.IN_PROGRESS || i.status() == QuestStatus.AWAITING_REWARD_PICKUP)
                .toList();
        if (others.isEmpty()) {
            line(out, "(none)");
        } else {
            for (QuestInstance other : others) {
                Optional<QuestDefinition> otherDef = QuestRegistry.get(other.definitionId());
                line(out, "  - " + otherDef.map(QuestDefinition::name).orElse(other.definitionId().toString()) + " (" + other.status() + ")");
            }
        }
    }

    private static void line(Writer out, String text) throws IOException {
        out.write(text);
        out.write(System.lineSeparator());
    }

    private static String sanitize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
