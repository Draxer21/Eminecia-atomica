package com.draxer.atomic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Lado del cliente: la esfera de luz violeta que se expande, el destello en pantalla y el
 * temblor de cámara. Todo se dibuja con partículas locales para no saturar la red.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID, value = Dist.CLIENT)
public final class AtomicClient {
    private AtomicClient() {}

    private static final int VIOLET = 0x8A3BFF;
    private static final int PALE = 0xD9B8FF;
    private static final int CYAN = 0x5FE6FF;
    private static final int LINGER_TICKS = 50;

    private record Blast(double x, double y, double z, int radius, int color, boolean big, int[] age) {}

    private static final List<Blast> BLASTS = new ArrayList<>();

    /** Domos de barrera activos: life[0] = ticks restantes, life[1] = edad. */
    private record Barrier(int owner, double x, double y, double z, int radius, int color, int[] life) {}

    private static final List<Barrier> BARRIERS = new ArrayList<>();
    /** Intensidad del destello 0..1 y del temblor 0..1; decaen cada tick. */
    private static float flash;
    private static float shake;

    @SubscribeEvent
    public static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(AtomicPayload.TYPE, (payload, context) -> start(payload));
        event.register(ManaPayload.TYPE, (payload, context) -> ManaPayload.latest = payload);
        event.register(BarrierPayload.TYPE, (payload, context) -> {
            BARRIERS.removeIf(b -> b.owner() == payload.owner());
            if (payload.ticks() > 0) BARRIERS.add(new Barrier(payload.owner(), payload.x(), payload.y(), payload.z(),
                    payload.radius(), payload.color(), new int[] {payload.ticks(), 0}));
        });
    }

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "atomic_flash"), AtomicClient::renderFlash);
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "mana_bar"), AtomicClient::renderMana);
    }

    private static void start(AtomicPayload payload) {
        BLASTS.add(new Blast(payload.x(), payload.y(), payload.z(), payload.radius(), payload.color(), payload.big(), new int[] {0}));
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!payload.big()) {
            double d = Math.sqrt(player.distanceToSqr(payload.x(), payload.y(), payload.z()));
            if (payload.color() == AtomicPayload.VIOLET) shake = Math.max(shake, (float) Mth.clamp(0.5 - d / 80.0, 0.0, 0.4));
            return;
        }
        double distance = Math.sqrt(player.distanceToSqr(payload.x(), payload.y(), payload.z()));
        float near = (float) Mth.clamp(1.0 - distance / (payload.radius() * 8.0), 0.15, 1.0);
        flash = Math.max(flash, near);
        shake = Math.max(shake, near);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            BLASTS.clear();
            BARRIERS.clear();
            flash = 0;
            shake = 0;
            return;
        }
        if (mc.isPaused()) return;
        doubleJump(mc);
        tickBarriers(level);
        flash = Math.max(0, flash - 0.06F);
        shake = Math.max(0, shake - 0.025F);
        Iterator<Blast> it = BLASTS.iterator();
        while (it.hasNext()) {
            Blast blast = it.next();
            int age = ++blast.age()[0];
            if (age > AtomicBlast.EXPAND_TICKS + LINGER_TICKS) {
                it.remove();
                continue;
            }
            draw(level, blast, age);
        }
    }

    /** Una capa de la esfera por tick; al terminar de crecer queda un resplandor que se apaga. */
    private static void draw(ClientLevel level, Blast blast, int age) {
        RandomSource random = level.getRandom();
        double t = Math.min(1.0, age / (double) AtomicBlast.EXPAND_TICKS);
        double r = blast.radius() * (1.0 - Math.pow(1.0 - t, 3));
        boolean growing = age <= AtomicBlast.EXPAND_TICKS;
        boolean recovery = blast.color() != AtomicPayload.VIOLET;
        DustParticleOptions shell = new DustParticleOptions(recovery ? 0xE8FFF2 : (growing ? PALE : VIOLET), blast.big() ? 4.0F : (recovery ? 1.5F : 2.5F));
        DustParticleOptions glow = new DustParticleOptions(recovery ? blast.color() : VIOLET, blast.big() ? 3.0F : (recovery ? 1.2F : 2.0F));
        DustParticleOptions spark = new DustParticleOptions(recovery ? 0xFFF6B0 : CYAN, 2.0F);

        // La superficie de la esfera: más puntos cuanto más grande, con tope para no ahogar al cliente.
        int cap = blast.big() ? 900 : 160;
        int points = growing ? Mth.clamp((int) (r * r * 1.2), 40, cap) : Math.max(0, 260 - (age - AtomicBlast.EXPAND_TICKS) * 5);
        for (int i = 0; i < points; i++) {
            double u = random.nextDouble() * 2 - 1;
            double a = random.nextDouble() * Math.PI * 2;
            double s = Math.sqrt(1 - u * u);
            double dx = s * Math.cos(a);
            double dy = u;
            double dz = s * Math.sin(a);
            double rr = growing ? r : r * (0.9 + random.nextDouble() * 0.1);
            DustParticleOptions kind = i % 7 == 0 ? spark : (i % 3 == 0 ? glow : shell);
            level.addAlwaysVisibleParticle(kind, blast.x() + dx * rr, blast.y() + dy * rr, blast.z() + dz * rr,
                    0, 0, 0);
            if (growing && i % 5 == 0) {
                level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, blast.x() + dx * rr, blast.y() + dy * rr,
                        blast.z() + dz * rr, dx * 0.3, dy * 0.3, dz * 0.3);
            }
        }
        // El núcleo: un punto de luz blanca en el centro mientras la onda crece.
        if (growing) {
            for (int i = 0; i < (blast.big() ? 30 : 8); i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, blast.x(), blast.y(), blast.z(),
                        random.nextGaussian() * 0.6, random.nextGaussian() * 0.6, random.nextGaussian() * 0.6);
            }
        }
        // Columna de luz que sube al cielo.
        if (blast.big() && age < AtomicBlast.EXPAND_TICKS + 30) {
            for (int i = 0; i < 12; i++) {
                double h = random.nextDouble() * blast.radius() * 3;
                level.addAlwaysVisibleParticle(i % 2 == 0 ? glow : spark,
                        blast.x() + random.nextGaussian() * 0.8, blast.y() + h, blast.z() + random.nextGaussian() * 0.8,
                        0, 0.4, 0);
            }
        }
    }

    /** Destello blanco que se vuelve violeta mientras se desvanece. */
    private static void renderFlash(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (flash <= 0.01F) return;
        int alpha = Math.round(Math.min(1.0F, flash) * 235);
        int rgb = flash > 0.6F ? 0xFFFFFF : 0xC9A2FF;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | rgb);
    }

    /** Temblor de cámara que se calma poco a poco. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0.01F) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RandomSource random = mc.level.getRandom();
        float k = shake * shake * 4.0F;
        event.setYaw(event.getYaw() + (random.nextFloat() - 0.5F) * k);
        event.setPitch(event.getPitch() + (random.nextFloat() - 0.5F) * k);
        event.setRoll(event.getRoll() + (random.nextFloat() - 0.5F) * k * 1.5F);
    }

    /** Barra de poder mágico sobre la barra de experiencia: visible con la espada en mano o si no está llena. */
    private static void renderMana(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return;
        ManaPayload mana = ManaPayload.latest;
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        boolean holding = held.getItem() instanceof SlimeSwordItem;
        if (!holding && mana.mana() >= mana.max()) return;
        // A la izquierda de la barra rápida, lejos de la vida y del texto de acción.
        int width = 110;
        int x = graphics.guiWidth() / 2 - 91 - 8 - width;
        int y = graphics.guiHeight() - 12;
        float fraction = mana.max() <= 0 ? 0 : Math.min(1.0F, mana.mana() / (float) mana.max());
        int filled = Math.round((width - 2) * fraction);
        graphics.fill(x, y, x + width, y + 6, 0xC0100820);
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + 5, fraction >= 1.0F ? 0xFFB88CFF : 0xFF7A33E6);
        if (filled > 2) graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0x80FFFFFF);
        String numbers = mana.mana() + "/" + mana.max();
        graphics.text(mc.font, numbers, x + width - mc.font.width(numbers), y - 10, 0xFFE6D6FF);
        if (holding) {
            // El modo de la espada, a la derecha de la barra rápida (sobre el hechizo).
            net.minecraft.network.chat.Component mode = SwordMode.of(held).title();
            graphics.text(mc.font, mode, graphics.guiWidth() / 2 + 91 + 8, y - 20, 0xFFE6D6FF);
        }
    }

    // ---------------------------------------------------------------- doble salto (botas de slime)

    private static boolean jumpWasDown;
    private static boolean airJumpUsed;

    private static void doubleJump(Minecraft mc) {
        LocalPlayer player = mc.player;
        boolean jump = mc.options.keyJump.isDown();
        if (player.onGround() || player.isInWater() || player.onClimbable()) {
            airJumpUsed = false;
        } else if (jump && !jumpWasDown && !airJumpUsed && !player.getAbilities().flying && !player.isPassenger()
                && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(SlimeSuit.BOOTS.value())
                && (player.isCreative() || ManaPayload.latest.mana() >= SlimeSuit.DOUBLE_JUMP_COST)) {
            airJumpUsed = true;
            var motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x * 1.15, 0.62, motion.z * 1.15);
            player.fallDistance = 0;
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(DoubleJumpPayload.INSTANCE);
        }
        jumpWasDown = jump;
    }

    // ---------------------------------------------------------------- domo de barrera

    /** Esfera translúcida de puntos de luz que se cierra al aparecer y se desvanece al final. */
    private static void tickBarriers(ClientLevel level) {
        if (BARRIERS.isEmpty()) return;
        RandomSource random = level.getRandom();
        Iterator<Barrier> it = BARRIERS.iterator();
        while (it.hasNext()) {
            Barrier b = it.next();
            int left = --b.life()[0];
            int age = ++b.life()[1];
            if (left <= 0) {
                it.remove();
                continue;
            }
            // Aparece desde un radio mayor y se asienta en el suyo.
            double r = b.radius() * (1.0 + 0.25 * Math.max(0, 1.0 - age / 8.0));
            DustParticleOptions line = new DustParticleOptions(b.color(), 4.0F);
            DustParticleOptions glow = new DustParticleOptions(0xE6D2FF, 2.5F);
            double fade = left < 20 ? left / 20.0 : 1.0;
            double spin = age * 0.015;
            // Rejilla del domo (meridianos y paralelos): se lee como una cúpula incluso de lejos o desde dentro.
            if (age % 2 == 0) {
                int meridians = 18;
                double step = 1.6 / r; // ~1.6 bloques entre partículas
                for (int m = 0; m < meridians; m++) {
                    double lon = m * Math.PI * 2 / meridians + spin;
                    double cos = Math.cos(lon);
                    double sin = Math.sin(lon);
                    for (double lat = -0.12; lat < Math.PI / 2; lat += step) {
                        if (random.nextDouble() > fade) continue;
                        double h = Math.cos(lat);
                        level.addAlwaysVisibleParticle(line, b.x() + cos * h * r, b.y() + Math.sin(lat) * r, b.z() + sin * h * r, 0, 0, 0);
                    }
                }
                for (double lat : new double[] {0.0, 0.35, 0.7, 1.05, 1.35}) {
                    double h = Math.cos(lat) * r;
                    int n = Mth.clamp((int) (h * Math.PI * 2 / 1.6), 12, 400);
                    for (int i = 0; i < n; i++) {
                        if (random.nextDouble() > fade) continue;
                        double a = i * Math.PI * 2 / n - spin;
                        level.addAlwaysVisibleParticle(lat == 0.0 ? glow : line,
                                b.x() + Math.cos(a) * h, b.y() + Math.sin(lat) * r, b.z() + Math.sin(a) * h, 0, 0, 0);
                    }
                }
            }
            // Brillo disperso sobre la superficie (luz propia, se ve de noche y bajo tierra).
            int sparks = (int) (Mth.clamp(r * r * 0.03, 20, 220) * fade);
            for (int i = 0; i < sparks; i++) {
                double u = random.nextDouble() * 1.1 - 0.1;
                double a = random.nextDouble() * Math.PI * 2;
                double s = Math.sqrt(1 - u * u);
                level.addAlwaysVisibleParticle(ParticleTypes.END_ROD,
                        b.x() + s * Math.cos(a) * r, b.y() + u * r, b.z() + s * Math.sin(a) * r, 0, 0, 0);
            }
        }
    }
}
