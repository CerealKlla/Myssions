package com.github.cerealklla.myssions.quest;

import java.util.List;

import net.minecraft.resources.Identifier;

/**
 * Grocer quest content (2026-10-11 content-expansion pass) -- a profession with no plot-specific
 * NPC entity of its own (see {@code NpcQuests}' own doc for why that doesn't matter: quest-giving
 * is resolved purely off the plot's own {@code zoneTypeId}, not the NPC's Java class). Mostly
 * gather/quantity-scaled quests (a Grocer's natural fit) plus buy-item and one region-kill.
 */
public final class GrocerQuests {

    private static final String GROCER_ZONE_TYPE = "blueprynts:grocer";

    private GrocerQuests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(com.github.cerealklla.myssions.MyssionsMod.MODID, path);
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    public static void registerAll() {
        List<Identifier> quests = List.of(
                stockingTheShelves(), sweetsForTheCounter(), aFreshBatchOfCookies(), fruitForTheStand(),
                melonsForMarket(), beetrootSoupFixings(), aRareImport(), anotherRareImport(),
                pestControlOutBack(), honeycombForTheCounter());
        IssuerQuestPools.register(GROCER_ZONE_TYPE, quests);
    }

    private static Identifier stockingTheShelves() {
        Identifier questId = id("stocking_the_shelves");
        return QuestHooks.registerGatherQuest(questId, "Stocking the Shelves",
                "Shelves are looking bare and the morning rush is coming. Bring me a good stock of "
                        + "bread and I'll make it worth your while.",
                GROCER_ZONE_TYPE, "Bring 10 Bread", mc("bread"), 10, 2,
                List.of(QuestReward.intangible("The shelves are stocked before the morning rush")));
    }

    private static Identifier sweetsForTheCounter() {
        Identifier questId = id("sweets_for_the_counter");
        return QuestHooks.registerGatherQuest(questId, "Sweets for the Counter",
                "Kids keep asking for something sweet and I'm out of sugar to make more. Bring me a "
                        + "good stock and I'll see you paid.",
                GROCER_ZONE_TYPE, "Bring 16 Sugar", mc("sugar"), 16, 1,
                List.of(QuestReward.intangible("The sweets counter is stocked again")));
    }

    private static Identifier aFreshBatchOfCookies() {
        Identifier questId = id("a_fresh_batch_of_cookies");
        return QuestHooks.registerGatherQuest(questId, "A Fresh Batch of Cookies",
                "Promised a fresh batch of cookies to half the settlement and I'm short. Bring me what "
                        + "you can find and I'll pay fair for it.",
                GROCER_ZONE_TYPE, "Bring 12 Cookies", mc("cookie"), 12, 1,
                List.of(QuestReward.intangible("The promised cookies actually show up")));
    }

    private static Identifier fruitForTheStand() {
        Identifier questId = id("fruit_for_the_stand");
        return QuestHooks.registerGatherQuest(questId, "Fruit for the Stand",
                "The fruit stand's looking thin and customers have been asking. Bring me a basket of "
                        + "apples and I'll make it worth the trip.",
                GROCER_ZONE_TYPE, "Bring 16 Apples", mc("apple"), 16, 1,
                List.of(QuestReward.intangible("The fruit stand looks respectable again")));
    }

    private static Identifier melonsForMarket() {
        Identifier questId = id("melons_for_market");
        return QuestHooks.registerGatherQuest(questId, "Melons for Market",
                "Melon season doesn't last forever and I want to make the most of it. Bring me a good "
                        + "haul and I'll split the profit with you.",
                GROCER_ZONE_TYPE, "Bring 12 Melon Slices", mc("melon_slice"), 12, 1,
                List.of(QuestReward.intangible("The melon stock is ready for market")));
    }

    private static Identifier beetrootSoupFixings() {
        Identifier questId = id("beetroot_soup_fixings");
        return QuestHooks.registerGatherQuest(questId, "Beetroot Soup Fixings",
                "Half the settlement's asking after beetroot soup and I'm out of beetroot. Bring me a "
                        + "good stock and I'll pay well for it.",
                GROCER_ZONE_TYPE, "Bring 10 Beetroot", mc("beetroot"), 10, 1,
                List.of(QuestReward.intangible("The soup fixings are stocked again")));
    }

    private static Identifier aRareImport() {
        Identifier questId = id("a_rare_import");
        return QuestHooks.registerBuyItemQuest(questId, "A Rare Import",
                "There's something from one of the other shops in town I keep meaning to stock and "
                        + "never get around to. Fetch it for me and I'll make it worth your while.",
                GROCER_ZONE_TYPE,
                List.of(QuestReward.intangible("The shelf finally has that one rare item")));
    }

    private static Identifier anotherRareImport() {
        Identifier questId = id("another_rare_import");
        return QuestHooks.registerBuyItemQuest(questId, "Another Rare Import",
                "A customer's been asking for something I don't carry, but somebody else in town "
                        + "surely does. Fetch it for me and I'll make it worth your while.",
                GROCER_ZONE_TYPE,
                List.of(QuestReward.intangible("That customer finally gets what they wanted")));
    }

    private static Identifier pestControlOutBack() {
        Identifier questId = id("pest_control_out_back");
        return QuestHooks.registerRegionKillQuest(questId, "Pest Control Out Back",
                "There's a spider infestation building up behind the storeroom, and it's only a "
                        + "matter of time before they get into the stock. Clear them out.",
                GROCER_ZONE_TYPE, "Kill 5 Spiders near {location}", mc("spider"), 5,
                "storeroom_web_scraps", "Storeroom Web Scraps", "Thick, dusty, and definitely not food-safe.",
                false, 1,
                List.of(QuestReward.gold(11), QuestReward.intangible("The storeroom is pest-free again")));
    }

    private static Identifier honeycombForTheCounter() {
        Identifier questId = id("honeycomb_for_the_counter");
        return QuestHooks.registerGatherQuest(questId, "Honeycomb for the Counter",
                "Honeycomb sells faster than I can stock it. Bring me a good batch and I'll split the "
                        + "profit fairly.",
                GROCER_ZONE_TYPE, "Bring 8 Honeycomb", mc("honeycomb"), 8, 2,
                List.of(QuestReward.intangible("The counter has honeycomb again")));
    }
}
