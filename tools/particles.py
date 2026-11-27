"""Texturas de las partículas propias del mod (dibujos originales en píxeles).

Uso: python3 tools/particles.py   (requiere Pillow)
"""
import json, math, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/draxatomic')
TEX = os.path.join(ROOT, 'textures/particle')
DEF = os.path.join(ROOT, 'particles')


def rgba(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def save(name, frames):
    names = []
    for i, im in enumerate(frames):
        n = f'{name}_{i}'
        im.save(os.path.join(TEX, n + '.png'))
        names.append(f'draxatomic:{n}')
    json.dump({'textures': names}, open(os.path.join(DEF, name + '.json'), 'w'), indent=2)
    print('partícula', name, len(frames), 'cuadros')


def blank(size=8):
    return Image.new('RGBA', (size, size), (0, 0, 0, 0))


def violet_spark():
    """Estrella de cuatro puntas que se encoge al apagarse."""
    frames = []
    for k, arm in enumerate((3, 3, 2, 1)):
        im = blank(); p = im.load()
        c = 3
        for d in range(-arm, arm + 1):
            a = 255 - abs(d) * 50
            p[c + d, c] = rgba('#C9A0FF', a); p[c, c + d] = rgba('#C9A0FF', a)
        p[c, c] = rgba('#FFFFFF')
        if arm >= 2:
            for (x, y) in ((c - 1, c - 1), (c + 1, c + 1), (c - 1, c + 1), (c + 1, c - 1)):
                p[x, y] = rgba('#8A3BFF', 180)
        frames.append(im)
    save('violet_spark', frames)


def shadow_smoke():
    """Bocanadas de humo negro-violáceo, de densas a deshechas."""
    rnd = random.Random(7)
    frames = []
    for k in range(6):
        im = blank(); p = im.load()
        r = 3.6 - k * 0.35
        for x in range(8):
            for y in range(8):
                d = math.hypot(x - 3.5, y - 3.5)
                if d <= r and rnd.random() > k * 0.1:
                    shade = rnd.choice(['#120A1C', '#1E1030', '#2A1640', '#0A0610'])
                    p[x, y] = rgba(shade, int(230 - k * 25 - d * 10))
        frames.append(im)
    save('shadow_smoke', frames)


def blood_drop():
    """Gotas de sangre con brillo."""
    frames = []
    for shape in ([(3, 1), (3, 2), (2, 3), (3, 3), (4, 3), (2, 4), (3, 4), (4, 4), (3, 5)],
                  [(4, 2), (3, 3), (4, 3), (3, 4), (4, 4), (5, 4), (4, 5)]):
        im = blank(); p = im.load()
        for (x, y) in shape:
            p[x, y] = rgba('#8B0010')
        x0, y0 = shape[2]
        p[x0, y0] = rgba('#E03040')
        frames.append(im)
    save('blood_drop', frames)


def light_shard():
    """Esquirlas de luz rosada (las grietas del cuerpo)."""
    frames = []
    for pts in ([(1, 6), (2, 5), (3, 4), (4, 3), (5, 2), (6, 1)],
                [(3, 0), (3, 1), (4, 2), (3, 3), (4, 4), (4, 5), (3, 6), (3, 7)],
                [(0, 3), (1, 3), (2, 4), (3, 3), (4, 4), (5, 3), (6, 4), (7, 4)]):
        im = blank(); p = im.load()
        for i, (x, y) in enumerate(pts):
            p[x, y] = rgba('#FFFFFF') if i % 3 == 1 else rgba('#FF5C85')
        frames.append(im)
    save('light_shard', frames)


def energy_petal():
    """Pétalos de energía violeta-azulada."""
    frames = []
    for tilt in (0, 1, 2):
        im = blank(); p = im.load()
        for y in range(1, 7):
            w = [0, 1, 2, 2, 1, 0][y - 1]
            for x in range(3 - w, 4 + w):
                xx = min(7, x + (tilt if y > 3 else 0))
                p[xx, y] = rgba('#7FA8FF' if (x + y) % 2 else '#B07CFF', 220)
        p[3 + tilt // 2, 3] = rgba('#FFFFFF')
        frames.append(im)
    save('energy_petal', frames)


def shockwave():
    """Anillo que se expande y se desvanece (16x16, 8 cuadros)."""
    frames = []
    for k in range(8):
        im = blank(16); p = im.load()
        r = 2 + k * 0.85
        for x in range(16):
            for y in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if abs(d - r) < 0.9:
                    a = int(255 * (1 - k / 8) * (1 - abs(d - r) / 0.9))
                    p[x, y] = rgba('#F0E0FF' if abs(d - r) < 0.4 else '#9A50FF', a)
                elif d < r - 0.9 and k < 3:
                    p[x, y] = rgba('#C9A0FF', int(90 * (1 - k / 3)))
        frames.append(im)
    save('shockwave', frames)


if __name__ == '__main__':
    os.makedirs(TEX, exist_ok=True); os.makedirs(DEF, exist_ok=True)
    violet_spark(); shadow_smoke(); blood_drop(); light_shard(); energy_petal(); shockwave()
