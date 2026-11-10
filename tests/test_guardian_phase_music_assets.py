"""Validate the actual distributed phase audio against its authoring manifest."""
from pathlib import Path
import hashlib
import json
import struct

ROOT = Path(__file__).resolve().parents[1]
AUDIO = ROOT / "resourcepacks/src/assets/copimine/sounds/end_rift"


def test_all_six_arrangements_are_distinct_finite_45_second_ogg_streams():
    manifest = json.loads((AUDIO / "guardian_music_v2_manifest.json").read_text())
    tracks = manifest["tracks"]
    assert [t["phase"] for t in tracks] == ["awakening", "hunt", "rift", "overload", "rage", "last_seal"]
    assert [t["pulseBpm"] for t in tracks] == [76, 88, 100, 112, 124, 136]
    assert len({t["sha256"] for t in tracks}) == 6
    for track in tracks:
        data = (AUDIO / track["filename"]).read_bytes()
        assert hashlib.sha256(data).hexdigest() == track["sha256"]
        offset = 0
        granule = 0
        rate = None
        while offset < len(data):
            assert data[offset:offset+4] == b"OggS"
            count = data[offset+26]
            header = 27 + count
            length = header + sum(data[offset+27:offset+header])
            body = data[offset+header:offset+length]
            if body.startswith(b"\x01vorbis"):
                rate = struct.unpack_from("<I", body, 12)[0]
            value = struct.unpack_from("<Q", data, offset+6)[0]
            if value != 0xFFFFFFFFFFFFFFFF:
                granule = max(granule, value)
            offset += length
        assert rate and granule == 45 * rate
        assert offset == len(data)
        assert track["durationSeconds"] == 45
        assert 0 < track["rms"] < track["peak"] < 1
