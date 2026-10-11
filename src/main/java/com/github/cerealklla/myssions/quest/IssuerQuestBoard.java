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

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Save-wide, per-issuer "what quest is currently sitting on the board, waiting for someone to take
 * it" (added 2026-10-10, explicit user spec: "at midnight, NPCs who do not currently have a quest
 * available will decide with a 50% chance to generate a quest. Upon a player accepting a quest it
 * will be removed from the NPC so other players can't take it."). Keyed by the issuer's own
 * {@code plotId} (the same identity {@code zone.PlotNpc}/{@code plotsign.PlotConfigSignBlockEntity}
 * both already expose) -- one slot per plot, independent of every other plot, so two different
 * Lumberyards can easily offer the same quest definition at once with no conflict.
 *
 * <p>{@code MinecraftServer}-scoped, never {@code ServerLevel#getDataStorage()} -- mirrors
 * Settlemynts' own {@code plotsign.PlotConfigSignIndex} exactly (a plot/settlement isn't
 * dimension-scoped), even though this class itself has no Settlemynts dependency and is always
 * safe to load.
 */
public final class IssuerQuestBoard extends SavedData {

    public static final SavedDataType<IssuerQuestBoard> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "issuer_quest_board"),
            IssuerQuestBoard::new,
            codec());

    private final Map<UUID, Identifier> board;

    IssuerQuestBoard() {
        this(new HashMap<>());
    }

    private IssuerQuestBoard(Map<UUID, Identifier> board) {
        this.board = board;
    }

    private record Entry(UUID plotId, Identifier questId) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.core.UUIDUtil.CODEC.fieldOf("plot_id").forGetter(Entry::plotId),
                Identifier.CODEC.fieldOf("quest_id").forGetter(Entry::questId)
        ).apply(i, Entry::new));
    }

    private static Codec<IssuerQuestBoard> codec() {
        return Codec.list(Entry.CODEC).xmap(
                entries -> {
                    Map<UUID, Identifier> map = new HashMap<>();
                    for (Entry entry : entries) {
                        map.put(entry.plotId(), entry.questId());
                    }
                    return new IssuerQuestBoard(map);
                },
                data -> {
                    List<Entry> entries = new ArrayList<>(data.board.size());
                    data.board.forEach((plotId, questId) -> entries.add(new Entry(plotId, questId)));
                    return entries;
                });
    }

    public static IssuerQuestBoard get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Identifier> get(UUID plotId) {
        return Optional.ofNullable(board.get(plotId));
    }

    public void set(UUID plotId, Identifier questId) {
        board.put(plotId, questId);
        setDirty();
    }

    /** Called once a player actually accepts the offered quest -- "removed from the NPC so other players can't take it." */
    public void clear(UUID plotId) {
        if (board.remove(plotId) != null) {
            setDirty();
        }
    }
}
