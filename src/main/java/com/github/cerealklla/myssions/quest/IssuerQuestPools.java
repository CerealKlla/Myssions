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
 *
 * <p><b>Additive, not replacing, 2026-10-12</b> -- changed for {@code api.Myssions
 * #registerQuestForZoneType}, the real missing half of this mod's third-party extensibility story.
 * {@code api.Myssions#registerQuestDefinition}/{@code #reportMilestoneProgress}/{@code
 * #completeMilestone}/{@code #tryCompleteQuest} already let any mod define and fully drive a brand
 * new quest from outside this mod (a plain {@code QuestMilestone#simple} milestone is exactly that
 * extension point -- completed only by a direct API call, no built-in detection mechanism assumed).
 * What was missing: the only way a definition ever became *offerable* through the normal NPC/board
 * flow was this class's own internal, per-zone-type call list -- a second call to the old
 * overwriting {@code register} would have silently wiped out every quest already registered for
 * that zone type. Every existing internal call site (one `register` call per zone type, at mod
 * setup) is unaffected by the switch to append-only semantics.
 */
public final class IssuerQuestPools {

    private static final Map<String, List<Identifier>> BY_ZONE_TYPE = new ConcurrentHashMap<>();

    private IssuerQuestPools() {
    }

    /** Appends to {@code zoneTypeId}'s pool -- never replaces what's already there (see this class's own doc). */
    public static void register(String zoneTypeId, List<Identifier> questIds) {
        BY_ZONE_TYPE.compute(zoneTypeId, (id, existing) -> {
            List<Identifier> merged = existing == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(existing);
            for (Identifier questId : questIds) {
                if (!merged.contains(questId)) {
                    merged.add(questId);
                }
            }
            return List.copyOf(merged);
        });
    }

    public static List<Identifier> poolFor(String zoneTypeId) {
        return BY_ZONE_TYPE.getOrDefault(zoneTypeId, List.of());
    }
}
