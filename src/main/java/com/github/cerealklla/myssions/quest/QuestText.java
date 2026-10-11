package com.github.cerealklla.myssions.quest;

/**
 * Substitutes the {@code {location}} token a quest's own flavor/milestone text can contain (see
 * {@code QuestInstance#locationName()}'s own doc) with this specific player's actual resolved place
 * name, e.g. "Kill 4 Zombies near my fields in {location}" -> "...in Bramton." Falls back to a
 * generic phrase for a quest with no resolvable real location (an off-site/wilderness quest, or
 * Settlemynts not loaded) rather than leaving the literal token visible.
 */
public final class QuestText {

    private static final String TOKEN = "{location}";
    private static final String FALLBACK = "the area";

    private QuestText() {
    }

    public static String resolve(String template, QuestInstance instance) {
        if (!template.contains(TOKEN)) {
            return template;
        }
        return template.replace(TOKEN, instance.locationName().orElse(FALLBACK));
    }
}
