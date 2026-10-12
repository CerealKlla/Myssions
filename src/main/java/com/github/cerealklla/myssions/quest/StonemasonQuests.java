package com.github.cerealklla.myssions.quest;

import java.util.List;

import net.minecraft.resources.Identifier;

/** Stonemason quest content (2026-10-11 content-expansion pass) -- see {@code GrocerQuests}' own doc for the shared registration pattern. */
public final class StonemasonQuests {

    private static final String STONEMASON_ZONE_TYPE = "blueprynts:stonemason";

    private StonemasonQuests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(com.github.cerealklla.myssions.MyssionsMod.MODID, path);
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    public static void registerAll() {
        List<Identifier> quests = List.of(
                stoneForTheChisel(), graniteSupply(), dioriteSupply(), andesiteStock(), deepslateForFineCarving(),
                quartzForAccents(), clearingTheQuarry(), caveSpidersInThePit(), aRareStone(), amethystForAccents(),
                expandingTheQuarry());
        IssuerQuestPools.register(STONEMASON_ZONE_TYPE, quests);
    }

    private static Identifier stoneForTheChisel() {
        Identifier questId = id("stone_for_the_chisel");
        return QuestHooks.registerGatherQuest(questId, "Stone for the Chisel",
                "Every carving starts as a plain block of stone, and I'm running low. Bring me a "
                        + "good stockpile.",
                STONEMASON_ZONE_TYPE, "Bring 40 Stone", mc("stone"), 40, 1,
                List.of());
    }

    private static Identifier graniteSupply() {
        Identifier questId = id("granite_supply");
        return QuestHooks.registerGatherQuest(questId, "Granite Supply",
                "A customer wants a granite facade and I'm completely out. Bring me what you can "
                        + "find.",
                STONEMASON_ZONE_TYPE, "Bring 24 Granite", mc("granite"), 24, 1,
                List.of());
    }

    private static Identifier dioriteSupply() {
        Identifier questId = id("diorite_supply");
        return QuestHooks.registerGatherQuest(questId, "Diorite Supply",
                "Diorite's popular for trim work lately, and I'm fresh out. Bring me a good stock.",
                STONEMASON_ZONE_TYPE, "Bring 24 Diorite", mc("diorite"), 24, 1,
                List.of());
    }

    private static Identifier andesiteStock() {
        Identifier questId = id("andesite_stock");
        return QuestHooks.registerGatherQuest(questId, "Andesite Stock",
                "Need a good stock of andesite for a wall commission. Bring me what you can find.",
                STONEMASON_ZONE_TYPE, "Bring 24 Andesite", mc("andesite"), 24, 1,
                List.of());
    }

    private static Identifier deepslateForFineCarving() {
        Identifier questId = id("deepslate_for_fine_carving");
        return QuestHooks.registerGatherQuest(questId, "Deepslate for Fine Carving",
                "Deepslate holds a much finer edge than regular stone, and I'm out. Bring me a good "
                        + "stock.",
                STONEMASON_ZONE_TYPE, "Bring 24 Deepslate", mc("deepslate"), 24, 2,
                List.of());
    }

    private static Identifier quartzForAccents() {
        Identifier questId = id("quartz_for_accents");
        return QuestHooks.registerGatherQuest(questId, "Quartz for Accents",
                "A commission calls for bright quartz accents, and I'm out of the raw stuff. Bring "
                        + "me what you can find.",
                STONEMASON_ZONE_TYPE, "Bring 12 Quartz", mc("quartz"), 12, 2,
                List.of());
    }

    private static Identifier clearingTheQuarry() {
        Identifier questId = id("clearing_the_quarry");
        return QuestHooks.registerRegionKillQuest(questId, "Clearing the Quarry",
                "The quarry's gotten overrun with the dead again, and the crew refuses to go back "
                        + "down until it's cleared. Handle it.",
                STONEMASON_ZONE_TYPE, "Kill 5 Zombies near {location}", mc("zombie"), 5,
                "quarry_grave_dust", "Quarry Grave Dust", "Fine and pale, nothing like the stone dust down there usually looks like.",
                false, 2,
                List.of(QuestReward.gold(18)));
    }

    private static Identifier caveSpidersInThePit() {
        Identifier questId = id("cave_spiders_in_the_pit");
        return QuestHooks.registerRegionKillQuest(questId, "Cave Spiders in the Pit",
                "A nest of cave spiders has taken over one of the deeper pits, and nobody wants to "
                        + "work down there until it's cleared out.",
                STONEMASON_ZONE_TYPE, "Kill 5 Cave Spiders near {location}", mc("cave_spider"), 5,
                "pit_venom_sacs", "Pit Venom Sacs", "Best carried carefully.",
                false, 2,
                List.of(QuestReward.gold(19)));
    }

    private static Identifier aRareStone() {
        Identifier questId = id("a_rare_stone");
        return QuestHooks.registerBuyItemQuest(questId, "A Rare Stone",
                "There's a particular stone from one of the other shops in town I need for a "
                        + "commission. Fetch it for me and I'll make it worth your while.",
                STONEMASON_ZONE_TYPE,
                List.of());
    }

    private static Identifier amethystForAccents() {
        Identifier questId = id("amethyst_for_accents");
        return QuestHooks.registerGatherQuest(questId, "Amethyst for Accents",
                "A wealthy customer wants amethyst accents worked into their new facade. Bring me "
                        + "what you can find.",
                STONEMASON_ZONE_TYPE, "Bring 8 Amethyst Shards", mc("amethyst_shard"), 8, 3,
                List.of());
    }

    /** See {@code QuestHooks#registerPlotUpgradeQuest}'s own doc -- one per profession, per the standing instruction in design-document.md's "Plot Upgrade quest" section. */
    private static Identifier expandingTheQuarry() {
        Identifier questId = id("expanding_the_quarry");
        return QuestHooks.registerPlotUpgradeQuest(questId, "Expanding the Quarry",
                "I'd like to expand this quarry, but I'm short on raw building materials. Bring me "
                        + "what's missing and I'll make it worth your while.",
                STONEMASON_ZONE_TYPE,
                List.of(QuestReward.gold(25), QuestReward.xp("mayor", 50)));
    }
}
