"""Small real Anvil fixtures for map coordinates, format and publication gates."""
import argparse
import gzip
import importlib.util
import io
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest import mock
import zlib

from PIL import Image

SITE = Path(__file__).resolve().parents[1]
SCRIPT = SITE / 'scripts/render_world_tiles.py'
spec = importlib.util.spec_from_file_location('copimine_map_renderer', SCRIPT)
renderer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renderer)


def string(value):
    raw = value.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw


def compound(tags):
    return b''.join(bytes([kind]) + string(name) + payload for kind, name, payload in tags) + b'\0'


def root(tags):
    return b'\x0a\0\0' + compound(tags)


def padded(values, bits):
    per = 64 // bits
    return [sum(v << (j * bits) for j, v in enumerate(values[i:i + per]))
            for i in range(0, len(values), per)]


def section(y, block):
    palette = b'\x0a' + struct.pack('>i', 1) + compound([(8, 'Name', string(block))])
    return compound([(1, 'Y', struct.pack('>b', y)),
                     (10, 'block_states', compound([(9, 'palette', palette)]))])


def chunk(cx, cz, dimension):
    min_y = -64 if dimension == 'overworld' else 0
    floor = {'overworld': 'minecraft:stone', 'nether': 'minecraft:lava', 'end': 'minecraft:end_stone'}[dimension]
    sections = [section(0, floor)]
    if dimension == 'nether':
        sections.append(section(7, 'minecraft:bedrock'))
    heights = padded([128 if dimension == 'nether' else 16 - min_y] * 256, 9)
    height_array = struct.pack('>i', len(heights)) + b''.join(struct.pack('>q', x) for x in heights)
    return root([(3, 'DataVersion', struct.pack('>i', 3955)),
                 (3, 'xPos', struct.pack('>i', cx)), (3, 'zPos', struct.pack('>i', cz)),
                 (3, 'yPos', struct.pack('>i', min_y // 16)),
                 (8, 'Status', string('minecraft:full')),
                 (9, 'sections', b'\x0a' + struct.pack('>i', len(sections)) + b''.join(sections)),
                 (10, 'Heightmaps', compound([(12, 'WORLD_SURFACE', height_array)]))])


def region_bytes(cx, cz, dimension, external=False):
    payload = zlib.compress(chunk(cx, cz, dimension))
    record = struct.pack('>I', 1 if external else len(payload) + 1) + bytes([130 if external else 2])
    if not external:
        record += payload
    sectors = (len(record) + 4095) // 4096
    header = bytearray(8192)
    offset = ((cz % 32) * 32 + (cx % 32)) * 4
    header[offset:offset + 4] = b'\0\0\x02' + bytes([sectors])
    return bytes(header) + record.ljust(sectors * 4096, b'\0'), payload


def write_region(directory, cx, cz, dimension, external=False):
    directory.mkdir(parents=True, exist_ok=True)
    raw, payload = region_bytes(cx, cz, dimension, external)
    (directory / f'r.{cx // 32}.{cz // 32}.mca').write_bytes(raw)
    if external:
        (directory / f'c.{cx}.{cz}.mcc').write_bytes(payload)


def args(path, world_name=None):
    return argparse.Namespace(input=str(path), captured_at=None, world_name=world_name, snapshot_attempts=2)


class RendererTests(unittest.TestCase):
    def test_padded_longs_keep_entry_boundaries(self):
        values = [(i * 73) % 512 for i in range(256)]
        self.assertEqual(renderer.packed(padded(values, 9), 9, range(256)), values)
        with self.assertRaises(ValueError):
            renderer.packed([0], 9, [7])

    def test_live_directory_reads_three_dimensions_and_external_chunk(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            source = base / 'server'
            write_region(source / 'world/region', -1, 2, 'overworld', external=True)
            write_region(source / 'world_nether/DIM-1/region', 0, 0, 'nether')
            write_region(source / 'world_the_end/DIM1/region', 0, 0, 'end')
            (source / 'server.properties').write_text('level-name=world\n', encoding='utf-8')
            spawn = compound([(3, 'SpawnX', struct.pack('>i', 7)), (3, 'SpawnY', struct.pack('>i', 65)),
                              (3, 'SpawnZ', struct.pack('>i', -9)), (8, 'Player', string('private-test-marker'))])
            (source / 'world/level.dat').write_bytes(gzip.compress(root([(10, 'Data', spawn)])))
            output = base / 'staging'
            run = subprocess.run([sys.executable, str(SCRIPT), str(source), '--output', str(output)],
                                 capture_output=True, text=True, timeout=60)
            self.assertEqual(run.returncode, 0, run.stderr)
            manifest = json.loads((output / 'data/map-manifest.json').read_text())
            dims = {d['id']: d for d in manifest['dimensions']}
            self.assertEqual(set(dims), {'overworld', 'nether', 'end'})
            self.assertEqual(manifest['source']['kind'], 'directory')
            self.assertEqual(manifest['source']['externalChunkFiles'], 1)
            self.assertEqual(manifest['errors'], 0)
            self.assertEqual(dims['overworld']['sourceSpawn'], {'x': 7, 'y': 65, 'z': -9})
            self.assertEqual(dims['overworld']['bounds'], {'minX': -16, 'minZ': 32, 'maxX': 0,
                                                         'maxZ': 48, 'width': 16, 'height': 16})
            for dim in dims.values():
                self.assertEqual((dim['savedChunks'], dim['chunks'], dim['surfacePixels']), (1, 1, 256))
                self.assertEqual(dim['overviewMetadata']['width'], 16)
                self.assertEqual(dim['overviewMetadata']['height'], 16)
            with Image.open(output / dims['nether']['overview']) as image:
                red, green, blue, alpha = image.convert('RGBA').getpixel((8, 8))
                self.assertGreater(red, green * 1.5)  # Lava floor, not the bedrock roof.
                self.assertEqual(alpha, 255)
            self.assertNotIn('private-test-marker', json.dumps(manifest))
            self.assertTrue(all(p.suffix in ('.json', '.webp') for p in output.rglob('*') if p.is_file()))
            # A broken external payload prevents the next revision from being published.
            (source / 'world/region/c.-1.2.mcc').write_bytes(b'not a compressed chunk')
            failed = base / 'failed'
            run = subprocess.run([sys.executable, str(SCRIPT), str(source), '--output', str(failed)],
                                 capture_output=True, text=True, timeout=60)
            self.assertNotEqual(run.returncode, 0)
            self.assertFalse((failed / 'data/map-manifest.json').exists())
            errors = json.loads((base / 'failed.render-checkpoints/render-errors.json').read_text())
            self.assertEqual(len(errors['chunkErrors']), 1)

    def test_vanilla_archive_dimensions_and_source_change_guard(self):
        with tempfile.TemporaryDirectory() as temporary:
            archive = Path(temporary) / 'world-20261016-222730.tar.gz'
            with tarfile.open(archive, 'w:gz') as handle:
                for directory, dimension in [('region', 'overworld'), ('DIM-1/region', 'nether'), ('DIM1/region', 'end')]:
                    raw, _ = region_bytes(0, 0, dimension)
                    info = tarfile.TarInfo('world/' + directory + '/r.0.0.mca')
                    info.size = len(raw)
                    handle.addfile(info, io.BytesIO(raw))
            source = renderer.RegionSource(args(archive))
            self.assertEqual({entry[0] for entry in source}, {'overworld', 'nether', 'end'})
            self.assertEqual(source.meta['capturedAt'], '2026-10-16T22:27:30')
            self.assertIn('timezone is unspecified', source.meta['captureTimeSource'])
            source.finish()
            with archive.open('ab') as handle:
                handle.write(b'changed')
            with self.assertRaisesRegex(RuntimeError, 'archive changed'):
                source.finish()

    def test_unstable_live_file_is_rejected_after_bounded_attempts(self):
        class ChangingPath:
            calls = 0
            def stat(self):
                self.calls += 1
                return argparse.Namespace(st_size=8192, st_mtime_ns=self.calls, st_ino=1)
            def open(self, mode):
                return io.BytesIO(b'\0' * 8192)
        path = ChangingPath()
        with mock.patch.object(renderer.time, 'sleep'):
            with self.assertRaisesRegex(RuntimeError, 'after 2 attempts'):
                renderer.stable_read(path, attempts=2)
        self.assertEqual(path.calls, 4)

    def test_world_name_cannot_escape_selected_server_root(self):
        for name in ('../elsewhere', '..\\elsewhere', '..'):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'direct world subdirectory'):
                renderer.RegionSource(args(Path('/unused'), name))


if __name__ == '__main__':
    unittest.main()
