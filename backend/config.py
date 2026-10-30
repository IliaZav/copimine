from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlsplit
import os

ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Settings:
    database_url: str
    secret_key: str
    public_url: str = "https://copimine.ru"
    secure_cookies: bool = True
    bootstrap_owner: str = ""
    bootstrap_password: str = ""
    frontend: Path = field(default_factory=lambda: ROOT / "frontend")
    private: Path = field(default_factory=lambda: ROOT / "private")
    game_root: Path = Path("/opt/copimine-game")
    smtp_host: str = ""
    smtp_port: int = 25
    smtp_from: str = "CopiMine <noreply@copimine.ru>"
    smtp_username: str = ""
    smtp_password: str = ""
    smtp_starttls: bool = False
    mail_domain_ready: bool = False
    vk_client_id: str = ""
    vk_client_secret: str = ""
    session_hours: int = 12
    map_root: Path = Path("/var/lib/copimine-site/maps")
    map_minimum_free_bytes: int = 5 * 1024**3

    @classmethod
    def from_env(cls):
        truth = lambda key, default="0": os.getenv(key, default).lower() in {"1", "true", "yes"}
        database_url = os.environ.get("DATABASE_URL", "")
        secret = os.environ.get("SECRET_KEY", "")
        if not database_url.startswith(("postgresql://", "postgres://")):
            raise RuntimeError("Production DATABASE_URL must select PostgreSQL")
        if (len(secret) < 40 or len(set(secret)) < 8 or secret != secret.strip()
                or any(marker in secret.lower() for marker in ("replace_", "changeme", "change_me", "placeholder"))):
            raise RuntimeError("Set a persistent random SECRET_KEY, not the deployment template")
        public_url = os.getenv("SITE_PUBLIC_URL", "https://copimine.ru").rstrip("/")
        try:
            origin = urlsplit(public_url)
            port = origin.port  # Reject malformed ports before constructing callbacks.
            valid_origin = (origin.scheme == "https" and bool(origin.hostname)
                            and origin.username is None and origin.password is None
                            and not origin.path and not origin.query and not origin.fragment
                            and not any(character.isspace() for character in public_url)
                            and (port is None or 1 <= port <= 65535))
        except ValueError:
            valid_origin = False
        if not valid_origin:
            raise RuntimeError("SITE_PUBLIC_URL must be an HTTPS origin without credentials, path, query or fragment")
        return cls(
            database_url=database_url, secret_key=secret,
            public_url=public_url,
            secure_cookies=True,
            bootstrap_owner=os.getenv("SITE_BOOTSTRAP_OWNER", ""),
            bootstrap_password=os.getenv("SITE_BOOTSTRAP_PASSWORD", ""),
            game_root=Path(os.getenv("COPIMINE_GAME_ROOT", "/opt/copimine-game")),
            map_root=Path(os.getenv("MAP_PUBLICATION_ROOT", "/var/lib/copimine-site/maps")),
            smtp_host=os.getenv("SMTP_HOST", ""), smtp_port=int(os.getenv("SMTP_PORT", "25")),
            smtp_from=os.getenv("SMTP_FROM", "CopiMine <noreply@copimine.ru>"),
            smtp_username=os.getenv("SMTP_USERNAME", ""), smtp_password=os.getenv("SMTP_PASSWORD", ""),
            smtp_starttls=truth("SMTP_STARTTLS"), mail_domain_ready=truth("MAIL_DOMAIN_READY"),
            vk_client_id=os.getenv("VK_CLIENT_ID", ""), vk_client_secret=os.getenv("VK_CLIENT_SECRET", ""),
        )
