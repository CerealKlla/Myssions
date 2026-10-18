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
        // Real report, 2026-10-17 (Settlemynts): "NPCs keep getting stuck up in trees" -- neither
        // vanilla's Mob nor AbstractVillager sets a PathType.LEAVES malus, so a tree's leaf canopy
        // reads as ordinary walkable ground to the pathfinder. Same fix applied here since a quest
        // NPC spawned out in the wilderness (an unborn-town reporter, etc.) is just as exposed.
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.LEAVES, -1.0F);
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
        if (completedAnything) {
            // Real live report, 2026-10-12: "Harlen didn't actually have any dialog for me so it was
            // kind of a weird quest" -- the talk milestone completed entirely silently, even though
            // the quest's own description promises "see what he has to say." No per-NPC dialogue
            // content system exists (every quest NPC here is generic, no authored line table), so
            // this is a deliberately minimal fix: always say SOMETHING on the interaction that
            // actually completes the milestone, rather than nothing at all. A real dialogue system is
            // a much bigger feature, not in scope for closing out this report.
            player.sendSystemMessage(Component.literal(npcName + ": \"Ah, you found me. Glad you made it.\""));
            // Same live report asked "Does he ever de-spawn?" -- he didn't, there was no cleanup at
            // all once the talk milestone was done, so a personally-spawned (owner-only) quest NPC
            // would sit there forever with nothing left to say. A shared/public NPC (ownerPlayerId
            // null) is left alone here -- no current quest uses one, but discarding on the FIRST
            // player to talk would wrongly remove it for everyone else who hasn't yet.
            if (ownerPlayerId != null) {
                discard();
            }
        } else {
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
