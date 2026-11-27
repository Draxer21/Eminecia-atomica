package com.draxer.atomic;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Estado de red compacto del haz; no tiene colisión, gravedad ni persistencia. */
public final class ProjectionBeamEntity extends Entity {
    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(ProjectionBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(ProjectionBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(ProjectionBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(ProjectionBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> CHANNEL = SynchedEntityData.defineId(ProjectionBeamEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IMPACT = SynchedEntityData.defineId(ProjectionBeamEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> IMPACT_TICK = SynchedEntityData.defineId(ProjectionBeamEntity.class, EntityDataSerializers.INT);

    private java.util.UUID owner;
    private long expiresAt;

    public ProjectionBeamEntity(EntityType<? extends ProjectionBeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DX, 0.0F);
        builder.define(DY, 0.0F);
        builder.define(DZ, 0.0F);
        builder.define(RADIUS, 0.12F);
        builder.define(CHANNEL, false);
        builder.define(IMPACT, false);
        builder.define(IMPACT_TICK, 0);
    }

    public void configure(java.util.UUID owner, Vec3 from, Vec3 to, float radius, boolean channel, boolean impact) {
        this.owner = owner;
        this.setPos(from);
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(DX, (float) (to.x - from.x));
        this.entityData.set(DY, (float) (to.y - from.y));
        this.entityData.set(DZ, (float) (to.z - from.z));
        this.entityData.set(RADIUS, radius);
        this.entityData.set(CHANNEL, channel);
        this.entityData.set(IMPACT, impact);
        if (impact) this.entityData.set(IMPACT_TICK, this.tickCount);
        this.expiresAt = this.level().getGameTime() + 5L;
    }

    public Vec3 beamDelta() {
        return new Vec3(this.entityData.get(DX), this.entityData.get(DY), this.entityData.get(DZ));
    }

    public float beamRadius() {
        return this.entityData.get(RADIUS);
    }

    public boolean isChannel() {
        return this.entityData.get(CHANNEL);
    }

    public boolean hasImpact() {
        return this.entityData.get(IMPACT);
    }

    public int impactTick() {
        return this.entityData.get(IMPACT_TICK);
    }

    java.util.UUID owner() {
        return this.owner;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel && serverLevel.getGameTime() >= this.expiresAt) {
            Magic.forgetProjectionBeam(this);
            this.discard();
        }
    }
}
