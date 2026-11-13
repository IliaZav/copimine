"""Deterministic Minecraft cuboid meshes using the original generated material atlas.

The PNG is an authored input. This generator never repaints it or the Guardian skin.
"""
import argparse
import json
import math
from pathlib import Path

FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def box(x, y, z, sx, sy, sz, tile, rotation=None):
    col, row = tile % 4, tile // 4
    uv = [col * 4 + .04, row * 4 + .04, col * 4 + 3.96, row * 4 + 3.96]
    element = {'from': [round(x - sx / 2, 4), round(y - sy / 2, 4), round(z - sz / 2, 4)],
               'to': [round(x + sx / 2, 4), round(y + sy / 2, 4), round(z + sz / 2, 4)],
               'faces': {face: {'uv': uv, 'texture': '#surface'} for face in FACES}}
    if rotation:
        axis, angle = rotation
        element['rotation'] = {'origin': [x, y, z], 'axis': axis, 'angle': angle, 'rescale': False}
    return element


def crown(tile, accent):
    pieces = [box(8, 8, 8, 4, 6, 4, tile, ('y', 45)),
              box(8, 8, 8, 1.4, 8, 1.4, accent, ('y', 45))]
    for index in range(8):
        angle = index * math.pi / 4
        x, z = 8 + math.cos(angle) * 4, 8 + math.sin(angle) * 4
        pieces.append(box(x, 7 + index % 2, z, 1.4, 4 + index % 3, 1.4, tile, ('y', 45)))
        pieces.append(box(x, 10 + index % 2, z, .7, 1.5, .7, accent, ('y', 45)))
    return pieces


def meshes():
    result = {'void_core': crown(0, 1), 'inferno_core': crown(6, 7)}
    lance = [box(8, 8, 7, 1.6, 1.6, 11, 2, ('z', 45)),
             box(8, 8, 12, 5, 5, 2, 3, ('z', 45)),
             box(8, 8, 14, 3, 3, 2, 2, ('z', 45)),
             box(8, 8, 15.5, 1, 1, 1, 14, ('z', 45))]
    for side in (-1, 1):
        lance += [box(8 + side * 2.6, 8, 6, 4, .8, 4, 2, ('y', side * 22.5)),
                  box(8, 8 + side * 2.6, 6, .8, 4, 4, 3, ('x', side * 22.5))]
    result['rift_lance'] = lance
    result['echo_shard'] = [box(8 + side * 3, 8 + side, 8, 2.2, 7 - abs(side), 2.2, 11, ('z', side * 22.5))
                           for side in (-1, 0, 1)]
    result['echo_shard'] += [box(8, 8, 8, 6.5, .8, 6.5, 8, ('y', 45))]
    seal = [box(8, 8, 8, 2.4, 5, 2.4, 14, ('y', 45))]
    for x in (4, 12):
        for z in (4, 12):
            seal.append(box(x, 8, z, 1.2, 10, 1.2, 12))
    for y in (3, 13):
        seal += [box(8, y, 4, 8, 1.1, 1.1, 13), box(8, y, 12, 8, 1.1, 1.1, 13),
                 box(4, y, 8, 1.1, 1.1, 8, 13), box(12, y, 8, 1.1, 1.1, 8, 13)]
    result['final_seal'] = seal
    iris = [box(8, 8, 8, 2, 2, 2, 14, ('z', 45))]
    for index in range(8):
        a = index * math.pi / 4
        iris.append(box(8 + math.cos(a) * 4, 8 + math.sin(a) * 4, 8,
                        2.6 if index % 2 == 0 else 1.3, 1.3 if index % 2 == 0 else 2.6,
                        1.5, 12, ('z', 45 if index % 2 else 0)))
        iris.append(box(8 + math.cos(a) * 2.5, 8 + math.sin(a) * 2.5, 8.6, .8, .8, .7, 1))
    result['chest_iris'] = iris
    result['kagune_splinter'] = [box(7.5, 4, 8, 3, 5, 3, 9, ('z', -22.5)),
                                 box(9, 8, 8, 2.2, 5, 2.2, 9, ('z', 22.5)),
                                 box(9.2, 11.5, 8, 1, 3, 1, 10, ('z', -22.5)),
                                 box(8, 3.3, 8, 5.5, 1, 5.5, 9, ('y', 45))]
    result['fault_fragment'] = [box(8, 5, 8, 6, 3, 6, 15, ('y', 45)),
                               box(7, 8, 8, 3, 4, 3, 0, ('z', -22.5)),
                               box(10, 7, 9, 2, 2, 2, 6, ('z', 22.5))]
    return result


def outputs():
    # Private entity geometry uses a standalone texture. Never put the authored
    # non-POT material sheet in the shared Minecraft block/item texture atlas.
    return {f'assets/copimineclient/geometry/guardian_spells/{name}.json': {'elements': elements}
            for name, elements in meshes().items()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] / 'src/main/resources')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for relative, document in outputs().items():
        expected = (json.dumps(document, indent=2) + '\n').encode('utf-8')
        path = args.output / relative
        if args.check:
            if not path.exists() or path.read_bytes() != expected:raise SystemExit(f'Generated mesh differs: {path}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(expected)
    print(f'Authored {len(meshes())} bounded standalone spell meshes')


if __name__ == '__main__':main()
