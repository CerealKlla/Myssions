package com.github.cerealklla.myssions.quest;

import java.util.Optional;

/**
 * Substitutes the {@code {location}} token a quest's own flavor/milestone text can contain (see
 * {@code QuestInstance#locationName()}'s own doc) with this specific player's actual resolved place
 * name, e.g. "Kill 4 Zombies near my fields in {location}" -> "...in Bramton." Falls back to a
 * generic phrase for a quest with no resolvable real location (an off-site/wilderness quest, or
 * Settlemynts not loaded) rather than leaving the literal token visible.
 *
 * <p>A buy-item milestone ({@code QuestMilestone#isBuyMilestone()}) reuses this same {@code
 * locationName} field to carry its resolved item name (see {@code QuestHooks#registerBuyItemHook}),
 * since {@link QuestInstance} is already at Mojang's 16-field {@code RecordCodecBuilder} arity limit
 * and can't take a dedicated field for its own resolved gold reward either. <b>Added 2026-10-12</b>,
 * real user report: the Quest Log's Rewards list for a buy-item quest showed only its static flavor
 * line ("The shaft gets what it needed") with no real reward visible at all, since the resolved gold
 * amount -- known only per-instance, after acceptance -- was never surfaced anywhere client-side.
 * Rather than a new network round-trip, the same field is packed as {@code "<itemName>#<goldReward>"}
 * -- {@link #resolve} strips the suffix for every normal (`{location}`) use, and {@link
 * #resolveBuyItemGoldReward} parses it back out for the Rewards list. A bare `#` is never otherwise
 * valid in a resolved location/item display name, so this is unambiguous.
 */
public final class QuestText {

    private static final String TOKEN = "{location}";
    private static final String FALLBACK = "the area";
    private static final char GOLD_SUFFIX_DELIMITER = '#';

    private QuestText() {
    }

    public static String resolve(String template, QuestInstance instance) {
        if (!template.contains(TOKEN)) {
            return template;
        }
        return template.replace(TOKEN, displayOnly(instance.locationName().orElse(FALLBACK)));
    }

    /** The resolved gold reward packed onto a buy-item milestone's own {@code locationName} -- see this class's own doc. Empty for any non-buy-item quest, or before acceptance has resolved one. */
    public static Optional<Integer> resolveBuyItemGoldReward(QuestInstance instance) {
        return instance.locationName().flatMap(raw -> {
            int i = raw.indexOf(GOLD_SUFFIX_DELIMITER);
            if (i < 0) {
                return Optional.empty();
            }
            try {
                return Optional.of(Integer.parseInt(raw.substring(i + 1)));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    /** Packs a buy-item milestone's resolved item name and gold reward into one {@code locationName} string -- see {@code QuestHooks#registerBuyItemHook}. */
    public static String packBuyItemHint(String itemName, int goldReward) {
        return itemName + GOLD_SUFFIX_DELIMITER + goldReward;
    }

    private static String displayOnly(String rawLocationName) {
        int i = rawLocationName.indexOf(GOLD_SUFFIX_DELIMITER);
        return i < 0 ? rawLocationName : rawLocationName.substring(0, i);
    }
}
