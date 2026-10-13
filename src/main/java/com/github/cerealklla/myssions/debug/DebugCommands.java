package com.github.cerealklla.myssions.debug;

import com.mojang.brigadier.CommandDispatcher;

import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.myssions.quest.SampleQuests;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** DEBUG ONLY -- deliberately no permission gate, same precedent as every other sibling mod's own {@code debug.DebugCommands}. */
public final class DebugCommands {

    private DebugCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("myssions")
                .requires(source -> true)
                .then(Commands.literal("givetest")
                        .executes(ctx -> giveTestQuest(ctx.getSource())))
                .then(Commands.literal("fixplotupgrade")
                        .executes(ctx -> fixPlotUpgrade(ctx.getSource()))));
    }

    /**
     * One-off remediation for the real bug fixed 2026-10-13 (see {@code quest.QuestCompletionHooks}'
     * own doc) -- a Plot Upgrade quest completed and paid out before this fix existed, so its
     * completion hook never fired and the plot's real Tier never moved. Finds every one of the
     * calling player's already-{@code COMPLETED} instances still sitting in {@code
     * quest.PlotUpgradeQuestTargets} (a normal, already-applied completion never leaves an entry
     * behind -- see below), applies the real upgrade for each, then clears the entry regardless of
     * outcome so a repeat run is always a safe no-op. Not meant to be re-run routinely -- a quest
     * completing through the normal, now-fixed flow applies this itself and is never left here to
     * find.
     */
    private static int fixPlotUpgrade(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            source.sendFailure(Component.literal("Only a player can run this."));
            return 0;
        }
        if (!com.github.cerealklla.myssions.bridge.SettlemyntsPlotUpgradeBridge.isLoaded()) {
            source.sendFailure(Component.literal("Settlemynts isn't loaded."));
            return 0;
        }
        com.github.cerealklla.myssions.quest.PlotUpgradeQuestTargets targets =
                com.github.cerealklla.myssions.quest.PlotUpgradeQuestTargets.get(level.getServer());
        int applied = 0;
        int checked = 0;
        for (com.github.cerealklla.myssions.quest.QuestInstance instance
                : player.getData(com.github.cerealklla.myssions.registration.ModAttachments.QUEST_DATA).quests().values()) {
            if (instance.status() != com.github.cerealklla.myssions.quest.QuestStatus.COMPLETED) {
                continue;
            }
            java.util.Optional<com.github.cerealklla.myssions.quest.PlotUpgradeQuestTargets.Target> target =
                    targets.get(instance.instanceId());
            if (target.isEmpty()) {
                continue;
            }
            checked++;
            if (com.github.cerealklla.myssions.bridge.SettlemyntsPlotUpgradeBridge.applyUpgrade(
                    level, target.get().plotId(), target.get().targetTier())) {
                applied++;
            }
            targets.clear(instance.instanceId());
        }
        int finalApplied = applied;
        int finalChecked = checked;
        source.sendSuccess(() -> Component.literal(
                "Checked " + finalChecked + " completed Plot Upgrade quest(s), applied " + finalApplied + " real upgrade(s)."), false);
        return applied;
    }

    private static int giveTestQuest(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can run this."));
            return 0;
        }
        boolean assigned = Myssions.assignQuest(player, SampleQuests.WIRING_TEST_ID).isPresent();
        if (assigned) {
            source.sendSuccess(() -> Component.literal("Assigned the wiring-test quest."), false);
            return 1;
        }
        source.sendFailure(Component.literal("Already have that quest in progress."));
        return 0;
    }
}
