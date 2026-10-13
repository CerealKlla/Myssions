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
 * Which real Settlemynts plot a "help upgrade this plot" quest instance (see {@code
 * QuestMilestone#isPlotUpgradeMilestone()}, {@code NpcQuests#registerPlotUpgradeQuest}) actually
 * targets -- a side table rather than a new field on {@link QuestInstance} itself, same "already at
 * Mojang's codec arity limit" reasoning {@link BuyItemTargets} documents for its own near-identical
 * shape (copy this pattern, not a coincidence).
 *
 * <p>Added 2026-10-12, real live report: a player used Settlemynts' own real "Upgrade Plot" action
 * (a genuine, separate Tier-upgrade-funding feature, not part of this quest at all) to pay for and
 * complete the actual upgrade their quest was themed around -- real materials spent, the plot's real
 * Tier went up, but the quest itself never found out and stayed stuck forever at 0 progress. This
 * table is what lets {@code QuestEngine#completePlotUpgradeQuestsForPlot} (called from Settlemynts'
 * own successful-upgrade path via {@code bridge}) find which of a player's in-progress instances, if
 * any, was tracking that same plot.
 */
public final class PlotUpgradeQuestTargets extends SavedData {

    public static final SavedDataType<PlotUpgradeQuestTargets> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "plot_upgrade_quest_targets"),
            PlotUpgradeQuestTargets::new,
            codec());

    private final Map<UUID, UUID> plotIdByInstance;

    PlotUpgradeQuestTargets() {
        this(new HashMap<>());
    }

    private PlotUpgradeQuestTargets(Map<UUID, UUID> plotIdByInstance) {
        this.plotIdByInstance = plotIdByInstance;
    }

    private record Entry(UUID instanceId, UUID plotId) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("instance_id").forGetter(Entry::instanceId),
                UUIDUtil.CODEC.fieldOf("plot_id").forGetter(Entry::plotId)
        ).apply(i, Entry::new));
    }

    private static Codec<PlotUpgradeQuestTargets> codec() {
        return Codec.list(Entry.CODEC).xmap(
                entries -> {
                    Map<UUID, UUID> map = new HashMap<>();
                    for (Entry entry : entries) {
                        map.put(entry.instanceId(), entry.plotId());
                    }
                    return new PlotUpgradeQuestTargets(map);
                },
                data -> {
                    List<Entry> entries = new ArrayList<>(data.plotIdByInstance.size());
                    data.plotIdByInstance.forEach((instanceId, plotId) -> entries.add(new Entry(instanceId, plotId)));
                    return entries;
                });
    }

    public static PlotUpgradeQuestTargets get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<UUID> get(UUID instanceId) {
        return Optional.ofNullable(plotIdByInstance.get(instanceId));
    }

    public void set(UUID instanceId, UUID plotId) {
        plotIdByInstance.put(instanceId, plotId);
        setDirty();
    }
}
