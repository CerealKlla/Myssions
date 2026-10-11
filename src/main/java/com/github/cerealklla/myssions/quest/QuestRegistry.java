package com.github.cerealklla.myssions.quest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

/**
 * Open, in-memory registry of every known {@link QuestDefinition} -- not a Minecraft {@code
 * Registry} (quests are authored/registered at mod-constructor or common-setup time by whichever
 * mod owns them, same "open, trust-based registry" shape as Settlemynts' own {@code
 * zone.ZoneTypeRegistry}, not datapack-driven content). {@code api.Myssions#registerQuestDefinition}
 * is the only real entry point other mods should use -- this class itself is Myssions-internal.
 */
public final class QuestRegistry {

    private static final Map<Identifier, QuestDefinition> DEFINITIONS = new LinkedHashMap<>();

    private QuestRegistry() {
    }

    public static void register(QuestDefinition definition) {
        DEFINITIONS.put(definition.id(), definition);
    }

    public static Optional<QuestDefinition> get(Identifier id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static Map<Identifier, QuestDefinition> all() {
        return Map.copyOf(DEFINITIONS);
    }
}
