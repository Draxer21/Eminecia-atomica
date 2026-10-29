package com.draxer.atomic;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Integrante de la Shadow Garden. Sigue a quien la invocó, lo defiende, ataca a los monstruos
 * cercanos y pelea según su rol. Clic derecho (su dueño): alterna entre seguir y esperar.
 */
public class ShadowAllyEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> MEMBER =
            SynchedEntityData.defineId(ShadowAllyEntity.class, EntityDataSerializers.INT);

    private @Nullable UUID owner;
    private boolean staying;
    private int skillCooldown;

    public ShadowAllyEntity(EntityType<? extends ShadowAllyEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    // ---------------------------------------------------------------- datos

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MEMBER, 0);
    }

    public ShadowGarden.Member member() {
        return ShadowGarden.Member.of(entityData.get(MEMBER));
    }

    public ShadowGarden.Role role() {
        return member().role;
    }

    /** Recién invocada: rol, nombre, equipo y estadísticas. */
    void setup(ShadowGarden.Member member, Player summoner) {
        entityData.set(MEMBER, member.ordinal());
        owner = summoner.getUUID();
        setCustomName(member.displayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        setCustomNameVisible(true);
        applyRole();
        setHealth(getMaxHealth());
    }

    private void applyRole() {
        ShadowGarden.Role role = role();
        var health = getAttribute(Attributes.MAX_HEALTH);
        var damage = getAttribute(Attributes.ATTACK_DAMAGE);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        var armor = getAttribute(Attributes.ARMOR);
        if (health != null) health.setBaseValue(switch (role) {
            case GUARDIAN -> 90.0;
            case BERSERKER -> 55.0;
            case MAGE, ARCHER, HEALER -> 32.0;
            default -> 40.0;
        });
        if (damage != null) damage.setBaseValue(switch (role) {
            case BERSERKER -> 16.0;
            case SWORD -> 11.0;
            case STEALTH -> 9.0;
            case GUARDIAN -> 7.0;
            default -> 4.0;
        });
        if (speed != null) speed.setBaseValue(switch (role) {
            case STEALTH -> 0.38;
            case GUARDIAN -> 0.27;
            default -> 0.32;
        });
        if (armor != null) armor.setBaseValue(role == ShadowGarden.Role.GUARDIAN ? 18.0 : 8.0);
        ItemStack hand = switch (role) {
            case SWORD, BERSERKER -> new ItemStack(Items.NETHERITE_SWORD);
            case STEALTH -> new ItemStack(Items.IRON_SWORD);
            case MAGE -> new ItemStack(Items.BLAZE_ROD);
            case ARCHER -> new ItemStack(Items.BOW);
            case HEALER -> new ItemStack(Items.AMETHYST_SHARD);
            case GUARDIAN -> new ItemStack(Items.NETHERITE_AXE);
        };
        setItemSlot(EquipmentSlot.MAINHAND, hand);
        if (role == ShadowGarden.Role.GUARDIAN) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        setDropChance(EquipmentSlot.OFFHAND, 0.0F);
    }

    public @Nullable Player owner() {
        return owner == null ? null : level().getPlayerByUUID(owner);
    }

    public boolean isOwnedBy(Entity entity) {
        return owner != null && entity != null && owner.equals(entity.getUUID());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Member", entityData.get(MEMBER));
        output.putBoolean("Staying", staying);
        if (owner != null) output.store("Owner", UUIDUtil.CODEC, owner);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(MEMBER, input.getIntOr("Member", 0));
        staying = input.getBooleanOr("Staying", false);
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ---------------------------------------------------------------- bandos

    /** Aliadas entre sí y con su dueño (así el Atomic y las habilidades no las dañan). */
    @Override
    public boolean isAlliedTo(Entity other) {
        if (isOwnedBy(other)) return true;
        if (other instanceof ShadowAllyEntity ally && ally.owner != null && ally.owner.equals(owner)) return true;
        return super.isAlliedTo(other);
    }

    private boolean isFoe(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && target != this && !isAlliedTo(target)
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return isFoe(target) && super.canAttack(target);
    }

    // ---------------------------------------------------------------- IA

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new RangedSkillGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true) {
            @Override
            public boolean canUse() {
                return !isRanged() && super.canUse();
            }
        });
        goalSelector.addGoal(3, new FollowOwnerGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new DefendOwnerGoal(this));
        targetSelector.addGoal(2, new HurtByTargetGoal(this).setAlertOthers(ShadowAllyEntity.class));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                (target, level) -> target instanceof Enemy && !(target instanceof net.minecraft.world.entity.monster.Creeper)));
    }

    boolean isRanged() {
        ShadowGarden.Role role = role();
        return role == ShadowGarden.Role.MAGE || role == ShadowGarden.Role.ARCHER || role == ShadowGarden.Role.HEALER;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level)) return;
        if (skillCooldown > 0) skillCooldown--;
        if (tickCount % 40 == 0 && getHealth() < getMaxHealth()) heal(1.0F);
        switch (role()) {
            case STEALTH -> {
                // Invisible mientras no pelea.
                if (getTarget() == null && tickCount % 20 == 0) {
                    addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
                } else if (getTarget() != null) {
                    removeEffect(MobEffects.INVISIBILITY);
                }
            }
            case HEALER -> {
                if (skillCooldown == 0 && tickCount % 10 == 0) healFriends(level);
            }
            default -> {}
        }
    }

    /** Sanadora: cura al dueño y a las aliadas heridas cercanas. */
    private void healFriends(ServerLevel level) {
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12),
                e -> e != this && isAlliedTo(e) && e.getHealth() < e.getMaxHealth() * 0.75F);
        if (getHealth() < getMaxHealth() * 0.75F) near.add(this);
        if (near.isEmpty()) return;
        for (LivingEntity friend : near) {
            friend.heal(6.0F);
            level.sendParticles(ParticleTypes.HEART, friend.getX(), friend.getY() + friend.getBbHeight() + 0.3, friend.getZ(),
                    3, 0.3, 0.2, 0.3, 0);
            level.sendParticles(new DustParticleOptions(0xD9B8FF, 1.0F), friend.getX(), friend.getY() + 1, friend.getZ(),
                    10, 0.4, 0.6, 0.4, 0);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.4F);
        swing(InteractionHand.MAIN_HAND, true);
        skillCooldown = 80;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && role() == ShadowGarden.Role.BERSERKER && skillCooldown == 0) {
            // Golpe de barrido: daña a los enemigos alrededor del objetivo.
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.5),
                    e -> e != target && isFoe(e))) {
                other.hurtServer(level, damageSources().mobAttack(this), 8.0F);
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1, target.getZ(), 3, 1, 0.2, 1, 0);
            skillCooldown = 30;
        }
        if (hit && role() == ShadowGarden.Role.STEALTH && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
        }
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() != null && isAlliedTo(source.getEntity())) return false;
        return super.hurtServer(level, source, amount);
    }

    // ---------------------------------------------------------------- dueño

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isOwnedBy(player) || hand != InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);
        if (!level().isClientSide()) {
            staying = !staying;
            getNavigation().stop();
            setTarget(null);
            player.sendOverlayMessage(Component.translatable(staying ? "message.draxatomic.ally_stay" : "message.draxatomic.ally_follow",
                    member().displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return InteractionResult.SUCCESS;
    }

    boolean staying() {
        return staying;
    }

    /** Efecto de llegada al ser invocada. */
    void arrive() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(new DustParticleOptions(0x2A1048, 2.0F), getX(), getY() + 1, getZ(), 40, 0.4, 0.9, 0.4, 0);
            level.sendParticles(new DustParticleOptions(0x8A3BFF, 1.4F), getX(), getY() + 1, getZ(), 25, 0.5, 1.0, 0.5, 0);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) {
            // Devuelve el emblema para poder invocarla de nuevo.
            spawnAtLocation(level, new ItemStack(ShadowGarden.EMBLEM.value()));
            Player player = owner();
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.draxatomic.ally_fell", member().displayName())
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
        super.die(source);
    }

    // ---------------------------------------------------------------- objetivos

    /** Sigue al dueño; si queda muy lejos, aparece a su lado. */
    static final class FollowOwnerGoal extends Goal {
        private final ShadowAllyEntity ally;
        private @Nullable Player owner;
        private int recalc;

        FollowOwnerGoal(ShadowAllyEntity ally) {
            this.ally = ally;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (ally.staying()) return false;
            Player player = ally.owner();
            if (player == null || player.isSpectator() || ally.getTarget() != null) return false;
            if (ally.distanceToSqr(player) < 36) return false;
            owner = player;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && !ally.staying() && ally.getTarget() == null && ally.distanceToSqr(owner) > 9;
        }

        @Override
        public void stop() {
            owner = null;
            ally.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (owner == null) return;
            ally.getLookControl().setLookAt(owner, 10.0F, ally.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = 10;
            if (ally.distanceToSqr(owner) > 24 * 24) {
                Vec3 behind = owner.position().subtract(owner.getLookAngle().multiply(2, 0, 2));
                ally.teleportTo(behind.x, owner.getY(), behind.z);
                ally.getNavigation().stop();
                ally.arrive();
            } else {
                ally.getNavigation().moveTo(owner, 1.2);
            }
        }
    }

    /** Ataca a quien hiera al dueño o a quien el dueño esté atacando. */
    static final class DefendOwnerGoal extends Goal {
        private final ShadowAllyEntity ally;
        private int lastHurtBy;
        private int lastHurt;

        DefendOwnerGoal(ShadowAllyEntity ally) {
            this.ally = ally;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (ally.staying()) return false;
            Player player = ally.owner();
            if (player == null) return false;
            LivingEntity attacker = player.getLastHurtByMob();
            if (attacker != null && player.getLastHurtByMobTimestamp() != lastHurtBy && ally.isFoe(attacker)) return true;
            LivingEntity victim = player.getLastHurtMob();
            return victim != null && player.getLastHurtMobTimestamp() != lastHurt && ally.isFoe(victim);
        }

        @Override
        public void start() {
            Player player = ally.owner();
            if (player == null) return;
            LivingEntity attacker = player.getLastHurtByMob();
            if (attacker != null && player.getLastHurtByMobTimestamp() != lastHurtBy && ally.isFoe(attacker)) {
                ally.setTarget(attacker);
            } else {
                ally.setTarget(player.getLastHurtMob());
            }
            lastHurtBy = player.getLastHurtByMobTimestamp();
            lastHurt = player.getLastHurtMobTimestamp();
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }

    /** Maga y arquera: disparan rayos de slime a distancia; la sanadora también, más débil. */
    static final class RangedSkillGoal extends Goal {
        private final ShadowAllyEntity ally;
        private int cooldown;

        RangedSkillGoal(ShadowAllyEntity ally) {
            this.ally = ally;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return ally.isRanged() && ally.getTarget() != null && ally.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            ally.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = ally.getTarget();
            if (target == null || !(ally.level() instanceof ServerLevel level)) return;
            ally.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double range = ally.role() == ShadowGarden.Role.ARCHER ? 24 : 16;
            double dist = ally.distanceTo(target);
            boolean sees = ally.getSensing().hasLineOfSight(target);
            if (dist > range * 0.8 || !sees) ally.getNavigation().moveTo(target, 1.1);
            else if (dist < 5) {
                Vec3 away = ally.position().subtract(target.position()).normalize().scale(4).add(ally.position());
                ally.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
            } else ally.getNavigation().stop();
            if (--cooldown > 0 || !sees || dist > range) return;
            ShadowGarden.Role role = ally.role();
            cooldown = switch (role) {
                case ARCHER -> 25;
                case MAGE -> 40;
                default -> 50;
            };
            float damage = switch (role) {
                case MAGE -> 14.0F;
                case ARCHER -> 9.0F;
                default -> 5.0F;
            };
            bolt(level, target, damage, role == ShadowGarden.Role.MAGE ? 0x8A3BFF : (role == ShadowGarden.Role.ARCHER ? 0x5FE6FF : 0xD9B8FF));
        }

        private void bolt(ServerLevel level, LivingEntity target, float damage, int color) {
            Vec3 from = ally.getEyePosition();
            Vec3 to = target.getBoundingBox().getCenter();
            Vec3 step = to.subtract(from);
            double length = step.length();
            DustParticleOptions dust = new DustParticleOptions(color, 1.2F);
            for (double d = 0; d < length; d += 0.4) {
                Vec3 p = from.add(step.scale(d / length));
                level.sendParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            }
            target.hurtServer(level, ally.damageSources().indirectMagic(ally, ally), damage);
            if (ally.role() == ShadowGarden.Role.MAGE) {
                level.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y, to.z, 1, 0, 0, 0, 0);
            }
            level.playSound(null, ally.getX(), ally.getY(), ally.getZ(),
                    ally.role() == ShadowGarden.Role.ARCHER ? SoundEvents.ARROW_SHOOT : SoundEvents.EVOKER_CAST_SPELL,
                    SoundSource.NEUTRAL, 1.0F, 1.2F);
            ally.swing(InteractionHand.MAIN_HAND, true);
        }
    }
}
