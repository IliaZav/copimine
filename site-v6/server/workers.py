"""Outbox worker and atomic terrain publication. Run independently from HTTP."""
from datetime import datetime, timezone
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from .common import APIError
from .operations import DEFAULT_MAP
from .security import ROLE_RANK, PERMISSIONS, canonical_role, adapter_signature, token
from .storage import encoded, identifier, now_ms

def verify_adapter_envelope(service,body,timestamp,nonce,signature):
    if not service.config.adapter_secret:
        raise APIError(503,"The game adapter is not configured.","adapter_unconfigured")
    try:
        value=int(timestamp)
    except (ValueError,TypeError):
        raise APIError(401,"Invalid adapter signature.")
    if abs(now_ms()//1000-value)>300 or not isinstance(nonce,str) or not re.fullmatch(r"[A-Za-z0-9_-]{16,96}",nonce):
        raise APIError(401,"Expired or invalid adapter signature.")
    expected=adapter_signature(service.config.adapter_secret,str(timestamp),nonce,body)
    if not isinstance(signature,str) or not hmac.compare_digest(expected,signature):
        raise APIError(401,"Invalid adapter signature.")
    with service.store.transaction() as conn:
        if conn.execute("SELECT nonce FROM adapter_nonces WHERE nonce=?",(nonce,)).fetchone():
            raise APIError(409,"Adapter nonce was already used.","replay")
        conn.execute("INSERT INTO adapter_nonces VALUES(?,?)",(nonce,now_ms()))

def claim_actions(service,limit=20):
    if not service.config.adapter_url:
        return []
    operations=[]
    with service.store.transaction() as conn:
        for row in conn.execute("SELECT * FROM actions WHERE status='queued' OR (status='dispatched' AND lease_until<=?) ORDER BY created_at LIMIT 100",(now_ms(),)).fetchall():
            if len(operations)>=limit:
                break
            if row["action"] not in service.config.adapter_capabilities:
                continue
            params=json.loads(row["params"])
            bound=params.pop("_target",None)
            if not bound:
                continue
            if row["status"]=="queued":
                actor=conn.execute("SELECT * FROM users WHERE id=?",(row["actor_id"],)).fetchone()
                target=conn.execute("SELECT * FROM users WHERE id=?",(row["target_id"],)).fetchone()
                allowed=False
                if row["actor_id"]=="system-timer" and row["action"] in ("sanction.revoke","effect.revert"):
                    allowed=bool(target and target["game_link_verified"] and bound.get("uuid")==target["game_uuid"])
                elif row["action"]=="purchase.deliver":
                    order=conn.execute("SELECT * FROM orders WHERE id=? AND user_id=?",(row["order_id"],row["actor_id"])).fetchone()
                    allowed=bool(order and order["status"] not in ("refunded","cancelled") and target and bound.get("uuid")==target["game_uuid"])
                elif row["action"]=="inventory.refresh":
                    allowed=bool(actor and actor["enabled"] and canonical_role(actor["role"]) and target and target["game_link_verified"] and
                        bound.get("uuid")==target["game_uuid"] and (actor["id"]==target["id"] or "players.read" in PERMISSIONS[actor["role"]]))
                else:
                    spec=service.registry.get(row["action"],{"permission":"players.moderate" if row["action"]=="sanction.revoke" else "server.manage"})
                    allowed=bool(actor and actor["enabled"] and canonical_role(actor["role"]) and spec["permission"] in PERMISSIONS[actor["role"]])
                    if allowed and row["target_id"]!="*":
                        allowed=bool(target and canonical_role(target["role"]) and ROLE_RANK[actor["role"]]>ROLE_RANK[target["role"]] and bound.get("uuid")==target["game_uuid"])
                    if allowed and row["target_id"]=="*":
                        for player in bound.get("players",[]):
                            current=conn.execute("SELECT * FROM users WHERE id=?",(player["accountId"],)).fetchone()
                            if not current or not canonical_role(current["role"]) or current["game_uuid"]!=player["uuid"] or ROLE_RANK[current["role"]]>=ROLE_RANK[actor["role"]]:
                                allowed=False;break
                if not allowed:
                    conn.execute("UPDATE actions SET status='cancelled',updated_at=? WHERE id=?",(now_ms(),row["id"]))
                    service.store.audit(conn,"worker","operation.cancel",row["target_id"],"Authority changed before dispatch",{"operationId":row["id"]})
                    continue
            conn.execute("UPDATE actions SET status='dispatched',attempts=attempts+1,lease_until=?,updated_at=? WHERE id=?",
                (now_ms()+60000,now_ms(),row["id"]))
            operations.append({"operationId":row["id"],"action":row["action"],"target":bound,"params":params,
                "actorId":row["actor_id"],"reason":row["reason"],"createdAt":row["created_at"],"orderId":row["order_id"]})
    return operations

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):
        return None

def dispatch_actions(service):
    results=[]
    opener=urllib.request.build_opener(NoRedirect())
    started=time.monotonic()
    for _ in range(20):
        if time.monotonic()-started>=45:
            break
        # A previous transport can take several seconds. Expire queued sanctions
        # before claiming the next operation instead of reserving a whole batch
        # which may already be stale by the time it reaches the game adapter.
        service.timers()
        operations=claim_actions(service,limit=1)
        if not operations:
            break
        operation=operations[0]
        body=encoded(operation).encode()
        timestamp=str(now_ms()//1000);nonce=token()
        headers={"Content-Type":"application/json","X-CM-Timestamp":timestamp,"X-CM-Nonce":nonce,
            "X-CM-Signature":adapter_signature(service.config.adapter_secret,timestamp,nonce,body)}
        request=urllib.request.Request(service.config.adapter_url,data=body,headers=headers,method="POST")
        try:
            with opener.open(request,timeout=10) as response:
                if response.status==202:
                    results.append({"operationId":operation["operationId"],"status":"awaiting_ack"})
                    continue
                raw=response.read(65537)
                if len(raw)>65536:
                    raise ValueError("Adapter response exceeds limit.")
                verify_adapter_envelope(service,raw,response.headers.get("X-CM-Timestamp"),response.headers.get("X-CM-Nonce"),response.headers.get("X-CM-Signature"))
                ack=json.loads(raw)
                if ack.get("operationId")!=operation["operationId"]:
                    raise ValueError("Adapter acknowledged a different operation.")
                results.append(service.acknowledge(ack))
        except (OSError,ValueError,APIError,urllib.error.HTTPError):
            results.append({"operationId":operation["operationId"],"status":"awaiting_ack"})
            with service.store.transaction() as conn:
                service.store.audit(conn,"worker","operation.transport",operation["target"].get("accountId","*"),
                    "No verified acknowledgement; operation retains its stable id for retry",{"operationId":operation["operationId"]})
    return results

def captured_at_ms(manifest,override=None):
    source=manifest.get("source",{})
    if source.get("kind")=="directory" and override is not None:
        raise ValueError("A live directory completion time cannot be overridden.")
    value=override or source.get("snapshotCompletedAt") or source.get("capturedAt")
    if not isinstance(value,str):
        raise ValueError("A source capture time with an explicit timezone is required.")
    parsed=datetime.fromisoformat(value.replace("Z","+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("Archive capture timezone is unspecified; publication requires an operator-confirmed timezone.")
    result=int(parsed.timestamp()*1000)
    if result>now_ms()+60000:
        raise ValueError("Source capture time is in the future.")
    return result

def publish_map(service,staging,job_id=None,captured_at=None,lease_owner=None):
    staging=Path(staging).resolve()
    source_file=staging/"data"/"map-manifest.json"
    if not source_file.is_file():
        source_file=staging/"map-manifest.json"
    manifest=json.loads(source_file.read_text(encoding="utf-8"))
    if manifest.get("errors") not in (None,0) or not isinstance(manifest.get("dimensions"),list):
        raise ValueError("The terrain renderer reported incomplete output.")
    capture=captured_at_ms(manifest,captured_at)
    policy=service.map_settings()
    if not policy["enabled"] or capture>now_ms()-policy["minAgeMinutes"]*60000:
        return None
    tiles=staging/"assets"/"map"
    if not tiles.is_dir() or tiles.is_symlink():
        raise ValueError("Renderer did not produce a raster tile directory.")
    publication=identifier()
    root=service.store.directory/"maps"
    root.mkdir(exist_ok=True,mode=0o700)
    temporary=root/("."+publication+".tmp")
    final=root/publication
    temporary.mkdir(mode=0o700)
    count=0
    try:
        for source in tiles.rglob("*"):
            if source.is_symlink():
                raise ValueError("Symlinks are not accepted from a map renderer.")
            if not source.is_file():
                continue
            if source.suffix.lower() not in (".png",".webp"):
                continue
            relative=source.relative_to(tiles)
            if not re.fullmatch(r"[A-Za-z0-9_./-]+",relative.as_posix()) or ".." in relative.parts:
                raise ValueError("Unsafe raster filename.")
            with source.open("rb") as handle:
                header=handle.read(16)
            if not (header.startswith(b"\x89PNG\r\n\x1a\n") or (header.startswith(b"RIFF") and header[8:12]==b"WEBP")):
                raise ValueError("Unexpected raster file signature.")
            count+=1
            if count>100000:
                raise ValueError("Too many map tiles.")
            destination=temporary/relative
            destination.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(source,destination)
        if not count:
            raise ValueError("Renderer produced no raster tiles.")
        dimensions=[]
        scalar_fields={"id","name","regionFiles","savedChunks","chunks","skippedUnfinishedChunks","emptySurfaceChunks",
            "surfacePixels","surfaceMinY","surfaceMaxY","renderMode","renderDescription","externalChunks"}
        nested_fields={"bounds":{"minX","minZ","maxX","maxZ","width","height"},"savedBounds":{"minX","minZ","maxX","maxZ"},
            "sourceSpawn":{"x","y","z"},"overviewMetadata":{"width","height","blocksPerPixel"}}
        for value in manifest["dimensions"]:
            if not isinstance(value,dict) or value.get("id") not in service.config.worlds:
                raise ValueError("Unknown map dimension.")
            dimension={k:v for k,v in value.items() if k in scalar_fields and isinstance(v,(str,int,float,bool))}
            for field,keys in nested_fields.items():
                if field in value:
                    if not isinstance(value[field],dict):
                        raise ValueError("Invalid nested map metadata.")
                    dimension[field]={k:v for k,v in value[field].items() if k in keys and isinstance(v,(int,float)) and not isinstance(v,bool)}
            for field in ("dataVersions","chunkStatuses"):
                if field in value:
                    if not isinstance(value[field],dict):
                        raise ValueError("Invalid chunk metadata counts.")
                    dimension[field]={k:v for k,v in value[field].items() if re.fullmatch(r"[a-z0-9_:.-]{1,80}",str(k)) and isinstance(v,int) and not isinstance(v,bool) and v>=0}
            for key in ("overview",):
                raw=value.get(key,"")
                if not raw.startswith("assets/map/") or ".." in raw:
                    raise ValueError("Unsafe map overview reference.")
                relative=raw[len("assets/map/"):]
                if not (temporary/relative).is_file():
                    raise ValueError("Map overview is missing.")
                dimension[key]="/map-tiles/"+publication+"/"+relative
            config=value.get("tiles",{})
            template=config.get("template","")
            if not re.fullmatch(r"assets/map/"+re.escape(value["id"])+r"/\{zoom\}/\{x\}/\{z\}\.(?:png|webp)",template):
                raise ValueError("Unsafe map tile template.")
            levels=[]
            if not isinstance(config.get("levels",[]),list):
                raise ValueError("Invalid map tile levels.")
            for level in config.get("levels",[]):
                if not isinstance(level,dict) or not isinstance(level.get("zoom"),int) or isinstance(level["zoom"],bool):
                    raise ValueError("Invalid map zoom level.")
                coordinates=level.get("coordinates")
                if not isinstance(coordinates,list) or len(coordinates)>100000:
                    raise ValueError("Invalid tile coordinate list.")
                seen=set()
                for coordinate in coordinates:
                    if not isinstance(coordinate,list) or len(coordinate)!=2 or any(not isinstance(n,int) or isinstance(n,bool) or abs(n)>10000000 for n in coordinate):
                        raise ValueError("Invalid tile coordinate.")
                    if tuple(coordinate) in seen:
                        raise ValueError("Duplicate tile coordinate.")
                    seen.add(tuple(coordinate))
                    try:
                        raster=template.format(zoom=level["zoom"],x=coordinate[0],z=coordinate[1])[len("assets/map/"):]
                    except (KeyError,ValueError):
                        raise ValueError("Invalid tile template placeholders.")
                    if not (temporary/raster).is_file():
                        raise ValueError("A referenced map tile is missing.")
                if level.get("tileCount")!=len(coordinates):
                    raise ValueError("Map tile count is inconsistent.")
                pixel_size=level.get("blocksPerPixel")
                if not isinstance(pixel_size,int) or isinstance(pixel_size,bool) or not 1<=pixel_size<=1024:
                    raise ValueError("Invalid tile scale.")
                levels.append({"zoom":level["zoom"],"blocksPerPixel":pixel_size,"tileCount":len(coordinates),"coordinates":coordinates})
            dimension["tiles"]={k:v for k,v in config.items() if k in ("tileSize","minZoom","maxZoom") and isinstance(v,int) and not isinstance(v,bool)}
            dimension["tiles"]["levels"]=levels
            dimension["tiles"]["template"]="/map-tiles/"+publication+"/"+template[len("assets/map/"):]
            dimensions.append(dimension)
        safe_source={k:v for k,v in manifest.get("source",{}).items() if k in ("kind","sha256","capturedAt","captureTimeSource","snapshotStartedAt","snapshotCompletedAt","consistency","stableReadRetries","bytes","regionBytes","filesRead") and isinstance(v,(str,int,float,bool))}
        public={"schemaVersion":1,"publicationId":publication,"generatedAt":manifest.get("generatedAt"),
            "publishedAt":datetime.now(timezone.utc).isoformat(),"source":safe_source,"capturedAt":safe_source.get("capturedAt"),
            "privacyEligibleAfter":datetime.fromtimestamp((capture+policy["minAgeMinutes"]*60000)/1000,timezone.utc).isoformat(),"tileSize":manifest.get("tileSize",512),
            "dimensions":dimensions,"errors":0,"privacy":{"terrainOnly":True,"noEntities":True,"noContainers":True},
            "coverage":manifest.get("coverage") if isinstance(manifest.get("coverage"),str) else None,
            "colorNotes":manifest.get("colorNotes") if isinstance(manifest.get("colorNotes"),str) else None,
            "renderer":manifest.get("renderer") if isinstance(manifest.get("renderer"),str) else None}
        os.replace(temporary,final)
        with service.store.transaction() as conn:
            if job_id:
                job=conn.execute("SELECT * FROM jobs WHERE id=?",(job_id,)).fetchone()
                if not job or job["status"] not in ("running","awaiting_age") or job["lease_owner"]!=lease_owner:
                    shutil.rmtree(final)
                    raise ValueError("The map worker no longer owns this job.")
            current=service.store.setting("map_settings",dict(DEFAULT_MAP),conn)
            if not current["enabled"] or capture>now_ms()-current["minAgeMinutes"]*60000:
                shutil.rmtree(final)
                return None
            conn.execute("INSERT INTO map_publications VALUES(?,?,?,?,?,?)",(publication,capture,now_ms(),encoded(public),str(final),current["version"]))
            if job_id:
                conn.execute("UPDATE jobs SET status='published',updated_at=?,lease_until=NULL WHERE id=?",(now_ms(),job_id))
            service.store.audit(conn,"map-worker","map.publish","map","Terrain snapshot met publication policy",
                {"publicationId":publication,"capturedAt":capture,"policyVersion":current["version"],"tileFiles":count})
        return public
    except BaseException:
        if temporary.exists():
            shutil.rmtree(temporary)
        if final.exists():
            shutil.rmtree(final)
        raise

def run_map_jobs(service):
    """One dedicated map-worker iteration. It must never run in the action worker.

    A render has a bounded process timeout and a longer durable lease. Privacy-aged
    output waits independently, so a 60-minute publication delay does not prevent
    collecting a newer snapshot every minute. A lost lease cannot publish output.
    """
    settings=service.map_settings()
    lease_owner=token()
    at=now_ms()
    with service.store.transaction() as conn:
        service.recover_map_leases(conn,at)
        if service.config.map_source and settings["enabled"] and settings.get("autoRefresh",True):
            pending=conn.execute("SELECT id FROM jobs WHERE kind='map' AND status IN ('queued','running')").fetchone()
            latest=conn.execute("SELECT MAX(created_at) FROM jobs WHERE kind='map'").fetchone()[0]
            if not pending and (not latest or latest<=at-settings["intervalMinutes"]*60000):
                job=identifier()
                conn.execute("INSERT INTO jobs(id,kind,status,created_at,updated_at,payload) VALUES(?,?,?,?,?,?)",(job,"map","queued",at,at,encoded({"scheduled":True})))
        waiting=conn.execute("SELECT * FROM jobs WHERE kind='map' AND status='awaiting_age' AND (lease_until IS NULL OR lease_until<=?) ORDER BY updated_at LIMIT 1",(at,)).fetchone() if settings["enabled"] else None
        if waiting:
            conn.execute("UPDATE jobs SET lease_owner=?,lease_until=? WHERE id=?",(lease_owner,at+2400000,waiting["id"]))
        running=conn.execute("SELECT id FROM jobs WHERE kind='map' AND status='running'").fetchone()
        queued=conn.execute("SELECT * FROM jobs WHERE kind='map' AND status='queued' ORDER BY created_at LIMIT 1").fetchone() if settings["enabled"] and not running else None
        if queued:
            conn.execute("UPDATE jobs SET status='running',updated_at=?,lease_owner=?,lease_until=? WHERE id=?",(at,lease_owner,at+2400000,queued["id"]))
    waiting_result=None
    if waiting:
        try:
            result=publish_map(service,service.store.directory/"map-staging"/waiting["id"],waiting["id"],lease_owner=lease_owner)
            waiting_result={"jobId":waiting["id"],"status":"published" if result else "awaiting_age"}
            if result is None:
                with service.store.transaction() as conn:
                    conn.execute("UPDATE jobs SET updated_at=?,lease_until=NULL,lease_owner=NULL WHERE id=? AND lease_owner=?",(now_ms(),waiting["id"],lease_owner))
        except (ValueError,OSError,TypeError):
            with service.store.transaction() as conn:
                conn.execute("UPDATE jobs SET status='failed',updated_at=?,error=?,lease_until=NULL WHERE id=? AND lease_owner=?",(now_ms(),"Map output cannot be published; inspect the private worker log.",waiting["id"],lease_owner))
            waiting_result={"jobId":waiting["id"],"status":"failed"}
    if not queued:
        return waiting_result
    job=queued["id"]
    staging=service.store.directory/"map-staging"/job
    staging.parent.mkdir(exist_ok=True,mode=0o700)
    try:
        if not service.config.map_source:
            raise ValueError("Map source is not configured.")
        script=service.config.site_dir/"scripts"/"render_world_tiles.py"
        palette=service.config.site_dir/"scripts"/"map-palette.json"
        log=service.store.directory/("map-job-"+job+".log")
        with log.open("wb") as output:
            result=subprocess.run([sys.executable,str(script),service.config.map_source,"--output",str(staging),"--palette",str(palette)],
                stdin=subprocess.DEVNULL,stdout=output,stderr=subprocess.STDOUT,shell=False,timeout=1800)
        if result.returncode:
            raise ValueError("Renderer failed.")
        publication=publish_map(service,staging,job,lease_owner=lease_owner)
        if publication is None:
            with service.store.transaction() as conn:
                conn.execute("UPDATE jobs SET status='awaiting_age',updated_at=?,lease_until=NULL,lease_owner=NULL WHERE id=? AND lease_owner=?",(now_ms(),job,lease_owner))
        return {"jobId":job,"status":"published" if publication else "awaiting_age"}
    except (OSError,ValueError,TypeError,subprocess.TimeoutExpired):
        with service.store.transaction() as conn:
            conn.execute("UPDATE jobs SET status='failed',updated_at=?,error=?,lease_until=NULL WHERE id=? AND lease_owner=?",
                (now_ms(),"Map render did not complete. Check source configuration and the private worker log.",job,lease_owner))
        return {"jobId":job,"status":"failed"}

def worker_once(service):
    return {"timers":service.timers(),"actions":dispatch_actions(service)}

