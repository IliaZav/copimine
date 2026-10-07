"""Original 128px wave glyphs with fine cuts, luminous cores and soft Rift halos."""
from pathlib import Path
import argparse
import hashlib
import json
import math
from PIL import Image, ImageDraw, ImageFilter
try:
    from resourcepacks.tools.deterministic_png import png_bytes
except ModuleNotFoundError:
    from deterministic_png import png_bytes

ROOT=Path(__file__).resolve().parents[2]
OUTPUTS=[ROOT/'CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/wave_combat_glyphs.png',
         ROOT/'resourcepacks/src/assets/copimine/textures/item/wave_combat_glyphs.png']
MANIFEST=ROOT/'docs/end-rift/WAVES_AI_ASSETS.json'
TILES=['dash','snare','pulse','salvo','web','freeze','recover','reflect']
TILE_SIZE=128

class GlyphDraw:
    """Vector coordinates are authored in logical units, rasterized at 128px."""
    def __init__(self,image): self.draw=ImageDraw.Draw(image)
    def __getattr__(self,name):
        def paint(coordinates,**options):
            if isinstance(coordinates[0],(int,float)): scaled=[v*4 for v in coordinates]
            else: scaled=[(x*4,y*4) for x,y in coordinates]
            if 'width' in options: options['width']*=4
            return getattr(self.draw,name)(scaled,**options)
        return paint

def atlas_bytes():
    atlas=Image.new('RGBA',(len(TILES)*TILE_SIZE,TILE_SIZE),(0,0,0,0))
    for tile,name in enumerate(TILES):
        im=Image.new('RGBA',(TILE_SIZE,TILE_SIZE),(0,0,0,0)); draw=GlyphDraw(im)
        ink=(255,255,255,235); dim=(165,150,190,155)
        if name=='dash':
            for y in [4,13,22]: draw.line([(5,y+6),(16,y),(26,y+6)],fill=ink,width=2)
            draw.line([(16,3),(16,29)],fill=dim,width=1)
        elif name=='snare':
            for start,end in [((4,4),(28,28)),((4,28),(28,4))]:
                draw.line([start,end],fill=dim,width=2)
            for x,y in [(8,8),(13,13),(18,18),(23,23),(8,23),(13,18),(18,13),(23,8)]:
                draw.rectangle((x-2,y-1,x+2,y+1),outline=ink)
            draw.polygon([(16,10),(22,16),(16,22),(10,16)],outline=ink)
        elif name=='pulse':
            draw.ellipse((3,3,29,29),outline=dim,width=1)
            draw.ellipse((9,9,23,23),outline=ink,width=2)
            for a in range(0,360,45):
                r=math.radians(a); draw.line([(16+10*math.cos(r),16+10*math.sin(r)),(16+14*math.cos(r),16+14*math.sin(r))],fill=ink,width=2)
        elif name=='salvo':
            for x,y in [(8,9),(16,3),(24,9)]:
                draw.line([(x,y),(x,y+18)],fill=ink,width=2)
                draw.line([(x-4,y+5),(x,y),(x+4,y+5)],fill=ink,width=2)
                draw.line([(x-3,y+16),(x,y+13),(x+3,y+16)],fill=dim,width=2)
        elif name=='web':
            for a in range(0,360,45):
                r=math.radians(a); draw.line([(16,16),(16+14*math.cos(r),16+14*math.sin(r))],fill=ink,width=1)
            for r in [6,11,14]:
                points=[(16+r*math.cos(math.radians(a)),16+r*math.sin(math.radians(a))) for a in range(0,361,45)]
                draw.line(points,fill=ink if r==11 else dim,width=1)
        elif name=='freeze':
            draw.line([(16,2),(16,30)],fill=ink,width=2)
            draw.line([(4,8),(28,24)],fill=ink,width=2); draw.line([(4,24),(28,8)],fill=ink,width=2)
            for y in [7,24]: draw.line([(11,y),(16,y+(-4 if y==7 else 4)),(21,y)],fill=ink,width=2)
            draw.polygon([(16,11),(21,16),(16,21),(11,16)],outline=dim)
        elif name=='recover':
            draw.line([(8,5),(24,5),(8,27),(24,27)],fill=ink,width=2)
            draw.line([(8,5),(24,27)],fill=dim,width=2)
            draw.line([(12,10),(20,10)],fill=dim,width=2)
        else:
            draw.polygon([(16,3),(27,13),(23,23),(16,29),(9,23),(5,13)],outline=ink)
            draw.line([(4,15),(15,15),(10,10)],fill=ink,width=2)
            draw.line([(28,20),(17,20),(22,25)],fill=ink,width=2)
        # Fine fractures and pearl-white hot spots break up a flat UI-like
        # stroke. Every mark is clipped to its own silhouette, never a square.
        alpha=im.getchannel('A')
        for y in range(TILE_SIZE):
            for x in range(TILE_SIZE):
                opacity=alpha.getpixel((x,y))
                if not opacity: continue
                bright=12 if (x*3+y*5+tile*7)%29 < 2 else 0
                pearl=round(210+25*(1-y/TILE_SIZE))+bright
                seam=(x*7+y*11+tile*13)%101 < 2
                im.putpixel((x,y),(min(255,pearl),min(255,pearl-10),255,
                                    max(55,opacity//2) if seam else opacity))
        halo=Image.new('RGBA',im.size,(160,115,255,0))
        blurred=im.getchannel('A').filter(ImageFilter.GaussianBlur(3.2))
        halo.putalpha(blurred.point(lambda a: round(a*.65) if a>=5 else 0))
        im=Image.alpha_composite(halo,im)
        atlas.paste(im,(tile*TILE_SIZE,0))
    return png_bytes(atlas)

def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--check',action='store_true'); args=parser.parse_args()
    data=atlas_bytes()
    manifest={'asset_type':'original_authored_effect_atlas','author':'CopiMine project',
        'source_url':None,'license':'Original project artwork; no third-party asset incorporated',
        'original_filename':'wave_combat_glyphs.png','sha256':hashlib.sha256(data).hexdigest(),
        'dimensions':[len(TILES)*TILE_SIZE,TILE_SIZE],'tile_size':[TILE_SIZE,TILE_SIZE],'tiles':TILES,
        'generator':'resourcepacks/tools/generate_wave_combat_glyphs.py',
        'outputs':[p.relative_to(ROOT).as_posix() for p in OUTPUTS],
        'audio':'Vanilla Minecraft Sound events only, layered by charge/release/impact; no external SFX'}
    encoded=(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    if args.check:
        for p in OUTPUTS:
            if not p.is_file() or p.read_bytes()!=data: raise SystemExit(f'Wave glyph output differs: {p}')
        if json.loads(MANIFEST.read_text(encoding='utf-8'))!=manifest: raise SystemExit('Wave glyph manifest differs')
    else:
        for p in OUTPUTS: p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(data)
        MANIFEST.write_bytes(encoded)
    print('WAVE_COMBAT_GLYPHS_OK sha256='+manifest['sha256'])

if __name__=='__main__': main()
