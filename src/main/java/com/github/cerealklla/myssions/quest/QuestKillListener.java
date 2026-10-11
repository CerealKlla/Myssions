package com.github.cerealklla.myssions.quest;

import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.core.BlockPos;
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
 *
 * <p><b>Geo-fenced to the quest's own real location, 2026-10-11</b> (explicit user correction: "if I
 * kill zombies somewhere else [it] will not count toward the quest Milestones ... that is how it
 * SHOULD behave") -- a kill only counts if the instance has a real {@link
 * QuestInstance#hintPos()}/{@link QuestInstance#hintDimensionId()} (set by {@code
 * NpcQuests#registerOnSiteHook}/{@code #findOldHarlen} for the quests that actually have one) and
 * the dead entity is within {@link #GEOFENCE_RADIUS_BLOCKS} of it in the same dimension. A quest
 * with no hint at all (the genuinely vague, distant wilderness/road quests -- {@code
 * timberWolves}/{@code webChokedGrove}/both Mayor quests, see {@code NpcQuests}' own doc) has no
 * real location to fence against, so those are deliberately left as "kill anywhere" -- unchanged,
 * not an oversight.
 */
public final class QuestKillListener {

    // Flagged tunable, same "placeholder, revisit by feel" convention every other distance/radius
    // constant in this mod uses -- generous enough to cover a plot's own grounds and immediate
    // surroundings ("near the fields"), not a pixel-precise point.
    private static final double GEOFENCE_RADIUS_BLOCKS = 64.0;

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Identifier deadType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        BlockPos deadPos = event.getEntity().blockPosition();
        String deadDimensionId = player.level().dimension().identifier().toString();
        var data = player.getData(ModAttachments.QUEST_DATA);
        for (QuestInstance instance : data.quests().values()) {
            if (instance.status() != QuestStatus.IN_PROGRESS || !isWithinQuestArea(instance, deadDimensionId, deadPos)) {
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
                    QuestEngine.reportMilestoneProgress(player, instance.instanceId(), milestone.id(), 1);
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

    private static boolean isWithinQuestArea(QuestInstance instance, String deadDimensionId, BlockPos deadPos) {
        if (instance.hintPos().isEmpty() || instance.hintDimensionId().isEmpty()) {
            return true; // No real location set for this quest -- "kill anywhere" is the deliberate behavior.
        }
        if (!instance.hintDimensionId().get().equals(deadDimensionId)) {
            return false;
        }
        return instance.hintPos().get().distSqr(deadPos) <= GEOFENCE_RADIUS_BLOCKS * GEOFENCE_RADIUS_BLOCKS;
    }
}
