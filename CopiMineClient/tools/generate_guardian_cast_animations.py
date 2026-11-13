"""Author cast beats on the user's existing Guardian bones; never rewrite geometry/UVs."""
from pathlib import Path
import argparse
import json
from generate_guardian_cocoon_animations import document

OUTPUT=Path(__file__).resolve().parents[1]/"src/main/resources/assets/copimineclient/models/entity/end_rift_guardian/animations"

def pose(body, head, arm, spread, forearm):
    return {
        "body":{"rotation":[body,0,0]},
        "head":{"rotation":[head,0,0]},
        "left_hand":{"rotation":[arm,-8 if arm else 0,-spread]},
        "right_hand":{"rotation":[arm,8 if arm else 0,spread]},
        "left_hand_low":{"rotation":[forearm,0,0]},
        "right_hand_low":{"rotation":[forearm,0,0]},
    }

NEUTRAL=pose(0,0,0,0,0)
CHARGED=pose(-12,8,-65,20,-38)
FOLLOW=pose(12,-6,-18,12,8)
CLIPS={
    "cast_charge":(1.0,[(0,NEUTRAL),(.18,pose(8,-5,12,8,-8)),
                            (.55,pose(-7,4,-40,28,-28)),(.85,CHARGED),(1,CHARGED)]),
    "cast_release":(1.0,[(0,CHARGED),(.1,pose(4,-4,-92,8,18)),
                           (.24,pose(18,-10,-52,14,24)),(.4,FOLLOW),
                           (.6,pose(5,-3,-9,6,3)),(1.0,NEUTRAL)]),
    "cast_recovery":(.6,[(0,FOLLOW),(.2,pose(5,-3,-9,6,3)),(.6,NEUTRAL)]),
    # FinalStrikePolicy impact=40 ticks: the committed two-second warning ends
    # in the release pose, not partway through an unrelated nine-second slam.
    "final_strike":(2.3,[(0,NEUTRAL),(.4,CHARGED),
        (1.2,pose(-22,12,-115,28,-45)),(1.9,pose(-24,15,-125,30,-48)),
        (2.0,pose(22,-15,-42,10,20)),(2.3,FOLLOW)]),
}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check",action="store_true")
    args=parser.parse_args()
    for name,(length,frames) in CLIPS.items():
        path=OUTPUT/f"{name}.animation.json"
        expected=(json.dumps(document(name,length,frames),indent=2)+"\n").encode("utf-8")
        if args.check:
            if not path.is_file() or path.read_bytes()!=expected:
                raise SystemExit(f"Authored cast clip differs: {path}")
        else:path.write_bytes(expected)
        print(f"{name}: {length}s, {len(frames)} key poses")

if __name__=="__main__":main()
