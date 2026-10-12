package com.github.cerealklla.myssions.quest;

import java.util.List;

import net.minecraft.resources.Identifier;

/** Building Supplier quest content (2026-10-11 content-expansion pass) -- see {@code GrocerQuests}' own doc for the shared registration pattern. */
public final class BuildingSupplierQuests {

    private static final String BUILDING_SUPPLIER_ZONE_TYPE = "blueprynts:building_supplier";

    private BuildingSupplierQuests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(com.github.cerealklla.myssions.MyssionsMod.MODID, path);
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    public static void registerAll() {
        List<Identifier> quests = List.of(
                logsForTheYard(), stoneStockpile(), sandForGlass(), clayForBricks(), woolForInsulation(),
                glassPanes(), gravelRun(), aSpecialOrderSupplier(), aNetherBrickRequest(), terracottaForTrim(),
                expandingTheYard());
        IssuerQuestPools.register(BUILDING_SUPPLIER_ZONE_TYPE, quests);
    }

    private static Identifier logsForTheYard() {
        Identifier questId = id("logs_for_the_yard");
        return QuestHooks.registerGatherQuest(questId, "Logs for the Yard",
                "The yard's running low on raw logs and I've got orders to fill. Bring me a good "
                        + "haul and I'll make it worth your while.",
                BUILDING_SUPPLIER_ZONE_TYPE, "Bring 32 Oak Logs", mc("oak_log"), 32, 1,
                List.of(QuestReward.intangible("The yard's log stock is replenished")));
    }

    private static Identifier stoneStockpile() {
        Identifier questId = id("stone_stockpile");
        return QuestHooks.registerGatherQuest(questId, "Stone Stockpile",
                "Can't fill a building order without stone, and I'm down to scraps. Bring me a good "
                        + "stockpile.",
                BUILDING_SUPPLIER_ZONE_TYPE, "Bring 48 Stone", mc("stone"), 48, 1,
                List.of(QuestReward.intangible("The stone stockpile is replenished")));
    }

    private static Identifier sandForGlass() {
        Identifier questId = id("sand_for_glass");
        return QuestHooks.registerGatherQuest(questId, "Sand for Glass",
                "Glass orders are piling up and I'm out of sand to melt down. Bring me a good haul.",
                BUILDING_SUPPLIER_ZONE_TYPE, "Bring 32 Sand", mc("sand"), 32, 1,
                List.of(QuestReward.intangible("The glass orders can finally be filled")));
    }

    private static Identifier clayForBricks() {
        Identifier questId = id("clay_for_bricks");
        return QuestHooks.registerGatherQuest(questId, "Clay for Bricks",
                "Brick orders need clay, and I'm fresh out. Bring me what you can find.",
                BUILDING_SUPPLIER_ZONE_TYPE, "Bring 24 Clay Balls", mc("clay_ball"), 24, 1,
                List.of(QuestReward.intangible("The brick orders can finally be filled")));
    }

    private static Identifier woolForInsulation() {
        Identifier questId = id("wool_for_insulation");
        return QuestHooks.registerGatherQuest(questId, "Wool for Insulation",
                "Winter builds need good insulation, and wool's the easiest way to get it. Bring me "
                        + "a good stock.",
                BUILDING_SUPPLIER_ZONE_TYPE, "Bring 12 Wool", mc("white_wool"), 12, 2,
                List.of(QuestReward.intangible("The winter builds get their insulation")));
    }

    private static Identifier glassPanes() {
        Identifier questId = id("glass_panes");
        return QuestHooks.registerGatherQuest(questId, "Glass Panes",
                "A customer wants a whole wall of windows, and I don't have nearly enough glass on "
                        + "hand. Bring me a good stock.",
                BUILDING_SUPPLIER_ZONE_TYPE, "Bring 24 Glass", mc("glass"), 24, 1,
                List.of(QuestReward.intangible("The window order can finally be filled")));
    }

    private static Identifier gravelRun() {
        Identifier questId = id("gravel_run");
        return QuestHooks.registerGatherQuest(questId, "Gravel Run",
                "Need a good stock of gravel for some roadwork the settlement's planning. Bring me "
                        + "what you can find.",
                BUILDING_SUPPLIER_ZONE_TYPE, "Bring 32 Gravel", mc("gravel"), 32, 1,
                List.of(QuestReward.intangible("The roadwork can proceed")));
    }

    private static Identifier aSpecialOrderSupplier() {
        Identifier questId = id("a_special_order_supplier");
        return QuestHooks.registerBuyItemQuest(questId, "A Special Order",
                "There's something from one of the other shops in town a customer's been asking "
                        + "after. Fetch it for me and I'll make it worth your while.",
                BUILDING_SUPPLIER_ZONE_TYPE,
                List.of(QuestReward.intangible("That customer finally gets their special order")));
    }

    private static Identifier aNetherBrickRequest() {
        Identifier questId = id("a_nether_brick_request");
        return QuestHooks.registerBuyItemQuest(questId, "An Unusual Request",
                "A customer wants something I can't exactly dig up myself -- probably best found at "
                        + "one of the other shops in town. Fetch it for me and I'll make it worth your while.",
                BUILDING_SUPPLIER_ZONE_TYPE,
                List.of(QuestReward.intangible("The unusual request gets filled after all")));
    }

    private static Identifier terracottaForTrim() {
        Identifier questId = id("terracotta_for_trim");
        return QuestHooks.registerGatherQuest(questId, "Terracotta for Trim",
                "A builder wants terracotta trim on their new place, and I'm out. Bring me what you "
                        + "can find.",
                BUILDING_SUPPLIER_ZONE_TYPE, "Bring 16 Terracotta", mc("terracotta"), 16, 1,
                List.of(QuestReward.intangible("The trim order can finally be filled")));
    }

    /** See {@code QuestHooks#registerPlotUpgradeQuest}'s own doc -- one per profession, per the standing instruction in design-document.md's "Plot Upgrade quest" section. */
    private static Identifier expandingTheYard() {
        Identifier questId = id("expanding_the_supplier_yard");
        return QuestHooks.registerPlotUpgradeQuest(questId, "Expanding the Yard",
                "I'd like to expand this yard, but I'm short on raw building materials myself. Bring "
                        + "me what's missing and I'll make it worth your while.",
                BUILDING_SUPPLIER_ZONE_TYPE,
                List.of(QuestReward.gold(25), QuestReward.xp("mayor", 50),
                        QuestReward.intangible("The yard is ready to grow")));
    }
}
