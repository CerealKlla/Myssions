package com.github.cerealklla.myssions.quest;

import java.util.List;

import net.minecraft.resources.Identifier;

/** Miner quest content (added for Settlemynts' new Mineshaft plot type) -- see {@code GrocerQuests}' own doc for the shared registration pattern. */
public final class MinerQuests {

    private static final String MINESHAFT_ZONE_TYPE = "blueprynts:mineshaft";

    private MinerQuests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(com.github.cerealklla.myssions.MyssionsMod.MODID, path);
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    public static void registerAll() {
        List<Identifier> quests = List.of(
                retainingWallStone(), coalForTheLanterns(), copperForThePipes(), ironOreRun(), aGlimmerOfGold(),
                redstoneForTheRigging(), lapisForTheSurveyor(), caveInBelow(), somethingInTheDark(), aUsefulErrandMiner());
        IssuerQuestPools.register(MINESHAFT_ZONE_TYPE, quests);
    }

    private static Identifier retainingWallStone() {
        Identifier questId = id("retaining_wall_stone");
        return QuestHooks.registerGatherQuest(questId, "Retaining Wall Stone",
                "Shaft's shedding loose rock faster than I can shore it up. Bring me a good stock of "
                        + "cobblestone and I'll get the walls sorted.",
                MINESHAFT_ZONE_TYPE, "Bring 48 Cobblestone", mc("cobblestone"), 48, 1,
                List.of(QuestReward.intangible("The shaft walls stop crumbling")));
    }

    private static Identifier coalForTheLanterns() {
        Identifier questId = id("coal_for_the_lanterns");
        return QuestHooks.registerGatherQuest(questId, "Coal for the Lanterns",
                "Can't see a hand in front of my face down there without a full rack of lanterns, and "
                        + "I'm burning through coal faster than I can dig it myself. Bring me a stock.",
                MINESHAFT_ZONE_TYPE, "Bring 32 Coal", mc("coal"), 32, 1,
                List.of(QuestReward.intangible("The tunnels stay lit")));
    }

    private static Identifier copperForThePipes() {
        Identifier questId = id("copper_for_the_pipes");
        return QuestHooks.registerGatherQuest(questId, "Copper for the Pipes",
                "Got a drainage problem two levels down and not nearly enough copper on hand to fix "
                        + "it. Bring me what you can find.",
                MINESHAFT_ZONE_TYPE, "Bring 16 Raw Copper", mc("raw_copper"), 16, 2,
                List.of(QuestReward.intangible("The lower level stops flooding")));
    }

    private static Identifier ironOreRun() {
        Identifier questId = id("iron_ore_run");
        return QuestHooks.registerGatherQuest(questId, "Iron Ore Run",
                "Rail carts don't fix themselves, and I'd rather smelt my own iron than buy finished "
                        + "ingots. Bring me the raw ore and I'll handle the rest.",
                MINESHAFT_ZONE_TYPE, "Bring 16 Raw Iron", mc("raw_iron"), 16, 3,
                List.of(QuestReward.intangible("The rail carts are running again")));
    }

    private static Identifier aGlimmerOfGold() {
        Identifier questId = id("a_glimmer_of_gold");
        return QuestHooks.registerGatherQuest(questId, "A Glimmer of Gold",
                "Struck a thin vein further down but it's slow going alone. Bring me what gold you "
                        + "find and I'll make it worth your while.",
                MINESHAFT_ZONE_TYPE, "Bring 6 Raw Gold", mc("raw_gold"), 6, 6,
                List.of(QuestReward.intangible("The vein's worth keeps working it")));
    }

    private static Identifier redstoneForTheRigging() {
        Identifier questId = id("redstone_for_the_rigging");
        return QuestHooks.registerGatherQuest(questId, "Redstone for the Rigging",
                "The cart rigging needs a redstone circuit if it's ever going to run itself, and I'm "
                        + "short on dust. Bring me a good stock.",
                MINESHAFT_ZONE_TYPE, "Bring 12 Redstone", mc("redstone"), 12, 2,
                List.of(QuestReward.intangible("The rigging finally gets its circuit")));
    }

    private static Identifier lapisForTheSurveyor() {
        Identifier questId = id("lapis_for_the_surveyor");
        return QuestHooks.registerGatherQuest(questId, "Lapis for the Surveyor",
                "Need a good stock of lapis to pay the surveyor for mapping the next level down. "
                        + "Bring me what you can find.",
                MINESHAFT_ZONE_TYPE, "Bring 8 Lapis Lazuli", mc("lapis_lazuli"), 8, 3,
                List.of(QuestReward.intangible("The surveyor maps the next level down")));
    }

    private static Identifier caveInBelow() {
        Identifier questId = id("cave_in_below");
        return QuestHooks.registerRegionKillQuest(questId, "Cave-In Below",
                "A nest of cave spiders has moved into a side tunnel near {location}, and the poison's "
                        + "keeping my crew from working it. Clear them out.",
                MINESHAFT_ZONE_TYPE, "Kill 5 Cave Spiders near {location}", mc("cave_spider"), 5,
                "shaft_chitin_fragments", "Shaft Chitin Fragments", "Brittle and pale -- these never saw daylight.",
                false, 2,
                List.of(QuestReward.gold(19), QuestReward.intangible("The crew goes back to work on that tunnel")));
    }

    private static Identifier somethingInTheDark() {
        Identifier questId = id("something_in_the_dark");
        return QuestHooks.registerRegionKillQuest(questId, "Something in the Dark",
                "Something's been shambling up out of the deep tunnels near {location} after dark and "
                        + "spooking the night shift. Go deal with it.",
                MINESHAFT_ZONE_TYPE, "Kill 4 Zombies near {location} at night", mc("zombie"), 4,
                "tunnel_grave_dust", "Tunnel Grave Dust", "Fine, pale, and nothing like the rock dust that usually settles down there.",
                true, 3,
                List.of(QuestReward.gold(21), QuestReward.intangible("The night shift stops hearing things")));
    }

    private static Identifier aUsefulErrandMiner() {
        Identifier questId = id("a_useful_errand_miner");
        return QuestHooks.registerBuyItemQuest(questId, "A Useful Errand",
                "There's something from one of the other shops in town I need down in the shaft. "
                        + "Fetch it for me and I'll make it worth your while.",
                MINESHAFT_ZONE_TYPE,
                List.of(QuestReward.intangible("The shaft gets what it needed")));
    }
}
