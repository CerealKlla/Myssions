package com.github.cerealklla.myssions.quest;

import java.util.List;

import net.minecraft.resources.Identifier;

/** Tavern quest content (2026-10-11 content-expansion pass) -- see {@code GrocerQuests}' own doc for the shared registration pattern. */
public final class TavernQuests {

    private static final String TAVERN_ZONE_TYPE = "blueprynts:tavern";

    private TavernQuests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(com.github.cerealklla.myssions.MyssionsMod.MODID, path);
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    public static void registerAll() {
        List<Identifier> quests = List.of(
                aleIngredients(), sweetsForTheBar(), meatForTheTable(), aRoundForTheHouse(), rowdyTravelers(),
                wolvesHowlingPastClosing(), theMissingBard(), breadForTheBoard(), berriesForTheHouseBrew(),
                aSecondRound(), expandingTheTavern());
        IssuerQuestPools.register(TAVERN_ZONE_TYPE, quests);
    }

    private static Identifier aleIngredients() {
        Identifier questId = id("ale_ingredients");
        return QuestHooks.registerGatherQuest(questId, "Ale Ingredients",
                "Can't brew a fresh batch without a good stock of wheat, and I'm running dry. Bring "
                        + "me what you can find.",
                TAVERN_ZONE_TYPE, "Bring 20 Wheat", mc("wheat"), 20, 1,
                List.of());
    }

    private static Identifier sweetsForTheBar() {
        Identifier questId = id("sweets_for_the_bar");
        return QuestHooks.registerGatherQuest(questId, "Sweets for the Bar",
                "Honey mead's a house specialty, and I'm out of honey to make more. Bring me a good "
                        + "stock.",
                TAVERN_ZONE_TYPE, "Bring 8 Honey Bottles", mc("honey_bottle"), 8, 2,
                List.of());
    }

    private static Identifier meatForTheTable() {
        Identifier questId = id("meat_for_the_table");
        return QuestHooks.registerGatherQuest(questId, "Meat for the Table",
                "The evening crowd wants a proper meal, and I'm out of beef for the stew. Bring me "
                        + "a good stock.",
                TAVERN_ZONE_TYPE, "Bring 12 Beef", mc("beef"), 12, 2,
                List.of());
    }

    private static Identifier aRoundForTheHouse() {
        Identifier questId = id("a_round_for_the_house");
        return QuestHooks.registerBuyItemQuest(questId, "A Round for the House",
                "There's something from one of the other shops in town that'd round out tonight's "
                        + "specials. Fetch it for me and I'll make it worth your while.",
                TAVERN_ZONE_TYPE,
                List.of());
    }

    private static Identifier rowdyTravelers() {
        Identifier questId = id("rowdy_travelers");
        return QuestHooks.registerRegionKillQuest(questId, "Rowdy Travelers",
                "A brawl spilled out the back door last night and some of it clearly wasn't human -- "
                        + "zombies, drawn in by the noise. Clear them out before it happens again.",
                TAVERN_ZONE_TYPE, "Kill 5 Zombies near {location}", mc("zombie"), 5,
                "torn_tavern_cloth", "Torn Tavern Cloth", "From someone's coat, probably. Best not to ask whose.",
                false, 2,
                List.of(QuestReward.gold(14)));
    }

    private static Identifier wolvesHowlingPastClosing() {
        Identifier questId = id("wolves_howling_past_closing");
        return QuestHooks.registerRegionKillQuest(questId, "Wolves Howling Past Closing",
                "A pack of wolves has taken to howling right outside the tavern after closing, and "
                        + "it's scaring off the last customers out the door. They only act up after dark.",
                TAVERN_ZONE_TYPE, "Kill 4 Wolves near {location} at night", mc("wolf"), 4,
                "howling_pack_fur", "Howling Pack Fur", "Still faintly damp with night dew.",
                true, 2,
                List.of(QuestReward.gold(16)));
    }

    private static Identifier theMissingBard() {
        Identifier questId = id("the_missing_bard");
        return QuestHooks.registerTalkQuest(questId, "The Missing Bard",
                "A traveling bard named Finn was supposed to play here three nights ago and never "
                        + "showed. Word is he's still out there somewhere -- go track him down.",
                TAVERN_ZONE_TYPE, "Find and talk to Finn", "Wandering Bard Finn", false,
                "You get a rough idea of which way Finn might have gone -- check your map.",
                List.of(QuestReward.gold(10)));
    }

    private static Identifier breadForTheBoard() {
        Identifier questId = id("bread_for_the_board");
        return QuestHooks.registerGatherQuest(questId, "Bread for the Board",
                "Can't set a proper table without bread, and the kitchen's run dry. Bring me a good "
                        + "stock.",
                TAVERN_ZONE_TYPE, "Bring 10 Bread", mc("bread"), 10, 1,
                List.of());
    }

    private static Identifier berriesForTheHouseBrew() {
        Identifier questId = id("berries_for_the_house_brew");
        return QuestHooks.registerGatherQuest(questId, "Berries for the House Brew",
                "The house brew gets its color from sweet berries, and I'm out. Bring me a good "
                        + "stock.",
                TAVERN_ZONE_TYPE, "Bring 16 Sweet Berries", mc("sweet_berries"), 16, 1,
                List.of());
    }

    private static Identifier aSecondRound() {
        Identifier questId = id("a_second_round");
        return QuestHooks.registerBuyItemQuest(questId, "A Second Round",
                "Lost a regular customer last week because I didn't have what they wanted on hand. "
                        + "Fetch me something from one of the other shops in town so it doesn't happen again.",
                TAVERN_ZONE_TYPE,
                List.of());
    }

    /** See {@code QuestHooks#registerPlotUpgradeQuest}'s own doc -- one per profession, per the standing instruction in design-document.md's "Plot Upgrade quest" section. */
    private static Identifier expandingTheTavern() {
        Identifier questId = id("expanding_the_tavern");
        return QuestHooks.registerPlotUpgradeQuest(questId, "Expanding the Tavern",
                "I'd like to expand this tavern, but I'm short on raw building materials. Bring me "
                        + "what's missing and I'll make it worth your while.",
                TAVERN_ZONE_TYPE,
                List.of(QuestReward.gold(25), QuestReward.xp("mayor", 50)));
    }
}
