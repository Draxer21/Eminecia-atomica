"""La Base del Culto (64x64): un laboratorio de quimeras en la superficie.

Patio con guardias (Niños del Culto), el laboratorio con jaulas de quimeras, celdas de
Poseídos fallidos, mesas de experimentos y, en el centro, el foso de la Quimera Alfa.

Uso: python3 tools/cult_base.py   (requiere: pip install nbtlib)
"""
import os, random, sys
sys.path.insert(0, os.path.dirname(__file__))
from structures import Build
from furniture import chest
from city import entity
from nbtlib import Compound, List, Int, String, Byte

B = 64
NO = dict(north='false', south='false', east='false', west='false', waterlogged='false')


def bars(b, x, y0, y1, z, **conn):
    p = dict(NO)
    p.update({k: 'true' for k in conn})
    for y in range(y0, y1 + 1):
        b.set(x, y, z, 'iron_bars', **p)


def loot_chest(b, x, y, z, facing, items):
    chest(b, x, y, z, facing)
    b.nbt[(x, y, z)] = Compound({'Items': List[Compound]([
        Compound({'Slot': Byte(i), 'id': String(iid), 'count': Int(n)}) for i, (iid, n) in enumerate(items)])})


def laboratory():
    rnd = random.Random(77)
    b = Build(B, 20, B)
    # Suelo del patio: losas oscuras y tierra quemada.
    for x in range(B):
        for z in range(B):
            b.set(x, 0, z, rnd.choice(['coarse_dirt', 'packed_mud', 'tuff', 'coarse_dirt', 'cobbled_deepslate']))
    # Camino desde la puerta sur de la muralla hasta el laboratorio.
    for x in range(29, 37):
        for z in range(50, B):
            b.set(x, 0, z, rnd.choice(['polished_deepslate', 'deepslate_tiles', 'polished_deepslate']))
    # Antorchas de alma y estandartes del Culto en el patio.
    for x, z in ((27, 56), (38, 56), (27, 61), (38, 61), (8, 8), (55, 8), (8, 55), (55, 55)):
        b.set(x, 1, z, 'polished_blackstone_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
        b.set(x, 2, z, 'soul_lantern', hanging='false', waterlogged='false')
    for x, z in ((6, 30), (57, 30)):
        b.box(x, 1, z, x, 4, z, 'crying_obsidian')
        b.set(x, 5, z, 'soul_campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')

    # ---------------------------------------------------------------- el laboratorio (40x40)
    X0, Z0, X1, Z1, H = 12, 10, 51, 49, 11
    b.box(X0, 0, Z0, X1, 0, Z1, 'polished_deepslate')
    for y in range(1, H + 1):
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if x in (X0, X1) or z in (Z0, Z1):
                    corner = x in (X0, X1) and z in (Z0, Z1)
                    pillar = (x - X0) % 8 == 0 or (z - Z0) % 8 == 0
                    if corner or pillar:
                        name = 'polished_blackstone_bricks'
                    elif y in (4, 5, 8) and not pillar:
                        name = 'purple_stained_glass'
                    else:
                        name = rnd.choice(['deepslate_bricks', 'deepslate_bricks', 'cracked_deepslate_bricks', 'deepslate_tiles'])
                    b.set(x, y, z, name)
    # Techo con claraboya sobre el foso.
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            sky = 26 <= x <= 37 and 24 <= z <= 35
            b.set(x, H + 1, z, 'tinted_glass' if sky else 'deepslate_tiles')
    for x in range(X0, X1 + 1, 2):
        b.set(x, H + 2, Z0, 'deepslate_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
        b.set(x, H + 2, Z1, 'deepslate_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    for z in range(Z0, Z1 + 1, 2):
        b.set(X0, H + 2, z, 'deepslate_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
        b.set(X1, H + 2, z, 'deepslate_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    # Puerta sur (4 de ancho, 5 de alto) con el emblema del Culto encima.
    b.box(30, 1, Z1, 33, 5, Z1, 'air')
    b.box(30, 6, Z1, 33, 6, Z1, 'crimson_planks')
    b.set(31, 7, Z1, 'redstone_block'); b.set(32, 7, Z1, 'redstone_block')
    b.set(30, 7, Z1, 'chiseled_polished_blackstone'); b.set(33, 7, Z1, 'chiseled_polished_blackstone')
    # Luz interior.
    for x in range(16, 49, 8):
        for z in range(14, 47, 8):
            b.set(x, H, z, 'soul_lantern', hanging='true', waterlogged='false')

    # Jaulas de quimeras: tres por lado (este y oeste), abiertas hacia el pasillo.
    cages = []
    for k in range(3):
        z0 = 13 + k * 12
        for side in ('w', 'e'):
            cx0, cx1 = (X0 + 1, X0 + 7) if side == 'w' else (X1 - 7, X1 - 1)
            open_x = cx1 if side == 'w' else cx0
            b.box(cx0, 0, z0, cx1, 0, z0 + 8, rnd.choice(['mud', 'soul_soil']))
            for z in range(z0, z0 + 9):
                for x in range(cx0, cx1 + 1):
                    edge_z = z in (z0, z0 + 8)
                    if edge_z:
                        bars(b, x, 1, 5, z, east=True, west=True)
                    elif x == open_x and not (z0 + 3 <= z <= z0 + 5):
                        bars(b, x, 1, 5, z, north=True, south=True)
            b.box(cx0, 6, z0, cx1, 6, z0 + 8, 'polished_blackstone_bricks')
            b.set((cx0 + cx1) // 2, 1, z0 + 2, 'bone_block', axis='y')
            if rnd.random() < 0.6:
                b.set((cx0 + cx1) // 2 - 1, 1, z0 + 6, 'cobweb')
            cages.append(((cx0 + cx1) // 2, z0 + 4))
    for i, (x, z) in enumerate(cages):
        if i % 3 != 2:
            entity(b, x, 1, z, 'draxatomic:cult_chimera')
    # Celdas de Poseídos al norte (con reja delante).
    for k, x0 in enumerate((22, 29, 36)):
        b.box(x0, 1, Z0 + 1, x0 + 5, 5, Z0 + 4, 'air')
        for x in range(x0, x0 + 6):
            bars(b, x, 1, 4, Z0 + 5, east=True, west=True)
        b.set(x0 + 2, 1, Z0 + 5, 'air'); b.set(x0 + 2, 2, Z0 + 5, 'air')
        b.set(x0 + 1, 1, Z0 + 1, 'cobweb')
        entity(b, x0 + 3, 1, Z0 + 2, 'draxatomic:possessed')
    # Mesas de experimentos y cofres con el botín del Culto.
    for x, z in ((23, 44), (27, 44), (36, 44), (40, 44)):
        b.set(x, 1, z, 'brewing_stand', has_bottle_0='true', has_bottle_1='false', has_bottle_2='true')
        b.set(x + 1, 1, z, 'cauldron')
        b.set(x - 1, 1, z, 'amethyst_cluster', facing='up', waterlogged='false')
    loot_chest(b, 17, 1, 47, 'north', [('draxatomic:magic_crystal', 3), ('draxatomic:diablos_drop', 4), ('minecraft:emerald', 6)])
    loot_chest(b, 46, 1, 47, 'north', [('draxatomic:chimera_shard', 4), ('draxatomic:blood_vial', 2), ('minecraft:golden_apple', 1)])

    # ---------------------------------------------------------------- el foso de la Quimera Alfa
    cx, cz = 31, 29
    for x in range(cx - 7, cx + 9):
        for z in range(cz - 7, cz + 9):
            d = max(abs(x - cx - 0.5), abs(z - cz - 0.5))
            if d <= 5.5:
                b.set(x, 0, z, rnd.choice(['blackstone', 'polished_blackstone', 'gilded_blackstone', 'blackstone']))
            elif d <= 7.5:
                b.set(x, 0, z, 'polished_blackstone_bricks')
                if (x + z) % 3 == 0:
                    b.set(x, 1, z, 'polished_blackstone_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    # Pilares de cristal en las esquinas del foso.
    for x, z in ((cx - 6, cz - 6), (cx + 7, cz - 6), (cx - 6, cz + 7), (cx + 7, cz + 7)):
        b.box(x, 1, z, x, 6, z, 'amethyst_block')
        b.set(x, 7, z, 'amethyst_cluster', facing='up', waterlogged='false')
    b.set(cx, 1, cz - 4, 'respawn_anchor', charges='4')
    entity(b, cx, 1, cz, 'draxatomic:chimera_alpha')

    # ---------------------------------------------------------------- guardias
    entity(b, 31, 1, 54, 'draxatomic:cult_child_3')
    entity(b, 34, 1, 54, 'draxatomic:cult_child_3')
    entity(b, 32, 1, 46, 'draxatomic:cult_child_2')
    entity(b, 25, 1, 38, 'draxatomic:cult_child_1')
    entity(b, 10, 1, 40, 'draxatomic:diablos_cultist', {'Kind': Int(1)})
    entity(b, 53, 1, 20, 'draxatomic:diablos_cultist', {'Kind': Int(0)})
    b.save_to('city/cult_base/laboratory')


if __name__ == '__main__':
    laboratory()
