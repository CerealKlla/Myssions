package com.github.cerealklla.myssions.quest;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A quest-spawned, possibly day/night-conditional, possibly per-player-visible NPC (design doc
 * items 8 and 9) -- built together as one real demo rather than two separate, half-built systems,
 * since item 8's own example ("talk to a named NPC who hides at night") already needs item 9's
 * per-player visibility to make sense (a hiding NPC that every player on the server could still
 * see would just look broken).
 *
 * <p><b>Per-player visibility (item 9)</b>: {@link #broadcastToPlayer} is the exact mechanism
 * vanilla itself uses to hide spectators from non-spectators (same technique Settlemynts' own
 * {@code founding.GhostTownHallCoreEntity} already uses) -- returning {@code false} means the
 * server never sends that player this entity's spawn packet at all, not just a client-side hide.
 * {@link #ownerPlayerId} unset (null) means visible to everyone (a future shared/public quest NPC);
 * set means visible only to that one player.
 *
 * <p><b>Day/night hiding (item 8)</b>: when {@link #hidesAtNight} is true, this NPC is invisible to
 * *everyone*, owner included, whenever {@code level.isDarkOutside()} -- matching "skittish, goes
 * and hides during the night" literally: there's genuinely nobody there to talk to until morning.
 *
 * <p>Interaction is handled by the always-registered {@code quest.QuestNpcTalkListener} (a plain
 * {@code PlayerInteractEvent.EntityInteract} listener, same shape as Settlemynts' own shop-NPC
 * interaction, not a {@code mobInteract} override) rather than here -- completes the quest's own
 * {@link QuestMilestone#isTalkMilestone()} milestone via {@link #talkTo}.
 */
public class QuestNpcEntity extends Villager {

    private UUID ownerPlayerId;
    private UUID linkedInstanceId;
    private boolean hidesAtNight;
    private String npcName = "";

    public QuestNpcEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        skipDropExperience();
    }

    public void configure(UUID ownerPlayerId, UUID linkedInstanceId, boolean hidesAtNight, String npcName) {
        this.ownerPlayerId = ownerPlayerId;
        this.linkedInstanceId = linkedInstanceId;
        this.hidesAtNight = hidesAtNight;
        this.npcName = npcName;
        setCustomName(Component.literal(npcName));
        setCustomNameVisible(true);
    }

    public Optional<UUID> linkedInstanceId() {
        return Optional.ofNullable(linkedInstanceId);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        if (hidesAtNight && level().isDarkOutside()) {
            return false;
        }
        return ownerPlayerId == null || ownerPlayerId.equals(player.getUUID());
    }

    /** Called by {@code quest.QuestNpcTalkListener} on a real interaction -- false if this player can't currently see/talk to this NPC at all. */
    public boolean talkTo(ServerPlayer player) {
        if (!broadcastToPlayer(player) || linkedInstanceId == null) {
            return false;
        }
        boolean completedAnything = false;
        QuestInstance instance = QuestEngine.getInstance(player, linkedInstanceId).orElse(null);
        QuestDefinition definition = instance == null ? null : QuestRegistry.get(instance.definitionId()).orElse(null);
        if (definition != null) {
            for (QuestMilestone milestone : definition.milestones()) {
                if (milestone.isTalkMilestone()
                        && instance.status() == QuestStatus.IN_PROGRESS && !instance.isMilestoneComplete(milestone)) {
                    QuestEngine.reportMilestoneProgress(player, linkedInstanceId, milestone.id(), 1);
                    completedAnything = true;
                }
            }
        }
        if (!completedAnything) {
            player.sendSystemMessage(Component.literal(npcName + ": \"Nothing more for you right now.\""));
        }
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // Deliberately never calls super.customServerAiStep (Villager's own trading/schedule AI) --
        // this NPC exists purely to stand somewhere and be talked to, same shape as Settlemynts'
        // own plot worker NPCs.
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("OwnerPlayerId", UUIDUtil.CODEC, ownerPlayerId);
        output.storeNullable("LinkedInstanceId", UUIDUtil.CODEC, linkedInstanceId);
        output.putBoolean("HidesAtNight", hidesAtNight);
        output.putString("NpcName", npcName);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        ownerPlayerId = input.read("OwnerPlayerId", UUIDUtil.CODEC).orElse(null);
        linkedInstanceId = input.read("LinkedInstanceId", UUIDUtil.CODEC).orElse(null);
        hidesAtNight = input.getBooleanOr("HidesAtNight", false);
        npcName = input.getStringOr("NpcName", "");
    }
}
