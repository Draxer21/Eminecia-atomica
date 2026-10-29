package com.draxer.atomic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;

/**
 * Lo que el jugador sabe de magia y lo que tiene activo. Se guarda con él y se conserva al morir.
 *
 * @param spell       hechizo seleccionado (índice de {@link Spell})
 * @param compression nivel de compresión acumulado (0 = nada)
 * @param toggles     hechizos sostenidos activos (bits de {@link Spell#bit()})
 * @param points      puntos de habilidad sin gastar
 * @param xp          poder gastado desde el último punto ganado
 * @param skills      nivel de cada mejora (id de {@link Upgrade})
 */
public record MagicState(int spell, int compression, int toggles, int points, int xp, Map<String, Integer> skills) {
    public static final MagicState EMPTY = new MagicState(0, 0, 0, 0, 0, Map.of());

    public static final com.mojang.serialization.MapCodec<MagicState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("spell", 0).forGetter(MagicState::spell),
            Codec.INT.optionalFieldOf("compression", 0).forGetter(MagicState::compression),
            Codec.INT.optionalFieldOf("toggles", 0).forGetter(MagicState::toggles),
            Codec.INT.optionalFieldOf("points", 0).forGetter(MagicState::points),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(MagicState::xp),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("skills", Map.of()).forGetter(MagicState::skills))
            .apply(i, MagicState::new));

    public int level(Upgrade upgrade) {
        return skills.getOrDefault(upgrade.id(), 0);
    }

    public boolean on(Spell spell) {
        return (toggles & spell.bit()) != 0;
    }

    public MagicState withSpell(int value) {
        return new MagicState(value, compression, toggles, points, xp, skills);
    }

    public MagicState withCompression(int value) {
        return new MagicState(spell, value, toggles, points, xp, skills);
    }

    public MagicState withToggle(Spell spell, boolean active) {
        int t = active ? toggles | spell.bit() : toggles & ~spell.bit();
        return new MagicState(this.spell, compression, t, points, xp, skills);
    }

    public MagicState withPoints(int points, int xp) {
        return new MagicState(spell, compression, toggles, points, xp, skills);
    }

    public MagicState withSkill(Upgrade upgrade, int level) {
        Map<String, Integer> map = new HashMap<>(skills);
        map.put(upgrade.id(), level);
        return new MagicState(spell, compression, toggles, points, xp, map);
    }

    /** Hechizos: los de "toggle" se mantienen activos y gastan poder mientras duran. */
    public enum Spell {
        SHADOW_STEP("shadow_step", 15, false),
        PROJECTION("projection", 30, false),
        COUNTER("counter", 10, false),
        SLIME_PLATFORM("slime_platform", 10, false),
        MANA_SENSE("mana_sense", 20, false),
        SLIME_FORGE("slime_forge", 0, false),
        SUMMON_GARDEN("summon_garden", 25, false),
        SLIME_ARMOR("slime_armor", 0, true),
        SHADOW_STEALTH("shadow_stealth", 10, true),
        CONCEAL("conceal", 0, true),
        AWAKENING("awakening", 0, true),
        OVERLOAD("overload", 0, true);

        private final String id;
        private final int cost;
        private final boolean toggle;

        Spell(String id, int cost, boolean toggle) {
            this.id = id;
            this.cost = cost;
            this.toggle = toggle;
        }

        public String id() {
            return id;
        }

        /** Costo base al lanzar (o al activar, si es sostenido). */
        public int cost() {
            return cost;
        }

        public boolean toggle() {
            return toggle;
        }

        public int bit() {
            return 1 << ordinal();
        }

        public static Spell of(int index) {
            Spell[] all = values();
            return all[Math.floorMod(index, all.length)];
        }
    }

    /** Mejoras del árbol de habilidades. Subir al nivel n cuesta n puntos. */
    public enum Upgrade {
        EFFICIENCY("efficiency", 3),
        POWER("power", 3),
        REACH("reach", 3),
        RESERVOIR("reservoir", 3),
        FLOW("flow", 3),
        COMPRESSION("compression", 2),
        ATOMIC("atomic", 2);

        private final String id;
        private final int max;

        Upgrade(String id, int max) {
            this.id = id;
            this.max = max;
        }

        public String id() {
            return id;
        }

        public int max() {
            return max;
        }
    }
}
