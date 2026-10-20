"""Quality and reproducibility gates for the authored wave effect atlas."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / 'CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/wave_combat_glyphs.png'
SERVER = ROOT / 'resourcepacks/src/assets/copimine/textures/item/wave_combat_glyphs.png'

def test_effect_glyphs_have_detail_transparency_and_distinct_silhouettes():
    assert CLIENT.read_bytes() == SERVER.read_bytes()
    with Image.open(CLIENT) as atlas:
        assert atlas.mode == 'RGBA' and atlas.size == (1024, 128)
        tiles = [atlas.crop((i*128,0,(i+1)*128,128)) for i in range(8)]
        assert len({hashlib.sha256(tile.tobytes()).hexdigest() for tile in tiles}) == 8
        for tile in tiles:
            alpha = tile.getchannel('A')
            assert alpha.getextrema()[0] == 0 and alpha.getextrema()[1] >= 200
            assert len(set(alpha.get_flattened_data() if hasattr(alpha,'get_flattened_data') else alpha.getdata())) > 32
            assert tile.getpixel((0,0))[3] == 0, 'No opaque square background in world or near the camera'

def test_effect_atlas_source_hash_and_generator_match_current_outputs():
    metadata = json.loads((ROOT / 'docs/end-rift/WAVES_AI_ASSETS.json').read_text(encoding='utf-8'))
    assert metadata['sha256'] == hashlib.sha256(CLIENT.read_bytes()).hexdigest()
    assert metadata['dimensions'] == [1024,128] and metadata['tile_size'] == [128,128]
    result = subprocess.run([sys.executable,str(ROOT/'resourcepacks/tools/generate_wave_combat_glyphs.py'),'--check'],capture_output=True,text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    builder = (ROOT/'resourcepacks/build-resourcepack.py').read_text(encoding='utf-8')
    assert 'assets/copimine/textures/item/wave_combat_glyphs.png' in builder
