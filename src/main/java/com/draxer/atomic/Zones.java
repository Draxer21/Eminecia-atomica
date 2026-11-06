package com.draxer.atomic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Calcula cada segundo en qué zona está cada jugador y se la avisa al cliente si cambió. */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class Zones {
    private Zones() {}

    private static final Map<UUID, Integer> LAST = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 7) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        int zones = 0;
        if (Cities.siteAt(level, CityLayout.Kind.MIDGAR, player.getX(), player.getZ()).isPresent()) zones |= ZonePayload.MIDGAR;
        if (Cities.siteAt(level, CityLayout.Kind.ORIANA, player.getX(), player.getZ()).isPresent()) zones |= ZonePayload.ORIANA;
        if (Cities.inSanctuary(level, player)) zones |= ZonePayload.SANCTUARY;
        if (Cities.lawlessAt(level, player.getX(), player.getZ()).isPresent()) {
            zones |= ZonePayload.LAWLESS;
            if (!level.isBrightOutside()) zones |= ZonePayload.LAWLESS_NIGHT;
        }
        for (Mob boss : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64),
                m -> m.isAlive() && (m instanceof BloodLordEntity || m instanceof AuroraEntity || m instanceof RoundsKnightEntity))) {
            if (boss.getTarget() != null || boss.distanceToSqr(player) < 24 * 24) zones |= ZonePayload.BOSS;
            if (boss instanceof BloodLordEntity lord && lord.redMoon()) zones |= ZonePayload.RED_MOON;
        }
        Integer last = LAST.put(player.getUUID(), zones);
        if (last == null || last != zones) PacketDistributor.sendToPlayer(player, new ZonePayload(zones));
    }
}
