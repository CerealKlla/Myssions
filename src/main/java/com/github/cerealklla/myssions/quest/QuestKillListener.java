package com.github.cerealklla.myssions.quest;

import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Tracks kill-type milestones (design doc item 7) -- on a {@link LivingDeathEvent} killed by a
 * player, checks every one of that player's in-progress quests for a {@link
 * QuestMilestone#killTargetEntityType()} matching the dead entity's type. Each qualifying kill
 * both reports milestone progress AND grants one unit of that milestone's own {@link
 * QuestMilestone#proofId()} item (via {@link ProofItemCatalog}), up to the milestone's {@code
 * requiredCount} -- so by the time the kill count is done, the player already holds exactly enough
 * proof to turn in, no separate drop-chance roll needed.
 */
public final class QuestKillListener {

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Identifier deadType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        var data = player.getData(ModAttachments.QUEST_DATA);
        for (QuestInstance instance : data.quests().values()) {
            if (instance.status() != QuestStatus.IN_PROGRESS) {
                continue;
            }
            QuestRegistry.get(instance.definitionId()).ifPresent(definition -> {
                for (QuestMilestone milestone : definition.milestones()) {
                    if (milestone.killTargetEntityType().isEmpty() || !milestone.killTargetEntityType().get().equals(deadType)) {
                        continue;
                    }
                    int current = instance.milestoneProgress().getOrDefault(milestone.id(), 0);
                    if (current >= milestone.requiredCount()) {
                        continue; // Already fully progressed -- no further proof to grant.
                    }
                    QuestEngine.reportMilestoneProgress(player, instance.definitionId(), milestone.id(), 1);
                    milestone.proofId().ifPresent(proofId -> {
                        ItemStack proof = ProofItemCatalog.create(proofId);
                        if (!player.getInventory().add(proof)) {
                            player.drop(proof, false);
                        }
                    });
                }
            });
        }
    }
}
