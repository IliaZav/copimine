from pathlib import Path
from dataclasses import replace
import subprocess
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


def test_public_shop_search_matches_gameplay_aliases_materials_and_keyboard_layout():
    script = r"""
const domain = require('./frontend/assets/js/domain-v73.js');
const returnStone = {id:'return_stone', name:'Камень возвращения', material:'minecraft:echo_shard', category:'Путешествия'};
const wings = {id:'angel_wings', name:'Крылья ангела', material:'minecraft:feather', category:'Путешествия'};
const matches = (query, item) => Number.isFinite(domain.rank(query, domain.productSearchTerms(item)));
if (!matches('домой', returnStone)) throw new Error('return stone alias missing');
if (!matches('echo shard', returnStone)) throw new Error('material alias missing');
if (!matches('летать', wings)) throw new Error('gameplay alias missing');
if (!matches('ntktgjhn', returnStone)) throw new Error('keyboard-layout fallback missing');
const aliases = [
 ['zmei_gorynych','меч дракона'], ['repair_kit','починить предмет'], ['return_stone','домой'],
 ['angel_wings','летать'], ['infinite_torch','освещение'], ['combat_crossbow','телепорт к стреле'],
 ['cobblestone_trail_bow','дорожка из блоков'], ['explosive_crossbow','взрывной болт'],
 ['smena_bez_perekura_pickaxe','копать быстрее'], ['lesnoy_bespredel_axe','срубить дерево'],
 ['kopatel_transhey_shovel','поиск руды'], ['fermer_bez_sna_hoe','обработка 5 на 5'],
 ['dezhurniy_argument_sword','ослабить противника'], ['vechniy_razgon_firework','бесконечные ракеты'],
 ['eternal_totem','тотем бессмертия'], ['treasurer_chestplate','эффект при уроне'],
 ['copimine_miner_pickaxe','копать 3 на 3'], ['craftsman_hammer','подбрасывание'],
 ['batin_remen_sudnogo_dnya','удар молнией'], ['nu_ty_i_nakopal_blyat_pickaxe','закопать игрока'],
 ['kosa_nalogovoy_inspekcii','кража ар'], ['kaska_prorab_huev','защита от падения'],
 ['mne_pohuy_ya_v_tanke_vest','снижение урона'], ['ne_segodnya_suka_shield','блокировать удар'],
 ['pohuy_na_debaffy_amulet','снять дебафф'], ['vremya_platit_nalogi_clock','не платить налоги'],
 ['gde_moy_lut_blyat_compass','направление взгляда']
];
for (const [id,query] of aliases) {
 if (!matches(query, {id,aliases:[]})) throw new Error(`catalog alias missing for ${id}: ${query}`);
}
"""
    subprocess.run(
        ["node", "-e", script],
        cwd=Path(__file__).resolve().parents[1],
        check=True,
        capture_output=True,
        text=True,
    )
    app = (Path(__file__).resolve().parents[1] / "frontend" / "assets" / "js" / "app.js").read_text(encoding="utf-8")
    assert "CM73Domain.rank(query,CM73Domain.productSearchTerms(p))" in app


def test_home_motion_is_opt_in_and_has_reduced_motion_fallback():
    css = (Path(__file__).resolve().parents[1] / "frontend" / "assets" / "site-polish-v8.css").read_text(encoding="utf-8")
    assert 'html[data-motion="on"] .cm72-hero' in css
    assert "@media (prefers-reduced-motion: reduce)" in css
