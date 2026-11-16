package com.github.cerealklla.myssions.quest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;

import com.github.cerealklla.myssions.MyssionsMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persisted record of every plot that has already received its one-time immediate quest-board roll
 * (see {@code bridge.QuestBoardMidnightTicker}'s own doc) -- added 2026-11-15, real follow-up
 * request: "force all the town NPCs to do their nightly quest roll the first time they get
 * generated, so people fresh from the tutorial as the very first visitors have a chance to pick up
 * quests immediately." This generalizes (and replaces) the ticker's old single global
 * {@code initialRollDone} boolean, which only ever covered whichever plots existed the very first
 * time the ticker found any plot at all -- a settlement founded afterward got no immediate roll of
 * its own, just whatever's left of the current real in-game day.
 *
 * <p>Its own {@code SavedData} type rather than a new field on {@link IssuerQuestBoard} -- keeps
 * that class's existing persisted format (a bare JSON array, no wrapping object) completely
 * untouched, so this adds zero migration risk to already-live board state.
 */
public final class InitialQuestRollTracker extends SavedData {

    public static final SavedDataType<InitialQuestRollTracker> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MyssionsMod.MODID, "initial_quest_roll_tracker"),
            InitialQuestRollTracker::new,
            codec());

    private final Set<UUID> rolledPlotIds;

    InitialQuestRollTracker() {
        this(new HashSet<>());
    }

    private InitialQuestRollTracker(Set<UUID> rolledPlotIds) {
        this.rolledPlotIds = rolledPlotIds;
    }

    private static Codec<InitialQuestRollTracker> codec() {
        return Codec.list(UUIDUtil.CODEC).xmap(
                ids -> new InitialQuestRollTracker(new HashSet<>(ids)),
                data -> new ArrayList<>(data.rolledPlotIds));
    }

    public static InitialQuestRollTracker get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean hasRolled(UUID plotId) {
        return rolledPlotIds.contains(plotId);
    }

    public void markRolled(UUID plotId) {
        if (rolledPlotIds.add(plotId)) {
            setDirty();
        }
    }
}
