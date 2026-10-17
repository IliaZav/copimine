#!/usr/bin/env python3
"""CopiMine v6 same-host game adapter. No third-party Python dependencies.
Only cmultra sitebridge execute UUID is emitted through RCON.
The plugin's signed durable receipt, never RCON text, proves execution.
"""
from __future__ import annotations
import argparse
import base64
import hashlib
import hmac
import http.server
import json
import os
import pathlib
import re
import secrets
import socket
import sqlite3
import ssl
import struct
import threading
import time
import urllib.parse
import urllib.request
import uuid

VERSION = "copimine-site-bridge/1.0.0"
MAX_BODY = 1024 * 1024
MAX_RECEIPT = 32 * 1024 * 1024
ROOT = pathlib.Path(__file__).resolve().parent
LOCAL_ACTIONS = {"money.adjust", "purchase.limit", "sessions.revoke"}
INTERNAL_ACTIONS = {"inventory.refresh", "purchase.deliver", "sanction.revoke", "effect.revert"}

class ProtocolError(ValueError):
    pass

def json_bytes(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False, allow_nan=False).encode("utf-8")

def strict_json(data):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ProtocolError("Duplicate JSON key")
            result[key] = value
        return result
    try:
        return json.loads(data, object_pairs_hook=pairs, parse_constant=lambda x: (_ for _ in ()).throw(ProtocolError("Nonfinite JSON")))
    except (UnicodeError, ValueError, TypeError) as error:
        raise ProtocolError("Invalid JSON") from error

def envelope(secret, payload):
    body = json_bytes(payload)
    return json_bytes({"payload": base64.b64encode(body).decode("ascii"),
                       "signature": hmac.new(secret, body, hashlib.sha256).hexdigest()})

def open_envelope(secret, raw):
    if len(raw) > MAX_RECEIPT:
        raise ProtocolError("Envelope too large")
    value = strict_json(raw)
    try:
        body = base64.b64decode(value["payload"], validate=True)
        signature = value["signature"]
        if not isinstance(signature, str) or not hmac.compare_digest(signature, hmac.new(secret, body, hashlib.sha256).hexdigest()):
            raise ProtocolError("Receipt authentication failed")
    except (KeyError, TypeError, ValueError) as error:
        raise ProtocolError("Invalid signed envelope") from error
    result = strict_json(body)
    if not isinstance(result, dict):
        raise ProtocolError("Envelope payload must be an object")
    return result

def sign_headers(secret, body):
    timestamp = str(int(time.time()))
    nonce = secrets.token_urlsafe(24)
    signed = timestamp.encode() + b"\n" + nonce.encode() + b"\n" + body
    return {"X-CM-Timestamp": timestamp, "X-CM-Nonce": nonce,
            "X-CM-Signature": hmac.new(secret, signed, hashlib.sha256).hexdigest(),
            "Content-Type": "application/json"}

def atomic_write(path, content):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + "." + secrets.token_hex(8) + ".tmp")
    try:
        with open(temporary, "xb") as handle:
            if os.name != "nt":
                os.chmod(temporary, 0o600)
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        if os.name != "nt":
            directory = os.open(path.parent, os.O_RDONLY)
            try:
                os.fsync(directory)
            finally:
                os.close(directory)
    finally:
        temporary.unlink(missing_ok=True)

class AuthStore:
    def __init__(self, path, secret):
        self.path = str(path)
        self.secret = secret
        pathlib.Path(path).parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(self.path) as db:
            db.execute("CREATE TABLE IF NOT EXISTS nonces(nonce TEXT PRIMARY KEY, at INTEGER NOT NULL)")
        if os.name != "nt":
            os.chmod(self.path, 0o600)

    def verify(self, headers, body):
        timestamp = headers.get("X-CM-Timestamp", "")
        nonce = headers.get("X-CM-Nonce", "")
        signature = headers.get("X-CM-Signature", "")
        if not re.fullmatch(r"[0-9]{10,12}", timestamp) or abs(int(time.time())-int(timestamp)) > 300:
            raise ProtocolError("Expired request")
        if not re.fullmatch(r"[A-Za-z0-9_-]{16,96}", nonce) or not re.fullmatch(r"[0-9a-f]{64}", signature):
            raise ProtocolError("Invalid request signature")
        expected = hmac.new(self.secret, timestamp.encode()+b"\n"+nonce.encode()+b"\n"+body, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expected, signature):
            raise ProtocolError("Request authentication failed")
        with sqlite3.connect(self.path, timeout=10) as db:
            db.execute("DELETE FROM nonces WHERE at<?", (int(time.time())-600,))
            try:
                db.execute("INSERT INTO nonces VALUES(?,?)", (nonce, int(time.time())))
            except sqlite3.IntegrityError as error:
                raise ProtocolError("Replayed request") from error

def canonical_uuid(value):
    try:
        result = str(uuid.UUID(value))
    except (ValueError, TypeError, AttributeError) as error:
        raise ProtocolError("A canonical UUID is required") from error
    if value != result:
        raise ProtocolError("A canonical UUID is required")
    return result

def load_actions():
    path = ROOT / "actions.json"
    if not path.exists():
        raise ProtocolError("The verified action registry is missing")
    obj = strict_json(path.read_bytes())
    return {row["id"]: row for row in obj["actions"]}

def validate_operation(op):
    if not isinstance(op, dict):
        raise ProtocolError("Operation must be an object")
    canonical_uuid(op.get("operationId"))
    created=op.get("createdAt")
    if isinstance(created,bool) or not isinstance(created,int) or not 1<=created<=int(time.time()*1000)+60000:
        raise ProtocolError("Invalid operation creation time")
    action = op.get("action")
    registry = load_actions()
    if action in LOCAL_ACTIONS:
        raise ProtocolError("This operation is performed transactionally by the site")
    if action not in registry and action not in INTERNAL_ACTIONS:
        raise ProtocolError("Unsupported typed action")
    params = op.get("params", {})
    if not isinstance(params, dict):
        raise ProtocolError("Parameters must be an object")
    target = op.get("target")
    if not isinstance(target, dict):
        raise ProtocolError("Target identity required")
    if target.get("scope") == "server":
        players = target.get("players", [])
        if not isinstance(players, list) or len(players) > 1000:
            raise ProtocolError("Invalid bound player set")
    else:
        players = [target]
    for player in players:
        if not isinstance(player, dict):
            raise ProtocolError("Invalid target")
        canonical_uuid(player.get("uuid"))
        if not isinstance(player.get("accountId"), str) or not 1 <= len(player["accountId"]) <= 100:
            raise ProtocolError("Verified account binding required")
        if player.get("name") is not None and not re.fullmatch(r"[A-Za-z0-9_]{1,32}", player["name"]):
            raise ProtocolError("Invalid target name")
    if action in registry:
        schema = registry[action].get("params", {})
        permitted = set(schema) | ({"durationMs", "expiresAt"} if action == "ban.ip" else set())
        if set(params) - permitted:
            raise ProtocolError("Unknown typed parameter")
        for key, definition in schema.items():
            value = params.get(key)
            if value is None and not definition.get("required"):
                continue
            kind = definition.get("type")
            valid = ((kind == "integer" and isinstance(value, int) and not isinstance(value, bool)) or
                     (kind == "number" and isinstance(value, (float,int)) and not isinstance(value,bool)) or
                     (kind == "boolean" and isinstance(value,bool)) or
                     (kind == "string" and isinstance(value,str) and len(value)<=1000))
            if not valid:
                raise ProtocolError("Invalid typed parameter: "+key)
            if "enum" in definition and value not in definition["enum"]:
                raise ProtocolError("Invalid enum parameter: "+key)
            if isinstance(value,(int,float)) and ("minimum" in definition and value < definition["minimum"] or "maximum" in definition and value > definition["maximum"]):
                raise ProtocolError("Parameter outside allowed range")
    if len(json_bytes(op)) > MAX_BODY:
        raise ProtocolError("Operation too large")

class SpoolBridge:
    def __init__(self, root, secret, dispatch):
        self.root = pathlib.Path(root).resolve()
        self.secret = secret
        self.dispatch = dispatch
        self.lock = threading.RLock()
        for folder in ("incoming", "receipts", "requested", "published", "profiles", "published-profiles"):
            (self.root/folder).mkdir(parents=True, exist_ok=True)

    def submit(self, operation):
        validate_operation(operation)
        operation_id = operation["operationId"]
        path = self.root/"incoming"/(operation_id+".json")
        digest = hashlib.sha256(json_bytes(operation)).hexdigest()
        receipt_path = self.root/"receipts"/(operation_id+".json")
        requested = self.root/"requested"/(operation_id+".json")
        with self.lock:
            if path.exists():
                previous = open_envelope(self.secret, path.read_bytes())
                if json_bytes(previous) != json_bytes(operation):
                    raise ProtocolError("Operation identifier already binds different content")
            else:
                atomic_write(path, envelope(self.secret, operation))
            if receipt_path.exists():
                receipt = open_envelope(self.secret, receipt_path.read_bytes())
                if receipt.get("operationId") != operation_id:
                    raise ProtocolError("Receipt belongs to another operation")
                if receipt.get("requestSha256") != digest:
                    raise ProtocolError("Receipt request hash mismatch")
                if receipt.get("status") in ("pending", "uncertain"):
                    return None
                if receipt.get("status") not in ("executed", "failed"):
                    raise ProtocolError("Invalid final receipt state")
                if receipt["status"] == "failed" and receipt.get("atomicNoDelivery") is not True:
                    raise ProtocolError("Failure receipt does not prove non-delivery")
                canonical_uuid(receipt.get("ackId"))
                executed=receipt.get("executedAt")
                if isinstance(executed,bool) or not isinstance(executed,int) or not max(0,operation["createdAt"]-60000)<=executed<=int(time.time()*1000)+60000:
                    raise ProtocolError("Invalid execution time")
                if not isinstance(receipt.get("snapshots",[]),list):
                    raise ProtocolError("Invalid snapshot receipt")
                identities = operation["target"].get("players",[]) if operation["target"].get("scope")=="server" else [operation["target"]]
                allowed = {(p["accountId"],p["uuid"]) for p in identities}
                for snapshot in receipt.get("snapshots",[]):
                    if (snapshot.get("playerId"),snapshot.get("gameUUID")) not in allowed:
                        raise ProtocolError("Snapshot identity does not match bound operation")
                if operation["action"] == "inventory.refresh" and receipt["status"]=="executed":
                    snapshot_id=receipt.get("result",{}).get("snapshotId")
                    if not any(s.get("snapshotId")==snapshot_id for s in receipt.get("snapshots",[])):
                        raise ProtocolError("Inventory refresh requires its actual signed snapshot")
                return receipt
            # Re-signal after lost connections; the plugin never repeats an applying operation.
            last = requested.stat().st_mtime if requested.exists() else 0
            if time.time() - last >= 5:
                atomic_write(requested, json_bytes({"operationId":operation_id,"requestSha256":digest,"at":int(time.time()*1000)}))
                try:
                    self.dispatch("cmultra sitebridge execute "+operation_id)
                except (OSError,TimeoutError,ProtocolError):
                    return None
            return None

class Rcon:
    """Source RCON framing matching the verified admin-web RconClient."""
    def __init__(self, host, port, password, timeout=5):
        if host not in ("127.0.0.1","::1","localhost"):
            raise ProtocolError("The spool adapter requires same-host loopback RCON")
        self.host,self.port,self.password,self.timeout = host,int(port),password,float(timeout)
    @staticmethod
    def recv_exact(sock,size):
        output=b""
        while len(output)<size:
            chunk=sock.recv(size-len(output))
            if not chunk:
                raise OSError("RCON connection closed")
            output+=chunk
        return output
    def send(self,sock,request_id,kind,value):
        data=struct.pack("<ii",request_id,kind)+value.encode("utf-8")+b"\0\0"
        sock.sendall(struct.pack("<i",len(data))+data)
    def recv(self,sock):
        size=struct.unpack("<i",self.recv_exact(sock,4))[0]
        if not 10<=size<=1024*1024:
            raise ProtocolError("Invalid RCON packet")
        data=self.recv_exact(sock,size)
        if data[-2:]!=b"\0\0":
            raise ProtocolError("Invalid RCON terminator")
        request_id,kind=struct.unpack("<ii",data[:8])
        return request_id,kind,data[8:-2].decode("utf-8","replace")
    def __call__(self,command):
        if not re.fullmatch(r"cmultra sitebridge execute [a-f0-9-]{36}",command):
            raise ProtocolError("Command is outside the fixed bridge protocol")
        with socket.create_connection((self.host,self.port),timeout=self.timeout) as sock:
            sock.settimeout(self.timeout)
            self.send(sock,101,3,self.password)
            for _ in range(3):
                ident,kind,_=self.recv(sock)
                if ident==-1:
                    raise ProtocolError("RCON authentication failed")
                if ident==101 and kind==2:
                    break
            else:
                raise ProtocolError("Missing RCON auth response")
            self.send(sock,102,2,command)
            self.recv(sock)  # Intentionally never interpreted as execution proof.

def secret_file(path,raw_text=False):
    path=pathlib.Path(path)
    value=path.read_text(encoding="utf-8").strip()
    if not re.fullmatch(r"[0-9a-fA-F]{64}",value):
        raise ProtocolError("Secrets must be independently generated 32-byte hex keys")
    if os.name!="nt" and path.stat().st_mode & 0o077:
        raise ProtocolError("Secret file permissions must be 0600")
    return value.encode("ascii") if raw_text else bytes.fromhex(value)

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):
        return None

class Application:
    def __init__(self,config):
        self.config=config
        # The site signs with the exact UTF-8 text of CM_V6_ADAPTER_SECRET.
        self.site_secret=secret_file(config["siteSecretFile"],raw_text=True)
        self.spool_secret=secret_file(config["spoolSecretFile"])
        self.auth=AuthStore(pathlib.Path(config["statePath"])/"auth.sqlite3",self.site_secret)
        self.spool=SpoolBridge(config["spoolPath"],self.spool_secret,
            Rcon(config.get("rconHost","127.0.0.1"),config.get("rconPort",25575),
                 pathlib.Path(config["rconPasswordFile"]).read_text(encoding="utf-8").strip()))
        self.site_url=config["siteBaseURL"].rstrip("/")
        parts=urllib.parse.urlparse(self.site_url)
        if parts.scheme!="https" and not (parts.scheme=="http" and parts.hostname in ("127.0.0.1","::1","localhost")):
            raise ProtocolError("Remote site API requires HTTPS")
        self.stopping=threading.Event()
        self.publish_lock=threading.RLock()
        self.opener=urllib.request.build_opener(NoRedirect())
    def post_site(self,path,value):
        body=json_bytes(value)
        request=urllib.request.Request(self.site_url+path,data=body,headers=sign_headers(self.site_secret,body),method="POST")
        with self.opener.open(request,timeout=15) as response:
            if response.status not in (200,201,202):
                raise ProtocolError("Site did not accept receipt")
            return strict_json(response.read(MAX_BODY))
    def publish(self,operation,receipt):
        marker=self.spool.root/"published"/(operation["operationId"]+".json")
        with self.publish_lock:
            if marker.exists():
                return
            for snapshot in receipt.get("snapshots",[]):
                self.post_site("/api/v6/adapter/snapshots",snapshot)
            ack={key:receipt[key] for key in ("operationId","ackId","status","executedAt","result")}
            self.post_site("/api/v6/adapter/ack",ack)
            atomic_write(marker,json_bytes({"ackId":receipt["ackId"],"at":int(time.time()*1000)}))
    def publish_profile(self,path):
        path=pathlib.Path(path)
        if path.is_symlink() or not path.is_file() or path.stat().st_size>MAX_RECEIPT:
            raise ProtocolError("Invalid private profile file")
        snapshot=open_envelope(self.spool_secret,path.read_bytes())
        game_id=canonical_uuid(snapshot.get("gameUUID"))
        snapshot_id=canonical_uuid(snapshot.get("snapshotId"))
        if path.name!=game_id+".json":
            raise ProtocolError("Profile filename does not match its signed identity")
        account_id=snapshot.get("playerId")
        captured=snapshot.get("capturedAt")
        if not isinstance(account_id,str) or not 1<=len(account_id)<=100:
            raise ProtocolError("Verified account binding required")
        if isinstance(captured,bool) or not isinstance(captured,int) or not int(time.time()*1000)-30*86400000<=captured<=int(time.time()*1000)+60000:
            raise ProtocolError("Invalid profile capture time")
        if not isinstance(snapshot.get("inventory"),list) or not isinstance(snapshot.get("ender"),list) or not isinstance(snapshot.get("profile"),dict):
            raise ProtocolError("Invalid profile snapshot")
        digest=hashlib.sha256(json_bytes(snapshot)).hexdigest()
        marker=self.spool.root/"published-profiles"/(game_id+".json")
        with self.publish_lock:
            if marker.exists():
                previous=strict_json(marker.read_bytes())
                if previous.get("snapshotId")==snapshot_id:
                    if previous.get("sha256")!=digest:
                        raise ProtocolError("Published profile identifier changed content")
                    return
                if previous.get("playerId")==account_id and previous.get("capturedAt",0)>captured:
                    return
            # The site checks this account's current verified game UUID again.
            # No operation or ACK is invented for periodic read-only snapshots.
            result=self.post_site("/api/v6/adapter/snapshots",snapshot)
            if not isinstance(result,dict) or result.get("snapshotId")!=snapshot_id or not (result.get("accepted") is True or result.get("replayed") is True):
                raise ProtocolError("Site did not confirm this profile snapshot")
            atomic_write(marker,json_bytes({"snapshotId":snapshot_id,"sha256":digest,"playerId":account_id,
                "capturedAt":captured,"publishedAt":int(time.time()*1000)}))
    def poll(self):
        while not self.stopping.wait(2):
            for path in (self.spool.root/"incoming").glob("*.json"):
                if (self.spool.root/"published"/path.name).exists():
                    continue
                try:
                    operation=open_envelope(self.spool_secret,path.read_bytes())
                    receipt=self.spool.submit(operation)
                    if receipt:
                        self.publish(operation,receipt)
                except Exception as error:
                    print(json.dumps({"event":"bridge_pending","operationId":path.stem,
                                      "errorType":type(error).__name__}),flush=True)
            for path in (self.spool.root/"profiles").glob("*.json"):
                try:
                    self.publish_profile(path)
                except Exception as error:
                    print(json.dumps({"event":"profile_publication_pending","errorType":type(error).__name__}),flush=True)
    def handler(self):
        app=self
        class Handler(http.server.BaseHTTPRequestHandler):
            server_version=VERSION
            def log_message(self,*args):
                pass
            def respond(self,status,body,signed=False):
                raw=json_bytes(body)
                self.send_response(status)
                for key,value in (sign_headers(app.site_secret,raw) if signed else {"Content-Type":"application/json"}).items():
                    self.send_header(key,value)
                self.send_header("Cache-Control","no-store")
                self.send_header("Content-Length",str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)
            def do_POST(self):
                if self.path!="/operations":
                    self.respond(404,{"error":"not_found"});return
                try:
                    length=int(self.headers.get("Content-Length","0"))
                    if not 0<length<=MAX_BODY:
                        raise ProtocolError("Invalid body size")
                    body=self.rfile.read(length)
                    if len(body)!=length:
                        raise ProtocolError("Truncated body")
                    app.auth.verify(self.headers,body)
                    operation=strict_json(body)
                    receipt=app.spool.submit(operation)
                    if receipt:
                        for snapshot in receipt.get("snapshots",[]):
                            app.post_site("/api/v6/adapter/snapshots",snapshot)
                        ack={key:receipt[key] for key in ("operationId","ackId","status","executedAt","result")}
                        self.respond(200,ack,signed=True)
                    else:
                        self.respond(202,{"operationId":operation["operationId"],"status":"awaiting_game_receipt"})
                except ProtocolError:
                    self.respond(400,{"error":"invalid_or_unauthenticated_operation"})
                except Exception:
                    self.respond(503,{"error":"receipt_temporarily_unavailable"})
        return Handler

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config",type=pathlib.Path,required=True)
    parser.add_argument("--check",action="store_true",help="Validate local configuration only; never connect to the game")
    args=parser.parse_args()
    config=strict_json(args.config.read_bytes())
    app=Application(config)
    if args.check:
        print(json.dumps({"valid":True,"version":VERSION,"actionCount":len(load_actions()),"transport":"same-host-signed-spool"}))
        return
    bind=config.get("listenHost","127.0.0.1")
    server=http.server.ThreadingHTTPServer((bind,int(config.get("listenPort",8791))),app.handler())
    cert,key=config.get("tlsCertificate"),config.get("tlsKey")
    if bool(cert)!=bool(key):
        raise ProtocolError("Both TLS certificate and key are required")
    if cert:
        context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version=ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(cert,key)
        server.socket=context.wrap_socket(server.socket,server_side=True)
    elif bind not in ("127.0.0.1","::1","localhost"):
        raise ProtocolError("Public adapter listeners require TLS")
    threading.Thread(target=app.poll,name="receipt-publisher",daemon=True).start()
    print(json.dumps({"event":"adapter_ready","version":VERSION}),flush=True)
    try:
        server.serve_forever()
    finally:
        app.stopping.set()
        server.server_close()

if __name__=="__main__":
    main()

