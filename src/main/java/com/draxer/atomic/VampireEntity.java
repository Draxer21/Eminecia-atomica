package com.draxer.atomic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Siervo vampiro (ghoul): rápido, se cura con cada mordida y arde al sol. Suelta viales de
 * sangre y, a veces, el Cáliz carmesí que invoca a Crimson.
 */
public class VampireEntity extends Monster {
    public VampireEntity(EntityType<? extends VampireEntity> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, VampireEntity.class, BloodLordEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, ShadowAllyEntity.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Villager.class, false));
    }

    @Override
    public boolean considersEntityAsAlly(Entity other) {
        return other instanceof VampireEntity || other instanceof BloodLordEntity || super.considersEntityAsAlly(other);
    }

    /** Cada mordida le devuelve vida. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            heal(4.0F);
            level.sendParticles(Vampires.BLOOD, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
            if (target instanceof ServerPlayer player) Mana.steal(player, 5);
        }
        return hit;
    }

    /** Arde al sol, como en las historias. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel level && isAlive() && level.isBrightOutside() && !isInWater() && !level.isRainingAt(blockPosition())
                && level.canSeeSky(blockPosition().above()) && tickCount % 20 == 0) {
            igniteForSeconds(8.0F);
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.2, getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (getRandom().nextFloat() < 0.35F) spawnAtLocation(level, new ItemStack(Vampires.BLOOD_VIAL.value()));
        if (recentlyHit && getRandom().nextFloat() < 0.04F) spawnAtLocation(level, new ItemStack(Vampires.CHALICE.value()));
    }
}
