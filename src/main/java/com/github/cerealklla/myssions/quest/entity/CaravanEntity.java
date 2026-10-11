package com.github.cerealklla.myssions.quest.entity;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The moving caravan a player escorts (caravan-raid quest style, added 2026-10-11 -- see
 * design-document.md's quest taxonomy). Deliberately dumb: it knows nothing about its own route or
 * when to raise an alarm -- {@code quest.QuestEscortTicker} owns all of that and simply tells this
 * entity where to walk next via {@link #setTargetPos}, then polls {@link #hasArrived} once per
 * cycle. Visible only to the owning player, same mechanism (and same reasoning) as {@code
 * QuestNpcEntity#broadcastToPlayer}.
 */
public class CaravanEntity extends Villager {

    private UUID ownerPlayerId;
    private UUID linkedInstanceId;
    private BlockPos targetPos;

    public CaravanEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        skipDropExperience();
    }

    public void configure(UUID ownerPlayerId, UUID linkedInstanceId) {
        this.ownerPlayerId = ownerPlayerId;
        this.linkedInstanceId = linkedInstanceId;
        setCustomName(Component.literal("Caravan"));
        setCustomNameVisible(true);
    }

    public Optional<UUID> linkedInstanceId() {
        return Optional.ofNullable(linkedInstanceId);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes();
    }

    public void setTargetPos(BlockPos pos) {
        this.targetPos = pos;
    }

    public Optional<BlockPos> getTargetPos() {
        return Optional.ofNullable(targetPos);
    }

    /** True once within {@link FollowRouteGoal#ARRIVE_DIST_SQ} of {@link #getTargetPos()} -- {@code QuestEscortTicker} should immediately call {@link #setTargetPos} again (or clear the route) once it sees this. */
    public boolean hasArrived() {
        return targetPos != null && blockPosition().distSqr(targetPos) <= FollowRouteGoal.ARRIVE_DIST_SQ;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new FollowRouteGoal(this, 0.6));
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return ownerPlayerId == null || ownerPlayerId.equals(player.getUUID());
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // Deliberately never calls super.customServerAiStep (Villager's own trading/schedule AI) --
        // this entity exists purely to walk its route, same shape as QuestNpcEntity.
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("OwnerPlayerId", UUIDUtil.CODEC, ownerPlayerId);
        output.storeNullable("LinkedInstanceId", UUIDUtil.CODEC, linkedInstanceId);
        output.storeNullable("TargetPos", BlockPos.CODEC, targetPos);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        ownerPlayerId = input.read("OwnerPlayerId", UUIDUtil.CODEC).orElse(null);
        linkedInstanceId = input.read("LinkedInstanceId", UUIDUtil.CODEC).orElse(null);
        targetPos = input.read("TargetPos", BlockPos.CODEC).orElse(null);
    }
}
