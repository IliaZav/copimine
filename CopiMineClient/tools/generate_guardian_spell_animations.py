"""Distinct spell releases on the original Guardian bones, without geometry/UV edits."""
from pathlib import Path
import json
from generate_guardian_cocoon_animations import document
from generate_guardian_cast_animations import pose, NEUTRAL, CHARGED

OUTPUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/copimineclient/models/entity/end_rift_guardian/animations'

def turn(frame, yaw=0, left=None, right=None):
    frame = {bone: {key: list(value) for key, value in tracks.items()} for bone, tracks in frame.items()}
    frame['body']['rotation'][1] = yaw
    if left is not None: frame['left_hand']['rotation'] = left
    if right is not None: frame['right_hand']['rotation'] = right
    return frame

CLIPS = {
    # Open the palms around a sphere, then compress both arms through its launch.
    'spell_void_blast': [(0,CHARGED),(.08,pose(-18,10,-82,46,-28)),
        (.22,pose(16,-8,-96,4,20)),(.48,pose(8,-5,-24,8,5)),(1,NEUTRAL)],
    # One hand is the spear; the other counterbalances the twisted torso.
    'spell_rift_projectile': [(0,CHARGED),(.10,turn(pose(-7,4,-48,15,-18),-16,
        [-35,-20,-35],[-105,8,4])),(.24,turn(pose(14,-7,-58,10,18),12,
        [-25,-25,-20],[-92,0,2])),(.55,pose(4,-3,-12,6,2)),(1,NEUTRAL)],
    # A broad rack fans open across both arms before the hands recoil.
    'spell_rift_arrows': [(0,CHARGED),(.12,pose(-9,5,-72,64,-12)),
        (.28,pose(12,-4,-54,42,14)),(.52,pose(4,0,-20,22,3)),(1,NEUTRAL)],
    # Point a mark at the floor rather than miming a projectile launch.
    'spell_void_mark': [(0,CHARGED),(.12,turn(pose(20,-12,-25,14,28),-8,
        [-40,-24,-25],[-82,12,8])),(.38,pose(25,-16,-42,16,25)),
        (.62,pose(8,-5,-15,10,8)),(1,NEUTRAL)],
    'spell_summon_servants': [(0,CHARGED),(.14,pose(-20,14,-122,52,-38)),
        (.32,pose(-12,8,-90,68,-12)),(.55,pose(14,-8,-32,40,24)),(1,NEUTRAL)],
    'spell_arena_inferno': [(0,CHARGED),(.12,pose(-24,14,-132,30,-44)),
        (.26,pose(35,-20,-45,15,30)),(.48,pose(26,-15,-24,28,18)),(1,NEUTRAL)],
}

if __name__ == '__main__':
    for name, frames in CLIPS.items():
        (OUTPUT / f'{name}.animation.json').write_text(
            json.dumps(document(name, 1.0, frames), indent=2)+'\n', encoding='utf-8', newline='\n')
        print(f'{name}: {len(frames)} key poses')
