package com.github.cerealklla.myssions.quest;

import java.util.List;

import net.minecraft.resources.Identifier;

/** Restaurant quest content (2026-10-11 content-expansion pass) -- see {@code GrocerQuests}' own doc for the shared registration pattern. */
public final class RestaurantQuests {

    private static final String RESTAURANT_ZONE_TYPE = "blueprynts:restaurant";

    private RestaurantQuests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(com.github.cerealklla.myssions.MyssionsMod.MODID, path);
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    public static void registerAll() {
        List<Identifier> quests = List.of(
                meatForTheGrill(), freshCatch(), sweetsForDessert(), eggsForBreakfast(), aPinchOfSpice(),
                mushroomsForTheStew(), aHuntingTrip(), melonForDessert(), milkForTheKitchen(), pestControlInThePantry(),
                expandingTheKitchen());
        IssuerQuestPools.register(RESTAURANT_ZONE_TYPE, quests);
    }

    private static Identifier meatForTheGrill() {
        Identifier questId = id("meat_for_the_grill");
        return QuestHooks.registerGatherQuest(questId, "Meat for the Grill",
                "The dinner rush is coming and the grill's running low. Bring me a good stock of beef "
                        + "and I'll make it worth your while.",
                RESTAURANT_ZONE_TYPE, "Bring 12 Beef", mc("beef"), 12, 2,
                List.of(QuestReward.intangible("The grill is stocked for the dinner rush")));
    }

    private static Identifier freshCatch() {
        Identifier questId = id("fresh_catch");
        return QuestHooks.registerGatherQuest(questId, "Fresh Catch",
                "Fish sells faster than anything else on the menu, and I'm out. Bring me a good "
                        + "catch and I'll pay fair for it.",
                RESTAURANT_ZONE_TYPE, "Bring 10 Cod", mc("cod"), 10, 2,
                List.of(QuestReward.intangible("The fish special is back on the menu")));
    }

    private static Identifier sweetsForDessert() {
        Identifier questId = id("sweets_for_dessert");
        return QuestHooks.registerGatherQuest(questId, "Sweets for Dessert",
                "Can't make dessert without sugar, and I'm completely out. Bring me a good stock.",
                RESTAURANT_ZONE_TYPE, "Bring 12 Sugar", mc("sugar"), 12, 1,
                List.of(QuestReward.intangible("Dessert is back on the menu")));
    }

    private static Identifier eggsForBreakfast() {
        Identifier questId = id("eggs_for_breakfast");
        return QuestHooks.registerGatherQuest(questId, "Eggs for Breakfast",
                "The breakfast crowd wants eggs and I can't keep up with my own hens. Bring me a "
                        + "basket and I'll make it worth your while.",
                RESTAURANT_ZONE_TYPE, "Bring 16 Eggs", mc("egg"), 16, 1,
                List.of(QuestReward.intangible("Breakfast is back on the menu")));
    }

    private static Identifier aPinchOfSpice() {
        Identifier questId = id("a_pinch_of_spice");
        return QuestHooks.registerBuyItemQuest(questId, "A Pinch of Spice",
                "There's something from one of the other shops in town that'd really round out a "
                        + "new dish I'm working on. Fetch it for me and I'll make it worth your while.",
                RESTAURANT_ZONE_TYPE,
                List.of(QuestReward.intangible("The new dish finally comes together")));
    }

    private static Identifier mushroomsForTheStew() {
        Identifier questId = id("mushrooms_for_the_stew");
        return QuestHooks.registerGatherQuest(questId, "Mushrooms for the Stew",
                "Mushroom stew's a house favorite, and I'm out of mushrooms to make more. Bring me "
                        + "what you can find.",
                RESTAURANT_ZONE_TYPE, "Bring 10 Red Mushrooms", mc("red_mushroom"), 10, 1,
                List.of(QuestReward.intangible("The stew is back on the menu")));
    }

    private static Identifier aHuntingTrip() {
        Identifier questId = id("a_hunting_trip");
        return QuestHooks.registerRegionKillQuest(questId, "A Hunting Trip",
                "Rabbit stew's on the menu tonight, but I'm out of rabbit. Go hunt a few down out "
                        + "past the settlement.",
                RESTAURANT_ZONE_TYPE, "Kill 6 Rabbits near {location}", mc("rabbit"), 6,
                "rabbit_hide_scraps", "Rabbit Hide Scraps", "Soft and well cared-for, right up until now.",
                false, 1,
                List.of(QuestReward.gold(12), QuestReward.intangible("Rabbit stew is back on the menu")));
    }

    private static Identifier melonForDessert() {
        Identifier questId = id("melon_for_dessert");
        return QuestHooks.registerGatherQuest(questId, "Melon for Dessert",
                "Chilled melon's a summer favorite here, and I'm fresh out. Bring me a good haul.",
                RESTAURANT_ZONE_TYPE, "Bring 10 Melon Slices", mc("melon_slice"), 10, 1,
                List.of(QuestReward.intangible("The summer dessert is back on the menu")));
    }

    private static Identifier milkForTheKitchen() {
        Identifier questId = id("milk_for_the_kitchen");
        return QuestHooks.registerGatherQuest(questId, "Milk for the Kitchen",
                "Half the recipes in this kitchen need fresh milk, and I'm completely out. Bring me "
                        + "a few buckets.",
                RESTAURANT_ZONE_TYPE, "Bring 8 Milk Buckets", mc("milk_bucket"), 8, 3,
                List.of(QuestReward.intangible("The kitchen is stocked again")));
    }

    private static Identifier pestControlInThePantry() {
        Identifier questId = id("pest_control_in_the_pantry");
        return QuestHooks.registerRegionKillQuest(questId, "Pest Control in the Pantry",
                "Silverfish have gotten into the pantry walls, and it's only a matter of time before "
                        + "they ruin the dry stores. Clear them out.",
                RESTAURANT_ZONE_TYPE, "Kill 5 Silverfish near {location}", mc("silverfish"), 5,
                "pantry_silverfish_husks", "Pantry Silverfish Husks", "Not exactly appetizing, but the problem's solved.",
                false, 1,
                List.of(QuestReward.gold(10), QuestReward.intangible("The pantry is pest-free again")));
    }

    /** See {@code QuestHooks#registerPlotUpgradeQuest}'s own doc -- one per profession, per the standing instruction in design-document.md's "Plot Upgrade quest" section. */
    private static Identifier expandingTheKitchen() {
        Identifier questId = id("expanding_the_kitchen");
        return QuestHooks.registerPlotUpgradeQuest(questId, "Expanding the Kitchen",
                "I'd like to expand this kitchen, but I'm short on raw building materials. Bring me "
                        + "what's missing and I'll make it worth your while.",
                RESTAURANT_ZONE_TYPE,
                List.of(QuestReward.gold(25), QuestReward.xp("mayor", 50),
                        QuestReward.intangible("The kitchen is ready to grow")));
    }
}
