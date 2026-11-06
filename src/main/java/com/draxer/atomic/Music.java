package com.draxer.atomic;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Pistas de música originales del mod (generadas por tools/music.py). */
public final class Music {
    private Music() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, DraxAtomic.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MIDGAR = track("midgar");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORIANA = track("oriana");
    public static final DeferredHolder<SoundEvent, SoundEvent> SANCTUARY = track("sanctuary");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAWLESS = track("lawless");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS = track("boss");

    private static DeferredHolder<SoundEvent, SoundEvent> track(String name) {
        return SOUNDS.register("music." + name,
                () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "music." + name)));
    }
}
