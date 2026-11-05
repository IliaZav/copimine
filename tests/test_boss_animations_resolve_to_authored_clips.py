import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / 'tools' / 'minecraft-26.3' / 'client' / 'src' / 'main' / 'java' / 'me' / 'copimine' / 'client'


def test_every_boss_animation_reaches_an_authored_clip():
    # The supplied asset ships seven clips but the server names twenty-five
    # animations. Any id without a clip used to render the identity pose, so the
    # boss looked frozen during every spell, cast beat, phase shift and final
    # strike. Each id must now resolve to a clip the artist actually drew.
    catalog = (CLIENT / 'BossAnimationId.java').read_text(encoding='utf-8')
    ids = [m.group(1) for m in re.finditer(r'^ {4}([A-Z_]+)\(', catalog, re.M)]
    ids = [i for i in ids if i not in ('UNKNOWN', 'NONE')]

    player = (CLIENT / 'UserEndBossAnimationPlayer.java').read_text(encoding='utf-8')
    clips = set(re.findall(r'load\(clips, "[^"]+", "([A-Z_]+)"', player))

    fallback = player.split('private static String clipFor', 1)[1].split('GuardianPose sample(', 1)[0]
    mapped = set(re.findall(r'"([A-Z_]+)"', fallback))

    uncovered = [i for i in ids if i not in clips and i not in mapped]
    assert not uncovered, 'these animations would freeze on the identity pose: ' + ', '.join(uncovered)


def test_missing_clip_no_longer_returns_the_identity_pose():
    player = (CLIENT / 'UserEndBossAnimationPlayer.java').read_text(encoding='utf-8')
    sample = player.split('static ChameleonGuardianGeometry.GuardianPose sample(', 1)[1]
    assert 'CLIPS.get(clipFor(animationId))' in sample, (
        'sample() must resolve through clipFor(), otherwise an unmapped id silently '
        'falls back to the identity pose'
    )


def test_blend_follows_the_resolved_clip():
    player = (CLIENT / 'UserEndBossAnimationPlayer.java').read_text(encoding='utf-8')
    blend = player.split('static float blendTicks(', 1)[1].split('}', 1)[0]
    assert 'clipFor(toAnimation)' in blend and 'clipFor(fromAnimation)' in blend, (
        'two different spell names share one authored clip; blending the same clip '
        'against itself would stutter the rig every beat'
    )
