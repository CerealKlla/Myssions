package com.github.cerealklla.myssions.quest;

import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Guarantees a kill-type milestone is actually completable once a player gets near its real target
 * -- explicit user spec, 2026-10-11: "When the player gets in render [of the target area], the
 * quest would actually spawn that wolf pack in that area. There may be other wolves there and
 * they'd also count, but it should make sure that the player is able to do the quest otherwise."
 * Always registered (pure vanilla entity spawning, no optional dependency involved).
 *
 * <p>Fires once per instance (see {@link QuestInstance#mobsSpawned()}), the first time a player
 * holding that quest gets within {@link #TRIGGER_RADIUS_BLOCKS} of its {@link
 * QuestInstance#hintPos()} -- the same radius {@code quest.QuestKillListener}'s own geo-fence uses,
 * so anything spawned here always counts toward the milestone. Counts whatever's already alive
 * nearby first and only tops up the shortfall, so naturally-spawned mobs of the right type aren't
 * duplicated on top of.
 */
public final class QuestMobSpawnTicker {

    private static final int INTERVAL_TICKS = 20; // 1 second -- cheap enough, no need for a tighter cadence.
    private static final double TRIGGER_RADIUS_BLOCKS = 64.0; // Matches QuestKillListener's own geo-fence radius.
    private static final int MAX_SPAWN_ATTEMPTS_PER_MOB = 8;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(ServerPlayer player) {
        var data = player.getData(ModAttachments.QUEST_DATA);
        for (QuestInstance instance : data.quests().values()) {
            if (instance.status() != QuestStatus.IN_PROGRESS || instance.mobsSpawned()
                    || instance.hintPos().isEmpty() || instance.hintDimensionId().isEmpty()) {
                continue;
            }
            Identifier dimensionId = Identifier.tryParse(instance.hintDimensionId().get());
            if (dimensionId == null || !player.level().dimension().identifier().equals(dimensionId)
                    || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            BlockPos truePos = instance.hintPos().get();
            if (player.distanceToSqr(truePos.getX() + 0.5, truePos.getY(), truePos.getZ() + 0.5) > TRIGGER_RADIUS_BLOCKS * TRIGGER_RADIUS_BLOCKS) {
                continue;
            }

            QuestRegistry.get(instance.definitionId()).ifPresent(definition -> ensureMobsForDefinition(level, instance, definition, truePos));
            QuestEngine.markMobsSpawned(player, instance.instanceId());
        }
    }

    private void ensureMobsForDefinition(ServerLevel level, QuestInstance instance, QuestDefinition definition, BlockPos truePos) {
        for (QuestMilestone milestone : definition.milestones()) {
            if (milestone.killTargetEntityType().isEmpty() || instance.isMilestoneComplete(milestone)) {
                continue;
            }
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(milestone.killTargetEntityType().get()).orElse(null);
            if (entityType == null) {
                continue;
            }
            int alreadyProgressed = instance.milestoneProgress().getOrDefault(milestone.id(), 0);
            int stillNeeded = milestone.requiredCount() - alreadyProgressed;
            if (stillNeeded <= 0) {
                continue;
            }
            AABB searchBox = new AABB(truePos).inflate(TRIGGER_RADIUS_BLOCKS);
            long alreadyAlive = level.getEntities(entityType, searchBox, Entity::isAlive).size();
            int toSpawn = (int) Math.max(0, stillNeeded - alreadyAlive);
            for (int i = 0; i < toSpawn; i++) {
                spawnNear(level, entityType, truePos);
            }
        }
    }

    private void spawnNear(ServerLevel level, EntityType<?> entityType, BlockPos center) {
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS_PER_MOB; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double distance = 6 + level.getRandom().nextDouble() * 18;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spawnPos = new BlockPos(x, y, z);
            Entity entity = entityType.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (entity == null) {
                return;
            }
            entity.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(entity);
            return;
        }
    }
}
