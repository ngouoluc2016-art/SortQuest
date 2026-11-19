"""Synthèse des effets sonores SortQuest : courts, doux, mono 22,05 kHz 16 bits."""
import math, random, struct, wave, sys, os
SR = 22050
random.seed(7)

def env(i, n, attack=0.01, release=0.6):
    t = i / n
    a = min(1.0, t / attack) if attack > 0 else 1.0
    r = 1.0 if t < 1 - release else max(0.0, (1 - t) / release)
    return a * r

def write(name, samples, gain=0.6):
    peak = max(1e-9, max(abs(s) for s in samples))
    out = os.path.join(sys.argv[1], name + ".wav")
    with wave.open(out, "w") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(32767 * gain * s / peak)) for s in samples))
    print(out, os.path.getsize(out), "octets")

def pop():  # classement : petit "pop" chaud, glissando descendant
    n = int(SR * 0.09); ph = 0.0; s = []
    for i in range(n):
        f = 900 - 500 * (i / n); ph += 2 * math.pi * f / SR
        s.append(math.sin(ph) * env(i, n, 0.03, 0.85) + 0.25 * math.sin(2 * ph) * env(i, n, 0.03, 0.9))
    return s

def whoosh():  # coffre-fort : souffle filtré qui monte puis s'éteint
    n = int(SR * 0.28); lp = 0.0; s = []
    for i in range(n):
        t = i / n
        cutoff = 0.05 + 0.35 * math.sin(math.pi * t)  # filtre passe-bas qui s'ouvre puis se ferme
        lp += cutoff * (random.uniform(-1, 1) - lp)
        s.append(lp * math.sin(math.pi * t) ** 1.5)
    return s

def trash():  # corbeille : "thud" grave et court
    n = int(SR * 0.12); ph = 0.0; s = []
    for i in range(n):
        f = 180 - 90 * (i / n); ph += 2 * math.pi * f / SR
        s.append((math.sin(ph) + 0.15 * random.uniform(-1, 1) * (1 - i / n)) * env(i, n, 0.01, 0.9))
    return s

def tick():  # seuil franchi : clic très bref
    n = int(SR * 0.018)
    return [math.sin(2 * math.pi * 2400 * i / SR) * (1 - i / n) ** 3 for i in range(n)]

def levelup():  # niveau / Express réussie : arpège montant doux (do-mi-sol-do)
    notes = [523.25, 659.25, 783.99, 1046.5]; s = []
    for k, f in enumerate(notes):
        n = int(SR * (0.11 if k < 3 else 0.32))
        for i in range(n):
            ph = 2 * math.pi * f * i / SR
            s.append((math.sin(ph) + 0.3 * math.sin(2 * ph)) * env(i, n, 0.02, 0.7 if k < 3 else 0.9))
    return s

for name, fn, g in [("sq_pop", pop, 0.55), ("sq_whoosh", whoosh, 0.5), ("sq_trash", trash, 0.6),
                    ("sq_tick", tick, 0.35), ("sq_levelup", levelup, 0.5)]:
    write(name, fn(), g)
