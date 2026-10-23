from contextlib import asynccontextmanager
from pathlib import Path
import hmac
import json
import logging
import secrets
import time
from urllib.parse import urlsplit

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from .auth import create_user, router as auth_router
from .config import Settings
from .db import Database
from .security import ANON_COOKIE, COOKIE, anonymous_csrf, current_user, digest, fail, signed_anonymous, valid_anonymous
from .domain import build_state, router as domain_router
from .game import GameAdapter
from .mail import router as mail_router
from .mfa import router as mfa_router
from .maps import router as maps_router
from .vk import router as vk_router

LOGGER = logging.getLogger("copimine.site")


def create_app(settings=None):
    settings = settings or Settings.from_env()
    database = Database(settings.database_url)

    @asynccontextmanager
    async def lifespan(app):
        database.initialize()
        if settings.bootstrap_owner and settings.bootstrap_password:
            with database.transaction() as db:
                exists = db.execute("SELECT id FROM cm_site_users WHERE role='owner'").fetchone()
            if not exists:
                create_user(database, settings.bootstrap_owner, settings.bootstrap_password, role="owner")
                database.audit({"id": "system", "name": "system"}, "owner.bootstrap", settings.bootstrap_owner)
        yield

    app = FastAPI(title="CopiMine", version="8.0.0", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.settings, app.state.database = settings, database
    app.state.game = GameAdapter(settings, database)

    @app.exception_handler(HTTPException)
    async def http_error(request, error):
        detail = error.detail if isinstance(error.detail, dict) else {"code": "request_failed", "message": str(error.detail)}
        return JSONResponse({"error": detail, "message": detail.get("message", "Ошибка запроса")}, status_code=error.status_code)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, error):
        return JSONResponse({"error": {"code": "validation", "message": "Проверьте заполнение полей формы."}}, status_code=422)

    @app.exception_handler(Exception)
    async def unexpected_error(request, error):
        incident = secrets.token_hex(6)
        LOGGER.error("Request failed incident=%s path=%s type=%s", incident, request.url.path, type(error).__name__)
        return JSONResponse({"error": {"code": "internal_error", "message": "Не удалось выполнить запрос. Повторите позже.", "incident": incident}}, status_code=500)

    @app.middleware("http")
    async def request_context(request, call_next):
        request.state.user = request.state.session = None
        request.state.csrf = ""
        anonymous = request.cookies.get(ANON_COOKIE, "")
        fresh_anon = not valid_anonymous(anonymous, settings.secret_key)
        if fresh_anon:
            anonymous = signed_anonymous(settings.secret_key)
        request.state.anonymous = anonymous
        request.state.csrf = anonymous_csrf(anonymous, settings.secret_key)
        if request.url.path.startswith(("/api/", "/maps/")):
            token = request.cookies.get(COOKIE)
            if token:
                with database.transaction() as db:
                    row = db.execute("SELECT * FROM cm_site_sessions WHERE token_hash=%s AND revoked_s=0 AND expires_s>%s", (digest(token), int(time.time()))).fetchone()
                    if row:
                        user = db.execute("SELECT * FROM cm_site_users WHERE id=%s AND enabled=1", (row["user_id"],)).fetchone()
                        if user and (not user["blocked_until_s"] or user["blocked_until_s"] <= time.time()):
                            request.state.session, request.state.user = dict(row), dict(user)
                            request.state.csrf = row["csrf"]
            if request.method not in {"GET", "HEAD", "OPTIONS"}:
                origin = request.headers.get("origin", "").rstrip("/")
                if origin != settings.public_url.rstrip("/"):
                    return JSONResponse({"error": {"code": "origin", "message": "Запрос отправлен с другого адреса сайта."}}, status_code=403)
                csrf = request.headers.get("x-csrf-token", request.headers.get("x-review-csrf", ""))
                if not csrf or not hmac.compare_digest(csrf, request.state.csrf):
                    return JSONResponse({"error": {"code": "csrf", "message": "Сеанс изменился. Обновите страницу и повторите действие."}}, status_code=403)
                chunks, total = [], 0
                async for chunk in request.stream():
                    total += len(chunk)
                    if total > 128 * 1024:
                        return JSONResponse({"error": {"code": "request_too_large", "message": "Запрос слишком большой."}}, status_code=413)
                    chunks.append(chunk)
                request._body = b"".join(chunks)
                try:
                    if not isinstance(json.loads(request._body or b"{}"), dict):
                        raise ValueError("Object required")
                except (ValueError, UnicodeError):
                    return JSONResponse({"error": {"code": "invalid_json", "message": "Ожидался JSON-объект с параметрами формы."}}, status_code=422)
        response = await call_next(request)
        if fresh_anon and request.url.path.startswith("/api/"):
            response.set_cookie(ANON_COOKIE, anonymous, httponly=True, secure=settings.secure_cookies, samesite="lax", max_age=86400, path="/")
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
        if "X-Frame-Options" not in response.headers:
            response.headers["X-Frame-Options"] = "DENY"
        response.headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()"
        if request.url.path.startswith("/api/"):
            response.headers["Cache-Control"] = "no-store"
        if settings.secure_cookies:
            response.headers["Strict-Transport-Security"] = "max-age=31536000"
        return response

    app.include_router(auth_router)
    app.include_router(domain_router)
    app.include_router(mail_router)
    app.include_router(mfa_router)
    app.include_router(maps_router)
    app.include_router(vk_router)

    @app.get("/api/health")
    def health():
        with database.transaction() as db:
            db.execute("SELECT 1").fetchone()
        return {"status": "ok", "version": "8.0.0", "storage": "sqlite-test" if database.sqlite else "postgresql", "production": True}

    @app.get("/api/v6/providers")
    def providers():
        return {"mail": {"configured": bool(settings.smtp_host), "ready": bool(settings.smtp_host and settings.mail_domain_ready)}, "vk": {"enabled": bool(settings.vk_client_id), "callbackUrl": settings.public_url + "/api/v6/auth/vk/callback"}, "payments": {"enabled": False}}

    @app.get("/api/v6/state")
    def state(request: Request):
        return build_state(request)

    @app.get("/api/v7/catalog")
    def private_catalog(request: Request):
        current_user(request, "catalog.manage")
        return {"items": app.state.game.catalog(private=True)}

    @app.get("/")
    def index():
        return FileResponse(settings.frontend / "index.html", headers={"Cache-Control": "no-cache"})

    app.mount("/assets", StaticFiles(directory=settings.frontend / "assets"), name="assets")
    app.mount("/data", StaticFiles(directory=settings.frontend / "data"), name="public-data")

    @app.get("/{filename}.html")
    def html_route(filename: str):
        if filename not in {"index", "account", "admin", "auth", "cart", "donate", "download", "launcher-link", "map", "register", "rules", "settings", "shop"}:
            fail(404, "not_found", "Страница не найдена.")
        return FileResponse(settings.frontend / (filename + ".html"))

    return app
