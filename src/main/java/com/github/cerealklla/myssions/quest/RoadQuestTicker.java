package com.github.cerealklla.myssions.quest;

import com.github.cerealklla.myssions.bridge.SettlemyntsRoadQuestBridge;
import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives every in-progress road-connection instance -- added 2026-10-11, see {@code
 * RoadQuestLogic}'s own doc for the full design. Each cycle, for every such instance: asks
 * Settlemynts for the player's live Highway Stake progress (see {@code
 * bridge.SettlemyntsRoadQuestBridge}); the instant both settlement ends are connected by one
 * contiguous stake chain, finalizes immediately -- "the quest will immediately complete and pop up
 * the reward screen," per the user's own spec, no separate player action needed.
 *
 * <p>Live "crossed-out/red" milestone-text feedback while the road is still under construction is
 * a client-side draw-time concern (comparing the live stake count against {@code
 * QuestInstance#roadOptimalStakeCap()}) -- not yet wired into {@code client.QuestLogScreen}/{@code
 * client.ActiveQuestOverlay}; this ticker only handles the server-side completion/payout, not that
 * display polish.
 */
public final class RoadQuestTicker {

    private static final int INTERVAL_TICKS = 40; // 2 seconds -- a live stake count check does real geometry work, no need for tighter polling.

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL_TICKS != 0 || !SettlemyntsRoadQuestBridge.isLoaded()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(ServerPlayer player) {
        var data = player.getData(ModAttachments.QUEST_DATA);
        for (QuestInstance instance : java.util.List.copyOf(data.quests().values())) {
            if (instance.status() != QuestStatus.IN_PROGRESS || !instance.definitionId().equals(RoadQuestLogic.DEFINITION_ID)
                    || instance.roadQuest().originPlaceId().isEmpty() || instance.knowledgePlaceId().isEmpty()) {
                continue;
            }
            if (!(player.level() instanceof ServerLevel level) || instance.hintPos().isEmpty()) {
                continue;
            }
            long originPlaceId = instance.roadQuest().originPlaceId().get();
            long destinationPlaceId = instance.knowledgePlaceId().get();
            BlockPos nearHint = instance.hintPos().get();

            var progress = SettlemyntsRoadQuestBridge.checkProgress(level, instance.instanceId(), nearHint, originPlaceId, destinationPlaceId);
            if (!progress.connected()) {
                continue;
            }
            finalizeConnection(level, player, instance, originPlaceId, destinationPlaceId, progress.stakeCount());
        }
    }

    private void finalizeConnection(ServerLevel level, ServerPlayer player, QuestInstance instance, long originPlaceId, long destinationPlaceId, int stakeCount) {
        RoadQuestRegistry.get(level.getServer()).markConnected(originPlaceId, destinationPlaceId, stakeCount);

        // Three cascading efficiency tiers (see RoadQuestDefinitions' own doc, "500 base + 300 more
        // max, spread across" slightly/more/very efficient) -- each tier's own flat bonus gold pays
        // through the normal reward-pickup path (QuestEngine#addPlainMilestoneBonus) once its
        // milestone's completion is reported here. Tier 2's threshold is the real midpoint between
        // the minimum possible stake count and the original "efficient" cap, so a road built right
        // at the theoretical minimum satisfies all three tiers (the full 300) and one built just
        // under the loose cap only satisfies the first.
        int minStakes = instance.roadQuest().minStakes();
        int optimalCap = instance.roadQuest().optimalStakeCap();
        int midpoint = Math.round((minStakes + optimalCap) / 2.0f);
        if (stakeCount <= optimalCap) {
            QuestEngine.reportMilestoneProgress(player, instance.instanceId(), "efficient_road_tier1", 1);
        }
        if (stakeCount <= midpoint) {
            QuestEngine.reportMilestoneProgress(player, instance.instanceId(), "efficient_road_tier2", 1);
        }
        if (stakeCount <= minStakes) {
            QuestEngine.reportMilestoneProgress(player, instance.instanceId(), "efficient_road_tier3", 1);
        }
        QuestEngine.reportMilestoneProgress(player, instance.instanceId(), "connect_road", 1);

        int unused = SettlemyntsRoadQuestBridge.buybackUnusedStakes(player, instance.instanceId());
        if (unused > 0) {
            int goldAmount = (int) Math.round(unused * RoadQuestLogic.BUYBACK_GOLD_PER_UNUSED_STAKE);
            if (goldAmount > 0) {
                ItemStack nuggets = new ItemStack(Items.GOLD_NUGGET, goldAmount);
                if (!player.getInventory().add(nuggets)) {
                    player.drop(nuggets, false);
                }
            }
            player.sendSystemMessage(Component.literal(
                    "The road sinks in! Bought back " + unused + " unused Highway Stake" + (unused == 1 ? "" : "s") + "."));
        }

        // Explicit user request, 2026-10-13: the ghost stake/torch markers should disappear once the
        // road is actually finished -- both cosmetic clutter at that point, and a real interaction
        // hazard left alone (right-clicking a still-live connected stake un-paves part of the road,
        // via HighwayStakeEntity#snapAllConnections). No terrain revert happens here -- see
        // SettlemyntsRoadQuestBridge#hideCompletedMarkers's own doc.
        instance.hintPos().ifPresent(hint -> SettlemyntsRoadQuestBridge.hideCompletedMarkers(level, instance.instanceId(), hint));
    }
}
