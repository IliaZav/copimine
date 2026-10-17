"""Personal profile, password changes, transfers, and authenticated support tickets."""
import hashlib
import json
import re
import sqlite3
from .common import APIError, idem, integer, reason, text
from .security import ROLE_RANK, check_password, password_hash, verify_totp
from .storage import encoded, identifier, now_ms

class Personal:
    def update_profile(self,body,user,session):
        self.require(user,session)
        if set(body)-{"displayName","email"} or not body:
            raise APIError(400,"Unsupported profile field.")
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,recent=ROLE_RANK[user["role"]]>0)
            display=text(body.get("displayName",actor.get("display_name") or actor["name"]),1,64,"displayName")
            email=text(body.get("email",actor["email"]),3,254,"email").lower()
            if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+",email):
                raise APIError(400,"Invalid email.")
            changed_email=email!=actor["email"]
            if changed_email:
                self.actor_current(conn,user,session,recent=True)
            try:
                conn.execute("UPDATE users SET display_name=?,email=?,updated_at=? WHERE id=?", (display,email,now_ms(),actor["id"]))
            except sqlite3.IntegrityError:
                raise APIError(409,"This email cannot be used.","email_unavailable")
            if changed_email:
                # Editing the text field never preserves or restores ownership proof.
                # Even switching back to an old address requires a fresh email code.
                at=now_ms()
                conn.execute("DELETE FROM account_contacts WHERE user_id=? AND channel='email'",(actor["id"],))
                conn.execute("UPDATE auth_challenges SET status='revoked',consumed_at=? WHERE user_id=? AND channel='email' AND status='active'",(at,actor["id"]))
                conn.execute("UPDATE notification_outbox SET status='cancelled',payload_cipher=NULL,updated_at=? WHERE user_id=? AND channel='email' AND status='queued'",(at,actor["id"]))
            self.store.audit(conn,actor["id"],"account.profile",actor["id"],"Account profile updated",
                {"displayNameChanged":display!=(actor.get("display_name") or actor["name"]),"emailChanged":email!=actor["email"]})
            row=dict(conn.execute("SELECT * FROM users WHERE id=?",(actor["id"],)).fetchone())
        return {"user":self.public_user(row,True)}

    def change_password(self,body,user,session,ip):
        self.require(user,session)
        if set(body)-{"currentPassword","password","totp"}:
            raise APIError(400,"Unsupported password field.")
        self.auth_rate(ip,user["name"])
        try:
            hashed=password_hash(body.get("password"))
        except ValueError as error:
            raise APIError(400,str(error))
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session)
            if not check_password(body.get("currentPassword"),actor["password_hash"]):
                raise APIError(401,"Invalid password or authenticator code.","invalid_credentials")
            if ROLE_RANK[actor["role"]]>0 or actor["totp_secret"]:
                step=verify_totp(actor["totp_secret"],body.get("totp"),actor["totp_last_step"])
                if step is None:
                    raise APIError(401,"Invalid password or authenticator code.","invalid_credentials")
                conn.execute("UPDATE users SET totp_last_step=? WHERE id=?",(step,actor["id"]))
            epoch=actor["auth_epoch"]+1
            conn.execute("UPDATE users SET password_hash=?,auth_epoch=?,updated_at=? WHERE id=?",(hashed,epoch,now_ms(),actor["id"]))
            conn.execute("UPDATE sessions SET revoked_at=? WHERE user_id=? AND token_hash<>?",(now_ms(),actor["id"],session["token_hash"]))
            conn.execute("UPDATE sessions SET auth_epoch=?,mfa_at=? WHERE token_hash=?",(epoch,now_ms(),session["token_hash"]))
            self.store.audit(conn,actor["id"],"account.password",actor["id"],"Password changed; other sessions revoked",{})
            self.queue_notice(conn,actor["id"],"password",actor["id"]+":"+str(epoch),{})
        return {"ok":True,"otherSessionsRevoked":True}

    def transfer(self,body,user,session):
        self.require(user,session)
        if set(body)-{"to","amount","idempotencyKey"}:
            raise APIError(400,"Unsupported transfer field.")
        destination=text(body.get("to"),1,100,"to")
        amount=integer(body.get("amount"),1,10**9,"amount")
        key=idem(body)
        request_hash=hashlib.sha256(encoded({"to":destination.casefold(),"amount":amount}).encode()).hexdigest()
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,recent=ROLE_RANK[user["role"]]>0)
            previous=conn.execute("SELECT * FROM transfers WHERE sender_id=? AND idempotency_key=?",(actor["id"],key)).fetchone()
            if previous:
                if previous["request_hash"]!=request_hash:
                    raise APIError(409,"Idempotency key was used for another transfer.")
                return {"transfer":self.transfer_row(previous),"replayed":True}
            target=conn.execute("SELECT * FROM users WHERE id=? OR name=? COLLATE NOCASE",(destination,destination)).fetchone()
            if not target or not target["enabled"]:
                raise APIError(404,"Recipient not found.")
            if target["id"]==actor["id"]:
                raise APIError(400,"Choose another recipient.")
            if actor["ar"]<amount:
                raise APIError(409,"Insufficient AR balance.","insufficient_balance")
            if target["ar"]+amount>10**12:
                raise APIError(409,"Recipient balance limit exceeded.")
            operation,at=identifier(),now_ms()
            conn.execute("UPDATE users SET ar=ar-?,updated_at=? WHERE id=?",(amount,at,actor["id"]))
            conn.execute("UPDATE users SET ar=ar+?,updated_at=? WHERE id=?",(amount,at,target["id"]))
            conn.execute("INSERT INTO transfers VALUES(?,?,?,?,?,?,?)",(operation,actor["id"],target["id"],amount,at,key,request_hash))
            for account,change,balance in ((actor["id"],-amount,actor["ar"]-amount),(target["id"],amount,target["ar"]+amount)):
                conn.execute("INSERT INTO ledger VALUES(?,?,?,?,?,?,?,?,?,?)",
                    (identifier(),account,"AR",change,balance,"transfer",operation,actor["id"],"User AR transfer",at))
            self.store.audit(conn,actor["id"],"account.transfer",target["id"],"User AR transfer",{"transferId":operation,"amount":amount,"currency":"AR"})
            return {"transfer":self.transfer_row(conn.execute("SELECT * FROM transfers WHERE id=?",(operation,)).fetchone())}

    @staticmethod
    def transfer_row(row):
        return {"id":row["id"],"from":row["sender_id"],"to":row["recipient_id"],"amount":row["amount"],"currency":"AR","createdAt":row["created_at"],"status":"completed"}

    @staticmethod
    def ticket_row(row):
        return {"id":row["id"],"playerId":row["user_id"],"userId":row["user_id"],"title":row["title"],"body":row["body"],
            "type":row["type"],"status":row["status"],"replies":json.loads(row["replies"]),"createdAt":row["created_at"],"updatedAt":row["updated_at"]}

    def create_ticket(self,body,user,session):
        self.require(user,session)
        if set(body)-{"title","body","type"}:
            raise APIError(400,"Unsupported ticket field.")
        title=text(body.get("title"),3,160,"title")
        message=text(body.get("body"),3,10000,"body")
        kind=body.get("type","support")
        if kind not in ("support","bug","report","appeal","question","other","payment","account","technical"):
            raise APIError(400,"Unknown ticket type.")
        if not self.store.rate("ticket:"+user["id"],10,3600000):
            raise APIError(429,"Too many support tickets.","rate_limited")
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session)
            ticket,at=identifier(),now_ms()
            conn.execute("INSERT INTO tickets VALUES(?,?,?,?,?,?,?,?,?)",(ticket,actor["id"],title,message,kind,"open","[]",at,at))
            self.store.audit(conn,actor["id"],"ticket.create",ticket,"Support ticket created",{"type":kind})
            return {"ticket":self.ticket_row(conn.execute("SELECT * FROM tickets WHERE id=?",(ticket,)).fetchone())}

    def update_ticket(self,ticket_id,body,user,session):
        self.require(user,session,"players.moderate",True)
        why=reason(body)
        if set(body)-{"reply","status","reason"}:
            raise APIError(400,"Unsupported ticket update field.")
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,"players.moderate",True)
            ticket=conn.execute("SELECT * FROM tickets WHERE id=?",(ticket_id,)).fetchone()
            if not ticket:
                raise APIError(404,"Ticket not found.")
            status=body.get("status",ticket["status"])
            if status not in ("open","in_progress","awaiting_player","closed","rejected"):
                raise APIError(400,"Unknown ticket status.")
            replies=json.loads(ticket["replies"])
            if body.get("reply"):
                replies.append({"id":identifier(),"author":actor["name"],"authorRole":actor["role"],
                    "body":text(body["reply"],1,10000,"reply"),"createdAt":now_ms()})
            conn.execute("UPDATE tickets SET replies=?,status=?,updated_at=? WHERE id=?",(encoded(replies),status,now_ms(),ticket_id))
            self.store.audit(conn,actor["id"],"ticket.update",ticket_id,why,{"status":status,"replyAdded":bool(body.get("reply"))})
            if body.get("reply"):
                self.queue_notice(conn,ticket["user_id"],"ticket",ticket_id+":"+replies[-1]["id"],{"status":status})
            return {"ticket":self.ticket_row(conn.execute("SELECT * FROM tickets WHERE id=?",(ticket_id,)).fetchone())}

