package com.github.cerealklla.myssions.quest;

import java.util.List;

import net.minecraft.resources.Identifier;

/** Blacksmith quest content (2026-10-11 content-expansion pass) -- see {@code GrocerQuests}' own doc for the shared registration pattern. */
public final class BlacksmithQuests {

    private static final String BLACKSMITH_ZONE_TYPE = "blueprynts:blacksmith";

    private BlacksmithQuests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(com.github.cerealklla.myssions.MyssionsMod.MODID, path);
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    public static void registerAll() {
        List<Identifier> quests = List.of(
                ironForTheForge(), coalForTheFire(), goldForFineWork(), copperStock(), flintForTools(),
                theMinersTunnel(), caveSpidersUnderfoot(), rawOreRun(), aSpecialOrderBlacksmith(), leatherForHilts(),
                expandingTheForge());
        IssuerQuestPools.register(BLACKSMITH_ZONE_TYPE, quests);
    }

    private static Identifier ironForTheForge() {
        Identifier questId = id("iron_for_the_forge");
        return QuestHooks.registerGatherQuest(questId, "Iron for the Forge",
                "The forge is hungry and my own stock won't last the week. Bring me a good haul of "
                        + "iron and I'll pay fair for it.",
                BLACKSMITH_ZONE_TYPE, "Bring 12 Iron Ingots", mc("iron_ingot"), 12, 3,
                List.of(QuestReward.intangible("The forge stays fed for another week")));
    }

    private static Identifier coalForTheFire() {
        Identifier questId = id("coal_for_the_fire");
        return QuestHooks.registerGatherQuest(questId, "Coal for the Fire",
                "Can't keep the forge lit without fuel, and I'm burning through coal faster than I can "
                        + "dig it myself. Bring me a good stock.",
                BLACKSMITH_ZONE_TYPE, "Bring 24 Coal", mc("coal"), 24, 1,
                List.of(QuestReward.intangible("The forge stays lit")));
    }

    private static Identifier goldForFineWork() {
        Identifier questId = id("gold_for_fine_work");
        return QuestHooks.registerGatherQuest(questId, "Gold for Fine Work",
                "Got a commission for some fine gold work and not nearly enough gold on hand. Bring me "
                        + "what you can find.",
                BLACKSMITH_ZONE_TYPE, "Bring 6 Gold Ingots", mc("gold_ingot"), 6, 5,
                List.of(QuestReward.intangible("The fine-work commission can proceed")));
    }

    private static Identifier copperStock() {
        Identifier questId = id("copper_stock");
        return QuestHooks.registerGatherQuest(questId, "Copper Stock",
                "Copper's been selling faster than I can smelt it. Bring me a good stock of ingots and "
                        + "I'll make it worth your while.",
                BLACKSMITH_ZONE_TYPE, "Bring 12 Copper Ingots", mc("copper_ingot"), 12, 2,
                List.of(QuestReward.intangible("The copper stock is replenished")));
    }

    private static Identifier flintForTools() {
        Identifier questId = id("flint_for_tools");
        return QuestHooks.registerGatherQuest(questId, "Flint for Tools",
                "Need a good stock of flint for some of the finer tool work. Bring me what you can "
                        + "find and I'll pay fair for it.",
                BLACKSMITH_ZONE_TYPE, "Bring 16 Flint", mc("flint"), 16, 1,
                List.of(QuestReward.intangible("The tool work can proceed")));
    }

    private static Identifier theMinersTunnel() {
        Identifier questId = id("the_miners_tunnel");
        return QuestHooks.registerRegionKillQuest(questId, "The Miners' Tunnel",
                "A supply tunnel out past the forge has gotten overrun with the dead, and the miners "
                        + "refuse to go back in until it's cleared. Clear it out.",
                BLACKSMITH_ZONE_TYPE, "Kill 5 Zombies near {location}", mc("zombie"), 5,
                "tunnel_scrap_metal", "Tunnel Scrap Metal", "Rusted and bent, pulled off something that used to be useful.",
                false, 2,
                List.of(QuestReward.gold(18), QuestReward.intangible("The miners go back to work")));
    }

    private static Identifier caveSpidersUnderfoot() {
        Identifier questId = id("cave_spiders_underfoot");
        return QuestHooks.registerRegionKillQuest(questId, "Cave Spiders Underfoot",
                "Cave spiders have moved into the old mine shaft the forge draws ore from, and the "
                        + "poison's keeping everyone out. Clear them out.",
                BLACKSMITH_ZONE_TYPE, "Kill 5 Cave Spiders near {location}", mc("cave_spider"), 5,
                "venom_sac_fragments", "Venom Sac Fragments", "Best handled with gloves on.",
                false, 2,
                List.of(QuestReward.gold(19), QuestReward.intangible("The mine shaft is safe to work again")));
    }

    private static Identifier rawOreRun() {
        Identifier questId = id("raw_ore_run");
        return QuestHooks.registerGatherQuest(questId, "Raw Ore Run",
                "Rather smelt it myself than buy finished ingots -- bring me raw iron ore and I'll "
                        + "handle the rest.",
                BLACKSMITH_ZONE_TYPE, "Bring 16 Raw Iron", mc("raw_iron"), 16, 2,
                List.of(QuestReward.intangible("The forge has plenty to smelt")));
    }

    private static Identifier aSpecialOrderBlacksmith() {
        Identifier questId = id("a_special_order_blacksmith");
        return QuestHooks.registerBuyItemQuest(questId, "A Special Order",
                "There's something from one of the other shops in town I need for a commission. "
                        + "Fetch it for me and I'll make it worth your while.",
                BLACKSMITH_ZONE_TYPE,
                List.of(QuestReward.intangible("The commission can finally proceed")));
    }

    private static Identifier leatherForHilts() {
        Identifier questId = id("leather_for_hilts");
        return QuestHooks.registerGatherQuest(questId, "Leather for Hilts",
                "Every sword needs a good grip, and I'm out of leather for the hilts. Bring me what "
                        + "you can find.",
                BLACKSMITH_ZONE_TYPE, "Bring 10 Leather", mc("leather"), 10, 2,
                List.of(QuestReward.intangible("The hilt-wrapping work can continue")));
    }

    /** See {@code QuestHooks#registerPlotUpgradeQuest}'s own doc -- one per profession, per the standing instruction in design-document.md's "Plot Upgrade quest" section. */
    private static Identifier expandingTheForge() {
        Identifier questId = id("expanding_the_forge");
        return QuestHooks.registerPlotUpgradeQuest(questId, "Expanding the Forge",
                "I'd like to expand this forge, but I'm short on raw building materials. Bring me "
                        + "what's missing and I'll make it worth your while.",
                BLACKSMITH_ZONE_TYPE,
                List.of(QuestReward.gold(25), QuestReward.xp("mayor", 50),
                        QuestReward.intangible("The forge is ready to grow")));
    }
}
