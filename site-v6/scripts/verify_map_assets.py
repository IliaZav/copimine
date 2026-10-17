#!/usr/bin/env python3
"""Verify every published tile, coverage count and overview against its manifest."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path

from PIL import Image


def verify(root):
    root = Path(root).resolve()
    manifest_path = root / 'data/map-manifest.json'
    raw_manifest = manifest_path.read_bytes()
    manifest = json.loads(raw_manifest)
    assert manifest['schemaVersion'] == 1 and manifest['errors'] == 0
    expected = set()
    dimensions = []
    total_bytes = 0
    for dim in manifest['dimensions']:
        bounds = dim['bounds']
        assert bounds['width'] == bounds['maxX'] - bounds['minX']
        assert bounds['height'] == bounds['maxZ'] - bounds['minZ']
        assert dim['savedChunks'] == dim['chunks'] + dim['skippedUnfinishedChunks'] + dim['emptySurfaceChunks']
        finest_pixels = 0
        tile_count = 0
        for level in dim['tiles']['levels']:
            assert level['tileCount'] == len(level['coordinates'])
            assert len({tuple(c) for c in level['coordinates']}) == len(level['coordinates'])
            assert level['blocksPerPixel'] == 2 ** (dim['tiles']['maxZoom'] - level['zoom'])
            for x, z in level['coordinates']:
                relative = dim['tiles']['template'].format(zoom=level['zoom'], x=x, z=z)
                assert relative not in expected
                expected.add(relative)
                path = (root / relative).resolve()
                assert path.is_relative_to(root)
                with Image.open(path) as image:
                    image.load()
                    assert image.size == (manifest['tileSize'], manifest['tileSize'])
                    if level['zoom'] == dim['tiles']['maxZoom']:
                        histogram = image.convert('RGBA').getchannel('A').histogram()
                        finest_pixels += sum(histogram[1:])
                tile_count += 1
                total_bytes += path.stat().st_size
        assert finest_pixels == dim['surfacePixels'], (dim['id'], finest_pixels, dim['surfacePixels'])
        overview = dim['overview']
        expected.add(overview)
        path = (root / overview).resolve()
        assert path.is_relative_to(root)
        meta = dim['overviewMetadata']
        with Image.open(path) as image:
            image.load()
            assert image.size == (meta['width'], meta['height'])
        for axis, pixels in (('width', meta['width']), ('height', meta['height'])):
            assert abs(pixels * meta['blocksPerPixel'] - bounds[axis]) < meta['blocksPerPixel'] or not bounds[axis]
        total_bytes += path.stat().st_size
        dimensions.append({'id': dim['id'], 'regionFiles': dim['regionFiles'],
                           'savedChunks': dim['savedChunks'], 'surfaceChunks': dim['chunks'],
                           'surfacePixels': finest_pixels, 'tiles': tile_count,
                           'overviewSize': [meta['width'], meta['height']], 'bounds': bounds})
    actual = {p.relative_to(root).as_posix() for p in (root / 'assets/map').rglob('*.webp')}
    assert actual == expected, {'missing': sorted(expected - actual), 'unexpected': sorted(actual - expected)}
    return {'verifiedAt': datetime.now(timezone.utc).isoformat(), 'result': 'passed',
            'manifestSHA256': hashlib.sha256(raw_manifest).hexdigest(),
            'sourceSHA256': manifest['source']['sha256'], 'sourceKind': manifest['source']['kind'],
            'images': len(expected), 'imageBytes': total_bytes,
            'regionFiles': sum(d['regionFiles'] for d in dimensions),
            'savedChunks': sum(d['savedChunks'] for d in dimensions),
            'surfaceChunks': sum(d['surfaceChunks'] for d in dimensions),
            'errors': manifest['errors'], 'dimensions': dimensions,
            'checks': ['All WebP files fully decoded', 'Exact manifest inventory',
                       'Every tile is 512 by 512 pixels', 'Finest-level alpha coverage equals source surface counts',
                       'Saved/visible/unfinished/empty chunk counts reconcile',
                       'Exclusive bounds and rectangular overview dimensions agree']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    result = verify(args.root)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
