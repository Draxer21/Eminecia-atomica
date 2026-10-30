package com.draxer.atomic;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Opciones del servidor (world/serverconfig/draxatomic-server.toml). */
public final class AtomicConfig {
    private AtomicConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static { BUILDER.push("mana"); }
    private static final ModConfigSpec.IntValue MAX_MANA = BUILDER
            .comment("Poder mágico máximo.")
            .defineInRange("maxMana", 100, 10, 10000);
    private static final ModConfigSpec.IntValue MANA_REGEN = BUILDER
            .comment("Poder mágico que se recupera por segundo.")
            .defineInRange("manaRegenPerSecond", 4, 0, 1000);

    static { BUILDER.pop().push("whip"); }
    private static final ModConfigSpec.IntValue WHIP_COST = BUILDER
            .comment("Costo del Látigo de slime.").defineInRange("cost", 8, 0, 10000);
    private static final ModConfigSpec.DoubleValue WHIP_DAMAGE = BUILDER
            .comment("Daño del Látigo a cada objetivo en la línea.").defineInRange("damage", 12.0, 0.0, 1000.0);
    private static final ModConfigSpec.IntValue WHIP_RANGE = BUILDER
            .comment("Alcance del Látigo en bloques.").defineInRange("range", 10, 3, 32);

    static { BUILDER.pop().push("dash"); }
    private static final ModConfigSpec.IntValue DASH_COST = BUILDER
            .comment("Costo del Corte instantáneo.").defineInRange("cost", 20, 0, 10000);
    private static final ModConfigSpec.DoubleValue DASH_DAMAGE = BUILDER
            .comment("Daño a todo lo que atraviesas.").defineInRange("damage", 16.0, 0.0, 1000.0);
    private static final ModConfigSpec.IntValue DASH_DISTANCE = BUILDER
            .comment("Distancia del Corte en bloques.").defineInRange("distance", 8, 2, 24);

    static { BUILDER.pop().push("blade_rain"); }
    private static final ModConfigSpec.IntValue RAIN_COST = BUILDER
            .comment("Costo de la Lluvia de cuchillas.").defineInRange("cost", 35, 0, 10000);
    private static final ModConfigSpec.DoubleValue RAIN_DAMAGE = BUILDER
            .comment("Daño de cada oleada de cuchillas (6 oleadas).").defineInRange("damage", 6.0, 0.0, 1000.0);
    private static final ModConfigSpec.IntValue RAIN_RADIUS = BUILDER
            .comment("Radio del área de la lluvia.").defineInRange("radius", 5, 2, 16);

    static { BUILDER.pop().push("atomic"); }
    private static final ModConfigSpec.IntValue ATOMIC_COST = BUILDER
            .comment("Poder mágico que consume el Atomic. Con el valor por defecto necesitas la barra llena.")
            .defineInRange("cost", 100, 0, 10000);
    private static final ModConfigSpec.IntValue RADIUS = BUILDER
            .comment("Radio de la explosión en bloques. Radios grandes tardan un poco más en tallar el cráter.")
            .defineInRange("radius", 24, 4, 64);
    private static final ModConfigSpec.DoubleValue DAMAGE = BUILDER
            .comment("Daño en el centro de la explosión (baja hasta la mitad en el borde). Ignora armadura.")
            .defineInRange("damage", 150.0, 0.0, 10000.0);
    private static final ModConfigSpec.BooleanValue DESTROY_BLOCKS = BUILDER
            .comment("Si es true, la explosión borra el terreno dentro del radio.")
            .define("destroyBlocks", true);
    private static final ModConfigSpec.BooleanValue PROTECT_CONTAINERS = BUILDER
            .comment("Si es true, cofres, hornos y cualquier bloque con contenido sobreviven a la explosión.")
            .define("protectContainers", true);
    private static final ModConfigSpec.DoubleValue CHARGE_SECONDS = BUILDER
            .comment("Segundos que hay que mantener clic derecho para cargar el ataque.")
            .defineInRange("chargeSeconds", 3.0, 1.0, 10.0);
    private static final ModConfigSpec.IntValue COOLDOWN_SECONDS = BUILDER
            .comment("Enfriamiento de la espada tras el Atomic, en segundos.")
            .defineInRange("cooldownSeconds", 10, 0, 3600);
    static { BUILDER.pop().push("all_range_atomic"); }
    private static final ModConfigSpec.IntValue AR_COST = BUILDER
            .comment("Costo del All-Range Atomic.").defineInRange("cost", 140, 0, 10000);
    private static final ModConfigSpec.IntValue AR_RADIUS = BUILDER
            .comment("Radio en el que busca enemigos.").defineInRange("radius", 40, 8, 128);
    private static final ModConfigSpec.IntValue AR_TARGETS = BUILDER
            .comment("Máximo de enemigos marcados.").defineInRange("maxTargets", 24, 1, 200);
    private static final ModConfigSpec.IntValue AR_BLAST = BUILDER
            .comment("Radio de cada explosión pequeña.").defineInRange("blastRadius", 4, 2, 16);
    private static final ModConfigSpec.DoubleValue AR_DAMAGE = BUILDER
            .comment("Daño de cada explosión pequeña.").defineInRange("damage", 60.0, 0.0, 10000.0);
    private static final ModConfigSpec.BooleanValue AR_BREAK = BUILDER
            .comment("Si las explosiones pequeñas rompen el terreno.").define("destroyBlocks", false);

    static { BUILDER.pop().push("recovery_atomic"); }
    private static final ModConfigSpec.IntValue REC_COST = BUILDER
            .comment("Costo del Recovery Atomic.").defineInRange("cost", 60, 0, 10000);
    private static final ModConfigSpec.IntValue REC_RADIUS = BUILDER
            .comment("Radio de curación.").defineInRange("radius", 20, 4, 64);

    static { BUILDER.pop().push("cities"); }
    private static final ModConfigSpec.BooleanValue CITIES = BUILDER
            .comment("Si es true, las capitales de Midgar y Oriana se construyen en el mundo.")
            .define("enabled", true);
    private static final ModConfigSpec.IntValue MIDGAR_SPACING = BUILDER
            .comment("Tamaño de la región de cada Midgar (256x256): una posible ciudad cada N bloques. Mínimo 800.")
            .defineInRange("midgarSpacing", 2400, 800, 100000);
    private static final ModConfigSpec.IntValue ORIANA_SPACING = BUILDER
            .comment("Tamaño de la región de cada Oriana (128x128): una posible ciudad cada N bloques. Mínimo 600.")
            .defineInRange("orianaSpacing", 1800, 600, 100000);

    static { BUILDER.pop().push("diablos_cult"); }
    private static final ModConfigSpec.BooleanValue CULT_PATROLS = BUILDER
            .comment("Si es true, patrullas del Culto de Diablos aparecen de noche cerca de los jugadores.")
            .define("patrols", true);
    private static final ModConfigSpec.DoubleValue PATROL_CHANCE = BUILDER
            .comment("Probabilidad (cada 30 s de noche, por jugador) de que aparezca una patrulla.")
            .defineInRange("patrolChance", 0.25, 0.0, 1.0);
    static { BUILDER.pop(); }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean loaded() {
        return SPEC.isLoaded();
    }

    public static int maxMana() { return loaded() ? MAX_MANA.getAsInt() : 100; }
    public static int manaRegenPerSecond() { return loaded() ? MANA_REGEN.getAsInt() : 4; }

    public static int whipCost() { return loaded() ? WHIP_COST.getAsInt() : 8; }
    public static float whipDamage() { return loaded() ? WHIP_DAMAGE.get().floatValue() : 12.0F; }
    public static int whipRange() { return loaded() ? WHIP_RANGE.getAsInt() : 10; }

    public static int dashCost() { return loaded() ? DASH_COST.getAsInt() : 20; }
    public static float dashDamage() { return loaded() ? DASH_DAMAGE.get().floatValue() : 16.0F; }
    public static int dashDistance() { return loaded() ? DASH_DISTANCE.getAsInt() : 8; }

    public static int rainCost() { return loaded() ? RAIN_COST.getAsInt() : 35; }
    public static float rainDamage() { return loaded() ? RAIN_DAMAGE.get().floatValue() : 6.0F; }
    public static int rainRadius() { return loaded() ? RAIN_RADIUS.getAsInt() : 5; }

    public static int allRangeCost() { return loaded() ? AR_COST.getAsInt() : 140; }
    public static int allRangeRadius() { return loaded() ? AR_RADIUS.getAsInt() : 40; }
    public static int allRangeMaxTargets() { return loaded() ? AR_TARGETS.getAsInt() : 24; }
    public static int allRangeBlastRadius() { return loaded() ? AR_BLAST.getAsInt() : 4; }
    public static float allRangeDamage() { return loaded() ? AR_DAMAGE.get().floatValue() : 60.0F; }
    public static boolean allRangeBreaksBlocks() { return loaded() && AR_BREAK.getAsBoolean(); }
    public static int recoveryCost() { return loaded() ? REC_COST.getAsInt() : 60; }
    public static int recoveryRadius() { return loaded() ? REC_RADIUS.getAsInt() : 20; }

    public static boolean cities() { return !loaded() || CITIES.getAsBoolean(); }
    public static int midgarSpacing() { return loaded() ? MIDGAR_SPACING.getAsInt() : 2400; }
    public static int orianaSpacing() { return loaded() ? ORIANA_SPACING.getAsInt() : 1800; }

    public static boolean cultPatrols() { return !loaded() || CULT_PATROLS.getAsBoolean(); }
    public static float patrolChance() { return loaded() ? PATROL_CHANCE.get().floatValue() : 0.25F; }

    public static int atomicCost() { return loaded() ? ATOMIC_COST.getAsInt() : 100; }
    public static int radius() { return loaded() ? RADIUS.getAsInt() : 24; }
    public static float damage() { return loaded() ? DAMAGE.get().floatValue() : 150.0F; }
    public static boolean destroyBlocks() { return !loaded() || DESTROY_BLOCKS.getAsBoolean(); }
    public static boolean protectContainers() { return !loaded() || PROTECT_CONTAINERS.getAsBoolean(); }
    public static int chargeTicks() { return loaded() ? (int) Math.round(CHARGE_SECONDS.get() * 20) : 60; }
    public static int cooldownTicks() { return loaded() ? COOLDOWN_SECONDS.getAsInt() * 20 : 200; }
}
