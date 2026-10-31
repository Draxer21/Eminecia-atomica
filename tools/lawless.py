"""La Ciudad sin Ley (128x128): barrios oscuros, taberna, mercado negro, el foso de Juggernaut
y la Torre Carmesí (64x64) donde duerme Elizabeth, la Reina de la Sangre.

Uso: python3 tools/lawless.py   (requiere: pip install nbtlib)
"""
import os, random, sys
sys.path.insert(0, os.path.dirname(__file__))
from structures import Build
from furniture import *
from city import (ground, roads, lamp, stairs, slab, villager, entity, npc, mid_house, market, C, STYLE)
from nbtlib import Int

S = 'lawless'


def dirty_ground(b, size, rnd):
    for x in range(size):
        for z in range(size):
            b.set(x, 0, z, rnd.choice(['coarse_dirt', 'mud', 'packed_mud', 'gravel', 'dirt', 'coarse_dirt']))


def red_lamp(b, x, y, z):
    b.set(x, y, z, 'dark_oak_fence', north='false', south='false', east='false', west='false', waterlogged='false')
    b.set(x, y + 1, z, 'dark_oak_fence', north='false', south='false', east='false', west='false', waterlogged='false')
    b.set(x, y + 2, z, 'red_stained_glass')
    b.set(x, y + 3, z, 'soul_lantern', hanging='false', waterlogged='false')


def grime(b, x0, z0, x1, z1, rnd, y=1, n=6):
    for _ in range(n):
        x, z = rnd.randint(x0, x1), rnd.randint(z0, z1)
        if b.get(x, y, z) in (None, 'minecraft:air'):
            b.set(x, y, z, rnd.choice(['cobweb', 'barrel', 'cobweb', 'dead_bush' if False else 'cobweb']),
                  **({'facing': 'up', 'open': 'false'} if False else {}))


def slums(name, seed):
    rnd = random.Random(seed)
    b = Build(C, 16, C)
    dirty_ground(b, C, rnd)
    roads(b, C, S, rnd)
    pal = [('spruce_planks', 'dark_oak', 'dark_oak_log'), ('cobbled_deepslate', 'spruce', 'spruce_log'),
           ('mud_bricks', 'dark_oak', 'stripped_dark_oak_log'), ('blackstone', 'deepslate_brick', 'dark_oak_log')]
    for qx, qz, door in ((5, 5, 'south'), (19, 5, 'south'), (5, 21, 'north'), (19, 21, 'north')):
        wall, roof, frame = rnd.choice(pal)
        mid_house(b, qx, qz, rnd.choice([8, 9]), 7, wall, roof, frame, rnd, door, job=False)
    # callejón con basura, telarañas y faroles rojos
    for x in range(4, C):
        b.set(x, 0, 16, 'mud'); b.set(x, 0, 17, 'coarse_dirt')
    for x, z in ((14, 15), (28, 18)):
        red_lamp(b, x, 1, z)
    for _ in range(10):
        x, z = rnd.randint(5, 30), rnd.choice([14, 15, 18, 19])
        if b.get(x, 1, z) in (None, 'minecraft:air'):
            if rnd.random() < 0.5:
                b.set(x, 1, z, 'barrel', facing='up', open='false')
            else:
                b.set(x, 1, z, 'cobweb')
    villager(b, 12, 1, 16, 'minecraft:none', 'swamp')
    if rnd.random() < 0.5:
        villager(b, 22, 1, 17, 'minecraft:none', 'taiga')
    b.save_to(f'city/{S}/{name}')


def tavern():
    rnd = random.Random(31)
    b = Build(C, 16, C)
    dirty_ground(b, C, rnd)
    roads(b, C, S, rnd)
    X0, Z0, X1, Z1 = 6, 6, 28, 26
    b.box(X0, 0, Z0, X1, 0, Z1, 'dark_oak_planks')
    b.walls(X0, 1, Z0, X1, 6, Z1, 'stripped_dark_oak_log', axis='y')
    b.walls(X0 + 1, 1, Z0 + 1, X1 - 1, 5, Z1 - 1, 'spruce_planks')
    b.box(X0 + 1, 1, Z0 + 1, X1 - 1, 6, Z1 - 1, 'air')
    b.box(X0, 7, Z0, X1, 7, Z1, 'dark_oak_planks')
    b.walls(X0, 8, Z0, X1, 8, Z1, 'dark_oak_fence', north='false', south='false', east='false', west='false', waterlogged='false')
    for x in range(X0 + 3, X1 - 1, 4):
        b.set(x, 3, Z0, 'red_stained_glass_pane' if False else 'red_stained_glass'); b.set(x, 3, Z1, 'red_stained_glass')
    b.box(16, 1, Z1, 18, 3, Z1, 'air')
    b.door(17, 1, Z1, 'dark_oak', 'south')
    # barra con barriles detrás
    b.box(X0 + 2, 1, Z0 + 4, X1 - 2, 1, Z0 + 4, 'dark_oak_planks')
    for x in range(X0 + 2, X1 - 1):
        b.set(x, 2, Z0 + 4, 'dark_oak_slab', type='bottom', waterlogged='false')
        b.set(x, 1, Z0 + 1, 'barrel', facing='south', open='false')
        if x % 3 == 0:
            b.set(x, 2, Z0 + 1, 'barrel', facing='south', open='false')
    for x in range(X0 + 3, X1 - 2, 3):
        b.set(x, 1, Z0 + 6, 'spruce_stairs', facing='north', half='bottom', shape='straight', waterlogged='false')
    # mesas
    for tx, tz in ((10, 15), (16, 15), (22, 15), (10, 21), (22, 21)):
        table(b, tx, 1, tz, 'dark_oak', 'red_carpet')
        chair(b, tx - 1, 1, tz, 'east', 'spruce'); chair(b, tx + 1, 1, tz, 'west', 'spruce')
        candles(b, tx, 2, tz, 'red', 1)
    for x in range(X0 + 4, X1 - 2, 6):
        b.set(x, 6, 18, 'soul_lantern', hanging='true', waterlogged='false')
    villager(b, 17, 1, Z0 + 2, 'minecraft:butcher', 'swamp')
    villager(b, 12, 1, 18, 'minecraft:none', 'taiga')
    npc(b, 22, 1, 19, 'mary', 0.0)
    red_lamp(b, 14, 1, Z1 + 2)
    b.save_to(f'city/{S}/tavern')


def arena():
    """El foso de Juggernaut: un ruedo hundido rodeado de gradas."""
    rnd = random.Random(32)
    b = Build(C, 12, C)
    dirty_ground(b, C, rnd)
    roads(b, C, S, rnd)
    cx, cz = 18, 18
    for x in range(5, 31):
        for z in range(5, 31):
            d = max(abs(x - cx), abs(z - cz))
            if d <= 7:
                b.set(x, 0, z, rnd.choice(['sand', 'coarse_dirt', 'red_sand']))
            elif d <= 12:
                step = d - 7
                b.box(x, 1, z, x, step, z, 'polished_blackstone_bricks')
            elif d == 13:
                b.box(x, 1, z, x, 7, z, 'blackstone')
    for x, z in ((cx - 7, cz - 7), (cx + 7, cz - 7), (cx - 7, cz + 7), (cx + 7, cz + 7)):
        b.box(x, 1, z, x, 4, z, 'nether_brick_fence', north='false', south='false', east='false', west='false', waterlogged='false')
        b.set(x, 5, z, 'soul_lantern', hanging='false', waterlogged='false')
    b.box(cx - 1, 1, cz + 8, cx + 1, 6, cz + 13, 'air')      # entrada sur
    b.box(cx - 1, 0, cz + 8, cx + 1, 0, cz + 13, 'cobbled_deepslate')
    villager(b, cx - 5, 6, cz - 11, 'minecraft:none', 'swamp'); villager(b, cx + 6, 6, cz - 11, 'minecraft:none', 'taiga')
    npc(b, cx, 1, cz + 3, 'juggernaut', 180.0)
    b.save_to(f'city/{S}/arena')


def crimson_tower():
    """La Torre Carmesí (64x64). Pisos cada 10 bloques; Crimson en el quinto y el ataúd de
    Elizabeth en el último (el centro, (31.5, 51, 31.5), lo usa Cities para despertarla)."""
    rnd = random.Random(33)
    B = 64
    b = Build(B, 92, B)
    for x in range(B):
        for z in range(B):
            b.set(x, 0, z, rnd.choice(['cobbled_deepslate', 'polished_blackstone', 'blackstone', 'cobbled_deepslate']))
    roads(b, B, S, rnd)
    # camino de entrada desde la puerta sur de la muralla
    for z in range(43, B):
        for x in range(29, 35):
            b.set(x, 0, z, 'polished_blackstone_bricks')
    for x, z in ((27, 50), (36, 50), (27, 58), (36, 58)):
        red_lamp(b, x, 1, z)
    X0, Z0, X1, Z1 = 21, 21, 42, 42
    TOP = 60
    b.walls(X0, 1, Z0, X1, TOP, Z1, 'nether_bricks')
    b.box(X0 + 1, 1, Z0 + 1, X1 - 1, TOP, Z1 - 1, 'air')
    for y in range(1, TOP + 1, 10):
        b.walls(X0, y, Z0, X1, y, Z1, 'red_nether_bricks')
    # contrafuertes en las esquinas
    for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
        b.box(x - 1, 1, z - 1, x + 1, TOP + 4, z + 1, 'polished_blackstone_bricks')
        b.set(x, TOP + 5, z, 'soul_lantern', hanging='false', waterlogged='false')
    # ventanas rojas
    for y0 in range(3, TOP, 10):
        for c in range(X0 + 3, X1 - 2, 4):
            for (x, z) in ((c, Z0), (c, Z1), (X0, c), (X1, c)):
                b.box(x, y0, z, x, y0 + 3, z, 'red_stained_glass')
    # pisos (y = 10, 20, 30, 40, 50) con hueco para la escalera de mano
    LX, LZ = X1 - 2, Z0 + 1
    for fy in range(10, TOP, 10):
        b.box(X0 + 1, fy, Z0 + 1, X1 - 1, fy, Z1 - 1, 'dark_oak_planks')
        b.set(LX, fy, LZ, 'air')
    b.box(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, 'polished_blackstone')
    for y in range(1, TOP):
        b.set(LX, y, LZ, 'ladder', facing='south', waterlogged='false')
    b.box(X0 + 1, TOP, Z0 + 1, X1 - 1, TOP, Z1 - 1, 'nether_bricks')
    b.set(LX, TOP, LZ, 'air')
    # puerta
    b.box(30, 1, Z1, 33, 6, Z1, 'air')
    b.box(29, 7, Z1, 34, 7, Z1, 'chiseled_polished_blackstone')
    # planta baja: vestíbulo con alfombra y siervos
    for z in range(Z0 + 2, Z1):
        b.set(31, 1, z, 'red_carpet'); b.set(32, 1, z, 'red_carpet')
    for z in range(Z0 + 4, Z1 - 2, 6):
        for x in (X0 + 2, X1 - 2):
            b.box(x, 1, z, x, 9, z, 'polished_blackstone_bricks')
            b.set(x, 8, z + 1 if z + 1 < Z1 else z - 1, 'soul_lantern', hanging='false', waterlogged='false')
    entity(b, 26, 1, 30, 'draxatomic:vampire_servant'); entity(b, 37, 1, 30, 'draxatomic:vampire_servant')
    # piso 1: biblioteca
    shelf_wall(b, X0 + 1, Z0 + 1, X0 + 1, Z1 - 1, 11, 14)
    shelf_wall(b, X1 - 1, Z0 + 3, X1 - 1, Z1 - 1, 11, 14)
    rug(b, 26, 27, 37, 36, 11, 'red', 'black')
    table(b, 31, 11, 31, 'dark_oak', 'red_carpet'); candles(b, 31, 12, 31, 'red', 3)
    # piso 2: depósito de sangre
    for x in range(X0 + 2, X1 - 1, 2):
        for z in (Z0 + 3, Z1 - 2):
            b.set(x, 21, z, 'barrel', facing='up', open='false')
            b.set(x, 22, z, 'red_stained_glass')
    entity(b, 30, 21, 31, 'draxatomic:vampire_servant')
    # piso 3: dormitorios (ataúdes)
    for k, x in enumerate(range(X0 + 3, X1 - 3, 4)):
        b.box(x, 31, Z0 + 4, x + 1, 31, Z0 + 6, 'black_wool')
        b.box(x, 31, Z1 - 6, x + 1, 31, Z1 - 4, 'black_wool')
    # piso 4: salón del trono de Crimson
    rug(b, 24, 24, 39, 39, 41, 'red', 'black')
    b.box(29, 41, Z0 + 2, 34, 41, Z0 + 4, 'polished_blackstone_bricks')
    b.set(31, 42, Z0 + 3, 'dark_oak_stairs', facing='south', half='bottom', shape='straight', waterlogged='false')
    b.set(32, 42, Z0 + 3, 'dark_oak_stairs', facing='south', half='bottom', shape='straight', waterlogged='false')
    b.box(31, 43, Z0 + 2, 32, 45, Z0 + 2, 'red_nether_bricks')
    for x in (26, 37):
        for z in (26, 37):
            chandelier(b, x, 49, z)
    entity(b, 31, 41, 30, 'draxatomic:crimson')
    # piso 5: la cripta de Elizabeth
    b.box(X0 + 1, 51, Z0 + 1, X1 - 1, 51, Z1 - 1, 'polished_blackstone')
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            if (x - 31.5) ** 2 + (z - 31.5) ** 2 <= 20:
                b.set(x, 51, z, 'red_nether_bricks')
    b.box(29, 52, 30, 34, 52, 33, 'red_wool')                    # el ataúd
    b.walls(29, 52, 30, 34, 52, 33, 'dark_oak_planks')
    b.box(30, 53, 31, 33, 53, 32, 'dark_oak_trapdoor', facing='north', half='bottom', open='false', powered='false', waterlogged='false')
    for x, z in ((27, 28), (36, 28), (27, 35), (36, 35)):
        candles(b, x, 52, z, 'red', 4)
    for x in range(X0 + 2, X1 - 1, 5):
        banner(b, x, 56, Z0 + 1, 'south', 'red')
    # remate: almenas y aguja
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if (x in (X0, X1) or z in (Z0, Z1)) and (x + z) % 2 == 0:
                b.set(x, TOP + 1, z, 'nether_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    k = 0
    while 25 + k <= 38 - k:
        b.walls(25 + k, TOP + 1 + k * 2, 25 + k, 38 - k, TOP + 2 + k * 2, 38 - k, 'red_nether_bricks' if k % 2 else 'nether_bricks')
        k += 1
    b.set(31, TOP + 2 + k * 2, 31, 'gold_block'); b.set(32, TOP + 2 + k * 2, 32, 'gold_block')
    b.save_to(f'city/{S}/crimson_tower')


def build_all():
    for k, name in enumerate(['slums_a', 'slums_b', 'slums_c']):
        slums(name, 300 + k)
    tavern()
    arena()
    market(S, 34, lambda b: npc(b, 18, 1, 13, 'yukime', 0.0))
    crimson_tower()


if __name__ == '__main__':
    build_all()
