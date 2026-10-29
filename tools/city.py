"""Genera las cuadras de las capitales (Midgar 512x512 y Oriana 256x256).

Cada plantilla mide 32x32 (o 64x64 para los edificios grandes). La capa y=0 es el suelo de la
ciudad. Las calles van por los bordes oeste (x 0..3) y norte (z 0..3) de cada cuadra, así al
juntarlas se forma la cuadrícula de calles.

Uso: python3 tools/city.py   (requiere: pip install nbtlib)
"""
import os, random, sys
sys.path.insert(0, os.path.dirname(__file__))
from structures import Build, OUT
import nbtlib
from nbtlib import Compound, List, Int, String, Double, Byte, Float

C = 32
JOBS = ['lectern', 'composter', 'smoker', 'barrel', 'blast_furnace', 'cartography_table', 'fletching_table',
        'grindstone', 'loom', 'smithing_table', 'stonecutter', 'brewing_stand', 'cauldron']
JOB_PROPS = {'smoker': dict(facing='north', lit='false'), 'blast_furnace': dict(facing='north', lit='false'),
             'barrel': dict(facing='up', open='false'), 'grindstone': dict(face='floor', facing='north'),
             'loom': dict(facing='north'), 'stonecutter': dict(facing='north'), 'lectern': dict(facing='north', has_book='false', powered='false'),
             'composter': dict(level='0'), 'brewing_stand': dict(has_bottle_0='false', has_bottle_1='false', has_bottle_2='false')}

STYLE = {
    'midgar': dict(road=['stone_bricks', 'andesite', 'cobblestone', 'stone_bricks', 'polished_andesite'], curb='polished_andesite',
                   wall='stone_bricks', wall2='cracked_stone_bricks', cap='chiseled_stone_bricks', tower_roof='deepslate_tile',
                   lamp_post='dark_oak_fence'),
    'oriana': dict(road=['polished_andesite', 'smooth_stone', 'polished_andesite', 'polished_diorite'], curb='smooth_stone',
                   wall='quartz_bricks', wall2='smooth_quartz', cap='chiseled_quartz_block', tower_roof='oxidized_cut_copper',
                   lamp_post='iron_bars'),
}


def villager(b, x, y, z, profession='minecraft:none', vtype='plains', name=None, extra=None):
    b.villager(x, y, z, profession, vtype, name)
    if extra:
        b.entities[-1]['nbt'].update(extra)


def entity(b, x, y, z, eid, nbt=None):
    data = {'id': String(eid), 'PersistenceRequired': Byte(1)}
    if nbt:
        data.update(nbt)
    b.entities.append(Compound({'pos': List[Double]([Double(x + 0.5), Double(y), Double(z + 0.5)]),
                                'blockPos': List[Int]([Int(x), Int(y), Int(z)]), 'nbt': Compound(data)}))


def npc(b, x, y, z, character, rot=0.0):
    entity(b, x, y, z, 'draxatomic:story_npc', {'Character': String(character),
                                                 'Rotation': List[Float]([Float(rot), Float(0.0)])})


def ground(b, size, style, rnd, mat='grass_block'):
    for x in range(size):
        for z in range(size):
            if mat == 'grass_block':
                b.set(x, 0, z, 'grass_block', snowy='false')
            else:
                b.set(x, 0, z, mat)


def roads(b, size, style, rnd, west=True, north=True, z_from=0, x_from=0):
    st = STYLE[style]
    for x in range(size):
        for z in range(size):
            if (west and x < 4 and z >= z_from) or (north and z < 4 and x >= x_from):
                b.set(x, 0, z, rnd.choice(st['road']))
    # farol en la esquina interior
    lamp(b, 4, 1, 4, style)


def lamp(b, x, y, z, style, h=3):
    post = STYLE[style]['lamp_post']
    for dy in range(h):
        if post == 'iron_bars':
            b.set(x, y + dy, z, 'iron_bars', north='false', south='false', east='false', west='false', waterlogged='false')
        else:
            b.set(x, y + dy, z, post, north='false', south='false', east='false', west='false', waterlogged='false')
    b.set(x, y + h, z, 'lantern', hanging='false', waterlogged='false')


def tree(b, x, y, z, rnd, log='oak_log', leaves='oak_leaves', h=None):
    h = h or rnd.randint(4, 6)
    for dy in range(h):
        b.set(x, y + dy, z, log, axis='y')
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(h - 2, h + 1):
                if abs(dx) + abs(dz) + max(0, dy - (h - 1)) * 2 <= 3 and (dx, dz) != (0, 0) or dy == h:
                    if b.get(x + dx, y + dy, z + dz) in (None, 'minecraft:air'):
                        b.set(x + dx, y + dy, z + dz, leaves, persistent='true', distance='1', waterlogged='false')


def flowers(b, x0, z0, x1, z1, rnd, y=1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if rnd.random() < 0.45:
                b.set(x, y, z, rnd.choice(['poppy', 'dandelion', 'azure_bluet', 'oxeye_daisy', 'cornflower', 'allium', 'short_grass', 'short_grass']))


def bench(b, x, y, z, facing='north'):
    b.set(x, y, z, 'spruce_stairs', facing=facing, half='bottom', shape='straight', waterlogged='false')


def stairs(b, x, y, z, mat, facing, half='bottom'):
    b.set(x, y, z, f'{mat}_stairs', facing=facing, half=half, shape='straight', waterlogged='false')


def slab(b, x, y, z, mat, t='bottom'):
    b.set(x, y, z, f'{mat}_slab', type=t, waterlogged='false')


def window(b, x, y, z):
    b.set(x, y, z, 'glass')


# ------------------------------------------------------------------ casas de Midgar

def mid_house(b, x0, z0, w, d, wall, roof, frame, rnd, door='north', job=True):
    x1, z1 = x0 + w - 1, z0 + d - 1
    b.box(x0, 0, z0, x1, 0, z1, 'spruce_planks')
    b.walls(x0, 1, z0, x1, 4, z1, wall)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.box(x, 1, z, x, 4, z, frame, axis='y')
    for x in range(x0, x1 + 1):
        b.set(x, 4, z0, frame, axis='x'); b.set(x, 4, z1, frame, axis='x')
    b.box(x0 + 1, 1, z0 + 1, x1 - 1, 4, z1 - 1, 'air')
    for x in range(x0 + 2, x1 - 1, 2):
        window(b, x, 2, z0); window(b, x, 2, z1)
    for z in range(z0 + 2, z1 - 1, 2):
        window(b, x0, 2, z); window(b, x1, 2, z)
    # techo a dos aguas con cumbrera a lo largo de x
    half = (d + 1) // 2
    for i in range(half + 1):
        y = 5 + i
        za, zb = z0 - 1 + i, z1 + 1 - i
        for x in range(x0 - 1, x1 + 2):
            if za < zb:
                stairs(b, x, y, za, roof, 'south'); stairs(b, x, y, zb, roof, 'north')
            elif za == zb:
                slab(b, x, y, za, roof)
        if za < zb:
            for z in range(za + 1, zb):
                if b.get(x0, y, z) is None or True:
                    b.set(x0, y, z, wall); b.set(x1, y, z, wall)
    dx = x0 + w // 2
    if door == 'north':
        b.door(dx, 1, z0, 'spruce', 'north'); bz = z1 - 1; bed_f = 'north'
    else:
        b.door(dx, 1, z1, 'spruce', 'south'); bz = z0 + 1; bed_f = 'south'
    # cama (cabecera contra la pared del fondo) y puesto de trabajo
    bx = x0 + 1
    b.set(bx, 1, bz, 'red_bed' if rnd.random() < 0.5 else 'blue_bed', facing=bed_f, part='head', occupied='false')
    b.set(bx, 1, bz - 1 if door == 'north' else bz + 1, b.get(bx, 1, bz).split(':')[1], facing=bed_f, part='foot', occupied='false')
    if job:
        j = rnd.choice(JOBS)
        b.set(x1 - 1, 1, bz, j, **JOB_PROPS.get(j, {}))
    b.set(x1 - 1, 1, z0 + 1 if door == 'north' else z1 - 1, 'crafting_table')
    b.set(x0 + w // 2, 4, z0 + d // 2, 'lantern', hanging='true', waterlogged='false')


def midgar_houses(name, seed, palettes):
    rnd = random.Random(seed)
    b = Build(C, 14, C)
    ground(b, C, 'midgar', rnd)
    roads(b, C, 'midgar', rnd)
    for qx, qz, door in ((5, 5, 'south'), (19, 5, 'south'), (5, 21, 'north'), (19, 21, 'north')):
        wall, roof, frame = rnd.choice(palettes)
        mid_house(b, qx, qz, rnd.choice([8, 9]), 7, wall, roof, frame, rnd, door)
    # patio central: pozo, flores y un sendero
    for x in range(4, C):
        b.set(x, 0, 16, 'dirt_path'); b.set(x, 0, 17, 'dirt_path')
    b.walls(15, 1, 15, 18, 1, 18, 'cobblestone')
    b.box(16, 0, 16, 17, 0, 17, 'water', level='0')
    for x in (15, 18):
        for z in (15, 18):
            b.box(x, 2, z, x, 3, z, 'oak_fence', north='false', south='false', east='false', west='false', waterlogged='false')
    b.box(15, 4, 15, 18, 4, 18, 'oak_slab', type='bottom', waterlogged='false')
    flowers(b, 5, 13, 13, 14, rnd); flowers(b, 20, 19, 29, 19, rnd)
    tree(b, 29, 1, 14, rnd)
    n = 1 + (rnd.random() < 0.35)
    for k in range(n):
        villager(b, 10 + k * 10, 1, 16)
    b.save_to(f'city/midgar/{name}')


# ------------------------------------------------------------------ edificios de Oriana (estilo parisino)

def haussmann(b, x0, z0, w, d, rnd, facade='smooth_sandstone', floors=4, door='north'):
    x1, z1 = x0 + w - 1, z0 + d - 1
    H = floors * 4
    b.box(x0, 0, z0, x1, 0, z1, 'polished_andesite')
    b.walls(x0, 1, z0, x1, H, z1, facade)
    b.box(x0 + 1, 1, z0 + 1, x1 - 1, H, z1 - 1, 'air')
    for f in range(1, floors):
        b.box(x0 + 1, f * 4 + 1, z0 + 1, x1 - 1, f * 4 + 1, z1 - 1, 'spruce_planks')
    # cornisas entre pisos
    for f in range(1, floors + 1):
        for x in range(x0, x1 + 1):
            b.set(x, f * 4, z0, 'smooth_quartz'); b.set(x, f * 4, z1, 'smooth_quartz')
    # ventanas altas y balcones de hierro
    for f in range(floors):
        yb = f * 4 + 2
        for x in range(x0 + 2, x1 - 1, 3):
            for z, out in ((z0, z0 - 1), (z1, z1 + 1)):
                b.set(x, yb, z, 'glass'); b.set(x, yb + 1, z, 'glass')
                if f in (1, 3) and b.size[2] > out >= 0:
                    b.set(x, yb - 1, out, 'smooth_quartz_slab', type='top', waterlogged='false')
                    b.set(x, yb, out, 'iron_bars', north='false', south='false', east='true', west='true', waterlogged='false')
    # planta baja: tiendas con toldo
    for x in range(x0 + 1, x1, 6):
        for z, out in ((z0, z0 - 1), (z1, z1 + 1)):
            if 0 <= out < b.size[2]:
                for dx in range(3):
                    if x + dx < x1:
                        b.set(x + dx, 4, out, rnd.choice(['red_carpet', 'green_carpet', 'blue_carpet']))
    # techo abuhardillado (mansarda) de pizarra
    for i in range(3):
        y = H + 1 + i
        for x in range(x0 - 1 + i, x1 + 2 - i):
            stairs(b, x, y, z0 - 1 + i, 'deepslate_tile', 'south'); stairs(b, x, y, z1 + 1 - i, 'deepslate_tile', 'north')
        for z in range(z0 + i, z1 + 1 - i):
            stairs(b, x0 - 1 + i, y, z, 'deepslate_tile', 'east'); stairs(b, x1 + 1 - i, y, z, 'deepslate_tile', 'west')
    b.box(x0 + 2, H + 3, z0 + 2, x1 - 2, H + 3, z1 - 2, 'deepslate_tiles')
    # buhardillas
    for x in range(x0 + 3, x1 - 2, 5):
        b.set(x, H + 2, z0 + 1, 'glass'); b.set(x, H + 2, z1 - 1, 'glass')
    # chimeneas
    for x in (x0 + 1, x1 - 1):
        b.box(x, H + 3, z0 + d // 2, x, H + 5, z0 + d // 2, 'bricks')
    # puerta, camas y trabajo en la planta baja
    dx = x0 + w // 2
    if door == 'north':
        b.door(dx, 1, z0, 'dark_oak', 'north')
    else:
        b.door(dx, 1, z1, 'dark_oak', 'south')
    for k, bx in enumerate((x0 + 2, x1 - 3)):
        bz = z1 - 1 if door == 'north' else z0 + 1
        f = 'north' if door == 'north' else 'south'
        b.set(bx, 1, bz, 'white_bed', facing=f, part='head', occupied='false')
        b.set(bx, 1, bz - 1 if door == 'north' else bz + 1, 'white_bed', facing=f, part='foot', occupied='false')
        j = rnd.choice(JOBS)
        b.set(bx + 2, 1, bz, j, **JOB_PROPS.get(j, {}))
    for x in range(x0 + 3, x1 - 2, 6):
        b.set(x, 3, z0 + d // 2, 'lantern', hanging='true', waterlogged='false')


def oriana_houses(name, seed, facades):
    rnd = random.Random(seed)
    b = Build(C, 24, C)
    ground(b, C, 'oriana', rnd)
    roads(b, C, 'oriana', rnd)
    haussmann(b, 5, 5, 26, 10, rnd, rnd.choice(facades), rnd.choice([3, 4]), 'north')
    haussmann(b, 5, 21, 26, 10, rnd, rnd.choice(facades), rnd.choice([3, 4]), 'south')
    # patio interior con tilos
    for x in range(5, 31):
        for z in (16, 17, 18, 19):
            b.set(x, 0, z, 'grass_block', snowy='false')
    for x in (8, 15, 22, 28):
        tree(b, x, 1, 17, rnd, 'birch_log', 'birch_leaves', 4)
    villager(b, 12, 1, 18)
    if rnd.random() < 0.5:
        villager(b, 20, 1, 18)
    b.save_to(f'city/oriana/{name}')


# ------------------------------------------------------------------ comunes

def park(style, seed):
    rnd = random.Random(seed)
    b = Build(C, 12, C)
    ground(b, C, style, rnd)
    roads(b, C, style, rnd)
    for x in range(4, C):
        for z in range(4, C):
            if abs(x - 18) <= 1 or abs(z - 18) <= 1:
                b.set(x, 0, z, 'gravel' if style == 'midgar' else 'smooth_stone')
    # estanque
    for x in range(22, 29):
        for z in range(6, 13):
            if (x - 25) ** 2 + (z - 9) ** 2 <= 9:
                b.set(x, 0, z, 'water', level='0'); b.set(x, -0, z, 'water', level='0')
    b.set(25, 0, 9, 'lily_pad') if False else None
    for tx, tz in ((8, 8), (12, 26), (26, 26), (7, 22), (29, 17), (10, 13)):
        tree(b, tx, 1, tz, rnd, *(('oak_log', 'oak_leaves') if style == 'midgar' else ('cherry_log', 'cherry_leaves')))
    flowers(b, 5, 5, 15, 15, rnd); flowers(b, 20, 20, 31, 31, rnd)
    for x in (15, 21):
        bench(b, x, 1, 16, 'south'); bench(b, x, 1, 20, 'north')
    lamp(b, 17, 1, 17, style)
    b.save_to(f'city/{style}/park')


def market(style, seed):
    rnd = random.Random(seed)
    b = Build(C, 10, C)
    ground(b, C, style, rnd, 'stone_bricks' if style == 'midgar' else 'polished_andesite')
    roads(b, C, style, rnd)
    colors = ['red', 'yellow', 'blue', 'green', 'orange', 'purple', 'white', 'lime']
    stalls = [(6, 6), (14, 6), (22, 6), (6, 20), (14, 20), (22, 20)]
    for k, (sx, sz) in enumerate(stalls):
        col = rnd.choice(colors)
        for x in range(sx, sx + 6):
            for z in range(sz, sz + 5):
                b.set(x, 4, z, f'{col}_wool' if (x + z) % 2 else 'white_wool')
        for x, z in ((sx, sz), (sx + 5, sz), (sx, sz + 4), (sx + 5, sz + 4)):
            b.box(x, 1, z, x, 3, z, 'spruce_fence', north='false', south='false', east='false', west='false', waterlogged='false')
        for x in range(sx + 1, sx + 5):
            b.set(x, 1, sz + 1, 'barrel', facing='up', open='false')
        j = rnd.choice(JOBS)
        b.set(sx + 2, 1, sz + 3, j, **JOB_PROPS.get(j, {}))
        b.set(sx + 1, 2, sz + 1, rnd.choice(['pumpkin', 'melon', 'hay_block', 'cake']), **({'bites': '0'} if False else {}))
    b.walls(15, 1, 15, 18, 1, 18, 'stone_brick_wall' if False else 'cobblestone')
    b.box(16, 1, 16, 17, 1, 17, 'water', level='0')
    for k in range(3):
        villager(b, 9 + k * 7, 1, 13)
    b.save_to(f'city/{style}/market')


def barracks(style, seed):
    rnd = random.Random(seed)
    b = Build(C, 14, C)
    ground(b, C, style, rnd, 'gravel')
    roads(b, C, style, rnd)
    wall = 'stone_bricks' if style == 'midgar' else 'quartz_bricks'
    b.walls(6, 1, 6, 29, 7, 15, wall)
    b.box(7, 1, 7, 28, 6, 14, 'air')
    b.box(6, 0, 6, 29, 0, 15, 'spruce_planks')
    b.box(5, 8, 5, 30, 8, 16, 'deepslate_tiles' if style == 'midgar' else 'oxidized_cut_copper')
    for x in range(8, 28, 3):
        window(b, x, 3, 6); window(b, x, 3, 15)
        b.set(x, 1, 13, 'gray_bed', facing='north', part='head', occupied='false')
        b.set(x, 1, 12, 'gray_bed', facing='north', part='foot', occupied='false')
    b.door(17, 1, 15, 'spruce', 'south')
    b.set(10, 1, 8, 'smithing_table'); b.set(12, 1, 8, 'grindstone', face='floor', facing='north'); b.set(14, 1, 8, 'fletching_table')
    for x in range(10, 26, 5):
        b.set(x, 5, 10, 'lantern', hanging='true', waterlogged='false')
    # patio de entrenamiento con dianas de paja
    for x in (9, 14, 19, 24):
        b.box(x, 1, 24, x, 2, 24, 'hay_block', axis='y')
        b.set(x, 3, 24, 'carved_pumpkin', facing='north')
    for k in range(2):
        entity(b, 12 + k * 10, 1, 20, 'draxatomic:kingdom_guard', {'Kingdom': String(style)})
    entity(b, 18, 1, 21, 'minecraft:iron_golem', {'PlayerCreated': Byte(0)})
    b.save_to(f'city/{style}/barracks')


# ------------------------------------------------------------------ murallas, puertas y torres

def wall_piece(style, side, gate=False):
    rnd = random.Random(hash((style, side, gate)) & 0xffff)
    st = STYLE[style]
    h = 12 if style == 'midgar' else 9
    b = Build(C, h + 4, C)
    ground(b, C, style, rnd)
    # rango interior para las calles
    if side == 'n':
        wz = (4, 7)
        for x in range(C):
            for z in range(wz[0], wz[1] + 1):
                b.box(x, 1, z, x, h, z, rnd.choice([st['wall'], st['wall'], st['wall2']]))
        for x in range(0, C, 2):
            b.set(x, h + 1, wz[0], st['cap'])
        roads(b, C, style, rnd, west=True, north=False, z_from=8)
        if gate:
            b.box(0, 1, 0, 5, 6, 7, 'air'); b.box(0, 0, 0, 5, 0, 7, rnd.choice(st['road']))
            b.box(0, 7, wz[0], 5, 7, wz[1], st['cap'])
            lamp(b, 6, 1, 8, style)
    elif side == 's':
        wz = (24, 27)
        for x in range(C):
            for z in range(wz[0], wz[1] + 1):
                b.box(x, 1, z, x, h, z, rnd.choice([st['wall'], st['wall'], st['wall2']]))
        for x in range(0, C, 2):
            b.set(x, h + 1, wz[1], st['cap'])
        roads(b, C, style, rnd, west=True, north=True)
        for x in range(4, C):
            for z in range(4, 24):
                if rnd.random() < 0.3: b.set(x, 1, z, 'short_grass')
        if gate:
            b.box(0, 1, 24, 5, 6, 31, 'air'); b.box(0, 0, 24, 5, 0, 31, rnd.choice(st['road']))
            b.box(0, 7, wz[0], 5, 7, wz[1], st['cap'])
    elif side == 'w':
        wx = (4, 7)
        for z in range(C):
            for x in range(wx[0], wx[1] + 1):
                b.box(x, 1, z, x, h, z, rnd.choice([st['wall'], st['wall'], st['wall2']]))
        for z in range(0, C, 2):
            b.set(wx[0], h + 1, z, st['cap'])
        roads(b, C, style, rnd, west=False, north=True, x_from=8)
        if gate:
            b.box(0, 1, 0, 7, 6, 5, 'air'); b.box(0, 0, 0, 7, 0, 5, rnd.choice(st['road']))
            b.box(wx[0], 7, 0, wx[1], 7, 5, st['cap'])
    else:  # 'e'
        wx = (24, 27)
        for z in range(C):
            for x in range(wx[0], wx[1] + 1):
                b.box(x, 1, z, x, h, z, rnd.choice([st['wall'], st['wall'], st['wall2']]))
        for z in range(0, C, 2):
            b.set(wx[1], h + 1, z, st['cap'])
        roads(b, C, style, rnd, west=True, north=True)
        if gate:
            b.box(24, 1, 0, 31, 6, 5, 'air'); b.box(24, 0, 0, 31, 0, 5, rnd.choice(st['road']))
            b.box(wx[0], 7, 0, wx[1], 7, 5, st['cap'])
    b.save_to(f'city/{style}/{"gate" if gate else "wall"}_{side}')


def tower(style, corner):
    rnd = random.Random(hash((style, corner)) & 0xffff)
    st = STYLE[style]
    h = 12 if style == 'midgar' else 9
    th = h + 8
    b = Build(C, th + 6, C)
    ground(b, C, style, rnd)
    tx = 2 if 'w' in corner else 18
    tz = 2 if 'n' in corner else 18
    # tramos de muralla que llegan a la torre
    wz = (4, 7) if 'n' in corner else (24, 27)
    wx = (4, 7) if 'w' in corner else (24, 27)
    for x in range(C):
        for z in range(wz[0], wz[1] + 1):
            if ('w' in corner and x >= wx[0]) or ('e' in corner and x <= wx[1]):
                b.box(x, 1, z, x, h, z, st['wall'])
    for z in range(C):
        for x in range(wx[0], wx[1] + 1):
            if ('n' in corner and z >= wz[0]) or ('s' in corner and z <= wz[1]):
                b.box(x, 1, z, x, h, z, st['wall'])
    b.walls(tx, 1, tz, tx + 11, th, tz + 11, st['wall'])
    b.box(tx + 1, 1, tz + 1, tx + 10, th - 1, tz + 10, 'air')
    b.box(tx, th, tz, tx + 11, th, tz + 11, st['cap'])
    for i in range(5):
        b.box(tx + 1 + i, th + 1 + i, tz + 1 + i, tx + 10 - i, th + 1 + i, tz + 10 - i,
              st['tower_roof'] + ('s' if st['tower_roof'] == 'deepslate_tile' else ''))
    b.set(tx + 5, th - 1, tz + 5, 'lantern', hanging='true', waterlogged='false')
    for y in range(4, th - 2, 4):
        for x in (tx + 5, tx + 6):
            b.set(x, y, tz, 'air'); b.set(x, y, tz + 11, 'air')
    b.save_to(f'city/{style}/tower_{corner}')


def save_to(self, name):
    path = os.path.join(OUT, name + '.nbt')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    self.save(name)


Build.save_to = save_to

if __name__ == '__main__':
    import buildings
    for style in ('midgar', 'oriana'):
        for side in 'nswe':
            wall_piece(style, side)
            wall_piece(style, side, gate=True)
        for corner in ('nw', 'ne', 'sw', 'se'):
            tower(style, corner)
        park(style, 11)
        market(style, 12)
        barracks(style, 13)
    MP = [('white_terracotta', 'dark_oak', 'dark_oak_log'), ('smooth_sandstone', 'spruce', 'spruce_log'),
          ('calcite', 'deepslate_brick', 'dark_oak_log'), ('white_concrete', 'brick', 'stripped_dark_oak_log'),
          ('mud_bricks', 'spruce', 'spruce_log')]
    for k, name in enumerate(['houses_a', 'houses_b', 'houses_c', 'houses_d']):
        midgar_houses(name, 100 + k, MP[k:k + 2] + MP[:1])
    OF = ['smooth_sandstone', 'white_terracotta', 'calcite', 'birch_planks', 'smooth_quartz']
    for k, name in enumerate(['houses_a', 'houses_b', 'houses_c']):
        oriana_houses(name, 200 + k, OF[k:k + 3])
    buildings.build_all()
