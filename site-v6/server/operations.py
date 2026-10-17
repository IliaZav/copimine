"""Privacy-preserving analytics and map policy API."""
from datetime import datetime, timedelta, timezone
import hashlib
import hmac
import json
import re
from .common import APIError, integer, reason
from .security import digest, token
from .storage import encoded, identifier, now_ms

PAGES={"home","shop","donate","map","rules","support"}
EVENTS={"page_view","item_view","cart_add","cart_remove","checkout_start","checkout_complete","search"}
DEFAULT_MAP={"enabled":True,"intervalMinutes":60,"minAgeMinutes":60,"playerDelayMinutes":60,"showPlayers":False,"version":1}

class Operations:
    def analytics_events(self,body,ip):
        if body.get("consent") is not True:
            raise APIError(400,"Analytics requires consent.","consent_required")
        events=body.get("events")
        if not isinstance(events,list) or not 1<=len(events)<=50:
            raise APIError(400,"Expected between 1 and 50 events.")
        if not self.store.rate("analytics:"+digest(str(ip)),60,60000):
            raise APIError(429,"Too many events.","rate_limited")
        day=datetime.now(timezone.utc).date().isoformat()
        accepted=0
        with self.store.transaction() as conn:
            secret=self.store.setting("analytics_secret",None,conn)
            if secret is None:
                secret=token();self.store.set_setting(conn,"analytics_secret",secret)
            for event in events:
                if not isinstance(event,dict):
                    raise APIError(400,"Invalid analytics event.")
                typ={"visit":"page_view","view_item":"item_view","add_to_cart":"cart_add"}.get(event.get("type"),event.get("type"))
                page=str(event.get("page","")).split("?")[0].split("#")[0].strip("/").removesuffix(".html")
                page={"":"home","index":"home"}.get(page,page)
                if typ not in EVENTS or page not in PAGES:
                    continue
                visitor=event.get("visitor")
                if not isinstance(visitor,str) or not re.fullmatch(r"[A-Za-z0-9_-]{16,80}",visitor):
                    raise APIError(400,"Expected an anonymous random visitor token.")
                hashed=hmac.new(secret.encode(),(day+":"+visitor).encode(),hashlib.sha256).hexdigest()
                conn.execute("INSERT INTO analytics VALUES(?,?,?,1) ON CONFLICT(day,event,path) DO UPDATE SET count=count+1",(day,typ,page))
                conn.execute("INSERT OR IGNORE INTO analytics_visitors VALUES(?,?)",(day,hashed))
                accepted+=1
            cutoff=(datetime.now(timezone.utc)-timedelta(days=90)).date().isoformat()
            conn.execute("DELETE FROM analytics_visitors WHERE day<?",(cutoff,))
            conn.execute("DELETE FROM analytics WHERE day<?",(cutoff,))
        return {"accepted":accepted,"stored":"daily aggregates only"}

    def analytics(self,user,session,from_day=None,to_day=None):
        self.require(user,session,"analytics.read")
        try:
            last=datetime.strptime(to_day,"%Y-%m-%d").replace(tzinfo=timezone.utc) if to_day else datetime.now(timezone.utc).replace(hour=0,minute=0,second=0,microsecond=0)
            first=datetime.strptime(from_day,"%Y-%m-%d").replace(tzinfo=timezone.utc) if from_day else last-timedelta(days=29)
            if not 0<=(last-first).days<=365:
                raise ValueError()
        except (ValueError,TypeError):
            raise APIError(400,"Use a date range of up to 366 days.")
        first_ms,last_ms=int(first.timestamp()*1000),int((last+timedelta(days=1)).timestamp()*1000)
        first_s,last_s=first.date().isoformat(),last.date().isoformat()
        with self.store.connect() as conn:
            orders=conn.execute("SELECT * FROM orders WHERE created_at>=? AND created_at<?",(first_ms,last_ms)).fetchall()
            paid=[row for row in orders if row["status"]=="delivered"]
            revenue={c:sum(row["total"] for row in paid if row["currency"]==c) for c in ("AR","CM")}
            rubles=conn.execute("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='completed' AND currency='RUB' AND created_at>=? AND created_at<?",(first_ms,last_ms)).fetchone()[0]
            events=[{"day":row["day"],"type":row["event"],"page":row["path"],"count":row["count"]} for row in conn.execute("SELECT * FROM analytics WHERE day>=? AND day<=?",(first_s,last_s))]
            visitors=conn.execute("SELECT count(*) FROM analytics_visitors WHERE day>=? AND day<=?",(first_s,last_s)).fetchone()[0]
            top=[{"id":row["item_id"],"name":row["name"],"qty":row["qty"],"revenue":row["revenue"],"currency":row["currency"],"orders":row["orders"]} for row in conn.execute(
                "SELECT oi.item_id,MAX(oi.name) name,SUM(oi.quantity) qty,SUM(oi.quantity*oi.price) revenue,o.currency,COUNT(DISTINCT o.id) orders FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.status='delivered' AND o.created_at>=? AND o.created_at<? GROUP BY oi.item_id,o.currency ORDER BY revenue DESC LIMIT 100",(first_ms,last_ms))]
            days={}
            for row in paid:
                day=datetime.fromtimestamp(row["created_at"]/1000,timezone.utc).date().isoformat()
                days.setdefault(day,{"at":day,"AR":0,"CM":0,"rubles":0})[row["currency"]]+=row["total"]
            for row in conn.execute("SELECT * FROM payments WHERE status='completed' AND currency='RUB' AND created_at>=? AND created_at<?",(first_ms,last_ms)):
                day=datetime.fromtimestamp(row["created_at"]/1000,timezone.utc).date().isoformat()
                days.setdefault(day,{"at":day,"AR":0,"CM":0,"rubles":0})["rubles"]+=row["amount"]
        buyers=len({row["user_id"] for row in paid})
        pages={}
        for event in events:
            if event["type"]=="page_view":
                pages[event["page"]]=pages.get(event["page"],0)+event["count"]
        return {"from":first_s,"to":last_s,"revenue":revenue,"rubles":rubles,"orders":len(paid),"buyers":buyers,
            "items":sum(row["qty"] for row in top),"visits":sum(pages.values()),"visitors":visitors,
            "conversion":buyers/visitors*100 if visitors else 0,"conversionDefinition":"delivered-order buyers / consenting daily anonymous visitors; not a linked-user funnel",
            "top":top,"pending":sum(row["status"] in ("awaiting_delivery","partially_delivered") for row in orders),
            "failed":sum(row["status"] in ("failed","partially_failed") for row in orders),
            "days":[days[day] for day in sorted(days)],"pages":[{"page":p,"count":n} for p,n in sorted(pages.items(),key=lambda x:-x[1])],
            "events":events}

    def map_settings(self):
        return self.store.setting("map_settings",dict(DEFAULT_MAP))

    def map_settings_update(self,body,user,session):
        self.require(user,session,"map.manage",True)
        why=reason(body)
        aliases={"minimumAgeMinutes":"minAgeMinutes","delayMinutes":"minAgeMinutes"}
        body={aliases.get(k,k):v for k,v in body.items()}
        if set(body)-set(DEFAULT_MAP)-{"reason"}:
            raise APIError(400,"Unsupported map setting.")
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,"map.manage",True)
            current=self.store.setting("map_settings",dict(DEFAULT_MAP),conn)
            if "version" in body and body["version"]!=current["version"]:
                raise APIError(409,"Map settings changed.","version_conflict")
            for field in ("enabled","showPlayers"):
                if field in body:
                    if not isinstance(body[field],bool):
                        raise APIError(400,"Expected a boolean map setting.")
                    current[field]=body[field]
            for field in ("intervalMinutes","minAgeMinutes","playerDelayMinutes"):
                if field in body:
                    current[field]=integer(body[field],1 if field!="minAgeMinutes" else 0,43200,field)
            current["playerDelayMinutes"]=max(current["playerDelayMinutes"],current["minAgeMinutes"],1)
            current["version"]+=1
            self.store.set_setting(conn,"map_settings",current)
            self.store.audit(conn,actor["id"],"map.settings","map",why,current)
        return {"settings":current}

    def map_refresh(self,body,user,session):
        self.require(user,session,"map.manage",True)
        why=reason(body)
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,"map.manage",True)
            settings=self.store.setting("map_settings",dict(DEFAULT_MAP),conn)
            if not settings["enabled"]:
                raise APIError(409,"Map publishing is disabled.")
            pending=conn.execute("SELECT * FROM jobs WHERE kind='map' AND status IN ('queued','running','awaiting_age') ORDER BY created_at DESC LIMIT 1").fetchone()
            if pending:
                return {"job":self.job_row(pending)}
            job=identifier()
            conn.execute("INSERT INTO jobs VALUES(?,?,?,?,?,?,NULL)",(job,"map","queued",now_ms(),now_ms(),encoded({"policyVersion":settings["version"]})))
            self.store.audit(conn,actor["id"],"map.refresh","map",why,{"jobId":job})
            return {"job":self.job_row(conn.execute("SELECT * FROM jobs WHERE id=?",(job,)).fetchone())}

    @staticmethod
    def job_row(row):
        return {"id":row["id"],"status":row["status"],"createdAt":row["created_at"],"updatedAt":row["updated_at"],"error":row["error"]}

    def eligible_publication(self,publication_id=None):
        settings=self.map_settings()
        if not settings["enabled"]:
            return None
        with self.store.connect() as conn:
            query="SELECT * FROM map_publications WHERE captured_at<=?"
            args=[now_ms()-settings["minAgeMinutes"]*60000]
            if publication_id:
                query+=" AND id=?";args.append(publication_id)
            query+=" ORDER BY published_at DESC LIMIT 1"
            row=conn.execute(query,args).fetchone()
            return dict(row) if row else None

    def map(self):
        settings=self.map_settings()
        publication=self.eligible_publication()
        players=[]
        with self.store.connect() as conn:
            job=conn.execute("SELECT * FROM jobs WHERE kind='map' ORDER BY created_at DESC LIMIT 1").fetchone()
            if settings["enabled"] and settings["showPlayers"] and publication:
                cutoff=now_ms()-settings["playerDelayMinutes"]*60000
                for row in conn.execute("SELECT s.*,u.name FROM inventory_snapshots s JOIN users u ON u.id=s.user_id WHERE s.captured_at<=? AND s.captured_at=(SELECT MAX(t.captured_at) FROM inventory_snapshots t WHERE t.user_id=s.user_id AND t.captured_at<=?)",(cutoff,cutoff)):
                    profile=json.loads(row["profile"])
                    if profile.get("mapVisible") is True and profile.get("online") is True and profile.get("world") in self.config.worlds and all(isinstance(profile.get(k),(int,float)) for k in ("x","z")):
                        players.append({"name":row["name"],"world":profile["world"],"x":profile["x"],"z":profile["z"],"capturedAt":row["captured_at"],"delayed":True})
        return {"manifest":json.loads(publication["manifest"]) if publication else None,"settings":settings,"players":players,
            "job":self.job_row(job) if job else None,"lastUpdatedAt":publication["published_at"] if publication else None}

    def audit_rows(self,user,session):
        self.require(user,session,"audit.read")
        with self.store.connect() as conn:
            return {"entries":[dict(row,payload=json.loads(row["payload"])) for row in conn.execute("SELECT * FROM audit ORDER BY sequence DESC LIMIT 1000")]}
