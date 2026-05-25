package com.miguealguacil.butler.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class AlfredFollowGoal extends Goal {

    private final AlfredEntity alfred;
    private final double speed;
    private Player target;

    public AlfredFollowGoal(AlfredEntity alfred, double speed) {
        this.alfred = alfred;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!alfred.isFollowing()) return false;
        java.util.List<Player> players = alfred.level().getEntitiesOfClass(
            Player.class,
            alfred.getBoundingBox().inflate(64.0),
            p -> !p.isSpectator()
        );
        if (players.isEmpty()) return false;
        players.sort(java.util.Comparator.comparingDouble(alfred::distanceToSqr));
        target = players.get(0);
        return alfred.distanceToSqr(target) > 4.0;
    }

    @Override
    public boolean canContinueToUse() {
        return alfred.isFollowing() && target != null;
    }

    @Override
    public void start() {
        alfred.getNavigation().moveTo(target, speed);
    }

    @Override
    public void tick() {
        alfred.getLookControl().setLookAt(target, 10.0f, alfred.getMaxHeadXRot());
        if (alfred.distanceToSqr(target) > 4.0) {
            alfred.getNavigation().moveTo(target, speed);
        } else {
            alfred.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        target = null;
        alfred.getNavigation().stop();
    }
}
