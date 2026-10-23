from test_auth import client, login


def test_workspace_cannot_overwrite_game_balances_or_roles(client):
    session = login(client)
    response = client.post("/api/v7/workspace", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={"players": [{"id": session["user"]["id"], "AR": 999999999, "role": "owner"}], "ledger": [{"amount": 99}]})
    assert response.status_code == 400
    assert client.get("/api/v6/state").json()["players"][0].get("AR", 0) != 999999999


def test_owner_can_create_account_but_player_cannot_read_admin_data(client):
    session = login(client)
    headers = {"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}
    response = client.post("/api/v6/admin/accounts", headers=headers, json={"name": "RealPlayer", "password": "A-real-test-password-for-player", "role": "player", "reason": "Integration test account"})
    assert response.status_code == 200, response.text
    client.post("/api/v6/auth/logout", headers=headers, json={})
    session = client.get("/api/v6/session").json()
    response = client.post("/api/v6/auth/login", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={"name": "RealPlayer", "password": "A-real-test-password-for-player"})
    assert response.status_code == 200
    assert client.get("/api/v7/catalog").status_code == 403
    assert client.get("/api/v6/admin/health").status_code == 403
    assert len(client.get("/api/v6/state").json()["players"]) == 1


def test_admin_cannot_reset_own_account(client):
    session = login(client)
    response = client.post("/api/v7/recovery", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={"player": session["user"]["id"], "action": "sessions.revoke", "reason": "Test self recovery is prohibited"})
    assert response.status_code == 403


def test_support_ticket_is_persistent_and_recorded(client):
    session = login(client)
    response = client.post("/api/v6/tickets", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={"title": "A real support request", "body": "Please check this persisted support request", "type": "support"})
    assert response.status_code == 200
    assert any(t["id"] == response.json()["ticket"]["id"] for t in client.get("/api/v6/state").json()["tickets"])


def test_unlinked_player_transfer_fails_instead_of_using_demo_money(client):
    session = login(client)
    response = client.post("/api/v7/transfer", headers={"Origin": "http://testserver", "X-CSRF-Token": session["csrf"]}, json={"to": "SomePlayer", "amount": 1, "key": "test-transfer-no-game", "pin": "123456"})
    assert response.status_code in {409, 503}
    assert response.json()["error"]["code"] in {"game_link_required", "game_unavailable"}


def test_owner_can_close_account_access_and_login_is_rejected(client):
    session=login(client)
    h={'Origin':'http://testserver','X-CSRF-Token':session['csrf']}
    created=client.post('/api/v6/admin/accounts',headers=h,json={'name':'DisabledPlayer','password':'A-secure-disabled-test-password','role':'player','reason':'Testing account access control'}).json()['user']
    response=client.patch('/api/v6/admin/accounts/'+created['id'],headers=h,json={'enabled':False,'reason':'Disable integration test account'})
    assert response.status_code==200
    client.post('/api/v6/auth/logout',headers=h,json={})
    csrf=client.get('/api/v6/session').json()['csrf']
    result=client.post('/api/v6/auth/login',headers={'Origin':'http://testserver','X-CSRF-Token':csrf},json={'name':'DisabledPlayer','password':'A-secure-disabled-test-password'})
    assert result.status_code==403


def test_catalog_editor_uses_fresh_item_version_and_updates_plugin(client):
    from backend.game import catalog_version
    session=login(client);headers={"Origin":"http://testserver","X-CSRF-Token":session["csrf"]}
    item={"id":"return_stone","name":"Камень возвращения","description":"Старая подпись","category":"Инструменты","currency":"AR","price":300,"limit":0,"enabled":True,"visibility":"public","sourceType":"AR_SHOP"}
    item["version"]=catalog_version(item)
    game=client.app.state.game;game.catalog=lambda private=False:[item]
    class Legacy:
        calls=[]
        def update_shop_price_sync(self,item_id,category,price,actor,key):
            self.calls.append(("price",item_id,category,price));item["price"]=price;item["version"]=catalog_version(item)
        def update_shop_limit_sync(self,item_id,category,limit,actor,key):
            self.calls.append(("limit",item_id,category,limit));item["limit"]=limit;item["version"]=catalog_version(item)
    legacy=Legacy();game.legacy=lambda:legacy
    body={"version":item["version"],"name":"Камень возвращения","description":"Обновлённое описание","category":"Путешествия","price":350,"limit":4,"reason":"Fix product details"}
    saved=client.patch('/api/v6/admin/catalog/return_stone',headers=headers,json=body)
    assert saved.status_code==200,saved.text
    assert [call[0] for call in legacy.calls]==["price","limit"]
    assert client.app.state.database.setting('catalog_overrides',{})['return_stone']['category']=='Путешествия'
    stale=client.patch('/api/v6/admin/catalog/return_stone',headers=headers,json=body)
    assert stale.status_code==409
    assert len(legacy.calls)==2


def test_catalog_editor_cannot_change_currency_or_publish_private_items(client):
    session=login(client);headers={"Origin":"http://testserver","X-CSRF-Token":session["csrf"]}
    game=client.app.state.game
    game.catalog=lambda private=False:[{"id":"private_recipe","name":"Recipe","currency":"AR","price":1,"limit":0,"enabled":True,"visibility":"private","sourceType":"ADMIN_ONLY","version":"private-version"}]
    response=client.patch('/api/v6/admin/catalog/private_recipe',headers=headers,json={"version":"private-version","currency":"CM","reason":"Change private entry"})
    assert response.status_code in {400,409}
