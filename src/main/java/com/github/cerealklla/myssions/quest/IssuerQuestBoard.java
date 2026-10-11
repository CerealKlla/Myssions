package com.github.cerealklla.myssions.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Save-wide, per-issuer "what quests are currently sitting on the board, waiting for someone to take
 * them" (added 2026-10-10, explicit user spec: "at midnight, NPCs who do not currently have a quest
 * available will decide with a 50% chance to generate a quest. Upon a player accepting a quest it
 * will be removed from the NPC so other players can't take it."). Keyed by the issuer's own
 * {@code plotId} (the same identity {@code zone.PlotNpc}/{@code plotsign.PlotConfigSignBlockEntity}
 * both already expose), independent of every other plot, so two different Lumberyards can easily
 * offer the same quest definition at once with no conflict.
 *
 * <p><b>Widened from one slot per plot to up to {@link #MAX_BOARD_SLOTS}, 2026-10-11</b> (explicit
 * user spec: "NPCs should be able to hold 5 available quests at one time, and not roll any more when
 * at that limit") -- each plot now holds a short list of independently-offered quests rather than a
 * single value. Accepting one specific offer only removes that one entry ({@link #remove}), not the
 * whole board.
 *
 * <p>{@code MinecraftServer}-scoped, never {@code ServerLevel#getDataStorage()} -- mirrors
 * Settlemynts' own {@code plotsign.PlotConfigSignIndex} exactly (a plot/settlement isn't
 * dimension-scoped), even though this class itself has no Settlemynts dependency and is always
 * safe to load.
 */
public final class IssuerQuestBoard extends SavedData {

    public static final int MAX_BOARD_SLOTS = 5;

    public static final SavedDataType<IssuerQuestBoard> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "issuer_quest_board"),
            IssuerQuestBoard::new,
            codec());

    private final Map<UUID, List<Identifier>> board;

    IssuerQuestBoard() {
        this(new HashMap<>());
    }

    private IssuerQuestBoard(Map<UUID, List<Identifier>> board) {
        this.board = board;
    }

    private record Entry(UUID plotId, List<Identifier> questIds) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.core.UUIDUtil.CODEC.fieldOf("plot_id").forGetter(Entry::plotId),
                Codec.list(Identifier.CODEC).fieldOf("quest_ids").forGetter(Entry::questIds)
        ).apply(i, Entry::new));
    }

    private static Codec<IssuerQuestBoard> codec() {
        return Codec.list(Entry.CODEC).xmap(
                entries -> {
                    Map<UUID, List<Identifier>> map = new HashMap<>();
                    for (Entry entry : entries) {
                        map.put(entry.plotId(), new ArrayList<>(entry.questIds()));
                    }
                    return new IssuerQuestBoard(map);
                },
                data -> {
                    List<Entry> entries = new ArrayList<>(data.board.size());
                    data.board.forEach((plotId, questIds) -> entries.add(new Entry(plotId, questIds)));
                    return entries;
                });
    }

    public static IssuerQuestBoard get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Every offer currently sitting on this plot's board, empty if none. */
    public List<Identifier> get(UUID plotId) {
        return List.copyOf(board.getOrDefault(plotId, List.of()));
    }

    /** False (no-op) if this plot's board is already at {@link #MAX_BOARD_SLOTS}. */
    public boolean add(UUID plotId, Identifier questId) {
        List<Identifier> offers = board.computeIfAbsent(plotId, k -> new ArrayList<>());
        if (offers.size() >= MAX_BOARD_SLOTS) {
            return false;
        }
        offers.add(questId);
        setDirty();
        return true;
    }

    /** Called once a player actually accepts one specific offered quest -- "removed from the NPC so other players can't take it." Leaves the board's other offers (if any) untouched. */
    public void remove(UUID plotId, Identifier questId) {
        List<Identifier> offers = board.get(plotId);
        if (offers != null && offers.remove(questId)) {
            setDirty();
        }
    }
}
