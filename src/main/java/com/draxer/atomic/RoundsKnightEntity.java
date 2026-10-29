package com.draxer.atomic;

import java.util.EnumSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Caballero de la Mesa Redonda, jefe del Culto de Diablos.
 * <p>Fase 1: espada, rayos dorados, teletransporte y refuerzos. El golpe que lo bajaría de la
 * mitad de su vida lo deja exactamente en la mitad (sobrevive al primer Atomic), y se vuelve
 * invulnerable unos segundos mientras despierta.
 * <p>Fase 2: más rápido y fuerte, lanza lluvias de rayos en área y se cura absorbiendo a sus
 * cultistas. Al morir suelta el Corazón de Diablos.
 */
public class RoundsKnightEntity extends Monster {
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(RoundsKnightEntity.class, EntityDataSerializers.INT);
    private static final int AWAKEN_TICKS = 80;

    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.draxatomic.round_knight"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    private int awakening;
    private int summonCooldown = 200;
    private int teleportCooldown = 100;

    public RoundsKnightEntity(EntityType<? extends RoundsKnightEntity> type, Level level) {
        super(type, level);
        xpReward = 200;
        setPersistenceRequired();
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 1);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Phase", phase());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(PHASE, input.getIntOr("Phase", 1));
        if (phase() == 2) enterPhaseTwoStats();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ---------------------------------------------------------------- barra de jefe

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

    void announce() {
        if (!(level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.8F);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < 64 * 64) {
                player.sendSystemMessage(Component.translatable("message.draxatomic.knight_arrives").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    // ---------------------------------------------------------------- IA

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CastGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return awakening == 0 && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, CultistEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, ShadowAllyEntity.class, false));
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof CultistEntity || super.isAlliedTo(other);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (awakening > 0) {
            awakeningTick(level);
            return;
        }
        LivingEntity target = getTarget();
        if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) heal(phase() == 2 ? 2.0F : 1.0F);
        if (target == null) return;
        if (--teleportCooldown <= 0 && distanceToSqr(target) > 14 * 14) {
            teleportBehind(level, target);
            teleportCooldown = phase() == 2 ? 80 : 140;
        }
        if (--summonCooldown <= 0) {
            summonGuards(level);
            summonCooldown = phase() == 2 ? 400 : 600;
        }
    }

    // ---------------------------------------------------------------- fases

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (awakening > 0) {
            level.sendParticles(new DustParticleOptions(0xFFD54A, 1.5F), getX(), getY() + 1.4, getZ(), 10, 0.6, 1.0, 0.6, 0);
            return false;
        }
        if (source.getEntity() instanceof CultistEntity) return false;
        float half = getMaxHealth() / 2.0F;
        if (phase() == 1 && getHealth() - amount < half) {
            // Resiste el golpe y despierta.
            amount = Math.max(0, getHealth() - half);
            boolean hurt = super.hurtServer(level, source, amount);
            setHealth(half);
            startAwakening(level);
            return hurt;
        }
        return super.hurtServer(level, source, amount);
    }

    private void startAwakening(ServerLevel level) {
        awakening = AWAKEN_TICKS;
        getNavigation().stop();
        bossBar.setColor(BossEvent.BossBarColor.RED);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5F, 0.7F);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < 64 * 64) {
                player.sendSystemMessage(Component.translatable("message.draxatomic.knight_awakens").withStyle(ChatFormatting.RED));
            }
        }
    }

    private void awakeningTick(ServerLevel level) {
        awakening--;
        double r = 1.5 + (AWAKEN_TICKS - awakening) * 0.05;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8 + tickCount * 0.2;
            level.sendParticles(new DustParticleOptions(i % 2 == 0 ? 0xFFD54A : 0x8A1010, 1.6F),
                    getX() + Math.cos(a) * r, getY() + 0.2 + (i % 4) * 0.6, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (awakening == 0) {
            entityData.set(PHASE, 2);
            enterPhaseTwoStats();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 2, 1, 1, 1, 0);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.6F);
            // Onda que empuja a todos los que estén cerca.
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6), e -> e != this && !isAlliedTo(e))) {
                Vec3 push = e.position().subtract(position()).normalize().scale(1.8);
                e.push(push.x, 0.6, push.z);
                e.hurtMarked = true;
                e.hurtServer(level, damageSources().mobAttack(this), 10.0F);
            }
            summonGuards(level);
        }
    }

    private void enterPhaseTwoStats() {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        var damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (speed != null) speed.setBaseValue(0.38);
        if (damage != null) damage.setBaseValue(22.0);
        bossBar.setColor(BossEvent.BossBarColor.RED);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
    }

    // ---------------------------------------------------------------- habilidades

    private void teleportBehind(ServerLevel level, LivingEntity target) {
        Vec3 behind = target.position().subtract(target.getLookAngle().multiply(3, 0, 3));
        int x = (int) Math.floor(behind.x);
        int z = (int) Math.floor(behind.z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - target.getY()) > 6) return;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.4, getZ(), 30, 0.4, 1.0, 0.4, 0.05);
        teleportTo(behind.x, y, behind.z);
        level.sendParticles(new DustParticleOptions(0xFFD54A, 1.5F), getX(), getY() + 1.4, getZ(), 30, 0.4, 1.0, 0.4, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    private void summonGuards(ServerLevel level) {
        int nearby = level.getEntitiesOfClass(CultistEntity.class, getBoundingBox().inflate(24)).size();
        int count = Math.max(0, (phase() == 2 ? 4 : 2) - nearby);
        for (int i = 0; i < count; i++) {
            double a = getRandom().nextDouble() * Math.PI * 2;
            int x = (int) Math.floor(getX() + Math.cos(a) * 4);
            int z = (int) Math.floor(getZ() + Math.sin(a) * 4);
            BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            CultistEntity guard = Cult.CULTIST.value().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (guard == null) continue;
            guard.snapTo(x + 0.5, ground.getY(), z + 0.5, getRandom().nextFloat() * 360, 0);
            guard.setup(i % 2 == 0 ? CultistEntity.Kind.KNIGHT : CultistEntity.Kind.MAGE);
            guard.setTarget(getTarget());
            level.addFreshEntity(guard);
            level.sendParticles(new DustParticleOptions(0xFFD54A, 1.4F), guard.getX(), guard.getY() + 1, guard.getZ(), 20, 0.3, 0.8, 0.3, 0);
        }
        if (count > 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.8F);
    }

    /** Fase 2: lluvia de rayos dorados en un círculo alrededor del objetivo. */
    private void judgment(ServerLevel level, LivingEntity target) {
        Vec3 c = target.position();
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            level.sendParticles(new DustParticleOptions(0xFFD54A, 1.6F), c.x + Math.cos(a) * 4, c.y + 0.1, c.z + Math.sin(a) * 4,
                    1, 0, 0, 0, 0);
        }
        for (int h = 0; h < 12; h++) {
            level.sendParticles(new DustParticleOptions(0xFFF2B0, 2.0F), c.x, c.y + h, c.z, 6, 1.2, 0.2, 1.2, 0);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4), e -> !isAlliedTo(e) && e != this)) {
            e.hurtServer(level, damageSources().indirectMagic(this, this), 12.0F);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.5F, 1.2F);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        spawnAtLocation(level, new ItemStack(Cult.HEART.value()));
        spawnAtLocation(level, new ItemStack(Cult.FRAGMENT.value(), 4 + getRandom().nextInt(4)));
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(this) < 64 * 64) {
                    player.sendSystemMessage(Component.translatable("message.draxatomic.knight_defeated").withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
        }
        super.die(source);
    }

    /** Rayos a distancia en ambas fases; en la 2 alterna con el juicio en área. */
    static final class CastGoal extends Goal {
        private final RoundsKnightEntity knight;
        private int cooldown = 40;

        CastGoal(RoundsKnightEntity knight) {
            this.knight = knight;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = knight.getTarget();
            return knight.awakening == 0 && target != null && target.isAlive() && knight.distanceToSqr(target) > 16;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = knight.getTarget();
            if (target == null || !(knight.level() instanceof ServerLevel level)) return;
            knight.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--cooldown > 0 || !knight.getSensing().hasLineOfSight(target)) return;
            if (knight.phase() == 2 && knight.getRandom().nextBoolean()) {
                knight.judgment(level, target);
                cooldown = 70;
            } else {
                Bolts.fire(level, knight, target, knight.phase() == 2 ? 14.0F : 9.0F, 0xFFD54A, SoundEvents.EVOKER_CAST_SPELL);
                cooldown = knight.phase() == 2 ? 30 : 50;
            }
        }
    }
}
