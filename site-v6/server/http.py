"""Same-origin WSGI API and a deliberately small static-file boundary."""
from http import HTTPStatus
from http.cookies import SimpleCookie, CookieError
import hmac
import io
import ipaddress
import json
import mimetypes
from pathlib import Path, PurePosixPath
import re
from urllib.parse import parse_qs
from .common import APIError
from .security import PERMISSIONS
from .workers import verify_adapter_envelope

COOKIE="cm_v6_session"
PAGES={"/","/index.html","/shop.html","/donate.html","/map.html","/account.html","/admin.html"}
SCRIPTS=set("""core.js auth-session.js icons.js public-data-v6.js app.js map.js admin.js domain-v4.js base-v4.js
public-v4.js identity-v4.js cms-v4.js insights-v4.js operations-v4.js admin-v4.js domain-v5.js public-registry-v6.js
workspace-v5.js logs-v5.js recipes-v5.js elections-v5.js registry-v6.js actions-v6.js domain-v6.js site-v6.js
players-v6.js map-manifest-v6.js map-v6.js analytics-v6.js search-v6.js bridge-v6.js admin-v6.js auth-v6.js""".split())
API_PREFIX="/api/v6"
MAX_BODY=1024*1024

def parse_json(raw):
    def reject(value):
        raise ValueError("Non-finite JSON numbers are not permitted.")
    def unique(pairs):
        result={}
        for key,value in pairs:
            if key in result:
                raise ValueError("Duplicate JSON object key.")
            result[key]=value
        return result
    try:
        value=json.loads(raw.decode("utf-8"),parse_constant=reject,object_pairs_hook=unique)
    except (ValueError,UnicodeDecodeError):
        raise APIError(400,"Invalid JSON body.")
    if not isinstance(value,dict):
        raise APIError(400,"Expected a JSON object.")
    return value

class Application:
    def __init__(self,service):
        self.service=service
        self.config=service.config
        self.site=Path(self.config.site_dir).resolve()

    def secure(self,environ):
        if environ.get("wsgi.url_scheme")=="https":
            return True
        return bool(self.config.trust_loopback_proxy and environ.get("REMOTE_ADDR") in ("127.0.0.1","::1") and environ.get("HTTP_X_FORWARDED_PROTO")=="https")

    def client_ip(self,environ):
        """An explicit local proxy overwrites this header with its socket peer IP.

        X-Forwarded-For is intentionally not parsed: unconfigured chains cannot
        choose a rate-limit identity by submitting their own forwarding headers.
        """
        remote=environ.get("REMOTE_ADDR","unknown")
        supplied=environ.get("HTTP_X_CM_CLIENT_IP")
        if not self.config.trust_loopback_proxy or remote not in ("127.0.0.1","::1") or supplied is None:
            return remote
        try:
            if not isinstance(supplied,str) or not supplied or len(supplied)>45 or "%" in supplied:
                raise ValueError()
            address=ipaddress.ip_address(supplied)
            if isinstance(address,ipaddress.IPv6Address) and address.ipv4_mapped:
                address=address.ipv4_mapped
            return str(address)
        except ValueError:
            raise APIError(400,"The trusted proxy supplied an invalid client address.","invalid_proxy_address")

    def transport(self,environ):
        if self.config.insecure_local and not self.secure(environ) and environ.get("REMOTE_ADDR") not in ("127.0.0.1","::1"):
            raise APIError(400,"Insecure development access is restricted to loopback.","https_required")
        if not self.config.insecure_local and not self.secure(environ):
            raise APIError(400,"HTTPS is required for account requests.","https_required")

    def cookie(self,raw,expires_in=43200):
        value=SimpleCookie()
        value[COOKIE]=raw
        value[COOKIE]["path"]="/"
        value[COOKIE]["httponly"]=True
        value[COOKIE]["samesite"]="Lax"
        value[COOKIE]["max-age"]=str(max(0,expires_in))
        if not self.config.insecure_local:
            value[COOKIE]["secure"]=True
        return ("Set-Cookie",value.output(header="").strip())

    def auth(self,environ):
        cookies=SimpleCookie()
        try:
            cookies.load(environ.get("HTTP_COOKIE",""))
        except CookieError:
            return None,None
        value=cookies.get(COOKIE)
        return self.service.authenticate(value.value if value else None)

    def oauth_cookie(self,raw,expires_in=600):
        value=SimpleCookie()
        value['cm_v6_oauth']=raw
        value['cm_v6_oauth']['path']='/api/v6/auth/vk'
        value['cm_v6_oauth']['httponly']=True
        value['cm_v6_oauth']['samesite']='Lax'
        value['cm_v6_oauth']['max-age']=str(max(0,expires_in))
        if not self.config.insecure_local:
            value['cm_v6_oauth']['secure']=True
        return ('Set-Cookie',value.output(header='').strip())

    def oauth_identity(self,environ):
        try:
            cookies=SimpleCookie(environ.get('HTTP_COOKIE',''))
            value=cookies.get('cm_v6_oauth')
            return value.value if value else None
        except CookieError:
            return None

    def csrf(self,environ,session):
        self.transport(environ)
        origin=environ.get("HTTP_ORIGIN")
        if origin is not None and origin!=self.config.origin:
            raise APIError(403,"Request origin is not allowed.","origin")
        fetch=environ.get("HTTP_SEC_FETCH_SITE")
        if fetch and fetch not in ("same-origin","none"):
            raise APIError(403,"Cross-site requests are not allowed.","origin")
        supplied=environ.get("HTTP_X_CSRF_TOKEN","")
        if not session or not supplied or not hmac.compare_digest(supplied,session["csrf"]):
            raise APIError(403,"A valid session CSRF token is required.","csrf")

    def bundle(self,bundle,headers):
        from .storage import now_ms
        session=bundle["session"]
        headers.append(self.cookie(bundle["token"],(session["expires_at"]-now_ms())//1000))
        return {"user":self.service.public_user(bundle["user"],True),"csrf":session["csrf"],"mode":"server"}

    def __call__(self,environ,start_response):
        headers=[]
        try:
            status,data,content_type=self.handle(environ,headers)
        except APIError as error:
            status,data,content_type=error.status,{"error":{"code":error.code,"message":error.message},"message":error.message,"detail":error.message},"application/json; charset=utf-8"
        except Exception:
            # Tracebacks go only to the server's private stderr, never to API clients.
            import traceback
            traceback.print_exc(file=environ.get("wsgi.errors"))
            status,data,content_type=500,{"error":{"code":"internal_error","message":"The server could not complete this request."}},"application/json; charset=utf-8"
        body=json.dumps(data,ensure_ascii=False,separators=(",",":"),allow_nan=False).encode() if content_type.startswith("application/json") else data
        headers.extend([("Content-Type",content_type),("Content-Length",str(len(body))),
            ("X-Content-Type-Options","nosniff"),("X-Frame-Options","DENY"),
            ("Referrer-Policy","same-origin"),("Cache-Control","no-store"),
            ("Permissions-Policy","camera=(), microphone=(), geolocation=()"),
            ("Content-Security-Policy","default-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://mc-heads.net https://minotar.net; font-src 'self'; connect-src 'self'")])
        if self.secure(environ):
            headers.append(("Strict-Transport-Security","max-age=31536000"))
        start_response(str(status)+" "+HTTPStatus(status).phrase,headers)
        return [b"" if environ.get("REQUEST_METHOD")=="HEAD" else body]

    def handle(self,environ,headers):
        method=environ.get("REQUEST_METHOD","GET").upper()
        path=environ.get("PATH_INFO","/")
        if not isinstance(path,str) or "\\" in path or "\x00" in path or "%" in path or ".." in PurePosixPath(path).parts:
            raise APIError(404,"Not found.")
        session,user=self.auth(environ)
        if not path.startswith(API_PREFIX+"/"):
            if path.startswith("/api/"):
                raise APIError(404,"Unknown API route.")
            if method not in ("GET","HEAD"):
                raise APIError(405,"Method not allowed.")
            return self.static(path,user,session)
        endpoint=path[len(API_PREFIX):]
        query=parse_qs(environ.get("QUERY_STRING",""),keep_blank_values=False,max_num_fields=30)
        raw=b""
        body={}
        if method in ("POST","PATCH","PUT","DELETE"):
            try:
                length=int(environ.get("CONTENT_LENGTH") or "0")
            except ValueError:
                raise APIError(400,"Invalid content length.")
            if length<0 or length>MAX_BODY:
                raise APIError(413,"Request body is too large.")
            if environ.get("HTTP_TRANSFER_ENCODING"):
                raise APIError(400,"Unsupported transfer encoding.")
            if not environ.get("CONTENT_TYPE","").split(";")[0].strip().lower()=="application/json":
                raise APIError(415,"Use application/json.")
            raw=environ["wsgi.input"].read(length)
            if len(raw)!=length:
                raise APIError(400,"Incomplete request body.")
            body=parse_json(raw or b"{}")
            if endpoint in ("/adapter/ack","/adapter/snapshots"):
                self.transport(environ)
                verify_adapter_envelope(self.service,raw,environ.get("HTTP_X_CM_TIMESTAMP"),
                    environ.get("HTTP_X_CM_NONCE"),environ.get("HTTP_X_CM_SIGNATURE"))
            elif endpoint=="/payments/webhook":
                self.transport(environ)
            else:
                self.csrf(environ,session)
        svc=self.service
        ip=self.client_ip(environ)
        result=None
        code=200
        if method=="GET" and endpoint=="/session":
            self.transport(environ)
            if not session:
                result=self.bundle(svc.guest_session(),headers)
            else:
                result={"user":svc.public_user(user,True),"csrf":session["csrf"],"mode":"server"}
        elif method=="GET" and endpoint=="/auth/providers":
            result={"vk":{"enabled":svc.vk_enabled()},"email":{"enabled":False},"sms":{"enabled":False}}
            if hasattr(svc,'notification_providers'):
                result.update(svc.notification_providers())
        elif method=="POST" and endpoint=="/auth/vk/start":
            started=svc.vk_start(body,user,session,ip)
            headers.append(self.oauth_cookie(started.pop('cookie')))
            result=started
        elif method=="GET" and endpoint=="/auth/vk/callback":
            self.transport(environ)
            svc.vk_callback(query,self.oauth_identity(environ),user,session,ip)
            headers.append(('Location','/#auth/vk'))
            return 303,b'',"text/html; charset=utf-8"
        elif method=="GET" and endpoint=="/auth/vk/status":
            self.transport(environ)
            result=svc.vk_status(self.oauth_identity(environ),session)
        elif method=="POST" and endpoint=="/auth/vk/finish":
            result=self.bundle(svc.vk_finish(body,self.oauth_identity(environ),session,ip),headers)
            headers.append(self.oauth_cookie('',0))
        elif method=="GET" and endpoint=="/account/connections":
            result=svc.account_connections(user,session)
        elif method=="POST" and endpoint=="/account/vk/unlink":
            result=svc.vk_unlink(body,user,session,ip)
        elif method=="GET" and endpoint=="/account/contacts":
            result=svc.contacts(user,session)
        elif method=="POST" and endpoint=="/account/contacts/request":
            result=svc.contact_request(body,user,session,ip)
        elif method=="POST" and endpoint=="/account/contacts/verify":
            result=svc.contact_verify(body,user,session,ip)
        elif method=="POST" and endpoint=="/account/contacts/remove":
            result=svc.contact_remove(body,user,session,ip)
        elif method=="POST" and endpoint=="/auth/code/request":
            result=svc.auth_code_request(body,session,ip)
        elif method=="POST" and endpoint=="/auth/code/login":
            result=self.bundle(svc.auth_code_login(body,session,ip),headers)
        elif method=="POST" and endpoint=="/auth/recovery/request":
            result=svc.auth_code_request(body,session,ip,purpose="recovery")
        elif method=="POST" and endpoint=="/auth/recovery/confirm":
            result=self.bundle(svc.recovery_confirm(body,session,ip),headers)
        elif method=="GET" and endpoint=="/state":
            result=svc.state(user,session)
        elif method=="GET" and endpoint=="/catalog":
            result={"catalog":svc.catalog()}
        elif endpoint in ("/admin/faq","/admin/gallery") and method in ("GET","PATCH"):
            svc.require(user,session,"server.manage")
            kind=endpoint.rsplit("/",1)[1]
            result=svc.content_list(kind,True) if method=="GET" else svc.update_content_list(kind,body,user,session)
        elif method=="GET" and endpoint=="/map":
            result=svc.map()
        elif method=="GET" and endpoint=="/pages":
            result={"pages":svc.pages()}
        elif method=="GET" and endpoint=="/admin/config":
            svc.require(user,session,"server.manage")
            result={"config":svc.site_config()}
        elif method=="PATCH" and endpoint=="/admin/config":
            result=svc.update_config(body,user,session)
        elif method=="GET" and endpoint=="/admin/pages":
            svc.require(user,session,"server.manage")
            result={"pages":svc.pages(True)}
        elif method=="POST" and endpoint=="/admin/pages":
            result=svc.create_page(body,user,session);code=201
        elif method=="POST" and endpoint in ("/auth/login","/auth/register"):
            bundle=svc.login(body,session,ip) if endpoint.endswith("login") else svc.register(body,session,ip)
            result=self.bundle(bundle,headers)
            code=200 if endpoint.endswith("login") else 201
        elif method=="POST" and endpoint=="/auth/logout":
            result=self.bundle(svc.logout(session),headers)
        elif method=="POST" and endpoint in ("/auth/reauth","/auth/me/stepup"):
            result=svc.reauth(body,user,session,ip)
        elif method=="POST" and endpoint=="/auth/totp/setup":
            result=svc.totp_setup(body,user,session,ip)
        elif method=="POST" and endpoint=="/auth/totp/confirm":
            result=svc.totp_confirm(body,user,session,ip)
        elif method=="PATCH" and endpoint=="/account/profile":
            result=svc.update_profile(body,user,session)
        elif method=="POST" and endpoint=="/account/password":
            result=svc.change_password(body,user,session,ip)
        elif method=="POST" and endpoint=="/account/transfer":
            result=svc.transfer(body,user,session)
        elif method=="POST" and endpoint=="/tickets":
            result=svc.create_ticket(body,user,session);code=201
        elif method=="POST" and endpoint=="/checkout":
            result=svc.checkout(body,user,session);code=201
        elif method=="POST" and endpoint=="/payments":
            result=svc.create_payment(body,user,session);code=201
        elif method=="POST" and endpoint=="/payments/webhook":
            result=svc.payment_webhook(body,ip)
        elif method=="POST" and endpoint=="/analytics/events":
            result=svc.analytics_events(body,ip)
        elif method=="GET" and endpoint=="/admin/analytics":
            result=svc.analytics(user,session,query.get("from",[None])[0],query.get("to",[None])[0])
        elif method=="GET" and endpoint=="/admin/actions":
            result=svc.capabilities(user,session)
        elif method=="POST" and endpoint=="/admin/actions":
            result=svc.server_action(body,user,session);code=202
        elif method=="GET" and endpoint=="/admin/audit":
            result=svc.audit_rows(user,session)
        elif method=="GET" and endpoint=="/admin/map/settings":
            svc.require(user,session,"map.manage")
            result={"settings":svc.map_settings()}
        elif method=="PATCH" and endpoint=="/admin/map/settings":
            result=svc.map_settings_update(body,user,session)
        elif method=="POST" and endpoint=="/admin/map/refresh":
            result=svc.map_refresh(body,user,session);code=202
        elif method=="POST" and endpoint=="/adapter/ack":
            result=svc.acknowledge(body)
        elif method=="POST" and endpoint=="/adapter/snapshots":
            result=svc.ingest_snapshot(body)
        else:
            match=re.fullmatch(r"/payments/([A-Za-z0-9_-]{1,100})",endpoint)
            if match and method=="GET":
                result=svc.refresh_payment(match[1],user,session)
            match=re.fullmatch(r"/admin/pages/([A-Za-z0-9_-]{1,100})(?:/(publish|hide|restore))?",endpoint)
            if match and ((method=="PATCH" and not match[2]) or (method=="POST" and match[2])):
                result=svc.change_page(match[1],body,user,session,match[2] or "draft")
            match=re.fullmatch(r"/players/([A-Za-z0-9_-]{1,100})/inventory/refresh",endpoint)
            if match and method=="POST":
                result=svc.refresh_inventory(match[1],body,user,session);code=202
            match=re.fullmatch(r"/admin/tickets/([A-Za-z0-9_-]{1,100})",endpoint)
            if match and method=="PATCH":
                result=svc.update_ticket(match[1],body,user,session)
            match=re.fullmatch(r"/players/([A-Za-z0-9_-]{1,100})",endpoint)
            if match and method=="GET":
                result=svc.player_profile(match[1],user,session)
            match=re.fullmatch(r"/admin/players/([A-Za-z0-9_-]{1,100})/(actions|role)",endpoint)
            if result is None and match and method=="POST":
                result=svc.player_action(match[1],body,user,session) if match[2]=="actions" else svc.change_role(match[1],body,user,session)
                code=202 if match[2]=="actions" else 200
            match=re.fullmatch(r"/admin/sanctions/([A-Za-z0-9_-]{1,100})/revoke",endpoint)
            if result is None and match and method=="POST":
                result=svc.revoke_sanction(match[1],body,user,session);code=202
            match=re.fullmatch(r"/(?:admin/)?catalog/([a-z0-9_-]{1,100})",endpoint)
            if result is None and match and method=="PATCH":
                result=svc.patch_catalog(match[1],body,user,session)
            match=re.fullmatch(r"/admin/icons/([a-z0-9_-]{1,100})\.png",endpoint)
            if result is None and match and method=="GET":
                svc.require(user,session)
                if not ({"catalog.manage","inventory.manage"} & PERMISSIONS[user["role"]]):
                    raise APIError(403,"Permission denied.")
                return self.file(self.site/"server"/"private"/"icons"/(match[1]+".png"),self.site/"server"/"private"/"icons")
        if result is None:
            raise APIError(404,"Unknown API route.")
        return code,result,"application/json; charset=utf-8"

    def file(self,path,root):
        path,root=Path(path),Path(root).resolve()
        if path.is_symlink() or not path.is_file():
            raise APIError(404,"Not found.")
        resolved=path.resolve()
        if resolved!=root and root not in resolved.parents:
            raise APIError(404,"Not found.")
        for ancestor in path.parents:
            if ancestor==root:
                break
            if ancestor.is_symlink():
                raise APIError(404,"Not found.")
        if path.stat().st_size>20*1024*1024:
            raise APIError(404,"Not found.")
        typ=mimetypes.guess_type(path.name)[0] or "application/octet-stream"
        if typ in ("text/html","text/css","application/javascript","text/javascript"):
            typ+="; charset=utf-8"
        return 200,path.read_bytes(),typ

    def static(self,path,user,session):
        if path in PAGES:
            return self.file(self.site/"server-index.html",self.site)
        match=re.fullmatch(r"/map-tiles/([0-9a-f-]{36})/([A-Za-z0-9_./-]+\.(?:png|webp))",path)
        if match:
            publication=self.service.eligible_publication(match[1])
            if not publication:
                raise APIError(404,"Map publication is unavailable.")
            return self.file(Path(publication["directory"])/match[2],Path(publication["directory"]))
        relative=path.lstrip("/")
        item=PurePosixPath(relative)
        if item.parts[:2]==("assets","map"):
            raise APIError(404,"Use a published map revision.")
        if not item.parts or item.parts[0]!="assets" or any(p.startswith(".") for p in item.parts):
            raise APIError(404,"Not found.")
        if item.suffix==".js":
            if item.parts[:2]!=("assets","js") or len(item.parts)!=3 or item.name not in SCRIPTS:
                raise APIError(404,"Not found.")
        elif item.suffix==".css":
            if len(item.parts)>3 or (len(item.parts)==3 and item.parts[1]!="css"):
                raise APIError(404,"Not found.")
        elif item.suffix.lower() not in (".png",".webp",".jpg",".jpeg",".svg",".ico",".woff",".woff2"):
            raise APIError(404,"Not found.")
        if any(p in ("private","sources","docs","data","server") for p in item.parts) or item.stem.startswith("narcotic_") or item.stem in ("streamer_stick","kozyrny_tuz_pozdnyakova"):
            raise APIError(404,"Not found.")
        if item.parts[:2]==("assets","custom") and item.stem not in self.service.public_source_ids:
            raise APIError(404,"Not found.")
        return self.file(self.site/relative,self.site/"assets")

