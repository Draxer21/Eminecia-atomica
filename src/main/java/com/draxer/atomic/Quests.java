package com.draxer.atomic;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Misiones de los personajes. Cada una es "derrota N de algo" o "trae N de algo", y al
 * entregarla da objetos y/o poder mágico permanente. El progreso de cada jugador se guarda con
 * él (attachment): -1 = sin aceptar, 0..N = en curso, DONE = entregada.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class Quests {
    private Quests() {}

    static final int DONE = Integer.MAX_VALUE;

    public enum Type { KILL, BRING }

    /** {@code target}: id de entidad, "enemy" (cualquier monstruo) o id de ítem. */
    public record Reward(String item, int count) {}

    public record Quest(String id, String giver, Type type, String target, int count, int mana, List<Reward> rewards) {}

    private static Quest kill(String id, String giver, String target, int count, int mana, Reward... rewards) {
        return new Quest(id, giver, Type.KILL, target, count, mana, List.of(rewards));
    }

    private static Quest bring(String id, String giver, String item, int count, int mana, Reward... rewards) {
        return new Quest(id, giver, Type.BRING, item, count, mana, List.of(rewards));
    }

    private static Reward r(String item, int count) {
        return new Reward(item, count);
    }

    public static final List<Quest> ALL = List.of(
            // Alexia Midgar
            kill("alexia_cleanup", "alexia", "draxatomic:diablos_cultist", 10, 0,
                    r("minecraft:emerald", 16), r("draxatomic:mitsugoshi_chocolate", 4)),
            kill("alexia_round_knight", "alexia", "draxatomic:round_knight", 1, 0,
                    r("minecraft:netherite_ingot", 2), r("minecraft:emerald", 32)),
            // Iris Midgar
            kill("iris_crimson_order", "iris", "enemy", 25, 0,
                    r("minecraft:emerald", 24), r("minecraft:golden_apple", 2)),
            bring("iris_supplies", "iris", "minecraft:iron_ingot", 32, 0,
                    r("draxatomic:shadow_emblem", 1), r("minecraft:emerald", 16)),
            // Claire Kagenou
            kill("claire_training", "claire", "minecraft:skeleton", 15, 0,
                    r("minecraft:diamond_sword", 1), r("minecraft:emerald", 8)),
            bring("claire_brother", "claire", "draxatomic:diablos_drop", 10, 25,
                    r("minecraft:emerald", 12)),
            // Gamma / Mitsugoshi
            bring("gamma_cocoa", "gamma", "minecraft:cocoa_beans", 64, 0,
                    r("draxatomic:mitsugoshi_chocolate", 10), r("minecraft:emerald", 16)),
            bring("gamma_expansion", "gamma", "minecraft:gold_ingot", 32, 0,
                    r("draxatomic:mitsugoshi_counter", 1), r("minecraft:emerald", 48)),
            bring("gamma_research", "gamma", "draxatomic:diablos_drop", 16, 0,
                    r("draxatomic:slime_coat", 1)),
            // Rose Oriana
            kill("rose_art", "rose", "draxatomic:diablos_cultist", 8, 0,
                    r("draxatomic:mitsugoshi_shampoo", 3), r("minecraft:emerald", 24)),
            kill("rose_wedding", "rose", "draxatomic:round_knight", 1, 50,
                    r("minecraft:diamond", 8)),
            // Rey Raphael
            bring("raphael_tribute", "raphael", "draxatomic:diablos_drop", 20, 0,
                    r("minecraft:emerald", 64), r("minecraft:gold_block", 4)),
            // Mary, la cazadora de vampiros (Ciudad sin Ley)
            kill("mary_servants", "mary", "draxatomic:vampire_servant", 15, 0,
                    r("draxatomic:blood_vial", 4), r("minecraft:emerald", 16)),
            kill("mary_crimson", "mary", "draxatomic:crimson", 1, 25,
                    r("minecraft:diamond", 6)),
            kill("mary_queen", "mary", "draxatomic:elizabeth", 1, 50,
                    r("minecraft:netherite_ingot", 2)),
            kill("mary_aurora", "mary", "draxatomic:aurora", 1, 50,
                    r("minecraft:enchanted_golden_apple", 1)),
            // Yukime, la Zorra de las Nieves
            bring("yukime_vials", "yukime", "draxatomic:blood_vial", 10, 0,
                    r("minecraft:emerald", 32), r("minecraft:gold_block", 2)),
            // Juggernaut
            kill("juggernaut_pit", "juggernaut", "enemy", 30, 0,
                    r("minecraft:netherite_ingot", 1), r("minecraft:iron_block", 8)),
            kill("juggernaut_cult", "juggernaut", "draxatomic:diablos_cultist", 15, 0,
                    r("minecraft:emerald", 24)));

    public static Quest byId(String id) {
        for (Quest q : ALL) if (q.id().equals(id)) return q;
        return null;
    }

    // ---------------------------------------------------------------- progreso guardado

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Map<String, Integer>>> LOG = Mana.ATTACHMENTS.register("quests",
            () -> AttachmentType.<Map<String, Integer>>builder(() -> new HashMap<>())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("progress")).copyOnDeath().build());

    static int progress(ServerPlayer player, Quest quest) {
        return player.getData(LOG).getOrDefault(quest.id(), -1);
    }

    private static void setProgress(ServerPlayer player, Quest quest, int value) {
        Map<String, Integer> log = new HashMap<>(player.getData(LOG));
        log.put(quest.id(), value);
        player.setData(LOG, log);
    }

    // ---------------------------------------------------------------- diálogo

    /** El personaje saluda y lista sus misiones con botones para aceptar o entregar. */
    static void talk(ServerPlayer player, StoryNpcEntity npc) {
        Characters.Character c = npc.character();
        Component name = Component.translatable("npc.draxatomic." + c.id()).withStyle(c.color(), ChatFormatting.BOLD);
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.empty().append(name).append(Component.literal(": "))
                .append(Component.translatable("dialog.draxatomic." + c.id() + ".greet").withStyle(ChatFormatting.ITALIC)));
        boolean any = false;
        for (Quest quest : ALL) {
            if (!quest.giver().equals(c.id())) continue;
            any = true;
            int p = progress(player, quest);
            MutableComponent line = Component.literal(" • ").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("quest.draxatomic." + quest.id()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(goal(quest).withStyle(ChatFormatting.GRAY));
            if (p == DONE) {
                line.append(Component.translatable("quest.draxatomic.status.done").withStyle(ChatFormatting.DARK_GREEN));
            } else if (p < 0) {
                line.append(button("quest.draxatomic.accept", "/draxatomic quest accept " + quest.id(), ChatFormatting.GREEN, quest));
            } else {
                int have = quest.type() == Type.BRING ? count(player, quest.target()) : p;
                line.append(Component.literal(" (" + Math.min(have, quest.count()) + "/" + quest.count() + ")").withStyle(ChatFormatting.WHITE));
                if (have >= quest.count()) {
                    line.append(button("quest.draxatomic.turnin", "/draxatomic quest turnin " + quest.id(), ChatFormatting.AQUA, quest));
                }
            }
            player.sendSystemMessage(line);
        }
        if (!any) player.sendSystemMessage(Component.translatable("quest.draxatomic.none").withStyle(ChatFormatting.GRAY));
        player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.6F, 1.3F);
    }

    private static MutableComponent goal(Quest quest) {
        Component what = quest.type() == Type.KILL
                ? (quest.target().equals("enemy") ? Component.translatable("quest.draxatomic.monsters")
                        : entityType(quest.target()).getDescription())
                : item(quest.target()).getName(new ItemStack(item(quest.target())));
        return Component.translatable(quest.type() == Type.KILL ? "quest.draxatomic.goal.kill" : "quest.draxatomic.goal.bring",
                quest.count(), what);
    }

    private static MutableComponent rewardText(Quest quest) {
        MutableComponent text = Component.translatable("quest.draxatomic.rewards").withStyle(ChatFormatting.YELLOW);
        for (Reward reward : quest.rewards()) {
            text.append(Component.literal("\n " + reward.count() + "× ").withStyle(ChatFormatting.WHITE))
                    .append(item(reward.item()).getName(new ItemStack(item(reward.item()))));
        }
        if (quest.mana() > 0) {
            text.append(Component.literal("\n ")).append(Component.translatable("quest.draxatomic.reward_mana", quest.mana())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return text;
    }

    private static MutableComponent button(String key, String command, ChatFormatting color, Quest quest) {
        return Component.literal(" ").append(Component.translatable(key).withStyle(style -> style
                .withColor(color).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(rewardText(quest)))));
    }

    private static EntityType<?> entityType(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private static int count(ServerPlayer player, String itemId) {
        Item item = item(itemId);
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void take(ServerPlayer player, String itemId, int amount) {
        Item item = item(itemId);
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(item)) continue;
            int t = Math.min(amount, stack.getCount());
            stack.shrink(t);
            amount -= t;
        }
    }

    /** El jugador tiene que estar cerca de quien da la misión. */
    private static boolean nearGiver(ServerPlayer player, Quest quest) {
        return !player.level().getEntitiesOfClass(StoryNpcEntity.class, player.getBoundingBox().inflate(10),
                npc -> npc.character().id().equals(quest.giver())).isEmpty();
    }

    // ---------------------------------------------------------------- comandos (botones)

    static int accept(ServerPlayer player, String id) {
        Quest quest = byId(id);
        if (quest == null || !nearGiver(player, quest) || progress(player, quest) != -1) return 0;
        setProgress(player, quest, 0);
        player.sendSystemMessage(Component.translatable("quest.draxatomic.accepted",
                Component.translatable("quest.draxatomic." + quest.id())).withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.translatable("quest.draxatomic." + quest.id() + ".text").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return 1;
    }

    static int turnIn(ServerPlayer player, String id) {
        Quest quest = byId(id);
        if (quest == null || !nearGiver(player, quest)) return 0;
        int p = progress(player, quest);
        if (p < 0 || p == DONE) return 0;
        if (quest.type() == Type.BRING) {
            if (count(player, quest.target()) < quest.count()) return 0;
            take(player, quest.target(), quest.count());
        } else if (p < quest.count()) {
            return 0;
        }
        setProgress(player, quest, DONE);
        for (Reward reward : quest.rewards()) {
            ItemStack stack = new ItemStack(item(reward.item()), reward.count());
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        if (quest.mana() > 0) Mana.addBonus(player, quest.mana());
        Magic.addPoints(player, 1);
        player.sendSystemMessage(Component.translatable("quest.draxatomic.completed",
                Component.translatable("quest.draxatomic." + quest.id())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        return 1;
    }

    static void list(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("quest.draxatomic.list").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        boolean any = false;
        for (Quest quest : ALL) {
            int p = progress(player, quest);
            if (p < 0 || p == DONE) continue;
            any = true;
            int have = quest.type() == Type.BRING ? count(player, quest.target()) : p;
            player.sendSystemMessage(Component.literal(" • ").append(Component.translatable("quest.draxatomic." + quest.id()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" — ")).append(goal(quest))
                    .append(Component.literal(" (" + Math.min(have, quest.count()) + "/" + quest.count() + ") "))
                    .append(Component.translatable("npc.draxatomic." + quest.giver()).withStyle(ChatFormatting.GRAY)));
        }
        if (!any) player.sendSystemMessage(Component.translatable("quest.draxatomic.list_empty").withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- progreso por bajas

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Entity killer = event.getSource().getEntity();
        ServerPlayer player = null;
        if (killer instanceof ServerPlayer p) player = p;
        else if (killer instanceof ShadowAllyEntity ally && ally.owner() instanceof ServerPlayer p) player = p;
        if (player == null) return;
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString();
        for (Quest quest : ALL) {
            if (quest.type() != Type.KILL) continue;
            boolean match = quest.target().equals(typeId) || (quest.target().equals("enemy") && dead instanceof Enemy);
            if (!match) continue;
            int p = progress(player, quest);
            if (p < 0 || p == DONE || p >= quest.count()) continue;
            setProgress(player, quest, p + 1);
            if (p + 1 == quest.count()) {
                player.sendSystemMessage(Component.translatable("quest.draxatomic.ready",
                        Component.translatable("quest.draxatomic." + quest.id()),
                        Component.translatable("npc.draxatomic." + quest.giver())).withStyle(ChatFormatting.AQUA));
            }
        }
    }
}
