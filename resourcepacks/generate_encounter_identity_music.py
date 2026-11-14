"""Original procedural End Rift cues. No third-party audio or network inputs.

Each arrangement has independent meter, harmony, lead and percussion. The same
seed yields the same source samples; OGG serials are canonicalized for packaging.
"""
from pathlib import Path
import hashlib
import json
import numpy as np
import soundfile as sf
from generate_guardian_phase_music import normalize_ogg_serial

RATE = 22050
SCORES = {
    'wave_6_ram': (96, 7, 64, (31, 38, 41, 43), (0, 7, 3, 10, 7, 2, 0), .75),
    'wave_7_chambers': (112, 5, 64, (34, 41, 37, 44), (0, 1, 7, 3, 10, 6, 12, 7), .52),
    'boss_melee_fury': (148, 4, 48, (26, 29, 33, 28), (0, 12, 7, 13, 3, 10, 6, 7), 1.0),
    'intermission_4': (68, 3, 16, (38, 45, 41, 48), (0, 7, 12, 10, 3, 7), .15),
}


def render(name, score):
    bpm, meter, duration, roots, melody, force = score
    frames = RATE * duration
    out = np.zeros((frames, 2), dtype=np.float64)
    seed = int.from_bytes(hashlib.sha256(name.encode()).digest()[:8], 'little')
    rng = np.random.default_rng(seed)
    beat = 60 / bpm

    def add(at, sample, gain, pan=0):
        start = round(at * RATE)
        if start >= frames: return
        sample = sample[:frames-start] * gain
        out[start:start+len(sample), 0] += sample * np.sqrt((1-pan)*.5)
        out[start:start+len(sample), 1] += sample * np.sqrt((1+pan)*.5)

    def note(midi, length, kind):
        t = np.arange(round(length * RATE)) / RATE
        hz = 440 * 2 ** ((midi-69)/12)
        if kind == 'bell':
            wave = np.sin(2*np.pi*hz*t) + .28*np.sin(2*np.pi*hz*2.01*t) + .12*np.sin(2*np.pi*hz*3.99*t)
            envelope = (1-np.exp(-t*170))*np.exp(-t*4)
        else:
            wave = np.sin(2*np.pi*hz*t) + .20*np.sin(2*np.pi*hz*2*t) + .09*np.sin(2*np.pi*hz*3*t)
            envelope = (1-np.exp(-t*75))*np.exp(-t*(3 if kind == 'bass' else 1.4))
        fade = min(len(t)//2, round(.025*RATE))
        if fade: envelope[-fade:] *= np.linspace(1,0,fade)
        return wave * envelope

    beats = int(duration / beat)
    for i in range(beats):
        bar, step = divmod(i, meter)
        root = roots[(bar//2) % len(roots)]
        at = i*beat
        add(at, note(root, beat*.9, 'bass'), .11 + force*.035)
        if step == 0:
            add(at, note(root+12, meter*beat*.98, 'pad'), .055, -.32)
            add(at+.018, note(root+19, meter*beat*.98, 'pad'), .038, .32)
        if name == 'boss_melee_fury' or i % 2 == 0:
            tone = root+24+melody[i % len(melody)]
            add(at+beat*.5, note(tone, beat*.8, 'bell'), .035 + force*.03, (-1 if i%2 else 1)*.42)
        t = np.arange(round(.28*RATE))/RATE
        kick = np.sin(2*np.pi*(43*t+1.5*(1-np.exp(-t*32)))) * np.exp(-t*19)
        if step == 0 or step % 2 == 0: add(at, kick, .15*force)
        if step in (2, meter-1):
            t = np.arange(round(.16*RATE))/RATE
            noise = rng.uniform(-1,1,len(t)); noise = noise-np.roll(noise,1)*.7
            add(at, noise*np.exp(-t*34), .035*force, .12)
        if force > .4:
            t = np.arange(round(.045*RATE))/RATE
            hat = rng.uniform(-1,1,len(t))*np.exp(-t*95)
            add(at+beat*.5, hat, .014*force, -.2)
    out = np.tanh(out*.95)
    peak = np.max(np.abs(out))
    out *= .78/max(.78, peak)
    fade = round(.10*RATE)
    out[:fade] *= np.linspace(0,1,fade)[:,None]
    out[-fade:] *= np.linspace(1,0,fade)[:,None]
    path = Path(__file__).parent/'src/assets/copimine/sounds/end_rift'/f'{name}.ogg'
    with sf.SoundFile(path, mode='w', samplerate=RATE, channels=2, format='OGG', subtype='VORBIS') as handle:
        for begin in range(0, len(out), RATE):
            handle.write(out[begin:begin+RATE].astype(np.float32))
    normalize_ogg_serial(path, seed & 0xffffffff)
    return {'file': path.name, 'bpm': bpm, 'beatsPerBar': meter, 'durationSeconds': duration,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'origin': 'original procedural score'}


if __name__ == '__main__':
    tracks = [render(name, score) for name, score in SCORES.items()]
    manifest = Path(__file__).parent/'src/assets/copimine/sounds/end_rift/encounter_identity_music.json'
    manifest.write_bytes((json.dumps({'authoring': 'CopiMine End Rift 2026-11-14', 'tracks': tracks}, indent=2)+'\n').encode())
    print('Rendered four original encounter scores')
