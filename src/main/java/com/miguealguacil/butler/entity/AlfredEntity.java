package com.miguealguacil.butler.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;

public class AlfredEntity extends PathfinderMob {

    private boolean isFollowing = false;

    public AlfredEntity(EntityType<? extends AlfredEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AlfredFollowGoal(this, 0.6));
    }

    public boolean isFollowing() {
        return isFollowing;
    }

    public void setFollowing(boolean following) {
        this.isFollowing = following;
    }
}
