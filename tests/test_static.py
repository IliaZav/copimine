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


def test_initial_skeleton_matches_the_requested_route():
    html=(ROOT/'index.html').read_text(encoding='utf-8')
    css=(ROOT/'assets/react-v1.css').read_text(encoding='utf-8')
    for route in ('home','map','rules','auth','account','admin'):
        assert f'data-initial-route="{route}"' in css
        assert f'class="cm-skeleton-route cm-skeleton-{route}' in html or route in ('auth','account','admin')
    assert 'dataset.initialRoute' in html
    assert 'cm-skeleton-map-stage' in html


def test_home_uses_visual_sections_without_repeated_marketing_panels():
    source=(ROOT/'src/app.tsx').read_text(encoding='utf-8')
    gallery=(ROOT/'src/coverflow-gallery.tsx').read_text(encoding='utf-8')
    assert 'function FeatureList' not in source
    assert 'cm72-community cm72-reveal' not in source
    assert 'Описание, цена и валюта — до покупки.' not in source
    assert 'Игровые места и постройки.' not in gallery


def test_react_bootstrap_replaces_the_legacy_first_render():
    app=(ROOT/'assets/js/app.js').read_text(encoding='utf-8')
    entry=(ROOT/'src/main.tsx').read_text(encoding='utf-8')
    assert 'copimine:react-ready' in app
    assert 'window.CM?.render?.()' in app
    assert 'copimine:react-ready' in entry


def test_gallery_slide_is_centred_by_css_not_by_its_own_size():
    """Слайд центрируется CSS (inset-block + margin-inline:auto).

    Инлайновый translate(-50%,-50%) сдвигал кадр на половину его размера вверх
    и влево, и карточка налезала на hero всякий раз, когда анимация не
    запускалась (reduced motion, выключенное движение, первый кадр).
    """
    gallery=(ROOT/'src/coverflow-gallery.tsx').read_text(encoding='utf-8')
    bundle=(ROOT/'assets/js/copimine-react.v1.js').read_text(encoding='utf-8')
    assert 'translate(-50%, -50%)' not in gallery
    assert 'translate(-50%' not in bundle
    assert 'translate3d(' in gallery


def test_mapart_upload_accepts_drops_and_is_not_disabled_by_the_bridge():
    """Поле картины должно принимать и клик, и перетаскивание.

    bridge-v6.js выключал все input[type=file] без отметки, поэтому проводник
    не открывался, а перетаскивание нигде не было подключено. Поле помечено
    data-cm-upload, и у него есть настоящий серверный приём (POST /api/v6/mapart/image).
    """
    app=(ROOT/'src/app.tsx').read_text(encoding='utf-8')
    assert 'data-cm-upload' in app
    for handler in ('onDragOver={onDragOver}','onDrop={onDrop}','onDragLeave={onDragLeave}'):
        assert handler in app
    assert 'event.dataTransfer.files' in app
    assert 'preventDefault();\n    event.dataTransfer.dropEffect' in app

    bridge=(ROOT/'assets/js/bridge-v6.js').read_text(encoding='utf-8')
    assert ":not([data-cm-upload])" in bridge
    bundle=(ROOT/'assets/js/app-bundle.js').read_text(encoding='utf-8')
    assert ":not([data-cm-upload])" in bundle

    react=(ROOT/'assets/js/copimine-react.v1.js').read_text(encoding='utf-8')
    assert 'dataTransfer' in react
    assert '" is-dragging"' in react


def test_production_react_bundle_does_not_require_a_node_process_global():
    bundle=(ROOT/'assets/js/copimine-react.v1.js').read_text(encoding='utf-8')
    assert 'process.env.NODE_ENV' not in bundle
    for chunk in re.findall(r'["\']\.\/([^"\']+\.mjs)["\']',bundle):
        assert 'process.env.NODE_ENV' not in (ROOT/'assets/js'/chunk).read_text(encoding='utf-8')


def test_payment_methods_use_local_brand_marks_and_cookie_notice_stays_compact():
    payments=(ROOT/'assets/js/public-v4.js').read_text(encoding='utf-8')
    assert "assets/img/brands/sbp.webp" in payments
    assert "assets/img/brands/mir.webp" in payments
    assert (ROOT/'assets/img/brands/sbp.webp').is_file()
    assert (ROOT/'assets/img/brands/mir.webp').is_file()

    site_v6=(ROOT/'assets/js/site-v6.js').read_text(encoding='utf-8')
    rendered_donate=site_v6.split("R.donate=()=>",1)[1].split('\n',1)[0]
    assert "payBrand(id)" in rendered_donate
    assert "assets/img/brands/" in site_v6
    assert "sbp.webp" in site_v6
    assert "mir.webp" in site_v6
    payment_css=(ROOT/'assets/site-motion-v1.css').read_text(encoding='utf-8')
    assert ".payment-brand-card" in payment_css

    cookie_source=(ROOT/'assets/js/site-v6.js').read_text(encoding='utf-8')
    banner=cookie_source.split('function cookieBanner()',1)[1].split('\n',1)[0]
    assert "M.btn('Настроить','v6-cookies'" not in banner
    assert "M.btn('Принять всё','v6-cookies-all'" in banner
    assert "M.btn('Только необходимые','v6-cookies-essential'" in banner
    assert 'href="#privacy"' in banner


def test_server_actions_panel_survives_the_production_override():
    """production.js загружается последним и перезаписывает вкладку «Сервер и
    службы» целиком. Панель серверных действий живёт в bridge-v6.js, поэтому
    без явного подключения кнопки time.*, weather.* и save не отрисовывались."""
    bridge = (ROOT / 'assets/js/bridge-v6.js').read_text(encoding='utf-8')
    assert 'X.serverActionsPanel=' in bridge
    production = (ROOT / 'assets/js/production.js').read_text(encoding='utf-8')
    system = production.split('W.adminExtensions.system=')[1]
    assert 'X.serverActionsPanel' in system.split('H[')[0]
    bundle = (ROOT / 'assets/js/app-bundle.js').read_text(encoding='utf-8')
    assert bundle.count('serverActionsPanel') >= 2
