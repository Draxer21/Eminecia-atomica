package com.draxer.atomic;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Logros del mod que se dan desde el código (los demás usan disparadores de vanilla). */
public final class Achievements {
    private Achievements() {}

    public static void grant(ServerPlayer player, String id) {
        if (!(player.level() instanceof ServerLevel level)) return;
        AdvancementHolder holder = level.getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, id));
        if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
            player.getAdvancements().award(holder, "done");
        }
    }
}
