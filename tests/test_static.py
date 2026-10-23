from pathlib import Path
import json
import re

ROOT=Path(__file__).parents[1]/'frontend'


def test_production_entry_disables_review_and_offline_identity_fallback():
    html=(ROOT/'index.html').read_text(encoding='utf-8')
    assert 'window.CM_SERVER_MODE=true' in html
    assert 'window.CM_OFFLINE=true' not in html
    source=(ROOT/'assets/js/review-v7.js').read_text(encoding='utf-8')
    assert "fetch('/review/" not in source
    assert "username==='2'?'demo-admin'" not in source
    assert 'Q.offline=true' not in source


def test_seed_contains_no_fake_players_money_or_world_data():
    source=(ROOT/'assets/js/data.js').read_text(encoding='utf-8')
    seed=json.JSONDecoder().raw_decode(source.split('window.CM_SEED=',1)[1])[0]
    for key in ('players','orders','tickets','ledger','audit','playerSnapshots','eventLog'):
        assert seed[key]==[],key
    assert seed['session'] is None
    assert all(row.get('visibility')=='public' for row in seed['catalog'])
    assert 'demo-' not in source


def test_public_data_does_not_include_closed_event_rewards_or_offline_html():
    assert not (ROOT/'CopiMine_v7.html').exists()
    for path in [*ROOT.joinpath('data').glob('*.json'),ROOT/'assets/js/registry-v5.js',ROOT/'assets/js/registry-v6.js']:
        text=path.read_text(encoding='utf-8')
        assert 'rift_core_shard' not in text,path.name
        assert 'night_cloak' not in text,path.name
