package com.draxer.atomic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Los monstruos nuevos: registro, materiales que sueltan y dónde aparecen.
 * <ul>
 *   <li>De noche en la superficie: jaurías de sabuesos de sangre, enjambres de murciélagos de
 *   sangre, bestias de sombra y Poseídos fallidos.</li>
 *   <li>En cuevas: gólems de limo y bestias de sombra.</li>
 *   <li>En la Ciudad sin Ley: bandidos y luchadores del foso.</li>
 *   <li>Tras un Atomic grande: Espectros de maná atraídos por la magia liberada.</li>
 *   <li>Las patrullas del Culto pueden traer a un Niño del Culto; la Base del Culto guarda
 *   quimeras, Poseídos, Niños y a la Quimera Alfa.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class Monsters {
    private Monsters() {}

    private static ResourceKey<EntityType<?>> key(String id) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, id));
    }

    private static DeferredHolder<EntityType<?>, EntityType<FoeEntity>> foe(FoeEntity.Foe foe) {
        return ShadowGarden.ENTITY_TYPES.register(foe.id, () ->
                EntityType.Builder.<FoeEntity>of((type, level) -> new FoeEntity(type, level, foe), MobCategory.MONSTER)
                        .sized(0.6F, 1.95F)
                        .clientTrackingRange(10)
                        .build(key(foe.id)));
    }

    private static DeferredHolder<EntityType<?>, EntityType<BeastEntity>> beast(BeastEntity.Beast beast, float w, float h, boolean fireImmune) {
        return ShadowGarden.ENTITY_TYPES.register(beast.id, () -> {
            EntityType.Builder<BeastEntity> b = EntityType.Builder.<BeastEntity>of((type, level) -> new BeastEntity(type, level, beast), MobCategory.MONSTER)
                    .sized(w, h)
                    .clientTrackingRange(beast == BeastEntity.Beast.CHIMERA_ALPHA ? 12 : 10);
            if (fireImmune) b = b.fireImmune();
            return b.build(key(beast.id));
        });
    }

    public static final DeferredHolder<EntityType<?>, EntityType<FoeEntity>> CHILD_3 = foe(FoeEntity.Foe.CHILD_3);
    public static final DeferredHolder<EntityType<?>, EntityType<FoeEntity>> CHILD_2 = foe(FoeEntity.Foe.CHILD_2);
    public static final DeferredHolder<EntityType<?>, EntityType<FoeEntity>> CHILD_1 = foe(FoeEntity.Foe.CHILD_1);
    public static final DeferredHolder<EntityType<?>, EntityType<FoeEntity>> POSSESSED = foe(FoeEntity.Foe.POSSESSED);
    public static final DeferredHolder<EntityType<?>, EntityType<FoeEntity>> BANDIT = foe(FoeEntity.Foe.BANDIT);
    public static final DeferredHolder<EntityType<?>, EntityType<FoeEntity>> FIGHTER = foe(FoeEntity.Foe.FIGHTER);

    public static final DeferredHolder<EntityType<?>, EntityType<BeastEntity>> BLOOD_HOUND = beast(BeastEntity.Beast.BLOOD_HOUND, 0.8F, 0.95F, false);
    public static final DeferredHolder<EntityType<?>, EntityType<BeastEntity>> CHIMERA = beast(BeastEntity.Beast.CHIMERA, 1.1F, 1.3F, false);
    public static final DeferredHolder<EntityType<?>, EntityType<BeastEntity>> CHIMERA_ALPHA = beast(BeastEntity.Beast.CHIMERA_ALPHA, 1.1F, 1.3F, true);
    public static final DeferredHolder<EntityType<?>, EntityType<BeastEntity>> BLOOD_BAT = beast(BeastEntity.Beast.BLOOD_BAT, 0.5F, 0.5F, false);
    public static final DeferredHolder<EntityType<?>, EntityType<BeastEntity>> SLIME_GOLEM = beast(BeastEntity.Beast.SLIME_GOLEM, 0.9F, 1.6F, false);
    public static final DeferredHolder<EntityType<?>, EntityType<BeastEntity>> SHADOW_BEAST = beast(BeastEntity.Beast.SHADOW_BEAST, 0.8F, 2.0F, false);
    public static final DeferredHolder<EntityType<?>, EntityType<BeastEntity>> MANA_WRAITH = beast(BeastEntity.Beast.MANA_WRAITH, 0.6F, 1.6F, true);

    // ---------------------------------------------------------------- materiales

    /** Insignia de los Niños del Culto: +120 de experiencia mágica. */
    public static final DeferredHolder<Item, Item> INSIGNIA = DraxAtomic.ITEMS.registerItem("cult_insignia",
            properties -> new UseItem(properties.stacksTo(64).rarity(Rarity.UNCOMMON), "cult_insignia"));
    /** Colmillo de sabueso: material (cáliz carmesí). */
    public static final DeferredHolder<Item, Item> FANG = DraxAtomic.ITEMS.registerItem("blood_fang",
            properties -> new Item(properties.stacksTo(64)));
    /** Esquirla de quimera: cristal mágico que crece en el lomo de las quimeras. */
    public static final DeferredHolder<Item, Item> CHIMERA_SHARD = DraxAtomic.ITEMS.registerItem("chimera_shard",
            properties -> new Item(properties.stacksTo(64).rarity(Rarity.UNCOMMON)));
    /** Esencia de sombra: invisibilidad y visión nocturna un rato. */
    public static final DeferredHolder<Item, Item> SHADOW_ESSENCE = DraxAtomic.ITEMS.registerItem("shadow_essence",
            properties -> new UseItem(properties.stacksTo(16).rarity(Rarity.UNCOMMON), "shadow_essence"));
    /** Residuo de maná: devuelve 60 de poder. */
    public static final DeferredHolder<Item, Item> MANA_RESIDUE = DraxAtomic.ITEMS.registerItem("mana_residue",
            properties -> new UseItem(properties.stacksTo(16).rarity(Rarity.UNCOMMON), "mana_residue"));
    /** Núcleo de quimera: +60 de poder máximo y 2 puntos de habilidad. */
    public static final DeferredHolder<Item, Item> CHIMERA_CORE = DraxAtomic.ITEMS.registerItem("chimera_core",
            properties -> new UseItem(properties.stacksTo(4).rarity(Rarity.EPIC).fireResistant(), "chimera_core"));
    /** Sello del Vacío: de noche invoca al Caballero del Vacío. */
    public static final DeferredHolder<Item, Item> VOID_SIGIL = DraxAtomic.ITEMS.registerItem("void_sigil",
            properties -> new VoidSigilItem(properties.stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    static final int CORE_BONUS = 60;

    static void init(IEventBus modEventBus) {
        modEventBus.addListener(Monsters::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(CHILD_3.value(), FoeEntity.createAttributes(FoeEntity.Foe.CHILD_3).build());
        event.put(CHILD_2.value(), FoeEntity.createAttributes(FoeEntity.Foe.CHILD_2).build());
        event.put(CHILD_1.value(), FoeEntity.createAttributes(FoeEntity.Foe.CHILD_1).build());
        event.put(POSSESSED.value(), FoeEntity.createAttributes(FoeEntity.Foe.POSSESSED).build());
        event.put(BANDIT.value(), FoeEntity.createAttributes(FoeEntity.Foe.BANDIT).build());
        event.put(FIGHTER.value(), FoeEntity.createAttributes(FoeEntity.Foe.FIGHTER).build());
        event.put(BLOOD_HOUND.value(), BeastEntity.createAttributes(BeastEntity.Beast.BLOOD_HOUND).build());
        event.put(CHIMERA.value(), BeastEntity.createAttributes(BeastEntity.Beast.CHIMERA).build());
        event.put(CHIMERA_ALPHA.value(), BeastEntity.createAttributes(BeastEntity.Beast.CHIMERA_ALPHA).build());
        event.put(BLOOD_BAT.value(), BeastEntity.createAttributes(BeastEntity.Beast.BLOOD_BAT).build());
        event.put(SLIME_GOLEM.value(), BeastEntity.createAttributes(BeastEntity.Beast.SLIME_GOLEM).build());
        event.put(SHADOW_BEAST.value(), BeastEntity.createAttributes(BeastEntity.Beast.SHADOW_BEAST).build());
        event.put(MANA_WRAITH.value(), BeastEntity.createAttributes(BeastEntity.Beast.MANA_WRAITH).build());
    }

    static List<EntityType<?>> all() {
        return List.of(CHILD_3.value(), CHILD_2.value(), CHILD_1.value(), POSSESSED.value(), BANDIT.value(), FIGHTER.value(),
                BLOOD_HOUND.value(), CHIMERA.value(), CHIMERA_ALPHA.value(), BLOOD_BAT.value(), SLIME_GOLEM.value(),
                SHADOW_BEAST.value(), MANA_WRAITH.value());
    }

    /** Un Niño del Culto al azar: el tercero es común, el primero raro. */
    static EntityType<FoeEntity> randomChild(RandomSource random) {
        float r = random.nextFloat();
        return r < 0.6F ? CHILD_3.value() : r < 0.9F ? CHILD_2.value() : CHILD_1.value();
    }

    /** Crea y coloca un monstruo; devuelve null si no se pudo. */
    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, double x, double y, double z, EntitySpawnReason reason) {
        Mob mob = type.create(level, reason);
        if (mob == null) return null;
        mob.snapTo(x, y, z, level.getRandom().nextFloat() * 360, 0);
        if (mob instanceof FoeEntity f) f.equip();
        if (mob instanceof BeastEntity b) {
            if (b.beast() == BeastEntity.Beast.SLIME_GOLEM) b.setSize(3);
            if (b.beast() == BeastEntity.Beast.CHIMERA_ALPHA) b.setupAlpha();
        }
        level.addFreshEntity(mob);
        return mob;
    }

    // ---------------------------------------------------------------- aparición

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        wraithTick(level);
        if (!AtomicConfig.monsters() || level.getGameTime() % 400 != 7) return;
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || random.nextFloat() > AtomicConfig.monsterChance()) continue;
            int nearby = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64),
                    m -> m instanceof BeastEntity || m instanceof FoeEntity).size();
            if (nearby >= 10) continue;
            if (Cities.lawlessAt(level, player.getX(), player.getZ()).isPresent()) {
                // La Ciudad sin Ley: bandidos y luchadores del foso (a cualquier hora).
                EntityType<FoeEntity> type = random.nextFloat() < 0.65F ? BANDIT.value() : FIGHTER.value();
                surfaceGroup(level, player, random, type, type == BANDIT.value() ? 1 + random.nextInt(2) : 1, 14, 24);
                continue;
            }
            boolean underground = player.getY() < 40 && !level.canSeeSky(player.blockPosition());
            if (underground) {
                caveSpawn(level, player, random, random.nextFloat() < 0.65F ? SLIME_GOLEM.value() : SHADOW_BEAST.value());
                continue;
            }
            if (level.isBrightOutside()) continue;
            float r = random.nextFloat();
            if (r < 0.32F) surfaceGroup(level, player, random, BLOOD_HOUND.value(), 3 + random.nextInt(3), 24, 36);
            else if (r < 0.55F) surfaceGroup(level, player, random, BLOOD_BAT.value(), 5 + random.nextInt(4), 18, 28);
            else if (r < 0.8F) surfaceGroup(level, player, random, SHADOW_BEAST.value(), 1, 20, 30);
            else surfaceGroup(level, player, random, POSSESSED.value(), 1 + random.nextInt(2), 20, 32);
        }
    }

    private static void surfaceGroup(ServerLevel level, ServerPlayer player, RandomSource random, EntityType<? extends Mob> type,
                                     int count, int min, int max) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = min + random.nextDouble() * (max - min);
        int cx = (int) (player.getX() + Math.cos(angle) * distance);
        int cz = (int) (player.getZ() + Math.sin(angle) * distance);
        boolean flyer = type == BLOOD_BAT.value();
        for (int i = 0; i < count; i++) {
            int x = cx + random.nextInt(5) - 2;
            int z = cz + random.nextInt(5) - 2;
            if (!Cities.generated(level, x >> 4, z >> 4)) continue;
            BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.isLoaded(ground) || !level.getFluidState(ground).isEmpty()) continue;
            if (!flyer && !level.getBlockState(ground.below()).isSolid()) continue;
            spawn(level, type, x + 0.5, ground.getY() + (flyer ? 4 + random.nextInt(3) : 0), z + 0.5, EntitySpawnReason.NATURAL);
        }
    }

    /** Busca un hueco oscuro en la cueva, cerca del jugador. */
    private static void caveSpawn(ServerLevel level, ServerPlayer player, RandomSource random, EntityType<? extends Mob> type) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 12 + random.nextDouble() * 12;
            BlockPos pos = BlockPos.containing(player.getX() + Math.cos(a) * d, player.getY() + random.nextInt(11) - 5, player.getZ() + Math.sin(a) * d);
            if (!Cities.generated(level, pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.below()).isSolid()) continue;
            if (level.getMaxLocalRawBrightness(pos) > 7 || level.canSeeSky(pos)) continue;
            spawn(level, type, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, EntitySpawnReason.NATURAL);
            return;
        }
    }

    // ---------------------------------------------------------------- espectros de maná

    private record Wraiths(ServerLevel level, Vec3 at, int[] delay) {}

    private static final List<Wraiths> WRAITHS = new ArrayList<>();

    /** Un Atomic grande deja magia suelta... y algo viene a devorarla. */
    static void afterBigMagic(ServerLevel level, Vec3 at) {
        if (!AtomicConfig.monsters() || level.getRandom().nextFloat() > AtomicConfig.wraithChance()) return;
        WRAITHS.add(new Wraiths(level, at, new int[] {80}));
    }

    private static void wraithTick(ServerLevel ignored) {
        WRAITHS.removeIf(w -> {
            if (--w.delay()[0] > 0) return false;
            ServerLevel level = w.level();
            int count = 1 + level.getRandom().nextInt(2);
            for (int i = 0; i < count; i++) {
                double a = level.getRandom().nextDouble() * Math.PI * 2;
                Vec3 p = w.at().add(Math.cos(a) * 6, 3 + i, Math.sin(a) * 6);
                Mob m = spawn(level, MANA_WRAITH.value(), p.x, p.y, p.z, EntitySpawnReason.EVENT);
                if (m != null) level.sendParticles(ModParticles.ENERGY_PETAL.value(), p.x, p.y + 1, p.z, 40, 0.4, 0.8, 0.4, 0.06);
            }
            level.playSound(null, w.at().x, w.at().y, w.at().z, SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 2.0F, 0.5F);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(w.at()) < 96 * 96) {
                    player.sendSystemMessage(Component.translatable("message.draxatomic.wraiths_come").withStyle(ChatFormatting.DARK_PURPLE));
                }
            }
            return true;
        });
    }

    // ---------------------------------------------------------------- ítems

    /** Ítems que se consumen con clic derecho. */
    public static final class UseItem extends Item {
        private final String id;

        public UseItem(Properties properties, String id) {
            super(properties);
            this.id = id;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            switch (id) {
                case "cult_insignia" -> {
                    Magic.addXp(sp, 120);
                    sp.sendOverlayMessage(Component.translatable("message.draxatomic.insignia_used").withStyle(ChatFormatting.LIGHT_PURPLE));
                    level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 0.7F);
                }
                case "shadow_essence" -> {
                    sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30 * 20, 0));
                    sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 60 * 20, 0));
                    if (level instanceof ServerLevel sl) {
                        sl.sendParticles(ModParticles.SHADOW_SMOKE.value(), sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
                    }
                    level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.5F);
                }
                case "mana_residue" -> {
                    Mana.restore(sp, 60);
                    Mana.sync(sp);
                    level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
                }
                case "chimera_core" -> {
                    Mana.addBonus(sp, CORE_BONUS);
                    Magic.addPoints(sp, 2);
                    sp.sendOverlayMessage(Component.translatable("message.draxatomic.core_used", CORE_BONUS, Mana.max(sp))
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                    level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 0.8F);
                }
                default -> {
                    return InteractionResult.PASS;
                }
            }
            player.getItemInHand(hand).consume(1, player);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable("tooltip.draxatomic." + id).withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    /** Sello del Vacío: de noche, en el suelo, invoca al Caballero del Vacío. */
    public static final class VoidSigilItem extends Item {
        public VoidSigilItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Player player = context.getPlayer();
            if (player == null) return InteractionResult.PASS;
            if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
            if (level.isBrightOutside()) {
                player.sendOverlayMessage(Component.translatable("message.draxatomic.seal_day").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            BlockPos at = context.getClickedPos().above();
            RoundsKnightEntity knight = Cult.KNIGHT.value().create(level, EntitySpawnReason.TRIGGERED);
            if (knight == null) return InteractionResult.FAIL;
            knight.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0);
            knight.setRank(RoundsKnightEntity.Rank.VOID);
            level.addFreshEntity(knight);
            knight.announce();
            context.getItemInHand().consume(1, player);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable("tooltip.draxatomic.void_sigil").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
