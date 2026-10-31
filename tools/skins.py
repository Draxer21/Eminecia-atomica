"""Genera las skins (64x64, mitad superior estilo zombi) de los vampiros y de Claire poseída.

Uso: python3 tools/skins.py   (requiere Pillow)
"""
import os, random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/draxatomic/textures/entity')


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


class Skin:
    def __init__(self, seed):
        self.im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.px = self.im.load()
        self.rnd = random.Random(seed)

    def rect(self, x0, y0, w, h, color, noise=10):
        c = hexc(color) if isinstance(color, str) else color
        for x in range(x0, x0 + w):
            for y in range(y0, y0 + h):
                d = self.rnd.randint(-noise, noise) if noise else 0
                self.px[x, y] = (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)), max(0, min(255, c[2] + d)), c[3])

    def dot(self, x, y, color):
        self.px[x, y] = hexc(color) if isinstance(color, str) else color

    # cajas del modelo: (u, v, ancho, alto, fondo)
    def box(self, u, v, w, h, d, color, noise=10):
        self.rect(u + d, v, w, d, color, noise)           # arriba
        self.rect(u + d + w, v, w, d, color, noise)       # abajo
        self.rect(u, v + d, d, h, color, noise)           # derecha
        self.rect(u + d, v + d, w, h, color, noise)       # frente
        self.rect(u + d + w, v + d, d, h, color, noise)   # izquierda
        self.rect(u + 2 * d + w, v + d, w, h, color, noise)  # atrás

    def head(self, color, noise=6):
        self.box(0, 0, 8, 8, 8, color, noise)

    def body(self, color, noise=8):
        self.box(16, 16, 8, 12, 4, color, noise)

    def arm(self, color, noise=8):
        self.box(40, 16, 4, 12, 4, color, noise)

    def leg(self, color, noise=8):
        self.box(0, 16, 4, 12, 4, color, noise)

    # caras frontales
    FACE = (8, 8)
    BODY = (20, 20)
    ARM = (44, 20)
    LEG = (4, 20)

    def hair(self, color, long=False, slick=False, noise=10):
        # arriba de la cabeza y nuca completas, flequillo y costados
        self.rect(8, 0, 8, 8, color, noise)
        self.rect(24, 8, 8, 8, color, noise)           # atrás
        for side in (0, 16):                            # costados
            self.rect(side, 8, 8, 4 if not long else 8, color, noise)
        if slick:
            self.rect(8, 8, 8, 1, color, noise)
        else:
            self.rect(8, 8, 8, 2, color, noise)
            self.rect(8, 10, 1, 2, color, noise)
            self.rect(15, 10, 1, 2, color, noise)
        if long:
            self.rect(8, 10, 1, 6, color, noise)
            self.rect(15, 10, 1, 6, color, noise)
            # el pelo cae sobre la espalda
            self.rect(32, 20, 8, 7, color, noise)

    def eyes(self, color, white='#F2F2F2', y=12):
        self.dot(9, y, white); self.dot(10, y, color)
        self.dot(13, y, color); self.dot(14, y, white)

    def save(self, path):
        full = os.path.join(OUT, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        self.im.save(full)
        print('skin', path)


def servant():
    s = Skin(11)
    s.head('#8E978E'); s.hair('#1C1A1A')
    s.eyes('#E01030', white='#2A2020')
    s.rect(10, 14, 4, 1, '#3A0A10', 0)            # boca
    s.dot(11, 15, '#B0001E'); s.dot(13, 15, '#B0001E')   # sangre
    s.body('#3A3532'); s.arm('#8E978E'); s.leg('#2B2622')
    s.rect(44, 20, 4, 6, '#3A3532')                # mangas rotas
    s.rect(52, 20, 4, 6, '#3A3532')
    for _ in range(14):                            # rasgaduras y manchas
        x, y = s.rnd.randint(20, 27), s.rnd.randint(20, 31)
        s.dot(x, y, '#5A0010' if s.rnd.random() < 0.5 else '#1A1714')
    s.save('vampire/servant.png')


def crimson():
    s = Skin(12)
    s.head('#EADBD3'); s.hair('#8B0F1E', slick=True)
    s.rect(8, 9, 8, 1, '#8B0F1E', 6)
    s.eyes('#D0102A')
    s.rect(11, 14, 2, 1, '#9A5050', 0)
    s.dot(10, 15, '#F4F4F4'); s.dot(13, 15, '#F4F4F4')   # colmillos
    s.body('#141016')                              # levita negra
    s.rect(22, 20, 4, 12, '#ECE6E6', 4)            # camisa
    s.rect(23, 20, 2, 3, '#B0001E', 0)             # corbatín
    s.rect(20, 20, 1, 12, '#6A0A18', 0); s.rect(27, 20, 1, 12, '#6A0A18', 0)   # forro rojo
    s.arm('#141016'); s.rect(44, 30, 4, 2, '#EADBD3', 4)   # manos
    s.leg('#100C10'); s.rect(4, 30, 4, 2, '#2A0A10', 2)
    s.save('vampire/crimson.png')


def elizabeth():
    s = Skin(13)
    s.head('#F0E4E0'); s.hair('#D8D4E0', long=True)
    s.eyes('#E0103A')
    s.rect(11, 14, 2, 1, '#B0304A', 0)
    s.rect(9, 7, 6, 1, '#C9A030', 0)               # tiara
    s.dot(12, 6, '#B0001E')
    s.body('#5A0012')                              # vestido carmesí
    s.rect(20, 20, 8, 2, '#F0E4E0', 4)             # escote
    s.rect(20, 22, 8, 1, '#1A0006', 0)
    s.rect(23, 23, 2, 9, '#2A0008', 4)             # corsé
    s.arm('#F0E4E0'); s.rect(44, 20, 4, 4, '#5A0012'); s.rect(52, 20, 4, 4, '#5A0012')
    s.leg('#4A000E')                               # falda larga
    s.rect(4, 28, 4, 4, '#2A0008', 4)
    s.save('vampire/elizabeth.png')


def claire_aurora():
    """Claire poseída por Aurora: ojos violeta brillantes y marcas que le suben por el cuello."""
    base = Image.open(os.path.join(OUT, 'npc/claire.png')).convert('RGBA')
    px = base.load()
    for (x, y) in ((10, 12), (13, 12)):
        px[x, y] = hexc('#C060FF')
    for (x, y) in ((9, 12), (14, 12)):
        px[x, y] = hexc('#F0D8FF')
    for (x, y) in ((8, 15), (9, 14), (15, 15), (14, 14), (20, 20), (21, 21), (27, 20), (26, 21)):
        px[x, y] = hexc('#8A3BFF')
    base.save(os.path.join(OUT, 'npc/claire_aurora.png'))
    print('skin npc/claire_aurora.png')


def item(name, draw):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw(im.load())
    path = os.path.join(OUT, '..', 'item', name + '.png')
    im.save(path)
    print('item', name)


def vial(p):
    for y in range(3, 15):
        for x in range(5, 11):
            if y < 6 and not (6 <= x <= 9):
                continue
            edge = x in (5, 10) or y == 14 or (y < 6 and x in (6, 9))
            p[x, y] = hexc('#CFE6F0') if edge else (hexc('#B0001E') if y > 7 else hexc('#E8F4F8', 120))
    for x in range(6, 10):
        p[x, 2] = hexc('#7A5030')


def chalice(p):
    for x in range(3, 13):
        p[x, 3] = hexc('#D4AF37')
    for y in range(4, 9):
        for x in range(4 + (y - 4) // 2, 12 - (y - 4) // 2):
            p[x, y] = hexc('#B0001E') if y == 4 else hexc('#C9A030')
    for y in range(9, 13):
        p[7, y] = hexc('#A88420'); p[8, y] = hexc('#C9A030')
    for x in range(4, 12):
        p[x, 13] = hexc('#A88420')
    p[8, 6] = hexc('#E01030')


def crimson_blood(p):
    for y in range(2, 15):
        w = min(y - 1, 5) if y < 9 else 5 - (y - 9) // 2
        for x in range(8 - w, 8 + w):
            if 0 <= x < 16:
                p[x, y] = hexc('#8B0F1E') if (x + y) % 3 else hexc('#B0001E')
    p[6, 6] = hexc('#FF6070'); p[6, 7] = hexc('#FF6070')


def queen_blood(p):
    crimson_blood(p)
    for x in range(5, 11):
        p[x, 1] = hexc('#D4AF37')
    p[5, 0] = hexc('#D4AF37'); p[8, 0] = hexc('#D4AF37'); p[10, 0] = hexc('#D4AF37')


if __name__ == '__main__':
    servant(); crimson(); elizabeth(); claire_aurora()
    item('blood_vial', vial); item('crimson_chalice', chalice)
    item('crimson_blood', crimson_blood); item('queen_blood', queen_blood)
