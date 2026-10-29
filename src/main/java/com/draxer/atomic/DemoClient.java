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
            }
            case 400 -> cmd(mc, "draxatomic test allrange");
            case 412 -> shot("allrange_marks");
            case 434 -> shot("allrange_blasts");
            case 470 -> {
                cmd(mc, "effect give @s minecraft:poison 30 0");
                cmd(mc, "draxatomic test recovery");
            }
            case 478 -> shot("recovery");
            case 500 -> {
                cmd(mc, "forceload add " + (ox + 180) + " " + (oz - 30) + " " + (ox + 240) + " " + (oz + 30));
                cmd(mc, "forceload add " + (ox + 380) + " " + (oz - 30) + " " + (ox + 470) + " " + (oz + 70));
                cmd(mc, "tp @s " + (ox + 206) + " 140 " + (oz - 22) + " 0 40");
            }
            case 600 -> cmd(mc, "place structure draxatomic:mitsugoshi_store " + (ox + 200) + " 100 " + oz);
            case 690 -> shot("store");
            case 700 -> cmd(mc, "tp @s " + (ox + 424) + " 175 " + (oz - 35) + " 0 50");
            case 800 -> cmd(mc, "place structure draxatomic:midgar_capital " + (ox + 400) + " 100 " + oz);
            case 900 -> shot("capital");
            case 920 -> cmd(mc, "tp @s " + (ox + 424) + " 125 " + (oz - 5) + " 0 25");
            case 980 -> shot("capital_street");
            case 1000 -> shot("end");
            default -> {}
        }
    }
}
