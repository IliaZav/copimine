"""Security and consistency regressions, executed against the real WSGI application."""
import base64
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import io
import json
from pathlib import Path
import tempfile
import unittest
import uuid
from unittest.mock import patch
from server.common import APIError, Config
from server.core import Service
from server.http import Application, COOKIE
from server.security import new_totp_secret, totp_code, adapter_signature, check_password
from server.storage import encoded, identifier, now_ms
from server.workers import claim_actions, verify_adapter_envelope, publish_map, run_map_jobs, worker_once

PASSWORD="Test Password! 4893456"
class BackendTests(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory()
        self.base=Path(self.temporary.name)
        self.site=self.base/"public"
        private=self.site/"server"/"private"
        private.mkdir(parents=True)
        (self.site/"data").mkdir()
        self.public={"id":"sword","name":"Sword","description":"Test fixture","price":100,"currency":"AR","category":"Tools","enabled":True,"visibility":"public","limit":2,"image":"assets/custom/sword.png"}
        hidden=dict(self.public,id="narcotic_recipe_fixture",name="Private fixture",visibility="hidden",sourceType="AR_SHOP")
        admin=dict(self.public,id="staff_item",name="Staff fixture",visibility="admin",sourceType="ADMIN_ONLY")
        (private/"catalog.json").write_text(json.dumps([self.public,hidden,admin]),encoding="utf-8")
        (self.site/"data"/"catalog-v6-public.json").write_text(json.dumps([self.public]),encoding="utf-8")
        (private/"materials.json").write_text(json.dumps([{"id":"stone","name":"Stone","giveable":True},{"id":"air","giveable":False}]),encoding="utf-8")
        (private/"admin-actions.json").write_text(json.dumps({"actions":[{"id":"fun.wave","scope":"server","permission":"server.manage","params":{}},{"id":"tphere","scope":"player","permission":"players.manage"}],"excluded":[{"id":"tphere"}]}),encoding="utf-8")
        (self.site/"server-index.html").write_text("<!doctype html><title>Safe runtime</title>")
        config=Config(self.site,self.base/"state","https://copimine.test",adapter_url="http://127.0.0.1:9998/operations",
            adapter_secret="a-secure-adapter-fixture-secret-1234567890",
            adapter_capabilities={"ban","ban.ip","mute","give","inventory.remove","inventory.refresh","sanction.revoke","effect.revert","purchase.deliver","fly","fun.wave"})
        self.svc=Service(config)
        self.app=Application(self.svc)
        self.owner=self.account("Owner","owner")
        self.player=self.account("Player","player")
        self.other=self.account("Other","player")

    def tearDown(self):
        self.temporary.cleanup()

    def account(self,name,role):
        secret=new_totp_secret() if role!="player" else None
        uid=self.svc.bootstrap_user(name,name.lower()+"@example.test",PASSWORD,role,secret)
        self.svc.link_player(uid,str(uuid.uuid4()),name)
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE users SET ar=1000,cm=1000 WHERE id=?",(uid,))
            row=conn.execute("SELECT * FROM users WHERE id=?",(uid,)).fetchone()
            result=self.svc._new_session(conn,row,mfa=role!="player")
        result["secret"]=secret
        return result

    def http(self,path,method="GET",body=None,bundle=None,csrf=True,origin="https://copimine.test",extra=None):
        raw=json.dumps(body or {}).encode()
        env={"REQUEST_METHOD":method,"PATH_INFO":path,"QUERY_STRING":"","CONTENT_TYPE":"application/json",
            "CONTENT_LENGTH":str(len(raw)),"wsgi.input":io.BytesIO(raw),"wsgi.errors":io.StringIO(),"wsgi.url_scheme":"https",
            "REMOTE_ADDR":"127.0.0.1","HTTP_ORIGIN":origin}
        if "?" in path:
            env["PATH_INFO"],env["QUERY_STRING"]=path.split("?",1)
        if bundle:
            env["HTTP_COOKIE"]=COOKIE+"="+bundle["token"]
            if csrf:
                env["HTTP_X_CSRF_TOKEN"]=bundle["session"]["csrf"]
        if extra:
            env.update(extra)
        result={}
        def start(status,headers):
            result.update(status=int(status.split()[0]),headers=dict(headers))
        data=b"".join(self.app(env,start))
        result["raw"]=data
        if result["headers"]["Content-Type"].startswith("application/json"):
            result["body"]=json.loads(data)
        return result

    def action(self,action,params=None,key=None,target=None,bundle=None):
        return self.svc.player_action((target or self.player)["user"]["id"],
            {"action":action,"params":params or {},"reason":"Regression test reason","idempotencyKey":key or identifier()},
            (bundle or self.owner)["user"],(bundle or self.owner)["session"])

    def test_guest_has_no_identity_or_admin_authority(self):
        response=self.http("/api/v6/session")
        self.assertEqual(response["status"],200)
        self.assertIsNone(response["body"]["user"])
        cookie=response["headers"]["Set-Cookie"]
        for flag in ("HttpOnly","Secure","SameSite=Lax"):
            self.assertIn(flag,cookie)
        forged=self.http("/api/v6/admin/actions",extra={"HTTP_AUTHORIZATION":"Bearer owner","HTTP_X_ROLE":"owner"})
        self.assertEqual(forged["status"],401)
        self.assertEqual(self.http("/api/v6/admin/actions",bundle=self.player)["status"],403)

    def test_csrf_and_origin_also_protect_login(self):
        body={"name":"Player","password":PASSWORD}
        self.assertEqual(self.http("/api/v6/auth/login","POST",body,self.player,csrf=False)["status"],403)
        self.assertEqual(self.http("/api/v6/auth/login","POST",body,self.player,origin="https://other.test")["status"],403)
        self.assertEqual(self.http("/api/v6/auth/login","POST",body,self.player)["status"],200)

    def test_registration_cannot_choose_role(self):
        response=self.http("/api/v6/auth/register","POST",{"name":"Intruder","email":"i@example.test","password":PASSWORD,"role":"owner"},self.player)
        self.assertEqual(response["status"],400)
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT count(*) FROM users WHERE role='owner'").fetchone()[0],1)

    def test_unknown_role_session_fails_closed(self):
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE users SET role='' WHERE id=?",(self.player["user"]["id"],))
        session,user=self.svc.authenticate(self.player["token"])
        self.assertIsNone(user)
        self.assertEqual(self.http("/api/v6/admin/actions",bundle=self.player)["status"],401)

    def test_staff_totp_mandatory_and_replay_rejected(self):
        body={"name":"Owner","password":PASSWORD}
        self.assertEqual(self.http("/api/v6/auth/login","POST",body,self.player)["status"],401)
        body["totp"]=totp_code(self.owner["secret"])
        self.assertEqual(self.http("/api/v6/auth/login","POST",body,self.player)["status"],200)
        self.assertEqual(self.http("/api/v6/auth/login","POST",body,self.owner)["status"],401)

    def test_hierarchy_blocks_self_and_equal_role(self):
        second=self.account("SecondOwner","owner")
        for target in (self.owner,second):
            with self.assertRaises(APIError) as error:
                self.action("ban",target=target)
            self.assertEqual(error.exception.status,403)

    def test_fresh_stepup_is_required(self):
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE sessions SET mfa_at=? WHERE token_hash=?",(now_ms()-301000,self.owner["session"]["token_hash"]))
        with self.assertRaises(APIError) as error:
            self.action("ban")
        self.assertEqual(error.exception.code,"reauth_required")

    def test_public_catalog_excludes_source_private_items(self):
        response=self.http("/api/v6/state")
        self.assertEqual([x["id"] for x in response["body"]["catalog"]],["sword"])
        self.assertNotIn(b"narcotic",response["raw"])
        self.assertNotIn(b"reason",response["raw"])
        for visibility in ("hidden","public"):
            body={"visibility":visibility,"reason":"Attempt to reclassify source"}
            result=self.http("/api/v6/admin/catalog/narcotic_recipe_fixture","PATCH",body,self.owner)
            self.assertEqual(result["status"],200 if visibility=="hidden" else 403)

    def test_checkout_idempotency_and_concurrent_limit(self):
        key=identifier()
        request={"items":[{"id":"sword","qty":1}],"idempotencyKey":key,"total":1}
        def buy():
            return self.svc.checkout(request,self.player["user"],self.player["session"])
        with ThreadPoolExecutor(max_workers=2) as pool:
            outcomes=list(pool.map(lambda _:buy(),range(2)))
        self.assertEqual(outcomes[0]["order"]["id"],outcomes[1]["order"]["id"])
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT ar FROM users WHERE id=?",(self.player["user"]["id"],)).fetchone()[0],900)
            self.assertEqual(conn.execute("SELECT count(*) FROM orders").fetchone()[0],1)
        def second_buy(_):
            try:
                return self.svc.checkout({"items":[{"id":"sword","qty":1}],"idempotencyKey":identifier()},self.player["user"],self.player["session"])["order"]["id"]
            except APIError as error:
                return error.code
        with ThreadPoolExecutor(max_workers=2) as pool:
            outcomes=list(pool.map(second_buy,range(2)))
        self.assertEqual(outcomes.count("purchase_limit"),1)

    def test_personal_global_limit_reset_and_remove(self):
        self.action("purchase.limit",{"itemId":None,"maximum":0,"period":"lifetime"})
        with self.assertRaises(APIError) as error:
            self.svc.checkout({"items":[{"id":"sword","qty":1}],"idempotencyKey":identifier()},self.player["user"],self.player["session"])
        self.assertEqual(error.exception.code,"purchase_limit")
        result=self.action("purchase.limit",{"itemId":None,"maximum":1,"period":"lifetime","reset":True})
        limit_id=result["result"]["limitId"]
        self.svc.checkout({"items":[{"id":"sword","qty":1}],"idempotencyKey":identifier()},self.player["user"],self.player["session"])
        self.action("purchase.limit",{"limitId":limit_id,"remove":True})
        self.svc.checkout({"items":[{"id":"sword","qty":1}],"idempotencyKey":identifier()},self.player["user"],self.player["session"])

    def test_duration_action_retry_is_idempotent(self):
        key=identifier()
        first=self.action("ban",{"durationMs":60000},key)
        with patch("server.common.now_ms",return_value=now_ms()+1000):
            second=self.action("ban",{"durationMs":60000},key)
        self.assertEqual(first["action"]["id"],second["action"]["id"])

    def test_queued_action_not_active_until_signed_ack(self):
        first=self.action("ban",{"durationMs":60000})
        self.assertEqual(first["action"]["status"],"queued")
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT status FROM sanctions").fetchone()[0],"pending")
        body={"operationId":first["action"]["id"],"ackId":identifier(),"status":"executed","executedAt":now_ms()}
        with self.assertRaises(APIError):
            self.svc.acknowledge(body)
        claim_actions(self.svc)
        raw=encoded(body).encode()
        timestamp=str(now_ms()//1000)
        nonce=identifier()
        signature=adapter_signature(self.svc.config.adapter_secret,timestamp,nonce,raw)
        verify_adapter_envelope(self.svc,raw,timestamp,nonce,signature)
        self.svc.acknowledge(body)
        self.assertTrue(self.svc.acknowledge(body)["replayed"])
        with self.assertRaises(APIError):
            verify_adapter_envelope(self.svc,raw,timestamp,nonce,signature)
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT status FROM sanctions").fetchone()[0],"active")

    def test_cancel_pending_sanction_never_dispatches_it(self):
        result=self.action("mute",{"channel":"text","durationMs":60000})
        with self.svc.store.connect() as conn:
            sanction=conn.execute("SELECT id FROM sanctions").fetchone()[0]
        revoke=self.svc.revoke_sanction(sanction,{"reason":"Cancelled before game dispatch","idempotencyKey":identifier()},self.owner["user"],self.owner["session"])
        self.assertEqual(revoke["action"]["status"],"executed")
        self.assertEqual(claim_actions(self.svc),[])
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT status FROM actions WHERE id=?",(result["action"]["id"],)).fetchone()[0],"cancelled")

    def test_snapshot_compare_prevents_stale_slot_removal(self):
        snapshot=identifier()
        self.svc.ingest_snapshot({"playerId":self.player["user"]["id"],"gameUUID":self.player["user"]["game_uuid"],"snapshotId":snapshot,
            "capturedAt":now_ms(),"inventory":[{"slot":0,"id":"minecraft:stone","quantity":3}],"ender":[],"profile":{}})
        with self.assertRaises(APIError) as error:
            self.action("inventory.remove",{"slot":0,"quantity":1,"expectedSnapshotId":"wrong-snapshot","expectedItemId":"minecraft:stone"})
        self.assertEqual(error.exception.code,"stale_snapshot")
        response=self.action("inventory.remove",{"slot":0,"quantity":1,"expectedSnapshotId":snapshot,"expectedItemId":"minecraft:stone"})
        self.assertEqual(response["action"]["status"],"queued")
        with self.assertRaises(APIError):
            self.svc.player_profile(self.player["user"]["id"],self.other["user"],self.other["session"])

    def test_session_revocation_is_persistent(self):
        self.action("sessions.revoke")
        self.assertIsNone(self.svc.authenticate(self.player["token"])[1])
        restart=Service(self.svc.config)
        self.assertIsNone(restart.authenticate(self.player["token"])[1])

    def test_role_assignment_requires_enrolled_mfa(self):
        response=self.http("/api/v6/admin/players/"+self.player["user"]["id"]+"/role","POST",{"role":"admin","reason":"Staff promotion"},self.owner)
        self.assertEqual(response["status"],409)
        setup=self.svc.totp_setup({"password":PASSWORD},self.player["user"],self.player["session"],"127.0.0.2")
        self.svc.totp_confirm({"totp":totp_code(setup["secret"])},self.player["user"],self.player["session"],"127.0.0.2")
        response=self.http("/api/v6/admin/players/"+self.player["user"]["id"]+"/role","POST",{"role":"admin","reason":"Staff promotion"},self.owner)
        self.assertEqual(response["status"],200)
        self.assertIsNone(self.svc.authenticate(self.player["token"])[1])
        response=self.http("/api/v6/admin/players/"+self.owner["user"]["id"]+"/role","POST",{"role":"player","reason":"Self demotion attempt"},self.owner)
        self.assertEqual(response["status"],403)

    def test_analytics_consent_and_no_raw_identifier_storage(self):
        visitor="anonymousvisitor_123456789"
        event={"type":"page_view","page":"shop?email=private@example.test","visitor":visitor,"name":"Private Person","ip":"192.0.2.1"}
        with self.assertRaises(APIError):
            self.svc.analytics_events({"events":[event],"consent":False},"192.0.2.1")
        self.svc.analytics_events({"events":[event],"consent":True},"192.0.2.1")
        self.svc.analytics_events({"events":[dict(event,page="admin/players")],"consent":True},"192.0.2.1")
        with self.svc.store.connect() as conn:
            output=encoded([dict(x) for x in conn.execute("SELECT * FROM analytics")]+[dict(x) for x in conn.execute("SELECT * FROM analytics_visitors")])
        for forbidden in (visitor,"private@example","Private Person","192.0.2.1","admin"):
            self.assertNotIn(forbidden,output)
        analytics=self.svc.analytics(self.owner["user"],self.owner["session"])
        self.assertEqual(analytics["visits"],1)
        self.assertEqual(analytics["visitors"],1)
        self.assertEqual(analytics["orders"],0)

    def test_profile_changes_preserve_game_identity_and_email_uniqueness(self):
        response=self.http("/api/v6/account/profile","PATCH",{"displayName":"Игрок","email":"new@example.test"},self.player)
        self.assertEqual(response["status"],403)
        self.assertEqual(response["body"]["error"]["code"],"reauth_required")
        self.assertEqual(self.http("/api/v6/auth/reauth","POST",{"password":PASSWORD},self.player)["status"],200)
        response=self.http("/api/v6/account/profile","PATCH",{"displayName":"Игрок","email":"new@example.test"},self.player)
        self.assertEqual(response["status"],200,response.get("body"))
        self.assertEqual(response["body"]["user"]["displayName"],"Игрок")
        self.assertEqual(response["body"]["user"]["name"],"Player")
        self.assertEqual(self.http("/api/v6/account/profile","PATCH",{"gameName":"Other"},self.player)["status"],400)
        self.assertEqual(self.http("/api/v6/account/profile","PATCH",{"email":"other@example.test"},self.player)["status"],409)
        with self.svc.store.connect() as conn:
            row=conn.execute("SELECT * FROM users WHERE id=?",(self.player["user"]["id"],)).fetchone()
            self.assertEqual(row["game_uuid"],self.player["user"]["game_uuid"])
            self.assertEqual(row["game_name"],"Player")

    def test_password_change_requires_current_secret_and_revokes_other_sessions(self):
        with self.svc.store.transaction() as conn:
            row=conn.execute("SELECT * FROM users WHERE id=?",(self.player["user"]["id"],)).fetchone()
            second=self.svc._new_session(conn,row)
        new_password="A replacement password! 685392"
        request={"currentPassword":"wrong","password":new_password}
        self.assertEqual(self.http("/api/v6/account/password","POST",request,self.player)["status"],401)
        request["currentPassword"]=PASSWORD
        self.assertEqual(self.http("/api/v6/account/password","POST",request,self.player)["status"],200)
        self.assertIsNotNone(self.svc.authenticate(self.player["token"])[1])
        self.assertIsNone(self.svc.authenticate(second["token"])[1])
        with self.svc.store.connect() as conn:
            hashed=conn.execute("SELECT password_hash FROM users WHERE id=?",(self.player["user"]["id"],)).fetchone()[0]
            self.assertFalse(check_password(PASSWORD,hashed))
            self.assertTrue(check_password(new_password,hashed))
        self.assertEqual(self.http("/api/v6/account/password","POST",request,self.owner)["status"],401)
        request["totp"]=totp_code(self.owner["secret"])
        self.assertEqual(self.http("/api/v6/account/password","POST",request,self.owner)["status"],200)

    def test_transfer_is_atomic_idempotent_and_cannot_overdraw(self):
        request={"to":"Other","amount":125,"idempotencyKey":identifier()}
        def send(body):
            try:
                return self.svc.transfer(body,self.player["user"],self.player["session"])
            except APIError as error:
                return {"error":error.code}
        with ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(send,[request,request]))
        self.assertEqual(results[0]["transfer"]["id"],results[1]["transfer"]["id"])
        with self.assertRaises(APIError):
            self.svc.transfer(dict(request,amount=126),self.player["user"],self.player["session"])
        with ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(send,[dict(request,amount=700,idempotencyKey=identifier()) for _ in range(2)]))
        self.assertEqual(sum(x.get("error")=="insufficient_balance" for x in results),1)
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT SUM(ar) FROM users").fetchone()[0],3000)
            self.assertEqual(conn.execute("SELECT COUNT(*) FROM transfers").fetchone()[0],2)
            self.assertEqual(conn.execute("SELECT SUM(amount) FROM ledger WHERE kind='transfer'").fetchone()[0],0)

    def test_tickets_are_private_and_only_authorized_staff_can_reply(self):
        request={"title":"Нужна помощь","body":"Предмет еще не доставлен.","type":"payment"}
        self.assertEqual(self.http("/api/v6/tickets","POST",request)["status"],403)
        response=self.http("/api/v6/tickets","POST",request,self.player)
        self.assertEqual(response["status"],201,response.get("body"))
        ticket=response["body"]["ticket"]["id"]
        update={"reply":"Проверили очередь выдачи.","status":"in_progress","reason":"Private operator audit reason"}
        path="/api/v6/admin/tickets/"+ticket
        self.assertEqual(self.http(path,"PATCH",update,self.other)["status"],403)
        self.assertEqual(self.http(path,"PATCH",update,self.owner)["status"],200)
        own=self.http("/api/v6/state",bundle=self.player)
        self.assertEqual(len(own["body"]["tickets"]),1)
        self.assertEqual(own["body"]["tickets"][0]["replies"][0]["body"],update["reply"])
        self.assertNotIn(update["reason"].encode(),own["raw"])
        self.assertEqual(self.http("/api/v6/state",bundle=self.other)["body"]["tickets"],[])

    def terrain_fixture(self):
        staging=self.base/identifier()
        folder=staging/"assets"/"map"/"overworld"
        folder.mkdir(parents=True)
        (folder/"overview.png").write_bytes(base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aHyQAAAAASUVORK5CYII="))
        old=(datetime.now(timezone.utc)-timedelta(hours=2)).isoformat()
        manifest={"errors":0,"source":{"kind":"directory","sha256":"a"*64,"capturedAt":old,"snapshotCompletedAt":old,"label":"PRIVATE_SOURCE_PATH"},
            "dimensions":[{"id":"overworld","name":"Overworld","overview":"assets/map/overworld/overview.png","bounds":{"minX":0,"privateExtra":"PRIVATE_NESTED_DATA"},
                "tiles":{"template":"assets/map/overworld/{zoom}/{x}/{z}.png","levels":[{"zoom":0,"blocksPerPixel":32,"tileCount":1,"coordinates":[[0,0]],"privateExtra":"PRIVATE_LEVEL_DATA"}]}}]}
        return staging,manifest

    def test_map_publication_validates_all_tiles_and_strips_nested_private_metadata(self):
        staging,manifest=self.terrain_fixture()
        (staging/"map-manifest.json").write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError,"referenced map tile is missing"):
            publish_map(self.svc,staging)
        folder=staging/"assets"/"map"/"overworld"
        (folder/"0"/"0").mkdir(parents=True)
        (folder/"0"/"0"/"0.png").write_bytes((folder/"overview.png").read_bytes())
        result=publish_map(self.svc,staging)
        self.assertEqual(result["source"]["sha256"],"a"*64)
        self.assertEqual(result["source"]["snapshotCompletedAt"],manifest["source"]["snapshotCompletedAt"])
        self.assertNotIn("PRIVATE",encoded(result))
        self.assertNotIn("privateExtra",encoded(result))
        with self.assertRaisesRegex(ValueError,"cannot be overridden"):
            publish_map(self.svc,staging,captured_at=(datetime.now(timezone.utc)-timedelta(days=1)).isoformat())
        manifest["source"]["snapshotCompletedAt"]=datetime.now(timezone.utc).isoformat()
        (staging/"map-manifest.json").write_text(json.dumps(manifest))
        self.assertIsNone(publish_map(self.svc,staging))

    def test_map_delay_does_not_block_new_jobs_and_stale_worker_lease_recovers(self):
        old=now_ms()-120000
        waiting,stale=identifier(),identifier()
        with self.svc.store.transaction() as conn:
            conn.execute("INSERT INTO jobs(id,kind,status,created_at,updated_at,payload) VALUES(?,?,?,?,?,?)",(waiting,"map","awaiting_age",old,old,"{}"))
            conn.execute("INSERT INTO jobs(id,kind,status,created_at,updated_at,payload,lease_until,lease_owner) VALUES(?,?,?,?,?,?,?,?)",(stale,"map","running",old,old,"{}",old,"lost-owner"))
        result=self.svc.map_refresh({"reason":"Manual fresh snapshot"},self.owner["user"],self.owner["session"])
        self.assertNotIn(result["job"]["id"],(waiting,stale))
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT status FROM jobs WHERE id=?",(stale,)).fetchone()[0],"failed")
        self.svc.config.map_source="/fixture-world"
        with patch("server.workers.subprocess.run") as render,patch("server.workers.publish_map",return_value=None) as publish:
            render.return_value.returncode=0
            outcome=run_map_jobs(self.svc)
            self.assertEqual(outcome["status"],"awaiting_age")
            self.assertEqual(render.call_count,1)
            self.assertEqual(publish.call_count,2)
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE jobs SET created_at=?",(old,))
        self.svc.map_settings_update({"intervalMinutes":1,"autoRefresh":True,"playerDelayMinutes":0,"minAgeMinutes":0,"reason":"Independent refresh timer"},self.owner["user"],self.owner["session"])
        with patch("server.workers.subprocess.run") as render,patch("server.workers.publish_map",return_value=None):
            render.return_value.returncode=0
            run_map_jobs(self.svc)
            self.assertEqual(render.call_count,1)

    def test_actions_worker_never_runs_terrain_renderer(self):
        with patch("server.workers.run_map_jobs",side_effect=AssertionError("Blocking map worker called")),patch("server.workers.dispatch_actions",return_value=[]):
            result=worker_once(self.svc)
        self.assertNotIn("map",result)
        self.assertIn("timers",result)

    def test_inventory_refresh_is_an_authorized_bound_read_operation(self):
        path="/api/v6/players/"+self.player["user"]["id"]+"/inventory/refresh"
        self.assertEqual(self.http(path,"POST",{},self.other)["status"],403)
        first=self.http(path,"POST",{},self.player)
        self.assertEqual(first["status"],202,first.get("body"))
        self.assertEqual(first["body"]["action"]["status"],"queued")
        second=self.http(path,"POST",{},self.player)
        self.assertEqual(first["body"]["action"]["id"],second["body"]["action"]["id"])
        operations=claim_actions(self.svc)
        self.assertEqual(len(operations),1)
        self.assertEqual(operations[0]["action"],"inventory.refresh")
        self.assertEqual(operations[0]["target"]["uuid"],self.player["user"]["game_uuid"])
        self.assertIsNone(self.svc.player_profile(self.player["user"]["id"],self.player["user"],self.player["session"])["inventory"])

    def test_ip_ban_timer_queues_verified_revoke_and_failed_inverse_does_not_crash(self):
        first=self.action("ban.ip",{"durationMs":1000})
        claim_actions(self.svc)
        self.svc.acknowledge({"operationId":first["action"]["id"],"ackId":identifier(),"status":"executed","executedAt":now_ms()})
        with patch("server.integration.now_ms",return_value=now_ms()+2000):
            self.assertEqual(self.svc.timers(),1)
        operations=claim_actions(self.svc)
        self.assertEqual(operations[0]["action"],"sanction.revoke")
        self.assertEqual(operations[0]["params"]["kind"],"ban.ip")
        self.assertEqual(operations[0]["params"]["originalActionId"],first["action"]["id"])
        with patch("server.integration.now_ms",return_value=now_ms()+3000):
            self.svc.acknowledge({"operationId":operations[0]["operationId"],"ackId":identifier(),"status":"failed","executedAt":now_ms()+2000})
            self.assertEqual(self.svc.timers(),0)
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT status FROM sanctions").fetchone()[0],"active")

    def test_public_settings_are_versioned_persistent_and_reject_unsafe_urls(self):
        request={"serverIp":"play.example.test","version":1,"reason":"Update connection address"}
        self.assertEqual(self.http("/api/v6/admin/config","PATCH",request,self.player)["status"],403)
        result=self.http("/api/v6/admin/config","PATCH",request,self.owner)
        self.assertEqual(result["status"],200,result.get("body"))
        self.assertEqual(result["body"]["config"]["version"],2)
        self.assertEqual(self.http("/api/v6/admin/config","PATCH",request,self.owner)["status"],409)
        for url in ("javascript:alert(1)","https://user:password@example.test/file.zip","http://example.test/file.zip"):
            response=self.http("/api/v6/admin/config","PATCH",{"version":2,"reason":"URL validation test","modUrl":url},self.owner)
            self.assertEqual(response["status"],400)
        self.assertEqual(Service(self.svc.config).state()["config"]["serverIp"],"play.example.test")

    def test_cms_drafts_are_private_and_publication_has_compare_and_swap_history(self):
        response=self.http("/api/v6/admin/pages","POST",{"title":"About","slug":"about","reason":"Create an about page"},self.owner)
        self.assertEqual(response["status"],201,response.get("body"))
        page=response["body"]["page"]
        path="/api/v6/admin/pages/"+page["id"]
        response=self.http(path,"PATCH",{"revision":1,"title":"About","body":"PUBLIC_FIRST_TEXT","reason":"Prepare initial publication"},self.owner)
        self.assertEqual(response["status"],200)
        self.assertNotIn(b"PUBLIC_FIRST_TEXT",self.http("/api/v6/state")["raw"])
        response=self.http(path+"/publish","POST",{"revision":2,"reason":"Publish approved content"},self.owner)
        self.assertEqual(response["status"],200,response.get("body"))
        self.assertIn(b"PUBLIC_FIRST_TEXT",self.http("/api/v6/state")["raw"])
        response=self.http(path,"PATCH",{"revision":3,"body":"PRIVATE_SECOND_DRAFT","reason":"Prepare private revision"},self.owner)
        self.assertEqual(response["status"],200)
        anonymous=self.http("/api/v6/state")
        self.assertNotIn(b"PRIVATE_SECOND_DRAFT",anonymous["raw"])
        self.assertEqual(anonymous["body"]["pages"][0]["history"],[])
        self.assertEqual(self.http(path+"/publish","POST",{"revision":3,"reason":"Stale publication request"},self.owner)["status"],409)
        self.assertEqual(self.http(path,"PATCH",{"revision":4,"body":"<script>alert(1)</script>","reason":"Reject arbitrary HTML"},self.owner)["status"],400)
        self.assertEqual(self.http(path+"/publish","POST",{"revision":4,"reason":"Publish second revision"},self.owner)["status"],200)
        response=self.http(path+"/restore","POST",{"revision":5,"historyRevision":3,"reason":"Restore first draft"},self.owner)
        self.assertEqual(response["status"],200,response.get("body"))
        self.assertEqual(response["body"]["page"]["draft"]["body"],"PUBLIC_FIRST_TEXT")
        self.assertEqual(self.http(path+"/hide","POST",{"revision":6,"reason":"Temporarily hide page"},self.owner)["status"],200)
        self.assertEqual(self.http("/api/v6/state")["body"]["pages"],[])

    def add_cm_fixture(self):
        item=dict(self.public,id="spark",name="CM item",currency="CM",price=200,limit=0)
        self.svc.public_source_ids.add("spark")
        with self.svc.store.transaction() as conn:
            conn.execute("INSERT INTO catalog(id,payload,price,currency,visibility,enabled) VALUES(?,?,?,?,?,?)",("spark",encoded(item),200,"CM","public",1))

    def test_mixed_checkout_is_atomic_and_idempotent_across_both_currencies(self):
        self.add_cm_fixture()
        request={"items":[{"id":"sword","qty":1},{"id":"spark","qty":1}],"idempotencyKey":identifier()}
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE users SET cm=0 WHERE id=?",(self.player["user"]["id"],))
        with self.assertRaises(APIError) as error:
            self.svc.checkout(request,self.player["user"],self.player["session"])
        self.assertEqual(error.exception.code,"insufficient_balance")
        with self.svc.store.transaction() as conn:
            self.assertEqual(conn.execute("SELECT ar FROM users WHERE id=?",(self.player["user"]["id"],)).fetchone()[0],1000)
            self.assertEqual(conn.execute("SELECT count(*) FROM orders").fetchone()[0],0)
            conn.execute("UPDATE users SET cm=1000 WHERE id=?",(self.player["user"]["id"],))
        self.action("purchase.limit",{"itemId":None,"maximum":1,"period":"lifetime"})
        with self.assertRaises(APIError) as error:
            self.svc.checkout(request,self.player["user"],self.player["session"])
        self.assertEqual(error.exception.code,"purchase_limit")
        self.action("purchase.limit",{"itemId":None,"maximum":2,"period":"lifetime"})
        def buy(_):
            return self.svc.checkout(request,self.player["user"],self.player["session"])
        with ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(buy,range(2)))
        self.assertEqual(results[0]["batchId"],results[1]["batchId"])
        self.assertEqual({x["currency"] for x in results[0]["orders"]},{"AR","CM"})
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT count(*) FROM orders").fetchone()[0],2)
            self.assertEqual(conn.execute("SELECT count(*) FROM checkout_batches").fetchone()[0],1)
            balance=conn.execute("SELECT ar,cm FROM users WHERE id=?",(self.player["user"]["id"],)).fetchone()
            self.assertEqual(tuple(balance),(900,800))

    def test_mixed_checkout_rolls_back_first_currency_when_second_queue_fails(self):
        self.add_cm_fixture()
        original=self.svc.queue
        def queue(*args,**kwargs):
            if args[4].get("itemId")=="spark":
                raise APIError(500,"Fixture delivery queue failure")
            return original(*args,**kwargs)
        with patch.object(self.svc,"queue",side_effect=queue):
            with self.assertRaises(APIError):
                self.svc.checkout({"items":[{"id":"sword","qty":1},{"id":"spark","qty":1}],"idempotencyKey":identifier()},self.player["user"],self.player["session"])
        with self.svc.store.connect() as conn:
            self.assertEqual(tuple(conn.execute("SELECT ar,cm FROM users WHERE id=?",(self.player["user"]["id"],)).fetchone()),(1000,1000))
            for table in ("orders","order_items","checkout_batches","ledger","actions"):
                self.assertEqual(conn.execute("SELECT count(*) FROM "+table).fetchone()[0],0,table)

    def test_purchase_periods_are_rolling_windows_with_inclusive_lower_boundary(self):
        first=self.svc.checkout({"items":[{"id":"sword","qty":1}],"idempotencyKey":identifier()},self.player["user"],self.player["session"])
        self.action("purchase.limit",{"itemId":None,"maximum":1,"period":"day"})
        at=now_ms()
        with patch("server.commerce.now_ms",return_value=at):
            for period,days in (("day",1),("week",7),("month",30)):
                self.assertEqual(self.svc.period_start(period),at-days*86400000)
            with self.svc.store.transaction() as conn:
                conn.execute("UPDATE orders SET created_at=? WHERE id=?",(at-86400000,first["order"]["id"]))
            with self.assertRaises(APIError) as error:
                self.svc.checkout({"items":[{"id":"sword","qty":1}],"idempotencyKey":identifier()},self.player["user"],self.player["session"])
            self.assertEqual(error.exception.code,"purchase_limit")
            with self.svc.store.transaction() as conn:
                conn.execute("UPDATE orders SET created_at=? WHERE id=?",(at-86400001,first["order"]["id"]))
            self.svc.checkout({"items":[{"id":"sword","qty":1}],"idempotencyKey":identifier()},self.player["user"],self.player["session"])

    def test_static_boundary_and_map_revision_privacy(self):
        for path in ("/server/core.py","/server/private/catalog.json","/data/catalog-current.json","/assets/js/data.js",
            "/assets/js/registry-v5.js","/assets/js/search-v5.js","/assets/map/overworld/overview.webp","/assets/custom/narcotic_recipe_fixture.png",
            "/../server/core.py"):
            self.assertEqual(self.http(path)["status"],404,path)
        self.assertEqual(self.http("/")["status"],200)
        staging=self.base/"renderer"
        folder=staging/"assets"/"map"/"overworld"
        folder.mkdir(parents=True)
        png=base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aHyQAAAAASUVORK5CYII=")
        (folder/"overview.png").write_bytes(png)
        data=staging/"data";data.mkdir()
        manifest={"errors":0,"source":{"capturedAt":datetime.now(timezone.utc).isoformat()},
            "dimensions":[{"id":"overworld","name":"Overworld","overview":"assets/map/overworld/overview.png",
                "tiles":{"template":"assets/map/overworld/{zoom}/{x}/{z}.png","levels":[]}}]}
        (data/"map-manifest.json").write_text(json.dumps(manifest))
        self.assertIsNone(publish_map(self.svc,staging))
        manifest["source"]["capturedAt"]=(datetime.now(timezone.utc)-timedelta(hours=2)).isoformat()
        (data/"map-manifest.json").write_text(json.dumps(manifest))
        publication=publish_map(self.svc,staging)
        url=publication["dimensions"][0]["overview"]
        self.assertEqual(self.http(url)["status"],200)
        self.svc.map_settings_update({"enabled":False,"reason":"Privacy pause"},self.owner["user"],self.owner["session"])
        self.assertEqual(self.http(url)["status"],404)
        self.assertIsNone(self.svc.map()["manifest"])

if __name__=="__main__":
    unittest.main()

