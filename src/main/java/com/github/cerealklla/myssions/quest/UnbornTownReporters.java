package com.github.cerealklla.myssions.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

/**
 * Maps an "unborn town" quest's id (see {@code NpcQuests#registerUnbornTownQuest}) to the reporter
 * NPC's display name -- read by {@link QuestReportBackTicker}, which spawns that NPC once the
 * player actually reaches the quest's real target, not at acceptance time. Kept separate from the
 * quest/milestone data itself for the same reason {@link ProofItemCatalog} is: authoring a quest's
 * content only ever names a reporter, never duplicates the spawn-timing logic.
 */
final class UnbornTownReporters {

    private static final Map<Identifier, String> NAMES = new HashMap<>();

    private UnbornTownReporters() {
    }

    static void register(Identifier questId, String reporterName) {
        NAMES.put(questId, reporterName);
    }

    static Optional<String> get(Identifier questId) {
        return Optional.ofNullable(NAMES.get(questId));
    }
}
