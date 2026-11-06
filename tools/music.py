"""Música original del mod, generada por síntesis (sin muestras externas).

Cada pista es una composición propia y procedural: progresiones de acordes, arpegios y
melodías elegidas al azar dentro de una escala, con una semilla fija para que sea repetible.
Uso: python3 tools/music.py   (requiere numpy, scipy y ffmpeg con libvorbis)
"""
import os, subprocess, tempfile, random
import numpy as np
from scipy.signal import fftconvolve
from scipy.io import wavfile

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/draxatomic/sounds/music')


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def env(n, a, d, s, r):
    e = np.ones(n) * s
    a, d, r = int(a * SR), int(d * SR), int(r * SR)
    a = min(a, n); e[:a] = np.linspace(0, 1, a)
    d2 = min(a + d, n); e[a:d2] = np.linspace(1, s, d2 - a)
    if r > 0 and r < n:
        e[-r:] *= np.linspace(1, 0, r)
    return e


def tone(freq, dur, kind='pluck', vol=0.3):
    n = int(dur * SR)
    t = np.arange(n) / SR
    if kind == 'pluck':      # laúd / arpa
        w = sum(np.sin(2 * np.pi * freq * k * t) / k ** 1.6 for k in range(1, 7))
        e = np.exp(-t * 3.2) * env(n, 0.005, 0.05, 1, 0.05)
    elif kind == 'pad':      # cuerdas / coro suave
        w = sum(np.sin(2 * np.pi * freq * k * t * (1 + 0.002 * np.sin(2 * np.pi * 5 * t))) / k ** 2 for k in range(1, 6))
        e = env(n, min(0.6, dur / 3), 0.2, 0.8, min(0.8, dur / 3))
    elif kind == 'bell':
        w = (np.sin(2 * np.pi * freq * t) + 0.5 * np.sin(2 * np.pi * freq * 2.76 * t) + 0.25 * np.sin(2 * np.pi * freq * 5.4 * t))
        e = np.exp(-t * 1.8) * env(n, 0.002, 0.01, 1, 0.05)
    elif kind == 'bass':
        w = np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * freq * 2 * t)
        e = env(n, 0.01, 0.1, 0.7, 0.1)
    elif kind == 'lead':     # flauta / violín
        vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * t)
        w = sum(np.sin(2 * np.pi * freq * k * t * vib) / k ** 2.2 for k in range(1, 5))
        e = env(n, 0.06, 0.1, 0.8, 0.12)
    elif kind == 'drum':     # timbal grave
        f = freq * np.exp(-t * 12) + 40
        w = np.sin(2 * np.pi * np.cumsum(f) / SR)
        e = np.exp(-t * 9)
    elif kind == 'hit':      # golpe de ruido corto
        w = np.random.default_rng(int(freq)).standard_normal(n)
        e = np.exp(-t * 30)
    return (w * e * vol).astype(np.float32)


def place(buf, sig, at):
    i = int(at * SR)
    j = min(len(buf), i + len(sig))
    if i < len(buf):
        buf[i:j] += sig[:j - i]


def reverb(x, secs=2.0, mix=0.3, seed=1):
    rng = np.random.default_rng(seed)
    n = int(secs * SR)
    ir = rng.standard_normal(n) * np.exp(-np.linspace(0, 6, n))
    wet = fftconvolve(x, ir)[:len(x)]
    wet /= (np.max(np.abs(wet)) + 1e-9)
    return (1 - mix) * x / (np.max(np.abs(x)) + 1e-9) + mix * wet


SCALES = {'major': [0, 2, 4, 5, 7, 9, 11], 'minor': [0, 2, 3, 5, 7, 8, 10], 'harm': [0, 2, 3, 5, 7, 8, 11],
          'phryg': [0, 1, 3, 5, 7, 8, 10]}


def chord(root, scale, degree):
    sc = SCALES[scale]
    return [root + sc[(degree + k) % 7] + 12 * ((degree + k) // 7) for k in (0, 2, 4)]


def compose(name, seed, root, scale, bpm, bars, prog, arp_kind, pad=True, lead_kind='lead', drums=None,
            bells=False, lead_density=0.6, length_note=1, rev=0.3):
    rnd = random.Random(seed)
    np.random.seed(seed)
    beat = 60.0 / bpm
    total = bars * 4 * beat + 3
    buf = np.zeros(int(total * SR), dtype=np.float32)
    sc = SCALES[scale]
    for b in range(bars):
        deg = prog[b % len(prog)]
        ch = chord(root, scale, deg)
        t0 = b * 4 * beat
        if pad:
            for n_ in ch:
                place(buf, tone(midi(n_), 4 * beat + 0.4, 'pad', 0.07), t0)
        place(buf, tone(midi(ch[0] - 12), 4 * beat, 'bass', 0.18), t0)
        # arpegio
        pat = [0, 1, 2, 1, 0, 2, 1, 2]
        for k in range(8):
            n_ = ch[pat[k]] + (12 if k >= 4 else 0)
            place(buf, tone(midi(n_ + 12), beat * 0.9, arp_kind, 0.08), t0 + k * beat / 2)
        # melodía (a partir del 2.º compás de cada frase)
        if b % 8 >= 1:
            step = beat * length_note
            t = t0
            while t < t0 + 4 * beat - 0.01:
                if rnd.random() < lead_density:
                    deg_m = rnd.choice([deg, deg + 2, deg + 4, deg + 1, deg - 1, deg + 6])
                    n_ = root + 12 + sc[deg_m % 7] + 12 * (deg_m // 7)
                    dur = step * rnd.choice([1, 1, 2])
                    place(buf, tone(midi(n_ + 12), dur * 0.95, lead_kind, 0.11), t)
                    t += dur
                else:
                    t += step
        if bells and b % 2 == 0:
            place(buf, tone(midi(ch[2] + 24), 3.0, 'bell', 0.05), t0)
        if drums == 'march':
            for k in range(4):
                place(buf, tone(70, 0.5, 'drum', 0.25 if k % 2 == 0 else 0.12), t0 + k * beat)
        elif drums == 'battle':
            for k in range(8):
                place(buf, tone(65, 0.35, 'drum', 0.3 if k in (0, 3, 6) else 0.12), t0 + k * beat / 2)
                if k % 2 == 1:
                    place(buf, tone(900 + k, 0.08, 'hit', 0.05), t0 + k * beat / 2)
        elif drums == 'slow':
            place(buf, tone(55, 1.2, 'drum', 0.3), t0)
    out = reverb(buf, mix=rev, seed=seed)
    # fundido de entrada y salida para que encadene suave
    fade = int(2.0 * SR)
    out[:fade] *= np.linspace(0, 1, fade); out[-fade:] *= np.linspace(1, 0, fade)
    out = (out / (np.max(np.abs(out)) + 1e-9) * 0.85).astype(np.float32)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + '.wav')
        wavfile.write(wav, SR, (out * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '2',
                        os.path.join(OUT, name + '.ogg')], check=True)
    print('música', name, f'{total:.0f} s')


if __name__ == '__main__':
    # Midgar: reino luminoso, marcha tranquila en re mayor.
    compose('midgar', 101, 50, 'major', 92, 32, [0, 4, 5, 3, 0, 3, 4, 4], 'pluck', drums='march')
    # Oriana: ciudad de arte, vals de arpa en la mayor.
    compose('oriana', 102, 57, 'major', 84, 32, [0, 5, 3, 4, 0, 2, 3, 4], 'pluck', bells=True, lead_density=0.5)
    # El Santuario: coro y campanas lejanas, re menor.
    compose('sanctuary', 103, 50, 'minor', 60, 20, [0, 5, 3, 4], 'bell', lead_kind='pad', lead_density=0.35,
            length_note=2, rev=0.5)
    # Ciudad sin Ley: cuerdas graves y campanas, do menor armónico.
    compose('lawless', 104, 48, 'harm', 72, 24, [0, 5, 3, 4, 0, 1, 4, 4], 'bell', drums='slow', bells=True,
            lead_density=0.45, rev=0.4)
    # Jefe: ostinato rápido y tambores, mi frigio.
    compose('boss', 105, 52, 'phryg', 150, 40, [0, 0, 1, 0, 5, 6, 1, 0], 'pluck', drums='battle', lead_density=0.7,
            length_note=0.5, rev=0.2)
