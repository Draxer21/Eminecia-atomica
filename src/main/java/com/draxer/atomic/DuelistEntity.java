package com.draxer.atomic;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Rival del torneo de espadas: solo pelea contra quien lo desafió. Cada ronda es más fuerte;
 * en la final tiene un estilo propio (aparece la estela de su espada).
 */
public class DuelistEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SKIN =
            SynchedEntityData.defineId(DuelistEntity.class, EntityDataSerializers.INT);
    public static final int SKINS = 3;

    private java.util.UUID challenger;
    private int round;

    public DuelistEntity(EntityType<? extends DuelistEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
    }

    public int skin() {
        return Math.floorMod(entityData.get(SKIN), SKINS);
    }

    /** Prepara al rival para la ronda {@code round} (1..4) contra ese jugador. */
    void setup(Player player, int round) {
        this.challenger = player.getUUID();
        this.round = round;
        entityData.set(SKIN, round == Tournament.ROUNDS ? 2 : getRandom().nextInt(2));
        double scale = 1.0 + 0.45 * (round - 1);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0 * scale);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5.0 * scale);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.32 + 0.02 * round);
        setHealth(getMaxHealth());
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(round >= 3 ? Items.DIAMOND_SWORD : Items.IRON_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        setTarget(player);
    }

    public int round() {
        return round;
    }

    public java.util.UUID challenger() {
        return challenger;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        // Solo pelea con su retador; si se pierde de vista, lo vuelve a buscar.
        if (challenger != null && (getTarget() == null || !getTarget().getUUID().equals(challenger))
                && level.getEntity(challenger) instanceof Player p && p.isAlive()) {
            setTarget(p);
        }
        if (round == Tournament.ROUNDS && swinging && tickCount % 2 == 0) {
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0xE0E8FF, 1.0F),
                    getX(), getY() + 1.2, getZ(), 3, 0.5, 0.3, 0.5, 0);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Nadie más puede meterse en el duelo.
        if (challenger != null && source.getEntity() != null && !source.getEntity().getUUID().equals(challenger)) return false;
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) Tournament.roundWon(level, this);
        super.die(source);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Round", round);
        output.putInt("Skin", skin());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        round = input.getIntOr("Round", 1);
        entityData.set(SKIN, input.getIntOr("Skin", 0));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
