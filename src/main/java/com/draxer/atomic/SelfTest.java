package com.draxer.atomic;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Solo CI (-Ddraxatomic.selfTest=true): al arrancar el servidor revisa que existan las
 * plantillas de estructura y los intercambios, crea cada entidad, coloca las estructuras y lanza
 * cada Atomic con un jugador falso. Todo queda en el log con el prefijo DRAXTEST.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class SelfTest {
    private SelfTest() {}

    private static final Logger LOG = LogUtils.getLogger();

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("draxatomic.selfTest")) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        step("templates", () -> {
            for (String name : new String[] {"mitsugoshi_store", "midgar_capital"}) {
                boolean found = level.getStructureManager().get(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, name)).isPresent();
                LOG.info("DRAXTEST template {} {}", name, found ? "OK" : "MISSING");
            }
        });
        step("trades", () -> {
            var sets = server.registryAccess().lookupOrThrow(Registries.TRADE_SET);
            for (int i = 1; i <= 5; i++) {
                var key = ResourceKey.create(Registries.TRADE_SET,
                        Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "mitsugoshi_clerk/level_" + i));
                LOG.info("DRAXTEST trade_set level_{} {}", i, sets.get(key).isPresent() ? "OK" : "MISSING");
            }
        });
        BlockPos base = new BlockPos(2000, 120, 2000);
        step("entities", () -> {
            for (var type : new net.minecraft.world.entity.EntityType<?>[] {
                    ShadowGarden.ALLY.value(), Cult.CULTIST.value(), Cult.KNIGHT.value()}) {
                var entity = type.create(level, EntitySpawnReason.COMMAND);
                if (entity == null) {
                    LOG.info("DRAXTEST entity {} NULL", type);
                    continue;
                }
                entity.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
                if (entity instanceof Mob mob) mob.setNoAi(true);
                LOG.info("DRAXTEST entity {} {}", type, level.addFreshEntity(entity) ? "OK" : "REJECTED");
            }
        });
        var commands = server.getCommands();
        var source = server.createCommandSourceStack();
        step("place", () -> {
            commands.performPrefixedCommand(source, "forceload add 3000 3000 3060 3060");
            commands.performPrefixedCommand(source, "place structure draxatomic:mitsugoshi_store 3000 100 3000");
            commands.performPrefixedCommand(source, "place structure draxatomic:midgar_capital 3030 100 3030");
            commands.performPrefixedCommand(source, "locate structure draxatomic:mitsugoshi_store");
        });
        step("atomics", () -> {
            var fake = FakePlayerFactory.getMinecraft(level);
            fake.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
            AtomicBlast.detonate(level, fake);
            LOG.info("DRAXTEST allRange targets={}", AtomicVariants.allRange(level, fake));
            AtomicVariants.recovery(level, fake);
            SlimeSkills.whip(level, fake);
            SlimeSkills.dash(level, fake);
        });
        LOG.info("DRAXTEST done");
    }

    private static void step(String name, Runnable body) {
        try {
            body.run();
            LOG.info("DRAXTEST step {} OK", name);
        } catch (Throwable t) {
            LOG.error("DRAXTEST step {} FAILED", name, t);
        }
    }
}
