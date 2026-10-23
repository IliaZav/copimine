from pathlib import Path
from dataclasses import replace
from test_auth import client


def test_live_list_catalog_adds_new_items_and_never_sells_admin_rewards(client,tmp_path):
    game=client.app.state.game
    game.settings=replace(game.settings,game_root=tmp_path)
    source=tmp_path/'copimine-artifacts';source.mkdir()
    (source/'items.yml').write_text('items:\n  - id: fresh_server_item\n    name: Fresh item\n    material: DIAMOND\n    price_ar: 123\n    source: AR_SHOP\n  - id: night_cloak\n    name: Closed reward\n    material: LEATHER_CHESTPLATE\n    price_ar: 0\n    source: ADMIN_ONLY\n',encoding='utf-8')
    rows=game.catalog()
    assert any(x['id']=='fresh_server_item' and x['price']==123 for x in rows)
    assert all(x['id']!='night_cloak' for x in rows)
    assert len(rows)==1
