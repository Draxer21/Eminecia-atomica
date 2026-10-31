package com.draxer.atomic;

import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Los vampiros del arco de la Ciudad sin Ley: siervos que se curan al morder y arden al sol,
 * Crimson y Elizabeth, la Reina de la Sangre. La sangre de Crimson le enseña al jugador la
 * magia de sangre (lanza de sangre, forma de murciélagos y drenaje).
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class Vampires {
    private Vampires() {}

    private static ResourceKey<EntityType<?>> key(String id) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, id));
    }

    public static final DeferredHolder<EntityType<?>, EntityType<VampireEntity>> SERVANT = ShadowGarden.ENTITY_TYPES.register("vampire_servant", () ->
            EntityType.Builder.of(VampireEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(key("vampire_servant")));

    public static final DeferredHolder<EntityType<?>, EntityType<BloodLordEntity>> CRIMSON = ShadowGarden.ENTITY_TYPES.register("crimson", () ->
            EntityType.Builder.<BloodLordEntity>of((type, level) -> new BloodLordEntity(type, level, BloodLordEntity.Lord.CRIMSON), MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build(key("crimson")));

    public static final DeferredHolder<EntityType<?>, EntityType<BloodLordEntity>> ELIZABETH = ShadowGarden.ENTITY_TYPES.register("elizabeth", () ->
            EntityType.Builder.<BloodLordEntity>of((type, level) -> new BloodLordEntity(type, level, BloodLordEntity.Lord.ELIZABETH), MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build(key("elizabeth")));

    /** Vial de sangre: lo sueltan los vampiros; cura y devuelve un poco de poder. */
    public static final DeferredHolder<Item, Item> BLOOD_VIAL = DraxAtomic.ITEMS.registerItem("blood_vial",
            properties -> new VialItem(properties.stacksTo(16).rarity(Rarity.UNCOMMON)));
    /** Cáliz carmesí: de noche, en el suelo, invoca a Crimson. */
    public static final DeferredHolder<Item, Item> CHALICE = DraxAtomic.ITEMS.registerItem("crimson_chalice",
            properties -> new ChaliceItem(properties.stacksTo(1).rarity(Rarity.RARE)));
    /** Sangre de Crimson: al beberla se aprende la magia de sangre. */
    public static final DeferredHolder<Item, Item> CRIMSON_BLOOD = DraxAtomic.ITEMS.registerItem("crimson_blood",
            properties -> new CrimsonBloodItem(properties.stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    /** Sangre de la Reina: +100 de poder máximo y 3 puntos de habilidad. */
    public static final DeferredHolder<Item, Item> QUEEN_BLOOD = DraxAtomic.ITEMS.registerItem("queen_blood",
            properties -> new QueenBloodItem(properties.stacksTo(4).rarity(Rarity.EPIC).fireResistant()));

    /** Si el jugador ya conoce la magia de sangre (se conserva al morir). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> BLOOD_MAGIC = Mana.ATTACHMENTS.register("blood_magic",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("value")).copyOnDeath().build());

    static void init(IEventBus modEventBus) {
        modEventBus.addListener(Vampires::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(SERVANT.value(), VampireEntity.createAttributes().build());
        event.put(CRIMSON.value(), BloodLordEntity.createAttributes(BloodLordEntity.Lord.CRIMSON).build());
        event.put(ELIZABETH.value(), BloodLordEntity.createAttributes(BloodLordEntity.Lord.ELIZABETH).build());
    }

    public static boolean knowsBloodMagic(Player player) {
        return player.getData(BLOOD_MAGIC);
    }

    static final DustParticleOptions BLOOD = new DustParticleOptions(0xB0001E, 1.3F);
    static final DustParticleOptions BLOOD_DARK = new DustParticleOptions(0x5A0010, 1.6F);

    // ---------------------------------------------------------------- siervos nocturnos

    /** De noche aparecen pequeños grupos de siervos vampiro cerca de los jugadores. */
    private record Awakening(ServerLevel level, net.minecraft.world.phys.Vec3 at, int[] delay) {}

    private static final java.util.List<Awakening> AWAKENINGS = new java.util.ArrayList<>();

    /** La sangre de Crimson empapa el ataúd: Elizabeth despierta en lo alto de la Torre Carmesí. */
    static void awakenQueen(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
        AWAKENINGS.add(new Awakening(level, at, new int[] {80}));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < 128 * 128) {
                player.sendSystemMessage(Component.translatable("message.draxatomic.queen_stirs").withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    private static void awakeningTick() {
        java.util.Iterator<Awakening> it = AWAKENINGS.iterator();
        while (it.hasNext()) {
            Awakening a = it.next();
            int left = --a.delay()[0];
            ServerLevel level = a.level();
            if (left > 0) {
                if (left % 4 == 0) level.sendParticles(BLOOD, true, true, a.at().x, a.at().y + 0.5, a.at().z, 20, 0.8, 0.4, 0.8, 0.02);
                continue;
            }
            it.remove();
            BloodLordEntity queen = ELIZABETH.value().create(level, EntitySpawnReason.TRIGGERED);
            if (queen == null) continue;
            queen.snapTo(a.at().x, a.at().y, a.at().z, 180.0F, 0);
            level.addFreshEntity(queen);
            queen.announce();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!AWAKENINGS.isEmpty()) awakeningTick();
        if (!AtomicConfig.vampires()) return;
        ServerLevel level = event.getServer().overworld();
        // En la Ciudad sin Ley, de noche, los siervos salen de los callejones mucho más seguido.
        if (level.getGameTime() % 200 == 100 && !level.isBrightOutside()) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || Cities.lawlessAt(level, player.getX(), player.getZ()).isEmpty()) continue;
                if (level.getEntitiesOfClass(VampireEntity.class, player.getBoundingBox().inflate(48)).size() >= 10) continue;
                RandomSource r = level.getRandom();
                double angle = r.nextDouble() * Math.PI * 2;
                int cx = (int) (player.getX() + Math.cos(angle) * 18);
                int cz = (int) (player.getZ() + Math.sin(angle) * 18);
                for (int i = 0; i < 2; i++) spawnServant(level, cx + r.nextInt(5) - 2, cz + r.nextInt(5) - 2, null);
            }
        }
        if (level.getGameTime() % 800 != 400 || level.isBrightOutside()) return;
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || random.nextFloat() > AtomicConfig.vampireChance()) continue;
            if (level.getEntitiesOfClass(VampireEntity.class, player.getBoundingBox().inflate(64)).size() >= 6) continue;
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 24 + random.nextDouble() * 16;
            int cx = (int) (player.getX() + Math.cos(angle) * distance);
            int cz = (int) (player.getZ() + Math.sin(angle) * distance);
            int count = 2 + random.nextInt(2);
            for (int i = 0; i < count; i++) spawnServant(level, cx + random.nextInt(5) - 2, cz + random.nextInt(5) - 2, null);
        }
    }

    static VampireEntity spawnServant(ServerLevel level, int x, int z, LivingEntity target) {
        BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        if (!level.isLoaded(ground) || !level.getFluidState(ground).isEmpty()) return null;
        VampireEntity servant = SERVANT.value().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (servant == null) return null;
        servant.snapTo(x + 0.5, ground.getY(), z + 0.5, level.getRandom().nextFloat() * 360, 0);
        if (target != null) servant.setTarget(target);
        level.addFreshEntity(servant);
        level.sendParticles(BLOOD_DARK, servant.getX(), servant.getY() + 1, servant.getZ(), 16, 0.3, 0.8, 0.3, 0);
        return servant;
    }

    // ---------------------------------------------------------------- drenaje de sangre (jugador)

    /** Con "Drenaje de sangre" activo, tus golpes te curan un cuarto del daño hecho. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity() == player) return;
        if (!Magic.state(player).on(MagicState.Spell.BLOOD_DRAIN)) return;
        float heal = event.getAmount() * 0.25F;
        if (heal <= 0) return;
        player.heal(heal);
        LivingEntity victim = event.getEntity();
        ((ServerLevel) player.level()).sendParticles(BLOOD, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                8, 0.25, 0.3, 0.25, 0.02);
    }

    // ---------------------------------------------------------------- ítems

    public static final class VialItem extends Item {
        public VialItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer sp) {
                player.heal(8.0F);
                Mana.restore(sp, 30);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.5F);
                player.getItemInHand(hand).consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }
    }

    public static final class ChaliceItem extends Item {
        public ChaliceItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Player player = context.getPlayer();
            if (player == null) return InteractionResult.PASS;
            if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
            if (level.isBrightOutside()) {
                player.sendOverlayMessage(Component.translatable("message.draxatomic.chalice_day").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            BlockPos at = context.getClickedPos().above();
            BloodLordEntity lord = CRIMSON.value().create(level, EntitySpawnReason.TRIGGERED);
            if (lord == null) return InteractionResult.FAIL;
            lord.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0);
            level.addFreshEntity(lord);
            lord.announce();
            context.getItemInHand().consume(1, player);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable("tooltip.draxatomic.crimson_chalice").withStyle(ChatFormatting.DARK_RED));
        }
    }

    public static final class CrimsonBloodItem extends Item {
        public CrimsonBloodItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer sp) {
                boolean already = knowsBloodMagic(sp);
                sp.setData(BLOOD_MAGIC, true);
                if (!already) Magic.addPoints(sp, 1);
                ServerLevel server = (ServerLevel) level;
                server.sendParticles(BLOOD_DARK, sp.getX(), sp.getY() + 1, sp.getZ(), 60, 0.5, 0.9, 0.5, 0.05);
                server.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.4F);
                sp.sendSystemMessage(Component.translatable(already ? "message.draxatomic.blood_known" : "message.draxatomic.blood_learned")
                        .withStyle(ChatFormatting.DARK_RED));
                if (!already) player.getItemInHand(hand).consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable("tooltip.draxatomic.crimson_blood").withStyle(ChatFormatting.DARK_RED));
        }
    }

    public static final class QueenBloodItem extends Item {
        public static final int BONUS = 100;

        public QueenBloodItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer sp) {
                Mana.addBonus(sp, BONUS);
                Magic.addPoints(sp, 3);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 0.8F);
                ((ServerLevel) level).sendParticles(ParticleTypes.DAMAGE_INDICATOR, sp.getX(), sp.getY() + 1, sp.getZ(), 20, 0.4, 0.6, 0.4, 0.1);
                player.sendOverlayMessage(Component.translatable("message.draxatomic.heart_used", BONUS, Mana.max(player))
                        .withStyle(ChatFormatting.DARK_RED));
                player.getItemInHand(hand).consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable("tooltip.draxatomic.queen_blood", BONUS).withStyle(ChatFormatting.DARK_RED));
        }
    }
}
