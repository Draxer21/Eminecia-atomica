package com.draxer.atomic;

import java.util.EnumSet;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
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
 * Enemigos humanoides nuevos:
 * <ul>
 *   <li>Niños del Culto (3.º, 2.º y 1.º): espada y magia; el 1.º además se desvanece en sombra y reaparece detrás.</li>
 *   <li>Poseído fallido: lento, magia inestable y estalla al morir.</li>
 *   <li>Bandido: te roba esmeraldas y huye; si lo atrapas, las devuelve.</li>
 *   <li>Luchador del foso: puños demoledores y mucha vida.</li>
 * </ul>
 */
public class FoeEntity extends Monster {
    public enum Foe {
        CHILD_3("cult_child_3", 30, 6, 4, 0.30),
        CHILD_2("cult_child_2", 45, 8, 8, 0.31),
        CHILD_1("cult_child_1", 80, 11, 12, 0.33),
        POSSESSED("possessed", 40, 7, 2, 0.24),
        BANDIT("bandit", 26, 4, 3, 0.36),
        FIGHTER("pit_fighter", 70, 10, 6, 0.30);

        public final String id;
        final double health;
        final double damage;
        final double armor;
        final double speed;

        Foe(String id, double health, double damage, double armor, double speed) {
            this.id = id;
            this.health = health;
            this.damage = damage;
            this.armor = armor;
            this.speed = speed;
        }

        boolean child() {
            return this == CHILD_1 || this == CHILD_2 || this == CHILD_3;
        }
    }

    private final Foe foe;
    private int castCooldown = 40;
    private int shadowCooldown = 80;
    /** Esmeraldas robadas (las devuelve al morir). */
    private int stolen;
    private int fleeTicks;
    private boolean equipped;

    public FoeEntity(EntityType<? extends FoeEntity> type, Level level, Foe foe) {
        super(type, level);
        this.foe = foe;
        // Mob llama a registerGoals() antes de que exista el tipo: los objetivos se agregan aquí.
        addGoals();
        xpReward = foe == Foe.CHILD_1 ? 30 : foe == Foe.FIGHTER ? 20 : 10;
    }

    public static AttributeSupplier.Builder createAttributes(Foe foe) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, foe.health)
                .add(Attributes.ATTACK_DAMAGE, foe.damage)
                .add(Attributes.ARMOR, foe.armor)
                .add(Attributes.MOVEMENT_SPEED, foe.speed)
                .add(Attributes.KNOCKBACK_RESISTANCE, foe == Foe.FIGHTER ? 0.6 : 0.1)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    public Foe foe() {
        return foe;
    }

    /** Equipo al aparecer. */
    void equip() {
        ItemStack hand = switch (foe) {
            case CHILD_3 -> new ItemStack(Items.IRON_SWORD);
            case CHILD_2 -> new ItemStack(Items.DIAMOND_SWORD);
            case CHILD_1 -> new ItemStack(Items.NETHERITE_SWORD);
            case BANDIT -> new ItemStack(Items.IRON_SWORD);
            default -> ItemStack.EMPTY;
        };
        setItemSlot(EquipmentSlot.MAINHAND, hand);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    protected void registerGoals() {}

    private void addGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeGoal(this));
        goalSelector.addGoal(2, new CastGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, foe == Foe.POSSESSED ? 1.0 : 1.2, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, ShadowAllyEntity.class, true));
        if (foe.child() || foe == Foe.POSSESSED) targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Villager.class, false));
    }

    @Override
    public boolean considersEntityAsAlly(Entity other) {
        if (foe.child() && (other instanceof CultistEntity || other instanceof RoundsKnightEntity
                || other instanceof FoeEntity f && f.foe.child())) return true;
        return super.considersEntityAsAlly(other);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!equipped) {
            equipped = true;
            if (getMainHandItem().isEmpty()) equip();
        }
        if (fleeTicks > 0 && --fleeTicks == 0 && stolen > 0) {
            // Se escapó con el botín.
            level.sendParticles(ModParticles.SHADOW_SMOKE.value(), getX(), getY() + 1, getZ(), 20, 0.4, 0.8, 0.4, 0.02);
            discard();
            return;
        }
        if (foe == Foe.POSSESSED && tickCount % 5 == 0) {
            level.sendParticles(ModParticles.LIGHT_SHARD.value(), getX(), getY() + 1.0, getZ(), 1, 0.3, 0.6, 0.3, 0);
        }
        LivingEntity target = getTarget();
        if (foe == Foe.CHILD_1 && target != null && --shadowCooldown <= 0 && distanceToSqr(target) > 36) {
            shadowCooldown = 100;
            Vec3 behind = target.position().subtract(target.getLookAngle().multiply(2.5, 0, 2.5));
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(behind.x), (int) Math.floor(behind.z));
            if (Math.abs(y - target.getY()) < 4) {
                level.sendParticles(ModParticles.SHADOW_SMOKE.value(), getX(), getY() + 1, getZ(), 24, 0.4, 0.8, 0.4, 0.02);
                teleportTo(behind.x, y, behind.z);
                level.sendParticles(ModParticles.SHADOW_SMOKE.value(), getX(), getY() + 1, getZ(), 24, 0.4, 0.8, 0.4, 0.02);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.6F);
            }
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (!hit) return false;
        if (foe == Foe.FIGHTER) {
            Vec3 push = target.position().subtract(position()).normalize().scale(1.4);
            target.push(push.x, 0.45, push.z);
            target.hurtMarked = true;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.2F, 0.6F);
        }
        if (foe == Foe.BANDIT && stolen == 0 && target instanceof ServerPlayer player && !player.isCreative()) {
            int taken = 0;
            for (int i = 0; i < player.getInventory().getContainerSize() && taken < 8; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.is(Items.EMERALD)) {
                    int n = Math.min(8 - taken, s.getCount());
                    s.shrink(n);
                    taken += n;
                }
            }
            if (taken > 0) {
                stolen = taken;
                fleeTicks = 240;
                setTarget(null);
                player.sendOverlayMessage(Component.translatable("message.draxatomic.bandit_stole", taken).withStyle(ChatFormatting.RED));
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 1.0F, 0.6F);
            }
        }
        return true;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) {
            if (foe == Foe.POSSESSED) {
                // La magia inestable estalla.
                Vec3 c = position().add(0, 1, 0);
                level.sendParticles(ModParticles.SHOCKWAVE.value(), true, true, c.x, c.y, c.z, 2, 0.5, 0.3, 0.5, 0);
                Fx3D.shards(level, c, 8, 3.0, 20);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.3F);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4), e -> e != this)) {
                    e.hurtServer(level, damageSources().indirectMagic(this, this), 8.0F);
                }
            }
            if (stolen > 0) {
                spawnAtLocation(level, new ItemStack(Items.EMERALD, stolen));
                if (source.getEntity() instanceof ServerPlayer p) {
                    p.sendOverlayMessage(Component.translatable("message.draxatomic.bandit_caught").withStyle(ChatFormatting.GREEN));
                }
            }
        }
        super.die(source);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (foe.child() && getRandom().nextFloat() < (foe == Foe.CHILD_1 ? 0.8F : 0.3F)) {
            spawnAtLocation(level, new ItemStack(Monsters.INSIGNIA.value()));
        }
        if (foe == Foe.FIGHTER && getRandom().nextFloat() < 0.5F) spawnAtLocation(level, new ItemStack(Items.EMERALD, 2 + getRandom().nextInt(4)));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return stolen == 0 && super.removeWhenFarAway(distance);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Stolen", stolen);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        stolen = input.getIntOr("Stolen", 0);
    }

    /** El bandido huye con lo robado. */
    static final class FleeGoal extends Goal {
        private final FoeEntity foe;

        FleeGoal(FoeEntity foe) {
            this.foe = foe;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return foe.fleeTicks > 0;
        }

        @Override
        public void tick() {
            Player p = foe.level().getNearestPlayer(foe, 24);
            if (p == null || !foe.getNavigation().isDone() && foe.tickCount % 20 != 0) return;
            Vec3 away = foe.position().subtract(p.position()).normalize().scale(16);
            foe.getNavigation().moveTo(foe.getX() + away.x, foe.getY(), foe.getZ() + away.z, 1.5);
        }
    }

    /** Magia a distancia: Niños del Culto (rayos) y Poseídos (rayos inestables de colores). */
    static final class CastGoal extends Goal {
        private final FoeEntity mob;

        CastGoal(FoeEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return (mob.foe.child() || mob.foe == Foe.POSSESSED) && t != null && t.isAlive() && mob.distanceToSqr(t) > 16;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null || !(mob.level() instanceof ServerLevel level)) return;
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (--mob.castCooldown > 0 || !mob.getSensing().hasLineOfSight(t)) return;
            if (mob.foe == Foe.POSSESSED) {
                int[] colors = {0xFF3B6B, 0x8A3BFF, 0x5FE6FF, 0xE0B040};
                Bolts.fire(level, mob, t, 6.0F + mob.getRandom().nextInt(5), colors[mob.getRandom().nextInt(colors.length)], SoundEvents.EVOKER_CAST_SPELL);
                mob.castCooldown = 50 + mob.getRandom().nextInt(40);
            } else {
                float dmg = mob.foe == Foe.CHILD_1 ? 10.0F : mob.foe == Foe.CHILD_2 ? 7.0F : 5.0F;
                Bolts.fire(level, mob, t, dmg, 0xC02030, SoundEvents.EVOKER_CAST_SPELL);
                mob.castCooldown = mob.foe == Foe.CHILD_1 ? 35 : 55;
            }
        }
    }
}
