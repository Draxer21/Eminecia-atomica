package com.draxer.atomic;

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
