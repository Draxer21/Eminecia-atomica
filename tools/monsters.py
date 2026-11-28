"""Texturas de los monstruos nuevos (diseños originales):

- Skins de jugador (64x64) de los humanoides: Niños del Culto (3.º, 2.º y 1.º), Poseído fallido,
  bandido y luchador del foso.
- Texturas de las bestias con modelo propio (las cajas coinciden con MonstersClient.java).
- Ítems nuevos y las texturas del Caballero del Vacío (a partir del Caballero de la Mesa Redonda).

Uso: python3 tools/monsters.py [--force]   (requiere Pillow)
"""
import colorsys, os, random, sys
sys.path.insert(0, os.path.dirname(__file__))
import skins
from skins import Skin, hexc, to_modern, OUT
from PIL import Image

MON = os.path.join(OUT, 'monster')


# ------------------------------------------------------------------ humanoides

def child(rank):
    s = Skin(90 + rank)
    band = {3: '#B0001E', 2: '#C8CCD8', 1: '#D4AF37'}[rank]
    s.head('#D8B8A0')
    s.rect(8, 0, 8, 8, '#141218', 6)            # capucha
    s.rect(24, 8, 8, 8, '#141218', 6)
    for side in (0, 16):
        s.rect(side, 8, 8, 8, '#141218', 6)
    s.rect(8, 8, 8, 2, '#141218', 4)
    s.rect(8, 10, 8, 4, '#E8E4E0', 3)           # máscara blanca
    s.rect(9, 11, 2, 1, '#B0001E', 0); s.rect(13, 11, 2, 1, '#B0001E', 0)   # rendijas rojas
    s.rect(11, 12, 2, 2, '#C8C4C0', 0)
    s.rect(8, 14, 8, 2, '#141218', 4)
    s.body('#1C1A22', 6)                         # traje negro ajustado
    s.rect(20, 20, 8, 1, band, 0)
    for k in range(4 - rank):                    # franjas de rango en el pecho
        s.rect(21 + k * 2, 22, 1, 3, band, 0)
    s.rect(20, 28, 8, 1, '#3A3440', 0)           # cinturón
    s.rect(23, 28, 2, 1, band, 0)
    s.arm('#1C1A22', 6); s.rect(44, 29, 4, 3, '#2A2630', 3)
    s.rect(44, 22, 4, 1, band, 0)
    s.leg('#16141A', 6); s.rect(4, 29, 4, 3, '#0E0C10', 2)
    if rank == 1:                                # capa carmesí del primero
        s.rect(32, 20, 8, 12, '#7A0A18', 8)
        s.rect(20, 20, 1, 8, '#7A0A18', 4); s.rect(27, 20, 1, 8, '#7A0A18', 4)
    s.save(f'monster/cult_child_{rank}.png')


def possessed():
    s = Skin(95)
    s.head('#A8A2AC', 8)
    s.rect(8, 0, 8, 8, '#2A2430', 10)            # pelo enmarañado
    s.rect(24, 8, 8, 3, '#2A2430', 10)
    s.rect(9, 12, 2, 1, '#D080FF', 0); s.rect(13, 12, 2, 1, '#D080FF', 0)   # ojos violeta
    s.dot(10, 12, '#FFFFFF'); s.dot(13, 12, '#FFFFFF')
    for (x, y) in ((8, 13), (9, 14), (15, 13), (14, 14), (11, 14), (12, 15), (10, 9), (13, 10)):
        s.dot(x, y, '#3A1050')                   # venas negras
    s.rect(10, 14, 4, 1, '#2A1020', 0)
    s.body('#5A4A3A', 12)                        # túnica rasgada
    for _ in range(18):
        x, y = s.rnd.randint(20, 27), s.rnd.randint(20, 31)
        s.dot(x, y, s.rnd.choice(['#A8A2AC', '#2A1A12', '#8A3BFF']))
    s.rect(23, 23, 2, 2, '#C060FF', 0)          # cristal incrustado
    s.arm('#A8A2AC', 8)
    for (x, y) in ((44, 22), (45, 24), (46, 26), (45, 28)):
        s.dot(x, y, '#3A1050')
    s.rect(44, 20, 4, 3, '#5A4A3A', 10)
    s.leg('#3A3028', 10)
    s.save('monster/possessed.png')


def bandit():
    s = Skin(96)
    s.head('#C8946C'); s.hair('#3A2618')
    s.eyes('#2A1A10')
    s.rect(9, 11, 2, 1, '#2A1A10', 0); s.rect(13, 11, 2, 1, '#2A1A10', 0)
    s.rect(8, 13, 8, 3, '#A01A1A', 6)            # pañuelo rojo
    s.rect(8, 13, 8, 1, '#7A1010', 0)
    s.body('#6A4A2A', 10)                        # chaleco de cuero
    s.rect(22, 20, 4, 12, '#C8B898', 6)          # camisa
    s.rect(20, 27, 8, 1, '#2A1A10', 0)
    s.rect(26, 27, 1, 1, '#C9A030', 0)
    s.rect(20, 22, 1, 4, '#3A2A1A', 0)           # correa
    s.arm('#C8B898', 6); s.rect(44, 28, 4, 4, '#4A3020', 4)
    s.leg('#3A3A44', 8); s.rect(4, 29, 4, 3, '#2A1A10', 2)
    s.save('monster/bandit.png')


def fighter():
    s = Skin(97)
    s.head('#A87A58'); s.rect(8, 0, 8, 8, '#1A1410', 4)
    s.rect(24, 8, 8, 1, '#1A1410', 4)
    s.dot(9, 9, '#3A6AB0'); s.dot(10, 8, '#3A6AB0'); s.dot(11, 9, '#3A6AB0')   # tatuaje
    s.eyes('#2A1A10')
    s.rect(9, 11, 6, 1, '#3A2418', 0)
    s.rect(14, 12, 1, 3, '#7A4A38', 0)           # cicatriz
    s.rect(10, 14, 4, 1, '#5A3020', 0)
    s.body('#A87A58', 6)                         # torso desnudo
    s.rect(21, 22, 2, 2, '#946848', 0); s.rect(25, 22, 2, 2, '#946848', 0)
    s.rect(22, 25, 4, 1, '#946848', 0); s.rect(22, 27, 4, 1, '#946848', 0)
    s.rect(20, 29, 8, 3, '#8A1A1A', 4)           # faja roja
    s.arm('#A87A58', 6); s.rect(44, 27, 4, 5, '#E8E0D0', 4)   # vendas
    s.rect(40, 27, 4, 5, '#E8E0D0', 4); s.rect(48, 27, 4, 5, '#E8E0D0', 4)
    s.leg('#3A2A22', 8)
    s.save('monster/pit_fighter.png')


# ------------------------------------------------------------------ bestias (modelos propios)

class Tex:
    def __init__(self, seed):
        self.im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.px = self.im.load()
        self.rnd = random.Random(seed)

    def rect(self, x0, y0, w, h, color, noise=10, alpha=None):
        c = hexc(color) if isinstance(color, str) else color
        a = c[3] if alpha is None else alpha
        for x in range(x0, x0 + w):
            for y in range(y0, y0 + h):
                if 0 <= x < 64 and 0 <= y < 64:
                    d = self.rnd.randint(-noise, noise) if noise else 0
                    self.px[x, y] = (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)), max(0, min(255, c[2] + d)), a)

    def dot(self, x, y, color, alpha=255):
        c = hexc(color)
        self.px[x, y] = (c[0], c[1], c[2], alpha)

    def faces(self, u, v, w, h, d):
        return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
                'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}

    def box(self, u, v, w, h, d, color, noise=10, alpha=None, top=None, bottom=None):
        f = self.faces(u, v, w, h, d)
        for name, (x, y, fw, fh) in f.items():
            col = top if name == 'top' and top else bottom if name == 'bottom' and bottom else color
            self.rect(x, y, fw, fh, col, noise, alpha)
        return f

    def speckle(self, rect, colors, n, alpha=None):
        x0, y0, w, h = rect
        for _ in range(n):
            x, y = self.rnd.randrange(x0, x0 + w), self.rnd.randrange(y0, y0 + h)
            c = hexc(self.rnd.choice(colors))
            self.px[x, y] = (c[0], c[1], c[2], self.px[x, y][3] if alpha is None else alpha)

    def save(self, name):
        full = os.path.join(MON, name + '.png')
        if os.path.exists(full) and not skins.FORCE:
            print('se conserva (ya existe):', name)
            return
        os.makedirs(MON, exist_ok=True)
        self.im.save(full)
        print('textura', name)


def quadruped(name, seed, fur, back, belly, eye, eye2, horn, crystal, leg_colors, tail_tip, stitches=False):
    t = Tex(seed)
    f = t.box(0, 0, 8, 7, 14, fur, 10, top=back, bottom=belly)       # cuerpo
    if stitches:
        for side in ('right', 'left', 'top'):
            x, y, w, h = f[side]
            for _ in range(3):                                          # retazos cosidos
                px, py = t.rnd.randrange(x, x + w - 3), t.rnd.randrange(y, y + h - 2)
                t.rect(px, py, 3, 2, t.rnd.choice(leg_colors), 6)
            sx = t.rnd.randrange(x + 2, x + w - 2)
            for yy in range(y, y + h):
                t.dot(sx, yy, '#1A0E1A')
                if yy % 2 == 0:
                    t.dot(sx - 1, yy, '#C8B8A8'); t.dot(sx + 1, yy, '#C8B8A8')
    else:
        for side in ('right', 'left'):                                  # costillas marcadas
            x, y, w, h = f[side]
            for k in range(2, w - 2, 3):
                t.rect(x + k, y + 1, 1, h - 3, back, 4)
    h = t.box(0, 22, 6, 6, 6, fur, 8, top=back)                         # cabeza
    fx, fy, fw, fh = h['front']
    t.rect(fx, fy, fw, 2, back, 6)                                      # ceño
    t.dot(fx + 1, fy + 2, eye); t.dot(fx + 4, fy + 2, eye2)
    t.dot(fx + 1, fy + 3, '#1A0A0A'); t.dot(fx + 4, fy + 3, '#1A0A0A')
    s = t.box(24, 22, 3, 2, 3, belly, 6)                                # hocico
    sx, sy, _, _ = s['front']
    t.dot(sx + 1, sy, '#101010')
    j = t.box(44, 22, 3, 1, 3, '#2A0A10', 4)                            # mandíbula con colmillos
    jx, jy, _, _ = j['front']
    t.dot(jx, jy, '#F0ECE0'); t.dot(jx + 2, jy, '#F0ECE0')
    t.box(36, 22, 1, 3, 1, horn, 6)
    t.box(40, 22, 1, 3, 1, horn, 6)
    t.box(36, 26, 2, 2, 1, back, 6)
    t.box(42, 26, 2, 2, 1, back, 6)
    l = t.box(0, 36, 3, 8, 3, leg_colors[0], 8)                         # patas
    for name_ in ('front', 'right', 'left', 'back'):
        x, y, w, hh = l[name_]
        t.rect(x, y + hh - 2, w, 2, '#1A1418', 4)
        if len(leg_colors) > 1:
            t.rect(x, y + 2, w, 2, leg_colors[1], 6)
    tl = t.box(12, 36, 2, 2, 8, fur, 8, top=back)                       # cola
    for name_ in ('top', 'bottom', 'right', 'left'):
        x, y, w, hh = tl[name_]
        if name_ in ('top', 'bottom'):
            t.rect(x, y + hh - 2, w, 2, tail_tip, 6)
        else:
            t.rect(x, y, 2, hh, tail_tip, 6)
    t.box(12, 36, 2, 2, 8, fur, 0) if False else None
    c = t.box(32, 36, 2, 4, 2, crystal, 14)                             # cristales del lomo
    x, y, w, hh = c['front']
    t.rect(x, y, 1, hh, '#FFFFFF', 0, alpha=200)
    return t


def bat():
    t = Tex(201)
    t.box(0, 0, 4, 4, 6, '#3A0A12', 8, top='#2A060C')
    h = t.box(0, 10, 4, 4, 3, '#3A0A12', 6)
    fx, fy, _, _ = h['front']
    t.dot(fx, fy + 1, '#FF2030'); t.dot(fx + 3, fy + 1, '#FF2030')
    t.dot(fx + 1, fy + 3, '#F0ECE0'); t.dot(fx + 2, fy + 3, '#F0ECE0')
    t.box(14, 10, 1, 2, 1, '#5A1018', 4)
    t.box(18, 10, 1, 2, 1, '#5A1018', 4)
    for v in (18, 26):
        f = t.box(0, v, 10, 1, 6, '#6A1020', 8)
        for side in ('top', 'bottom'):
            x, y, w, hh = f[side]
            for k in range(0, w, 3):                                    # nervios del ala
                for yy in range(y, y + hh):
                    t.dot(x + k, yy, '#2A060C')
            t.rect(x, y + hh - 1, w, 1, '#2A060C', 4)
    t.save('blood_bat')


def slime_golem():
    t = Tex(202)
    gel = '#5ACB4A'
    f = t.box(0, 0, 12, 12, 10, gel, 10, alpha=170)
    for side in f.values():
        t.speckle(side, ['#8AF07A', '#3A9A2A'], 14, alpha=170)
    x, y, w, h = f['front']
    t.rect(x + 2, y + 1, 3, 2, '#B8FFB0', 6, alpha=190)                 # brillo
    t.box(0, 38, 4, 4, 4, '#E8FF60', 12)                                # núcleo (opaco)
    hd = t.box(0, 22, 8, 6, 8, gel, 10, alpha=180)
    x, y, w, h = hd['front']
    t.rect(x + 1, y + 2, 2, 2, '#0E3A0E', 0, alpha=255)
    t.rect(x + 5, y + 2, 2, 2, '#0E3A0E', 0, alpha=255)
    t.rect(x + 2, y + 4, 4, 1, '#1A5A1A', 0, alpha=230)
    for u in (32, 48):
        hh = 12 if u == 32 else 8
        b = t.box(u, 22, 4, hh, 4, gel, 10, alpha=170)
        for side in b.values():
            t.speckle(side, ['#8AF07A', '#3A9A2A'], 4, alpha=170)
    t.save('slime_golem')


def shadow_beast():
    t = Tex(203)
    dark, hl = '#141218', '#2A2238'
    f = t.box(0, 0, 8, 12, 5, dark, 6, top=hl)
    for side in ('back', 'right', 'left'):
        t.speckle(f[side], [hl, '#3A2A52'], 10)
    x, y, w, h = f['back']
    for yy in range(y, y + h, 2):
        t.rect(x + 3, yy, 2, 1, '#3A3046', 2)                           # columna
    hd = t.box(0, 17, 7, 6, 7, dark, 6, top=hl)
    x, y, w, h = hd['front']
    t.rect(x, y + 2, 2, 1, '#FF1020', 0); t.rect(x + 5, y + 2, 2, 1, '#FF1020', 0)
    t.dot(x + 1, y + 1, '#5A0A10'); t.dot(x + 5, y + 1, '#5A0A10')
    j = t.box(28, 17, 6, 2, 6, '#1E1A24', 4)
    x, y, w, h = j['front']
    for k in range(0, w, 2):
        t.dot(x + k, y, '#E8E4F0')                                      # dientes
    t.box(52, 17, 1, 3, 1, '#3A3046', 4)
    t.box(56, 17, 1, 3, 1, '#3A3046', 4)
    a = t.box(0, 30, 3, 18, 3, dark, 6)
    for side in ('front', 'right', 'left', 'back'):
        x, y, w, h = a[side]
        t.rect(x, y + h - 2, w, 2, '#C8C4D0', 6)                        # garras
        t.speckle((x, y, w, h - 2), [hl], 5)
    l = t.box(12, 30, 3, 10, 3, dark, 6)
    for side in ('front', 'right', 'left', 'back'):
        x, y, w, h = l[side]
        t.rect(x, y + h - 1, w, 1, '#8A8494', 4)
    t.save('shadow_beast')


def mana_wraith():
    t = Tex(204)
    hd = t.box(0, 0, 8, 8, 8, '#D8C8FF', 8, alpha=210)                  # rostro de hueso pálido
    x, y, w, h = hd['front']
    t.rect(x + 1, y + 3, 2, 2, '#FFFFFF', 0, alpha=255)                 # ojos de luz
    t.rect(x + 5, y + 3, 2, 2, '#FFFFFF', 0, alpha=255)
    t.dot(x + 2, y + 4, '#C060FF'); t.dot(x + 5, y + 4, '#C060FF')
    t.rect(x + 3, y + 6, 2, 1, '#5A2AA0', 0, alpha=230)
    hood = t.box(32, 0, 8, 8, 8, '#3A1A6A', 8, alpha=235)              # capucha
    x, y, w, h = hood['front']
    t.rect(x + 1, y + 1, 6, 7, '#000000', 0, alpha=0)                   # abierta por delante
    for side in ('right', 'left', 'back', 'top'):
        t.speckle(hood[side], ['#5A2AA0', '#8A3BFF'], 6)
    b = t.box(0, 16, 8, 10, 5, '#5A2AA0', 10, alpha=160)
    x, y, w, h = b['front']
    for (dx, dy) in ((3, 2), (4, 2), (2, 4), (5, 4), (3, 6), (4, 6), (3, 8), (4, 8)):
        t.dot(x + dx, y + dy, '#E0B0FF', alpha=230)                     # runas
    for u, v, w, h, d, a in ((26, 16, 6, 7, 4, 120), (46, 16, 3, 6, 2, 70)):
        tl = t.box(u, v, w, h, d, '#7A4AD0', 12, alpha=a)
        for side in tl.values():
            t.speckle(side, ['#C090FF'], 3, alpha=a)
    a = t.box(0, 31, 3, 11, 3, '#7A4AD0', 10, alpha=150)
    for side in ('front', 'right', 'left', 'back'):
        x, y, w, h = a[side]
        t.rect(x, y + h - 2, w, 2, '#E8DCFF', 4, alpha=220)
    t.save('mana_wraith')


# ------------------------------------------------------------------ ítems

def item(name, draw):
    skins.item(name, draw)


def insignia(p):
    for y in range(2, 14):
        for x in range(3, 13):
            if abs(x - 7.5) + abs(y - 7.5) <= 6.5:
                edge = abs(x - 7.5) + abs(y - 7.5) > 5.0
                p[x, y] = hexc('#D4AF37') if edge else hexc('#1C1A22')
    for y in range(5, 11):
        p[7, y] = hexc('#B0001E'); p[8, y] = hexc('#B0001E')
    p[6, 7] = hexc('#B0001E'); p[9, 7] = hexc('#B0001E')


def fang(p):
    for y in range(2, 15):
        w = max(0, (14 - y) // 3)
        for x in range(8 - w, 9 + w):
            if 0 <= x < 16:
                p[x, y] = hexc('#F0E8D8') if x != 8 + w else hexc('#C8BCA8')
    for x in range(5, 12):
        p[x, 2] = hexc('#8A1A1A'); p[x, 3] = hexc('#6A1010')


def chimera_shard(p):
    for y in range(1, 15):
        w = 3 - abs(y - 8) // 3
        for x in range(8 - w, 8 + w):
            p[x, y] = hexc('#B070FF') if x < 8 else hexc('#7A3BD0')
    for y in range(3, 12):
        p[7, y] = hexc('#F0D8FF')
    p[5, 12] = hexc('#8A3BFF'); p[10, 4] = hexc('#8A3BFF')


def shadow_essence(p):
    rnd = random.Random(5)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 8.5) ** 2) ** 0.5
            if d < 5.5 + rnd.random():
                p[x, y] = hexc('#141218') if d > 2.5 else hexc('#3A2A52')
    p[6, 8] = hexc('#FF1020'); p[9, 8] = hexc('#FF1020')
    for (x, y) in ((7, 1), (8, 2), (6, 2), (9, 1)):
        p[x, y] = hexc('#2A2238', 180)


def mana_residue(p):
    for y in range(3, 14):
        for x in range(3, 13):
            d = ((x - 7.5) ** 2 + (y - 8.5) ** 2) ** 0.5
            if d < 5:
                p[x, y] = hexc('#8A3BFF', 200) if d > 2 else hexc('#E0C0FF', 230)
    for (x, y) in ((2, 4), (13, 6), (12, 13), (4, 13), (7, 1)):
        p[x, y] = hexc('#C090FF')


def chimera_core(p):
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6.5:
                p[x, y] = hexc('#3A1A4A') if d > 5.2 else hexc('#7A2BC0') if d > 3 else hexc('#E070FF') if d > 1.5 else hexc('#FFFFFF')
    for (x, y) in ((7, 0), (8, 0), (0, 7), (15, 8), (7, 15)):
        p[x, y] = hexc('#B070FF')


def void_sigil(p):
    for y in range(1, 15):
        for x in range(1, 15):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 5.0 < d < 6.6:
                p[x, y] = hexc('#2A1040')
            elif d <= 5.0:
                p[x, y] = hexc('#0E0614')
    for k in range(-4, 5):
        p[7 + k // 2, 7 + k] = hexc('#8A3BFF') if 0 <= 7 + k < 16 else p[0, 0]
    for k in range(-3, 4):
        p[7 + k, 7] = hexc('#C060FF')
    p[7, 7] = hexc('#FFFFFF')


# ------------------------------------------------------------------ Caballero del Vacío

def recolor(src, dst, hue, sat=1.0, dark=1.0):
    """El Caballero del Vacío: la armadura del Caballero, teñida de violeta y negro."""
    full_src = os.path.join(OUT, 'diablos_cult', src)
    full_dst = os.path.join(OUT, 'diablos_cult', dst)
    if os.path.exists(full_dst) and not skins.FORCE:
        print('se conserva (ya existe):', dst)
        return
    im = Image.open(full_src).convert('RGBA')
    px = im.load()
    for x in range(im.width):
        for y in range(im.height):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if ss > 0.15:
                hh = hue
            ll *= dark
            r2, g2, b2 = colorsys.hls_to_rgb(hh, min(1, ll), min(1, ss * sat))
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    im.save(full_dst)
    print('textura', dst)


if __name__ == '__main__':
    skins.FORCE = '--force' in sys.argv
    for r in (3, 2, 1):
        child(r)
    possessed(); bandit(); fighter()
    for n in ('cult_child_3', 'cult_child_2', 'cult_child_1', 'possessed', 'bandit', 'pit_fighter'):
        if to_modern(os.path.join(MON, n + '.png')):
            print('formato moderno:', n)
    quadruped('blood_hound', 101, '#5A1018', '#2A0A0E', '#7A2A2A', '#FF3040', '#FF3040', '#E0D8C0', '#B0001E',
              ['#4A0C14'], '#B0001E').save('blood_hound')
    quadruped('cult_chimera', 102, '#6A5A6E', '#4A3A52', '#8A7A7A', '#C060FF', '#FF3040', '#E0D8C0', '#B070FF',
              ['#7A5A3A', '#4A5A4A', '#8A6A5A'], '#4A5A4A', stitches=True).save('cult_chimera')
    quadruped('chimera_alpha', 103, '#2E2236', '#1A1222', '#4A3A52', '#E070FF', '#E070FF', '#D4AF37', '#E070FF',
              ['#3A2A46', '#5A3A2A', '#2A3A3A'], '#7A2BC0', stitches=True).save('chimera_alpha')
    bat(); slime_golem(); shadow_beast(); mana_wraith()
    item('cult_insignia', insignia); item('blood_fang', fang); item('chimera_shard', chimera_shard)
    item('shadow_essence', shadow_essence); item('mana_residue', mana_residue)
    item('chimera_core', chimera_core); item('void_sigil', void_sigil)
    recolor('round_knight.png', 'void_knight.png', 0.76, 1.1, 0.75)
    recolor('round_knight_awakened.png', 'void_knight_awakened.png', 0.78, 1.3, 0.7)
