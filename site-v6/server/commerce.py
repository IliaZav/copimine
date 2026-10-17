"""Server-priced orders and transactionally enforced purchase policies."""
from datetime import datetime, timedelta, timezone
import hashlib
import json
from .common import APIError, idem, integer, reason, text
from .security import PERMISSIONS
from .storage import encoded, identifier, now_ms

PUBLIC_FIELDS={"id","name","description","price","currency","category","image","material","type","rarity","tags","enabled","limit","supplyLimit","requires","model","icon","sourceKey"}
class Commerce:
    def catalog(self, admin=False):
        result=[]
        with self.store.connect() as conn:
            rows=conn.execute("SELECT * FROM catalog ORDER BY id").fetchall()
        for row in rows:
            if not admin and (row["visibility"]!="public" or not row["enabled"]):
                continue
            data=json.loads(row["payload"])
            if not admin:
                data={k:v for k,v in data.items() if k in PUBLIC_FIELDS}
            data.update(id=row["id"],price=row["price"],currency=row["currency"],enabled=bool(row["enabled"]),
                limit=row["per_player_limit"],supplyLimit=row["supply_limit"],version=row["version"])
            if admin:
                data["visibility"]=row["visibility"]
                if row["visibility"]!="public":
                    data["image"]="/api/v6/admin/icons/"+row["id"]+".png"
            result.append(data)
        return result

    def patch_catalog(self,item_id,body,user,session):
        self.require(user,session,"catalog.manage",True)
        why=reason(body)
        allowed={"name","description","price","currency","category","enabled","visibility","limit","supplyLimit","version","reason"}
        if set(body)-allowed:
            raise APIError(400,"Unsupported catalog field.")
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,"catalog.manage",True)
            row=conn.execute("SELECT * FROM catalog WHERE id=?", (item_id,)).fetchone()
            if not row:
                raise APIError(404,"Catalog item not found.")
            if body.get("version") is not None and body["version"]!=row["version"]:
                raise APIError(409,"Catalog item changed. Reload before saving.","version_conflict")
            payload=json.loads(row["payload"])
            for key,limit in (("name",120),("description",3000),("category",100)):
                if key in body:
                    payload[key]=text(body[key],1,limit,key)
            price=integer(body.get("price",row["price"]),0,10**9,"price")
            currency=body.get("currency",row["currency"])
            if currency not in ("AR","CM"):
                raise APIError(400,"Unknown currency.")
            enabled=body.get("enabled",bool(row["enabled"]))
            if not isinstance(enabled,bool):
                raise APIError(400,"enabled must be boolean.")
            visibility=body.get("visibility",row["visibility"])
            if visibility not in ("public","hidden","admin","restricted"):
                raise APIError(400,"Unknown visibility.")
            if visibility=="public" and item_id not in self.public_source_ids:
                raise APIError(403,"This source item is not approved for the public catalog.")
            personal=integer(body.get("limit",row["per_player_limit"]),0,10**9,"limit")
            supply=integer(body.get("supplyLimit",row["supply_limit"]),0,10**9,"supplyLimit")
            conn.execute("UPDATE catalog SET payload=?,price=?,currency=?,visibility=?,enabled=?,per_player_limit=?,supply_limit=?,version=version+1 WHERE id=?",
                (encoded(payload),price,currency,visibility,int(enabled),personal,supply,item_id))
            self.store.audit(conn,actor["id"],"catalog.update",item_id,why,{k:v for k,v in body.items() if k!="reason"})
        return {"item":next(x for x in self.catalog(True) if x["id"]==item_id)}

    @staticmethod
    def period_start(period):
        now=datetime.now(timezone.utc)
        if period=="day":
            start=now.replace(hour=0,minute=0,second=0,microsecond=0)
        elif period=="week":
            start=(now-timedelta(days=now.weekday())).replace(hour=0,minute=0,second=0,microsecond=0)
        elif period=="month":
            start=now.replace(day=1,hour=0,minute=0,second=0,microsecond=0)
        else:
            return 0
        return int(start.timestamp()*1000)

    def check_policies(self,conn,user_id,items):
        for policy in conn.execute("SELECT * FROM purchase_policies WHERE user_id=? AND enabled=1 AND (expires_at IS NULL OR expires_at>?)",(user_id,now_ms())):
            wanted=sum(qty for item,qty in items.items() if policy["item_id"] is None or item==policy["item_id"])
            if not wanted:
                continue
            condition="" if policy["item_id"] is None else " AND oi.item_id=?"
            args=[user_id,policy["reset_sequence"],self.period_start(policy["period"])]
            if policy["item_id"] is not None:
                args.append(policy["item_id"])
            used=conn.execute("SELECT COALESCE(SUM(oi.quantity),0) FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.user_id=? AND o.sequence>? AND o.created_at>=? AND o.status NOT IN ('cancelled','refunded')"+condition,args).fetchone()[0]
            if used+wanted>policy["maximum"]:
                raise APIError(409,"A personal purchase limit would be exceeded.","purchase_limit")

    def checkout(self,body,user,session):
        self.require(user,session)
        key=idem(body)
        rows=body.get("items")
        if not isinstance(rows,list) or not 1<=len(rows)<=40:
            raise APIError(400,"A cart must contain 1–40 lines.")
        items={}
        for row in rows:
            if not isinstance(row,dict) or set(row)-{"id","itemId","qty","quantity"}:
                raise APIError(400,"Invalid cart line.")
            item=text(row.get("id",row.get("itemId")),1,100,"itemId")
            qty=integer(row.get("qty",row.get("quantity")),1,64,"quantity")
            items[item]=integer(items.get(item,0)+qty,1,64,"quantity")
        request_hash=hashlib.sha256(encoded(items).encode()).hexdigest()
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session)
            previous=conn.execute("SELECT * FROM orders WHERE user_id=? AND idempotency_key=?", (actor["id"],key)).fetchone()
            if previous:
                if previous["request_hash"]!=request_hash:
                    raise APIError(409,"Idempotency key was used for another cart.")
                return {"order":self.order_row(conn,previous),"replayed":True}
            if not actor["game_link_verified"]:
                raise APIError(409,"Link a verified Minecraft account before purchasing.","game_link_required")
            chosen=[]
            for item,qty in sorted(items.items()):
                row=conn.execute("SELECT * FROM catalog WHERE id=?",(item,)).fetchone()
                if not row or row["visibility"]!="public" or not row["enabled"] or item not in self.public_source_ids:
                    raise APIError(409,"A cart item is unavailable.","item_unavailable")
                if row["per_player_limit"]:
                    count=conn.execute("SELECT COALESCE(SUM(oi.quantity),0) FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE oi.item_id=? AND o.user_id=? AND o.status NOT IN ('refunded','cancelled')",(item,actor["id"])).fetchone()[0]
                    if count+qty>row["per_player_limit"]:
                        raise APIError(409,"Item purchase limit exceeded.","purchase_limit")
                if row["supply_limit"]:
                    count=conn.execute("SELECT COALESCE(SUM(oi.quantity),0) FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE oi.item_id=? AND o.status NOT IN ('refunded','cancelled')",(item,)).fetchone()[0]
                    if count+qty>row["supply_limit"]:
                        raise APIError(409,"Item supply limit exceeded.","supply_limit")
                chosen.append((dict(row),qty))
            currencies={row["currency"] for row,qty in chosen}
            if len(currencies)!=1:
                raise APIError(400,"Use separate orders for AR and CM.")
            self.check_policies(conn,actor["id"],items)
            currency=currencies.pop()
            column="ar" if currency=="AR" else "cm"
            total=sum(row["price"]*qty for row,qty in chosen)
            if total>actor[column]:
                raise APIError(409,"Insufficient balance.","insufficient_balance")
            order,at=identifier(),now_ms()
            sequence=conn.execute("SELECT COALESCE(MAX(sequence),0)+1 FROM orders").fetchone()[0]
            conn.execute(f"UPDATE users SET {column}={column}-?,updated_at=? WHERE id=?", (total,at,actor["id"]))
            conn.execute("INSERT INTO orders VALUES(?,?,?,?,?,?,?,?,?)",(order,actor["id"],currency,total,"awaiting_delivery",at,sequence,key,request_hash))
            for row,qty in chosen:
                name=json.loads(row["payload"])["name"]
                conn.execute("INSERT INTO order_items VALUES(?,?,?,?,?)",(order,row["id"],qty,row["price"],name))
                self.queue(conn,actor["id"],actor["id"],"purchase.deliver",{"itemId":row["id"],"quantity":qty,"orderId":order},
                    "Store purchase",order+":"+row["id"],request_hash,order)
            conn.execute("INSERT INTO ledger VALUES(?,?,?,?,?,?,?,?,?,?)",(identifier(),actor["id"],currency,-total,actor[column]-total,"purchase",order,actor["id"],"Store purchase",at))
            self.store.audit(conn,actor["id"],"checkout",actor["id"],"Store purchase",{"orderId":order,"currency":currency,"total":total,"items":items})
            return {"order":self.order_row(conn,conn.execute("SELECT * FROM orders WHERE id=?",(order,)).fetchone())}

    def order_row(self,conn,row):
        result={k:v for k,v in dict(row).items() if k not in ("idempotency_key","request_hash")}
        result.update(playerId=row["user_id"],createdAt=row["created_at"],
            items=[{"id":x["item_id"],"itemId":x["item_id"],"name":x["name"],"qty":x["quantity"],"quantity":x["quantity"],"price":x["price"]} for x in conn.execute("SELECT * FROM order_items WHERE order_id=?",(row["id"],))])
        return result

    def snapshot_row(self,row):
        if not row:
            return None
        data=dict(row)
        for key in ("inventory","ender","profile"):
            data[key]=json.loads(data[key])
        data.update(playerId=row["user_id"],capturedAt=row["captured_at"],source="verified-game-adapter")
        return data

    def sanction_row(self,row):
        data=dict(row)
        data.update(playerId=row["user_id"],createdAt=row["created_at"],expiresAt=row["expires_at"],revokedAt=row["revoked_at"],
            actionId=row["action_id"],revokeActionId=row["revoke_action_id"])
        return data

    def player_profile(self,target_id,user,session):
        self.require(user,session)
        if target_id!=user["id"]:
            self.require(user,session,"players.read")
        with self.store.connect() as conn:
            target=conn.execute("SELECT * FROM users WHERE id=?",(target_id,)).fetchone()
            if not target:
                raise APIError(404,"Player not found.")
            player=self.public_user(dict(target),target_id==user["id"])
            snapshots=[self.snapshot_row(row) for row in conn.execute("SELECT * FROM inventory_snapshots WHERE user_id=? ORDER BY captured_at DESC LIMIT 100",(target_id,))]
            latest=snapshots[0] if snapshots else None
            player.update(profile=latest["profile"] if latest else None,
                inventory=latest["inventory"] if latest else None,ender=latest["ender"] if latest else None,
                snapshotId=latest["id"] if latest else None,inventoryVerified=bool(latest))
            return {"player":player,"inventory":player["inventory"],"ender":player["ender"],"snapshots":snapshots,
                "orders":[self.order_row(conn,row) for row in conn.execute("SELECT * FROM orders WHERE user_id=? ORDER BY created_at DESC LIMIT 500",(target_id,))],
                "payments":[dict(row) for row in conn.execute("SELECT id,user_id,amount,currency,status,created_at FROM payments WHERE user_id=? ORDER BY created_at DESC LIMIT 500",(target_id,))],
                "sanctions":[self.sanction_row(row) for row in conn.execute("SELECT * FROM sanctions WHERE user_id=? ORDER BY created_at DESC LIMIT 500",(target_id,))],
                "actions":[self.action_row(row) for row in conn.execute("SELECT * FROM actions WHERE target_id=? ORDER BY created_at DESC LIMIT 500",(target_id,))],
                "purchaseLimits":[dict(row) for row in conn.execute("SELECT * FROM purchase_policies WHERE user_id=?",(target_id,))]}

    def state(self,user=None,session=None):
        permissions=PERMISSIONS.get(user["role"],set()) if user else set()
        state={"mode":"server","config":{"mode":"server","currencyAuthority":"site-v6-ledger","gameAdapterConfigured":bool(self.config.adapter_url),"catalogVersion":6},
            "catalog":self.catalog("catalog.manage" in permissions),"players":[],"orders":[],"payments":[],"ledger":[],
            "sanctions":[],"playerSnapshots":[],"playerActions":[],"purchaseLimits":[],"mapSettings":self.map_settings()}
        if not user:
            return state
        self.require(user,session)
        with self.store.connect() as conn:
            all_players="players.read" in permissions
            users=conn.execute("SELECT * FROM users" if all_players else "SELECT * FROM users WHERE id=?", () if all_players else (user["id"],)).fetchall()
            state["players"]=[self.public_user(dict(row),row["id"]==user["id"]) for row in users]
            finance="economy.manage" in permissions or "analytics.read" in permissions
            for table,key,owncol in (("orders","orders","user_id"),("payments","payments","user_id"),("ledger","ledger","user_id"),
                ("sanctions","sanctions","user_id"),("inventory_snapshots","playerSnapshots","user_id"),("actions","playerActions","target_id"),
                ("purchase_policies","purchaseLimits","user_id")):
                broad=finance if table in ("orders","payments","ledger","purchase_policies") else all_players
                query=f"SELECT * FROM {table}" + ("" if broad else f" WHERE {owncol}=?") + " ORDER BY " + ("captured_at" if table=="inventory_snapshots" else "created_at") + " DESC LIMIT 2000"
                rows=conn.execute(query,() if broad else (user["id"],)).fetchall()
                converted=[]
                for row in rows:
                    if table=="orders": value=self.order_row(conn,row)
                    elif table=="actions": value=self.action_row(row)
                    elif table=="sanctions": value=self.sanction_row(row)
                    elif table=="inventory_snapshots": value=self.snapshot_row(row)
                    else: value=dict(row)
                    value.pop("provider_reference",None)
                    converted.append(value)
                state[key]=converted
        return state
