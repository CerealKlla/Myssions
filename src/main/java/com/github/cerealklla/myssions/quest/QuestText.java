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
 *
 * <p>A plot-upgrade milestone ({@code QuestMilestone#isPlotUpgradeMilestone()}, added 2026-10-12)
 * reuses the same field a third way, for the same reason (per-instance data, no spare
 * {@link QuestInstance} field): packed as {@code "PU:<woodNeeded>,<stoneNeeded>"} (the {@code PU:}
 * prefix keeps it unambiguous against the buy-item scheme above, and against a real place name,
 * which never starts with it). {@link #resolve} substitutes {@code {wood}}/{@code {stone}} tokens
 * from it; this quest kind's own flavor/milestone text never uses {@code {location}}, so the two
 * schemes never collide on the same instance.
 */
public final class QuestText {

    private static final String TOKEN = "{location}";
    private static final String WOOD_TOKEN = "{wood}";
    private static final String STONE_TOKEN = "{stone}";
    private static final String FALLBACK = "the area";
    private static final char GOLD_SUFFIX_DELIMITER = '#';
    private static final String PLOT_UPGRADE_PREFIX = "PU:";

    private QuestText() {
    }

    public static String resolve(String template, QuestInstance instance) {
        return resolve(template, instance, false);
    }

    /**
     * Same substitution as {@link #resolve}, but with {@code short_} set true, {@code {location}}
     * resolves to just {@link QuestInstance#realLocationName()} once the player's knowledge has
     * already upgraded {@link QuestInstance#locationName()} past its initial bare {@link
     * QuestInstance#directionPhrase()} -- e.g. "The Boughs" instead of "to the northeast of
     * Starterville, in The Boughs". Added 2026-10-12 for {@code client.ActiveQuestOverlay}'s own
     * terse HUD line (real report: the full resolved phrase ran the active-quest box into the
     * minimap) -- {@code client.QuestLogScreen}'s detail pane keeps the full phrase via plain {@link
     * #resolve}. Still respects the knowledge gate: before the upgrade, shows the same (already
     * short) direction phrase every other view shows -- never reveals the real name early.
     */
    public static String resolveShort(String template, QuestInstance instance) {
        return resolve(template, instance, true);
    }

    private static String resolve(String template, QuestInstance instance, boolean shortLocation) {
        String result = template;
        if (result.contains(TOKEN)) {
            result = result.replace(TOKEN, displayOnly(shortLocationName(instance, shortLocation)));
        }
        if (result.contains(WOOD_TOKEN) || result.contains(STONE_TOKEN)) {
            PlotUpgradeAmounts amounts = resolvePlotUpgradeAmounts(instance).orElse(new PlotUpgradeAmounts(0, 0));
            result = result.replace(WOOD_TOKEN, String.valueOf(amounts.woodNeeded()))
                    .replace(STONE_TOKEN, String.valueOf(amounts.stoneNeeded()));
        }
        return result;
    }

    private static String shortLocationName(QuestInstance instance, boolean shortLocation) {
        String displayed = instance.locationName().orElse(FALLBACK);
        if (!shortLocation) {
            return displayed;
        }
        boolean alreadyUpgraded = instance.directionPhrase().isPresent() && !displayed.equals(instance.directionPhrase().get());
        if (alreadyUpgraded && instance.realLocationName().isPresent()) {
            return instance.realLocationName().get();
        }
        return displayed;
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

    public record PlotUpgradeAmounts(int woodNeeded, int stoneNeeded) {
    }

    /** The resolved Wood/Stone amounts packed onto a plot-upgrade quest's own {@code locationName} -- see this class's own doc. Empty for any other quest kind, or before acceptance has resolved one. */
    public static Optional<PlotUpgradeAmounts> resolvePlotUpgradeAmounts(QuestInstance instance) {
        return instance.locationName().filter(s -> s.startsWith(PLOT_UPGRADE_PREFIX)).flatMap(raw -> {
            String[] parts = raw.substring(PLOT_UPGRADE_PREFIX.length()).split(",", 2);
            if (parts.length != 2) {
                return Optional.empty();
            }
            try {
                return Optional.of(new PlotUpgradeAmounts(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    /** Packs a plot-upgrade quest's resolved per-instance shortfall into one {@code locationName} string -- see {@code QuestHooks#registerPlotUpgradeHook}. */
    public static String packPlotUpgradeHint(int woodNeeded, int stoneNeeded) {
        return PLOT_UPGRADE_PREFIX + woodNeeded + "," + stoneNeeded;
    }

    private static String displayOnly(String rawLocationName) {
        int i = rawLocationName.indexOf(GOLD_SUFFIX_DELIMITER);
        return i < 0 ? rawLocationName : rawLocationName.substring(0, i);
    }
}
