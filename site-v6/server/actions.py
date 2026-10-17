"""Typed administrative operations. A queued operation is not a game acknowledgement."""
import hashlib
import json
import math
import re
from .common import APIError, expiry, idem, integer, reason, text
from .security import ROLE_RANK, PERMISSIONS, canonical_role
from .storage import encoded, identifier, now_ms

BUILTINS = {
 "ban":("players.moderate",{"durationMs":"integer","expiresAt":"integer"}),
 "ban.ip":("players.moderate",{"durationMs":"integer","expiresAt":"integer"}),
 "mute":("players.moderate",{"durationMs":"integer","expiresAt":"integer","channel":"string"}),
 "kick":("players.moderate",{}), "warn":("players.moderate",{"durationMs":"integer","expiresAt":"integer"}),
 "freeze":("players.moderate",{"durationMs":"integer","expiresAt":"integer"}), "unfreeze":("players.moderate",{}),
 "jail":("players.moderate",{"durationMs":"integer","expiresAt":"integer"}), "unjail":("players.moderate",{}),
 "give":("inventory.manage",{"itemId":"string","quantity":"integer"}),
 "inventory.remove":("inventory.manage",{"slot":"integer","quantity":"integer","expectedSnapshotId":"string","expectedItemId":"string"}),
 "ender.remove":("inventory.manage",{"slot":"integer","quantity":"integer","expectedSnapshotId":"string","expectedItemId":"string"}),
 "inventory.restore":("inventory.manage",{"snapshotId":"string","expectedSnapshotId":"string"}),
 "heal":("players.manage",{}), "feed":("players.manage",{}), "extinguish":("players.manage",{}),
 "effect.clear":("players.manage",{}),
 "tp.coords":("players.manage",{"world":"string","x":"number","y":"number","z":"number"}),
 "tp.spawn":("players.manage",{"world":"string"}), "tp.bed":("players.manage",{"world":"string"}),
 "gamemode":("players.manage",{"mode":"string"}),
 "fly":("players.manage",{"enabled":"boolean","durationMs":"integer","expiresAt":"integer"}),
 "god":("players.manage",{"enabled":"boolean","durationMs":"integer","expiresAt":"integer"}),
 "money.adjust":("economy.manage",{"currency":"string","mode":"string","amount":"integer"}),
 "purchase.limit":("economy.manage",{"itemId":"string","maximum":"integer","period":"string","durationMs":"integer","expiresAt":"integer","reset":"boolean","remove":"boolean","limitId":"string"}),
 "sessions.revoke":("sessions.manage",{}),
 "fun.title":("players.fun",{"title":"string","subtitle":"string"}),
 "fun.sound":("players.fun",{"sound":"string","volume":"number","pitch":"number"}),
 "fun.firework":("players.fun",{"color":"string","count":"integer"}),
 "fun.glow":("players.fun",{"durationMs":"integer","color":"string"}),
}
REQUIRED = {
 "give":{"itemId","quantity"}, "inventory.remove":{"slot","quantity","expectedSnapshotId","expectedItemId"},
 "ender.remove":{"slot","quantity","expectedSnapshotId","expectedItemId"},
 "inventory.restore":{"snapshotId","expectedSnapshotId"},"tp.coords":{"world","x","y","z"},
 "gamemode":{"mode"},"fly":{"enabled"},"god":{"enabled"},
 "money.adjust":{"currency","mode","amount"},"fun.title":{"title"},"fun.sound":{"sound"},
}
PHYSICAL = {"tpto","tphere","swap","check-start","arsync","givebook","giveballot"}
DATABASE_ACTIONS = {"money.adjust","purchase.limit","sessions.revoke"}

class Actions:
    def refresh_inventory(self,target_id,body,user,session):
        """Request a signed snapshot without mutating the player's inventory."""
        self.require(user,session)
        if set(body)-{"idempotencyKey","reason"}:
            raise APIError(400,"Unsupported inventory refresh field.")
        if target_id!=user["id"]:
            self.require(user,session,"players.read")
        if not self.store.rate("inventory.refresh:"+user["id"],20,60000):
            raise APIError(429,"Too many inventory refresh requests.","rate_limited")
        key=idem(body) if "idempotencyKey" in body else identifier()
        why=reason(body) if "reason" in body else "Authoritative inventory snapshot requested"
        request_hash=hashlib.sha256(encoded({"target":target_id,"action":"inventory.refresh"}).encode()).hexdigest()
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,"players.read" if target_id!=user["id"] else None)
            target=self.target(conn,actor,target_id,mutation=False)
            if not target["game_link_verified"]:
                raise APIError(409,"The account has no verified Minecraft identity.","game_link_required")
            previous=conn.execute("SELECT * FROM actions WHERE actor_id=? AND idempotency_key=?",(actor["id"],key)).fetchone()
            if previous:
                if previous["request_hash"]!=request_hash:
                    raise APIError(409,"Idempotency key was used for another request.")
                return {"action":self.action_row(previous),"replayed":True}
            pending=conn.execute("SELECT * FROM actions WHERE actor_id=? AND target_id=? AND action='inventory.refresh' AND status IN ('queued','dispatched') ORDER BY created_at DESC LIMIT 1",(actor["id"],target_id)).fetchone()
            if pending:
                return {"action":self.action_row(pending),"replayed":True}
            operation=self.queue(conn,actor["id"],target_id,"inventory.refresh",{},why,key,request_hash)
            self.store.audit(conn,actor["id"],"inventory.refresh",target_id,why,{"operationId":operation})
            return {"action":self.action_row(conn.execute("SELECT * FROM actions WHERE id=?",(operation,)).fetchone())}

    def target(self, conn, actor, target_id, mutation=True):
        row = conn.execute("SELECT * FROM users WHERE id=?", (target_id,)).fetchone()
        if not row or canonical_role(row["role"]) is None:
            raise APIError(404,"Player not found.")
        target = dict(row)
        if mutation and ROLE_RANK[actor["role"]] <= ROLE_RANK[target["role"]]:
            raise APIError(403,"An administrator may act only on accounts below their own role.","hierarchy")
        return target

    def validate_params(self, action, params):
        if not isinstance(params,dict):
            raise APIError(400,"params must be an object.")
        params = dict(params)
        spec = self.registry[action]
        schema = spec.get("params",{})
        if action in BUILTINS:
            schema = BUILTINS[action][1]
        if isinstance(schema,list):
            schema = {x["name"]:x for x in schema if isinstance(x,dict) and "name" in x}
        if isinstance(schema,dict) and "properties" in schema:
            schema = schema["properties"]
        if not isinstance(schema,dict):
            schema = {}
        schema_required={key for key,value in schema.items() if isinstance(value,dict) and value.get("required") is True}
        if schema_required-set(params):
            raise APIError(400,"Required operation parameter is missing.")
        if any(not isinstance(key,str) or key.startswith("_") for key in params) or set(params)-set(schema):
            raise APIError(400,"Unsupported operation parameter.")
        if REQUIRED.get(action,set())-set(params):
            raise APIError(400,"Required operation parameter is missing.")
        for key,value in params.items():
            if value is None and key in {"itemId","durationMs","expiresAt","limitId"}:
                continue
            definition = schema[key]
            typ = definition.get("type","string") if isinstance(definition,dict) else definition
            if isinstance(typ,list):
                typ = next((t for t in typ if t!="null"),"string")
            if typ in ("int","integer"):
                integer(value,0 if key not in {"x","y","z"} else -30000000,366*86400000 if key=="durationMs" else 10**15 if key=="expiresAt" else 10**12,key)
            elif typ in ("number","float"):
                if isinstance(value,bool) or not isinstance(value,(int,float)) or not math.isfinite(value) or abs(value)>30000000:
                    raise APIError(400,"Invalid numeric parameter.")
            elif typ in ("bool","boolean"):
                if not isinstance(value,bool):
                    raise APIError(400,"Expected boolean parameter.")
            elif typ in ("string","str","enum"):
                text(value,1,512,key)
            else:
                raise APIError(400,"This parameter requires an adapter-specific typed schema.")
            if isinstance(definition,dict):
                if "enum" in definition and value not in definition["enum"]:
                    raise APIError(400,"Invalid parameter choice.")
                if isinstance(value,(int,float)) and not isinstance(value,bool):
                    if value<definition.get("minimum",-10**15) or value>definition.get("maximum",10**15):
                        raise APIError(400,"Parameter is outside its allowed range.")
        if "itemId" in params and params["itemId"] is not None and not re.fullmatch(r"[a-z0-9_.:-]{1,100}",params["itemId"]):
            raise APIError(400,"Invalid item identifier.")
        if "world" in params and params["world"] not in self.config.worlds:
            raise APIError(400,"Unknown world.")
        if action=="gamemode" and params["mode"] not in ("survival","creative","adventure","spectator"):
            raise APIError(400,"Unknown game mode.")
        if action=="mute":
            params.setdefault("channel","both")
            if params["channel"] not in ("text","voice","both"):
                raise APIError(400,"Unknown mute channel.")
        if action=="money.adjust":
            if params["currency"] not in ("AR","CM") or params["mode"] not in ("add","set","credit","debit"):
                raise APIError(400,"Invalid money adjustment.")
            integer(params["amount"],0,10**9,"amount")
            if params["mode"]=="credit":
                params["mode"]="add"
            if params["mode"]=="debit":
                params["mode"],params["amount"]="add",-params["amount"]
        if action=="game.money.adjust" and params.get("mode") in ("credit","debit"):
            integer(params.get("amount"),1,10**9,"amount")
        if action=="purchase.limit" and not params.get("remove"):
            integer(params.get("maximum"),0,10**9,"maximum")
            if params.get("period") not in ("day","week","month","lifetime"):
                raise APIError(400,"Unknown limit period.")
        if any(k in params for k in ("durationMs","expiresAt")):
            until = expiry(params)
            if until:
                params["expiresAt"] = until
        if action in ("tp.spawn","tp.bed"):
            params.setdefault("world","overworld")
        if action=="give":
            integer(params["quantity"],1,64,"quantity")
        if action=="fun.sound":
            if not re.fullmatch(r"[a-z0-9_.:]+",params["sound"]):
                raise APIError(400,"Invalid sound identifier.")
            if not 0<=params.get("volume",1)<=2 or not 0.5<=params.get("pitch",1)<=2:
                raise APIError(400,"Sound volume or pitch is outside its range.")
        if action=="fun.firework":
            integer(params.get("count",1),1,10,"count")
        return params

    def known_item(self, conn, item_id):
        key = item_id.removeprefix("minecraft:")
        if key in self.materials:
            if self.materials[key].get("giveable") is False or key=="air":
                raise APIError(400,"This material cannot be given.")
            return self.materials[key]
        row = conn.execute("SELECT payload,enabled FROM catalog WHERE id=?", (item_id,)).fetchone()
        if not row or not row["enabled"]:
            raise APIError(400,"Unknown or disabled item.")
        return json.loads(row["payload"])

    def action_row(self, row):
        data = dict(row)
        data["params"] = {k:v for k,v in json.loads(data["params"]).items() if not k.startswith("_")}
        data["ack"] = json.loads(data["ack"]) if data.get("ack") else None
        for field in ("request_hash","idempotency_key","lease_until"):
            data.pop(field,None)
        data.update(createdAt=data["created_at"],updatedAt=data["updated_at"],playerId=data["target_id"],
            executed=data["status"]=="executed",deliveryStatus=("awaiting_adapter" if data["status"]=="queued" and not self.config.adapter_url else data["status"]))
        return data

    def queue(self, conn, actor_id, target_id, action, params, why, key, request_hash, order_id=None):
        operation = identifier()
        params = dict(params)
        if target_id!="*" and "_target" not in params:
            row = conn.execute("SELECT * FROM users WHERE id=?", (target_id,)).fetchone()
            if row and row["game_link_verified"]:
                params["_target"]={"accountId":row["id"],"uuid":row["game_uuid"],"name":row["game_name"]}
        conn.execute("INSERT INTO actions(id,actor_id,target_id,action,params,reason,status,created_at,updated_at,idempotency_key,request_hash,order_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            (operation,actor_id,target_id,action,encoded(params),why,"queued",now_ms(),now_ms(),key,request_hash,order_id))
        return operation

    def money(self, conn, actor, target, params, operation, why):
        column = "ar" if params["currency"]=="AR" else "cm"
        old = target[column]
        amount = params["amount"] if params["mode"]=="add" else params["amount"]-old
        balance = old+amount
        if not 0<=balance<=10**12:
            raise APIError(409,"Insufficient balance or balance limit exceeded.")
        conn.execute(f"UPDATE users SET {column}=?,updated_at=? WHERE id=?", (balance,now_ms(),target["id"]))
        conn.execute("INSERT INTO ledger VALUES(?,?,?,?,?,?,?,?,?,?)",
            (identifier(),target["id"],params["currency"],amount,balance,"admin.adjust",operation,actor["id"],why,now_ms()))
        return {"balance":balance,"currency":params["currency"],"authority":"site-v6-ledger"}

    def limit(self, conn, target, params):
        item = params.get("itemId")
        if item is not None:
            self.known_item(conn,item)
        row = conn.execute("SELECT * FROM purchase_policies WHERE id=? AND user_id=?",
            (params["limitId"],target["id"])).fetchone() if params.get("limitId") else conn.execute(
            "SELECT * FROM purchase_policies WHERE user_id=? AND item_id IS ? AND enabled=1 ORDER BY created_at DESC LIMIT 1",(target["id"],item)).fetchone()
        if params.get("remove"):
            if not row:
                raise APIError(404,"Limit not found.")
            conn.execute("UPDATE purchase_policies SET enabled=0 WHERE id=?", (row["id"],))
            return {"limitId":row["id"],"removed":True}
        policy = row["id"] if row else identifier()
        sequence = conn.execute("SELECT COALESCE(MAX(sequence),0) FROM orders").fetchone()[0] if params.get("reset") else (row["reset_sequence"] if row else 0)
        conn.execute("INSERT INTO purchase_policies VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET item_id=excluded.item_id,maximum=excluded.maximum,period=excluded.period,expires_at=excluded.expires_at,reset_sequence=excluded.reset_sequence,enabled=1",
            (policy,target["id"],item,params["maximum"],params["period"],params.get("expiresAt"),sequence,1,now_ms()))
        return {"limitId":policy,"resetSequence":sequence,"removed":False}

    def player_action(self, target_id, body, user, session):
        action, why, key = body.get("action"), reason(body), idem(body)
        if action not in self.registry or self.registry[action]["scope"]!="player":
            raise APIError(400,"Unknown player action.")
        permission = self.registry[action]["permission"]
        self.require(user,session,permission,True)
        params = self.validate_params(action,body.get("params",{}))
        request_hash = hashlib.sha256(encoded({"target":target_id,"action":action,"params":body.get("params",{}),"reason":why}).encode()).hexdigest()
        with self.store.transaction() as conn:
            actor = self.actor_current(conn,user,session,permission,True)
            target = self.target(conn,actor,target_id)
            previous = conn.execute("SELECT * FROM actions WHERE actor_id=? AND idempotency_key=?", (actor["id"],key)).fetchone()
            if previous:
                if previous["request_hash"]!=request_hash:
                    raise APIError(409,"Idempotency key was used for a different request.")
                return {"action":self.action_row(previous),"replayed":True}
            if action not in DATABASE_ACTIONS and not target["game_link_verified"]:
                raise APIError(409,"The account has no verified Minecraft identity.","game_link_required")
            if action=="give":
                item=self.known_item(conn,params["itemId"])
                if params["quantity"]>self.maximum_quantity(item):
                    raise APIError(400,"This native item can be issued only one at a time.","item_quantity_limit")
            if action in ("inventory.remove","ender.remove","inventory.restore","inventory.clear","inventory.armor-off","inventory.shuffle-hotbar"):
                snapshot = conn.execute("SELECT * FROM inventory_snapshots WHERE user_id=? ORDER BY captured_at DESC LIMIT 1",(target_id,)).fetchone()
                if not snapshot or snapshot["id"]!=params.get("expectedSnapshotId"):
                    raise APIError(409,"Inventory changed. Refresh the authoritative snapshot.","stale_snapshot")
                if action in ("inventory.remove","ender.remove"):
                    slot = integer(params["slot"],0,26 if action=="ender.remove" else 40,"slot")
                    items = json.loads(snapshot["ender" if action=="ender.remove" else "inventory"])
                    item = next((i for i in items if i["slot"]==slot),None)
                    quantity = integer(params["quantity"],1,64,"quantity")
                    if not item or item["id"]!=params["expectedItemId"] or quantity>item["quantity"]:
                        raise APIError(409,"The slot contents differ from this request.","stale_snapshot")
                elif action=="inventory.restore":
                    restore = conn.execute("SELECT * FROM inventory_snapshots WHERE id=? AND user_id=?", (params["snapshotId"],target_id)).fetchone()
                    if not restore:
                        raise APIError(404,"Snapshot not found.")
                    params["_restore"]={"inventory":json.loads(restore["inventory"]),"ender":json.loads(restore["ender"])}
            operation = self.queue(conn,actor["id"],target_id,action,params,why,key,request_hash)
            result = None
            if action=="money.adjust":
                result=self.money(conn,actor,target,params,operation,why)
            elif action=="purchase.limit":
                result=self.limit(conn,target,params)
            elif action=="sessions.revoke":
                conn.execute("UPDATE sessions SET revoked_at=? WHERE user_id=?", (now_ms(),target_id))
                conn.execute("UPDATE users SET auth_epoch=auth_epoch+1 WHERE id=?", (target_id,))
                result={"sessionsRevoked":True}
            if result is not None:
                conn.execute("UPDATE actions SET status='executed',ack_id=?,ack=?,updated_at=? WHERE id=?",
                    ("db:"+operation,encoded({"authority":"site-v6-database","result":result}),now_ms(),operation))
            elif action in ("ban","ban.ip","mute","warn","freeze","jail"):
                conn.execute("INSERT INTO sanctions(id,user_id,kind,channel,reason,actor_id,created_at,expires_at,status,action_id) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    (identifier(),target_id,action,params.get("channel"),why,actor["id"],now_ms(),params.get("expiresAt"),"pending",operation))
            self.store.audit(conn,actor["id"],"player.action",target_id,why,{"actionId":operation,"action":action,"params":{k:v for k,v in params.items() if not k.startswith("_")}})
            row = conn.execute("SELECT * FROM actions WHERE id=?", (operation,)).fetchone()
            return {"action":self.action_row(row),"result":result}

    def server_action(self, body, user, session):
        action, why, key = body.get("action"), reason(body), idem(body)
        if action not in self.registry or self.registry[action]["scope"]!="server":
            raise APIError(400,"Unknown server action.")
        permission = self.registry[action]["permission"]
        self.require(user,session,permission,True)
        params = self.validate_params(action,body.get("params",{}))
        request_hash = hashlib.sha256(encoded({"action":action,"params":body.get("params",{}),"reason":why}).encode()).hexdigest()
        with self.store.transaction() as conn:
            actor = self.actor_current(conn,user,session,permission,True)
            old = conn.execute("SELECT * FROM actions WHERE actor_id=? AND idempotency_key=?", (actor["id"],key)).fetchone()
            if old:
                if old["request_hash"]!=request_hash:
                    raise APIError(409,"Idempotency key was used for a different request.")
                return {"action":self.action_row(old),"replayed":True}
            targets=[{"accountId":r["id"],"uuid":r["game_uuid"],"name":r["game_name"]} for r in conn.execute("SELECT * FROM users WHERE enabled=1 AND game_link_verified=1") if canonical_role(r["role"]) and ROLE_RANK[r["role"]]<ROLE_RANK[actor["role"]]]
            params["_target"]={"scope":"server","players":targets}
            operation=self.queue(conn,actor["id"],"*",action,params,why,key,request_hash)
            self.store.audit(conn,actor["id"],"server.action","*",why,{"actionId":operation,"action":action,"targetCount":len(targets)})
            return {"action":self.action_row(conn.execute("SELECT * FROM actions WHERE id=?", (operation,)).fetchone())}

    def revoke_sanction(self, sanction_id, body, user, session):
        why,key=reason(body),idem(body)
        self.require(user,session,"players.moderate",True)
        request_hash=hashlib.sha256(encoded({"sanctionId":sanction_id,"reason":why}).encode()).hexdigest()
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,"players.moderate",True)
            sanction=conn.execute("SELECT * FROM sanctions WHERE id=?", (sanction_id,)).fetchone()
            if not sanction:
                raise APIError(404,"Sanction not found.")
            self.target(conn,actor,sanction["user_id"])
            old=conn.execute("SELECT * FROM actions WHERE actor_id=? AND idempotency_key=?", (actor["id"],key)).fetchone()
            if old:
                if old["request_hash"]!=request_hash:
                    raise APIError(409,"Idempotency conflict.")
                return {"action":self.action_row(old)}
            if sanction["status"] not in ("pending","active"):
                raise APIError(409,"Sanction is already ended or being revoked.")
            original=conn.execute("SELECT * FROM actions WHERE id=?", (sanction["action_id"],)).fetchone()
            params={"sanctionId":sanction_id,"kind":sanction["kind"],"channel":sanction["channel"],"originalActionId":sanction["action_id"]}
            operation=self.queue(conn,actor["id"],sanction["user_id"],"sanction.revoke",params,why,key,request_hash)
            if original["status"]=="queued":
                conn.execute("UPDATE actions SET status='cancelled',updated_at=? WHERE id=?", (now_ms(),original["id"]))
                conn.execute("UPDATE actions SET status='executed',ack_id=?,ack=? WHERE id=?",("db:"+operation,encoded({"cancelledBeforeDispatch":True}),operation))
                status="revoked"
            else:
                status="revoking"
            conn.execute("UPDATE sanctions SET status=?,revoke_action_id=?,revoke_reason=?,revoked_at=? WHERE id=?",
                (status,operation,why,now_ms() if status=="revoked" else None,sanction_id))
            self.store.audit(conn,actor["id"],"sanction.revoke",sanction["user_id"],why,{"sanctionId":sanction_id,"actionId":operation})
            return {"action":self.action_row(conn.execute("SELECT * FROM actions WHERE id=?", (operation,)).fetchone())}

    def capabilities(self,user,session):
        self.require(user,session,"players.read")
        permissions=PERMISSIONS[user["role"]]
        return {"actions":[dict(value,connected=(key in DATABASE_ACTIONS or bool(self.config.adapter_url and key in self.config.adapter_capabilities)),queueSupported=True) for key,value in self.registry.items() if value["permission"] in permissions],
            "excluded":self.excluded,"adapterConfigured":bool(self.config.adapter_url)}

