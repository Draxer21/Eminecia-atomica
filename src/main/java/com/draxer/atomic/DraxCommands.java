package com.draxer.atomic;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /draxatomic test atomic|allrange|recovery  lanza el ataque al instante (sin carga ni costo).
 * /draxatomic test atomicfar                 el Atomic 45 bloques delante, para verlo desde fuera.
 * /draxatomic test garden                    invoca a las siete integrantes frente a ti.
 * /draxatomic mana                           llena tu poder mágico.
 * Solo en creativo (o desde la consola / bloques de comandos).
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class DraxCommands {
    private DraxCommands() {}

    private static ServerPlayer creative(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player && player.isCreative()) return player;
        source.sendFailure(Component.translatable("message.draxatomic.command.creative"));
        return null;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("draxatomic")
                .then(Commands.literal("quest")
                        .then(Commands.literal("list").executes(c -> {
                            if (c.getSource().getEntity() instanceof ServerPlayer p) Quests.list(p);
                            return 1;
                        }))
                        .then(Commands.literal("accept").then(Commands.argument("id", StringArgumentType.word()).executes(c ->
                                c.getSource().getEntity() instanceof ServerPlayer p ? Quests.accept(p, StringArgumentType.getString(c, "id")) : 0)))
                        .then(Commands.literal("turnin").then(Commands.argument("id", StringArgumentType.word()).executes(c ->
                                c.getSource().getEntity() instanceof ServerPlayer p ? Quests.turnIn(p, StringArgumentType.getString(c, "id")) : 0))))
                .then(Commands.literal("city")
                        .then(Commands.literal("locate")
                                .then(Commands.literal("midgar").executes(c -> locate(c.getSource(), CityLayout.Kind.MIDGAR, false)))
                                .then(Commands.literal("oriana").executes(c -> locate(c.getSource(), CityLayout.Kind.ORIANA, false))))
                        .then(Commands.literal("build").executes(c -> buildHere(c.getSource())))
                        .then(Commands.literal("view")
                                .then(Commands.literal("midgar")
                                        .then(Commands.literal("overview").executes(c -> view(c.getSource(), CityLayout.Kind.MIDGAR, "overview")))
                                        .then(Commands.literal("castle").executes(c -> view(c.getSource(), CityLayout.Kind.MIDGAR, "castle")))
                                        .then(Commands.literal("street").executes(c -> view(c.getSource(), CityLayout.Kind.MIDGAR, "street"))))
                                .then(Commands.literal("oriana")
                                        .then(Commands.literal("overview").executes(c -> view(c.getSource(), CityLayout.Kind.ORIANA, "overview")))
                                        .then(Commands.literal("castle").executes(c -> view(c.getSource(), CityLayout.Kind.ORIANA, "castle")))
                                        .then(Commands.literal("street").executes(c -> view(c.getSource(), CityLayout.Kind.ORIANA, "street")))))
                        .then(Commands.literal("tp")
                                .then(Commands.literal("midgar").executes(c -> locate(c.getSource(), CityLayout.Kind.MIDGAR, true)))
                                .then(Commands.literal("oriana").executes(c -> locate(c.getSource(), CityLayout.Kind.ORIANA, true)))))
                .then(Commands.literal("mana").executes(context -> {
                    ServerPlayer player = creative(context.getSource());
                    if (player == null) return 0;
                    Mana.refill(player);
                    return 1;
                }))
                .then(Commands.literal("test")
                        .then(Commands.literal("atomic").executes(context -> {
                            ServerPlayer player = creative(context.getSource());
                            if (player == null) return 0;
                            AtomicBlast.detonate((ServerLevel) player.level(), player);
                            return 1;
                        }))
                        .then(Commands.literal("atomicfar").executes(context -> {
                            // El Atomic completo 45 bloques delante, para verlo desde fuera.
                            ServerPlayer player = creative(context.getSource());
                            if (player == null) return 0;
                            Vec3 at = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(45));
                            AtomicBlast.detonateAt((ServerLevel) player.level(), player, at.add(0, 1, 0),
                                    AtomicConfig.radius(), AtomicConfig.damage(), AtomicConfig.destroyBlocks(), true);
                            return 1;
                        }))
                        .then(Commands.literal("allrange").executes(context -> {
                            ServerPlayer player = creative(context.getSource());
                            if (player == null) return 0;
                            return AtomicVariants.allRange((ServerLevel) player.level(), player);
                        }))
                        .then(Commands.literal("recovery").executes(context -> {
                            ServerPlayer player = creative(context.getSource());
                            if (player == null) return 0;
                            AtomicVariants.recovery((ServerLevel) player.level(), player);
                            return 1;
                        }))
                        .then(Commands.literal("garden").executes(context -> {
                            ServerPlayer player = creative(context.getSource());
                            if (player == null) return 0;
                            return summonGarden((ServerLevel) player.level(), player);
                        }))));
    }

    /** Busca la capital más cercana; con tp (solo creativo) te lleva sobre su centro. */
    private static int locate(CommandSourceStack source, CityLayout.Kind kind, boolean teleport) {
        ServerLevel level = source.getServer().overworld();
        var found = Cities.nearest(level, kind, net.minecraft.core.BlockPos.containing(source.getPosition()), 6);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("message.draxatomic.city_none", Component.translatable("city.draxatomic." + kind.style)));
            return 0;
        }
        Cities.Site site = found.get();
        int distance = (int) Math.sqrt(Math.pow(site.centerX() - source.getPosition().x, 2) + Math.pow(site.centerZ() - source.getPosition().z, 2));
        source.sendSuccess(() -> Component.translatable("message.draxatomic.city_found", Component.translatable("city.draxatomic." + kind.style),
                site.centerX(), site.centerZ(), distance, Cities.progress(level, site), Cities.total(site)), false);
        if (teleport) {
            ServerPlayer player = creative(source);
            if (player == null) return 0;
            int y = level.getChunkSource().getGenerator().getBaseHeight(site.centerX(), site.centerZ(),
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState()) + 60;
            player.teleportTo(site.centerX() + 0.5, y, site.centerZ() + 0.5);
        }
        return distance;
    }

    /** Construye ya las cuadras cargadas de las capitales cercanas (creativo). */
    private static int buildHere(CommandSourceStack source) {
        ServerPlayer player = creative(source);
        if (player == null) return 0;
        ServerLevel level = source.getServer().overworld();
        int built = 0;
        for (CityLayout.Kind kind : CityLayout.Kind.values()) {
            var site = Cities.nearest(level, kind, player.blockPosition(), 2);
            if (site.isPresent() && Math.abs(site.get().centerX() - player.getX()) < kind.size()
                    && Math.abs(site.get().centerZ() - player.getZ()) < kind.size()) {
                built += Cities.buildLoaded(level, site.get());
            }
        }
        int total = built;
        source.sendSuccess(() -> Component.literal("Cuadras construidas: " + total), false);
        return built;
    }

    /** Puntos de vista para mirar una capital (creativo). */
    private static int view(CommandSourceStack source, CityLayout.Kind kind, String where) {
        ServerPlayer player = creative(source);
        if (player == null) return 0;
        ServerLevel level = source.getServer().overworld();
        var found = Cities.nearest(level, kind, player.blockPosition(), 6);
        if (found.isEmpty()) return 0;
        Cities.Site site = found.get();
        int baseY = Cities.baseY(level, site);
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        switch (where) {
            case "castle" -> {
                // Frente al trono: Midgar castillo (7,3), Oriana palacio (3,1).
                int ci = kind == CityLayout.Kind.MIDGAR ? 3 : 0;
                int cj = 0;
                int ty = kind == CityLayout.Kind.MIDGAR ? 20 : 12;
                x = site.minX() + ci * CityLayout.CELL + 33.5;
                z = site.minZ() + cj * CityLayout.CELL + ty + 9.5;
                y = baseY + 2;
                yaw = 180.0F;
                pitch = 5.0F;
            }
            case "street" -> {
                // Sobre la calle norte-sur que sale de la puerta sur, mirando hacia el centro.
                x = site.minX() + (kind.cells / 2) * CityLayout.CELL + 2.0;
                z = site.minZ() + (kind.cells - 2) * CityLayout.CELL + 10.5;
                y = baseY + 3;
                yaw = 180.0F;
                pitch = 4.0F;
            }
            default -> {
                // Fuera de la muralla sur, en alto, mirando la ciudad.
                x = site.centerX() + 0.5;
                z = site.minZ() + kind.size() + 24.5;
                y = baseY + 55;
                yaw = 180.0F;
                pitch = 22.0F;
            }
        }
        player.teleportTo(level, x, y, z, java.util.Set.of(), yaw, pitch, true);
        return 1;
    }

    /** Las siete integrantes en fila frente al jugador, mirándolo. */
    static int summonGarden(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        Vec3 center = player.position().add(look.scale(5));
        int count = 0;
        ShadowGarden.Member[] members = ShadowGarden.Member.values();
        for (int i = 0; i < members.length; i++) {
            Vec3 at = center.add(side.scale((i - 3) * 1.4));
            ShadowAllyEntity ally = ShadowGarden.ALLY.value().create(level, EntitySpawnReason.COMMAND);
            if (ally == null) continue;
            ally.snapTo(at.x, player.getY(), at.z, player.getYRot() + 180.0F, 0.0F);
            ally.setup(members[i], player);
            if (level.addFreshEntity(ally)) {
                ally.arrive();
                count++;
            }
        }
        return count;
    }
}
