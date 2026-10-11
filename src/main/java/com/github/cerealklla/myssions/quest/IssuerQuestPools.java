package com.github.cerealklla.myssions.quest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;

/**
 * Which quests a given Zone Type's NPC/sign offers -- keyed by the plain {@code zoneTypeId}
 * string (e.g. {@code "blueprynts:lumberyard"}), since that's what {@code
 * zone.PlotRecord#zoneTypeId()} resolves to for both the NPC-interact and sign-interact turn-in
 * paths (see {@code quest.QuestTurnIn}). Order matters -- the first entry the player doesn't
 * already have (in progress, completed, or failed) is the one offered.
 */
public final class IssuerQuestPools {

    private static final Map<String, List<Identifier>> BY_ZONE_TYPE = new ConcurrentHashMap<>();

    private IssuerQuestPools() {
    }

    public static void register(String zoneTypeId, List<Identifier> questIds) {
        BY_ZONE_TYPE.put(zoneTypeId, questIds);
    }

    public static List<Identifier> poolFor(String zoneTypeId) {
        return BY_ZONE_TYPE.getOrDefault(zoneTypeId, List.of());
    }
}
