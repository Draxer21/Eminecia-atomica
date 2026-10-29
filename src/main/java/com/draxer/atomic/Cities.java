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
    static final TagKey<Biome> ORIANA_BIOMES = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "has_oriana_capital"));

    private static final int CLEAR_ABOVE = 40;
    private static final int MAX_FOUNDATION = 24;
    private static final int CELLS_PER_PASS = 2;

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
        return kind == CityLayout.Kind.MIDGAR ? AtomicConfig.midgarSpacing() : AtomicConfig.orianaSpacing();
    }

    private static long salt(CityLayout.Kind kind) {
        return kind == CityLayout.Kind.MIDGAR ? 0x5A11D6A2L : 0x0121A4AL;
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
        return VALID.computeIfAbsent(level.getSeed() + "/" + site.key(), k -> {
            TagKey<Biome> tag = site.kind() == CityLayout.Kind.MIDGAR ? MIDGAR_BIOMES : ORIANA_BIOMES;
            int r = site.kind().size() / 2 - 16;
            int y = level.getSeaLevel();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = site.centerX() + dx * r;
                    int z = site.centerZ() + dz * r;
                    Holder<Biome> biome = level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2);
                    if (!biome.is(tag)) return false;
                }
            }
            // Oriana no se pone encima de una Midgar.
            if (site.kind() == CityLayout.Kind.ORIANA) {
                for (Site other : sitesNear(level, CityLayout.Kind.MIDGAR, site.centerX(), site.centerZ(), 900, false)) {
                    if (Math.abs(other.centerX() - site.centerX()) < 700 && Math.abs(other.centerZ() - site.centerZ()) < 700) return false;
                }
            }
            return true;
        });
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

    // ---------------------------------------------------------------- construcción

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!AtomicConfig.cities()) return;
        ServerLevel level = event.getServer().overworld();
        if (level.getGameTime() % 10 != 0 || level.players().isEmpty()) return;
        int budget = CELLS_PER_PASS;
        for (ServerPlayer player : level.players()) {
            for (CityLayout.Kind kind : CityLayout.Kind.values()) {
                int reach = kind.size() / 2 + 256;
                for (Site site : sitesNear(level, kind, player.getBlockX(), player.getBlockZ(), reach, true)) {
                    if (Math.abs(site.centerX() - player.getX()) > reach || Math.abs(site.centerZ() - player.getZ()) > reach) continue;
                    budget -= buildSome(level, site, budget);
                    if (budget <= 0) return;
                }
            }
        }
    }

    /** Coloca hasta {@code budget} cuadras listas de la ciudad; devuelve cuántas colocó. */
    static int buildSome(ServerLevel level, Site site, int budget) {
        State state = level.getData(STATE);
        CityState city = state.cities().get(site.key());
        CityLayout layout = new CityLayout(site.kind(), site.seed());
        int n = site.kind().cells;
        if (city != null && city.placed().size() >= cellCount(layout)) return 0;
        java.util.Set<Integer> placed = city == null ? new java.util.HashSet<>() : new java.util.HashSet<>(city.placed());
        int baseY = city == null ? Integer.MIN_VALUE : city.baseY();
        int done = 0;
        for (int i = 0; i < n && done < budget; i++) {
            for (int j = 0; j < n && done < budget; j++) {
                String template = layout.template(i, j);
                if (template == null || placed.contains(i * n + j)) continue;
                int span = layout.span(i, j);
                int x0 = site.minX() + i * CityLayout.CELL;
                int z0 = site.minZ() + j * CityLayout.CELL;
                int size = span * CityLayout.CELL;
                if (!areaLoaded(level, x0 - 2, z0 - 2, x0 + size + 2, z0 + size + 2)) continue;
                if (baseY == Integer.MIN_VALUE) baseY = baseHeight(level, site);
                placeCell(level, template, x0, baseY, z0, size, site.seed() ^ (i * 31L + j));
                placed.add(i * n + j);
                done++;
            }
        }
        if (done > 0) {
            Map<String, CityState> cities = new HashMap<>(state.cities());
            cities.put(site.key(), new CityState(baseY, new ArrayList<>(placed)));
            level.setData(STATE, new State(cities));
        }
        return done;
    }

    private static int cellCount(CityLayout layout) {
        int n = layout.kind().cells;
        int count = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (layout.template(i, j) != null) count++;
        return count;
    }

    private static boolean areaLoaded(ServerLevel level, int x0, int z0, int x1, int z1) {
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return false;
            }
        }
        return true;
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
        int top = Math.min(level.getMaxY() - 1, baseY + CLEAR_ABOVE);
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                // Arriba: fuera colinas, árboles y agua.
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = baseY + 1; y <= Math.min(top, surface); y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, air, flags);
                }
                // Abajo: cimientos hasta tocar terreno firme.
                for (int y = baseY - 1, depth = 0; y > level.getMinY() && depth < MAX_FOUNDATION; y--, depth++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) break;
                    level.setBlock(pos, depth < 3 ? dirt : stone, flags);
                }
            }
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

    /** Para el comando y la autoprueba: construye ya todas las cuadras cuyo terreno esté cargado. */
    public static int buildLoaded(ServerLevel level, Site site) {
        int total = 0;
        int step;
        do {
            step = buildSome(level, site, 64);
            total += step;
        } while (step > 0);
        return total;
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
