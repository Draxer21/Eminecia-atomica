package com.draxer.atomic;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.lwjgl.glfw.GLFW;

/** Teclas de magia (R lanzar, Z cambiar hechizo, V comprimir, K habilidades) y su parte del HUD. */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID, value = Dist.CLIENT)
public final class MagicClient {
    private MagicClient() {}

    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "magic"));
    public static final KeyMapping CAST = new KeyMapping("key.draxatomic.cast", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping NEXT = new KeyMapping("key.draxatomic.next_spell", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping COMPRESS = new KeyMapping("key.draxatomic.compress", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping SKILLS = new KeyMapping("key.draxatomic.skills", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    private static boolean compressing;

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(CAST);
        event.register(NEXT);
        event.register(COMPRESS);
        event.register(SKILLS);
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(MagicPayloads.Sync.TYPE, (payload, context) -> MagicPayloads.Sync.latest = payload);
    }

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "magic_hud"), MagicClient::renderHud);
    }

    private static void send(int action, int arg) {
        ClientPacketDistributor.sendToServer(new MagicPayloads.Action(action, arg));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        while (CAST.consumeClick()) send(MagicPayloads.CAST, 0);
        while (NEXT.consumeClick()) send(player.isShiftKeyDown() ? MagicPayloads.PREV : MagicPayloads.NEXT, 0);
        while (SKILLS.consumeClick()) mc.gui.setScreen(new SkillScreen());
        boolean down = COMPRESS.isDown();
        if (down != compressing) {
            compressing = down;
            send(down ? MagicPayloads.COMPRESS_START : MagicPayloads.COMPRESS_STOP, 0);
        }
    }

    /** Hechizo elegido, nivel de compresión y hechizos sostenidos, bajo la barra de poder. */
    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return;
        MagicPayloads.Sync s = MagicPayloads.Sync.latest;
        int cx = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 12;
        MagicState.Spell spell = MagicState.Spell.of(s.spell());
        Component name = Component.translatable("spell.draxatomic." + spell.id());
        // Solo se muestra si el jugador ya usa la magia (tiene la espada, poder gastado o algo activo).
        boolean show = player.getMainHandItem().getItem() instanceof SlimeSwordItem || s.compression() > 0 || s.toggles() != 0 || s.xp() > 0 || s.points() > 0;
        if (!show) return;
        int x = cx + 91 + 8;
        graphics.text(mc.font, Component.literal("✦ ").append(name), x, y - 10, s.on(spell) ? 0xFFB8F5C4 : 0xFFD9B8FF);
        // Compresión: rombos llenos por nivel.
        int max = 3 + s.level(MagicState.Upgrade.COMPRESSION);
        for (int i = 0; i < max; i++) {
            int px = x + i * 7;
            int color = i < s.compression() ? 0xFF8A3BFF : 0x60FFFFFF;
            graphics.fill(px + 2, y, px + 4, y + 6, color);
            graphics.fill(px + 1, y + 1, px + 5, y + 5, color);
        }
        // Sostenidos activos.
        int tx = cx - 91 - 8;
        int line = 0;
        for (MagicState.Spell t : MagicState.Spell.values()) {
            if (!t.toggle() || !s.on(t)) continue;
            Component label = Component.translatable("spell.draxatomic." + t.id());
            graphics.text(mc.font, label, tx - mc.font.width(label), y - 22 - line * 10, 0xFFB8F5C4);
            line++;
        }
        if (s.points() > 0) {
            Component p = Component.translatable("gui.draxatomic.points_hint", s.points());
            graphics.text(mc.font, p, x + 7 * max + 4, y - 1, 0xFFFFD54A);
        }
    }
}
