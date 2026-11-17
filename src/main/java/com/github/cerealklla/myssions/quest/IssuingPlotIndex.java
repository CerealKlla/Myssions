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
 * Persisted {@code instanceId -> plotId} lookup -- {@code QuestEngine#getIssuingPlotId}'s backing
 * store, replacing a plain in-memory {@code ConcurrentHashMap} that used to hold this (added
 * 2026-11-08, see Terrytories/Arryval's own quest-to-settlement crediting), wiped on every server
 * restart. That was believed safe at the time ("only ever needs to answer 'what plot issued this,
 * right now' at the instant an instance assigned and ended within the same server lifetime"), but
 * that assumption is false in practice -- a player routinely holds an accepted quest across a
 * restart (any deploy, crash, or maintenance stop), and this server restarts often. Real report,
 * 2026-11-17, right after one such restart: "I've turned in several more quests now since using the
 * /arryval fixhomevillage command and I'm still not getting progress" -- every one of those quests
 * had been accepted before the restart that shipped that very fix, so their plotId mapping was
 * already gone by the time they were turned in, silently breaking both Arryval's {@code
 * starttown.VillageQuestTracker} milestone and Terrytories' own quest-completion reputation credit
 * for every such instance, not just this one player's.
 *
 * <p>Same side-table shape as {@link PlotUpgradeQuestTargets}/{@code BuyItemTargets} (a plain
 * {@code UUID -> UUID} map doesn't need {@link QuestInstance}'s own 16-field codec at all).
 */
public final class IssuingPlotIndex extends SavedData {

    public static final SavedDataType<IssuingPlotIndex> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "issuing_plot_index"),
            IssuingPlotIndex::new,
            codec());

    private final Map<UUID, UUID> plotIdByInstance;

    IssuingPlotIndex() {
        this(new HashMap<>());
    }

    private IssuingPlotIndex(Map<UUID, UUID> plotIdByInstance) {
        this.plotIdByInstance = plotIdByInstance;
    }

    private record Entry(UUID instanceId, UUID plotId) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("instance_id").forGetter(Entry::instanceId),
                UUIDUtil.CODEC.fieldOf("plot_id").forGetter(Entry::plotId)
        ).apply(i, Entry::new));
    }

    private static Codec<IssuingPlotIndex> codec() {
        return Codec.list(Entry.CODEC).xmap(
                entries -> {
                    Map<UUID, UUID> map = new HashMap<>();
                    for (Entry entry : entries) {
                        map.put(entry.instanceId(), entry.plotId());
                    }
                    return new IssuingPlotIndex(map);
                },
                data -> {
                    List<Entry> entries = new ArrayList<>(data.plotIdByInstance.size());
                    data.plotIdByInstance.forEach((instanceId, plotId) -> entries.add(new Entry(instanceId, plotId)));
                    return entries;
                });
    }

    public static IssuingPlotIndex get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<UUID> get(UUID instanceId) {
        return Optional.ofNullable(plotIdByInstance.get(instanceId));
    }

    public void put(UUID instanceId, UUID plotId) {
        plotIdByInstance.put(instanceId, plotId);
        setDirty();
    }

    public void remove(UUID instanceId) {
        if (plotIdByInstance.remove(instanceId) != null) {
            setDirty();
        }
    }
}
