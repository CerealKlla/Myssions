package com.github.cerealklla.myssions.bridge;

import com.github.cerealklla.myssions.quest.QuestTurnIn;
import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.settlemynts.zone.PlotNpc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Shift+right-click a Settlemynts plot NPC (e.g. a Lumberyard/Farm worker) to talk quests --
 * optional soft dependency, only ever constructed/registered from {@code MyssionsMod}'s
 * constructor behind {@code ModList.get().isLoaded("settlemynts")}, same isolation convention as
 * every other optional bridge in this suite (see {@code bridge.LyfeXpBridge}'s own doc).
 *
 * <p><b>Deliberately shift-gated, not a plain right-click</b> -- Settlemynts' own {@code
 * resident.VillagerShopInteractListener} already claims a plain right-click on any {@link PlotNpc}
 * with a real Shop to open that Shop; a Lumberjack/Farmer worker always has one. Shift+right-click
 * is a genuinely distinct vanilla gesture, matching the same "gate behind sneak to avoid colliding
 * with an existing plain-click action" precedent Lyfe's own {@code knowledge.SignListener} already
 * uses for its map-right-click.
 */
public final class QuestNpcInteractListener {

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getTarget() instanceof PlotNpc npc) || npc.plotId() == null) {
            return;
        }
        Settlemynts.findPlotRecord(level, npc.plotId()).ifPresent(plot -> {
            event.setCanceled(true);
            QuestTurnIn.quickInteract(player, npc.plotId(), plot.zoneTypeId().toString());
        });
    }
}
