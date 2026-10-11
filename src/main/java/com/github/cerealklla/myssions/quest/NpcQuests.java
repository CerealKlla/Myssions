package com.github.cerealklla.myssions.quest;

import java.util.List;

import net.minecraft.resources.Identifier;

import com.github.cerealklla.myssions.MyssionsMod;

/**
 * Ten real fetch-quest definitions (design doc item 7) given out by a settlement's Tier 1 economy
 * workers -- Lumberjack and Farmer (the two confirmed NPC-staffed Tier 1 structure types, see
 * {@code zone.PlotNpc}'s own subclasses in Settlemynts), plus two "Mayor" quests. There is no
 * literal Mayor NPC anywhere in the suite (confirmed before writing this -- "Mayor" is a player
 * role only), so those two are issued via a shift+right-click on the settlement's Town Hall Plot
 * Config Sign instead (see {@code bridge.QuestSignInteractListener}), a deliberate stand-in flagged
 * here and in context/decisions.md for whenever a real Mayor NPC exists.
 *
 * <p><b>Kill-tracking is still NOT geo-fenced</b> -- the actual counting ({@code
 * quest.QuestKillListener}) matches a kill anywhere, not just at the named location, for every
 * quest here including the six below with a real waypoint hint. This is an honest, flagged
 * simplification, not an oversight; real region-arrival checking is separate, still-unbuilt work.
 *
 * <p><b>Six on-site quests now DO point a real waypoint at their issuing plot</b> (added
 * 2026-10-10, real user feedback -- a milestone saying "near the fields" meant nothing once the
 * player left and forgot where that was, and since they accepted the quest standing right there,
 * they should already know exactly where it is): the four Farmer quests and two of the four
 * Lumberjack quests ({@link #fuseHappyNuisance}/{@link #nightThieves}) register {@link
 * #registerOnSiteHook}, which sets a real hint position + {@link QuestInstance#locationName()} via
 * {@code bridge.SettlemyntsPlotLocationBridge}, with full knowledge granted immediately (not a
 * rough bearing) since the player was standing right there. The other two Lumberjack quests ({@link
 * #timberWolves}/{@link #webChokedGrove}) and both Mayor quests are deliberately left as genuinely
 * vague, distant wilderness threats ("the Whispering Pines," "a cave system near the trade road")
 * -- not retrofitted, a different category of quest on purpose.
 *
 * <p>IDs/zone-type strings are plain string literals here on purpose, not references to
 * Settlemynts' own constants (e.g. {@code GhostTownHallCoreEntity.TOWN_HALL_ZONE_TYPE_ID}) --
 * this class is registered unconditionally from {@code MyssionsMod#commonSetup} on every server
 * regardless of whether Settlemynts is loaded, so it must never touch an optional mod's class at
 * all, the same classloading trap the root project's own CLAUDE.md documents.
 */
public final class NpcQuests {

    private static final String LUMBERYARD_ZONE_TYPE = "blueprynts:lumberyard";
    private static final String FARM_ZONE_TYPE = "blueprynts:farm";
    private static final String TOWN_HALL_ZONE_TYPE = "settlemynts:town_hall";

    private NpcQuests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MyssionsMod.MODID, path);
    }

    public static void registerAll() {
        List<Identifier> lumberjackQuests = List.of(
                timberWolves(), webChokedGrove(), fuseHappyNuisance(), nightThieves());
        List<Identifier> farmerQuests = List.of(
                nightRaiders(), skeletonScarecrows(), burrowingNuisance(), midnightHowlers());
        List<Identifier> mayorQuests = List.of(
                warbandAtTheGates(), clearTheCaves(), findOldHarlen());

        IssuerQuestPools.register(LUMBERYARD_ZONE_TYPE, lumberjackQuests);
        IssuerQuestPools.register(FARM_ZONE_TYPE, farmerQuests);
        IssuerQuestPools.register(TOWN_HALL_ZONE_TYPE, mayorQuests);
    }

    // ---- Lumberjack ----

    private static Identifier timberWolves() {
        Identifier questId = id("timber_wolves");
        String proofId = "wolf_pelt_scraps";
        ProofItemCatalog.register(proofId, "Wolf Pelt Scraps",
                "Torn from a pelt, still smells like pine sap and old blood.");
        QuestRegistry.register(new QuestDefinition(questId, "Timber Wolves",
                "A pack of wolves has taken to stalking the tree line out past the north clearing, "
                        + "about a day's walk through the Whispering Pines. They've already spooked off two "
                        + "work crews -- thin the pack and I'll make it worth your while.",
                LUMBERYARD_ZONE_TYPE,
                List.of(
                        QuestMilestone.kill("kill_wolves", "Kill 4 Wolves near the tree line",
                                Identifier.fromNamespaceAndPath("minecraft", "wolf"), 4, proofId),
                        QuestMilestone.deliver("deliver_pelts", "Bring back 4 Wolf Pelt Scraps as proof", proofId, 4)
                ),
                List.of(QuestReward.gold(12), QuestReward.item(Identifier.fromNamespaceAndPath("minecraft", "oak_log"), 16),
                        QuestReward.intangible("The crews can work the north clearing again")),
                QuestTimeLimit.NONE));
        return questId;
    }

    private static Identifier webChokedGrove() {
        Identifier questId = id("web_choked_grove");
        String proofId = "bundled_spider_silk";
        ProofItemCatalog.register(proofId, "Bundled Spider Silk",
                "A thick, sticky knot of webbing, still twitching faintly.");
        QuestRegistry.register(new QuestDefinition(questId, "Web-Choked Grove",
                "There's a dense grove east of here, thick with spiders, that's completely fouled with "
                        + "webbing -- we can't get a saw line through it. Clear enough of them out and bring back "
                        + "proof so I know it's safe to send a crew in.",
                LUMBERYARD_ZONE_TYPE,
                List.of(
                        QuestMilestone.kill("kill_spiders", "Kill 5 Spiders in the grove",
                                Identifier.fromNamespaceAndPath("minecraft", "spider"), 5, proofId),
                        QuestMilestone.deliver("deliver_silk", "Bring back 5 bundles of Spider Silk as proof", proofId, 5)
                ),
                List.of(QuestReward.gold(14), QuestReward.item(Identifier.fromNamespaceAndPath("minecraft", "string"), 8),
                        QuestReward.intangible("The grove is safe to fell again")),
                QuestTimeLimit.NONE));
        return questId;
    }

    private static Identifier fuseHappyNuisance() {
        Identifier questId = id("fuse_happy_nuisance");
        String proofId = "scorched_fuse_casings";
        ProofItemCatalog.register(proofId, "Scorched Fuse Casings",
                "Blackened and still warm. Whatever this came from, it isn't walking away.");
        QuestRegistry.register(new QuestDefinition(questId, "Fuse-Happy Nuisance",
                "We've lost two whole stacks of cut logs to creepers wandering into the yard and going "
                        + "off. I don't care how you deal with them, just thin them out before they cost us a "
                        + "third stack.",
                LUMBERYARD_ZONE_TYPE,
                List.of(
                        QuestMilestone.kill("kill_creepers", "Kill 3 Creepers near my lumberyard in {location}",
                                Identifier.fromNamespaceAndPath("minecraft", "creeper"), 3, proofId),
                        QuestMilestone.deliver("deliver_fuses", "Bring back 3 Scorched Fuse Casings as proof", proofId, 3)
                ),
                List.of(QuestReward.gold(10), QuestReward.item(Identifier.fromNamespaceAndPath("minecraft", "gunpowder"), 4),
                        QuestReward.intangible("No more log stacks lost to green nuisances")),
                QuestTimeLimit.NONE));
        registerOnSiteHook(questId);
        return questId;
    }

    private static Identifier nightThieves() {
        Identifier questId = id("night_thieves");
        String proofId = "teleport_residue_vials";
        ProofItemCatalog.register(proofId, "Teleport Residue Vials",
                "A faint violet shimmer still clings to whatever's bottled in here.");
        QuestRegistry.register(new QuestDefinition(questId, "Night Thieves",
                "Something's been moving stacked logs around in the dead of night -- turns out it's "
                        + "endermen, carrying off whatever isn't nailed down. Deal with a few of them and "
                        + "collect proof so I believe you actually did it.",
                LUMBERYARD_ZONE_TYPE,
                List.of(
                        QuestMilestone.kill("kill_endermen", "Kill 3 Endermen disturbing my log stacks in {location}",
                                Identifier.fromNamespaceAndPath("minecraft", "enderman"), 3, proofId),
                        QuestMilestone.deliver("deliver_residue", "Bring back 3 Teleport Residue Vials as proof", proofId, 3)
                ),
                List.of(QuestReward.gold(18), QuestReward.intangible("The log stacks stop moving themselves at night")),
                QuestTimeLimit.NONE));
        registerOnSiteHook(questId);
        return questId;
    }

    // ---- Farmer ----

    private static Identifier nightRaiders() {
        Identifier questId = id("night_raiders");
        String proofId = "trampled_field_rags";
        ProofItemCatalog.register(proofId, "Trampled Field Rags",
                "Muddy, torn, and smells like a freshly ruined crop row.");
        QuestRegistry.register(new QuestDefinition(questId, "Night Raiders",
                "Zombies keep wandering in from the tree line after dark and trampling a night's worth "
                        + "of growth flat. Thin them out before I lose another planting.",
                FARM_ZONE_TYPE,
                List.of(
                        QuestMilestone.kill("kill_zombies", "Kill 4 Zombies near my fields in {location}",
                                Identifier.fromNamespaceAndPath("minecraft", "zombie"), 4, proofId),
                        QuestMilestone.deliver("deliver_rags", "Bring back 4 Trampled Field Rags as proof", proofId, 4)
                ),
                List.of(QuestReward.gold(10), QuestReward.item(Identifier.fromNamespaceAndPath("minecraft", "bread"), 6),
                        QuestReward.intangible("The fields can grow a night through undisturbed")),
                QuestTimeLimit.NONE));
        registerOnSiteHook(questId);
        return questId;
    }

    private static Identifier skeletonScarecrows() {
        Identifier questId = id("skeleton_scarecrows");
        String proofId = "snapped_bowstrings";
        ProofItemCatalog.register(proofId, "Snapped Bowstrings",
                "Still coiled tight from the shot that never got fired again.");
        QuestRegistry.register(new QuestDefinition(questId, "Skeleton Scarecrows",
                "Skeletons have taken to sniping at my farmhands from the edge of the field -- scared "
                        + "off a whole shift yesterday. Drop a few of them and bring back their bowstrings so I "
                        + "know they won't be shooting at anyone else.",
                FARM_ZONE_TYPE,
                List.of(
                        QuestMilestone.kill("kill_skeletons", "Kill 4 Skeletons near my field's edge in {location}",
                                Identifier.fromNamespaceAndPath("minecraft", "skeleton"), 4, proofId),
                        QuestMilestone.deliver("deliver_bowstrings", "Bring back 4 Snapped Bowstrings as proof", proofId, 4)
                ),
                List.of(QuestReward.gold(11), QuestReward.item(Identifier.fromNamespaceAndPath("minecraft", "wheat_seeds"), 12),
                        QuestReward.intangible("The farmhands feel safe working the edge rows again")),
                QuestTimeLimit.NONE));
        registerOnSiteHook(questId);
        return questId;
    }

    private static Identifier burrowingNuisance() {
        Identifier questId = id("burrowing_nuisance");
        String proofId = "carapace_shards";
        ProofItemCatalog.register(proofId, "Carapace Shards",
                "Hard, segmented, and still faintly twitching when you're not looking directly at it.");
        QuestRegistry.register(new QuestDefinition(questId, "Burrowing Nuisance",
                "The old stone irrigation ditch under the farm is infested with silverfish -- they keep "
                        + "boring into the channel lining and we're losing water faster than we can dig it back "
                        + "out. Clear them out and bring back proof.",
                FARM_ZONE_TYPE,
                List.of(
                        QuestMilestone.kill("kill_silverfish", "Kill 6 Silverfish in my irrigation ditch in {location}",
                                Identifier.fromNamespaceAndPath("minecraft", "silverfish"), 6, proofId),
                        QuestMilestone.deliver("deliver_carapace", "Bring back 6 Carapace Shards as proof", proofId, 6)
                ),
                List.of(QuestReward.gold(13), QuestReward.item(Identifier.fromNamespaceAndPath("minecraft", "carrot"), 10),
                        QuestReward.intangible("The irrigation channel holds water properly again")),
                QuestTimeLimit.NONE));
        registerOnSiteHook(questId);
        return questId;
    }

    private static Identifier midnightHowlers() {
        Identifier questId = id("midnight_howlers");
        String proofId = "coop_raider_fur";
        ProofItemCatalog.register(proofId, "Coop-Raider Fur Tufts",
                "Matted fur, still smells faintly of feathers.");
        QuestRegistry.register(new QuestDefinition(questId, "Midnight Howlers",
                "A pack of wolves has figured out where the chicken coop is and keeps raiding it after "
                        + "midnight. I'm down to half my flock. Deal with them before there's nothing left to "
                        + "raid.",
                FARM_ZONE_TYPE,
                List.of(
                        QuestMilestone.kill("kill_wolves", "Kill 3 Wolves near my coop in {location}",
                                Identifier.fromNamespaceAndPath("minecraft", "wolf"), 3, proofId),
                        QuestMilestone.deliver("deliver_fur", "Bring back 3 Coop-Raider Fur Tufts as proof", proofId, 3)
                ),
                List.of(QuestReward.gold(10), QuestReward.item(Identifier.fromNamespaceAndPath("minecraft", "egg"), 8),
                        QuestReward.intangible("The flock finally gets a quiet night")),
                QuestTimeLimit.NONE));
        registerOnSiteHook(questId);
        return questId;
    }

    // ---- Mayor (issued via the Town Hall sign, no literal Mayor NPC exists -- see class doc) ----

    private static Identifier warbandAtTheGates() {
        Identifier questId = id("warband_at_the_gates");
        String proofId = "warband_insignia_scraps";
        ProofItemCatalog.register(proofId, "Warband Insignia Scraps",
                "Crude, torn cloth -- whoever wore this won't be marching on the settlement.");
        QuestRegistry.register(new QuestDefinition(questId, "A Warband at the Gates",
                "Travelers coming up the main road report a band of the dead massing not far out -- "
                        + "could be nothing, could be trouble for every plot in this settlement. I'd rather "
                        + "not find out the hard way. Clear them out and bring back proof it's done.",
                TOWN_HALL_ZONE_TYPE,
                List.of(
                        QuestMilestone.kill("kill_zombies", "Kill 6 Zombies massing near the road",
                                Identifier.fromNamespaceAndPath("minecraft", "zombie"), 6, proofId),
                        QuestMilestone.deliver("deliver_insignia", "Bring back 6 Warband Insignia Scraps as proof", proofId, 6)
                ),
                List.of(QuestReward.gold(30), QuestReward.intangible("The settlement's main road is safe to travel again")),
                QuestTimeLimit.NONE));
        return questId;
    }

    private static Identifier clearTheCaves() {
        Identifier questId = id("clear_the_caves");
        String proofId = "bone_fragments";
        ProofItemCatalog.register(proofId, "Arrow-Notched Bone Fragments",
                "Splintered and grooved -- these didn't come from just walking around.");
        QuestRegistry.register(new QuestDefinition(questId, "Clear the Caves",
                "There's a cave system near the trade road that's been spitting out more skeletons than "
                        + "usual lately, and a merchant caravan nearly didn't make it through last week. Thin "
                        + "them out before it costs this settlement its trade.",
                TOWN_HALL_ZONE_TYPE,
                List.of(
                        QuestMilestone.kill("kill_skeletons", "Kill 5 Skeletons from the cave system",
                                Identifier.fromNamespaceAndPath("minecraft", "skeleton"), 5, proofId),
                        QuestMilestone.deliver("deliver_bones", "Bring back 5 Arrow-Notched Bone Fragments as proof", proofId, 5)
                ),
                List.of(QuestReward.gold(28), QuestReward.intangible("The trade road stays open")),
                QuestTimeLimit.NONE));
        return questId;
    }

    /**
     * Registers a real on-site hint for a quest whose threat is literally at the issuing NPC's own
     * plot (not some vague distant location) -- added 2026-10-10, real user feedback: a milestone
     * just saying "near the fields" means nothing once the player has forgotten where that was, and
     * since they were standing right there when they accepted it, they already know exactly where
     * it is (full knowledge, not a rough bearing -- unlike {@link #findOldHarlen}'s reveal). Sets
     * both the hint position (the plot's own Plot Config Sign location, via {@code
     * bridge.SettlemyntsPlotLocationBridge}) and {@link QuestInstance#locationName()} for this
     * quest's own {@code {location}}-tokened milestone text (see {@code quest.QuestText}). A no-op
     * if Settlemynts isn't loaded or the plot can't be resolved -- the milestone text's {@code
     * {location}} token then just falls back to a generic phrase.
     */
    private static void registerOnSiteHook(Identifier questId) {
        QuestAssignmentHooks.register(questId, (player, instanceId, assignedId, plotId) -> {
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                    || !com.github.cerealklla.myssions.bridge.SettlemyntsPlotLocationBridge.isLoaded()) {
                return;
            }
            com.github.cerealklla.myssions.bridge.SettlemyntsPlotLocationBridge.resolve(level, plotId).ifPresent(loc -> {
                net.minecraft.server.level.ServerLevel hintLevel = level.getServer().getLevel(loc.dimension());
                if (hintLevel == null) {
                    return;
                }
                java.util.Optional<String> settlementName = loc.settlementName().isBlank()
                        ? java.util.Optional.empty() : java.util.Optional.of(loc.settlementName());
                QuestEngine.setHint(player, instanceId, loc.pos(), loc.dimension().identifier().toString(), settlementName);

                if (com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.isLoaded()) {
                    long placeId = QuestWaypointTicker.placeIdFor(instanceId);
                    com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.grantKnowledge(player, placeId, true, true, true);
                }
            });
        });
    }

    /**
     * Design doc items 8/9's real demo -- "go talk to a named NPC who hides at night." Spawns a
     * {@link QuestNpcEntity} right next to the player the moment they accept, via {@link
     * QuestAssignmentHooks} (not geo-fenced to any particular location, same honest simplification
     * as the kill quests above -- real "the NPC is actually somewhere specific" placement needs the
     * still-unbuilt waypoint system). Visible only to the assignee ({@code ownerPlayerId}) and only
     * during the day ({@code hidesAtNight = true}) -- see {@link QuestNpcEntity}'s own doc for
     * exactly what that means mechanically.
     */
    private static Identifier findOldHarlen() {
        Identifier questId = id("find_old_harlen");
        QuestRegistry.register(new QuestDefinition(questId, "Find Old Harlen",
                "There's an old hermit who knows these parts better than anyone -- goes by Harlen. "
                        + "Skittish type, only ever seen during the day; word is he's been spotted nearby. "
                        + "Track him down and see what he has to say.",
                TOWN_HALL_ZONE_TYPE,
                List.of(QuestMilestone.talk("talk_to_harlen", "Find and talk to Old Harlen")),
                List.of(QuestReward.gold(8), QuestReward.intangible("Old Harlen's thanks")),
                QuestTimeLimit.NONE));
        QuestAssignmentHooks.register(questId, (player, instanceId, assignedId, plotId) -> {
            if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                // A real target somewhere out past the player, not right at their feet -- the whole
                // point of the waypoint hint (design doc item 2) is having to go find it.
                double angle = player.getRandom().nextDouble() * Math.PI * 2;
                double distance = 80 + player.getRandom().nextDouble() * 120;
                net.minecraft.core.BlockPos roughSpot = player.blockPosition().offset(
                        (int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
                int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, roughSpot.getX(), roughSpot.getZ());
                net.minecraft.core.BlockPos truePos = new net.minecraft.core.BlockPos(roughSpot.getX(), groundY, roughSpot.getZ());

                QuestEngine.setHint(player, instanceId, truePos, level.dimension().identifier().toString());
                QuestNpcSpawner.spawn(level, truePos, player.getUUID(), instanceId, true, "Old Harlen");

                if (com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.isLoaded()) {
                    long placeId = QuestWaypointTicker.placeIdFor(instanceId);
                    // Simulates "the Mayor gave you a rough description" -- a real bearing and
                    // distance, but not the exact spot -- rather than starting from zero knowledge.
                    com.github.cerealklla.myssions.bridge.LyfeWaypointBridge.grantKnowledge(player, placeId, true, true, false);
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "The Mayor gives you a rough idea where to look for Harlen -- check your map."));
            }
        });
        return questId;
    }
}
