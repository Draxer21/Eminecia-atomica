package com.draxer.atomic;

import java.util.Random;

/**
 * Plano de una ciudad: una cuadrícula de cuadras de {@link #CELL}×{@link #CELL} bloques.
 * Cada cuadra recibe el nombre de una plantilla (data/draxatomic/structure/city/&lt;estilo&gt;/&lt;nombre&gt;.nbt).
 * Los edificios grandes ocupan 2×2 cuadras: la esquina noroeste lleva la plantilla y las otras
 * tres quedan "cubiertas" (null).
 */
public final class CityLayout {
    public static final int CELL = 32;

    public enum Kind {
        MIDGAR("midgar", 8),
        ORIANA("oriana", 4),
        /** El Santuario: un templo amurallado de 64×64 que absorbe el poder mágico. */
        SANCTUARY("sanctuary", 2);

        public final String style;
        /** Cuadras por lado. */
        public final int cells;

        Kind(String style, int cells) {
            this.style = style;
            this.cells = cells;
        }

        public int size() {
            return cells * CELL;
        }
    }

    private final Kind kind;
    private final String[][] grid;
    private final int[][] spans;

    public CityLayout(Kind kind, long seed) {
        this.kind = kind;
        int n = kind.cells;
        this.grid = new String[n][n];
        this.spans = new int[n][n];
        Random random = new Random(seed);
        // La muralla, las torres y las puertas las construye Cities alrededor de la cuadrícula.
        if (kind == Kind.MIDGAR) {
            big(3, 0, "castle");
            big(1, 2, "mitsugoshi_hq");
            big(5, 2, "cathedral");
            big(3, 3, "plaza");
            big(3, 5, "academy");
            for (int[] m : new int[][] {{2, 4}, {5, 4}, {1, 5}}) one(m[0], m[1], "market");
            for (int[] b : new int[][] {{0, 0}, {7, 7}}) one(b[0], b[1], "barracks");
        } else if (kind == Kind.SANCTUARY) {
            big(0, 0, "temple");
        } else {
            big(0, 0, "palace");
            big(2, 0, "opera");
            big(1, 2, "plaza");
            one(0, 2, "mitsugoshi");
            one(3, 2, "gallery");
            one(0, 3, "houses_a");
            one(3, 3, "houses_b");
        }
        // El resto: casas de varios estilos, con algún parque.
        String[] houses = kind == Kind.MIDGAR
                ? new String[] {"houses_a", "houses_b", "houses_c", "houses_d"}
                : new String[] {"houses_a", "houses_b", "houses_c"};
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] != null) continue;
                grid[i][j] = random.nextInt(9) == 0 ? "park" : houses[random.nextInt(houses.length)];
            }
        }
    }

    private static final String COVERED = "";

    private void big(int i, int j, String name) {
        grid[i][j] = name;
        spans[i][j] = 2;
        grid[i + 1][j] = COVERED;
        grid[i][j + 1] = COVERED;
        grid[i + 1][j + 1] = COVERED;
    }

    private void one(int i, int j, String name) {
        if (grid[i][j] == null) grid[i][j] = name;
    }

    public Kind kind() {
        return kind;
    }

    /** Plantilla de la cuadra (i, j), o null si está cubierta por un edificio grande. */
    public String template(int i, int j) {
        String name = grid[i][j];
        return name == null || name.isEmpty() ? null : "city/" + kind.style + "/" + name;
    }

    /** Tamaño en cuadras de la plantilla que empieza en (i, j). */
    public int span(int i, int j) {
        return spans[i][j] == 2 ? 2 : 1;
    }
}
