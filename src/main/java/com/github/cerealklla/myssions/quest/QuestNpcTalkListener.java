package com.github.cerealklla.myssions.quest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Right-clicking a {@link QuestNpcEntity} talks to it -- always registered (this is Myssions' own entity type, no optional dependency involved). */
public final class QuestNpcTalkListener {

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getTarget() instanceof QuestNpcEntity npc)) {
            return;
        }
        if (npc.talkTo(player)) {
            event.setCanceled(true);
        }
    }
}
