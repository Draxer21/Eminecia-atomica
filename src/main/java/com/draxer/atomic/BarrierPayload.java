package com.draxer.atomic;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Servidor → cliente: el domo de barrera que aparece al cargar un Atomic y marca hasta dónde
 * llegará la explosión. {@code ticks} = 0 lo quita (carga cancelada).
 */
public record BarrierPayload(int owner, double x, double y, double z, int radius, int ticks, int color) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BarrierPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "barrier"));
    public static final StreamCodec<ByteBuf, BarrierPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BarrierPayload decode(ByteBuf buf) {
            return new BarrierPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.INT.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, BarrierPayload p) {
            ByteBufCodecs.VAR_INT.encode(buf, p.owner());
            ByteBufCodecs.DOUBLE.encode(buf, p.x());
            ByteBufCodecs.DOUBLE.encode(buf, p.y());
            ByteBufCodecs.DOUBLE.encode(buf, p.z());
            ByteBufCodecs.VAR_INT.encode(buf, p.radius());
            ByteBufCodecs.VAR_INT.encode(buf, p.ticks());
            ByteBufCodecs.INT.encode(buf, p.color());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Envía el domo a todos los que puedan verlo. */
    public static void send(ServerLevel level, ServerPlayer caster, int radius, int ticks, int color) {
        BarrierPayload payload = new BarrierPayload(caster.getId(), caster.getX(), caster.getY() + 1.0, caster.getZ(), radius, ticks, color);
        double reach = radius + 160;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(caster) < reach * reach) PacketDistributor.sendToPlayer(p, payload);
        }
    }
}
