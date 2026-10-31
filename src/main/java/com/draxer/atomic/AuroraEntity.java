package com.draxer.atomic;

import java.util.EnumSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Aurora, la Bruja de la Calamidad, en el cuerpo de Claire. Lanza rayos violeta y, herida,
 * intenta el All-Range Atomic: carga un domo inestable, el cuerpo de Claire no tolera la
 * compresión y la técnica se disipa. El estallido la deja aturdida y vulnerable.
 */
public class AuroraEntity extends Monster {
    private static final int CHARGE_TICKS = 110;
    private static final int STUN_TICKS = 120;
    private static final DustParticleOptions VIOLET = new DustParticleOptions(0x8A3BFF, 1.4F);
    private static final DustParticleOptions CRACK = new DustParticleOptions(0xFF3B6B, 1.0F);

    private final ServerBossEvent bossBar = new ServerBossEvent(java.util.UUID.randomUUID(),
            Component.translatable("entity.draxatomic.aurora"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int charging;
    private int stunned;
    private int attempts;
    private int nextAttempt;

    public AuroraEntity(EntityType<? extends AuroraEntity> type, Level level) {
        super(type, level);
        xpReward = 250;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 450.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    private void tell(ServerLevel level, String key, ChatFormatting color) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < 96 * 96) player.sendSystemMessage(Component.translatable(key).withStyle(color));
        }
    }

    void announce() {
        if (!(level() instanceof ServerLevel level)) return;
        level.sendParticles(VIOLET, getX(), getY() + 1, getZ(), 80, 0.6, 1.0, 0.6, 0.1);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2F, 1.6F);
        tell(level, "message.draxatomic.aurora_arrives", ChatFormatting.LIGHT_PURPLE);
    }

    public boolean charging() {
        return charging > 0;
    }

    // ---------------------------------------------------------------- IA

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CastGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return charging == 0 && stunned == 0 && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, ShadowAllyEntity.class, false));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (stunned > 0) {
            stunned--;
            getNavigation().stop();
            if (tickCount % 4 == 0) level.sendParticles(CRACK, getX(), getY() + 1.2, getZ(), 6, 0.3, 0.6, 0.3, 0);
            if (stunned == 0) bossBar.setColor(BossEvent.BossBarColor.PURPLE);
            return;
        }
        if (charging > 0) {
            chargeTick(level);
            return;
        }
        if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) heal(1.5F);
        // Herida, intenta el All-Range Atomic (cada vez más seguido).
        float ratio = getHealth() / getMaxHealth();
        boolean foe = getTarget() != null || level.getNearestPlayer(this, 24) != null;
        if (foe && ratio < 0.7F && --nextAttempt <= 0) startCharge(level);
    }

    // ---------------------------------------------------------------- el All-Range de Aurora

    private int domeRadius() {
        return 36 + attempts * 6;
    }

    private void startCharge(ServerLevel level) {
        charging = CHARGE_TICKS;
        getNavigation().stop();
        setNoGravity(true);
        setDeltaMovement(0, 0.08, 0);
        tell(level, "message.draxatomic.aurora_charge", ChatFormatting.DARK_PURPLE);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    private void chargeTick(ServerLevel level) {
        charging--;
        int elapsed = CHARGE_TICKS - charging;
        if (elapsed < 20) setDeltaMovement(0, 0.08, 0);
        else setDeltaMovement(Vec3.ZERO);
        Vec3 c = position().add(0, 1.0, 0);
        // Domo inestable: tiembla de tamaño y parpadea entre violeta y rojo.
        if (elapsed % 10 == 1) {
            int r = domeRadius() + getRandom().nextInt(9) - 4;
            BarrierPayload.sendFrom(level, getId(), c, r, 14, elapsed % 20 == 1 ? AtomicPayload.VIOLET : 0xFF3B6B);
        }
        // Energía absorbida y el cuerpo que se agrieta.
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, false, c.x, c.y, c.z, 12, 3, 2, 3, 0.3);
        int cracks = 1 + elapsed / 20;
        for (int i = 0; i < cracks; i++) {
            level.sendParticles(CRACK, getX() + (getRandom().nextDouble() - 0.5) * 0.7, getY() + getRandom().nextDouble() * 1.8,
                    getZ() + (getRandom().nextDouble() - 0.5) * 0.7, 1, 0, 0, 0, 0);
        }
        if (elapsed % 20 == 10) level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.2F, 0.5F + elapsed * 0.004F);
        if (elapsed == CHARGE_TICKS / 2) tell(level, "message.draxatomic.aurora_strain", ChatFormatting.RED);
        if (charging == 0) collapse(level);
    }

    /** El cuerpo de Claire no aguanta: la técnica se deshace en un estallido. */
    private void collapse(ServerLevel level) {
        setNoGravity(false);
        BarrierPayload.sendFrom(level, getId(), position(), 0, 0, 0);
        Vec3 c = position().add(0, 1.0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, c.x, c.y, c.z, 2, 1, 1, 1, 0);
        level.sendParticles(VIOLET, true, true, c.x, c.y, c.z, 200, 4, 3, 4, 0.4);
        level.sendParticles(CRACK, true, true, c.x, c.y, c.z, 120, 2, 2, 2, 0.3);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0F, 0.3F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5F, 0.8F);
        // La energía que se escapa golpea alrededor, pero sobre todo a ella misma.
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8), e -> e != this)) {
            e.hurtServer(level, damageSources().indirectMagic(this, this), 10.0F);
            Vec3 push = e.position().subtract(position()).normalize().scale(1.6);
            e.push(push.x, 0.5, push.z);
            e.hurtMarked = true;
        }
        setHealth(Math.max(1.0F, getHealth() - getMaxHealth() * 0.15F));
        stunned = STUN_TICKS;
        attempts++;
        nextAttempt = 260 - Math.min(140, attempts * 40);
        bossBar.setColor(BossEvent.BossBarColor.RED);
        tell(level, "message.draxatomic.aurora_dissipates", ChatFormatting.LIGHT_PURPLE);
    }

    /** Aturdida tras disiparse, recibe el doble de daño; cargando, se la puede interrumpir. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (stunned > 0) amount *= 2.0F;
        if (charging > 0 && amount >= 30.0F) {
            charging = 1; // un golpe fuerte rompe la concentración: se disipa antes
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        spawnAtLocation(level, new ItemStack(Vampires.AURORA_FRAGMENT.value()));
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) {
            BarrierPayload.sendFrom(level, getId(), position(), 0, 0, 0);
            level.sendParticles(VIOLET, getX(), getY() + 1, getZ(), 100, 0.6, 1.0, 0.6, 0.2);
            tell(level, "message.draxatomic.aurora_defeated", ChatFormatting.LIGHT_PURPLE);
        }
        super.die(source);
    }

    @Override
    public boolean considersEntityAsAlly(Entity other) {
        return super.considersEntityAsAlly(other);
    }

    /** Rayos violeta a distancia (más rápido cuanto más herida). */
    static final class CastGoal extends Goal {
        private final AuroraEntity aurora;
        private int cooldown = 30;

        CastGoal(AuroraEntity aurora) {
            this.aurora = aurora;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = aurora.getTarget();
            return aurora.charging == 0 && aurora.stunned == 0 && target != null && target.isAlive() && aurora.distanceToSqr(target) > 9;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = aurora.getTarget();
            if (target == null || !(aurora.level() instanceof ServerLevel level)) return;
            aurora.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--cooldown > 0 || !aurora.getSensing().hasLineOfSight(target)) return;
            Bolts.fire(level, aurora, target, 9.0F, 0x8A3BFF, SoundEvents.EVOKER_CAST_SPELL);
            cooldown = aurora.getHealth() < aurora.getMaxHealth() / 2 ? 25 : 40;
        }
    }
}
