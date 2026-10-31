package com.draxer.atomic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Los modos de la espada de slime; se cambian con shift + clic derecho. */
public enum SwordMode {
    WHIP("whip", ChatFormatting.DARK_GREEN),
    DASH("dash", ChatFormatting.AQUA),
    BLADE_RAIN("blade_rain", ChatFormatting.GOLD),
    ATOMIC("atomic", ChatFormatting.LIGHT_PURPLE),
    ALL_RANGE_ATOMIC("all_range_atomic", ChatFormatting.DARK_PURPLE),
    RECOVERY_ATOMIC("recovery_atomic", ChatFormatting.GREEN);

    private static final String KEY = "DraxMode";
    private final String id;
    private final ChatFormatting color;

    SwordMode(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    public Component title() {
        return Component.translatable("mode.draxatomic." + id).withStyle(color, ChatFormatting.BOLD);
    }

    public int cost() {
        return switch (this) {
            case WHIP -> AtomicConfig.whipCost();
            case DASH -> AtomicConfig.dashCost();
            case BLADE_RAIN -> AtomicConfig.rainCost();
            case ATOMIC -> AtomicConfig.atomicCost();
            case ALL_RANGE_ATOMIC -> 0; // se paga con poder comprimido
            case RECOVERY_ATOMIC -> AtomicConfig.recoveryCost();
        };
    }

    /** Los Atomic se cargan manteniendo clic derecho. */
    public boolean charged() {
        return this == ATOMIC || this == ALL_RANGE_ATOMIC || this == RECOVERY_ATOMIC;
    }

    public int chargeTicks() {
        int base = AtomicConfig.chargeTicks();
        return switch (this) {
            case ALL_RANGE_ATOMIC -> Math.round(base * 1.35F);
            case RECOVERY_ATOMIC -> Math.max(12, Math.round(base * 0.65F));
            default -> base;
        };
    }

    public String id() {
        return id;
    }

    public SwordMode next() {
        SwordMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static SwordMode of(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return ATOMIC;
        int index = data.copyTag().getInt(KEY).orElse(ATOMIC.ordinal());
        SwordMode[] all = values();
        return index >= 0 && index < all.length ? all[index] : ATOMIC;
    }

    public static void set(ItemStack stack, SwordMode mode) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(KEY, mode.ordinal());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
