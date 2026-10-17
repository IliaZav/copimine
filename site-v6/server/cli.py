"""Operator CLI. No default credentials, implicit owners, or automatic identity links."""
import argparse
import getpass
import json
import os
from pathlib import Path
import sys
import time
from urllib.parse import quote
from .common import Config
from .core import Service
from .http import Application
from .security import new_totp_secret, verify_totp
from .workers import worker_once, run_map_jobs, publish_map

def parser():
    value=argparse.ArgumentParser(description="CopiMine v6 server")
    value.add_argument("--site-dir",default=str(Path(__file__).resolve().parents[1]))
    value.add_argument("--data-dir",help="Private state directory outside the public site")
    value.add_argument("--origin",default="https://copimine.ru")
    value.add_argument("--insecure-local",action="store_true",help="Allow explicitly configured loopback HTTP")
    value.add_argument("--trust-loopback-proxy",action="store_true")
    value.add_argument("--config",help="Private operator JSON configuration (not served by HTTP)")
    commands=value.add_subparsers(dest="command",required=True)
    serve=commands.add_parser("serve")
    serve.add_argument("--host",default="127.0.0.1")
    serve.add_argument("--port",type=int,default=8787)
    serve.add_argument("--waitress",action="store_true",help="Use optional production WSGI dependency")
    commands.add_parser("bootstrap-owner")
    worker=commands.add_parser("worker",help="Run action dispatch and sanction timers (no terrain rendering)")
    worker.add_argument("--once",action="store_true")
    worker.add_argument("--interval",type=int,default=10)
    map_worker=commands.add_parser("map-worker",help="Run the terrain scheduler separately from action dispatch")
    map_worker.add_argument("--once",action="store_true")
    map_worker.add_argument("--interval",type=int,default=10)
    payments=commands.add_parser("payment-worker",help="Reconcile provider statuses separately from game commands")
    payments.add_argument("--once",action="store_true")
    payments.add_argument("--interval",type=int,default=60)
    notifications=commands.add_parser("notification-worker",help="Send queued contact codes and transactional messages")
    notifications.add_argument("--once",action="store_true")
    notifications.add_argument("--interval",type=int,default=5)
    link=commands.add_parser("link-player")
    link.add_argument("account")
    link.add_argument("game_uuid")
    link.add_argument("game_name")
    publish=commands.add_parser("publish-map")
    publish.add_argument("directory")
    publish.add_argument("--captured-at",help="Only when the operator knows the original capture timezone")
    commands.add_parser("verify-audit")
    commands.add_parser("check-integrations",help="Print provider configuration and queue counts without secrets")
    return value

def configuration(args):
    values={}
    if args.config:
        config_path=Path(args.config).resolve()
        site=Path(args.site_dir).resolve()
        if config_path==site or site in config_path.parents:
            raise ValueError("Keep operator configuration outside the public site.")
        values=json.loads(config_path.read_text(encoding="utf-8"))
        if not isinstance(values,dict) or set(values)-{"adapterUrl","adapterSecret","adapterCapabilities","mapSource","worlds","paymentShopId","paymentSecret","paymentTest","paymentReceipt","vkClientId","notificationEnv","notificationKey"}:
            raise ValueError("Unknown configuration key.")
    site=Path(args.site_dir).resolve()
    notification_names=("CM_NOTIFY_MODE","CM_SMTP_HOST","CM_SMTP_PORT","CM_SMTP_TLS","CM_SMTP_USER","CM_SMTP_PASSWORD","CM_SMTP_FROM","CM_SMSRU_API_ID","CM_SMSRU_SENDER","CM_NOTIFY_EMAIL_DAILY_LIMIT","CM_NOTIFY_SMS_DAILY_LIMIT")
    notification_env={key:os.environ[key] for key in notification_names if key in os.environ}
    supplied_notifications=values.get("notificationEnv",{})
    if not isinstance(supplied_notifications,dict):
        raise ValueError("notificationEnv must be an object of server-only provider settings.")
    notification_env.update(supplied_notifications)
    return Config(site_dir=site,data_dir=Path(args.data_dir).resolve() if args.data_dir else site.parent/"copimine-v6-state",
        origin=args.origin,insecure_local=args.insecure_local,trust_loopback_proxy=args.trust_loopback_proxy,
        adapter_url=values.get("adapterUrl",os.environ.get("CM_V6_ADAPTER_URL","")),
        adapter_secret=values.get("adapterSecret",os.environ.get("CM_V6_ADAPTER_SECRET","")),
        adapter_capabilities=set(values.get("adapterCapabilities",[])),
        vk_client_id=values.get("vkClientId",os.environ.get("CM_V6_VK_CLIENT_ID","")),
        notification_env=notification_env,
        notification_key=values.get("notificationKey",os.environ.get("CM_V6_NOTIFICATION_KEY","")),
        payment_shop_id=values.get("paymentShopId",os.environ.get("CM_V6_PAYMENT_SHOP_ID","")),
        payment_secret=values.get("paymentSecret",os.environ.get("CM_V6_PAYMENT_SECRET","")),
        payment_test=values.get("paymentTest",False),payment_receipt=values.get("paymentReceipt",{}),
        map_source=values.get("mapSource",os.environ.get("CM_V6_MAP_SOURCE","")),
        worlds=values.get("worlds",{"overworld":"world","nether":"world_nether","end":"world_the_end"}))

def main(argv=None):
    args=parser().parse_args(argv)
    service=Service(configuration(args))
    if args.command=="serve":
        application=Application(service)
        if args.waitress:
            try:
                from waitress import serve
            except ImportError:
                raise SystemExit("Install optional requirements-server.txt to use Waitress.")
            serve(application,host=args.host,port=args.port,threads=8,max_request_body_size=1024*1024,
                  clear_untrusted_proxy_headers=not args.trust_loopback_proxy)
        else:
            from wsgiref.simple_server import make_server
            if args.host not in ("127.0.0.1","localhost","::1"):
                raise SystemExit("The development server binds only to loopback. Use Waitress behind TLS for production.")
            print("Development HTTP server:",args.host,args.port,"(serve via TLS proxy unless --insecure-local)")
            with make_server(args.host,args.port,application) as httpd:
                httpd.serve_forever()
    elif args.command=="bootstrap-owner":
        if not sys.stdin.isatty():
            raise SystemExit("Owner setup must be run interactively; credentials cannot be provided as arguments.")
        name=input("New owner account name: ").strip()
        email=input("Owner email: ").strip()
        password=getpass.getpass("New password (12+ characters): ")
        if password!=getpass.getpass("Repeat password: "):
            raise SystemExit("Passwords do not match.")
        secret=new_totp_secret()
        print("Add this account to your authenticator and store the enrollment secret securely:")
        print("otpauth://totp/"+quote("CopiMine:"+name,safe="")+"?secret="+secret+"&issuer=CopiMine&digits=6&period=30")
        if verify_totp(secret,getpass.getpass("Current authenticator code: ")) is None:
            raise SystemExit("Authenticator confirmation failed. No account was created.")
        uid=service.bootstrap_user(name,email,password,"owner",secret)
        print("Owner created:",uid,". No game account is linked automatically.")
    elif args.command=="link-player":
        print(json.dumps(service.link_player(args.account,args.game_uuid,args.game_name),ensure_ascii=False))
    elif args.command in ("worker","map-worker","payment-worker","notification-worker"):
        if not 1<=args.interval<=60:
            raise SystemExit("Worker interval must be from 1 to 60 seconds.")
        while True:
            if args.command=="notification-worker":
                from .notifications import process_notification_jobs
                result={"notifications":process_notification_jobs(service)}
            else:
                result=worker_once(service) if args.command=="worker" else {"payments":service.reconcile_payments()} if args.command=="payment-worker" else {"map":run_map_jobs(service)}
            print(json.dumps(result,ensure_ascii=False),flush=True)
            if args.once:
                break
            time.sleep(args.interval)
    elif args.command=="publish-map":
        result=publish_map(service,args.directory,captured_at=args.captured_at)
        print(json.dumps({"published":bool(result),"publicationId":result.get("publicationId") if result else None},ensure_ascii=False))
    elif args.command=="verify-audit":
        import hashlib
        from .storage import encoded
        previous="0"*64
        count=0
        with service.store.connect() as conn:
            for row in conn.execute("SELECT * FROM audit ORDER BY sequence"):
                event={"at":row["at"],"actor":row["actor_id"],"kind":row["kind"],"target":row["target_id"],"reason":row["reason"],"payload":json.loads(row["payload"])}
                expected=hashlib.sha256((previous+encoded(event)).encode()).hexdigest()
                if row["previous_hash"]!=previous or row["entry_hash"]!=expected:
                    raise SystemExit("Audit chain validation failed at entry "+str(row["sequence"]))
                previous=expected;count+=1
        print(json.dumps({"entries":count,"valid":True,"head":previous,"note":"Local hash chain; keep a separately protected external checkpoint to detect full rewrites."}))
    elif args.command=="check-integrations":
        with service.store.connect() as conn:
            delivery_counts={row[0]:row[1] for row in conn.execute("SELECT status,count(*) FROM notification_outbox GROUP BY status")}
        print(json.dumps({"origin":service.config.origin,
            "vk":{"configured":service.vk_enabled(),"redirectUri":service.config.origin+"/api/v6/auth/vk/callback"},
            "contacts":service.notification_providers(),"notificationQueue":delivery_counts,
            "payments":{"configured":bool(service.config.payment_shop_id and service.config.payment_secret),"test":service.config.payment_test},
            "gameAdapter":{"configured":bool(service.config.adapter_url and service.config.adapter_secret)},
            "liveMapSourceConfigured":bool(service.config.map_source),
            "note":"Configuration presence only; no external delivery, payment or game action was performed."},ensure_ascii=False,indent=2))
    return 0

