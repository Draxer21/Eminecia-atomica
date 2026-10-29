"""Edificios grandes de las capitales (64x64 = 2x2 cuadras, salvo los de 32)."""
from nbtlib import String
import random
from structures import Build
from city import (ground, roads, lamp, tree, flowers, bench, stairs, slab, window, villager, entity, npc,
                  JOBS, JOB_PROPS, STYLE, C)

B = 64


def paving(b, size, mats, rnd, x0=0, z0=0, x1=None, z1=None):
    x1 = size - 1 if x1 is None else x1
    z1 = size - 1 if z1 is None else z1
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, rnd.choice(mats))


def cone(b, cx, cz, r, y, mat):
    """Techo cónico/piramidal sobre una torre cuadrada de lado 2r+1."""
    k = 0
    while r - k >= 0:
        b.box(cx - r + k, y + k, cz - r + k, cx + r - k, y + k, cz + r - k, mat)
        k += 1
    return y + k


def square_tower(b, cx, cz, r, h, wall, roof, rnd, y0=1):
    b.walls(cx - r, y0, cz - r, cx + r, h, cz + r, wall)
    b.box(cx - r + 1, y0, cz - r + 1, cx + r - 1, h - 1, cz + r - 1, 'air')
    for y in range(y0 + 3, h, 5):
        b.set(cx, y, cz - r, 'glass'); b.set(cx, y, cz + r, 'glass'); b.set(cx - r, y, cz, 'glass'); b.set(cx + r, y, cz, 'glass')
    top = cone(b, cx, cz, r + 1, h + 1, roof)
    b.set(cx, top, cz, 'gold_block')


# ------------------------------------------------------------------ Midgar

def midgar_castle():
    rnd = random.Random(1)
    b = Build(B, 48, B)
    ground(b, B, 'midgar', rnd)
    roads(b, B, 'midgar', rnd)
    # patio
    paving(b, B, ['stone_bricks', 'polished_andesite'], rnd, 8, 8, 59, 59)
    # muralla exterior con torres
    b.walls(8, 1, 8, 59, 14, 59, 'stone_bricks')
    for x in range(8, 60, 2):
        b.set(x, 15, 8, 'stone_bricks'); b.set(x, 15, 59, 'stone_bricks')
    for z in range(8, 60, 2):
        b.set(8, 15, z, 'stone_bricks'); b.set(59, 15, z, 'stone_bricks')
    for cx, cz in ((10, 10), (57, 10), (10, 57), (57, 57)):
        square_tower(b, cx, cz, 4, 24, 'stone_bricks', 'deepslate_tiles', rnd)
    # puerta principal al sur, con rastrillo de hierro levantado
    b.box(30, 1, 59, 37, 9, 59, 'air')
    b.box(30, 10, 59, 37, 10, 59, 'chiseled_stone_bricks')
    for x in range(30, 38):
        b.set(x, 9, 58, 'iron_bars', north='false', south='false', east='true', west='true', waterlogged='false')
    for x in range(30, 38):
        for z in range(59, 64):
            b.set(x, 0, z, 'polished_andesite')
    # torreón central
    X0, Z0, X1, Z1 = 20, 16, 47, 44
    b.box(X0, 0, Z0, X1, 0, Z1, 'polished_deepslate')
    b.walls(X0, 1, Z0, X1, 22, Z1, 'polished_andesite')
    b.box(X0 + 1, 1, Z0 + 1, X1 - 1, 21, Z1 - 1, 'air')
    b.box(X0 + 1, 11, Z0 + 1, X1 - 1, 11, Z1 - 1, 'dark_oak_planks')
    b.box(X0 + 6, 11, Z0 + 6, X1 - 6, 11, Z1 - 6, 'air')  # balcón interior sobre el salón
    for x in range(X0 + 3, X1 - 2, 4):
        for y in (4, 5, 6, 14, 15, 16):
            b.set(x, y, Z0, 'glass'); b.set(x, y, Z1, 'glass')
    for z in range(Z0 + 3, Z1 - 2, 4):
        for y in (4, 5, 6, 14, 15, 16):
            b.set(X0, y, z, 'glass'); b.set(X1, y, z, 'glass')
    # techo escalonado de pizarra y torre del homenaje
    for i in range(6):
        b.box(X0 - 1 + i, 23 + i, Z0 - 1 + i, X1 + 1 - i, 23 + i, Z1 + 1 - i, 'deepslate_tiles')
    square_tower(b, 33, 30, 3, 40, 'polished_andesite', 'deepslate_tiles', rnd, y0=24)
    # salón del trono
    for z in range(Z0 + 1, Z1):
        b.set(33, 1, z, 'red_carpet'); b.set(34, 1, z, 'red_carpet')
    b.box(31, 1, Z0 + 2, 36, 1, Z0 + 4, 'gold_block')
    b.box(31, 2, Z0 + 2, 36, 2, Z0 + 4, 'red_carpet')
    for x, f in ((32, 'south'), (35, 'south')):
        stairs(b, x, 3, Z0 + 3, 'quartz', f)
        b.box(x, 3, Z0 + 2, x, 5, Z0 + 2, 'gold_block')
    for x in (X0 + 4, X1 - 4):
        for z in range(Z0 + 4, Z1 - 2, 6):
            b.box(x, 1, z, x, 10, z, 'quartz_pillar', axis='y')
    for x in range(X0 + 4, X1 - 2, 6):
        for z in range(Z0 + 4, Z1 - 2, 6):
            b.set(x, 10, z, 'lantern', hanging='true', waterlogged='false')
    b.box(32, 1, Z1, 35, 5, Z1, 'air')
    b.door(32, 1, Z1, 'dark_oak', 'south', 'left'); b.door(35, 1, Z1, 'dark_oak', 'south', 'right')
    # jardines del patio
    for tx, tz in ((14, 48), (53, 48), (14, 22), (53, 22)):
        tree(b, tx, 1, tz, rnd)
    flowers(b, 12, 50, 26, 55, rnd); flowers(b, 41, 50, 55, 55, rnd)
    # la realeza y su guardia
    npc(b, 33, 3, Z0 + 4, 'alexia', 0.0)
    npc(b, 35, 1, Z0 + 8, 'iris', 0.0)
    for x in (29, 38):
        entity(b, x, 1, 61, 'draxatomic:kingdom_guard', {'Kingdom': String('midgar')})
    entity(b, 33, 1, 50, 'draxatomic:kingdom_guard', {'Kingdom': String('midgar')})
    b.save_to('city/midgar/castle')


def midgar_plaza(style='midgar'):
    rnd = random.Random(2 if style == 'midgar' else 22)
    b = Build(B, 20, B)
    ground(b, B, style, rnd)
    roads(b, B, style, rnd)
    c = 34
    mats = ['polished_andesite', 'smooth_stone', 'polished_diorite'] if style == 'midgar' else ['smooth_quartz', 'polished_diorite', 'calcite']
    for x in range(4, B):
        for z in range(4, B):
            r = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
            if r <= 27:
                b.set(x, 0, z, mats[int(r) // 3 % len(mats)])
            else:
                b.set(x, 0, z, 'grass_block', snowy='false')
    # fuente monumental
    for x in range(c - 8, c + 9):
        for z in range(c - 8, c + 9):
            r = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
            if r <= 8:
                b.set(x, 0, z, 'water', level='0')
                if r > 7:
                    slab(b, x, 1, z, 'stone_brick' if style == 'midgar' else 'quartz')
    b.box(c - 1, 0, c - 1, c + 1, 5, c + 1, 'quartz_pillar', axis='y')
    b.box(c - 2, 6, c - 2, c + 2, 6, c + 2, 'water', level='0')
    b.walls(c - 3, 6, c - 3, c + 3, 6, c + 3, 'quartz_slab', type='bottom', waterlogged='false')
    b.set(c, 7, c, 'gold_block'); b.set(c, 8, c, 'lantern', hanging='false', waterlogged='false')
    # estatuas, bancos, faroles y árboles alrededor
    for k, (sx, sz) in enumerate(((c - 18, c - 18), (c + 18, c - 18), (c - 18, c + 18), (c + 18, c + 18))):
        b.box(sx, 1, sz, sx, 2, sz, 'chiseled_stone_bricks' if style == 'midgar' else 'chiseled_quartz_block')
        b.box(sx, 3, sz, sx, 4, sz, 'gold_block')
        tree(b, sx + (3 if sx < c else -3), 1, sz, rnd, *(('oak_log', 'oak_leaves') if style == 'midgar' else ('cherry_log', 'cherry_leaves')))
    for a in range(0, 360, 30):
        import math
        x = int(round(c + 13 * math.cos(math.radians(a))))
        z = int(round(c + 13 * math.sin(math.radians(a))))
        lamp(b, x, 1, z, style, 3)
    for x in range(c - 6, c + 7, 4):
        bench(b, x, 1, c - 11, 'south'); bench(b, x, 1, c + 11, 'north')
    for k in range(4):
        villager(b, c - 10 + k * 6, 1, c - 14)
    for k in range(2):
        entity(b, c - 4 + k * 8, 1, c + 14, 'draxatomic:kingdom_guard', {'Kingdom': String(style)})
    b.save_to(f'city/{style}/plaza')


def midgar_cathedral():
    rnd = random.Random(3)
    b = Build(B, 48, B)
    ground(b, B, 'midgar', rnd)
    roads(b, B, 'midgar', rnd)
    paving(b, B, ['polished_diorite', 'calcite'], rnd, 6, 6, 61, 61)
    X0, Z0, X1, Z1 = 20, 12, 44, 56
    b.box(X0, 0, Z0, X1, 0, Z1, 'polished_diorite')
    b.walls(X0, 1, Z0, X1, 16, Z1, 'calcite')
    b.box(X0 + 1, 1, Z0 + 1, X1 - 1, 16, Z1 - 1, 'air')
    for z in range(Z0 + 3, Z1 - 2, 4):
        b.box(X0, 4, z, X0, 12, z, 'purple_stained_glass')
        b.box(X1, 4, z, X1, 12, z, 'yellow_stained_glass')
        for x in (X0 + 1, X1 - 1):
            b.box(x, 1, z - 1, x, 15, z - 1, 'quartz_pillar', axis='y')
    # bóveda
    w = (X1 - X0) // 2
    for i in range(w + 1):
        y = 17 + i // 2
        for z in range(Z0 - 1, Z1 + 2):
            stairs(b, X0 - 1 + i, y, z, 'deepslate_tile', 'east') if i < w else b.set(X0 - 1 + i, y, z, 'deepslate_tiles')
            stairs(b, X1 + 1 - i, y, z, 'deepslate_tile', 'west') if i < w else None
    # rosetón y portada
    for x in range(X0 + 9, X0 + 16):
        for y in range(9, 15):
            if (x - (X0 + 12)) ** 2 + (y - 12) ** 2 <= 9:
                b.set(x, y, Z1, 'magenta_stained_glass' if (x + y) % 2 else 'light_blue_stained_glass')
    b.box(X0 + 10, 1, Z1, X0 + 14, 6, Z1, 'air')
    b.door(X0 + 10, 1, Z1, 'dark_oak', 'south', 'left'); b.door(X0 + 14, 1, Z1, 'dark_oak', 'south', 'right')
    # campanarios gemelos
    for tx in (X0 + 2, X1 - 2):
        square_tower(b, tx, Z1 - 2, 3, 30, 'calcite', 'deepslate_tiles', rnd)
        b.set(tx, 26, Z1 - 2, 'bell', attachment='ceiling', facing='north', powered='false')
        b.set(tx, 27, Z1 - 2, 'calcite')
    # altar y bancos
    b.box(X0 + 8, 1, Z0 + 2, X1 - 8, 1, Z0 + 5, 'quartz_block')
    b.set(X0 + 12, 2, Z0 + 3, 'gold_block')
    for z in range(Z0 + 9, Z1 - 4, 2):
        for x in list(range(X0 + 3, X0 + 10)) + list(range(X1 - 9, X1 - 2)):
            stairs(b, x, 1, z, 'spruce', 'south')
    for z in range(Z0 + 2, Z1):
        b.set(X0 + 12, 1, z, 'red_carpet')
    for z in range(Z0 + 6, Z1 - 2, 8):
        b.set(X0 + 12, 14, z, 'lantern', hanging='true', waterlogged='false')
    villager(b, X0 + 12, 1, Z0 + 7, 'minecraft:cleric')
    b.save_to('city/midgar/cathedral')


def mitsugoshi_building(b, X0, Z0, X1, Z1, floors, rnd, clerks=2):
    H = floors * 5
    b.box(X0, 0, Z0, X1, 0, Z1, 'smooth_quartz')
    b.walls(X0, 1, Z0, X1, H, Z1, 'quartz_bricks')
    b.box(X0 + 1, 1, Z0 + 1, X1 - 1, H, Z1 - 1, 'air')
    for f in range(1, floors):
        b.box(X0 + 1, f * 5, Z0 + 1, X1 - 1, f * 5, Z1 - 1, 'smooth_quartz')
        for x in range(X0, X1 + 1):
            b.set(x, f * 5, Z0, 'gold_block') if x % 4 == 0 else None
    for x in (X0, X1):
        b.box(x, 1, Z0, x, H + 1, Z0, 'quartz_pillar', axis='y'); b.box(x, 1, Z1, x, H + 1, Z1, 'quartz_pillar', axis='y')
    # fachada de vidrio al sur
    for x in range(X0 + 2, X1 - 1):
        for f in range(floors):
            for y in range(f * 5 + 2, f * 5 + 5):
                if x % 6 != 1:
                    b.set(x, y, Z1, 'glass')
    for x in range(X0 + 2, X1 - 1, 6):
        for y in range(2, H, 5):
            b.set(x, y, Z0, 'glass'); b.set(x, y + 1, Z0, 'glass')
    b.box(X0, H + 1, Z0, X1, H + 1, Z1, 'quartz_bricks')
    b.walls(X0, H + 2, Z0, X1, H + 2, Z1, 'quartz_slab', type='bottom', waterlogged='false')
    # la gran M dorada sobre la entrada
    cx = (X0 + X1) // 2
    M = ['G...G', 'GG.GG', 'G.G.G', 'G...G', 'G...G']
    for row, line in enumerate(M):
        for col, ch in enumerate(line):
            if ch == 'G':
                b.set(cx - 2 + col, H + 7 - row, Z1, 'gold_block')
    # entrada
    b.box(cx - 2, 1, Z1, cx + 2, 4, Z1, 'air')
    b.door(cx - 1, 1, Z1, 'dark_oak', 'south', 'left'); b.door(cx + 1, 1, Z1, 'dark_oak', 'south', 'right')
    for z in range(Z0 + 2, Z1):
        b.set(cx, 1, z, 'red_carpet')
    # mostradores con empleados
    for k in range(clerks):
        x = X0 + 4 + k * ((X1 - X0 - 8) // max(1, clerks - 1)) if clerks > 1 else cx
        b.set(x, 1, Z0 + 4, 'draxatomic:mitsugoshi_counter')
        for dx in (-1, 1):
            b.set(x + dx, 1, Z0 + 4, 'dark_oak_planks')
        villager(b, x, 1, Z0 + 2, 'draxatomic:mitsugoshi_clerk')
    # estanterías y vitrinas
    for z in range(Z0 + 7, Z1 - 3, 3):
        for x in (X0 + 1, X1 - 1):
            b.set(x, 1, z, 'barrel', facing='up', open='false')
            b.set(x, 2, z, 'cake', bites='0') if (z // 3) % 2 else b.set(x, 2, z, 'chiseled_bookshelf', facing='east' if x == X0 + 1 else 'west',
                                                                       **{f'slot_{i}_occupied': 'false' for i in range(6)})
    for f in range(floors):
        for x in range(X0 + 4, X1 - 2, 6):
            for z in range(Z0 + 4, Z1 - 2, 6):
                b.set(x, f * 5 + 4, z, 'lantern', hanging='true', waterlogged='false')
    return cx


def midgar_mitsugoshi_hq():
    rnd = random.Random(4)
    b = Build(B, 40, B)
    ground(b, B, 'midgar', rnd)
    roads(b, B, 'midgar', rnd)
    paving(b, B, ['smooth_quartz', 'polished_diorite'], rnd, 4, 52, 63, 63)
    cx = mitsugoshi_building(b, 10, 10, 55, 48, 5, rnd, clerks=4)
    npc(b, cx, 1, 22, 'gamma', 180.0)
    for x in (16, 49):
        tree(b, x, 1, 57, rnd, 'cherry_log', 'cherry_leaves')
    lamp(b, cx - 6, 1, 54, 'midgar'); lamp(b, cx + 6, 1, 54, 'midgar')
    b.save_to('city/midgar/mitsugoshi_hq')


def midgar_academy():
    rnd = random.Random(5)
    b = Build(B, 40, B)
    ground(b, B, 'midgar', rnd)
    roads(b, B, 'midgar', rnd)
    # edificio en U: calcita con techos azules
    def block(x0, z0, x1, z1, h):
        b.box(x0, 0, z0, x1, 0, z1, 'polished_andesite')
        b.walls(x0, 1, z0, x1, h, z1, 'calcite')
        b.box(x0 + 1, 1, z0 + 1, x1 - 1, h - 1, z1 - 1, 'air')
        for f in range(5, h, 5):
            b.box(x0 + 1, f, z0 + 1, x1 - 1, f, z1 - 1, 'spruce_planks')
        for x in range(x0 + 2, x1 - 1, 3):
            for y in range(2, h - 1, 5):
                b.set(x, y, z0, 'glass'); b.set(x, y + 1, z0, 'glass'); b.set(x, y, z1, 'glass'); b.set(x, y + 1, z1, 'glass')
        for z in range(z0 + 2, z1 - 1, 3):
            for y in range(2, h - 1, 5):
                b.set(x0, y, z, 'glass'); b.set(x0, y + 1, z, 'glass'); b.set(x1, y, z, 'glass'); b.set(x1, y + 1, z, 'glass')
        for i in range(4):
            b.box(x0 - 1 + i, h + i, z0 - 1 + i, x1 + 1 - i, h + i, z1 + 1 - i, 'blue_terracotta' if i < 3 else 'light_blue_terracotta')
    block(8, 8, 55, 19, 15)      # ala norte
    block(8, 20, 19, 50, 15)     # ala oeste
    block(44, 20, 55, 50, 15)    # ala este
    square_tower(b, 31, 13, 3, 26, 'calcite', 'blue_terracotta', rnd)
    b.box(30, 1, 19, 33, 4, 19, 'air')
    b.door(30, 1, 19, 'dark_oak', 'south', 'left'); b.door(33, 1, 19, 'dark_oak', 'south', 'right')
    # aulas: mesas y atriles
    for x in range(12, 52, 6):
        b.set(x, 1, 12, 'lectern', facing='south', has_book='false', powered='false')
        for z in (14, 16):
            stairs(b, x, 1, z, 'oak', 'north')
    # campo de entrenamiento de espada
    for x in range(21, 43):
        for z in range(22, 52):
            b.set(x, 0, z, 'sand' if (x + z) % 7 else 'smooth_sandstone')
    for x in (24, 30, 36, 40):
        b.box(x, 1, 36, x, 2, 36, 'hay_block', axis='y'); b.set(x, 3, 36, 'carved_pumpkin', facing='south')
    for x in range(21, 43, 4):
        lamp(b, x, 1, 53, 'midgar')
    npc(b, 32, 1, 30, 'claire', 180.0)
    for k in range(3):
        villager(b, 24 + k * 6, 1, 44)
    b.save_to('city/midgar/academy')


# ------------------------------------------------------------------ Oriana

def oriana_palace():
    rnd = random.Random(6)
    b = Build(B, 44, B)
    ground(b, B, 'oriana', rnd)
    roads(b, B, 'oriana', rnd)
    # jardines a la francesa al sur
    for x in range(4, B):
        for z in range(38, B):
            b.set(x, 0, z, 'grass_block', snowy='false')
            if (x % 6 in (0, 1)) and z % 6 not in (0, 1) and 8 <= x <= 58 and z < 60:
                b.set(x, 1, z, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    for z in range(38, B):
        for x in range(30, 36):
            b.set(x, 0, z, 'smooth_quartz'); b.set(x, 1, z, 'air')
    for x in range(26, 40):
        for z in range(46, 56):
            if (x - 33) ** 2 / 36 + (z - 51) ** 2 / 16 <= 1:
                b.set(x, 0, z, 'water', level='0'); b.set(x, 1, z, 'air')
    b.box(32, 0, 50, 33, 3, 51, 'quartz_pillar', axis='y'); b.set(32, 4, 50, 'gold_block')
    # cuerpo central y alas
    def wing(x0, z0, x1, z1, h):
        b.box(x0, 0, z0, x1, 0, z1, 'smooth_quartz')
        b.walls(x0, 1, z0, x1, h, z1, 'quartz_bricks')
        b.box(x0 + 1, 1, z0 + 1, x1 - 1, h - 1, z1 - 1, 'air')
        b.box(x0 + 1, 8, z0 + 1, x1 - 1, 8, z1 - 1, 'dark_oak_planks')
        for x in range(x0 + 2, x1 - 1, 3):
            for y in (2, 3, 4, 10, 11, 12):
                b.set(x, y, z1, 'glass'); b.set(x, y, z0, 'glass')
        for x in range(x0, x1 + 1, 3):
            b.box(x, 1, z1, x, h, z1, 'quartz_pillar', axis='y')
        b.box(x0, h, z0, x1, h, z1, 'gold_block')
        for i in range(3):
            b.box(x0 + i, h + 1 + i, z0 + i, x1 - i, h + 1 + i, z1 - i, 'deepslate_tiles')
    wing(8, 10, 22, 36, 15)
    wing(42, 10, 56, 36, 15)
    wing(20, 8, 44, 30, 18)
    # cúpula de cobre
    cx, cz = 32, 19
    for y in range(0, 9):
        r = (64 - y * y) ** 0.5
        for x in range(cx - 8, cx + 9):
            for z in range(cz - 8, cz + 9):
                d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
                if r - 1 <= d <= r:
                    b.set(x, 22 + y, z, 'oxidized_cut_copper')
    b.box(cx, 31, cz, cx, 33, cz, 'gold_block')
    # salón del trono
    b.box(30, 1, 29, 35, 5, 30, 'air')
    b.door(31, 1, 30, 'dark_oak', 'south', 'left'); b.door(34, 1, 30, 'dark_oak', 'south', 'right')
    for z in range(10, 30):
        b.set(32, 1, z, 'blue_carpet'); b.set(33, 1, z, 'blue_carpet')
    b.box(30, 1, 10, 35, 1, 12, 'gold_block'); b.box(30, 2, 10, 35, 2, 12, 'blue_carpet')
    stairs(b, 31, 3, 11, 'quartz', 'south'); stairs(b, 34, 3, 11, 'quartz', 'south')
    for x in range(22, 44, 5):
        b.set(x, 7, 20, 'lantern', hanging='true', waterlogged='false')
    npc(b, 31, 3, 12, 'rose', 0.0)
    npc(b, 34, 3, 12, 'raphael', 0.0)
    for x in (29, 36):
        entity(b, x, 1, 33, 'draxatomic:kingdom_guard', {'Kingdom': String('oriana')})
    b.save_to('city/oriana/palace')


def oriana_opera():
    rnd = random.Random(7)
    b = Build(B, 44, B)
    ground(b, B, 'oriana', rnd)
    roads(b, B, 'oriana', rnd)
    paving(b, B, ['smooth_quartz', 'polished_diorite'], rnd, 4, 4, 63, 63)
    X0, Z0, X1, Z1 = 10, 8, 54, 52
    b.walls(X0, 1, Z0, X1, 20, Z1, 'smooth_sandstone')
    b.box(X0 + 1, 1, Z0 + 1, X1 - 1, 19, Z1 - 1, 'air')
    b.box(X0, 20, Z0, X1, 20, Z1, 'smooth_sandstone')
    # fachada con columnas doradas y escalinata
    for x in range(X0 + 2, X1 - 1, 4):
        b.box(x, 1, Z1 + 1, x, 14, Z1 + 1, 'quartz_pillar', axis='y')
        b.set(x, 15, Z1 + 1, 'gold_block')
        for y in (4, 5, 6, 10, 11, 12):
            b.set(x + 2, y, Z1, 'glass') if x + 2 < X1 else None
    b.box(X0, 15, Z1 + 1, X1, 16, Z1 + 1, 'smooth_sandstone')
    for k in range(4):
        b.box(X0 + 6, 0, Z1 + 2 + k, X1 - 6, 0, Z1 + 2 + k, 'smooth_quartz')
    for x in range(X0 + 18, X1 - 17):
        b.box(x, 1, Z1, x, 6, Z1, 'air')
    b.door(X0 + 19, 1, Z1, 'dark_oak', 'south', 'left'); b.door(X1 - 19, 1, Z1, 'dark_oak', 'south', 'right')
    # estatuas doradas en el techo
    for x in (X0 + 1, X1 - 1):
        b.box(x, 21, Z1 - 1, x, 23, Z1 - 1, 'gold_block')
    # cúpula verde de cobre sobre la sala
    cx, cz = (X0 + X1) // 2, (Z0 + Z1) // 2 - 4
    for y in range(0, 12):
        r = (144 - y * y) ** 0.5
        for x in range(cx - 12, cx + 13):
            for z in range(cz - 12, cz + 13):
                d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
                if r - 1 <= d <= r:
                    b.set(x, 21 + y, z, 'oxidized_copper')
    b.box(cx, 33, cz, cx, 35, cz, 'gold_block')
    # escenario y butacas
    b.box(X0 + 6, 1, Z0 + 2, X1 - 6, 2, Z0 + 9, 'dark_oak_planks')
    b.box(X0 + 6, 3, Z0 + 2, X1 - 6, 14, Z0 + 2, 'red_wool')
    for z in range(Z0 + 14, Z1 - 4, 2):
        for x in range(X0 + 4, X1 - 3):
            if x != cx and x != cx + 1:
                stairs(b, x, 1 + (z - Z0 - 14) // 4, z, 'dark_oak', 'north')
    for x in range(X0 + 6, X1 - 5, 8):
        b.set(x, 18, cz, 'lantern', hanging='true', waterlogged='false')
    for k in range(3):
        villager(b, X0 + 10 + k * 10, 3, Z0 + 5)
    b.save_to('city/oriana/opera')


def oriana_gallery():
    rnd = random.Random(8)
    b = Build(C, 16, C)
    ground(b, C, 'oriana', rnd)
    roads(b, C, 'oriana', rnd)
    paving(b, C, ['smooth_quartz', 'polished_diorite'], rnd, 4, 4, 31, 31)
    # pirámide de vidrio en el patio del museo
    cx, cz = 18, 18
    for i in range(8):
        for x in range(cx - 7 + i, cx + 8 - i):
            for z in range(cz - 7 + i, cz + 8 - i):
                if x in (cx - 7 + i, cx + 7 - i) or z in (cz - 7 + i, cz + 7 - i):
                    b.set(x, 1 + i, z, 'glass')
    b.box(cx - 1, 1, cz + 7, cx + 1, 2, cz + 7, 'air')
    for x in (6, 30):
        for z in (6, 30):
            lamp(b, x, 1, z, 'oriana')
    villager(b, 12, 1, 26); villager(b, 24, 1, 10)
    b.save_to('city/oriana/gallery')


def oriana_mitsugoshi():
    rnd = random.Random(9)
    b = Build(C, 24, C)
    ground(b, C, 'oriana', rnd)
    roads(b, C, 'oriana', rnd)
    mitsugoshi_building(b, 6, 6, 29, 27, 3, rnd, clerks=2)
    b.save_to('city/oriana/mitsugoshi')


def build_all():
    midgar_castle()
    midgar_plaza('midgar')
    midgar_plaza('oriana')
    midgar_cathedral()
    midgar_mitsugoshi_hq()
    midgar_academy()
    oriana_palace()
    oriana_opera()
    oriana_gallery()
    oriana_mitsugoshi()
