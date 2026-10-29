"""Genera las estructuras .nbt de Drax Atomic (tienda Mitsugoshi y capital de Midgar).

Uso: python3 tools/structures.py   (requiere: pip install nbtlib)
"""
import os, random
import nbtlib
from nbtlib import Compound, List, Int, String, Double, Byte

OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/data/draxatomic/structure')
DATA_VERSION = 4189


class Build:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}
        self.nbt = {}
        self.entities = []

    def set(self, x, y, z, name, **props):
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]:
            self.blocks[(x, y, z)] = ('minecraft:' + name if ':' not in name else name,
                                      tuple(sorted((k, str(v).lower()) for k, v in props.items())))

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def box(self, x0, y0, z0, x1, y1, z1, name, **props):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, name, **props)

    def walls(self, x0, y0, z0, x1, y1, z1, name, **props):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1):
                        self.set(x, y, z, name, **props)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.box(x0, y0, z0, x1, y1, z1, 'air')

    def door(self, x, y, z, wood='oak', facing='north', hinge='left'):
        for half, dy in (('lower', 0), ('upper', 1)):
            self.set(x, y + dy, z, f'{wood}_door', facing=facing, half=half, hinge=hinge, open='false', powered='false')

    def villager(self, x, y, z, profession, vtype='plains', name=None):
        nbt = {'id': String('minecraft:villager'), 'PersistenceRequired': Byte(1),
               'VillagerData': Compound({'profession': String(profession), 'type': String('minecraft:' + vtype), 'level': Int(1)})}
        if name:
            nbt['CustomName'] = String(name)
        self.entities.append(Compound({
            'pos': List[Double]([Double(x + 0.5), Double(y), Double(z + 0.5)]),
            'blockPos': List[Int]([Int(x), Int(y), Int(z)]),
            'nbt': Compound(nbt)}))

    def save(self, name):
        palette, index = [], {}
        blocks = []
        for pos, (bname, props) in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            key = (bname, props)
            if key not in index:
                entry = {'Name': String(bname)}
                if props:
                    entry['Properties'] = Compound({k: String(v) for k, v in props})
                index[key] = len(palette)
                palette.append(Compound(entry))
            b = {'pos': List[Int]([Int(c) for c in pos]), 'state': Int(index[key])}
            if pos in self.nbt:
                b['nbt'] = self.nbt[pos]
            blocks.append(Compound(b))
        root = Compound({
            'DataVersion': Int(DATA_VERSION),
            'size': List[Int]([Int(c) for c in self.size]),
            'palette': List[Compound](palette),
            'blocks': List[Compound](blocks),
            'entities': List[Compound](self.entities)})
        os.makedirs(OUT, exist_ok=True)
        nbtlib.File(root).save(os.path.join(OUT, name + '.nbt'), gzipped=True)
        print(name, self.size, len(blocks), 'bloques', len(palette), 'estados')


# ------------------------------------------------------------------ tienda Mitsugoshi

def store(b, ox, oz, facing_door='north', with_clerk=True):
    """Tienda de 13x11 (x por z) con frente al norte (z=oz). Altura 10."""
    X0, Z0, X1, Z1 = ox, oz, ox + 12, oz + 10
    # cimientos y piso
    b.box(X0, 0, Z0, X1, 0, Z1, 'polished_andesite')
    b.box(X0 + 1, 1, Z0 + 1, X1 - 1, 1, Z1 - 1, 'white_concrete')
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            if (x + z) % 2 == 0:
                b.set(x, 1, z, 'smooth_quartz')
    # paredes de cuarzo con pilares de oro/cuarzo
    b.walls(X0, 1, Z0, X1, 6, Z1, 'quartz_bricks')
    for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
        b.box(x, 1, z, x, 7, z, 'quartz_pillar', axis='y')
    for x in (X0 + 4, X1 - 4):
        b.box(x, 1, Z0, x, 6, Z0, 'quartz_pillar', axis='y')
    # vitrinas (ventanas) al frente y a los lados
    for x in list(range(X0 + 1, X0 + 4)) + list(range(X1 - 3, X1)):
        b.box(x, 2, Z0, x, 5, Z0, 'glass')
    for z in range(Z0 + 2, Z1 - 1, 3):
        b.box(X0, 3, z, X0, 5, z + 1, 'glass')
        b.box(X1, 3, z, X1, 5, z + 1, 'glass')
    # entrada doble con marco dorado
    b.clear(X0 + 5, 2, Z0, X0 + 7, 4, Z0)
    b.door(X0 + 5, 2, Z0, 'dark_oak', 'north', 'left')
    b.door(X0 + 7, 2, Z0, 'dark_oak', 'north', 'right')
    b.set(X0 + 6, 2, Z0, 'quartz_pillar', axis='y'); b.set(X0 + 6, 3, Z0, 'quartz_pillar', axis='y')
    b.box(X0 + 4, 5, Z0, X1 - 4, 5, Z0, 'gold_block')
    # techo plano con parapeto
    b.box(X0, 7, Z0, X1, 7, Z1, 'quartz_bricks')
    b.walls(X0, 8, Z0, X1, 8, Z1, 'quartz_slab', type='bottom', waterlogged='false')
    # letrero: una "M" de oro sobre la entrada, encima del parapeto
    b.box(X0 + 3, 8, Z0, X1 - 3, 8, Z0, 'quartz_bricks')
    for dx, dy in ((0, 0), (0, 1), (0, 2), (4, 0), (4, 1), (4, 2), (1, 2), (3, 2), (2, 1)):
        b.set(X0 + 4 + dx, 8 + dy, Z0, 'gold_block')
    # iluminación
    for x in (X0 + 3, X0 + 6, X0 + 9):
        for z in (Z0 + 3, Z0 + 7):
            b.set(x, 6, z, 'lantern', hanging='true', waterlogged='false')
            b.set(x, 7, z, 'quartz_bricks')
        # mostrador al fondo con la caja (puesto de trabajo del empleado)
    for x in range(X0 + 3, X1 - 2):
        b.set(x, 2, Z1 - 3, 'dark_oak_planks')
    b.set(X0 + 6, 2, Z1 - 3, 'draxatomic:mitsugoshi_counter')
    for x in (X0 + 3, X1 - 3):
        b.set(x, 3, Z1 - 3, 'potted_white_tulip') if False else b.set(x, 3, Z1 - 3, 'lantern', hanging='false', waterlogged='false')
    # estanterías y vitrinas laterales
    for z in range(Z0 + 2, Z1 - 4):
        b.set(X0 + 1, 2, z, 'chiseled_bookshelf', facing='east', **{f'slot_{i}_occupied': 'false' for i in range(6)})
        b.set(X1 - 1, 2, z, 'barrel', facing='up', open='false')
        b.set(X1 - 1, 3, z, 'cake', bites='0') if z % 2 == 0 else b.set(X1 - 1, 3, z, 'brown_carpet')
    # alfombra roja hacia el mostrador
    for z in range(Z0 + 1, Z1 - 3):
        b.set(X0 + 6, 2, z, 'red_carpet')
    for x in (X0 + 5, X0 + 7):
        for z in range(Z0 + 2, Z1 - 3):
            b.set(x, 2, z, 'yellow_carpet') if z == Z0 + 2 else None
    # aire interior
    for x in range(X0 + 1, X1):
        for y in range(2, 7):
            for z in range(Z0 + 1, Z1):
                if (x, y, z) not in b.blocks:
                    b.set(x, y, z, 'air')
    if with_clerk:
        b.villager(X0 + 6, 2, Z1 - 2, 'draxatomic:mitsugoshi_clerk')


def build_store():
    b = Build(13, 11, 11)
    store(b, 0, 0)
    b.save('mitsugoshi_store')


# ------------------------------------------------------------------ capital de Midgar

def house(b, x0, z0, w, d, wall, roof, rnd, door_side='north'):
    x1, z1 = x0 + w - 1, z0 + d - 1
    b.box(x0, 1, z0, x1, 1, z1, 'spruce_planks')
    b.walls(x0, 2, z0, x1, 4, z1, wall)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.box(x, 2, z, x, 4, z, 'dark_oak_log', axis='y')
    b.box(x0 + 1, 2, z0 + 1, x1 - 1, 4, z1 - 1, 'air')
    # ventanas
    for x in range(x0 + 2, x1 - 1, 2):
        b.set(x, 3, z0, 'glass'); b.set(x, 3, z1, 'glass')
    for z in range(z0 + 2, z1 - 1, 2):
        b.set(x0, 3, z, 'glass'); b.set(x1, 3, z, 'glass')
    # techo a dos aguas (cumbrera a lo largo de x)
    half = (d + 1) // 2
    for i in range(half + 1):
        y = 5 + i
        za, zb = z0 - 1 + i, z1 + 1 - i
        for x in range(x0 - 1, x1 + 2):
            if za < zb:
                b.set(x, y, za, f'{roof}_stairs', facing='south', half='bottom', shape='straight', waterlogged='false')
                b.set(x, y, zb, f'{roof}_stairs', facing='north', half='bottom', shape='straight', waterlogged='false')
            elif za == zb:
                b.set(x, y, za, f'{roof}_slab', type='bottom', waterlogged='false')
        if za < zb:
            for z in range(za + 1, zb):
                b.set(x0, y, z, wall); b.set(x1, y, z, wall)
    # puerta
    dx = x0 + w // 2
    if door_side == 'north':
        b.door(dx, 2, z0, 'spruce', 'north')
        b.set(dx, 2, z0 - 1, 'air'); b.set(dx, 3, z0 - 1, 'air')
    else:
        b.door(dx, 2, z1, 'spruce', 'south')
    # interior: luz, mesa y cama
    b.set(x0 + 1, 2, z0 + 1, 'crafting_table')
    b.set(x1 - 1, 2, z1 - 1, 'barrel', facing='up', open='false')
    b.set(x0 + w // 2, 4, z0 + d // 2, 'lantern', hanging='true', waterlogged='false')
    bx = x0 + 1 if door_side == 'north' else x1 - 1
    b.set(bx, 2, z1 - 1 if door_side == 'north' else z0 + 1, 'red_bed', facing='north' if door_side == 'north' else 'south', part='head', occupied='false')
    b.set(bx, 2, z1 - 2 if door_side == 'north' else z0 + 2, 'red_bed', facing='north' if door_side == 'north' else 'south', part='foot', occupied='false')


def build_capital():
    S = 48
    H = 30
    b = Build(S, H, S)
    rnd = random.Random(7)
    # suelo: adoquines y piedra pulida
    for x in range(S):
        for z in range(S):
            b.set(x, 0, z, 'stone_bricks')
            b.set(x, 1, z, rnd.choice(['cobblestone', 'stone_bricks', 'andesite', 'stone_bricks']))
    # muralla con almenas y torres en las esquinas
    b.walls(0, 1, 0, S - 1, 6, S - 1, 'stone_bricks')
    for x in range(S):
        for z in (0, S - 1):
            if x % 2 == 0:
                b.set(x, 7, z, 'stone_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    for z in range(S):
        for x in (0, S - 1):
            if z % 2 == 0:
                b.set(x, 7, z, 'stone_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    for cx, cz in ((0, 0), (S - 4, 0), (0, S - 4), (S - 4, S - 4)):
        b.walls(cx, 1, cz, cx + 3, 10, cz + 3, 'stone_bricks')
        b.box(cx + 1, 2, cz + 1, cx + 2, 9, cz + 2, 'air')
        b.box(cx, 11, cz, cx + 3, 11, cz + 3, 'dark_prismarine')
        b.set(cx + 1, 12, cz + 1, 'lantern', hanging='false', waterlogged='false')
    # puertas de la ciudad (norte y sur) con arco
    for z in (0, S - 1):
        b.box(S // 2 - 2, 2, z, S // 2 + 1, 5, z, 'air')
        b.box(S // 2 - 3, 6, z, S // 2 + 2, 6, z, 'chiseled_stone_bricks')
        for x in (S // 2 - 3, S // 2 + 2):
            b.set(x, 5, z, 'lantern', hanging='false', waterlogged='false') if False else None
    # calle principal norte-sur y avenida este-oeste
    for z in range(1, S - 1):
        for x in range(S // 2 - 2, S // 2 + 2):
            b.set(x, 1, z, 'polished_andesite')
    for x in range(1, S - 1):
        for z in range(S // 2 - 2, S // 2 + 2):
            b.set(x, 1, z, 'polished_andesite')
    # plaza central con fuente
    c = S // 2
    for x in range(c - 6, c + 6):
        for z in range(c - 6, c + 6):
            if (x - c + 0.5) ** 2 + (z - c + 0.5) ** 2 <= 36:
                b.set(x, 1, z, 'smooth_stone' if (x + z) % 2 else 'polished_diorite')
    b.walls(c - 3, 2, c - 3, c + 2, 2, c + 2, 'stone_brick_slab', type='bottom', waterlogged='false')
    b.box(c - 2, 1, c - 2, c + 1, 1, c + 1, 'water', level='0')
    b.box(c - 1, 1, c - 1, c, 4, c, 'quartz_pillar', axis='y')
    b.box(c - 1, 5, c - 1, c, 5, c, 'water', level='0')
    b.set(c - 1, 6, c - 1, 'gold_block')
    # faroles a lo largo de las calles
    for z in range(4, S - 4, 6):
        for x in (c - 3, c + 2):
            if abs(z - c) > 6:
                b.box(x, 2, z, x, 3, z, 'dark_oak_fence', north='false', south='false', east='false', west='false', waterlogged='false')
                b.set(x, 4, z, 'lantern', hanging='false', waterlogged='false')
    for x in range(4, S - 4, 6):
        for z in (c - 3, c + 2):
            if abs(x - c) > 6:
                b.box(x, 2, z, x, 3, z, 'dark_oak_fence', north='false', south='false', east='false', west='false', waterlogged='false')
                b.set(x, 4, z, 'lantern', hanging='false', waterlogged='false')
    # barrios: casas en los cuatro cuadrantes
    walls_mats = ['white_terracotta', 'smooth_sandstone', 'calcite', 'white_concrete']
    roofs = ['dark_oak', 'spruce', 'deepslate_brick', 'brick']
    spots = [(5, 5, 'south'), (13, 5, 'south'), (5, 14, 'north'), (13, 14, 'north'),
             (S - 13, 5, 'south'), (S - 13, 14, 'north'),
             (5, S - 21, 'south'), (5, S - 12, 'north')]
    for i, (hx, hz, side) in enumerate(spots):
        house(b, hx, hz, 7, 6, walls_mats[i % 4], roofs[i % 4], rnd, side)
    # tienda Mitsugoshi (cuadrante noreste, al lado de la avenida)
    store(b, S - 17, S // 2 + 4)
    # catedral con torre (cuadrante sureste)
    cx0, cz0 = S - 18, 4
    b.box(cx0, 1, cz0, cx0 + 12, 1, cz0 + 14, 'polished_diorite')
    b.walls(cx0, 2, cz0, cx0 + 12, 9, cz0 + 14, 'calcite')
    b.box(cx0 + 1, 2, cz0 + 1, cx0 + 11, 9, cz0 + 13, 'air')
    for z in range(cz0 + 2, cz0 + 13, 3):
        b.box(cx0, 4, z, cx0, 7, z, 'purple_stained_glass')
        b.box(cx0 + 12, 4, z, cx0 + 12, 7, z, 'yellow_stained_glass')
    b.box(cx0, 10, cz0, cx0 + 12, 10, cz0 + 14, 'deepslate_tiles')
    for i in range(6):
        b.box(cx0 + i, 11 + i, cz0, cx0 + 12 - i, 11 + i, cz0 + 14, 'deepslate_tiles') if i < 6 else None
    for z in range(cz0 + 1, cz0 + 14):
        for i in range(6):
            b.box(cx0 + i + 1, 11 + i, z, cx0 + 11 - i, 11 + i, z, 'air') if cx0 + i + 1 <= cx0 + 11 - i and i < 5 else None
    # torre del campanario
    tx, tz = cx0 + 5, cz0 + 14
    b.walls(tx - 1, 2, tz - 1, tx + 3, 22, tz + 3, 'calcite')
    b.box(tx, 2, tz, tx + 2, 22, tz + 2, 'air')
    for y in range(18, 21):
        b.set(tx + 1, y, tz - 1, 'air'); b.set(tx + 1, y, tz + 3, 'air'); b.set(tx - 1, y, tz + 1, 'air'); b.set(tx + 3, y, tz + 1, 'air')
    b.set(tx + 1, 20, tz + 1, 'bell', attachment='ceiling', facing='north', powered='false')
    b.box(tx + 1, 21, tz + 1, tx + 1, 21, tz + 1, 'calcite')
    for i, y in enumerate(range(23, 28)):
        r = 2 - i // 2
        b.box(tx + 1 - r, y, tz + 1 - r, tx + 1 + r, y, tz + 1 + r, 'deepslate_tiles')
    b.set(tx + 1, 28, tz + 1, 'gold_block')
    # puerta y altar
    b.clear(cx0 + 5, 2, cz0, cx0 + 7, 5, cz0)
    b.door(cx0 + 5, 2, cz0, 'dark_oak', 'north', 'left'); b.door(cx0 + 7, 2, cz0, 'dark_oak', 'north', 'right')
    b.set(cx0 + 6, 2, cz0, 'air'); b.set(cx0 + 6, 3, cz0, 'air')
    b.box(cx0 + 4, 2, cz0 + 11, cx0 + 8, 2, cz0 + 12, 'quartz_block')
    b.set(cx0 + 6, 3, cz0 + 12, 'gold_block')
    for z in range(cz0 + 3, cz0 + 10, 2):
        for x in (cx0 + 2, cx0 + 3, cx0 + 9, cx0 + 10):
            b.set(x, 2, z, 'spruce_stairs', facing='south', half='bottom', shape='straight', waterlogged='false')
    for x in (cx0 + 3, cx0 + 9):
        for z in (cz0 + 4, cz0 + 10):
            b.set(x, 9, z, 'lantern', hanging='true', waterlogged='false')
    # jardines
    for x in range(c - 6, c + 6):
        for z in (c - 8, c + 7):
            if b.get(x, 1, z) not in ('minecraft:polished_andesite',):
                b.set(x, 1, z, 'grass_block', snowy='false')
                b.set(x, 2, z, rnd.choice(['poppy', 'dandelion', 'azure_bluet', 'oxeye_daisy', 'short_grass']))
    # habitantes
    for (vx, vz) in ((c + 3, c - 4), (c - 4, c + 3), (c + 3, c + 4)):
        b.villager(vx, 2, vz, 'minecraft:none')
    b.save('midgar_capital')


if __name__ == '__main__':
    build_store()
    build_capital()
