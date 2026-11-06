package com.draxer.atomic;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Servidor → cliente: en qué zona está el jugador (bits), para el cielo, la niebla y la música.
 */
public record ZonePayload(int zones) implements CustomPacketPayload {
    public static final int MIDGAR = 1;
    public static final int ORIANA = 2;
    public static final int SANCTUARY = 4;
    public static final int LAWLESS = 8;
    /** De noche dentro de la Ciudad sin Ley. */
    public static final int LAWLESS_NIGHT = 16;
    /** Elizabeth con la Luna Roja cerca. */
    public static final int RED_MOON = 32;
    /** Un jefe del mod cerca y peleando. */
    public static final int BOSS = 64;

    public static final CustomPacketPayload.Type<ZonePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "zone"));
    public static final StreamCodec<ByteBuf, ZonePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ZonePayload::zones, ZonePayload::new);

    /** Último valor recibido (solo cliente). */
    public static volatile int latest;

    public static boolean has(int bit) {
        return (latest & bit) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
