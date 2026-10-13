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
 * Save-wide, per-plot "Mayor's Special Assignments" board -- added 2026-10-13 for the Establish
 * Highway quest, explicit user spec: unlimited offers, shown in their own Quest Log section, not
 * subject to the ordinary {@link IssuerQuestBoard}'s {@code MAX_BOARD_SLOTS} cap. A deliberate
 * sibling of {@link IssuerQuestBoard} rather than a change to it -- raising/removing that class's
 * own 5-slot cap would also loosen it for every ordinary quest offer, which was never asked for.
 *
 * <p>Any {@link QuestDefinition} posted here must have {@link QuestDefinition#isSpecialAssignment()}
 * true -- {@code QuestEngine#assignQuest}'s own cap-exemption only applies to a definition that says
 * so itself, so posting an ordinary quest onto this board wouldn't actually bypass anything (the
 * player's normal 5-active cap would still count it). Not enforced here (this class has no reason to
 * depend on {@code QuestRegistry}), just a convention every caller must follow.
 */
public final class SpecialAssignmentBoard extends SavedData {

    public static final SavedDataType<SpecialAssignmentBoard> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "special_assignment_board"),
            SpecialAssignmentBoard::new,
            codec());

    private final Map<UUID, List<Identifier>> board;

    SpecialAssignmentBoard() {
        this(new HashMap<>());
    }

    private SpecialAssignmentBoard(Map<UUID, List<Identifier>> board) {
        this.board = board;
    }

    private record Entry(UUID plotId, List<Identifier> questIds) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.core.UUIDUtil.CODEC.fieldOf("plot_id").forGetter(Entry::plotId),
                Codec.list(Identifier.CODEC).fieldOf("quest_ids").forGetter(Entry::questIds)
        ).apply(i, Entry::new));
    }

    private static Codec<SpecialAssignmentBoard> codec() {
        return Codec.list(Entry.CODEC).xmap(
                entries -> {
                    Map<UUID, List<Identifier>> map = new HashMap<>();
                    for (Entry entry : entries) {
                        map.put(entry.plotId(), new ArrayList<>(entry.questIds()));
                    }
                    return new SpecialAssignmentBoard(map);
                },
                data -> {
                    List<Entry> entries = new ArrayList<>(data.board.size());
                    data.board.forEach((plotId, questIds) -> entries.add(new Entry(plotId, questIds)));
                    return entries;
                });
    }

    public static SpecialAssignmentBoard get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Every special-assignment offer currently sitting on this plot's board, empty if none. */
    public List<Identifier> get(UUID plotId) {
        return List.copyOf(board.getOrDefault(plotId, List.of()));
    }

    /** No cap, unlike {@link IssuerQuestBoard#add} -- always succeeds. */
    public void add(UUID plotId, Identifier questId) {
        board.computeIfAbsent(plotId, k -> new ArrayList<>()).add(questId);
        setDirty();
    }

    /** Called once a player accepts one specific offer, same semantics as {@link IssuerQuestBoard#remove}. */
    public void remove(UUID plotId, Identifier questId) {
        List<Identifier> offers = board.get(plotId);
        if (offers != null && offers.remove(questId)) {
            setDirty();
        }
    }
}
