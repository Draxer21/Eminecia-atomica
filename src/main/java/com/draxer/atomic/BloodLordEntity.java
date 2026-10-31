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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Señores de la sangre: Crimson y Elizabeth, la Reina de la Sangre.
 * <ul>
 *   <li>Lanzas de sangre a distancia y látigo de sangre en área.</li>
 *   <li>Forma de murciélagos: a veces se deshacen en murciélagos, esquivan el golpe y reaparecen detrás.</li>
 *   <li>Roban vida y poder mágico, se regeneran e invocan siervos.</li>
 *   <li>Elizabeth, bajo la mitad de vida, despierta la Luna Roja: más fuerte, lluvia de lanzas y pilares de sangre.</li>
 * </ul>
 */
public class BloodLordEntity extends Monster {
    public enum Lord { CRIMSON, ELIZABETH }

    private final Lord lord;
    private final ServerBossEvent bossBar;
    private boolean redMoon;
    private int awakening;
    private int whipCooldown = 100;
    private int batCooldown = 60;
    private int summonCooldown = 200;

    public BloodLordEntity(EntityType<? extends BloodLordEntity> type, Level level, Lord lord) {
        super(type, level);
        this.lord = lord;
        this.bossBar = new ServerBossEvent(java.util.UUID.randomUUID(),
                Component.translatable("entity.draxatomic." + (lord == Lord.CRIMSON ? "crimson" : "elizabeth")),
                lord == Lord.CRIMSON ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        xpReward = lord == Lord.CRIMSON ? 150 : 300;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes(Lord lord) {
        boolean queen = lord == Lord.ELIZABETH;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, queen ? 600.0 : 320.0)
                .add(Attributes.MOVEMENT_SPEED, queen ? 0.33 : 0.35)
                .add(Attributes.ATTACK_DAMAGE, queen ? 16.0 : 11.0)
                .add(Attributes.ARMOR, queen ? 12.0 : 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    public Lord lord() {
        return lord;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("RedMoon", redMoon);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        redMoon = input.getBooleanOr("RedMoon", false);
        if (redMoon) redMoonStats();
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
            if (player.distanceToSqr(this) < 64 * 64) player.sendSystemMessage(Component.translatable(key).withStyle(color));
        }
    }

    void announce() {
        if (!(level() instanceof ServerLevel level)) return;
        bats(level, position().add(0, 1, 0), 40);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5F, lord == Lord.CRIMSON ? 1.3F : 0.9F);
        tell(level, "message.draxatomic." + lord.name().toLowerCase() + "_arrives", ChatFormatting.DARK_RED);
    }

    // ---------------------------------------------------------------- IA

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CastGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true) {
            @Override
            public boolean canUse() {
                return awakening == 0 && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, VampireEntity.class, BloodLordEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, ShadowAllyEntity.class, false));
    }

    @Override
    public boolean considersEntityAsAlly(Entity other) {
        return other instanceof VampireEntity || other instanceof BloodLordEntity || super.considersEntityAsAlly(other);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (awakening > 0) {
            awakeningTick(level);
            return;
        }
        if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) heal(level.isBrightOutside() ? 1.0F : (redMoon ? 4.0F : 2.0F));
        if (tickCount % 6 == 0) level.sendParticles(Vampires.BLOOD_DARK, getX(), getY() + 0.2, getZ(), 2, 0.4, 0.05, 0.4, 0);
        LivingEntity target = getTarget();
        if (target == null) return;
        batCooldown--;
        if (--whipCooldown <= 0 && distanceToSqr(target) < 7 * 7) {
            bloodWhip(level);
            whipCooldown = redMoon ? 70 : 110;
        }
        if (--summonCooldown <= 0) {
            summonServants(level, target);
            summonCooldown = lord == Lord.ELIZABETH ? 360 : 500;
        }
        if (batCooldown <= 0 && distanceToSqr(target) > 16 * 16) {
            batShift(level, target);
        }
    }

    /** Sus golpes roban vida y poder mágico. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            heal(lord == Lord.ELIZABETH ? 10.0F : 6.0F);
            if (target instanceof ServerPlayer player) Mana.steal(player, lord == Lord.ELIZABETH ? 30 : 15);
            level.sendParticles(Vampires.BLOOD, target.getX(), target.getY() + 1, target.getZ(), 16, 0.3, 0.4, 0.3, 0.03);
        }
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (awakening > 0) {
            level.sendParticles(Vampires.BLOOD, getX(), getY() + 1.2, getZ(), 10, 0.6, 1.0, 0.6, 0);
            return false;
        }
        if (source.getEntity() instanceof VampireEntity || source.getEntity() instanceof BloodLordEntity) return false;
        // Forma de murciélagos: se deshace y esquiva el golpe.
        if (batCooldown <= 0 && amount > 4.0F && getRandom().nextFloat() < (redMoon ? 0.35F : 0.25F)
                && source.getEntity() instanceof LivingEntity attacker) {
            batShift(level, attacker);
            return false;
        }
        if (lord == Lord.ELIZABETH && !redMoon && getHealth() - amount < getMaxHealth() / 2) {
            amount = Math.max(0, getHealth() - getMaxHealth() / 2);
            boolean hurt = super.hurtServer(level, source, amount);
            startRedMoon(level);
            return hurt;
        }
        return super.hurtServer(level, source, amount);
    }

    // ---------------------------------------------------------------- Luna Roja (Elizabeth)

    private void startRedMoon(ServerLevel level) {
        awakening = 70;
        getNavigation().stop();
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5F, 1.2F);
        tell(level, "message.draxatomic.red_moon", ChatFormatting.RED);
    }

    private void awakeningTick(ServerLevel level) {
        awakening--;
        double r = 1.2 + (70 - awakening) * 0.08;
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI / 9 - tickCount * 0.25;
            level.sendParticles(i % 2 == 0 ? Vampires.BLOOD : Vampires.BLOOD_DARK,
                    getX() + Math.cos(a) * r, getY() + 0.2 + (i % 3) * 0.8, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (awakening == 0) {
            redMoon = true;
            redMoonStats();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(7), e -> e != this && !isAlliedTo(e))) {
                Vec3 push = e.position().subtract(position()).normalize().scale(1.8);
                e.push(push.x, 0.6, push.z);
                e.hurtMarked = true;
                e.hurtServer(level, damageSources().mobAttack(this), 12.0F);
            }
        }
    }

    private void redMoonStats() {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        var damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (speed != null) speed.setBaseValue(0.4);
        if (damage != null) damage.setBaseValue(24.0);
        bossBar.setColor(BossEvent.BossBarColor.RED);
        bossBar.setDarkenScreen(true);
    }

    // ---------------------------------------------------------------- técnicas

    static void bats(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(Vampires.BLOOD_DARK, at.x, at.y, at.z, count, 0.5, 0.8, 0.5, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, count / 2, 0.4, 0.6, 0.4, 0.04);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 1.5F, 0.8F);
    }

    /** Se deshace en murciélagos y reaparece detrás de su objetivo. */
    private void batShift(ServerLevel level, LivingEntity target) {
        Vec3 behind = target.position().subtract(target.getLookAngle().multiply(3, 0, 3));
        int x = (int) Math.floor(behind.x);
        int z = (int) Math.floor(behind.z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - target.getY()) > 6) return;
        bats(level, position().add(0, 1, 0), 30);
        teleportTo(behind.x, y, behind.z);
        bats(level, position().add(0, 1, 0), 20);
        batCooldown = redMoon ? 60 : 100;
    }

    /** Látigo de sangre: un anillo que corta a todo lo que esté cerca. */
    private void bloodWhip(ServerLevel level) {
        double r = 6.0;
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24;
            for (double d = 1.5; d <= r; d += 1.5) {
                level.sendParticles(Vampires.BLOOD, getX() + Math.cos(a + d * 0.2) * d, getY() + 1.0, getZ() + Math.sin(a + d * 0.2) * d,
                        1, 0, 0, 0, 0);
            }
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r), e -> e != this && !isAlliedTo(e))) {
            e.hurtServer(level, damageSources().mobAttack(this), redMoon ? 16.0F : 10.0F);
            heal(3.0F);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.6F);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    /** Luna Roja: pilares de sangre que brotan bajo el objetivo. */
    private void bloodPillars(ServerLevel level, LivingEntity target) {
        Vec3 c = target.position();
        for (int k = 0; k < 5; k++) {
            double px = c.x + (k == 0 ? 0 : (getRandom().nextDouble() - 0.5) * 8);
            double pz = c.z + (k == 0 ? 0 : (getRandom().nextDouble() - 0.5) * 8);
            for (int h = 0; h < 8; h++) level.sendParticles(Vampires.BLOOD, px, c.y + h * 0.7, pz, 4, 0.3, 0.2, 0.3, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(px - 1.2, c.y - 1, pz - 1.2, px + 1.2, c.y + 4, pz + 1.2), e -> e != this && !isAlliedTo(e))) {
                e.hurtServer(level, damageSources().indirectMagic(this, this), 12.0F);
                e.push(0, 0.8, 0);
                e.hurtMarked = true;
            }
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    private void summonServants(ServerLevel level, LivingEntity target) {
        int nearby = level.getEntitiesOfClass(VampireEntity.class, getBoundingBox().inflate(24)).size();
        int count = Math.max(0, (lord == Lord.ELIZABETH ? (redMoon ? 5 : 3) : 2) - nearby);
        for (int i = 0; i < count; i++) {
            double a = getRandom().nextDouble() * Math.PI * 2;
            Vampires.spawnServant(level, (int) Math.floor(getX() + Math.cos(a) * 4), (int) Math.floor(getZ() + Math.sin(a) * 4), target);
        }
        if (count > 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (lord == Lord.CRIMSON) {
            spawnAtLocation(level, new ItemStack(Vampires.CRIMSON_BLOOD.value()));
        } else {
            spawnAtLocation(level, new ItemStack(Vampires.QUEEN_BLOOD.value()));
            spawnAtLocation(level, new ItemStack(Vampires.CRIMSON_BLOOD.value()));
        }
        spawnAtLocation(level, new ItemStack(Vampires.BLOOD_VIAL.value(), 3 + getRandom().nextInt(4)));
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) {
            bats(level, position().add(0, 1, 0), 60);
            tell(level, "message.draxatomic." + lord.name().toLowerCase() + "_defeated", ChatFormatting.LIGHT_PURPLE);
            if (lord == Lord.CRIMSON) {
                Cities.lawlessAt(level, getX(), getZ()).ifPresent(site -> Vampires.awakenQueen(level, Cities.coffin(level, site)));
            } else if (Cities.lawlessAt(level, getX(), getZ()).isPresent()) {
                Vampires.possessClaire(level, position());
            }
        }
        super.die(source);
    }

    /** A distancia: lanzas de sangre; con la Luna Roja, ráfagas y pilares. */
    static final class CastGoal extends Goal {
        private final BloodLordEntity lord;
        private int cooldown = 30;

        CastGoal(BloodLordEntity lord) {
            this.lord = lord;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = lord.getTarget();
            return lord.awakening == 0 && target != null && target.isAlive() && lord.distanceToSqr(target) > 12;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = lord.getTarget();
            if (target == null || !(lord.level() instanceof ServerLevel level)) return;
            lord.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--cooldown > 0 || !lord.getSensing().hasLineOfSight(target)) return;
            boolean queen = lord.lord == Lord.ELIZABETH;
            if (lord.redMoon && lord.getRandom().nextBoolean()) {
                lord.bloodPillars(level, target);
                cooldown = 60;
                return;
            }
            int lances = queen ? (lord.redMoon ? 4 : 2) : 1;
            for (int i = 0; i < lances; i++) {
                Bolts.fire(level, lord, target, queen ? (lord.redMoon ? 6.0F : 8.0F) : 8.0F, 0xB0001E, SoundEvents.EVOKER_CAST_SPELL);
            }
            lord.heal(2.0F * lances);
            cooldown = queen ? 40 : 45;
        }
    }
}
