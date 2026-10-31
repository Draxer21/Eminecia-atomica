package com.draxer.atomic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * El sistema de magia: hechizos con tecla propia (R lanza, Z cambia), compresión de poder
 * (mantener V: el poder se condensa en niveles que multiplican el siguiente ataque), mejoras
 * con puntos de habilidad (K), y efectos sostenidos que gastan poder mientras están activos.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class Magic {
    private Magic() {}

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MagicState>> STATE = Mana.ATTACHMENTS.register("magic",
            () -> AttachmentType.builder(() -> MagicState.EMPTY).serialize(MagicState.CODEC).copyOnDeath().build());

    static void init() {}

    private static final int COMPRESS_COST = 40;
    private static final int XP_PER_POINT = 500;
    private static final DustParticleOptions VIOLET = new DustParticleOptions(0x8A3BFF, 1.3F);
    private static final DustParticleOptions DENSE = new DustParticleOptions(0x3A0A7A, 1.8F);
    private static final DustParticleOptions SMALL = new DustParticleOptions(0x8A3BFF, 0.7F);
    private static final DustParticleOptions SMALL_DENSE = new DustParticleOptions(0x3A0A7A, 0.8F);
    private static final DustParticleOptions CYAN = new DustParticleOptions(0x5FE6FF, 1.0F);

    /** Jugadores que mantienen la tecla de compresión, y ticks acumulados. */
    private static final Map<UUID, Integer> COMPRESSING = new HashMap<>();
    /** Ventana de contraataque abierta hasta este tick de juego. */
    private static final Map<UUID, Long> COUNTER = new HashMap<>();
    /** Ticks desde el último uso de la compresión (se disipa sola). */
    private static final Map<UUID, Integer> COMPRESSION_IDLE = new HashMap<>();

    public static MagicState state(Player player) {
        return player.getData(STATE);
    }

    private static void set(ServerPlayer player, MagicState state) {
        player.setData(STATE, state);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        MagicState s = state(player);
        List<Integer> levels = new ArrayList<>();
        for (MagicState.Upgrade u : MagicState.Upgrade.values()) levels.add(s.level(u));
        PacketDistributor.sendToPlayer(player, new MagicPayloads.Sync(s.spell(), s.compression(), s.toggles(), s.points(), s.xp(), levels));
    }

    // ---------------------------------------------------------------- multiplicadores

    public static int maxManaBonus(Player player) {
        return 50 * state(player).level(MagicState.Upgrade.RESERVOIR);
    }

    public static int regenBonus(Player player) {
        return 2 * state(player).level(MagicState.Upgrade.FLOW);
    }

    /** Costo real: con Eficiencia y el Despertar. */
    public static int cost(Player player, int base) {
        MagicState s = state(player);
        double c = base * (1.0 - 0.1 * s.level(MagicState.Upgrade.EFFICIENCY));
        if (s.on(MagicState.Spell.AWAKENING)) c *= 0.5;
        return (int) Math.ceil(c);
    }

    public static float power(Player player) {
        MagicState s = state(player);
        return (1.0F + 0.15F * s.level(MagicState.Upgrade.POWER)) * (s.on(MagicState.Spell.AWAKENING) ? 1.3F : 1.0F);
    }

    public static float reach(Player player) {
        return 1.0F + 0.15F * state(player).level(MagicState.Upgrade.REACH);
    }

    public static float chargeFactor(Player player) {
        return 1.0F - 0.2F * state(player).level(MagicState.Upgrade.ATOMIC);
    }

    public static int maxCompression(Player player) {
        return 3 + state(player).level(MagicState.Upgrade.COMPRESSION);
    }

    /** Usa la compresión acumulada: devuelve el multiplicador (1 = nada) y la vacía. */
    /** Niveles que se pueden comprimir por encima de la tolerancia del cuerpo. */
    public static final int OVERLOAD_LEVELS = 3;
    /** Probabilidad, por nivel de más, de que la técnica se disipe al soltarla. */
    public static final float OVERLOAD_FAIL_CHANCE = 0.25F;

    /** Niveles por encima de lo que el cuerpo tolera (0 = ninguno). */
    public static int overload(Player player) {
        return Math.max(0, state(player).compression() - maxCompression(player));
    }

    /** Mientras haya sobrecompresión: el cuerpo se daña cada segundo y se agrieta con luz. */
    private static void overloadTick(ServerLevel level, ServerPlayer player) {
        int over = overload(player);
        if (over <= 0) return;
        if (player.tickCount % 4 == 0) {
            DustParticleOptions crack = new DustParticleOptions(0xFF3B6B, 0.9F);
            for (int i = 0; i < 2 + over; i++) {
                level.sendParticles(crack, player.getX() + (player.getRandom().nextDouble() - 0.5) * 0.7,
                        player.getY() + 0.2 + player.getRandom().nextDouble() * 1.6,
                        player.getZ() + (player.getRandom().nextDouble() - 0.5) * 0.7, 1, 0, 0, 0, 0);
            }
        }
        if (player.tickCount % 20 == 0) {
            player.hurtServer(level, level.damageSources().magic(), 1.5F * over);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.PLAYERS, 0.8F, 0.5F);
        }
    }

    /**
     * Al soltar una técnica sobrecomprimida: puede que el cuerpo no la tolere y se disipe (como le
     * pasó a Aurora). Devuelve true si la técnica sale; false si se disipó (ya aplicó el castigo).
     */
    public static boolean tryRelease(ServerPlayer player) {
        int over = overload(player);
        if (over <= 0 || player.getRandom().nextFloat() >= OVERLOAD_FAIL_CHANCE * over) return true;
        dissipate((ServerLevel) player.level(), player, over);
        return false;
    }

    /** La técnica se deshace: estallido pequeño, daño al lanzador y se pierde toda la compresión. */
    public static void dissipate(ServerLevel level, LivingEntity caster, int over) {
        if (caster instanceof Player p) set(p, state(p).withCompression(0));
        Vec3 c = caster.position().add(0, 1.0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, true, true, c.x, c.y, c.z, 3, 0.6, 0.6, 0.6, 0);
        level.sendParticles(new DustParticleOptions(0xFF3B6B, 1.6F), true, true, c.x, c.y, c.z, 80, 1.5, 1.5, 1.5, 0.2);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, c.x, c.y, c.z, 120, 0.3, 0.3, 0.3, 1.2);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.4F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.4F);
        caster.hurtServer(level, level.damageSources().magic(), 4.0F + 4.0F * over);
        caster.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 200, 1));
        caster.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 100, 1));
        if (caster instanceof ServerPlayer sp) {
            sp.sendOverlayMessage(Component.translatable("message.draxatomic.dissipated").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        }
    }

    /** Niveles de compresión acumulados. */
    public static int compressionLevel(Player player) {
        return state(player).compression();
    }

    /** Multiplicador que daría la compresión actual, sin gastarla. */
    public static float peekCompression(Player player) {
        int c = state(player).compression();
        return c <= 0 ? 1.0F : 1.0F + 0.6F * c;
    }

    public static float consumeCompression(ServerPlayer player) {
        MagicState s = state(player);
        if (s.compression() <= 0) return 1.0F;
        float mult = 1.0F + 0.6F * s.compression();
        set(player, s.withCompression(0));
        ServerLevel level = (ServerLevel) player.level();
        Vec3 front = player.getEyePosition().add(player.getLookAngle().scale(2.5));
        level.sendParticles(ParticleTypes.SONIC_BOOM, front.x, front.y - 0.3, front.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 1.6F);
        return mult;
    }

    /** Llamado por Mana.spend: el poder gastado da experiencia mágica y, cada 500, un punto. */
    static void addXp(ServerPlayer player, int spent) {
        if (spent <= 0) return;
        MagicState s = state(player);
        int xp = s.xp() + spent;
        int points = s.points();
        while (xp >= XP_PER_POINT) {
            xp -= XP_PER_POINT;
            points++;
            player.sendSystemMessage(Component.translatable("message.draxatomic.skill_point").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        set(player, s.withPoints(points, xp));
    }

    public static void addPoints(ServerPlayer player, int points) {
        MagicState s = state(player);
        set(player, s.withPoints(s.points() + points, s.xp()));
    }

    // ---------------------------------------------------------------- acciones del cliente

    static void handle(ServerPlayer player, int action, int arg) {
        MagicState s = state(player);
        switch (action) {
            case MagicPayloads.NEXT, MagicPayloads.PREV -> {
                int next = s.spell() + (action == MagicPayloads.NEXT ? 1 : -1);
                MagicState.Spell spell = MagicState.Spell.of(next);
                set(player, s.withSpell(spell.ordinal()));
                player.sendOverlayMessage(spellLine(player, spell));
            }
            case MagicPayloads.SELECT -> set(player, s.withSpell(MagicState.Spell.of(arg).ordinal()));
            case MagicPayloads.CAST -> cast(player, MagicState.Spell.of(s.spell()));
            case MagicPayloads.COMPRESS_START -> COMPRESSING.put(player.getUUID(), 0);
            case MagicPayloads.COMPRESS_STOP -> COMPRESSING.remove(player.getUUID());
            case MagicPayloads.BUY -> buy(player, MagicState.Upgrade.values()[Math.floorMod(arg, MagicState.Upgrade.values().length)]);
            default -> {}
        }
    }

    private static Component spellLine(Player player, MagicState.Spell spell) {
        int cost = cost(player, spell.cost());
        return Component.translatable("message.draxatomic.spell_selected",
                Component.translatable("spell.draxatomic." + spell.id()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), cost);
    }

    private static void buy(ServerPlayer player, MagicState.Upgrade upgrade) {
        MagicState s = state(player);
        int level = s.level(upgrade);
        int price = level + 1;
        if (level >= upgrade.max() || s.points() < price) return;
        set(player, s.withSkill(upgrade, level + 1).withPoints(s.points() - price, s.xp()));
        Mana.sync(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    // ---------------------------------------------------------------- hechizos

    static void cast(ServerPlayer player, MagicState.Spell spell) {
        ServerLevel level = (ServerLevel) player.level();
        MagicState s = state(player);
        if (spell.toggle()) {
            boolean active = s.on(spell);
            if (!active) {
                if (spell == MagicState.Spell.AWAKENING && Mana.get(player) < Mana.max(player) * 0.95) {
                    player.sendOverlayMessage(Component.translatable("message.draxatomic.awakening_full").withStyle(ChatFormatting.RED));
                    return;
                }
                if (!Mana.spend(player, cost(player, spell.cost()))) {
                    noPower(player, cost(player, spell.cost()));
                    return;
                }
            }
            set(player, s.withToggle(spell, !active));
            player.sendOverlayMessage(Component.translatable(active ? "message.draxatomic.spell_off" : "message.draxatomic.spell_on",
                    Component.translatable("spell.draxatomic." + spell.id())).withStyle(active ? ChatFormatting.GRAY : ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    active ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
            if (active && spell == MagicState.Spell.SHADOW_STEALTH) player.removeEffect(MobEffects.INVISIBILITY);
            return;
        }
        int cost = cost(player, spell.cost());
        if (spell != MagicState.Spell.SLIME_FORGE && !Mana.spend(player, cost)) {
            noPower(player, cost);
            return;
        }
        switch (spell) {
            case SHADOW_STEP -> shadowStep(level, player);
            case PROJECTION -> projection(level, player);
            case COUNTER -> {
                COUNTER.put(player.getUUID(), level.getGameTime() + 14);
                level.sendParticles(SMALL, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.5, 0.25, 0.5, 0);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
            }
            case SLIME_PLATFORM -> platform(level, player);
            case MANA_SENSE -> sense(level, player);
            case SLIME_FORGE -> forge(level, player);
            case SUMMON_GARDEN -> summonGarden(level, player);
            default -> {}
        }
    }

    private static void noPower(ServerPlayer player, int cost) {
        player.sendOverlayMessage(Component.translatable("message.draxatomic.no_power", cost).withStyle(ChatFormatting.RED));
    }

    /** Paso sombra: aparece hasta 10 bloques adelante, dañando a lo que atraviesa. */
    private static void shadowStep(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition();
        double dist = 10 * reach(player);
        Vec3 to = from.add(look.scale(dist));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) to = hit.getLocation().subtract(look.scale(0.8));
        for (double d = 0; d < from.distanceTo(to); d += 0.5) {
            Vec3 p = from.add(look.scale(d));
            level.sendParticles(DENSE, p.x, p.y - 0.4, p.z, 2, 0.15, 0.3, 0.15, 0);
        }
        for (Entity e : level.getEntities(player, new AABB(from, to).inflate(1.2))) {
            if (e instanceof LivingEntity && !SlimeSkills.isFriend(e, player)) {
                e.hurtServer(level, level.damageSources().playerAttack(player), 6.0F * power(player));
            }
        }
        player.teleportTo(to.x, to.y - player.getEyeHeight() + 0.1, to.z);
        player.fallDistance = 0;
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.8F);
    }

    /** Proyección: un rayo de poder condensado que atraviesa todo en línea recta. */
    private static void projection(ServerLevel level, ServerPlayer player) {
        float mult = consumeCompression(player) * power(player);
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(0, -0.2, 0);
        double range = 48 * reach(player);
        Vec3 to = from.add(look.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) to = hit.getLocation();
        double length = from.distanceTo(to);
        double width = 0.6 + 0.25 * (mult - 1);
        DustParticleOptions core = new DustParticleOptions(0xE8D6FF, (float) Math.min(4.0, 1.2 + 0.4 * mult));
        for (double d = 1; d < length; d += 0.3) {
            Vec3 p = from.add(look.scale(d));
            level.sendParticles(core, true, false, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            level.sendParticles(VIOLET, true, false, p.x, p.y, p.z, 2, width * 0.4, width * 0.4, width * 0.4, 0);
        }
        AABB box = new AABB(from, to).inflate(width + 1);
        for (Entity e : level.getEntities(player, box)) {
            if (!(e instanceof LivingEntity) || SlimeSkills.isFriend(e, player)) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 dir = to.subtract(from);
            double t = Math.max(0, Math.min(1, c.subtract(from).dot(dir) / dir.lengthSqr()));
            if (c.distanceTo(from.add(dir.scale(t))) > width + e.getBbWidth()) continue;
            e.invulnerableTime = 0;
            e.hurtServer(level, level.damageSources().indirectMagic(player, player), 20.0F * mult);
            e.push(look.x * 0.8, 0.2, look.z * 0.8);
            e.hurtMarked = true;
        }
        // Con compresión alta, el final del rayo estalla.
        if (mult >= 2.0F) {
            AtomicBlast.detonateAt(level, player, to, Math.round(2 + mult), 15.0F * mult, false);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    // Bloques temporales de las plataformas: posición → tick en que desaparecen.
    private record Temp(ServerLevel level, BlockPos pos, long until) {}

    private static final List<Temp> TEMP_BLOCKS = new ArrayList<>();

    /** Plataforma de slime: un piso de 5×5 bajo los pies que dura 8 segundos. */
    private static void platform(ServerLevel level, ServerPlayer player) {
        BlockPos center = player.blockPosition().below();
        long until = level.getGameTime() + 160;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = center.offset(dx, 0, dz);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    TEMP_BLOCKS.add(new Temp(level, p.immutable(), until));
                }
            }
        }
        player.fallDistance = 0;
        level.playSound(null, center, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** Detección de maná: los enemigos cercanos brillan a través de las paredes. */
    private static void sense(ServerLevel level, ServerPlayer player) {
        double r = 48 * reach(player);
        int found = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                e -> e instanceof Enemy && e.isAlive())) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0, false, false));
            found++;
        }
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            level.sendParticles(CYAN, player.getX() + Math.cos(a) * 3, player.getY() + 0.2, player.getZ() + Math.sin(a) * 3, 1, 0, 0, 0, 0);
        }
        player.sendOverlayMessage(Component.translatable("message.draxatomic.sense", found).withStyle(ChatFormatting.AQUA));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.8F);
    }

    /** Forja de slime: repara el equipo con poder (1 de poder = 2 de durabilidad). */
    private static void forge(ServerLevel level, ServerPlayer player) {
        int repaired = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.getDamageValue() <= 0) continue;
            int need = (stack.getDamageValue() + 1) / 2;
            int pay = Math.min(need, Mana.get(player));
            if (pay <= 0 || !Mana.spend(player, pay)) break;
            stack.setDamageValue(Math.max(0, stack.getDamageValue() - pay * 2));
            repaired++;
        }
        if (repaired > 0) {
            level.sendParticles(SMALL, player.getX(), player.getY() + 0.5, player.getZ(), 30, 0.5, 0.25, 0.5, 0);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.5F);
        }
        player.sendOverlayMessage(Component.translatable("message.draxatomic.forge", repaired).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Convocatoria: tus integrantes de la Shadow Garden aparecen a tu lado. */
    private static void summonGarden(ServerLevel level, ServerPlayer player) {
        int count = 0;
        for (ShadowAllyEntity ally : level.getEntitiesOfClass(ShadowAllyEntity.class, player.getBoundingBox().inflate(256),
                a -> a.isOwnedBy(player))) {
            double a = count * Math.PI * 2 / 7;
            ally.teleportTo(player.getX() + Math.cos(a) * 2, player.getY(), player.getZ() + Math.sin(a) * 2);
            ally.arrive();
            count++;
        }
        player.sendOverlayMessage(Component.translatable("message.draxatomic.summon", count).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ---------------------------------------------------------------- cada tick

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TEMP_BLOCKS.isEmpty()) return;
        Iterator<Temp> it = TEMP_BLOCKS.iterator();
        while (it.hasNext()) {
            Temp t = it.next();
            if (t.level().getGameTime() < t.until()) continue;
            if (t.level().getBlockState(t.pos()).is(Blocks.AMETHYST_BLOCK)) {
                t.level().setBlock(t.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            it.remove();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) player.level();
        compressionTick(level, player);
        if (player.tickCount % 20 == 0) togglesTick(level, player);
        glide(level, player);
    }

    /** Mantener V: cada ~1,5 s condensa 40 de poder en un nivel de compresión. */
    private static void compressionTick(ServerLevel level, ServerPlayer player) {
        UUID id = player.getUUID();
        MagicState s = state(player);
        Integer t = COMPRESSING.get(id);
        if (t != null) {
            COMPRESSION_IDLE.put(id, 0);
            int interval = 30 - 6 * s.level(MagicState.Upgrade.COMPRESSION);
            int ticks = t + 1;
            COMPRESSING.put(id, ticks);
            // Aura que se cierra sobre la mano.
            // Anillo que se cierra alrededor de los pies (lejos de la cámara).
            double r = 2.2 - (ticks % interval) / (double) interval * 1.2;
            if (ticks % 2 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = ticks * 0.4 + i * Math.PI / 3;
                    level.sendParticles(s.compression() >= 2 ? SMALL_DENSE : SMALL, player.getX() + Math.cos(a) * r, player.getY() + 0.15,
                            player.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            if (ticks % interval == 0 && s.compression() < maxCompression(player) + OVERLOAD_LEVELS) {
                if (Mana.drain(player, cost(player, COMPRESS_COST))) {
                    int next = s.compression() + 1;
                    set(player, s.withCompression(next));
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                            SoundSource.PLAYERS, 0.8F, 0.6F + 0.25F * s.compression());
                    int over = next - maxCompression(player);
                    if (over > 0) {
                        // Sobrecompresión: el cuerpo empieza a romperse.
                        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK,
                                SoundSource.PLAYERS, 1.0F, 0.5F + 0.1F * over);
                        player.sendOverlayMessage(Component.translatable("message.draxatomic.overload", next, over,
                                Math.round(OVERLOAD_FAIL_CHANCE * over * 100)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    } else {
                        player.sendOverlayMessage(Component.translatable("message.draxatomic.compression", next,
                                Math.round((0.6F * next) * 100)).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
                    }
                }
            }
        }
        overloadTick(level, player);
        if (t != null) {
            // nada más mientras se comprime
        } else if (s.compression() > 0) {
            // Si no se usa, la compresión se disipa: un nivel cada 15 s.
            // Dentro del Santuario la magia comprimida es lo único que no se pierde: no se disipa.
            int idle = Cities.inSanctuary(level, player) ? 0 : COMPRESSION_IDLE.merge(id, 1, Integer::sum);
            if (player.tickCount % 8 == 0) {
                level.sendParticles(SMALL_DENSE, player.getX(), player.getY() + 0.1, player.getZ(), s.compression(), 0.4, 0.02, 0.4, 0);
            }
            if (idle >= 300) {
                COMPRESSION_IDLE.put(id, 0);
                set(player, s.withCompression(s.compression() - 1));
            }
        }
    }

    /** Hechizos sostenidos: cada segundo cobran su costo y aplican su efecto. */
    private static void togglesTick(ServerLevel level, ServerPlayer player) {
        MagicState s = state(player);
        if (s.toggles() == 0) return;
        if (s.on(MagicState.Spell.SHADOW_STEALTH)) {
            if (!Mana.drain(player, 3)) {
                off(player, MagicState.Spell.SHADOW_STEALTH);
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
                loseTrail(level, player, 0);
            }
        }
        if (state(player).on(MagicState.Spell.CONCEAL)) {
            if (player.tickCount % 40 == 0 && !Mana.drain(player, 1)) off(player, MagicState.Spell.CONCEAL);
            else loseTrail(level, player, 10);
        }
        if (state(player).on(MagicState.Spell.AWAKENING)) {
            int drain = Math.max(1, Mana.max(player) / 20);
            if (!Mana.drain(player, drain)) {
                off(player, MagicState.Spell.AWAKENING);
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 1, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false));
                level.sendParticles(SMALL_DENSE, player.getX(), player.getY() + 0.1, player.getZ(), 10, 0.6, 0.02, 0.6, 0.01);
            }
        }
    }

    private static void off(ServerPlayer player, MagicState.Spell spell) {
        set(player, state(player).withToggle(spell, false));
        player.sendOverlayMessage(Component.translatable("message.draxatomic.spell_off",
                Component.translatable("spell.draxatomic." + spell.id())).withStyle(ChatFormatting.GRAY));
    }

    /** Los monstruos más lejos que {@code distance} pierden tu rastro (salvo si los atacaste hace poco). */
    private static void loseTrail(ServerLevel level, ServerPlayer player, double distance) {
        if (player.tickCount - player.getLastHurtMobTimestamp() < 100) return;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32))) {
            if (mob.getTarget() == player && mob.distanceTo(player) > distance) mob.setTarget(null);
        }
    }

    /** Planeo: con el traje completo, agachado mientras caes, bajas suave y avanzas. */
    private static void glide(ServerLevel level, ServerPlayer player) {
        if (player.onGround() || !player.isShiftKeyDown() || player.getDeltaMovement().y > -0.05
                || player.getAbilities().flying || SlimeSuit.pieces(player) < 4) return;
        if (player.tickCount % 10 == 0 && !Mana.drain(player, 1)) return;
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false));
        player.fallDistance = 0;
        if (player.tickCount % 3 == 0) {
            level.sendParticles(SMALL, player.getX(), player.getY() - 0.2, player.getZ(), 2, 0.4, 0.05, 0.4, 0);
        }
    }

    // ---------------------------------------------------------------- daño recibido

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) player.level();
        // Contraataque: dentro de la ventana, el golpe cuerpo a cuerpo vuelve al atacante.
        Long until = COUNTER.get(player.getUUID());
        if (until != null && level.getGameTime() <= until && event.getSource().getDirectEntity() instanceof LivingEntity attacker
                && attacker != player) {
            COUNTER.remove(player.getUUID());
            event.setCanceled(true);
            float mult = consumeCompression(player) * power(player);
            attacker.hurtServer(level, level.damageSources().playerAttack(player), event.getAmount() * 2.0F * mult + 6.0F);
            Vec3 push = attacker.position().subtract(player.position()).normalize().scale(1.2);
            attacker.push(push.x, 0.3, push.z);
            attacker.hurtMarked = true;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, attacker.getX(), attacker.getY() + 1, attacker.getZ(), 3, 0.3, 0.3, 0.3, 0);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.7F);
            return;
        }
        // Armadura de slime: el poder absorbe el daño (4 de poder por punto de daño).
        if (state(player).on(MagicState.Spell.SLIME_ARMOR)) {
            float amount = event.getAmount();
            int need = (int) Math.ceil(amount * 4);
            int have = Mana.get(player);
            if (have >= need) {
                Mana.drain(player, need);
                event.setCanceled(true);
                level.sendParticles(SMALL, player.getX(), player.getY() + 0.5, player.getZ(), 12, 0.4, 0.25, 0.4, 0);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 0.9F);
            } else {
                Mana.drain(player, have);
                event.setAmount(amount - have / 4.0F);
                off(player, MagicState.Spell.SLIME_ARMOR);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
            }
        }
    }

    // ---------------------------------------------------------------- sincronización

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
