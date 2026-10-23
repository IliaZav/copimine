from dataclasses import replace
from pathlib import Path
import json
import time

import pytest
from test_auth import client, login


def headers(client):
    session = login(client)
    return {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}


def test_private_map_assets_require_current_admin_session(client):
    assert client.get('/maps/private/' + 'a' * 32 + '/settings.json').status_code == 401
    h = headers(client)
    assert client.get('/api/v6/admin/map').status_code == 200
    client.post('/api/v6/auth/logout', headers=h, json={})
    assert client.get('/maps/private/' + 'a' * 32 + '/index.html').status_code == 401


def test_manual_jobs_work_with_auto_disabled_and_deduplicate(client):
    h = headers(client)
    result = client.post('/api/v6/admin/map/settings', headers=h, json={'autoRefresh': False, 'intervalMinutes': 720})
    assert result.status_code == 200
    one = client.post('/api/v6/admin/map/refresh', headers=h, json={'key': 'map-manual-one', 'reason': 'Обновить карту после проверки'})
    two = client.post('/api/v6/admin/map/refresh', headers=h, json={'key': 'map-manual-two', 'reason': 'Повторное нажатие администратора'})
    assert one.status_code == two.status_code == 202
    assert one.json()['job']['id'] == two.json()['job']['id']
    assert one.json()['job']['status'] == 'queued'
    assert two.json()['duplicate'] is True
    from backend.maps import MapJobs
    reopened = MapJobs(client.app.state.database, client.app.state.settings)
    assert reopened.status()['job']['id'] == one.json()['job']['id']


def test_settings_reject_fake_marker_capability_and_too_fast_schedule(client):
    h = headers(client)
    assert client.post('/api/v6/admin/map/settings', headers=h, json={'showPlayers': True}).status_code == 409
    assert client.post('/api/v6/admin/map/settings', headers=h, json={'intervalMinutes': 1}).status_code == 400


def test_public_map_has_no_private_ids_or_host_paths(client):
    response = client.get('/api/v6/map')
    assert response.status_code == 200
    assert 'admin-overworld' not in response.text
    assert '/opt/' not in response.text
    assert response.json()['players'] == []
    assert len(response.json()['dimensions']) == 3


def source_tree(root):
    root.mkdir()
    (root / 'index.html').write_text('<html>BlueMap</html>')
    (root / 'settings.json').write_text(json.dumps({'maps': [{'id': 'public-overworld'}, {'id': 'admin-overworld'}]}))
    for map_id in ('public-overworld', 'admin-overworld'):
        folder = root / 'maps' / map_id
        folder.mkdir(parents=True)
        (folder / 'settings.json').write_text(json.dumps({'name': map_id}))
        (folder / 'textures.json').write_text('{}')
        (folder / 'tiles').mkdir()
        (folder / 'tiles' / 'tile.prbm').write_bytes(b'realistic-tile-test-data')
    return root


def test_publication_is_immutable_scoped_and_reuses_unchanged_blobs(tmp_path):
    from backend.maps import MapStorage
    source = source_tree(tmp_path / 'source')
    store = MapStorage(tmp_path / 'published', minimum_free=0)
    first = store.publish(source, 'a' * 32)
    public = store.root / 'versions' / ('a' * 32) / 'public'
    assert 'admin-overworld' not in (public / 'settings.json').read_text()
    assert not (public / 'maps' / 'admin-overworld').exists()
    before = (public / 'maps/public-overworld/tiles/tile.prbm').read_bytes()
    (source / 'maps/public-overworld/tiles/tile.prbm').write_bytes(b'changed-after-publication')
    assert (public / 'maps/public-overworld/tiles/tile.prbm').read_bytes() == before
    second = store.publish(source, 'b' * 32)
    assert store.current()['version'] == 'b' * 32
    assert second['reusedFiles'] > 0
    assert first['version'] == 'a' * 32


def test_invalid_staging_and_low_disk_preserve_previous_publication(tmp_path):
    from backend.maps import MapStorage, PublicationError
    source = source_tree(tmp_path / 'source')
    store = MapStorage(tmp_path / 'published', minimum_free=0)
    store.publish(source, 'a' * 32)
    (source / 'maps/public-overworld/settings.json').write_text('{invalid')
    with pytest.raises(PublicationError):
        store.publish(source, 'b' * 32)
    assert store.current()['version'] == 'a' * 32
    with pytest.raises(PublicationError):
        MapStorage(store.root, minimum_free=10**25).publish(source, 'c' * 32)
    assert store.current()['version'] == 'a' * 32


def test_scheduler_persists_due_time_and_off_switch(client):
    from backend.maps import MapJobs
    jobs = MapJobs(client.app.state.database, client.app.state.settings)
    now = int(time.time())
    assert jobs.schedule(now) is None  # First boot schedules 12 hours ahead.
    assert jobs.status()['nextScheduledAt'] >= now + 43199
    assert jobs.schedule(now + 43201)
    assert jobs.schedule(now + 43202) is None
    jobs.configure({'autoRefresh': False}, {'id': 'system', 'name': 'test'})
    assert jobs.schedule(now + 86402) is None


def test_asset_path_cannot_cross_scope_or_escape(tmp_path):
    from backend.maps import MapStorage, PublicationError
    source = source_tree(tmp_path / 'source')
    store = MapStorage(tmp_path / 'published', minimum_free=0)
    store.publish(source, 'a' * 32)
    for asset in ['../private/settings.json', 'maps/admin-overworld/settings.json', '%2e%2e/private', '/etc/passwd', 'maps\\admin-overworld\\settings.json']:
        with pytest.raises(PublicationError):
            store.asset('public', 'a' * 32, asset)


def test_bluemap_translation_compiler_policy_is_scoped_and_private_files_not_cached(client,tmp_path):
    from backend.maps import MapStorage
    source=source_tree(tmp_path/'viewer')
    store=MapStorage(tmp_path/'published',minimum_free=0)
    store.publish(source,'a'*32)
    client.app.state.settings=replace(client.app.state.settings,map_root=store.root)
    headers(client)
    response=client.get('/maps/private/'+'a'*32+'/index.html')
    assert response.status_code==200
    assert response.headers['Cache-Control']=='private, no-store'
    assert "script-src 'self' 'unsafe-eval'" in response.headers['Content-Security-Policy']
    assert 'unsafe-eval' not in client.get('/').headers.get('Content-Security-Policy','')


def test_unchanged_publication_does_not_copy_all_source_files_again(tmp_path):
    from backend.maps import MapStorage
    source=source_tree(tmp_path/'source')
    store=MapStorage(tmp_path/'published',minimum_free=0)
    store.publish(source,'a'*32)
    again=store.publish(source,'b'*32)
    assert again['sourceBytesRead']==0
