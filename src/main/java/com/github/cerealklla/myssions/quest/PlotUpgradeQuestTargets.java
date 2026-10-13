package com.github.cerealklla.myssions.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Which real Settlemynts plot (and which specific Tier it's headed to) a "help upgrade this plot"
 * quest instance (see {@code QuestMilestone#isPlotUpgradeMilestone()}, {@code
 * NpcQuests#registerPlotUpgradeQuest}) actually targets -- a side table rather than a new field on
 * {@link QuestInstance} itself, same "already at Mojang's codec arity limit" reasoning {@link
 * BuyItemTargets} documents for its own near-identical shape (copy this pattern, not a coincidence).
 *
 * <p>Added 2026-10-12, real live report: a player used Settlemynts' own real "Upgrade Plot" action
 * (a genuine, separate Tier-upgrade-funding feature, not part of this quest at all) to pay for and
 * complete the actual upgrade their quest was themed around -- real materials spent, the plot's real
 * Tier went up, but the quest itself never found out and stayed stuck forever at 0 progress. This
 * table is what lets {@code QuestEngine#completePlotUpgradeQuestsForPlot} (called from Settlemynts'
 * own successful-upgrade path via {@code bridge}) find which of a player's in-progress instances, if
 * any, was tracking that same plot.
 *
 * <p><b>{@code targetTier} added 2026-10-13</b> -- real correctness gap caught before it ever bit
 * anyone live: a quest instance's own required wood/stone amount is sized for one specific Tier at
 * acceptance time ({@code SettlemyntsPlotUpgradeBridge.Shortfall#nextTier}), but two players (or the
 * same player holding two stale instances) could each have their own instance targeting the same
 * plot. Without recording which Tier an instance was actually for, the completion hook (see {@code
 * QuestHooks#registerPlotUpgradeHook}) could only check "is the plot below its ceiling" -- the
 * second instance to complete would get silently re-evaluated against whatever Tier the plot has
 * *already* reached by then (from the first instance's completion), charging the wrong cost table
 * instead of safely recognizing someone already took care of it.
 */
public final class PlotUpgradeQuestTargets extends SavedData {

    public static final SavedDataType<PlotUpgradeQuestTargets> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "plot_upgrade_quest_targets"),
            PlotUpgradeQuestTargets::new,
            codec());

    public record Target(UUID plotId, int targetTier) {
    }

    private final Map<UUID, Target> targetsByInstance;

    PlotUpgradeQuestTargets() {
        this(new HashMap<>());
    }

    private PlotUpgradeQuestTargets(Map<UUID, Target> targetsByInstance) {
        this.targetsByInstance = targetsByInstance;
    }

    private record Entry(UUID instanceId, UUID plotId, int targetTier) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("instance_id").forGetter(Entry::instanceId),
                UUIDUtil.CODEC.fieldOf("plot_id").forGetter(Entry::plotId),
                Codec.INT.fieldOf("target_tier").forGetter(Entry::targetTier)
        ).apply(i, Entry::new));
    }

    private static Codec<PlotUpgradeQuestTargets> codec() {
        return Codec.list(Entry.CODEC).xmap(
                entries -> {
                    Map<UUID, Target> map = new HashMap<>();
                    for (Entry entry : entries) {
                        map.put(entry.instanceId(), new Target(entry.plotId(), entry.targetTier()));
                    }
                    return new PlotUpgradeQuestTargets(map);
                },
                data -> {
                    List<Entry> entries = new ArrayList<>(data.targetsByInstance.size());
                    data.targetsByInstance.forEach((instanceId, target) ->
                            entries.add(new Entry(instanceId, target.plotId(), target.targetTier())));
                    return entries;
                });
    }

    public static PlotUpgradeQuestTargets get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Target> get(UUID instanceId) {
        return Optional.ofNullable(targetsByInstance.get(instanceId));
    }

    public void set(UUID instanceId, UUID plotId, int targetTier) {
        targetsByInstance.put(instanceId, new Target(plotId, targetTier));
        setDirty();
    }

    /** Used by the one-off {@code /myssions fixplotupgrade} remediation command -- see its own doc. */
    public void clear(UUID instanceId) {
        if (targetsByInstance.remove(instanceId) != null) {
            setDirty();
        }
    }
}
