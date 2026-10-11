package com.github.cerealklla.myssions.bridge;

import com.github.cerealklla.myssions.quest.QuestTurnIn;
import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.settlemynts.founding.GhostTownHallCoreEntity;
import com.github.cerealklla.settlemynts.plotsign.PlotConfigSignBlockEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Shift+right-click a Town Hall plot's Plot Config Sign to talk Mayor quests -- a stand-in for a
 * literal Mayor NPC, which doesn't exist anywhere in this suite ("Mayor" is purely a player role,
 * confirmed before building this). Optional soft dependency, only constructed/registered behind
 * {@code ModList.get().isLoaded("settlemynts")}, same shape as {@link QuestNpcInteractListener}.
 * Shift-gated for the same reason -- a plain right-click on this sign already opens its own Main
 * Menu.
 */
public final class QuestSignInteractListener {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof PlotConfigSignBlockEntity sign) || sign.plotId() == null) {
            return;
        }
        Settlemynts.findPlotRecord(level, sign.plotId()).ifPresent(plot -> {
            if (!plot.zoneTypeId().equals(GhostTownHallCoreEntity.TOWN_HALL_ZONE_TYPE_ID)) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            QuestTurnIn.interact(player, plot.zoneTypeId().toString());
        });
    }
}
