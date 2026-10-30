"""Muebles y decoración para los interiores de las capitales."""
import random


def table(b, x, y, z, wood='oak', top='white_carpet'):
    b.set(x, y, z, f'{wood}_fence', north='false', south='false', east='false', west='false', waterlogged='false')
    b.set(x, y + 1, z, top)


def chair(b, x, y, z, facing, wood='oak'):
    b.set(x, y, z, f'{wood}_stairs', facing=facing, half='bottom', shape='straight', waterlogged='false')


def dining(b, x0, z0, length, y=1, wood='spruce', axis='x', cloth='white_carpet'):
    """Mesa larga con sillas a ambos lados."""
    for k in range(length):
        x, z = (x0 + k, z0) if axis == 'x' else (x0, z0 + k)
        table(b, x, y, z, wood, cloth)
        if axis == 'x':
            chair(b, x, y, z - 1, 'south', wood); chair(b, x, y, z + 1, 'north', wood)
        else:
            chair(b, x - 1, y, z, 'east', wood); chair(b, x + 1, y, z, 'west', wood)


def rug(b, x0, z0, x1, z1, y, color, border=None):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            b.set(x, y, z, f'{border if (edge and border) else color}_carpet')


def plant(b, x, y, z, rnd):
    b.set(x, y, z, rnd.choice(['potted_red_tulip', 'potted_fern', 'potted_azure_bluet', 'potted_oxeye_daisy',
                               'potted_poppy', 'potted_dandelion', 'potted_spruce_sapling']))


def shelf_wall(b, x0, z0, x1, z1, y0, y1):
    """Pared de libreros (rectángulo de una cara)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                b.set(x, y, z, 'bookshelf')


def chest(b, x, y, z, facing='north'):
    b.set(x, y, z, 'chest', facing=facing, type='single', waterlogged='false')


def ladder(b, x, y0, y1, z, facing):
    """Escalera de mano; abre un hueco en los pisos que atraviesa."""
    for y in range(y0, y1 + 1):
        b.set(x, y, z, 'ladder', facing=facing, waterlogged='false')


def chandelier(b, x, y, z, arms=True):
    """Candelabro colgante: barras de hierro y faroles."""
    b.set(x, y, z, 'iron_bars', north='false', south='false', east='false', west='false', waterlogged='false')
    b.set(x, y - 1, z, 'lantern', hanging='true', waterlogged='false')
    if arms:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            b.set(x + dx, y - 1, z + dz, 'lantern', hanging='false', waterlogged='false')


def banner(b, x, y, z, facing, color='red'):
    b.set(x, y, z, f'{color}_wall_banner', facing=facing)


def candles(b, x, y, z, color='white', n=3):
    b.set(x, y, z, f'{color}_candle', candles=str(n), lit='true', waterlogged='false')


def bed(b, x, y, z, facing, color='red'):
    """Cama con la cabecera en (x, z) mirando a {facing}; los pies van en la dirección opuesta."""
    dx, dz = {'north': (0, 1), 'south': (0, -1), 'east': (-1, 0), 'west': (1, 0)}[facing]
    b.set(x, y, z, f'{color}_bed', facing=facing, part='head', occupied='false')
    b.set(x + dx, y, z + dz, f'{color}_bed', facing=facing, part='foot', occupied='false')


def armor_stand(b, x, y, z, rot, head=None, chest_=None, legs=None, feet=None, hand=None):
    from nbtlib import Compound, List, Double, Int, Float, String, Byte
    eq = {}
    for slot, item in (('head', head), ('chest', chest_), ('legs', legs), ('feet', feet), ('mainhand', hand)):
        if item:
            eq[slot] = Compound({'id': String(item), 'count': Int(1)})
    nbt = {'id': String('minecraft:armor_stand'), 'ShowArms': Byte(1), 'Rotation': List[Float]([Float(rot), Float(0)])}
    if eq:
        nbt['equipment'] = Compound(eq)
    b.entities.append(Compound({'pos': List[Double]([Double(x + 0.5), Double(y), Double(z + 0.5)]),
                                'blockPos': List[Int]([Int(x), Int(y), Int(z)]), 'nbt': Compound(nbt)}))


def furnish_room(b, x0, z0, x1, z1, y, rnd, kind='home', wood='spruce'):
    """Amuebla un cuarto rectangular (interior, sin paredes) según su tipo."""
    w, d = x1 - x0 + 1, z1 - z0 + 1
    if w < 3 or d < 3:
        return
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    if kind == 'home':
        rug(b, cx - 1, cz - 1, cx + 1, cz + 1, y, rnd.choice(['red', 'blue', 'brown', 'green']), 'white')
        table(b, cx, y, cz, wood, 'white_carpet')
        b.set(cx, y + 1, cz, 'candle', candles='2', lit='true', waterlogged='false')
        chair(b, cx - 1, y, cz, 'east', wood); chair(b, cx + 1, y, cz, 'west', wood)
        b.set(x0, y, z0, 'bookshelf'); b.set(x0, y + 1, z0, 'bookshelf')
        plant(b, x1, y, z0, rnd)
        b.set(x1, y, z1, 'furnace', facing='west', lit='false')
    elif kind == 'bedroom':
        bed(b, x0, y, cz, 'west', rnd.choice(['red', 'blue', 'white', 'purple', 'cyan']))
        b.set(x0, y, cz - 1, 'barrel', facing='up', open='false'); plant(b, x0, y + 1, cz - 1, rnd)
        rug(b, cx, z0, x1 - 1, z1, y, rnd.choice(['light_gray', 'pink', 'light_blue']))
        b.set(x1, y, z0, 'bookshelf'); b.set(x1, y, z1, 'crafting_table')
    elif kind == 'office':
        for zz in range(z0 + 1, z1, 3):
            table(b, cx, y, zz, 'dark_oak', 'brown_carpet')
            chair(b, cx - 1, y, zz, 'east', 'dark_oak')
            b.set(cx + 1, y, zz, 'lectern', facing='west', has_book='false', powered='false')
        shelf_wall(b, x0, z0, x0, z1, y, y + 2)
        plant(b, x1, y, z0, rnd); plant(b, x1, y, z1, rnd)
    elif kind == 'library':
        for zz in range(z0, z1 + 1, 3):
            shelf_wall(b, x0, zz, x1 - 2, zz, y, y + 2)
        table(b, x1, y, cz, 'oak', 'red_carpet')
        chair(b, x1, y, cz + 1, 'north', 'oak')
    elif kind == 'dorm':
        for zz in range(z0, z1, 3):
            bed(b, x0, y, zz, 'west', 'blue')
            if x1 - x0 >= 4:
                bed(b, x1, y, zz, 'east', 'blue')
        for zz in range(z0 + 1, z1, 3):
            chest(b, cx, y, zz, 'north')
    elif kind == 'storage':
        for x in range(x0, x1 + 1, 2):
            chest(b, x, y, z0, 'south'); b.set(x, y, z1, 'barrel', facing='up', open='false')
        b.set(cx, y, cz, 'crafting_table')
