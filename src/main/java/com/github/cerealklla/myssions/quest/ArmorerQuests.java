package com.github.cerealklla.myssions.quest;

import java.util.List;

import net.minecraft.resources.Identifier;

/** Armorer quest content (2026-10-11 content-expansion pass) -- see {@code GrocerQuests}' own doc for the shared registration pattern. */
public final class ArmorerQuests {

    private static final String ARMORER_ZONE_TYPE = "blueprynts:armorer";

    private ArmorerQuests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(com.github.cerealklla.myssions.MyssionsMod.MODID, path);
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    public static void registerAll() {
        List<Identifier> quests = List.of(
                ironForArmor(), leatherPadding(), goldTrim(), fieldTestingZombies(), fieldTestingSkeletons(),
                fieldTestingSpiders(), diamondsForTheVanguard(), netheriteScrapRequest(), chainmailLinks(),
                wingsInTheDark(), expandingTheArmory());
        IssuerQuestPools.register(ARMORER_ZONE_TYPE, quests);
    }

    private static Identifier ironForArmor() {
        Identifier questId = id("iron_for_armor");
        return QuestHooks.registerGatherQuest(questId, "Iron for Armor",
                "Got three orders for plate armor and not nearly enough iron to fill them. Bring me a "
                        + "good stock and I'll make it worth your while.",
                ARMORER_ZONE_TYPE, "Bring 16 Iron Ingots", mc("iron_ingot"), 16, 3,
                List.of(QuestReward.intangible("Those armor orders can finally be filled")));
    }

    private static Identifier leatherPadding() {
        Identifier questId = id("leather_padding");
        return QuestHooks.registerGatherQuest(questId, "Leather Padding",
                "Can't make plate armor without the padding underneath, and I'm out of leather. "
                        + "Bring me what you can find.",
                ARMORER_ZONE_TYPE, "Bring 12 Leather", mc("leather"), 12, 2,
                List.of(QuestReward.intangible("The padding work can continue")));
    }

    private static Identifier goldTrim() {
        Identifier questId = id("gold_trim");
        return QuestHooks.registerGatherQuest(questId, "Gold Trim",
                "A noble customer wants gold trim on their new armor, and I'm not about to turn down "
                        + "that kind of coin. Bring me some gold.",
                ARMORER_ZONE_TYPE, "Bring 6 Gold Ingots", mc("gold_ingot"), 6, 5,
                List.of(QuestReward.intangible("The noble customer gets their gold trim")));
    }

    private static Identifier fieldTestingZombies() {
        Identifier questId = id("field_testing_zombies");
        return QuestHooks.registerRegionKillQuest(questId, "Field-Testing: Zombies",
                "New plate doesn't mean much until it's actually been hit. Go test a set against a "
                        + "few zombies and bring back proof it held up.",
                ARMORER_ZONE_TYPE, "Kill 6 Zombies near {location}", mc("zombie"), 6,
                "dented_test_plating", "Dented Test Plating", "Scratched up but intact -- a good sign.",
                false, 2,
                List.of(QuestReward.gold(17), QuestReward.intangible("The new plate passes its first real test")));
    }

    private static Identifier fieldTestingSkeletons() {
        Identifier questId = id("field_testing_skeletons");
        return QuestHooks.registerRegionKillQuest(questId, "Field-Testing: Skeletons",
                "Arrows are a different kind of test than a club to the ribs. Go see how the new set "
                        + "holds up against a few skeletons.",
                ARMORER_ZONE_TYPE, "Kill 6 Skeletons near {location}", mc("skeleton"), 6,
                "arrow_scored_plating", "Arrow-Scored Plating", "A few new scratches, nothing that went through.",
                false, 2,
                List.of(QuestReward.gold(17), QuestReward.intangible("The new plate holds up against arrows too")));
    }

    private static Identifier fieldTestingSpiders() {
        Identifier questId = id("field_testing_spiders");
        return QuestHooks.registerRegionKillQuest(questId, "Field-Testing: Spiders",
                "Poison's the real question with a new set -- does the padding actually hold against "
                        + "a bite. Go find out.",
                ARMORER_ZONE_TYPE, "Kill 6 Spiders near {location}", mc("spider"), 6,
                "bitten_test_padding", "Bitten Test Padding", "A few punctures, but nothing got through to skin.",
                false, 2,
                List.of(QuestReward.gold(17), QuestReward.intangible("The padding proves itself against poison")));
    }

    private static Identifier diamondsForTheVanguard() {
        Identifier questId = id("diamonds_for_the_vanguard");
        return QuestHooks.registerGatherQuest(questId, "Diamonds for the Vanguard",
                "The settlement's best guards deserve the best gear, and that means diamond. Bring me "
                        + "what you can find.",
                ARMORER_ZONE_TYPE, "Bring 3 Diamonds", mc("diamond"), 3, 10,
                List.of(QuestReward.intangible("The vanguard gets the gear they deserve")));
    }

    private static Identifier netheriteScrapRequest() {
        Identifier questId = id("netherite_scrap_request");
        return QuestHooks.registerBuyItemQuest(questId, "A Rare Request",
                "There's something from one of the other shops in town I need for a special "
                        + "commission. Fetch it for me and I'll make it worth your while.",
                ARMORER_ZONE_TYPE,
                List.of(QuestReward.intangible("The special commission can finally proceed")));
    }

    private static Identifier chainmailLinks() {
        Identifier questId = id("chainmail_links");
        return QuestHooks.registerGatherQuest(questId, "Chainmail Links",
                "Chainmail takes a lot more iron nuggets than most people realize, and I'm out. Bring "
                        + "me what you can find.",
                ARMORER_ZONE_TYPE, "Bring 24 Iron Nuggets", mc("iron_nugget"), 24, 1,
                List.of(QuestReward.intangible("The chainmail work can continue")));
    }

    private static Identifier wingsInTheDark() {
        Identifier questId = id("wings_in_the_dark");
        return QuestHooks.registerRegionKillQuest(questId, "Wings in the Dark",
                "Phantoms only show themselves after dark, which makes them exactly the kind of thing "
                        + "a new set of armor ought to be tested against. Go find some, after sundown.",
                ARMORER_ZONE_TYPE, "Kill 4 Phantoms near {location} at night", mc("phantom"), 4,
                "scored_phantom_membrane", "Scored Phantom Membrane", "Thin as paper, but the claw marks tell the real story.",
                true, 3,
                List.of(QuestReward.gold(21), QuestReward.intangible("The new set proves itself against the dark")));
    }

    /** See {@code QuestHooks#registerPlotUpgradeQuest}'s own doc -- one per profession, per the standing instruction in design-document.md's "Plot Upgrade quest" section. */
    private static Identifier expandingTheArmory() {
        Identifier questId = id("expanding_the_armory");
        return QuestHooks.registerPlotUpgradeQuest(questId, "Expanding the Armory",
                "I'd like to expand this armory, but I'm short on raw building materials. Bring me "
                        + "what's missing and I'll make it worth your while.",
                ARMORER_ZONE_TYPE,
                List.of(QuestReward.gold(25), QuestReward.xp("mayor", 50),
                        QuestReward.intangible("The armory is ready to grow")));
    }
}
