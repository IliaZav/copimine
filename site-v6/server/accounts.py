"""Account authentication, session revocation, and mandatory staff MFA."""
import hmac
import re
from urllib.parse import quote
import uuid
from .common import APIError, reason, text
from .security import ROLE_RANK, PERMISSIONS, canonical_role, token, digest, password_hash, check_password, new_totp_secret, verify_totp
from .storage import now_ms, identifier

class Accounts:
    def auth_rate(self, ip, name=""):
        if not self.store.rate("auth-ip:"+digest(str(ip)), 30, 300000) or not self.store.rate("auth-login:"+digest(str(name).lower()), 10, 300000):
            raise APIError(429, "Too many attempts. Try again later.", "rate_limited")

    def _new_session(self, conn, user=None, previous=None, remember=False, mfa=False):
        raw, csrf, at = token(), token(), now_ms()
        life = 8*3600000 if user and ROLE_RANK.get(user["role"],0)>0 else (30*86400000 if user and remember else 12*3600000 if user else 2*3600000)
        conn.execute("INSERT INTO sessions(token_hash,user_id,csrf,created_at,expires_at,mfa_at,auth_epoch) VALUES(?,?,?,?,?,?,?)",
            (digest(raw),user["id"] if user else None,csrf,at,at+life,at if mfa else None,user["auth_epoch"] if user else None))
        if previous:
            conn.execute("UPDATE sessions SET revoked_at=? WHERE token_hash=?", (at,previous["token_hash"]))
        row = dict(conn.execute("SELECT * FROM sessions WHERE token_hash=?", (digest(raw),)).fetchone())
        return {"token":raw, "session":row, "user":dict(user) if user else None}

    def guest_session(self):
        with self.store.transaction() as conn:
            return self._new_session(conn)

    def authenticate(self, raw):
        if not isinstance(raw,str) or not 20 <= len(raw) <= 128:
            return None, None
        with self.store.connect() as conn:
            session = conn.execute("SELECT * FROM sessions WHERE token_hash=?", (digest(raw),)).fetchone()
            if not session or session["revoked_at"] or session["expires_at"] <= now_ms():
                return None, None
            if session["user_id"] is None:
                return dict(session), None
            user = conn.execute("SELECT * FROM users WHERE id=?", (session["user_id"],)).fetchone()
            if not user or not user["enabled"] or canonical_role(user["role"]) is None or user["auth_epoch"] != session["auth_epoch"]:
                return None, None
            if ROLE_RANK[user["role"]] > 0 and (not user["totp_secret"] or not session["mfa_at"]):
                return None, None
            return dict(session), dict(user)

    def require(self, user, session=None, permission=None, recent=False):
        if not user or not user["enabled"] or canonical_role(user["role"]) is None:
            raise APIError(401,"Authentication required.","unauthenticated")
        if permission and permission not in PERMISSIONS[user["role"]]:
            raise APIError(403,"Permission denied.","forbidden")
        if recent and (not session or not session.get("mfa_at") or session["mfa_at"] < now_ms()-300000):
            raise APIError(403,"Confirm your password and current authenticator code.","reauth_required")

    def actor_current(self, conn, user, session, permission=None, recent=False):
        current = conn.execute("SELECT * FROM users WHERE id=?", (user["id"] if user else "",)).fetchone()
        row = conn.execute("SELECT * FROM sessions WHERE token_hash=?", (session["token_hash"] if session else "",)).fetchone()
        if not current or not row or row["revoked_at"] or row["expires_at"] <= now_ms() or current["auth_epoch"] != row["auth_epoch"]:
            raise APIError(401,"Session expired.","unauthenticated")
        current, row = dict(current), dict(row)
        if ROLE_RANK.get(current["role"],0)>0 and (not current["totp_secret"] or not row["mfa_at"]):
            raise APIError(403,"Staff MFA is required.","mfa_required")
        self.require(current,row,permission,recent)
        return current

    def public_user(self, user, own=False):
        if not user:
            return None
        result = {"id":user["id"],"name":user["name"],"role":user["role"],"enabled":bool(user["enabled"]),
            "AR":user["ar"],"CM":user["cm"],"balanceAR":user["ar"],"balanceCM":user["cm"],
            "permissions":sorted(PERMISSIONS.get(user["role"],set())), "gameUUID":user["game_uuid"],
            "gameName":user["game_name"],"gameLinkVerified":bool(user["game_link_verified"])}
        if own:
            result.update(email=user["email"],totpEnabled=bool(user["totp_secret"]))
        return result

    def register(self, body, session, ip):
        if set(body)-{"name","email","password","remember"}:
            raise APIError(400,"Unexpected registration field.")
        name = text(body.get("name"),3,32,"name")
        if not re.fullmatch(r"[A-Za-z0-9_]+", name):
            raise APIError(400,"Use 3–32 letters, numbers, or underscores for the account name.")
        email = text(body.get("email"),3,254,"email").lower()
        if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+",email):
            raise APIError(400,"Invalid email.")
        self.auth_rate(ip,name)
        try:
            password = password_hash(body.get("password"))
        except ValueError as exc:
            raise APIError(400,str(exc))
        import sqlite3
        with self.store.transaction() as conn:
            uid, at = identifier(), now_ms()
            try:
                conn.execute("INSERT INTO users(id,name,email,password_hash,role,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
                    (uid,name,email,password,"player",at,at))
            except sqlite3.IntegrityError:
                raise APIError(409,"This account cannot be registered.","account_exists")
            user = conn.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
            bundle = self._new_session(conn,user,session,body.get("remember") is True)
            self.store.audit(conn,uid,"account.register",uid,"Account registration",{})
        return bundle

    def login(self, body, session, ip):
        if set(body)-{"name","password","totp","remember"}:
            raise APIError(400,"Unexpected login field.")
        name = str(body.get("name",""))[:254]
        self.auth_rate(ip,name)
        with self.store.connect() as conn:
            row = conn.execute("SELECT * FROM users WHERE name=? COLLATE NOCASE OR email=? COLLATE NOCASE", (name,name)).fetchone()
        valid = check_password(body.get("password"),row["password_hash"] if row else self.dummy_password)
        if not valid or not row or not row["enabled"] or canonical_role(row["role"]) is None:
            raise APIError(401,"Invalid login or authenticator code.","invalid_credentials")
        with self.store.transaction() as conn:
            user = conn.execute("SELECT * FROM users WHERE id=?", (row["id"],)).fetchone()
            if not user or not user["enabled"] or user["auth_epoch"] != row["auth_epoch"] or canonical_role(user["role"]) is None:
                raise APIError(401,"Invalid login or authenticator code.","invalid_credentials")
            needs_mfa = ROLE_RANK[user["role"]] > 0 or bool(user["totp_secret"])
            if needs_mfa:
                step = verify_totp(user["totp_secret"],body.get("totp"),user["totp_last_step"])
                if step is None:
                    raise APIError(401,"Invalid login or authenticator code.","invalid_credentials")
                conn.execute("UPDATE users SET totp_last_step=? WHERE id=?", (step,user["id"]))
            bundle = self._new_session(conn,user,session,body.get("remember") is True,needs_mfa)
            self.store.audit(conn,user["id"],"auth.login",user["id"],"Password and MFA login" if needs_mfa else "Password login",{})
        return bundle

    def logout(self, session):
        if session:
            with self.store.transaction() as conn:
                conn.execute("UPDATE sessions SET revoked_at=? WHERE token_hash=?", (now_ms(),session["token_hash"]))
                self.store.audit(conn,session["user_id"],"auth.logout",session["user_id"],"Logout",{})
        return self.guest_session()

    def reauth(self, body, user, session, ip):
        self.require(user,session)
        self.auth_rate(ip,user["name"])
        with self.store.transaction() as conn:
            actor = self.actor_current(conn,user,session)
            if not check_password(body.get("password"),actor["password_hash"]):
                raise APIError(401,"Invalid password or authenticator code.","invalid_credentials")
            if ROLE_RANK[actor["role"]]>0 or actor["totp_secret"]:
                step = verify_totp(actor["totp_secret"],body.get("totp"),actor["totp_last_step"])
                if step is None:
                    raise APIError(401,"Invalid password or authenticator code.","invalid_credentials")
                conn.execute("UPDATE users SET totp_last_step=? WHERE id=?", (step,actor["id"]))
            conn.execute("UPDATE sessions SET mfa_at=? WHERE token_hash=?", (now_ms(),session["token_hash"]))
            self.store.audit(conn,actor["id"],"auth.reauth",actor["id"],"Fresh account verification",{})
        return {"ok":True,"validForMs":300000}

    def totp_setup(self, body, user, session, ip):
        self.require(user,session)
        self.auth_rate(ip,user["name"])
        with self.store.transaction() as conn:
            actor = self.actor_current(conn,user,session)
            if actor["totp_secret"]:
                raise APIError(409,"An authenticator is already enrolled.")
            if not check_password(body.get("password"),actor["password_hash"]):
                raise APIError(401,"Invalid credentials.","invalid_credentials")
            secret = new_totp_secret()
            conn.execute("INSERT INTO pending_totp VALUES(?,?,?) ON CONFLICT(user_id) DO UPDATE SET secret=excluded.secret,expires_at=excluded.expires_at",
                (actor["id"],secret,now_ms()+600000))
        uri = "otpauth://totp/"+quote("CopiMine:"+user["name"],safe="")+"?secret="+secret+"&issuer=CopiMine&digits=6&period=30"
        return {"secret":secret,"uri":uri,"expiresInMs":600000}

    def totp_confirm(self, body, user, session, ip):
        self.require(user,session)
        self.auth_rate(ip,user["name"])
        with self.store.transaction() as conn:
            actor = self.actor_current(conn,user,session)
            row = conn.execute("SELECT * FROM pending_totp WHERE user_id=?", (actor["id"],)).fetchone()
            step = verify_totp(row["secret"],body.get("totp")) if row and row["expires_at"]>now_ms() else None
            if step is None:
                raise APIError(400,"Invalid or expired authenticator code.")
            conn.execute("UPDATE users SET totp_secret=?,totp_last_step=?,updated_at=? WHERE id=?", (row["secret"],step,now_ms(),actor["id"]))
            conn.execute("DELETE FROM pending_totp WHERE user_id=?", (actor["id"],))
            conn.execute("UPDATE sessions SET revoked_at=? WHERE user_id=? AND token_hash<>?", (now_ms(),actor["id"],session["token_hash"]))
            conn.execute("UPDATE sessions SET mfa_at=? WHERE token_hash=?", (now_ms(),session["token_hash"]))
            self.store.audit(conn,actor["id"],"auth.totp.enroll",actor["id"],"Authenticator enrolled",{})
        return {"ok":True,"totpEnabled":True}

    def bootstrap_user(self, name, email, password, role="owner", totp_secret=None):
        if canonical_role(role) is None or (ROLE_RANK[role]>0 and not totp_secret):
            raise ValueError("A known role and staff authenticator are required.")
        if not re.fullmatch(r"[A-Za-z0-9_]{3,32}",name):
            raise ValueError("Invalid account name.")
        hashed, at, uid = password_hash(password), now_ms(), identifier()
        with self.store.transaction() as conn:
            conn.execute("INSERT INTO users(id,name,email,password_hash,role,totp_secret,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                (uid,name,email.lower(),hashed,role,totp_secret,at,at))
            self.store.audit(conn,"local-owner-cli","account.bootstrap",uid,"Interactive account provisioning",{"role":role})
        return uid

    def change_role(self, target_id, body, user, session):
        self.require(user,session,"users.manage",True)
        new_role, why = body.get("role"), reason(body)
        if canonical_role(new_role) is None:
            raise APIError(400,"Unknown role.")
        with self.store.transaction() as conn:
            actor = self.actor_current(conn,user,session,"users.manage",True)
            target = conn.execute("SELECT * FROM users WHERE id=?", (target_id,)).fetchone()
            if not target:
                raise APIError(404,"Account not found.")
            if target_id==actor["id"] and new_role!=actor["role"]:
                raise APIError(403,"You cannot change your own owner role.")
            owners = conn.execute("SELECT count(*) FROM users WHERE role='owner' AND enabled=1").fetchone()[0]
            if target["role"]=="owner" and new_role!="owner" and owners<=1:
                raise APIError(409,"The last owner cannot be removed.")
            if ROLE_RANK[new_role]>0 and not target["totp_secret"]:
                raise APIError(409,"The account must enroll an authenticator before receiving a staff role.","mfa_required")
            conn.execute("UPDATE users SET role=?,auth_epoch=auth_epoch+1,updated_at=? WHERE id=?", (new_role,now_ms(),target_id))
            conn.execute("UPDATE sessions SET revoked_at=? WHERE user_id=?", (now_ms(),target_id))
            self.store.audit(conn,actor["id"],"account.role",target_id,why,{"from":target["role"],"to":new_role})
            result = dict(conn.execute("SELECT * FROM users WHERE id=?", (target_id,)).fetchone())
        return {"user":self.public_user(result),"sessionsRevoked":True}

    def link_player(self, account_id, game_uuid, game_name):
        game_uuid = str(uuid.UUID(game_uuid))
        if not re.fullmatch(r"[A-Za-z0-9_]{3,16}",game_name):
            raise ValueError("Invalid Minecraft player name.")
        with self.store.transaction() as conn:
            row = conn.execute("SELECT * FROM users WHERE id=? OR name=? COLLATE NOCASE",(account_id,account_id)).fetchone()
            if not row:
                raise ValueError("Account not found.")
            conn.execute("UPDATE users SET game_uuid=?,game_name=?,game_link_verified=1,updated_at=? WHERE id=?",
                (game_uuid,game_name,now_ms(),row["id"]))
            conn.execute("UPDATE actions SET status='cancelled',updated_at=? WHERE target_id=? AND status='queued'",(now_ms(),row["id"]))
            self.store.audit(conn,"local-owner-cli","account.game-link",row["id"],"Operator verified Minecraft identity",{"uuid":game_uuid,"name":game_name})
        return {"id":row["id"],"gameUUID":game_uuid,"gameName":game_name}
