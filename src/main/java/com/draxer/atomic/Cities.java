package com.draxer.atomic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Las capitales: Midgar (512×512) y Oriana (256×256). Una estructura de ese tamaño no cabe en la
 * generación normal de Minecraft, así que cada ciudad se construye por cuadras de 32×32 a medida
 * que los jugadores cargan el terreno a su alrededor.
 * <ul>
 *   <li>Los sitios se eligen con la semilla del mundo: una región de {@code spacing} bloques por
 *   ciudad, en una posición pseudoaleatoria, y solo si el bioma lo permite.</li>
 *   <li>Cada cuadra limpia el terreno hasta 40 bloques por encima de la altura base de la ciudad,
 *   rellena los cimientos hacia abajo y coloca su plantilla (con aldeanos incluidos).</li>
 *   <li>Lo ya construido se guarda en el mundo (attachment del overworld).</li>
 * </ul>
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class Cities {
    private Cities() {}

    static final TagKey<Biome> MIDGAR_BIOMES = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "has_midgar_capital"));
    static final TagKey<Biome> SANCTUARY_BIOMES = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "has_sanctuary"));
    static final TagKey<Biome> LAWLESS_BIOMES = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "has_lawless_city"));
    static final TagKey<Biome> ORIANA_BIOMES = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "has_oriana_capital"));

    private static final int MAX_FOUNDATION = 24;

    // ---------------------------------------------------------------- estado guardado

    /** baseY = altura del suelo de la ciudad (Integer.MIN_VALUE = sin decidir); placed = cuadras hechas. */
    public record CityState(int baseY, List<Integer> placed) {
        static final Codec<CityState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("base_y").forGetter(CityState::baseY),
                Codec.INT.listOf().fieldOf("placed").forGetter(CityState::placed)).apply(i, CityState::new));
    }

    public record State(Map<String, CityState> cities) {
        static final State EMPTY = new State(Map.of());
        static final com.mojang.serialization.MapCodec<State> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.unboundedMap(Codec.STRING, CityState.CODEC).fieldOf("cities").forGetter(State::cities)).apply(i, State::new));
    }

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<State>> STATE = Mana.ATTACHMENTS.register("cities",
            () -> AttachmentType.builder(() -> State.EMPTY).serialize(State.CODEC).build());

    static void init(IEventBus modEventBus) {}

    // ---------------------------------------------------------------- sitios

    /** Una ciudad concreta del mundo. */
    public record Site(CityLayout.Kind kind, int regionX, int regionZ, int centerX, int centerZ, long seed) {
        public String key() {
            return kind.style + ":" + regionX + ":" + regionZ;
        }

        public int minX() {
            return centerX - kind.size() / 2;
        }

        public int minZ() {
            return centerZ - kind.size() / 2;
        }
    }

    private static int spacing(CityLayout.Kind kind) {
        return switch (kind) {
            case MIDGAR -> AtomicConfig.midgarSpacing();
            case ORIANA -> AtomicConfig.orianaSpacing();
            case SANCTUARY -> AtomicConfig.sanctuarySpacing();
            case LAWLESS -> AtomicConfig.lawlessSpacing();
        };
    }

    private static long salt(CityLayout.Kind kind) {
        return switch (kind) {
            case MIDGAR -> 0x5A11D6A2L;
            case ORIANA -> 0x0121A4AL;
            case SANCTUARY -> 0x5A4C7A11L;
            case LAWLESS -> 0x1A31E55CL;
        };
    }

    /** El sitio candidato de la región (rx, rz), haya o no ciudad (el bioma se mira aparte). */
    static Site candidate(ServerLevel level, CityLayout.Kind kind, int rx, int rz) {
        int spacing = spacing(kind);
        long seed = level.getSeed() ^ salt(kind) ^ (rx * 341873128712L) ^ (rz * 132897987541L);
        RandomSource random = RandomSource.create(seed);
        int margin = kind.size() / 2 + 64;
        int span = Math.max(1, spacing - 2 * margin);
        int x = rx * spacing + margin + random.nextInt(span);
        int z = rz * spacing + margin + random.nextInt(span);
        // Alinea a chunks para que las cuadras empiecen en el borde de un chunk.
        x = Math.floorDiv(x, 16) * 16;
        z = Math.floorDiv(z, 16) * 16;
        return new Site(kind, rx, rz, x, z, seed);
    }

    private static final Map<String, Boolean> VALID = new HashMap<>();

    /** ¿El terreno de la región sirve para esta ciudad? Mira el bioma en el centro y en 8 puntos alrededor. */
    static boolean valid(ServerLevel level, Site site) {
        String key = level.getSeed() + "/" + site.key();
        Boolean known = VALID.get(key);
        if (known != null) return known;
        boolean ok = computeValid(level, site);
        VALID.put(key, ok);
        return ok;
    }

    private static boolean computeValid(ServerLevel level, Site site) {
        {
            TagKey<Biome> tag = site.kind() == CityLayout.Kind.MIDGAR ? MIDGAR_BIOMES
                    : site.kind() == CityLayout.Kind.SANCTUARY ? SANCTUARY_BIOMES
                    : site.kind() == CityLayout.Kind.LAWLESS ? LAWLESS_BIOMES : ORIANA_BIOMES;
            TagKey<Biome> ocean = TagKey.create(Registries.BIOME, Identifier.withDefaultNamespace("is_ocean"));
            int r = site.kind().size() / 2 - 16;
            int y = level.getSeaLevel();
            int good = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = site.centerX() + dx * r;
                    int z = site.centerZ() + dz * r;
                    Holder<Biome> biome = level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2);
                    if (biome.is(ocean)) return false;
                    boolean ok = biome.is(tag);
                    if (dx == 0 && dz == 0 && !ok) return false; // el centro siempre en un bioma apto
                    if (ok) good++;
                }
            }
            // Basta con que la mayor parte del terreno sea apto (el resto se aplana igual).
            if (good < 5) return false;
            // Oriana no se pone encima de una Midgar; el Santuario, sobre ninguna de las dos.
            for (CityLayout.Kind other : CityLayout.Kind.values()) {
                if (other.ordinal() >= site.kind().ordinal()) break;
                // Oriana conserva la regla original (cualquier candidata de Midgar a menos de 400) para no mover las ya generadas.
                boolean legacy = site.kind() == CityLayout.Kind.ORIANA;
                int gap = legacy ? 400 : (site.kind().size() + other.size()) / 2 + 150;
                for (Site o : sitesNear(level, other, site.centerX(), site.centerZ(), gap + 200, false)) {
                    if (Math.abs(o.centerX() - site.centerX()) < gap && Math.abs(o.centerZ() - site.centerZ()) < gap
                            && (legacy || valid(level, o))) return false;
                }
            }
            return true;
        }
    }

    /** Sitios de este tipo cuyas regiones están a menos de {@code radius} bloques de (x, z). */
    static List<Site> sitesNear(ServerLevel level, CityLayout.Kind kind, int x, int z, int radius, boolean onlyValid) {
        int spacing = spacing(kind);
        List<Site> out = new ArrayList<>();
        for (int rx = Math.floorDiv(x - radius, spacing); rx <= Math.floorDiv(x + radius, spacing); rx++) {
            for (int rz = Math.floorDiv(z - radius, spacing); rz <= Math.floorDiv(z + radius, spacing); rz++) {
                Site site = candidate(level, kind, rx, rz);
                if (!onlyValid || valid(level, site)) out.add(site);
            }
        }
        return out;
    }

    /** La ciudad válida más cercana (busca hasta {@code regions} regiones alrededor). */
    public static Optional<Site> nearest(ServerLevel level, CityLayout.Kind kind, BlockPos from, int regions) {
        int spacing = spacing(kind);
        Site best = null;
        double bestDist = Double.MAX_VALUE;
        int crx = Math.floorDiv(from.getX(), spacing);
        int crz = Math.floorDiv(from.getZ(), spacing);
        for (int rx = crx - regions; rx <= crx + regions; rx++) {
            for (int rz = crz - regions; rz <= crz + regions; rz++) {
                Site site = candidate(level, kind, rx, rz);
                double d = Math.pow(site.centerX() - from.getX(), 2) + Math.pow(site.centerZ() - from.getZ(), 2);
                if (d < bestDist && valid(level, site)) {
                    best = site;
                    bestDist = d;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** La Ciudad sin Ley en la que está esta posición (muralla incluida), si hay una construida. */
    public static Optional<Site> lawlessAt(ServerLevel level, double x, double z) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD || !AtomicConfig.cities()) return Optional.empty();
        int half = CityLayout.Kind.LAWLESS.size() / 2 + 3;
        for (Site site : sitesNear(level, CityLayout.Kind.LAWLESS, (int) x, (int) z, 140, true)) {
            if (Math.abs(x - site.centerX()) <= half && Math.abs(z - site.centerZ()) <= half && built(level, site)) return Optional.of(site);
        }
        return Optional.empty();
    }

    /** Dónde despierta Elizabeth: junto a su ataúd, en lo alto de la Torre Carmesí. */
    public static Vec3 coffin(ServerLevel level, Site site) {
        int baseY = level.getData(STATE).cities().get(site.key()).baseY();
        return new Vec3(site.minX() + CityLayout.CELL + 31.5, baseY + 52, site.minZ() + CityLayout.CELL + 36.5);
    }

    /** ¿Está dentro de un Santuario (muralla incluida)? Lo usan el maná y la compresión. */
    public static boolean inSanctuary(ServerLevel level, net.minecraft.world.entity.Entity entity) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD || !AtomicConfig.cities()) return false;
        int half = CityLayout.Kind.SANCTUARY.size() / 2 + 3;
        for (Site site : sitesNear(level, CityLayout.Kind.SANCTUARY, entity.getBlockX(), entity.getBlockZ(), 80, true)) {
            if (Math.abs(entity.getX() - site.centerX()) <= half && Math.abs(entity.getZ() - site.centerZ()) <= half
                    && built(level, site) && entity.getY() > level.getData(STATE).cities().get(site.key()).baseY() - 8) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- construcción

    /** Marca en {@code placed} de que la muralla ya está construida. */
    private static final int WALLS_DONE = -1;
    /** Bloques alrededor de la ciudad que se generan antes de construir (para que nada la pise después). */
    private static final int MARGIN = 48;

    /** Capitales que un servidor dedicado construye de a poco desde el arranque (sin esperar jugadores). */
    private static final List<Site> STARTUP = new ArrayList<>();

    /**
     * Al crear o abrir el mundo: las capitales más cercanas al origen quedan construidas enteras.
     * En un mundo de un jugador se construyen de una vez antes de entrar; en un servidor dedicado se
     * construyen en los primeros ticks, de a poco, para que el vigilante del servidor no lo dé por colgado.
     */
    @SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        STARTUP.clear();
        if (!AtomicConfig.cities()) return;
        ServerLevel level = event.getServer().overworld();
        boolean dedicated = event.getServer().isDedicatedServer();
        for (CityLayout.Kind kind : CityLayout.Kind.values()) {
            nearest(level, kind, BlockPos.ZERO, 2).ifPresent(site -> {
                if (complete(level, site)) return;
                if (dedicated) {
                    STARTUP.add(site);
                    return;
                }
                long t = System.currentTimeMillis();
                int cells = buildAll(level, site);
                com.mojang.logging.LogUtils.getLogger().info("Drax Atomic: capital {} construida en X {} Z {} ({} cuadras, {} ms)",
                        kind.style, site.centerX(), site.centerZ(), cells, System.currentTimeMillis() - t);
            });
        }
    }

    /** En juego: si un jugador se acerca a otra capital, se genera su terreno y se construye entera. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!AtomicConfig.cities()) return;
        ServerLevel level = event.getServer().overworld();
        if (!STARTUP.isEmpty()) {
            Site site = STARTUP.get(0);
            if (complete(level, site)) {
                STARTUP.remove(0);
                com.mojang.logging.LogUtils.getLogger().info("Drax Atomic: capital {} construida en X {} Z {}",
                        site.kind().style, site.centerX(), site.centerZ());
            } else if (prepare(level, site, 16)) {
                if (buildSome(level, site, 2) == 0) buildWalls(level, site);
            }
        }
        if (level.getGameTime() % 5 != 0 || level.players().isEmpty()) return;
        for (ServerPlayer player : level.players()) {
            for (CityLayout.Kind kind : CityLayout.Kind.values()) {
                int reach = kind.size() / 2 + 256;
                for (Site site : sitesNear(level, kind, player.getBlockX(), player.getBlockZ(), reach, true)) {
                    if (Math.abs(site.centerX() - player.getX()) > reach || Math.abs(site.centerZ() - player.getZ()) > reach) continue;
                    if (complete(level, site)) continue;
                    if (!prepare(level, site, 12)) return;        // primero el terreno, de a poco
                    if (buildSome(level, site, 3) > 0) return;     // luego unas cuadras por pasada
                    buildWalls(level, site);
                    return;
                }
            }
        }
    }

    private static boolean built(ServerLevel level, Site site) {
        CityState city = level.getData(STATE).cities().get(site.key());
        return city != null && city.baseY() != Integer.MIN_VALUE;
    }

    public static boolean complete(ServerLevel level, Site site) {
        CityState city = level.getData(STATE).cities().get(site.key());
        return city != null && city.placed().contains(WALLS_DONE);
    }

    /** Chunks que el constructor mantiene cargados (se sueltan al terminar la ciudad). */
    private static final Map<String, java.util.Set<Long>> FORCED = new HashMap<>();

    /**
     * Pide (sin bloquear) hasta {@code budget} chunks del área de la ciudad y su margen; true cuando
     * ya están todos cargados. Así el servidor nunca se queda esperando a que se genere un chunk.
     */
    static boolean prepare(ServerLevel level, Site site, int budget) {
        int x0 = (site.minX() - MARGIN) >> 4;
        int z0 = (site.minZ() - MARGIN) >> 4;
        int x1 = (site.minX() + site.kind().size() + MARGIN) >> 4;
        int z1 = (site.minZ() + site.kind().size() + MARGIN) >> 4;
        java.util.Set<Long> forced = FORCED.computeIfAbsent(site.key(), k -> new java.util.HashSet<>());
        boolean ready = true;
        int requested = 0;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (level.hasChunk(cx, cz)) continue;
                ready = false;
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                if (requested < budget && forced.add(key)) {
                    level.setChunkForced(cx, cz, true);
                    requested++;
                }
            }
        }
        return ready;
    }

    /** Suelta los chunks que se mantuvieron cargados para construir esta ciudad. */
    private static void release(ServerLevel level, Site site) {
        java.util.Set<Long> forced = FORCED.remove(site.key());
        if (forced == null) return;
        for (long key : forced) {
            level.setChunkForced((int) (key >> 32), (int) key, false);
        }
    }

    /** Versión que bloquea hasta generar todo (al crear un mundo de un jugador y con el comando). */
    private static void prepareNow(ServerLevel level, Site site) {
        int x0 = (site.minX() - MARGIN) >> 4;
        int z0 = (site.minZ() - MARGIN) >> 4;
        int x1 = (site.minX() + site.kind().size() + MARGIN) >> 4;
        int z1 = (site.minZ() + site.kind().size() + MARGIN) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) level.getChunk(cx, cz);
        }
    }

    /** Construye la ciudad completa de una vez (terreno, cuadras y muralla). */
    public static int buildAll(ServerLevel level, Site site) {
        prepareNow(level, site);
        int total = 0;
        int step;
        do {
            step = buildSome(level, site, 64);
            total += step;
        } while (step > 0);
        buildWalls(level, site);
        return total;
    }

    /** Coloca hasta {@code budget} cuadras pendientes; devuelve cuántas colocó. */
    static int buildSome(ServerLevel level, Site site, int budget) {
        State state = level.getData(STATE);
        CityState city = state.cities().get(site.key());
        CityLayout layout = new CityLayout(site.kind(), site.seed());
        int n = site.kind().cells;
        java.util.Set<Integer> placed = city == null ? new java.util.HashSet<>() : new java.util.HashSet<>(city.placed());
        int baseY = city == null ? Integer.MIN_VALUE : city.baseY();
        int done = 0;
        for (int i = 0; i < n && done < budget; i++) {
            for (int j = 0; j < n && done < budget; j++) {
                String template = layout.template(i, j);
                if (template == null || placed.contains(i * n + j)) continue;
                int size = layout.span(i, j) * CityLayout.CELL;
                if (baseY == Integer.MIN_VALUE) baseY = baseHeight(level, site);
                placeCell(level, template, site.minX() + i * CityLayout.CELL, baseY, site.minZ() + j * CityLayout.CELL, size,
                        site.seed() ^ (i * 31L + j));
                placed.add(i * n + j);
                done++;
            }
        }
        if (done > 0) save(level, site, baseY, placed);
        return done;
    }

    private static void save(ServerLevel level, Site site, int baseY, java.util.Set<Integer> placed) {
        Map<String, CityState> cities = new HashMap<>(level.getData(STATE).cities());
        cities.put(site.key(), new CityState(baseY, new ArrayList<>(placed)));
        level.setData(STATE, new State(cities));
    }

    private static int cellCount(CityLayout layout) {
        int n = layout.kind().cells;
        int count = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (layout.template(i, j) != null) count++;
        return count;
    }

    // ---------------------------------------------------------------- muralla

    /** Muralla de 3 de grosor alrededor de la cuadrícula, torres en las esquinas, cuatro puertas y una explanada. */
    static void buildWalls(ServerLevel level, Site site) {
        CityState city = level.getData(STATE).cities().get(site.key());
        if (city == null || city.placed().contains(WALLS_DONE)) return;
        int baseY = city.baseY();
        boolean midgar = site.kind() == CityLayout.Kind.MIDGAR;
        boolean lawless = site.kind() == CityLayout.Kind.LAWLESS;
        BlockState wall = (midgar ? Blocks.STONE_BRICKS : lawless ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.QUARTZ_BRICKS).defaultBlockState();
        BlockState wall2 = (midgar ? Blocks.CRACKED_STONE_BRICKS : lawless ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.SMOOTH_QUARTZ).defaultBlockState();
        BlockState cap = (midgar ? Blocks.CHISELED_STONE_BRICKS : lawless ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.CHISELED_QUARTZ_BLOCK).defaultBlockState();
        BlockState roof = (midgar ? Blocks.DEEPSLATE_TILES : lawless ? Blocks.NETHER_BRICKS : Blocks.DARK_PRISMARINE).defaultBlockState();
        BlockState road = (midgar ? Blocks.STONE_BRICKS : lawless ? Blocks.COBBLED_DEEPSLATE : Blocks.POLISHED_ANDESITE).defaultBlockState();
        BlockState grass = (lawless ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK).defaultBlockState();
        BlockState lantern = (lawless ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState();
        int height = midgar || lawless ? 10 : 8;
        int size = site.kind().size();
        int x0 = site.minX();
        int z0 = site.minZ();
        int x1 = x0 + size - 1;
        int z1 = z0 + size - 1;
        int gate = site.kind().cells / 2 * CityLayout.CELL;   // coincide con la calle de esa columna/fila
        int apron = 10;
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        RandomSource random = RandomSource.create(site.seed());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0 - 3 - apron; x <= x1 + 3 + apron; x++) {
            for (int z = z0 - 3 - apron; z <= z1 + 3 + apron; z++) {
                if (x >= x0 && x <= x1 && z >= z0 && z <= z1) continue;
                int out = Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1)); // 1..3 = muralla
                boolean onWall = out >= 1 && out <= 3;
                // Terreno: limpia arriba y rellena abajo, igual que las cuadras.
                clearColumn(level, pos, x, z, baseY, flags);
                int along = (x < x0 || x > x1) ? z - z0 : x - x0;     // posición a lo largo del lado
                boolean inGate = along >= gate - 1 && along <= gate + 4;
                boolean nearGate = along >= gate - 3 && along <= gate + 6;
                level.setBlock(pos.set(x, baseY, z), inGate || (!onWall && nearGate) ? road : onWall ? wall : grass, flags);
                if (!onWall) continue;
                for (int y = 1; y <= height; y++) {
                    if (inGate && y <= 6) continue;
                    level.setBlock(pos.set(x, baseY + y, z), random.nextInt(6) == 0 ? wall2 : wall, flags);
                }
                // Almenas en el borde exterior.
                if (out == 3 && Math.floorMod(along, 2) == 0) level.setBlock(pos.set(x, baseY + height + 1, z), cap, flags);
                if (inGate && out == 2) level.setBlock(pos.set(x, baseY + 7, z), cap, flags);
                if (out == 1 && nearGate && !inGate && (along == gate - 2 || along == gate + 5)) {
                    level.setBlock(pos.set(x, baseY + height + 1, z), lantern, flags);
                }
            }
        }
        // Torres en las esquinas (7×7, centradas en la muralla).
        for (int[] c : new int[][] {{x0 - 2, z0 - 2}, {x1 + 2, z0 - 2}, {x0 - 2, z1 + 2}, {x1 + 2, z1 + 2}}) {
            int top = height + 8;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    for (int y = 1; y <= top; y++) {
                        BlockState st = edge ? wall : Blocks.AIR.defaultBlockState();
                        if (edge && y % 5 == 3 && (dx == 0 || dz == 0)) st = Blocks.GLASS_PANE.defaultBlockState();
                        level.setBlock(pos.set(c[0] + dx, baseY + y, c[1] + dz), st, flags);
                    }
                    level.setBlock(pos.set(c[0] + dx, baseY + top + 1, c[1] + dz), cap, flags);
                }
            }
            for (int k = 0; k <= 4; k++) {
                for (int dx = -4 + k; dx <= 4 - k; dx++) {
                    for (int dz = -4 + k; dz <= 4 - k; dz++) {
                        level.setBlock(pos.set(c[0] + dx, baseY + top + 2 + k, c[1] + dz), roof, flags);
                    }
                }
            }
            level.setBlock(pos.set(c[0], baseY + top + 7, c[1]), Blocks.GOLD_BLOCK.defaultBlockState(), flags);
            level.setBlock(pos.set(c[0], baseY + top - 1, c[1]), Blocks.LANTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), flags);
        }
        java.util.Set<Integer> placed = new java.util.HashSet<>(city.placed());
        placed.add(WALLS_DONE);
        save(level, site, baseY, placed);
        release(level, site);
    }

    /** Quita todo lo que haya sobre {@code baseY} y rellena con tierra/piedra lo que falte debajo. */
    private static void clearColumn(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z, int baseY, int flags) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (int y = surface; y > baseY; y--) {
            pos.set(x, y, z);
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
        }
        for (int y = baseY - 1, depth = 0; y > level.getMinY() && depth < MAX_FOUNDATION; y--, depth++) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) break;
            level.setBlock(pos, (depth < 3 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), flags);
        }
    }

    /** Altura del suelo: la mediana de 25 puntos del terreno generado dentro de la muralla. */
    static int baseHeight(ServerLevel level, Site site) {
        List<Integer> heights = new ArrayList<>();
        int half = site.kind().size() / 2 - 32;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = site.centerX() + dx * half / 2;
                int z = site.centerZ() + dz * half / 2;
                heights.add(level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                        level, level.getChunkSource().randomState()));
            }
        }
        heights.sort(Integer::compare);
        return Math.max(level.getSeaLevel() + 1, heights.get(heights.size() / 2));
    }

    /** Limpia el terreno, pone cimientos y coloca la plantilla de una cuadra. */
    static void placeCell(ServerLevel level, String template, int x0, int baseY, int z0, int size, long seed) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) clearColumn(level, pos, x, z, baseY, flags);
        }
        Identifier id = Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, template);
        Optional<StructureTemplate> found = level.getStructureManager().get(id);
        if (found.isEmpty()) {
            com.mojang.logging.LogUtils.getLogger().warn("Drax Atomic: falta la plantilla {}", id);
            return;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings();
        BlockPos at = new BlockPos(x0, baseY, z0);
        found.get().placeInWorld(level, at, at, settings, RandomSource.create(seed), Block.UPDATE_CLIENTS);
    }

    /** Para el comando y la autoprueba. */
    public static int buildLoaded(ServerLevel level, Site site) {
        return buildAll(level, site);
    }

    /** Altura base de la ciudad (la calcula si aún no se decidió). */
    public static int baseY(ServerLevel level, Site site) {
        CityState city = level.getData(STATE).cities().get(site.key());
        return city == null || city.baseY() == Integer.MIN_VALUE ? baseHeight(level, site) : city.baseY();
    }

    public static int progress(ServerLevel level, Site site) {
        CityState city = level.getData(STATE).cities().get(site.key());
        return city == null ? 0 : city.placed().size();
    }

    public static int total(Site site) {
        return cellCount(new CityLayout(site.kind(), site.seed()));
    }
}
