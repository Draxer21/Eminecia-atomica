package com.draxer.atomic;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Las bestias del mod, con modelos propios:
 * <ul>
 *   <li>Sabueso de sangre: cazan en jauría de noche y desangran.</li>
 *   <li>Quimera del Culto: experimentos del Culto; saltan, se regeneran y llevan cristales en el lomo.</li>
 *   <li>Quimera Alfa: jefe de la Base del Culto (golpe sísmico, púas de cristal y llama a su camada).</li>
 *   <li>Murciélago de sangre: enjambres que te roban la vida.</li>
 *   <li>Gólem de limo: en cuevas; al morir se parte en dos más pequeños.</li>
 *   <li>Bestia de sombra: invisible en la oscuridad (solo se le ven los ojos) y ataca por la espalda.</li>
 *   <li>Espectro de maná: aparece tras una gran descarga mágica, atraviesa paredes, te drena el poder
 *   y solo lo hiere la magia.</li>
 * </ul>
 */
public class BeastEntity extends Monster {
    public enum Beast {
        BLOOD_HOUND("blood_hound", 22, 5, 2, 0.36),
        CHIMERA("cult_chimera", 60, 9, 6, 0.32),
        CHIMERA_ALPHA("chimera_alpha", 420, 18, 12, 0.34),
        BLOOD_BAT("blood_bat", 8, 3, 0, 0.3),
        SLIME_GOLEM("slime_golem", 16, 4, 2, 0.25),
        SHADOW_BEAST("shadow_beast", 50, 10, 4, 0.34),
        MANA_WRAITH("mana_wraith", 30, 6, 0, 0.3);

        public final String id;
        final double health;
        final double damage;
        final double armor;
        final double speed;

        Beast(String id, double health, double damage, double armor, double speed) {
            this.id = id;
            this.health = health;
            this.damage = damage;
            this.armor = armor;
            this.speed = speed;
        }

        boolean flyer() {
            return this == BLOOD_BAT || this == MANA_WRAITH;
        }
    }

    private final Beast beast;
    /** Tamaño del gólem de limo (3, 2 o 1). */
    private int size = 3;
    private int contactCooldown;
    private int abilityCooldown = 100;
    private int spikeCooldown = 140;
    private int life;
    private boolean slamming;
    private boolean summoned;
    private boolean initialized;
    private final List<Spike> spikes = new ArrayList<>();
    private final ServerBossEvent bossBar;

    private record Spike(Vec3 at, int[] delay) {}

    public BeastEntity(EntityType<? extends BeastEntity> type, Level level, Beast beast) {
        super(type, level);
        this.beast = beast;
        xpReward = switch (beast) {
            case CHIMERA_ALPHA -> 300;
            case CHIMERA, SHADOW_BEAST -> 20;
            case MANA_WRAITH -> 15;
            default -> 6;
        };
        if (beast.flyer()) setNoGravity(true);
        if (beast == Beast.MANA_WRAITH) noPhysics = true;
        if (beast == Beast.CHIMERA_ALPHA) setPersistenceRequired();
        bossBar = beast == Beast.CHIMERA_ALPHA
                ? new ServerBossEvent(java.util.UUID.randomUUID(), Component.translatable("entity.draxatomic.chimera_alpha"),
                        BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10)
                : null;
    }

    public static AttributeSupplier.Builder createAttributes(Beast beast) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, beast.health)
                .add(Attributes.ATTACK_DAMAGE, beast.damage)
                .add(Attributes.ARMOR, beast.armor)
                .add(Attributes.MOVEMENT_SPEED, beast.speed)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.KNOCKBACK_RESISTANCE, beast == Beast.CHIMERA_ALPHA ? 1.0 : beast == Beast.CHIMERA ? 0.4 : 0.0)
                .add(Attributes.FOLLOW_RANGE, beast == Beast.CHIMERA_ALPHA ? 48.0 : 32.0);
    }

    public Beast beast() {
        return beast;
    }

    /** Ajusta tamaño, vida y daño del gólem de limo. */
    void setSize(int size) {
        this.size = Math.max(1, Math.min(3, size));
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(0.35 + 0.35 * this.size);
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(12.0 * this.size + 4);
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(2.0 + 2.0 * this.size);
        setHealth(getMaxHealth());
    }

    /** Escala del Alfa al aparecer. */
    void setupAlpha() {
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(2.0);
        setHealth(getMaxHealth());
    }

    // ---------------------------------------------------------------- IA

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        if (!beast.flyer()) {
            if (beast == Beast.BLOOD_HOUND || beast == Beast.CHIMERA) goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45F));
            goalSelector.addGoal(3, new MeleeAttackGoal(this, beast == Beast.SLIME_GOLEM ? 1.0 : 1.25, true));
            goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        }
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, beast == Beast.BLOOD_HOUND || beast == Beast.CHIMERA
                ? new HurtByTargetGoal(this).setAlertOthers() : new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, beast != Beast.MANA_WRAITH));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, ShadowAllyEntity.class, true));
        if (beast == Beast.BLOOD_HOUND || beast == Beast.SHADOW_BEAST) {
            targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Villager.class, true));
        }
    }

    @Override
    public boolean considersEntityAsAlly(Entity other) {
        if (other instanceof BeastEntity b && b.beast.id.startsWith(beast == Beast.CHIMERA_ALPHA ? "cult_chimera" : beast.id)) return true;
        if ((beast == Beast.CHIMERA || beast == Beast.CHIMERA_ALPHA)
                && (other instanceof CultistEntity || other instanceof FoeEntity || other instanceof BeastEntity b2
                && (b2.beast == Beast.CHIMERA || b2.beast == Beast.CHIMERA_ALPHA))) return true;
        return super.considersEntityAsAlly(other);
    }

    @Override
    public void tick() {
        // Como un vex: el espectro atraviesa las paredes.
        if (beast == Beast.MANA_WRAITH) noPhysics = true;
        super.tick();
        if (beast == Beast.MANA_WRAITH) noPhysics = false;
        if (beast.flyer()) setNoGravity(true);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (beast.flyer()) resetFallDistance();
        if (!(level() instanceof ServerLevel level) || !isAlive()) return;
        // Los sabuesos y murciélagos de sangre arden al sol, como sus amos.
        if ((beast == Beast.BLOOD_HOUND || beast == Beast.BLOOD_BAT) && tickCount % 20 == 0 && level.isBrightOutside()
                && !isInWater() && !level.isRainingAt(blockPosition()) && level.canSeeSky(blockPosition().above())) {
            igniteForSeconds(6.0F);
        }
        if (beast == Beast.SHADOW_BEAST && tickCount % 10 == 0) {
            int light = level.getMaxLocalRawBrightness(blockPosition().above());
            boolean dark = light <= 7;
            if (dark != isInvisible()) setInvisible(dark);
            if (dark) {
                // Solo los ojos rojos en la oscuridad.
                Vec3 look = getLookAngle();
                Vec3 eyes = position().add(look.x * 0.6, getBbHeight() * 0.78, look.z * 0.6);
                Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(0.18);
                DustParticleOptions red = new DustParticleOptions(0xFF1020, 0.6F);
                level.sendParticles(red, eyes.x + side.x, eyes.y, eyes.z + side.z, 1, 0, 0, 0, 0);
                level.sendParticles(red, eyes.x - side.x, eyes.y, eyes.z - side.z, 1, 0, 0, 0, 0);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!initialized) {
            // Los que vienen de una plantilla (Base del Culto) se ajustan al primer tick.
            initialized = true;
            var scale = getAttribute(Attributes.SCALE);
            if (beast == Beast.SLIME_GOLEM && scale != null && scale.getBaseValue() == 1.0) setSize(size);
            if (beast == Beast.CHIMERA_ALPHA && scale != null && scale.getBaseValue() < 2.0) setupAlpha();
        }
        if (bossBar != null) bossBar.setProgress(getHealth() / getMaxHealth());
        LivingEntity target = getTarget();
        if (contactCooldown > 0) contactCooldown--;
        switch (beast) {
            case BLOOD_BAT, MANA_WRAITH -> flyTick(level, target);
            case CHIMERA -> {
                if (tickCount % 40 == 0 && getHealth() < getMaxHealth()) heal(1.0F);
            }
            case CHIMERA_ALPHA -> alphaTick(level, target);
            case SHADOW_BEAST -> shadowTick(level, target);
            default -> {}
        }
        if (beast == Beast.MANA_WRAITH) {
            level.sendParticles(ModParticles.VIOLET_SPARK.value(), getX(), getY() + 0.9, getZ(), 1, 0.25, 0.4, 0.25, 0.01);
            // Se disipa si pasa mucho tiempo sin magia que devorar.
            if (++life > 2400 && target == null) {
                level.sendParticles(ModParticles.ENERGY_PETAL.value(), getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.05);
                discard();
            }
        }
    }

    /** Vuelo de murciélagos y espectros: van directo al objetivo y lo dañan al tocarlo. */
    private void flyTick(ServerLevel level, LivingEntity target) {
        Vec3 goal;
        double speed = beast == Beast.BLOOD_BAT ? 0.32 : 0.22;
        if (target != null && target.isAlive()) {
            double t = tickCount * 0.25 + getId();
            goal = target.position().add(Math.cos(t) * 0.8, target.getBbHeight() * 0.6 + Math.sin(t * 1.7) * 0.4, Math.sin(t) * 0.8);
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (contactCooldown == 0 && getBoundingBox().inflate(0.4).intersects(target.getBoundingBox())) {
                contactCooldown = beast == Beast.BLOOD_BAT ? 16 : 30;
                if (target.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
                    if (beast == Beast.BLOOD_BAT) {
                        // Robo de vida.
                        heal(3.0F);
                        if (getRandom().nextFloat() < 0.3F && getTarget() instanceof ServerPlayer) {
                            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                        }
                        level.sendParticles(ModParticles.BLOOD_DROP.value(), target.getX(), target.getY() + 1, target.getZ(), 6, 0.2, 0.3, 0.2, 0.05);
                        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BAT_HURT, SoundSource.HOSTILE, 0.5F, 0.7F);
                    } else if (target instanceof ServerPlayer p) {
                        // El espectro bebe poder mágico.
                        Mana.steal(p, Math.min(Mana.get(p), 20));
                        Mana.sync(p);
                        heal(4.0F);
                        p.sendOverlayMessage(Component.translatable("message.draxatomic.wraith_drain").withStyle(ChatFormatting.DARK_PURPLE));
                        level.sendParticles(ModParticles.VIOLET_SPARK.value(), p.getX(), p.getY() + 1, p.getZ(), 14, 0.3, 0.5, 0.3, 0.05);
                        level.playSound(null, getX(), getY(), getZ(), SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 0.8F, 0.6F);
                    }
                }
            }
        } else {
            if (tickCount % 60 == 0 || getDeltaMovement().lengthSqr() < 0.001) {
                int gx = (int) Math.floor(getX() + getRandom().nextInt(13) - 6);
                int gz = (int) Math.floor(getZ() + getRandom().nextInt(13) - 6);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                goal = new Vec3(gx + 0.5, Math.max(ground + 2 + getRandom().nextInt(4), getY() - 2), gz + 0.5);
                wander = goal;
            }
            goal = wander != null ? wander : position();
            speed *= 0.5;
        }
        Vec3 dir = goal.subtract(position());
        if (dir.lengthSqr() > 0.01) {
            Vec3 v = getDeltaMovement().scale(0.8).add(dir.normalize().scale(speed * 0.25));
            setDeltaMovement(v);
            setYRot((float) (Math.atan2(v.z, v.x) * 180 / Math.PI) - 90.0F);
            yBodyRot = getYRot();
        }
    }

    private Vec3 wander;

    /** Bestia de sombra: aparece a la espalda de su presa si está a oscuras. */
    private void shadowTick(ServerLevel level, LivingEntity target) {
        if (target == null || --abilityCooldown > 0 || !isInvisible()) return;
        abilityCooldown = 120 + getRandom().nextInt(60);
        Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1.8, 0, 1.8));
        BlockPos at = BlockPos.containing(behind);
        if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir() || !level.getBlockState(at.below()).isSolid()) return;
        level.sendParticles(ModParticles.SHADOW_SMOKE.value(), getX(), getY() + 1, getZ(), 20, 0.4, 0.8, 0.4, 0.02);
        teleportTo(behind.x, at.getY(), behind.z);
        level.sendParticles(ModParticles.SHADOW_SMOKE.value(), getX(), getY() + 1, getZ(), 20, 0.4, 0.8, 0.4, 0.02);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.HOSTILE, 0.6F, 1.4F);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        float bonus = 0;
        if (beast == Beast.SHADOW_BEAST && target instanceof LivingEntity living) {
            // Desde la espalda: daño doble.
            Vec3 toMe = position().subtract(living.position()).normalize();
            if (toMe.dot(living.getLookAngle()) < -0.3) bonus = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        }
        boolean hit = super.doHurtTarget(level, target);
        if (!hit || !(target instanceof LivingEntity living)) return hit;
        if (bonus > 0) {
            living.hurtServer(level, damageSources().mobAttack(this), bonus);
            level.sendParticles(ModParticles.SHADOW_SMOKE.value(), living.getX(), living.getY() + 1, living.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            if (living instanceof ServerPlayer p) {
                p.sendOverlayMessage(Component.translatable("message.draxatomic.backstab").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        switch (beast) {
            case BLOOD_HOUND -> {
                // Desangra: un poco de daño de más y lentitud.
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
                level.sendParticles(ModParticles.BLOOD_DROP.value(), living.getX(), living.getY() + 0.8, living.getZ(), 5, 0.2, 0.3, 0.2, 0.05);
            }
            case SLIME_GOLEM -> living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, size - 1));
            case CHIMERA_ALPHA -> {
                Vec3 push = living.position().subtract(position()).normalize().scale(1.6);
                living.push(push.x, 0.5, push.z);
                living.hurtMarked = true;
            }
            default -> {}
        }
        return true;
    }

    // ---------------------------------------------------------------- Quimera Alfa

    private void alphaTick(ServerLevel level, LivingEntity target) {
        if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) heal(2.0F);
        // Púas de cristal pendientes.
        spikes.removeIf(s -> {
            if (--s.delay()[0] > 0) {
                level.sendParticles(ModParticles.LIGHT_SHARD.value(), s.at().x, s.at().y + 0.1, s.at().z, 3, 1.0, 0.05, 1.0, 0);
                return false;
            }
            Fx3D.shards(level, s.at().add(0, 0.5, 0), 6, 2.5, 18);
            level.sendParticles(ModParticles.SHOCKWAVE.value(), s.at().x, s.at().y + 0.3, s.at().z, 1, 0, 0, 0, 0);
            level.playSound(null, s.at().x, s.at().y, s.at().z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(s.at(), s.at()).inflate(2.0, 2.5, 2.0),
                    e -> e != this && !isAlliedTo(e))) {
                e.hurtServer(level, damageSources().mobAttack(this), 12.0F);
                e.push(0, 0.9, 0);
                e.hurtMarked = true;
            }
            return true;
        });
        if (target == null) return;
        // A la mitad de su vida llama a su camada.
        if (!summoned && getHealth() < getMaxHealth() / 2) {
            summoned = true;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.6F);
            for (int i = 0; i < 3; i++) {
                BeastEntity cub = Monsters.CHIMERA.value().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (cub == null) continue;
                double a = i * Math.PI * 2 / 3;
                cub.snapTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, getRandom().nextFloat() * 360, 0);
                cub.setTarget(target);
                level.addFreshEntity(cub);
            }
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) < 48 * 48) p.sendSystemMessage(Component.translatable("message.draxatomic.alpha_howl").withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
        // Golpe sísmico: salta y cae sobre todo lo que tenga cerca.
        if (slamming && onGround() && getDeltaMovement().y <= 0.01) {
            slamming = false;
            level.sendParticles(ModParticles.SHOCKWAVE.value(), true, true, getX(), getY() + 0.2, getZ(), 3, 2.0, 0.1, 2.0, 0);
            Fx3D.ring(level, position().add(0, 0.3, 0), 6.0, 16, 16);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.6F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(7, 2, 7), e -> e != this && !isAlliedTo(e))) {
                Vec3 push = e.position().subtract(position()).normalize().scale(1.4);
                e.push(push.x, 0.7, push.z);
                e.hurtMarked = true;
                e.hurtServer(level, damageSources().mobAttack(this), 14.0F);
            }
        }
        if (--abilityCooldown <= 0 && onGround() && distanceToSqr(target) < 14 * 14) {
            abilityCooldown = summoned ? 90 : 130;
            slamming = true;
            Vec3 dir = target.position().subtract(position()).normalize();
            setDeltaMovement(dir.x * 0.9, 1.1, dir.z * 0.9);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        if (--spikeCooldown <= 0) {
            spikeCooldown = summoned ? 100 : 160;
            spikes.add(new Spike(target.position(), new int[] {25}));
            if (summoned) {
                for (int i = 0; i < 3; i++) {
                    double a = getRandom().nextDouble() * Math.PI * 2;
                    spikes.add(new Spike(target.position().add(Math.cos(a) * 4, 0, Math.sin(a) * 4), new int[] {35 + i * 6}));
                }
            }
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (bossBar != null) bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (bossBar != null) bossBar.removePlayer(player);
    }

    // ---------------------------------------------------------------- daño y muerte

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (beast == Beast.MANA_WRAITH && !magical(source)) {
            // Las armas normales lo atraviesan.
            level.sendParticles(ModParticles.ENERGY_PETAL.value(), getX(), getY() + 1, getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            if (source.getEntity() instanceof ServerPlayer p && tickCount % 4 == 0) {
                p.sendOverlayMessage(Component.translatable("message.draxatomic.wraith_immune").withStyle(ChatFormatting.GRAY));
            }
            return false;
        }
        if (beast == Beast.SHADOW_BEAST && source.is(DamageTypeTags.IS_FIRE)) amount *= 1.5F;
        if ((beast == Beast.CHIMERA || beast == Beast.CHIMERA_ALPHA) && source.getEntity() instanceof CultistEntity) return false;
        return super.hurtServer(level, source, amount);
    }

    /** ¿Es daño mágico? (hechizos, Atomic, la espada de limo…). */
    static boolean magical(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.SONIC_BOOM)
                || source.is(DamageTypeTags.IS_EXPLOSION)) return true;
        return source.getEntity() instanceof Player p && p.getMainHandItem().getItem() instanceof SlimeSwordItem;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) {
            if (beast == Beast.SLIME_GOLEM && size > 1) {
                // Se parte en dos más pequeños.
                for (int i = 0; i < 2; i++) {
                    BeastEntity part = Monsters.SLIME_GOLEM.value().create(level, EntitySpawnReason.TRIGGERED);
                    if (part == null) continue;
                    part.snapTo(getX() + (i == 0 ? -0.4 : 0.4), getY() + 0.3, getZ(), getRandom().nextFloat() * 360, 0);
                    part.setSize(size - 1);
                    if (getTarget() != null) part.setTarget(getTarget());
                    level.addFreshEntity(part);
                }
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.5F, 0.6F);
            }
            if (beast == Beast.MANA_WRAITH) {
                level.sendParticles(ModParticles.ENERGY_PETAL.value(), getX(), getY() + 1, getZ(), 40, 0.4, 0.6, 0.4, 0.08);
            }
            if (beast == Beast.CHIMERA_ALPHA) {
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(this) < 64 * 64) p.sendSystemMessage(Component.translatable("message.draxatomic.alpha_defeated").withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                Fx3D.shards(level, position().add(0, 1.5, 0), 14, 4.0, 30);
            }
        }
        super.die(source);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        var r = getRandom();
        switch (beast) {
            case BLOOD_HOUND -> {
                if (r.nextFloat() < 0.4F) spawnAtLocation(level, new ItemStack(Monsters.FANG.value()));
                if (r.nextFloat() < 0.15F) spawnAtLocation(level, new ItemStack(Vampires.BLOOD_VIAL.value()));
            }
            case BLOOD_BAT -> {
                if (r.nextFloat() < 0.2F) spawnAtLocation(level, new ItemStack(Vampires.BLOOD_VIAL.value()));
            }
            case CHIMERA -> {
                if (r.nextFloat() < 0.5F) spawnAtLocation(level, new ItemStack(Monsters.CHIMERA_SHARD.value(), 1 + r.nextInt(2)));
                if (r.nextFloat() < 0.15F) spawnAtLocation(level, new ItemStack(Progression.CRYSTAL.value()));
            }
            case CHIMERA_ALPHA -> {
                spawnAtLocation(level, new ItemStack(Monsters.CHIMERA_CORE.value()));
                spawnAtLocation(level, new ItemStack(Monsters.VOID_SIGIL.value()));
                spawnAtLocation(level, new ItemStack(Monsters.CHIMERA_SHARD.value(), 6 + r.nextInt(4)));
            }
            case SLIME_GOLEM -> {
                spawnAtLocation(level, new ItemStack(Items.SLIME_BALL, size + r.nextInt(2)));
                if (size == 3 && r.nextFloat() < 0.3F) spawnAtLocation(level, new ItemStack(Progression.CRYSTAL.value()));
            }
            case SHADOW_BEAST -> {
                spawnAtLocation(level, new ItemStack(Monsters.SHADOW_ESSENCE.value(), 1 + r.nextInt(2)));
            }
            case MANA_WRAITH -> {
                spawnAtLocation(level, new ItemStack(Monsters.MANA_RESIDUE.value(), 1 + r.nextInt(2)));
                if (r.nextFloat() < 0.35F) spawnAtLocation(level, new ItemStack(Progression.CRYSTAL.value()));
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return beast != Beast.CHIMERA_ALPHA && super.removeWhenFarAway(distance);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Size", size);
        output.putBoolean("Summoned", summoned);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int s = input.getIntOr("Size", 3);
        if (beast == Beast.SLIME_GOLEM && s != size) setSize(s);
        summoned = input.getBooleanOr("Summoned", false);
        if (beast == Beast.CHIMERA_ALPHA) setupAlphaScale();
    }

    private void setupAlphaScale() {
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null && scale.getBaseValue() < 2.0) scale.setBaseValue(2.0);
    }

    // ---------------------------------------------------------------- sonidos

    @Override
    protected SoundEvent getAmbientSound() {
        return switch (beast) {
            case BLOOD_HOUND -> SoundEvents.HOGLIN_ANGRY;
            case CHIMERA, CHIMERA_ALPHA -> SoundEvents.RAVAGER_AMBIENT;
            case BLOOD_BAT -> SoundEvents.BAT_AMBIENT;
            case SLIME_GOLEM -> SoundEvents.SLIME_SQUISH;
            case SHADOW_BEAST -> isInvisible() ? null : SoundEvents.WARDEN_AMBIENT;
            case MANA_WRAITH -> SoundEvents.VEX_AMBIENT;
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return switch (beast) {
            case BLOOD_HOUND -> SoundEvents.HOGLIN_HURT;
            case CHIMERA, CHIMERA_ALPHA -> SoundEvents.RAVAGER_HURT;
            case BLOOD_BAT -> SoundEvents.BAT_HURT;
            case SLIME_GOLEM -> SoundEvents.SLIME_HURT;
            case SHADOW_BEAST -> SoundEvents.WARDEN_HURT;
            case MANA_WRAITH -> SoundEvents.VEX_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return switch (beast) {
            case BLOOD_HOUND -> SoundEvents.HOGLIN_DEATH;
            case CHIMERA, CHIMERA_ALPHA -> SoundEvents.RAVAGER_DEATH;
            case BLOOD_BAT -> SoundEvents.BAT_DEATH;
            case SLIME_GOLEM -> SoundEvents.SLIME_DEATH;
            case SHADOW_BEAST -> SoundEvents.WARDEN_DEATH;
            case MANA_WRAITH -> SoundEvents.VEX_DEATH;
        };
    }
}
