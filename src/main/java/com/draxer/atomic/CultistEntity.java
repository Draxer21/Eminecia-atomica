package com.draxer.atomic;

import java.util.EnumSet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Cultista de la Culto de Diablos: caballero (cuerpo a cuerpo) o mago (rayos a distancia). */
public class CultistEntity extends Monster {
    public enum Kind { KNIGHT, MAGE }

    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(CultistEntity.class, EntityDataSerializers.INT);

    public CultistEntity(EntityType<? extends CultistEntity> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, 0);
    }

    public Kind kind() {
        Kind[] all = Kind.values();
        return all[Math.floorMod(entityData.get(KIND), all.length)];
    }

    void setup(Kind kind) {
        entityData.set(KIND, kind.ordinal());
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(kind == Kind.KNIGHT ? Items.IRON_SWORD : Items.BLAZE_ROD));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        if (kind == Kind.KNIGHT) {
            var armor = getAttribute(Attributes.ARMOR);
            if (armor != null) armor.setBaseValue(12.0);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Kind", entityData.get(KIND));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(KIND, input.getIntOr("Kind", 0));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CastGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false) {
            @Override
            public boolean canUse() {
                return kind() == Kind.KNIGHT && super.canUse();
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this, CultistEntity.class, RoundsKnightEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, ShadowAllyEntity.class, true));
    }

    /** No se atacan entre miembros de la Orden. */
    @Override
    public boolean considersEntityAsAlly(net.minecraft.world.entity.Entity other) {
        return other instanceof CultistEntity || other instanceof RoundsKnightEntity || super.considersEntityAsAlly(other);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (getRandom().nextFloat() < 0.6F) {
            spawnAtLocation(level, new ItemStack(Cult.FRAGMENT.value(), 1 + getRandom().nextInt(2)));
        }
    }

    /** Los magos mantienen distancia y lanzan rayos de Diablos. */
    static final class CastGoal extends Goal {
        private final CultistEntity mob;
        private int cooldown = 20;

        CastGoal(CultistEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.kind() == Kind.MAGE && mob.getTarget() != null && mob.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null || !(mob.level() instanceof ServerLevel level)) return;
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double dist = mob.distanceTo(target);
            boolean sees = mob.getSensing().hasLineOfSight(target);
            if (dist > 14 || !sees) mob.getNavigation().moveTo(target, 1.0);
            else if (dist < 5) {
                Vec3 away = mob.position().subtract(target.position()).normalize().scale(4).add(mob.position());
                mob.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
            } else mob.getNavigation().stop();
            if (--cooldown > 0 || !sees || dist > 16) return;
            cooldown = 45;
            Bolts.fire(level, mob, target, 6.0F, 0xE0B040, SoundEvents.EVOKER_CAST_SPELL);
        }
    }
}
