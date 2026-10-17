"""Versioned public settings and plain-text CMS with private drafts."""
import json
import re
import sqlite3
from urllib.parse import urlsplit
from .common import APIError, integer, reason, text
from .security import PERMISSIONS
from .storage import encoded, identifier, now_ms

CONFIG_TEXT={"headline":80,"description":220,"siteTitle":100,"serverIp":100,"gameVersion":30,"discordGuild":24}
CONFIG_URLS={"discordInvite","modUrl","launcherUrl"}
DEFAULT_CONFIG={"headline":"Строй. Торгуй.\nИграй с друзьями.","description":"Minecraft-сервер с экономикой, выборами и событиями.",
    "siteTitle":"CopiMine","serverIp":"copimine.ru","gameVersion":"26.3","discordGuild":"1499360677725343744","discordInvite":"",
    "modUrl":"","launcherUrl":"","minMemory":4,"cmRate":1,"topupPacks":[50,100,250,500,1000],"version":1}
PAGE_FIELDS={"title","description","body","nav"}

def plain(value,minimum,maximum,name):
    value=text(value,minimum,maximum,name)
    if re.search(r"</?[A-Za-z][^>]*>",value):
        raise APIError(400,"Страницы поддерживают текст и Markdown. HTML-теги запрещены.","html_not_allowed")
    return value

def safe_url(value,key):
    if value=="":
        return ""
    value=text(value,1,2048,key)
    try:
        url=urlsplit(value)
        if url.scheme!="https" or not url.hostname or url.username or url.password or any(c.isspace() for c in value):
            raise ValueError()
        if key=="discordInvite" and url.hostname.lower() not in ("discord.gg","discord.com","www.discord.com"):
            raise ValueError()
        if url.port is not None and not 1<=url.port<=65535:
            raise ValueError()
    except ValueError:
        raise APIError(400,"Нужна корректная HTTPS-ссылка без логина и пароля.","invalid_url")
    return value

class CMS:
    def init_cms(self):
        source=self.config.site_dir/"data"/"content-v6-public.json"
        seed=json.loads(source.read_text(encoding="utf-8")) if source.is_file() else {}
        with self.store.transaction() as conn:
            conn.execute("""CREATE TABLE IF NOT EXISTS pages(id TEXT PRIMARY KEY,slug TEXT UNIQUE NOT NULL,
                revision INTEGER NOT NULL,draft TEXT NOT NULL,published TEXT,published_revision INTEGER,
                published_at INTEGER,history TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)""")
            if self.store.setting("site_config",None,conn) is None:
                settings=dict(DEFAULT_CONFIG)
                for key,value in seed.get("config",{}).items():
                    if key in CONFIG_TEXT or key in CONFIG_URLS or key=="minMemory":
                        settings[key]=value
                self.store.set_setting(conn,"site_config",settings)
            for page in seed.get("pages",[]):
                published=page.get("published")
                if not isinstance(published,dict) or not re.fullmatch(r"[a-z][a-z0-9-]{2,39}",page.get("slug","")):
                    continue
                content={key:published.get(key,False if key=="nav" else "") for key in PAGE_FIELDS}
                conn.execute("INSERT OR IGNORE INTO pages VALUES(?,?,?,?,?,?,?,?,?,?)",
                    (page.get("id") or identifier(),page["slug"],1,encoded(content),encoded(content),1,None,"[]",now_ms(),now_ms()))

    def site_config(self):
        value={**DEFAULT_CONFIG,**self.store.setting("site_config",{})}
        # Defaults/approved fields only; operator credentials never use this namespace.
        return {k:v for k,v in value.items() if k in DEFAULT_CONFIG}

    def update_config(self,body,user,session):
        self.require(user,session,"server.manage",True)
        why=reason(body)
        if set(body)-set(CONFIG_TEXT)-CONFIG_URLS-{"minMemory","version","reason"}:
            raise APIError(400,"Unsupported public configuration field.")
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,"server.manage",True)
            current={**DEFAULT_CONFIG,**self.store.setting("site_config",{},conn)}
            expected=integer(body.get("version"),1,10**9,"version")
            if expected!=current["version"]:
                raise APIError(409,"Site settings changed. Reload before saving.","version_conflict")
            changes={}
            for key,maximum in CONFIG_TEXT.items():
                if key in body:
                    changes[key]=plain(body[key],0 if key=="discordGuild" else 1,maximum,key)
            if "serverIp" in changes and not re.fullmatch(r"[A-Za-z0-9.:-]{3,100}",changes["serverIp"]):
                raise APIError(400,"Server address must be a hostname or IP without a URL scheme.")
            if changes.get("discordGuild") and not re.fullmatch(r"[0-9]{15,24}",changes["discordGuild"]):
                raise APIError(400,"Invalid Discord server identifier.")
            for key in CONFIG_URLS:
                if key in body:
                    changes[key]=safe_url(body[key],key)
            if "minMemory" in body:
                changes["minMemory"]=integer(body["minMemory"],1,128,"minMemory")
            if not changes:
                raise APIError(400,"No public setting was changed.")
            current.update(changes)
            current["version"]+=1
            self.store.set_setting(conn,"site_config",current)
            self.store.audit(conn,actor["id"],"site.config","site",why,{"version":current["version"],"changes":changes})
        return {"config":current}

    @staticmethod
    def page_row(row,admin=False):
        published=json.loads(row["published"]) if row["published"] else None
        value={"id":row["id"],"slug":row["slug"],"revision":row["revision"] if admin else row["published_revision"],
            "draft":json.loads(row["draft"]) if admin else published,
            "published":published,"publishedRevision":row["published_revision"],"publishedAt":row["published_at"],
            "status":"published" if published else "draft","history":json.loads(row["history"]) if admin else []}
        return value

    def pages(self,admin=False):
        with self.store.connect() as conn:
            return [self.page_row(row,admin) for row in conn.execute("SELECT * FROM pages"+("" if admin else " WHERE published IS NOT NULL")+" ORDER BY slug")]

    def create_page(self,body,user,session):
        self.require(user,session,"server.manage",True)
        why=reason(body)
        if set(body)-{"slug","title","reason"}:
            raise APIError(400,"Unsupported page field.")
        slug=text(body.get("slug"),3,40,"slug")
        if not re.fullmatch(r"[a-z][a-z0-9-]{2,39}",slug) or slug in {"admin","account","api","server","assets","data","index","home","shop","donate","login","logout","map"}:
            raise APIError(400,"Invalid or reserved page address.")
        title=plain(body.get("title"),1,100,"title")
        content={"title":title,"description":"","body":"","nav":False}
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,"server.manage",True)
            if conn.execute("SELECT count(*) FROM pages").fetchone()[0]>=200:
                raise APIError(409,"Page limit reached.")
            page=identifier()
            try:
                conn.execute("INSERT INTO pages VALUES(?,?,?,?,?,?,?,?,?,?)",(page,slug,1,encoded(content),None,None,None,"[]",now_ms(),now_ms()))
            except sqlite3.IntegrityError:
                raise APIError(409,"This page address is already used.","slug_unavailable")
            self.store.audit(conn,actor["id"],"page.create",page,why,{"slug":slug})
            return {"page":self.page_row(conn.execute("SELECT * FROM pages WHERE id=?",(page,)).fetchone(),True)}

    def change_page(self,page_id,body,user,session,operation="draft"):
        self.require(user,session,"server.manage",True)
        why=reason(body)
        allowed={"revision","reason"} | (PAGE_FIELDS if operation=="draft" else {"historyRevision"} if operation=="restore" else set())
        if operation not in ("draft","publish","hide","restore") or set(body)-allowed:
            raise APIError(400,"Unsupported page operation or field.")
        with self.store.transaction() as conn:
            actor=self.actor_current(conn,user,session,"server.manage",True)
            row=conn.execute("SELECT * FROM pages WHERE id=?",(page_id,)).fetchone()
            if not row:
                raise APIError(404,"Page not found.")
            expected=integer(body.get("revision"),1,10**9,"revision")
            if row["revision"]!=expected:
                raise APIError(409,"The page changed. Reload before saving.","version_conflict")
            draft=json.loads(row["draft"])
            published=json.loads(row["published"]) if row["published"] else None
            published_revision,published_at=row["published_revision"],row["published_at"]
            history=json.loads(row["history"])
            revision,at=row["revision"]+1,now_ms()
            if operation=="draft":
                for key,maximum in (("title",100),("description",200),("body",100000)):
                    if key in body:
                        draft[key]=plain(body[key],1 if key=="title" else 0,maximum,key)
                if "nav" in body:
                    if not isinstance(body["nav"],bool):
                        raise APIError(400,"Page navigation must be boolean.")
                    draft["nav"]=body["nav"]
            elif operation=="restore":
                previous=next((x for x in history if x["revision"]==body.get("historyRevision")),None)
                if previous is None:
                    raise APIError(404,"Publication revision not found.")
                draft={key:previous.get(key,False if key=="nav" else "") for key in PAGE_FIELDS}
            else:
                if operation=="publish" and not draft.get("body","").strip():
                    raise APIError(400,"A page needs text before publication.")
                if published:
                    history.insert(0,{**published,"revision":published_revision,"at":published_at})
                    history=history[:50]
                published=dict(draft) if operation=="publish" else None
                published_revision=revision if published else None
                published_at=at if published else None
            conn.execute("UPDATE pages SET revision=?,draft=?,published=?,published_revision=?,published_at=?,history=?,updated_at=? WHERE id=?",
                (revision,encoded(draft),encoded(published) if published else None,published_revision,published_at,encoded(history),at,page_id))
            self.store.audit(conn,actor["id"],"page."+operation,page_id,why,{"slug":row["slug"],"revision":revision})
            return {"page":self.page_row(conn.execute("SELECT * FROM pages WHERE id=?",(page_id,)).fetchone(),True)}

