"""Composable v6 service; no demo identities or client-side authority."""
import json
from pathlib import Path
from .common import APIError, Config
from .storage import Store, encoded
from .security import password_hash, PERMISSIONS
from .accounts import Accounts
from .actions import Actions, BUILTINS, PHYSICAL
from .commerce import Commerce
from .operations import Operations
from .integration import Integration
from .personal import Personal
from .cms import CMS
from .payments import Payments
from .content_lists import ContentLists
from .vk_auth import VKAuth
from .notifications import Notifications

def array_file(path,key):
    value=json.loads(Path(path).read_text(encoding="utf-8-sig"))
    if isinstance(value,list):
        return value
    if isinstance(value,dict):
        for candidate in (key,"items","catalog","materials","actions"):
            if isinstance(value.get(candidate),list):
                return value[candidate]
    raise ValueError("Expected a JSON array in "+str(path))

class Service(Accounts, Actions, Commerce, Operations, Integration, Personal, CMS, Payments, ContentLists, VKAuth, Notifications):
    def __init__(self,config):
        self.config=config
        self.store=Store(config.data_dir,config.site_dir)
        private=config.site_dir/"server"/"private"
        catalog=array_file(private/"catalog.json","catalog")
        public=array_file(config.site_dir/"data"/"catalog-v6-public.json","catalog")
        self.public_source_ids={row["id"] for row in public}
        if any(row["id"].startswith("narcotic_") or row.get("sourceType")=="ADMIN_ONLY" for row in public):
            raise ValueError("The public catalog includes a source-restricted record.")
        if len(self.public_source_ids)!=len(public):
            raise ValueError("Duplicate public catalog identifiers.")
        materials=array_file(private/"materials.json","materials")
        self.materials={row["id"].removeprefix("minecraft:"):row for row in materials}
        raw=json.loads((private/"admin-actions.json").read_text(encoding="utf-8-sig"))
        records=raw if isinstance(raw,list) else raw.get("actions",[])
        self.excluded=[] if isinstance(raw,list) else raw.get("excluded",[])
        self.registry={}
        known_permissions=set().union(*PERMISSIONS.values())
        for record in records:
            if not isinstance(record,dict) or not isinstance(record.get("id"),str):
                continue
            action=record["id"]
            if action.split(".")[-1] in PHYSICAL or record.get("requiresAdminPresence") or record.get("excluded"):
                continue
            permission=record.get("permission",record.get("requiredPermission","server.manage"))
            if permission not in known_permissions:
                permission="server.manage"
            self.registry[action]=dict(record,scope=record.get("scope","player"),permission=permission)
        for action,(permission,params) in BUILTINS.items():
            previous=self.registry.get(action,{})
            self.registry[action]=dict(previous,id=action,scope="player",permission=permission,params=params,
                sourceKind=previous.get("sourceKind","site-extension" if action in ("jail","unjail","god","fly","purchase.limit","sessions.revoke","money.adjust") else "typed-adapter"))
        self.dummy_password=password_hash("Unusable fixture hash "+__import__("secrets").token_urlsafe(24))
        with self.store.transaction() as conn:
            for row in catalog:
                item_id=row["id"]
                visibility=row.get("visibility","hidden")
                if item_id not in self.public_source_ids:
                    visibility="admin" if row.get("sourceType")=="ADMIN_ONLY" or visibility=="admin" else "restricted"
                elif visibility not in ("public","hidden"):
                    visibility="hidden"
                price=row.get("price",0)
                currency=row.get("currency","AR")
                if isinstance(price,bool) or not isinstance(price,int) or price<0 or currency not in ("AR","CM"):
                    raise ValueError("Invalid seed price/currency for "+item_id)
                payload=dict(row)
                payload.pop("reason",None)
                payload.pop("changeReason",None)
                conn.execute("INSERT OR IGNORE INTO catalog(id,payload,price,currency,visibility,enabled,per_player_limit,supply_limit) VALUES(?,?,?,?,?,?,?,?)",
                    (item_id,encoded(payload),price,currency,visibility,int(row.get("enabled",True)),row.get("limit",row.get("perPlayerLimit",0)),row.get("supplyLimit",0)))
        self.init_cms()
        self.init_payments()
        self.init_content_lists()
        self.init_vk_auth()
        self.init_notifications()

