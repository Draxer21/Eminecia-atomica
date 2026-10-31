package com.draxer.atomic;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * Solo CI (-Ddraxatomic.demo=true): una vez dentro del mundo, recorre una serie de escenas
 * (traje, Shadow Garden, culto, los tres Atomic, la tienda y la capital). Antes de cada captura
 * escribe "DRAXDEMO nombre" en el log; el workflow saca la captura de pantalla al verlo.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID, value = Dist.CLIENT)
public final class DemoClient {
    private DemoClient() {}

    private static final boolean DEMO = Boolean.getBoolean("draxatomic.demo");
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks;
    private static int ox;
    private static int oy;
    private static int oz;

    private static void cmd(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }

    private static void shot(String name) {
        LOG.info("DRAXDEMO {}", name);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!DEMO) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        ticks++;
        if (ticks > 60) {
            // La cámara flota quieta en el aire (creativo) para que las escenas no se muevan.
            player.getAbilities().flying = true;
            player.setDeltaMovement(0, 0, 0);
        }
        switch (ticks) {
            case 60 -> {
                mc.gui.setScreen(null);
                mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
                cmd(mc, "gamemode creative");
                cmd(mc, "time set 13000");
                cmd(mc, "gamerule doDaylightCycle false");
                cmd(mc, "gamerule doMobSpawning false");
                // Sin mensajes de comandos en pantalla (nombre viejo y nuevo de la regla).
                cmd(mc, "gamerule sendCommandFeedback false");
                cmd(mc, "gamerule send_command_feedback false");
                cmd(mc, "gamerule spawn_mobs false");
                cmd(mc, "gamerule advance_time false");
                ox = player.getBlockX();
                oz = player.getBlockZ();
                oy = 150;
                // Plataforma en el cielo para que el terreno no estorbe.
                cmd(mc, "fill " + (ox - 20) + " " + (oy - 1) + " " + (oz - 20) + " " + (ox + 20) + " " + (oy - 1) + " " + (oz + 20) + " minecraft:grass_block");
                cmd(mc, "tp @s " + ox + ".5 " + oy + " " + oz + ".5 0 15");
            }
            case 100 -> {
                cmd(mc, "item replace entity @s armor.head with draxatomic:slime_hood");
                cmd(mc, "item replace entity @s armor.chest with draxatomic:slime_coat");
                cmd(mc, "item replace entity @s armor.legs with draxatomic:slime_pants");
                cmd(mc, "item replace entity @s armor.feet with draxatomic:slime_boots");
                cmd(mc, "item replace entity @s weapon.mainhand with draxatomic:slime_sword");
                cmd(mc, "time set 6000");
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 140 -> shot("suit");
            case 160 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                cmd(mc, "tp @s " + ox + ".5 " + oy + " " + oz + ".5 0 10");
                cmd(mc, "draxatomic test garden");
            }
            case 200 -> shot("garden");
            case 220 -> {
                cmd(mc, "kill @e[type=draxatomic:shadow_ally]");
                cmd(mc, "summon draxatomic:diablos_cultist " + (ox - 2) + " " + oy + " " + (oz + 6) + " {NoAI:1b,Kind:0,Rotation:[180f,0f]}");
                cmd(mc, "summon draxatomic:diablos_cultist " + (ox + 2) + " " + oy + " " + (oz + 6) + " {NoAI:1b,Kind:1,Rotation:[180f,0f]}");
                cmd(mc, "summon draxatomic:round_knight " + ox + " " + oy + " " + (oz + 8) + " {NoAI:1b,Rotation:[180f,0f]}");
            }
            case 260 -> shot("cult");
            case 280 -> {
                cmd(mc, "kill @e[type=!minecraft:player]");
                cmd(mc, "tp @s " + ox + ".5 " + (oy + 10) + " " + (oz - 40) + ".5 0 8");
                cmd(mc, "draxatomic mana");
            }
            case 300 -> cmd(mc, "draxatomic test barrier");
            case 316 -> shot("barrier");
            case 320 -> cmd(mc, "draxatomic test atomicfar");
            case 330 -> shot("atomic_flash");
            case 338 -> shot("atomic_sphere");
            case 346 -> shot("atomic_full");
            case 366 -> shot("atomic_after");
            case 380 -> {
                cmd(mc, "tp @s " + ox + ".5 " + (oy + 2) + " " + (oz - 60) + ".5 0 20");
                for (int i = 0; i < 6; i++) {
                    cmd(mc, "summon minecraft:zombie " + (ox - 10 + i * 4) + " " + oy + " " + (oz - 50) + " {NoAI:1b}");
                }
                cmd(mc, "fill " + (ox - 15) + " " + (oy - 1) + " " + (oz - 55) + " " + (ox + 15) + " " + (oy - 1) + " " + (oz - 45) + " minecraft:stone");
                // Un estanque dentro del alcance, para la ola.
                cmd(mc, "fill " + (ox + 18) + " " + (oy - 3) + " " + (oz - 82) + " " + (ox + 50) + " " + (oy - 1) + " " + (oz - 50) + " minecraft:stone");
                cmd(mc, "fill " + (ox + 19) + " " + (oy - 2) + " " + (oz - 81) + " " + (ox + 49) + " " + (oy - 1) + " " + (oz - 51) + " minecraft:water");
            }
            case 400 -> cmd(mc, "draxatomic test allrange");
            case 412 -> shot("allrange_marks");
            case 426 -> shot("allrange_blasts");
            case 440 -> shot("allrange_final");
            case 456 -> cmd(mc, "tp @s " + ox + ".5 " + (oy + 70) + " " + (oz - 230) + ".5 0 28");
            case 490 -> shot("allrange_wave");
            case 500 -> {
                cmd(mc, "tp @s " + ox + ".5 " + (oy + 2) + " " + (oz - 60) + ".5 0 20");
                cmd(mc, "effect give @s minecraft:poison 30 0");
                cmd(mc, "draxatomic test recovery");
            }
            case 508 -> shot("recovery");
            case 520 -> {
                cmd(mc, "forceload add " + (ox + 180) + " " + (oz - 30) + " " + (ox + 240) + " " + (oz + 30));
                cmd(mc, "tp @s " + (ox + 206) + " 140 " + (oz - 22) + " 0 40");
            }
            case 600 -> cmd(mc, "place structure draxatomic:mitsugoshi_store " + (ox + 200) + " 100 " + oz);
            case 690 -> shot("store");
            // Capital de Midgar
            case 700 -> cmd(mc, "draxatomic city view midgar overview");
            case 900 -> cmd(mc, "draxatomic city build");
            case 1000 -> cmd(mc, "draxatomic city view midgar overview");
            case 1060 -> shot("midgar_overview");
            case 1080 -> cmd(mc, "draxatomic city view midgar castle");
            case 1180 -> {
                cmd(mc, "draxatomic city build");
                cmd(mc, "time set 6000");
            }
            case 1240 -> shot("midgar_castle");
            case 1260 -> cmd(mc, "draxatomic city view midgar street");
            case 1360 -> cmd(mc, "draxatomic city build");
            case 1420 -> shot("midgar_street");
            // Capital de Oriana
            case 1440 -> cmd(mc, "draxatomic city view oriana overview");
            case 1640 -> cmd(mc, "draxatomic city build");
            case 1740 -> cmd(mc, "draxatomic city view oriana overview");
            case 1800 -> shot("oriana_overview");
            case 1820 -> cmd(mc, "draxatomic city view oriana castle");
            case 1920 -> cmd(mc, "draxatomic city build");
            case 1980 -> shot("oriana_palace");
            case 2000 -> cmd(mc, "draxatomic city view oriana street");
            case 2100 -> cmd(mc, "draxatomic city build");
            case 2160 -> shot("oriana_street");
            // Magia: compresión + proyección y el árbol de habilidades.
            case 2200 -> {
                cmd(mc, "tp @s " + ox + ".5 " + (oy + 4) + " " + (oz - 40) + ".5 0 5");
                cmd(mc, "draxatomic mana");
                for (int i = 0; i < 6; i++) {
                    cmd(mc, "summon minecraft:zombie " + (ox - 4 + i * 2) + " " + oy + " " + (oz - 18) + " {NoAI:1b}");
                }
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new MagicPayloads.Action(MagicPayloads.SELECT, 1));
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new MagicPayloads.Action(MagicPayloads.COMPRESS_START, 0));
            }
            case 2300 -> shot("compressing");
            case 2310 -> {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new MagicPayloads.Action(MagicPayloads.COMPRESS_STOP, 0));
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new MagicPayloads.Action(MagicPayloads.CAST, 0));
            }
            case 2313 -> shot("projection");
            case 2330 -> mc.gui.setScreen(new SkillScreen());
            case 2350 -> shot("skills");
            case 2360 -> mc.gui.setScreen(null);
            // El Santuario (absorbe el poder mágico).
            case 2380 -> cmd(mc, "draxatomic city view sanctuary overview");
            case 2540 -> shot("sanctuary");
            case 2560 -> cmd(mc, "draxatomic city view sanctuary castle");
            case 2660 -> shot("sanctuary_inside");
            case 2680 -> shot("end");
            default -> {}
        }
    }
}
