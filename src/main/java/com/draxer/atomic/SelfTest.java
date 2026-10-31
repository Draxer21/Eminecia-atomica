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
            java.util.Set<String> names = new java.util.TreeSet<>();
            names.add("mitsugoshi_store");
            for (CityLayout.Kind kind : CityLayout.Kind.values()) {
                CityLayout layout = new CityLayout(kind, 1L);
                for (int i = 0; i < kind.cells; i++) for (int j = 0; j < kind.cells; j++) {
                    String t = layout.template(i, j);
                    if (t != null) names.add(t);
                }
            }
            int missing = 0;
            for (String name : names) {
                boolean found = level.getStructureManager().get(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, name)).isPresent();
                if (!found) missing++;
                LOG.info("DRAXTEST template {} {}", name, found ? "OK" : "MISSING");
            }
            LOG.info("DRAXTEST templates total={} missing={}", names.size(), missing);
        });
        step("block_ids", () -> {
            // Cada bloque usado en las plantillas debe existir en esta versión (si no, se coloca aire).
            try (var in = SelfTest.class.getResourceAsStream("/assets/draxatomic/selftest/blocks.txt")) {
                if (in == null) return;
                int bad = 0;
                for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                    String id = line.trim();
                    if (id.isEmpty()) continue;
                    if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id))) {
                        LOG.info("DRAXTEST unknown_block {}", id);
                        bad++;
                    }
                }
                LOG.info("DRAXTEST block_ids unknown={}", bad);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
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
                    ShadowGarden.ALLY.value(), Cult.CULTIST.value(), Cult.KNIGHT.value(),
                    Vampires.SERVANT.value(), Vampires.CRIMSON.value(), Vampires.ELIZABETH.value(), Vampires.AURORA.value()}) {
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
            commands.performPrefixedCommand(source, "locate structure draxatomic:mitsugoshi_store");
        });
        step("cities", () -> {
            for (CityLayout.Kind kind : CityLayout.Kind.values()) {
                var site = Cities.nearest(level, kind, BlockPos.ZERO, 4);
                LOG.info("DRAXTEST city {} {}", kind, site.map(s -> s.centerX() + "," + s.centerZ() + " cells=" + Cities.total(s)).orElse("NONE"));
            }
            // Coloca algunas cuadras grandes en una zona cargada a la fuerza.
            commands.performPrefixedCommand(source, "forceload add 5000 5000 5420 5070");
            int k = 0;
            for (String t : new String[] {"city/midgar/castle", "city/oriana/palace", "city/midgar/houses_a", "city/oriana/houses_a", "city/sanctuary/temple", "city/lawless/crimson_tower"}) {
                Cities.placeCell(level, t, 5000 + k * 70, 90, 5000, 64, 1L);
                LOG.info("DRAXTEST placed {}", t);
                k++;
            }
            LOG.info("DRAXTEST npcs={} guards={}",
                    level.getEntitiesOfClass(StoryNpcEntity.class, new net.minecraft.world.phys.AABB(4990, 60, 4990, 5300, 160, 5080)).size(),
                    level.getEntitiesOfClass(KingdomGuardEntity.class, new net.minecraft.world.phys.AABB(4990, 60, 4990, 5300, 160, 5080)).size());
        });
        step("atomics", () -> {
            var fake = FakePlayerFactory.getMinecraft(level);
            fake.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
            AtomicBlast.detonate(level, fake);
            LOG.info("DRAXTEST allRange targets={} radius={}", AtomicVariants.allRange(level, fake), AtomicVariants.allRangeRadius(fake, 1));
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
