package com.github.cerealklla.myssions.quest;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import com.github.cerealklla.myssions.MyssionsMod;

/**
 * Lumberjack, Farmer, Mayor (issued via the Town Hall Plot Config Sign -- no literal Mayor NPC
 * exists, see below), and Traveling Merchant quest content -- the original four professions, each
 * now topped up to 10 real quests (2026-10-11 content-expansion pass). The other seven professions
 * (Grocer, Blacksmith, Armorer, Restaurant, Building Supplier, Stonemason, Tavern) have their own
 * sibling classes (`GrocerQuests`, etc., same package) for the same reason this class doesn't just
 * keep growing forever -- 90+ quest methods in one file stops being readable.
 *
 * <p><b>The "on-site" plot-anchored kill-quest category was removed entirely, 2026-10-11</b>
 * (explicit user correction) -- all 6 original on-site quests (two Lumberjack, four Farmer) spawned
 * their target mobs right at the issuing plot, inside a settlement, where Settlemynts' own town
 * guards reliably killed them before the player could, making the quest unfinishable by design. See
 * {@code context/decisions.md} for the removal entry and a deferred idea for a future
 * global-kill-count variant of this same shape. No quest registered by this class (or any sibling
 * profession class) spawns combat inside a settlement plot.
 *
 * <p>The actual hook/eligibility wiring for every real quest shape (region-kill, buy-item, escort,
 * talk-npc) now lives in the shared {@link QuestHooks} library, extracted from this class the same
 * day once seven more profession classes needed the same four shapes.
 *
 * <p>IDs/zone-type strings are plain string literals here on purpose, not references to
 * Settlemynts' own constants (e.g. {@code GhostTownHallCoreEntity.TOWN_HALL_ZONE_TYPE_ID}) --
 * this class is registered unconditionally from {@code MyssionsMod#commonSetup} on every server
 * regardless of whether Settlemynts is loaded, so it must never touch an optional mod's class at
 * all, the same classloading trap the root project's own CLAUDE.md documents.
 */
public final class NpcQuests {

    static final String LUMBERYARD_ZONE_TYPE = "blueprynts:lumberyard";
    static final String FARM_ZONE_TYPE = "blueprynts:farm";
    static final String TOWN_HALL_ZONE_TYPE = "settlemynts:town_hall";
    static final String TRAVELING_MERCHANT_ZONE_TYPE = "blueprynts:traveling_merchant_stall";

    private static Identifier MINECRAFT(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    private NpcQuests() {
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MyssionsMod.MODID, path);
    }

    public static void registerAll() {
        Identifier usefulErrand = aUsefulErrand();
        List<Identifier> lumberjackQuests = List.of(
                timberWolves(), webChokedGrove(), usefulErrand, silverfishInTheSawdust(), creepersInTheCoppice(),
                honeyForTheSaws(), freshSuppliesLumberjack(), sticksForKindling(), theGrovesOldGuardian(), applesForTheRoad(),
                growingTheYard());
        List<Identifier> farmerQuests = List.of(
                usefulErrand, aBiggerHarvest(), eggsForTheMarket(), rootVegetables(), woolForWinter(),
                pumpkinsForTheFair(), foxesInTheCornfield(), wolvesAtTheFenceLine(), milkForTheCellar(), aSecondOpinionFarmer(),
                expandingTheFarm());
        List<Identifier> mayorQuests = List.of(
                warbandAtTheGates(), clearTheCaves(), findOldHarlen(), scoutTheFrontier(),
                raidersOnTheOldRoad(), theWitchInTheSwamp(), beyondTheKnownMap(), theHermitsApprentice(),
                phantomsOverTheWalls(), drownedAtTheFord(), growingTheTownHall(), findATroll());
        List<Identifier> travelingMerchantQuests = List.of(
                merchantCaravanEscort(), aShortSupplyRun(), theLongHaul(), theBackRoads(), stockBeforeTheRun(),
                aTradeGoodRequest(), escortToTheCrossroads(), sparePartsForTheCart(), aRiskyShortcut(), restockingSilver(),
                settingUpShop());

        IssuerQuestPools.register(LUMBERYARD_ZONE_TYPE, lumberjackQuests);
        IssuerQuestPools.register(FARM_ZONE_TYPE, farmerQuests);
        IssuerQuestPools.register(TOWN_HALL_ZONE_TYPE, mayorQuests);
        IssuerQuestPools.register(TRAVELING_MERCHANT_ZONE_TYPE, travelingMerchantQuests);
    }

    // ---- Lumberjack ----

    private static Identifier timberWolves() {
        Identifier questId = id("timber_wolves");
        return QuestHooks.registerRegionKillQuest(questId, "Timber Wolves",
                "A pack of wolves has taken to stalking the tree line out past the north clearing. "
                        + "They've already spooked off two work crews -- thin the pack and I'll make it worth "
                        + "your while.",
                LUMBERYARD_ZONE_TYPE, "Kill 4 Wolves near {location}", MINECRAFT("wolf"), 4,
                "wolf_pelt_scraps", "Wolf Pelt Scraps", "Torn from a pelt, still smells like pine sap and old blood.",
                false, 2,
                List.of(QuestReward.gold(12), QuestReward.item(MINECRAFT("oak_log"), 16)));
    }

    private static Identifier webChokedGrove() {
        Identifier questId = id("web_choked_grove");
        return QuestHooks.registerRegionKillQuest(questId, "Web-Choked Grove",
                "There's a dense grove, thick with spiders, that's completely fouled with webbing -- we "
                        + "can't get a saw line through it. Clear enough of them out and bring back proof so I "
                        + "know it's safe to send a crew in.",
                LUMBERYARD_ZONE_TYPE, "Kill 5 Spiders near {location}", MINECRAFT("spider"), 5,
                "bundled_spider_silk", "Bundled Spider Silk", "A thick, sticky knot of webbing, still twitching faintly.",
                false, 2,
                List.of(QuestReward.gold(14), QuestReward.item(MINECRAFT("string"), 8)));
    }

    private static Identifier silverfishInTheSawdust() {
        Identifier questId = id("silverfish_in_the_sawdust");
        return QuestHooks.registerRegionKillQuest(questId, "Silverfish in the Sawdust",
                "Something's been getting into the log stacks and hollowing them out from the inside -- "
                        + "silverfish, by the look of the tunnels. Clear the infestation before it spreads to the "
                        + "whole yard.",
                LUMBERYARD_ZONE_TYPE, "Kill 6 Silverfish near {location}", MINECRAFT("silverfish"), 6,
                "silverfish_mandibles", "Silverfish Mandibles", "Small, sharp, and still twitching.",
                false, 1,
                List.of(QuestReward.gold(13)));
    }

    private static Identifier creepersInTheCoppice() {
        Identifier questId = id("creepers_in_the_coppice");
        return QuestHooks.registerRegionKillQuest(questId, "Creepers in the Coppice",
                "A work crew nearly lost a whole wagon of timber to a creeper that wandered right into "
                        + "the loading area. Clear a few out of the coppice before someone gets hurt.",
                LUMBERYARD_ZONE_TYPE, "Kill 4 Creepers near {location}", MINECRAFT("creeper"), 4,
                "creeper_fuse_caps", "Creeper Fuse Caps", "Still smells faintly of gunpowder.",
                false, 2,
                List.of(QuestReward.gold(16)));
    }

    private static Identifier honeyForTheSaws() {
        Identifier questId = id("honey_for_the_saws");
        return QuestHooks.registerGatherQuest(questId, "Honey for the Saws",
                "The crew swears a bit of honey keeps the saw blades from binding up in sap. I don't "
                        + "fully buy it, but they won't shut up about it -- bring me some and we'll see.",
                LUMBERYARD_ZONE_TYPE, "Bring 6 Honey Bottles", MINECRAFT("honey_bottle"), 6, 3,
                List.of());
    }

    private static Identifier freshSuppliesLumberjack() {
        Identifier questId = id("fresh_supplies_lumberjack");
        return QuestHooks.registerBuyItemQuest(questId, "Fresh Supplies",
                "Running low on something I'd rather not walk into town for myself -- fetch it for me "
                        + "and I'll make it worth your while.",
                LUMBERYARD_ZONE_TYPE,
                List.of());
    }

    private static Identifier sticksForKindling() {
        Identifier questId = id("sticks_for_kindling");
        return QuestHooks.registerGatherQuest(questId, "Sticks for Kindling",
                "Simple enough -- bring me a bundle of sticks for kindling the yard's fire pit. Doesn't "
                        + "matter where they came from.",
                LUMBERYARD_ZONE_TYPE, "Bring 32 Sticks", MINECRAFT("stick"), 32, 1,
                List.of());
    }

    private static Identifier theGrovesOldGuardian() {
        Identifier questId = id("the_groves_old_guardian");
        return QuestHooks.registerRegionKillQuest(questId, "The Grove's Old Guardian",
                "There's a stretch of grove the crew refuses to work past dark -- something tall and "
                        + "quiet watches from the tree line at night, and it's not a person. It only stirs after "
                        + "dark, so you'll need to go looking once the sun's down.",
                LUMBERYARD_ZONE_TYPE, "Kill 3 Endermen near {location} at night", MINECRAFT("enderman"), 3,
                "warped_tree_bark", "Warped Tree Bark", "Bark that's been teleported through more times than wood should be.",
                true, 3,
                List.of(QuestReward.gold(22)));
    }

    private static Identifier applesForTheRoad() {
        Identifier questId = id("apples_for_the_road");
        return QuestHooks.registerGatherQuest(questId, "Apples for the Road",
                "The crew goes through a lot of apples on a long haul out to the tree line. Bring me a "
                        + "basket's worth and I'll see you're paid for the trouble.",
                LUMBERYARD_ZONE_TYPE, "Bring 10 Apples", MINECRAFT("apple"), 10, 2,
                List.of());
    }

    /**
     * "Buy an item from a local shop" -- resolved dynamically from the issuing settlement's real
     * shop listings, never a static named item. See {@link QuestHooks#registerBuyItemHook}'s own doc
     * for the full design; shared by both the Lumberjack and Farmer pools since both lost their
     * original on-site quests in the 2026-10-11 removal.
     */
    private static Identifier aUsefulErrand() {
        Identifier questId = id("a_useful_errand");
        return QuestHooks.registerBuyItemQuest(questId, "A Useful Errand",
                "There's something I could use from one of the shops in town -- fetch it for me and "
                        + "I'll make it worth your while.",
                LUMBERYARD_ZONE_TYPE,
                List.of());
    }

    /** See {@code QuestHooks#registerPlotUpgradeQuest}'s own doc -- one per profession, per the standing instruction in design-document.md's "Plot Upgrade quest" section. */
    private static Identifier growingTheYard() {
        Identifier questId = id("growing_the_yard");
        return QuestHooks.registerPlotUpgradeQuest(questId, "Growing the Yard",
                "We're ready to expand this yard, but we're short on raw building materials. Bring me "
                        + "what's missing and I'll make it worth your while.",
                LUMBERYARD_ZONE_TYPE,
                List.of(QuestReward.gold(25), QuestReward.xp("mayor", 50)));
    }

    private static Identifier expandingTheFarm() {
        Identifier questId = id("expanding_the_farm");
        return QuestHooks.registerPlotUpgradeQuest(questId, "Expanding the Farm",
                "This farm could use more land under it, but we're short on raw building materials. "
                        + "Bring me what's missing and I'll make it worth your while.",
                FARM_ZONE_TYPE,
                List.of(QuestReward.gold(25), QuestReward.xp("mayor", 50)));
    }

    private static Identifier growingTheTownHall() {
        Identifier questId = id("growing_the_town_hall");
        return QuestHooks.registerPlotUpgradeQuest(questId, "Growing the Town",
                "The town's ready for its next stage, but the Town Hall is short on raw building "
                        + "materials. Bring what's missing and I'll make it worth your while.",
                TOWN_HALL_ZONE_TYPE,
                List.of(QuestReward.gold(30), QuestReward.xp("mayor", 60)));
    }

    private static Identifier settingUpShop() {
        Identifier questId = id("setting_up_shop");
        return QuestHooks.registerPlotUpgradeQuest(questId, "Setting Up Shop",
                "I'd like to expand this stall, but I'm short on raw building materials. Bring me "
                        + "what's missing and I'll make it worth your while.",
                TRAVELING_MERCHANT_ZONE_TYPE,
                List.of(QuestReward.gold(25), QuestReward.xp("mayor", 50)));
    }

    // ---- Farmer ----

    private static Identifier aBiggerHarvest() {
        Identifier questId = id("a_bigger_harvest");
        return QuestHooks.registerGatherQuest(questId, "A Bigger Harvest",
                "Got a buyer in town looking for more wheat than my own stores can spare this week. "
                        + "Bring me a good haul and I'll split the difference with you.",
                FARM_ZONE_TYPE, "Bring 24 Wheat", MINECRAFT("wheat"), 24, 1,
                List.of());
    }

    private static Identifier eggsForTheMarket() {
        Identifier questId = id("eggs_for_the_market");
        return QuestHooks.registerGatherQuest(questId, "Eggs for the Market",
                "My own hens can't keep up with what the market stall wants this week. Bring me a "
                        + "basket of eggs from wherever you can find them.",
                FARM_ZONE_TYPE, "Bring 16 Eggs", MINECRAFT("egg"), 16, 1,
                List.of());
    }

    private static Identifier rootVegetables() {
        Identifier questId = id("root_vegetables");
        QuestRegistry.register(new QuestDefinition(questId, "Root Vegetables",
                "Need a good stock of carrots and potatoes before the week's out -- bring me both and "
                        + "I'll pay fair for the lot.",
                FARM_ZONE_TYPE,
                List.of(
                        QuestMilestone.gather("gather_carrots", "Bring 12 Carrots", MINECRAFT("carrot"), 12, 1),
                        QuestMilestone.gather("gather_potatoes", "Bring 12 Potatoes", MINECRAFT("potato"), 12, 1)
                ),
                List.of(),
                QuestTimeLimit.NONE));
        return questId;
    }

    private static Identifier woolForWinter() {
        Identifier questId = id("wool_for_winter");
        return QuestHooks.registerGatherQuest(questId, "Wool for Winter",
                "Winter's coming and the settlement's going to want blankets. Bring me a good stock of "
                        + "wool and I'll see it's put to use.",
                FARM_ZONE_TYPE, "Bring 10 Wool", MINECRAFT("white_wool"), 10, 2,
                List.of());
    }

    private static Identifier pumpkinsForTheFair() {
        Identifier questId = id("pumpkins_for_the_fair");
        return QuestHooks.registerGatherQuest(questId, "Pumpkins for the Fall Fair",
                "The settlement's putting on a fall fair and somebody's got to bring the pumpkins. "
                        + "Might as well be you.",
                FARM_ZONE_TYPE, "Bring 8 Pumpkins", MINECRAFT("pumpkin"), 8, 2,
                List.of());
    }

    private static Identifier foxesInTheCornfield() {
        Identifier questId = id("foxes_in_the_cornfield");
        return QuestHooks.registerRegionKillQuest(questId, "Foxes in the Cornfield",
                "Something's been getting into the corn at night and leaving a mess of torn-up stalks "
                        + "behind. Thin out whatever's responsible before it costs me the whole field.",
                FARM_ZONE_TYPE, "Kill 5 Foxes near {location}", MINECRAFT("fox"), 5,
                "tufts_of_fox_fur", "Tufts of Fox Fur", "Soft, reddish, and covered in cornstalk dust.",
                false, 1,
                List.of(QuestReward.gold(10)));
    }

    private static Identifier wolvesAtTheFenceLine() {
        Identifier questId = id("wolves_at_the_fence_line");
        return QuestHooks.registerRegionKillQuest(questId, "Wolves at the Fence Line",
                "A pack's been testing the fence line at the edge of the property, and I'm not keen on "
                        + "finding out what happens if they get through. Drive them off.",
                FARM_ZONE_TYPE, "Kill 4 Wolves near {location}", MINECRAFT("wolf"), 4,
                "trampled_fence_rope", "Trampled Fence Rope", "Chewed through, not cut -- something with teeth did this.",
                false, 2,
                List.of(QuestReward.gold(12)));
    }

    private static Identifier milkForTheCellar() {
        Identifier questId = id("milk_for_the_cellar");
        return QuestHooks.registerGatherQuest(questId, "Milk for the Cellar",
                "Could use a good stock of fresh milk down in the cellar before it turns. Bring me a "
                        + "few buckets and I'll pay well for the trouble.",
                FARM_ZONE_TYPE, "Bring 6 Milk Buckets", MINECRAFT("milk_bucket"), 6, 4,
                List.of());
    }

    private static Identifier aSecondOpinionFarmer() {
        Identifier questId = id("a_second_opinion_farmer");
        return QuestHooks.registerBuyItemQuest(questId, "A Second Opinion",
                "There's something from one of the shops in town I keep meaning to pick up and never "
                        + "do. Fetch it for me and I'll make it worth your while.",
                FARM_ZONE_TYPE,
                List.of());
    }

    // ---- Mayor (issued via the Town Hall sign, no literal Mayor NPC exists -- see class doc) ----

    private static Identifier warbandAtTheGates() {
        Identifier questId = id("warband_at_the_gates");
        return QuestHooks.registerRegionKillQuest(questId, "A Warband at the Gates",
                "Travelers coming up the main road report a band of the dead massing not far out -- "
                        + "could be nothing, could be trouble for every plot in this settlement. I'd rather "
                        + "not find out the hard way. Clear them out and bring back proof it's done.",
                TOWN_HALL_ZONE_TYPE, "Kill 6 Zombies massing near {location}", MINECRAFT("zombie"), 6,
                "warband_insignia_scraps", "Warband Insignia Scraps", "Crude, torn cloth -- whoever wore this won't be marching on the settlement.",
                false, 2,
                List.of(QuestReward.gold(30)));
    }

    private static Identifier clearTheCaves() {
        Identifier questId = id("clear_the_caves");
        return QuestHooks.registerRegionKillQuest(questId, "Clear the Caves",
                "There's a cave system that's been spitting out more skeletons than usual lately, and a "
                        + "merchant caravan nearly didn't make it through last week. Thin them out before it "
                        + "costs this settlement its trade.",
                TOWN_HALL_ZONE_TYPE, "Kill 5 Skeletons near {location}", MINECRAFT("skeleton"), 5,
                "bone_fragments", "Arrow-Notched Bone Fragments", "Splintered and grooved -- these didn't come from just walking around.",
                false, 2,
                List.of(QuestReward.gold(28)));
    }

    /**
     * Design doc item 4's own worked example, verbatim: "a player takes a quest to go find a troll
     * within a cave." Real a vanilla Zombie standing in for "a troll" (no troll entity exists in
     * vanilla Minecraft) -- the "troll_cave" {@code WyrldRecipe} this needs must be created once,
     * server-side, via the real debug commands (see context/decisions.md for the exact commands
     * run and verified).
     */
    private static Identifier findATroll() {
        Identifier questId = id("find_a_troll");
        return QuestHooks.registerEntranceQuest(questId, "Find a Troll",
                "There's a troll denned up in a cave not far from here -- scared off two work crews "
                        + "already. Find its den, deal with it, and get yourself back out in one piece.",
                TOWN_HALL_ZONE_TYPE, "Defeat the troll", "Escape the cave",
                "troll_cave", "A troll's denned up nearby -- I've marked roughly where. Deal with it, then get out.",
                List.of(QuestReward.gold(25), QuestReward.xp("explorer", 40)));
    }

    private static Identifier raidersOnTheOldRoad() {
        Identifier questId = id("raiders_on_the_old_road");
        return QuestHooks.registerRegionKillQuest(questId, "Raiders on the Old Road",
                "A band of raiders has been shaking down travelers on the old road out of town. The "
                        + "settlement can't afford to lose what little trade still comes through that way.",
                TOWN_HALL_ZONE_TYPE, "Kill 6 Pillagers near {location}", MINECRAFT("pillager"), 6,
                "raider_banners", "Torn Raider Banners", "Crude cloth, stitched with a symbol nobody here recognizes.",
                false, 2,
                List.of(QuestReward.gold(32)));
    }

    private static Identifier theWitchInTheSwamp() {
        Identifier questId = id("the_witch_in_the_swamp");
        return QuestHooks.registerRegionKillQuest(questId, "The Witch in the Swamp",
                "Livestock have been turning up sick on the settlement's outskirts, and more than one "
                        + "person blames a witch said to be holed up nearby. Whether that's true or not, it's "
                        + "worth looking into.",
                TOWN_HALL_ZONE_TYPE, "Kill 3 Witches near {location}", MINECRAFT("witch"), 3,
                "shattered_potion_glass", "Shattered Potion Glass", "Still smells faintly of something that shouldn't be inhaled.",
                false, 3,
                List.of(QuestReward.gold(26)));
    }

    private static Identifier drownedAtTheFord() {
        Identifier questId = id("drowned_at_the_ford");
        return QuestHooks.registerRegionKillQuest(questId, "Drowned at the Ford",
                "The river crossing just outside the settlement has gotten dangerous -- something's "
                        + "pulling travelers under at the ford. Clear it out before someone doesn't make it "
                        + "across.",
                TOWN_HALL_ZONE_TYPE, "Kill 5 Drowned near {location}", MINECRAFT("drowned"), 5,
                "waterlogged_trinkets", "Waterlogged Trinkets", "Corroded almost past recognizing.",
                false, 2,
                List.of(QuestReward.gold(24)));
    }

    private static Identifier phantomsOverTheWalls() {
        Identifier questId = id("phantoms_over_the_walls");
        return QuestHooks.registerRegionKillQuest(questId, "Phantoms Over the Walls",
                "Watch has reported phantoms circling the settlement after dark, close enough to spook "
                        + "the guards on the wall. They only show themselves at night, so you'll have to go "
                        + "looking after sundown.",
                TOWN_HALL_ZONE_TYPE, "Kill 4 Phantoms near {location} at night", MINECRAFT("phantom"), 4,
                "tattered_phantom_membrane", "Tattered Phantom Membrane", "Papery and strange, like old parchment that was never quite alive.",
                true, 3,
                List.of(QuestReward.gold(27)));
    }

    // "2 miles" -- the user's own original phrasing for this quest style's search radius.
    private static final double UNBORN_TOWN_SEARCH_RADIUS_BLOCKS = 2 * 1609.0;

    /**
     * The "unborn town" quest style (design-document.md's quest taxonomy) -- see this class's
     * earlier doc for the full design. {@link #beyondTheKnownMap} is a second, differently-flavored
     * instance of the exact same mechanic.
     */
    private static Identifier scoutTheFrontier() {
        return registerUnbornTownQuest(id("scout_the_frontier"), "Scout the Frontier",
                "Traders keep talking about a town out past the edge of what we've properly mapped. "
                        + "Nobody from here has actually laid eyes on it. Go find it, see what's really out "
                        + "there, and bring back word.",
                15, "A Passing Scout",
                "Word is there's a town called ", ". Find it and report back.",
                "Nobody can point to anywhere unmapped worth scouting right now.");
    }

    private static Identifier beyondTheKnownMap() {
        return registerUnbornTownQuest(id("beyond_the_known_map"), "Beyond the Known Map",
                "Every settlement worth its salt knows roughly what's out past its own borders. We "
                        + "don't, not in this direction. Go find out what's actually out there and bring back "
                        + "word before someone else claims it first.",
                18, "A Returning Surveyor",
                "There's supposedly a place called ", " out that way. Go confirm it and report back.",
                "There's nowhere unclaimed left worth surveying right now.");
    }

    private static Identifier registerUnbornTownQuest(Identifier questId, String name, String description,
                                                         int goldReward, String reporterName,
                                                         String wordOfMessagePrefix, String wordOfMessageSuffix,
                                                         String nothingFoundMessage) {
        // Real live report, 2026-10-15: the Quest Log only ever showed the static "Return and
        // report what you found" -- never naming the actual discovered town (even though QuestEngine
        // #setHint below already records it as this instance's locationName, resolvable via the
        // {location} token every other location-aware quest already uses -- this one just never
        // used it), and "Return" read as "go back to where you accepted this," not "travel out to
        // the place you haven't been yet," which is what completing it actually requires (the
        // reporter NPC only spawns once the player gets within QuestReportBackTicker's own radius of
        // the real target -- standing back at the issuing Town Hall can never trigger it).
        QuestRegistry.register(new QuestDefinition(questId, name, description,
                TOWN_HALL_ZONE_TYPE,
                List.of(QuestMilestone.talk("report_back", "Travel to {location} and talk to " + reporterName)),
                List.of(QuestReward.gold(goldReward)),
                QuestTimeLimit.NONE));
        // Real live report, 2026-10-12: the reporter NPC used to spawn immediately at acceptance,
        // right on top of the player -- completable instantly with no exploration at all. It's now
        // spawned by QuestReportBackTicker, only once the player actually reaches the real target;
        // this registration just names who shows up when that happens. See that ticker's own doc.
        UnbornTownReporters.register(questId, reporterName);
        QuestAssignmentHooks.register(questId, (player, instanceId, assignedId, plotId) -> {
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                    || !com.github.cerealklla.myssions.bridge.CartographyrRegionBridge.isLoaded()) {
                return;
            }
            com.github.cerealklla.myssions.bridge.CartographyrRegionBridge.findUnbornTownNear(
                    level, player.blockPosition(), UNBORN_TOWN_SEARCH_RADIUS_BLOCKS
            ).ifPresentOrElse(target -> {
                String direction = QuestHooks.directionWord(player.blockPosition(), target.pos());
                QuestEngine.setHint(player, instanceId, target.pos(), target.dimension().identifier().toString(),
                        Optional.of(target.displayName()));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        wordOfMessagePrefix + target.displayName() + ", " + direction + " of here" + wordOfMessageSuffix));
            }, () -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal(nothingFoundMessage)));
        });
        return questId;
    }

    /**
     * Design doc items 8/9's real demo -- "go talk to a named NPC who hides at night." See {@link
     * QuestHooks#registerTalkNpcHook}'s own doc. {@link #theHermitsApprentice} is a second,
     * differently-flavored instance of the same mechanic.
     */
    private static Identifier findOldHarlen() {
        Identifier questId = id("find_old_harlen");
        return QuestHooks.registerTalkQuest(questId, "Find Old Harlen",
                "There's an old hermit who knows these parts better than anyone -- goes by Harlen. "
                        + "Skittish type, only ever seen during the day; word is he's been spotted nearby. "
                        + "Track him down and see what he has to say.",
                TOWN_HALL_ZONE_TYPE, "Find and talk to Old Harlen", "Old Harlen", true,
                "The Mayor gives you a rough idea where to look for Harlen -- check your map.",
                List.of(QuestReward.gold(8)));
    }

    private static Identifier theHermitsApprentice() {
        Identifier questId = id("the_hermits_apprentice");
        return QuestHooks.registerTalkQuest(questId, "The Hermit's Apprentice",
                "Old Harlen's taken on an apprentice, apparently -- a young woman named Dara, just as "
                        + "skittish as he is. She's been spotted out past the settlement; go see if she'll talk "
                        + "to you.",
                TOWN_HALL_ZONE_TYPE, "Find and talk to Dara", "Dara", false,
                "The Mayor gives you a rough idea where to look for Dara -- check your map.",
                List.of(QuestReward.gold(9)));
    }

    // ---- Traveling Merchant (caravan-raid quest style) ----

    /**
     * The first caravan-raid quest (design-document.md's quest taxonomy) -- a straight 3-waypoint
     * road in one random direction from the Traveling Merchant's own stall. Bandits (reusing
     * vanilla's Pillager) are an optional, bonus-only encounter -- the quest's only real required
     * milestone is the caravan reaching the end of the road alive. See {@link
     * QuestHooks#registerEscortHook}'s own doc for how the raid itself is gated.
     */
    private static Identifier merchantCaravanEscort() {
        Identifier questId = id("merchant_caravan_escort");
        return QuestHooks.registerEscortQuest(questId, "Guard the Caravan",
                "I've got a caravan heading out along the road, and the last one that went out "
                        + "light on guards never made it. Stay close the whole way -- if bandits don't see "
                        + "anyone watching the caravan, they'll hit it. Keep them off and see it through safely.",
                TRAVELING_MERCHANT_ZONE_TYPE, 3, 3,
                List.of(QuestReward.gold(20)));
    }

    private static Identifier aShortSupplyRun() {
        Identifier questId = id("a_short_supply_run");
        return QuestHooks.registerEscortQuest(questId, "A Short Supply Run",
                "Nothing fancy -- just a short hop out to resupply and back. Shouldn't take long, but "
                        + "an extra set of eyes never hurts.",
                TRAVELING_MERCHANT_ZONE_TYPE, 2, 2,
                List.of(QuestReward.gold(12)));
    }

    private static Identifier theLongHaul() {
        Identifier questId = id("the_long_haul");
        return QuestHooks.registerEscortQuest(questId, "The Long Haul",
                "This is the big one -- a long haul out and back, further than I'd ever risk without "
                        + "someone watching the road. Stay close the whole way.",
                TRAVELING_MERCHANT_ZONE_TYPE, 5, 4,
                List.of(QuestReward.gold(35)));
    }

    private static Identifier theBackRoads() {
        Identifier questId = id("the_back_roads");
        return QuestHooks.registerEscortQuest(questId, "The Back Roads",
                "I'd rather take the back roads this time -- fewer eyes, but fewer friendly ones too if "
                        + "something goes wrong. Keep the caravan safe.",
                TRAVELING_MERCHANT_ZONE_TYPE, 4, 3,
                List.of(QuestReward.gold(26)));
    }

    private static Identifier escortToTheCrossroads() {
        Identifier questId = id("escort_to_the_crossroads");
        return QuestHooks.registerEscortQuest(questId, "Escort to the Crossroads",
                "Just need the caravan out to the crossroads and back -- not far, but it's exactly the "
                        + "kind of stretch bandits like to watch.",
                TRAVELING_MERCHANT_ZONE_TYPE, 3, 3,
                List.of(QuestReward.gold(20)));
    }

    private static Identifier aRiskyShortcut() {
        Identifier questId = id("a_risky_shortcut");
        return QuestHooks.registerEscortQuest(questId, "A Risky Shortcut",
                "There's a shortcut that'd save real time, but it's exactly the kind of stretch bandits "
                        + "love to watch. Worth it if you can keep the caravan safe.",
                TRAVELING_MERCHANT_ZONE_TYPE, 2, 5,
                List.of(QuestReward.gold(24)));
    }

    private static Identifier stockBeforeTheRun() {
        Identifier questId = id("stock_before_the_run");
        return QuestHooks.registerGatherQuest(questId, "Stock Before the Run",
                "Can't head out again until I've restocked -- bring me a good haul of wheat and I'll "
                        + "make it worth the trouble.",
                TRAVELING_MERCHANT_ZONE_TYPE, "Bring 16 Wheat", MINECRAFT("wheat"), 16, 1,
                List.of());
    }

    private static Identifier sparePartsForTheCart() {
        Identifier questId = id("spare_parts_for_the_cart");
        return QuestHooks.registerGatherQuest(questId, "Spare Parts for the Cart",
                "The cart's seen better days -- could use some iron to shore it up before the next long "
                        + "haul. Bring me what you can.",
                TRAVELING_MERCHANT_ZONE_TYPE, "Bring 8 Iron Ingots", MINECRAFT("iron_ingot"), 8, 3,
                List.of());
    }

    private static Identifier aTradeGoodRequest() {
        Identifier questId = id("a_trade_good_request");
        return QuestHooks.registerBuyItemQuest(questId, "A Trade Good Request",
                "There's something from one of the shops in town I'd like to carry on the next run -- "
                        + "fetch it for me and I'll make it worth your while.",
                TRAVELING_MERCHANT_ZONE_TYPE,
                List.of());
    }

    private static Identifier restockingSilver() {
        Identifier questId = id("restocking_silver");
        return QuestHooks.registerBuyItemQuest(questId, "Restocking Silver",
                "Lost a buyer last trip because I didn't have the right goods on hand. Fetch me "
                        + "something from one of the shops in town so it doesn't happen again.",
                TRAVELING_MERCHANT_ZONE_TYPE,
                List.of());
    }
}
