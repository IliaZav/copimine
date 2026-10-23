from dataclasses import replace
from urllib.parse import urlsplit,parse_qs
from test_auth import client


def test_oauth_uses_pkce_and_state_is_bound_to_browser_and_single_use(client,monkeypatch):
    from backend import vk
    client.app.state.settings=replace(client.app.state.settings,vk_client_id='123456')
    client.get('/api/v6/session')
    response=client.get('/api/v6/auth/vk/start',follow_redirects=False)
    assert response.status_code==302
    fields=parse_qs(urlsplit(response.headers['location']).query)
    assert fields['code_challenge_method']==['S256']
    assert len(fields['code_challenge'][0])==43
    state=fields['state'][0]
    assert client.get('/api/v6/auth/vk/callback?state=wrong&code=fake&device_id=device').status_code==400
    monkeypatch.setattr(vk,'exchange_identity',lambda *args: {'id':'777001','name':'VK player'})
    callback='/api/v6/auth/vk/callback?state='+state+'&code=authorization&device_id=device'
    result=client.get(callback,follow_redirects=False)
    assert result.status_code==303,result.text
    assert client.get('/api/v6/session').json()['user']['role']=='player'
    assert client.get(callback,follow_redirects=False).status_code==400
