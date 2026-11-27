package com.draxer.atomic;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Preferencia visual local del cliente para el renderizador de Proyección. */
public final class ProjectionConfig {
    private ProjectionConfig() {}

    public enum Quality {
        LIGHT,
        NORMAL,
        CINEMATIC
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.EnumValue<Quality> QUALITY = BUILDER
            .comment("Calidad del renderizador 3D de Proyección: LIGHT, NORMAL o CINEMATIC.")
            .defineEnum("quality", Quality.NORMAL);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static Quality quality() {
        return SPEC.isLoaded() ? QUALITY.get() : Quality.NORMAL;
    }
}
