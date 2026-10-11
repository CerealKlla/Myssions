package com.github.cerealklla.myssions.quest.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Walks a {@link CaravanEntity} toward whatever {@link CaravanEntity#getTargetPos} currently
 * returns, re-pathing periodically and never giving up -- unlike {@code
 * Settlemynts.lumberyard.LumberjackWorkGoal} (the shape this is generalized from), there's no
 * "pick a new target" step here at all: {@code quest.QuestEscortTicker} is the sole owner of what
 * the target is and changes it externally. This goal's only job is locomotion.
 */
public class FollowRouteGoal extends Goal {

    static final double ARRIVE_DIST_SQ = 9.0;
    private static final int REPATH_INTERVAL_TICKS = 20;

    private final CaravanEntity caravan;
    private final double speedModifier;
    private int repathCooldown;
    private BlockPos lastPathedTo;

    public FollowRouteGoal(CaravanEntity caravan, double speedModifier) {
        this.caravan = caravan;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return caravan.getTargetPos().isPresent() && !caravan.hasArrived();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repathCooldown = 0;
        lastPathedTo = null;
    }

    @Override
    public void stop() {
        lastPathedTo = null;
    }

    @Override
    public void tick() {
        BlockPos target = caravan.getTargetPos().orElse(null);
        if (target == null) {
            return;
        }
        if (target.equals(lastPathedTo) && !caravan.getNavigation().isDone()) {
            return;
        }
        if (repathCooldown-- > 0) {
            return;
        }
        caravan.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speedModifier);
        lastPathedTo = target;
        repathCooldown = REPATH_INTERVAL_TICKS;
    }
}
