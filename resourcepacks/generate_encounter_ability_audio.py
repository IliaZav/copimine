"""Original bounded combat one-shots. No downloaded samples or third-party audio."""
from pathlib import Path
import hashlib
import json
import numpy as np
import soundfile as sf
from generate_guardian_phase_music import normalize_ogg_serial

ROOT = Path(__file__).resolve().parent / 'src/assets/copimine'
RATE = 22050
# Duration, initial/final resonant frequency, transient brightness, noise decay.
CUES = {
    'ram_charge': (1.3, 62, 95, .16, 2.4),
    'ram_stomp': (.9, 85, 28, .42, 8),
    'ram_impact': (1.1, 120, 34, .75, 6),
    'warden_swing': (.65, 190, 55, .36, 5),
    'warden_sonic': (1.0, 410, 80, .25, 3),
    'marksman_release': (.45, 920, 240, .62, 14),
    'archmage_cast': (.85, 180, 540, .24, 4),
    'guardian_void': (1.05, 140, 38, .3, 3),
    'guardian_lance': (.65, 680, 150, .45, 7),
    'guardian_arrows': (.7, 780, 210, .48, 10),
    'guardian_mark': (.9, 230, 120, .18, 4),
    'guardian_summon': (1.15, 95, 260, .2, 3),
    'guardian_inferno': (1.1, 110, 42, .65, 3),
}

def render(name, spec):
    duration, start, end, brightness, decay = spec
    t = np.arange(round(duration * RATE)) / RATE
    u = t / duration
    phase = 2*np.pi*(start*t + .5*(end-start)*t*t/duration)
    seed = int.from_bytes(hashlib.sha256(name.encode()).digest()[:8], 'little')
    noise = np.random.default_rng(seed).normal(size=len(t))
    low = np.convolve(noise, np.ones(17)/17, mode='same')
    resonant = np.sin(phase) + .24*np.sin(phase*1.507) + .12*np.sin(phase*2.013)
    envelope = np.minimum(1,t/.012) * np.exp(-decay*u) * np.minimum(1,(duration-t)/.1)
    samples = (resonant*.55 + low*.8 + noise*brightness*np.exp(-12*u))*envelope
    samples = np.tanh(samples*1.25)
    samples *= .62/max(.001,np.max(np.abs(samples)))
    return samples.astype(np.float32)

if __name__ == '__main__':
    folder = ROOT/'sounds/end_rift/abilities'
    folder.mkdir(parents=True,exist_ok=True)
    index = json.loads((ROOT/'sounds.json').read_text(encoding='utf-8'))
    for name, spec in CUES.items():
        path = folder/f'{name}.ogg'
        samples = render(name,spec)
        with sf.SoundFile(path,'w',samplerate=RATE,channels=1,format='OGG',subtype='VORBIS') as out:
            for start in range(0,len(samples),RATE): out.write(samples[start:start+RATE])
        normalize_ogg_serial(path, int.from_bytes(hashlib.sha256(name.encode()).digest()[:4], 'little'))
        key=f'end_rift/abilities/{name}'
        index[key]={'sounds':[{'name':f'copimine:{key}','stream':False}]}
        print(name,len(samples),path.stat().st_size)
    (ROOT/'sounds.json').write_text(json.dumps(index,indent=2)+'\n',encoding='utf-8',newline='\n')
