"""Verified adapter acknowledgements, authoritative snapshots, and expiration jobs."""
import json
import math
import re
from .common import APIError, integer, text
from .security import ROLE_RANK, PERMISSIONS, canonical_role
from .storage import encoded, identifier, now_ms

class Integration:
    def acknowledge(self,body):
        operation=text(body.get("operationId"),1,100,"operationId")
        ack_id=text(body.get("ackId"),8,128,"ackId")
        status=body.get("status")
        if status not in ("executed","failed"):
            raise APIError(400,"Acknowledgement must be executed or failed.")
        with self.store.transaction() as conn:
            row=conn.execute("SELECT * FROM actions WHERE id=?",(operation,)).fetchone()
            if not row:
                raise APIError(404,"Operation not found.")
            if row["status"] in ("executed","failed"):
                if row["ack_id"]==ack_id and row["status"]==status:
                    return {"action":self.action_row(row),"replayed":True}
                raise APIError(409,"Operation already has a different acknowledgement.")
            if row["status"]!="dispatched":
                raise APIError(409,"Only a dispatched operation may be acknowledged.")
            # The game host and API may differ slightly in wall-clock time.
            # Operation/receipt identity and dispatched state remain mandatory.
            executed_at=integer(body.get("executedAt"),row["created_at"]-60000,now_ms()+60000,"executedAt")
            result=body.get("result",{})
            if not isinstance(result,dict):
                raise APIError(400,"Invalid acknowledgement result.")
            safe={k:v for k,v in result.items() if k in ("message","adapterVersion","snapshotId","errorCode") and isinstance(v,str) and len(v)<=1000}
            params=json.loads(row["params"])
            if {"balanceAfter","currency","transactionId","authority"} & set(result):
                if row["action"] not in ("game.money.credit","game.money.adjust") or result.get("authority")!="EconomyCore":
                    raise APIError(400,"Financial receipt must identify the native economy authority.")
                currency=result.get("currency")
                if currency not in ("AR","CM") or currency!=params.get("currency"):
                    raise APIError(400,"Financial receipt currency does not match the operation.")
                balance=integer(result.get("balanceAfter"),0,10**12,"balanceAfter")
                transaction=text(result.get("transactionId"),1 if status=="executed" else 0,128,"transactionId")
                if transaction and not re.fullmatch(r"[A-Za-z0-9:_-]+",transaction):
                    raise APIError(400,"Invalid native transaction identifier.")
                safe.update(balanceAfter=balance,currency=currency,transactionId=transaction,authority="EconomyCore")
            if conn.execute("SELECT id FROM actions WHERE ack_id=?",(ack_id,)).fetchone():
                raise APIError(409,"Acknowledgement identifier was already used.")
            ack={"operationId":operation,"ackId":ack_id,"status":status,"executedAt":executed_at,"result":safe}
            conn.execute("UPDATE actions SET status=?,ack_id=?,ack=?,updated_at=?,lease_until=NULL WHERE id=?",
                (status,ack_id,encoded(ack),now_ms(),operation))
            conn.execute("UPDATE sanctions SET status=? WHERE action_id=? AND status='pending'",("active" if status=="executed" else "failed",operation))
            if row["action"]=="sanction.revoke":
                final=("expired" if row["actor_id"]=="system-timer" else "revoked") if status=="executed" else "active"
                conn.execute("UPDATE sanctions SET status=?,revoked_at=? WHERE revoke_action_id=?",
                    (final,now_ms() if status=="executed" else None,operation))
            if row["action"] in ("unfreeze","unjail") and status=="executed":
                conn.execute("UPDATE sanctions SET status='revoked',revoked_at=?,revoke_action_id=?,revoke_reason=? WHERE user_id=? AND kind=? AND status='active'",
                    (now_ms(),operation,row["reason"],row["target_id"],"freeze" if row["action"]=="unfreeze" else "jail"))
            if row["order_id"]:
                if status=="failed":
                    order=conn.execute("SELECT * FROM orders WHERE id=?",(row["order_id"],)).fetchone()
                    line=conn.execute("SELECT * FROM order_items WHERE order_id=? AND item_id=?",(row["order_id"],params["itemId"])).fetchone()
                    refund=line["price"]*line["quantity"]
                    column="ar" if order["currency"]=="AR" else "cm"
                    conn.execute(f"UPDATE users SET {column}={column}+? WHERE id=?", (refund,order["user_id"]))
                    balance=conn.execute(f"SELECT {column} FROM users WHERE id=?",(order["user_id"],)).fetchone()[0]
                    conn.execute("INSERT INTO ledger VALUES(?,?,?,?,?,?,?,?,?,?)",
                        (identifier(),order["user_id"],order["currency"],refund,balance,"delivery.failed.refund",operation,"game-adapter","Verified atomic non-delivery",now_ms()))
                statuses=[x[0] for x in conn.execute("SELECT status FROM actions WHERE order_id=?",(row["order_id"],))]
                state="delivered" if all(x=="executed" for x in statuses) else "refunded" if all(x=="failed" for x in statuses) else "partially_failed" if all(x in ("executed","failed") for x in statuses) else "awaiting_delivery"
                conn.execute("UPDATE orders SET status=? WHERE id=?",(state,row["order_id"]))
            self.store.audit(conn,"game-adapter","operation.ack",row["target_id"],"Signed game acknowledgement",{"operationId":operation,"ackId":ack_id,"status":status,"executedAt":executed_at})
            return {"action":self.action_row(conn.execute("SELECT * FROM actions WHERE id=?",(operation,)).fetchone())}

    def ingest_snapshot(self,body):
        player_id=text(body.get("playerId",body.get("userId")),1,100,"playerId")
        snapshot_id=text(body.get("snapshotId"),8,128,"snapshotId")
        captured=integer(body.get("capturedAt"),now_ms()-30*86400000,now_ms()+60000,"capturedAt")
        parsed={}
        for field,maximum in (("inventory",40),("ender",26)):
            values=body.get(field)
            if not isinstance(values,list) or len(values)>maximum+1:
                raise APIError(400,"Invalid inventory snapshot.")
            slots=set();items=[]
            for item in values:
                if not isinstance(item,dict):
                    raise APIError(400,"Invalid inventory slot.")
                slot=integer(item.get("slot"),0,maximum,"slot")
                if slot in slots:
                    raise APIError(400,"Duplicate inventory slot.")
                slots.add(slot)
                item_id=text(item.get("id",item.get("itemId")),1,100,"itemId")
                if not re.fullmatch(r"[a-z0-9_:.]+",item_id):
                    raise APIError(400,"Invalid item identifier.")
                # A snapshot reports existing game data; it does not authorize
                # creation or removal of this amount. Preserve real overstacks.
                quantity=integer(item.get("quantity",item.get("qty")),1,2_147_483_647,"quantity")
                items.append({"slot":slot,"id":item_id,"itemId":item_id,"quantity":quantity,"qty":quantity,
                    "name":str(item.get("name",item_id))[:160]})
            parsed[field]=sorted(items,key=lambda i:i["slot"])
        incoming=body.get("profile",{})
        if not isinstance(incoming,dict):
            raise APIError(400,"Invalid profile snapshot.")
        profile={}
        for key in ("world","gamemode"):
            if key in incoming and isinstance(incoming[key],str) and len(incoming[key])<=80:
                profile[key]=incoming[key]
        if "world" in profile and profile["world"] not in self.config.worlds:
            raise APIError(400,"Unknown snapshot world.")
        for key in ("health","food","experience","playtime","x","y","z"):
            if key in incoming:
                value=incoming[key]
                if isinstance(value,bool) or not isinstance(value,(float,int)) or not math.isfinite(value) or abs(value)>10**12:
                    raise APIError(400,"Invalid profile numeric value.")
                profile[key]=value
        for key in ("online","mapVisible"):
            if key in incoming:
                if not isinstance(incoming[key],bool):
                    raise APIError(400,"Invalid profile visibility flag.")
                profile[key]=incoming[key]
        with self.store.transaction() as conn:
            target=conn.execute("SELECT * FROM users WHERE id=?",(player_id,)).fetchone()
            if not target or not target["game_link_verified"] or target["game_uuid"]!=body.get("gameUUID"):
                raise APIError(409,"Snapshot does not match the verified game identity.")
            previous=conn.execute("SELECT * FROM inventory_snapshots WHERE id=?",(snapshot_id,)).fetchone()
            values=(encoded(parsed["inventory"]),encoded(parsed["ender"]),encoded(profile))
            if previous:
                if previous["user_id"]==player_id and previous["captured_at"]==captured and tuple(previous[k] for k in ("inventory","ender","profile"))==values:
                    return {"snapshotId":snapshot_id,"replayed":True}
                raise APIError(409,"Snapshot identifier already refers to different content.")
            conn.execute("INSERT INTO inventory_snapshots VALUES(?,?,?,?,?,?,?)",(snapshot_id,player_id,captured,"verified-game-adapter",*values))
            self.store.audit(conn,"game-adapter","player.snapshot",player_id,"Signed player snapshot",{"snapshotId":snapshot_id,"capturedAt":captured})
        return {"snapshotId":snapshot_id,"accepted":True}

    def timers(self):
        count=0
        with self.store.transaction() as conn:
            at=now_ms()
            conn.execute("UPDATE purchase_policies SET enabled=0 WHERE enabled=1 AND expires_at IS NOT NULL AND expires_at<=?",(at,))
            for sanction in conn.execute("SELECT * FROM sanctions WHERE expires_at IS NOT NULL AND expires_at<=? AND status IN ('pending','active')",(at,)).fetchall():
                original=conn.execute("SELECT * FROM actions WHERE id=?",(sanction["action_id"],)).fetchone()
                if not original:
                    continue
                if original["status"]=="queued":
                    conn.execute("UPDATE actions SET status='cancelled',updated_at=? WHERE id=?",(at,original["id"]))
                    conn.execute("UPDATE sanctions SET status='expired',revoked_at=? WHERE id=?",(at,sanction["id"]))
                elif original["status"]=="executed":
                    key="expire:"+sanction["id"]
                    if conn.execute("SELECT id FROM actions WHERE actor_id='system-timer' AND idempotency_key=?",(key,)).fetchone():
                        # A failed inverse is still visible as active with its failed
                        # acknowledgement. Do not crash the worker or silently erase
                        # the history by reusing a terminal operation identifier.
                        continue
                    operation=self.queue(conn,"system-timer",sanction["user_id"],"sanction.revoke",
                        {"sanctionId":sanction["id"],"kind":sanction["kind"],"channel":sanction["channel"],"originalActionId":original["id"]},
                        "Sanction duration elapsed",key,key)
                    conn.execute("UPDATE sanctions SET status='revoking',revoke_action_id=?,revoke_reason=? WHERE id=?",(operation,"Sanction duration elapsed",sanction["id"]))
                else:
                    continue
                count+=1
            for action in conn.execute("SELECT * FROM actions WHERE action IN ('god','fly') AND status IN ('queued','executed')").fetchall():
                params=json.loads(action["params"])
                if params.get("enabled") is not True or not params.get("expiresAt") or params["expiresAt"]>at:
                    continue
                key="effect-expire:"+action["id"]
                if conn.execute("SELECT id FROM actions WHERE actor_id='system-timer' AND idempotency_key=?",(key,)).fetchone():
                    continue
                if action["status"]=="queued":
                    conn.execute("UPDATE actions SET status='cancelled',updated_at=? WHERE id=?",(at,action["id"]))
                else:
                    self.queue(conn,"system-timer",action["target_id"],"effect.revert",
                        {"kind":action["action"],"enabled":False,"originalActionId":action["id"]},"Temporary ability duration elapsed",key,key)
                count+=1
            conn.execute("DELETE FROM sessions WHERE expires_at<? OR revoked_at<?",(at-86400000,at-86400000))
            conn.execute("DELETE FROM adapter_nonces WHERE at<?",(at-600000,))
        return count

