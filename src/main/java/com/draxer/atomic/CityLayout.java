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
        MIDGAR("midgar", 16),
        ORIANA("oriana", 8);

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
        walls(n);
        if (kind == Kind.MIDGAR) {
            big(7, 3, "castle");
            big(7, 7, "plaza");
            big(11, 6, "cathedral");
            big(3, 6, "mitsugoshi_hq");
            big(7, 11, "academy");
            for (int[] m : new int[][] {{6, 7}, {9, 7}, {6, 8}, {9, 8}, {5, 10}, {10, 10}}) one(m[0], m[1], "market");
            for (int[] b : new int[][] {{6, 1}, {9, 14}, {1, 8}, {14, 7}}) one(b[0], b[1], "barracks");
        } else {
            big(3, 1, "palace");
            big(3, 4, "plaza");
            big(1, 3, "opera");
            one(5, 3, "gallery");
            one(6, 3, "gallery");
            one(5, 5, "mitsugoshi");
            one(6, 5, "market");
            one(1, 6, "barracks");
        }
        // El resto: casas de varios estilos, con algún parque.
        String[] houses = kind == Kind.MIDGAR
                ? new String[] {"houses_a", "houses_b", "houses_c", "houses_d"}
                : new String[] {"houses_a", "houses_b", "houses_c"};
        for (int i = 1; i < n - 1; i++) {
            for (int j = 1; j < n - 1; j++) {
                if (grid[i][j] != null) continue;
                grid[i][j] = random.nextInt(9) == 0 ? "park" : houses[random.nextInt(houses.length)];
            }
        }
    }

    private void walls(int n) {
        int gate = n / 2;
        for (int k = 1; k < n - 1; k++) {
            grid[k][0] = k == gate ? "gate_n" : "wall_n";
            grid[k][n - 1] = k == gate ? "gate_s" : "wall_s";
            grid[0][k] = k == gate ? "gate_w" : "wall_w";
            grid[n - 1][k] = k == gate ? "gate_e" : "wall_e";
        }
        grid[0][0] = "tower_nw";
        grid[n - 1][0] = "tower_ne";
        grid[0][n - 1] = "tower_sw";
        grid[n - 1][n - 1] = "tower_se";
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
