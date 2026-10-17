"""Versioned FAQ and gallery metadata. Only packaged, approved images are selectable."""
import json
import re
from .common import APIError, integer, reason, text
from .storage import identifier, encoded

class ContentLists:
    def init_content_lists(self):
        source=self.config.site_dir/"data"/"content-v6-public.json"
        seed=json.loads(source.read_text(encoding="utf-8")) if source.exists() else {}
        self.gallery_assets={row["src"] for row in seed.get("gallery",[]) if isinstance(row,dict) and isinstance(row.get("src"),str)}
        with self.store.transaction() as conn:
            for kind in ("faq","gallery"):
                if self.store.setting("content_"+kind,None,conn) is None:
                    self.store.set_setting(conn,"content_"+kind,{"version":1,kind:seed.get(kind,[])})

    def content_list(self,kind,admin=False):
        if kind not in ("faq","gallery"):
            raise APIError(404,"Content collection not found.")
        data=self.store.setting("content_"+kind,{"version":1,kind:[]})
        return {"version":data["version"],kind:data[kind] if admin else [row for row in data[kind] if row.get("published")]}

    def update_content_list(self,kind,body,user,session):
        self.require(user,session,"server.manage",True)
        why=reason(body)
        if kind not in ("faq","gallery") or set(body)-{"version","reason",kind}:
            raise APIError(400,"Unsupported content field.")
        rows=body.get(kind)
        if not isinstance(rows,list) or len(rows)>100:
            raise APIError(400,"Коллекция может содержать до 100 записей.")
        cleaned=[];seen=set()
        for row in rows:
            allowed={"id","question","answer","published"} if kind=="faq" else {"id","src","title","caption","alt","position","published"}
            if not isinstance(row,dict) or set(row)-allowed:
                raise APIError(400,"Unknown content field.")
            item_id=row.get("id") or identifier()
            if not isinstance(item_id,str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,100}",item_id) or item_id in seen:
                raise APIError(400,"Duplicate or invalid content ID.")
            seen.add(item_id)
            if not isinstance(row.get("published"),bool):
                raise APIError(400,"Publication must be boolean.")
            clean={"id":item_id,"published":row["published"]}
            fields=(("question",1,150),("answer",1,1500)) if kind=="faq" else (("title",1,150),("caption",0,500),("alt",1,200))
            for key,minimum,maximum in fields:
                clean[key]=text(row.get(key,""),minimum,maximum,key)
            if kind=="gallery":
                if row.get("src") not in self.gallery_assets:
                    raise APIError(400,"Выбери изображение из библиотеки сайта.","unapproved_asset")
                clean["src"]=row["src"]
                clean["position"]=integer(row.get("position",0),0,999,"position")
            cleaned.append(clean)
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,"server.manage",True)
            current=self.store.setting("content_"+kind,{"version":1,kind:[]},conn)
            if integer(body.get("version"),1,10**9,"version")!=current["version"]:
                raise APIError(409,"Коллекция изменилась. Обнови страницу.","version_conflict")
            result={"version":current["version"]+1,kind:cleaned}
            self.store.set_setting(conn,"content_"+kind,result)
            self.store.audit(conn,actor["id"],"content."+kind,kind,why,{"version":result["version"],"count":len(cleaned)})
        return result

