#!/usr/bin/env python3
"""Apply and verify the CopiMine deployment-owned game runtime security policy."""

from __future__ import annotations

import argparse
import json
import os
import re
import socket
import struct
import sys
import tempfile
import time
import zipfile
from pathlib import Path
from typing import Any


class PolicyError(RuntimeError):
    """Raised when a deployment policy cannot be applied safely."""


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".copimine-tmp")
    temporary.write_text(text, encoding="utf-8")
    os.replace(temporary, path)


def load_policy(path: Path) -> dict[str, Any]:
    try:
        policy = json.loads(read_text(path))
    except (OSError, json.JSONDecodeError) as error:
        raise PolicyError(f"Cannot read game hardening policy: {error}") from error
    imageframe = policy.get("imageframe", {})
    imageframe_settings = imageframe.get("Settings", {})
    authme = policy.get("authme", {})
    image_url_policy = imageframe_settings.get("RestrictImageUrl", {})
    allowed_image_hosts = [
        "https://avatars.mds.yandex.net/",
        "https://photos.anysex.com/",
    ]
    if image_url_policy.get("Enabled") is not True:
        raise PolicyError("Managed ImageFrame policy must enable URL filtering")
    if image_url_policy.get("Whitelist") != allowed_image_hosts:
        raise PolicyError("Managed ImageFrame URL whitelist must contain only approved public image hosts")
    if imageframe_settings.get("MaxSize") != 32:
        raise PolicyError("Managed ImageFrame policy must cap map size at 32")
    if imageframe_settings.get("MaxImageFileSize") != 8 * 1024 * 1024:
        raise PolicyError("Managed ImageFrame policy must cap image files at 8 MiB")
    if imageframe_settings.get("MaxProcessingTime") != 15:
        raise PolicyError("Managed ImageFrame policy must cap processing time at 15 seconds")
    if imageframe_settings.get("ParallelProcessingLimit") != 1:
        raise PolicyError("Managed ImageFrame policy must allow only one parallel processor")
    if imageframe_settings.get("PlayerCreationLimit") != {"default": 10, "president": 50, "admin": -1}:
        raise PolicyError("Managed ImageFrame policy must set player=10, president=50 and admin=unlimited")
    if imageframe_settings.get("MapPacketSendingRateLimit") != 20:
        raise PolicyError("Managed ImageFrame policy must set a finite map packet rate limit")
    if imageframe_settings.get("CacheControlMode") != "DYNAMIC":
        raise PolicyError("Managed ImageFrame policy must use dynamic map cache control")
    if imageframe.get("UploadService", {}).get("Enabled") is not False:
        raise PolicyError("Managed ImageFrame policy must disable its embedded upload service")
    if imageframe.get("UploadService", {}).get("WebServer", {}).get("Host") != "127.0.0.1":
        raise PolicyError("Managed ImageFrame upload service must be loopback-bound")
    if authme.get("settings", {}).get("security", {}).get("passwordHash") != "BCRYPT":
        raise PolicyError("Managed AuthMe policy must use BCRYPT")
    if authme.get("settings", {}).get("security", {}).get("minPasswordLength") != 12:
        raise PolicyError("Managed AuthMe policy must require 12-character passwords")
    if authme.get("settings", {}).get("security", {}).get("legacyHashes") != ["SHA256"]:
        raise PolicyError("Managed AuthMe policy must migrate existing SHA256 credentials")
    if authme.get("ExternalBoardOptions", {}).get("bCryptLog2Round") != 12:
        raise PolicyError("Managed AuthMe policy must keep BCRYPT at 12 rounds")
    return policy


def indentation(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def key_matches(line: str, key: str) -> bool:
    stripped = line.lstrip(" ")
    return stripped.startswith(key + ":") and (len(stripped) == len(key) + 1 or stripped[len(key) + 1].isspace())


def block_end(lines: list[str], start: int, indent: int, limit: int) -> int:
    for index in range(start + 1, limit):
        stripped = lines[index].strip()
        if not stripped or stripped.startswith("#"):
            continue
        if indentation(lines[index]) <= indent:
            return index
    return limit


def find_block(lines: list[str], path: list[str]) -> tuple[int, int, int] | None:
    start, limit, parent_indent = 0, len(lines), -1
    for depth, key in enumerate(path):
        candidates: list[tuple[int, int]] = []
        for index in range(start, limit):
            if not key_matches(lines[index], key):
                continue
            indent = indentation(lines[index])
            if depth == 0:
                if indent != 0:
                    continue
            elif indent <= parent_indent:
                continue
            candidates.append((indent, index))
        if not candidates:
            return None
        indent, index = min(candidates)
        end = block_end(lines, index, indent, limit)
        start, limit, parent_indent = index + 1, end, indent
    return index, end, indent


def child_indent(lines: list[str], parent: tuple[int, int, int]) -> int:
    start, end, parent_indent = parent
    children = [
        indentation(lines[index])
        for index in range(start + 1, end)
        if lines[index].strip() and not lines[index].lstrip().startswith("#") and indentation(lines[index]) > parent_indent
    ]
    return min(children) if children else parent_indent + 2


def scalar(value: Any) -> str:
    if value is True:
        return "true"
    if value is False:
        return "false"
    if isinstance(value, (int, float)):
        return str(value)
    return json.dumps(str(value), ensure_ascii=False)


def render_mapping(key: str, value: dict[str, Any], indent: int) -> list[str]:
    prefix = " " * indent
    child_prefix = " " * (indent + 2)
    output = [f"{prefix}{key}:"]
    for child_key, child_value in value.items():
        if isinstance(child_value, dict):
            output.extend(render_mapping(child_key, child_value, indent + 2))
        elif isinstance(child_value, list):
            output.append(f"{child_prefix}{child_key}:")
            output.extend(f"{' ' * (indent + 4)}- {scalar(item)}" for item in child_value)
        else:
            output.append(f"{child_prefix}{child_key}: {scalar(child_value)}")
    return output


def replace_mapping(lines: list[str], path: list[str], value: dict[str, Any]) -> None:
    block = find_block(lines, path)
    if block is None:
        raise PolicyError("Missing YAML section: " + ".".join(path))
    start, end, indent = block
    lines[start:end] = render_mapping(path[-1], value, indent)


def replace_scalar(lines: list[str], parent_path: list[str], key: str, value: Any) -> None:
    parent = find_block(lines, parent_path)
    if parent is None:
        raise PolicyError("Missing YAML section: " + ".".join(parent_path))
    parent_start, parent_end, parent_indent = parent
    matches = [
        (indentation(lines[index]), index)
        for index in range(parent_start + 1, parent_end)
        if key_matches(lines[index], key) and indentation(lines[index]) > parent_indent
    ]
    replacement_indent = child_indent(lines, parent)
    if matches:
        indent, index = min(matches)
        end = block_end(lines, index, indent, parent_end)
        lines[index:end] = [f"{' ' * indent}{key}: {scalar(value)}"]
    else:
        lines.insert(parent_end, f"{' ' * replacement_indent}{key}: {scalar(value)}")


def replace_list(lines: list[str], parent_path: list[str], key: str, values: list[Any]) -> None:
    parent = find_block(lines, parent_path)
    if parent is None:
        raise PolicyError("Missing YAML section: " + ".".join(parent_path))
    parent_start, parent_end, parent_indent = parent
    matches = [
        (indentation(lines[index]), index)
        for index in range(parent_start + 1, parent_end)
        if key_matches(lines[index], key) and indentation(lines[index]) > parent_indent
    ]
    replacement_indent = child_indent(lines, parent)
    content = [f"{' ' * replacement_indent}{key}:"]
    content.extend(f"{' ' * (replacement_indent + 2)}- {scalar(value)}" for value in values)
    if matches:
        indent, index = min(matches)
        end = block_end(lines, index, indent, parent_end)
        content = [f"{' ' * indent}{key}:"]
        content.extend(f"{' ' * (indent + 2)}- {scalar(value)}" for value in values)
        lines[index:end] = content
    else:
        lines[parent_end:parent_end] = content


def plugin_jars(plugins: Path, prefix: str) -> list[Path]:
    prefix_lower = prefix.lower()
    return sorted(
        (candidate for candidate in plugins.glob("*.jar") if candidate.name.lower().startswith(prefix_lower)),
        key=lambda candidate: candidate.name.lower(),
    )


def extract_jar_resource(jar_path: Path, resource_name: str, destination: Path) -> bool:
    try:
        with zipfile.ZipFile(jar_path) as archive:
            try:
                content = archive.read(resource_name)
            except KeyError:
                return False
    except (OSError, zipfile.BadZipFile) as error:
        raise PolicyError(f"Cannot read plugin JAR {jar_path.name}: {error}") from error
    try:
        text = content.decode("utf-8")
    except UnicodeDecodeError as error:
        raise PolicyError(f"Plugin JAR resource {resource_name} is not UTF-8: {jar_path.name}") from error
    write_text(destination, text if text.endswith("\n") else text + "\n")
    return True


def seed_authme_config(path: Path, policy: dict[str, Any]) -> None:
    """Seed the settings AuthMe 5.6.0 materializes from code on its first start.

    Unlike ImageFrame, AuthMe 5.6.0 does not ship config.yml as a JAR resource:
    ConfigMe writes the remaining documented default properties on first boot.
    This bootstrap therefore contains only deployment-owned security properties,
    so a fresh install never starts with the plugin's weak SHA256/minimum-five
    defaults while unrelated AuthMe defaults remain plugin-owned.
    """
    document = [
        "# CopiMine security bootstrap for AuthMe 5.6.0.",
        "# AuthMe/ConfigMe writes its remaining official defaults on first startup.",
    ]
    document.extend(render_mapping("ExternalBoardOptions", policy["ExternalBoardOptions"], 0))
    document.extend(render_mapping("settings", policy["settings"], 0))
    document.extend(render_mapping("Protection", {"geoIpDatabase": {"enabled": False}}, 0))
    write_text(path, "\n".join(document) + "\n")


def seed_imageframe_config(path: Path, imageframe_jar: Path) -> None:
    if not extract_jar_resource(imageframe_jar, "config.yml", path):
        raise PolicyError(
            f"ImageFrame JAR {imageframe_jar.name} does not contain config.yml; refusing to invent plugin defaults"
        )


def sync_imageframe(path: Path, imageframe_jar: Path, policy: dict[str, Any]) -> None:
    settings = policy["Settings"]
    upload_service = policy["UploadService"]
    if not path.exists():
        seed_imageframe_config(path, imageframe_jar)
    lines = read_text(path).splitlines()
    replace_mapping(lines, ["Settings", "RestrictImageUrl"], settings["RestrictImageUrl"])
    replace_mapping(lines, ["Settings", "PlayerCreationLimit"], settings["PlayerCreationLimit"])
    for key in ("MaxSize", "MaxImageFileSize", "MaxProcessingTime", "ParallelProcessingLimit", "MapMarkerLimit", "MapPacketSendingRateLimit", "CacheControlMode"):
        replace_scalar(lines, ["Settings"], key, settings[key])
    if find_block(lines, ["PlayerCreationLimit"]) is not None:
        replace_mapping(lines, ["PlayerCreationLimit"], settings["PlayerCreationLimit"])
    replace_scalar(lines, ["UploadService"], "Enabled", upload_service["Enabled"])
    replace_scalar(lines, ["UploadService", "WebServer"], "Host", upload_service["WebServer"]["Host"])
    replace_scalar(lines, ["UploadService", "WebServer"], "Port", upload_service["WebServer"]["Port"])
    write_text(path, "\n".join(lines) + "\n")


def sync_authme(path: Path, policy: dict[str, Any]) -> None:
    lines = read_text(path).splitlines()
    security = policy["settings"]["security"]
    # GeoIP cannot work without MaxMind credentials and causes an avoidable
    # download/error loop on every boot. Keep the optional protection off until
    # credentials are explicitly configured by the operator.
    replace_scalar(lines, ["Protection", "geoIpDatabase"], "enabled", False)
    replace_scalar(lines, ["settings", "security"], "minPasswordLength", security["minPasswordLength"])
    replace_scalar(lines, ["settings", "security"], "passwordHash", security["passwordHash"])
    replace_list(lines, ["settings", "security"], "legacyHashes", security["legacyHashes"])
    replace_scalar(lines, ["ExternalBoardOptions"], "bCryptLog2Round", policy["ExternalBoardOptions"]["bCryptLog2Round"])
    write_text(path, "\n".join(lines) + "\n")


def sync_runtime(server_dir: Path, policy_path: Path, voice_template: Path) -> None:
    policy = load_policy(policy_path)
    if not voice_template.is_file():
        raise PolicyError(f"Missing voice-chat template: {voice_template}")
    plugins = server_dir / "plugins"
    imageframe_jars = plugin_jars(plugins, "ImageFrame")
    if not imageframe_jars:
        raise PolicyError("ImageFrame JAR is missing; cannot securely seed its managed configuration")
    if len(imageframe_jars) != 1:
        raise PolicyError("Multiple ImageFrame JARs were found; refusing an ambiguous configuration seed")
    authme_jars = plugin_jars(plugins, "AuthMe")
    if len(authme_jars) > 1:
        raise PolicyError("Multiple AuthMe JARs were found; refusing an ambiguous configuration seed")
    authme_config = plugins / "AuthMe" / "config.yml"
    if authme_jars and not authme_config.is_file():
        if not extract_jar_resource(authme_jars[0], "config.yml", authme_config):
            seed_authme_config(authme_config, policy["authme"])
    sync_imageframe(plugins / "ImageFrame" / "config.yml", imageframe_jars[0], policy["imageframe"])
    voice_config = plugins / "voicechat" / "voicechat-server.properties"
    if not voice_config.exists():
        write_text(voice_config, read_text(voice_template))
    if authme_config.is_file():
        sync_authme(authme_config, policy["authme"])


def properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in read_text(path).splitlines():
        if not line or line.lstrip().startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def is_public_bind(value: str, server_ip: str) -> bool:
    bind = value.strip().lower()
    if bind in {"", "*", "0.0.0.0", "::", "[::]"}:
        return True
    if bind in {"127.0.0.1", "::1", "[::1]", "localhost"}:
        return False
    # An explicit non-loopback binding can receive remote UDP traffic even
    # when Minecraft's TCP server-ip is blank, so treat it as public too.
    return True


def validate_voicechat(server_properties: Path, voicechat_config: Path, allow_exception: str, exception_reason: str) -> None:
    server = properties(server_properties)
    voice = properties(voicechat_config)
    offline_mode = server.get("online-mode", "true").strip().lower() == "false"
    public_bind = is_public_bind(voice.get("bind_address", ""), server.get("server-ip", ""))
    if not (offline_mode and public_bind):
        return
    if allow_exception.strip() != "1":
        raise PolicyError(
            "Offline-mode plus public/wildcard voice chat is blocked. Set COPIMINE_ALLOW_INSECURE_OFFLINE_VOICECHAT=1 "
            "and provide COPIMINE_OFFLINE_VOICECHAT_EXCEPTION_REASON only after accepting the voice-chat limitation."
        )
    if not exception_reason.strip():
        raise PolicyError("COPIMINE_OFFLINE_VOICECHAT_EXCEPTION_REASON is required for the offline voice-chat exception")


def receive_exact(sock: socket.socket, size: int) -> bytes:
    parts: list[bytes] = []
    remaining = size
    while remaining:
        chunk = sock.recv(remaining)
        if not chunk:
            raise PolicyError("RCON connection closed unexpectedly")
        parts.append(chunk)
        remaining -= len(chunk)
    return b"".join(parts)


def send_rcon(sock: socket.socket, request_id: int, packet_type: int, command: str) -> None:
    payload = struct.pack("<ii", request_id, packet_type) + command.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(payload)) + payload)


def receive_rcon(sock: socket.socket) -> tuple[int, int, str]:
    (length,) = struct.unpack("<i", receive_exact(sock, 4))
    if length < 10 or length > 1024 * 1024:
        raise PolicyError("Invalid RCON response length")
    payload = receive_exact(sock, length)
    request_id, packet_type = struct.unpack("<ii", payload[:8])
    return request_id, packet_type, payload[8:-2].decode("utf-8", errors="replace")


def apply_luckperms_imageframe(server_properties: Path, password: str, timeout_seconds: int, admin_group: str) -> None:
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,32}", admin_group):
        raise PolicyError("COPIMINE_IMAGEFRAME_ADMIN_GROUP contains unsupported characters")
    try:
        rcon_port = int(properties(server_properties).get("rcon.port", "25575"))
    except ValueError as error:
        raise PolicyError("server.properties contains a non-numeric rcon.port") from error
    if not 1 <= rcon_port <= 65535:
        raise PolicyError("server.properties rcon.port must be between 1 and 65535")
    deadline = time.monotonic() + max(1, timeout_seconds)
    last_error: OSError | PolicyError | None = None
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", rcon_port), timeout=5) as sock:
                sock.settimeout(8)
                send_rcon(sock, 1, 3, password)
                request_id, _packet_type, _body = receive_rcon(sock)
                if request_id == -1:
                    raise PolicyError("RCON authentication failed")
                commands = [
                    "lp group default permission set imageframe.create true",
                    "lp group default permission set imageframe.createlimit.default true",
                    "lp creategroup president",
                    "lp group president permission set imageframe.create true",
                    "lp group president permission set imageframe.createlimit.president true",
                    f"lp creategroup {admin_group}",
                    f"lp group {admin_group} permission set imageframe.create true",
                    f"lp group {admin_group} permission set imageframe.createlimit.admin true",
                    f"lp group {admin_group} permission set imageframe.createlimit.unlimited true",
                ]
                for request_id, command in enumerate(commands, start=10):
                    send_rcon(sock, request_id, 2, command)
                    response_id, _packet_type, _body = receive_rcon(sock)
                    if response_id == -1:
                        raise PolicyError("RCON command was rejected")
                print("IMAGEFRAME_LUCKPERMS_POLICY_APPLIED")
                return
        except (OSError, PolicyError) as error:
            last_error = error
            time.sleep(2)
    raise PolicyError(f"Unable to apply ImageFrame LuckPerms policy through RCON: {last_error}")


def self_test() -> None:
    script_root = Path(__file__).resolve().parent.parent
    policy_path = script_root / "templates" / "game-runtime-hardening.json"
    voice_template = script_root / "templates" / "voicechat-server.properties"
    with tempfile.TemporaryDirectory(prefix="copimine-game-hardening-") as temporary:
        root = Path(temporary)
        server = root / "minecraft" / "server"
        plugins = server / "plugins"
        image_config = plugins / "ImageFrame" / "config.yml"
        auth_config = plugins / "AuthMe" / "config.yml"
        voice_config = plugins / "voicechat" / "voicechat-server.properties"

        def write_plugin_jar(path: Path, resources: dict[str, str]) -> None:
            path.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(path, "w") as archive:
                for resource_name, content in resources.items():
                    archive.writestr(resource_name, content)

        bundled_imageframe_config = (
            "Settings:\n"
            "  RestrictImageUrl:\n"
            "    Enabled: true\n"
            "    Whitelist:\n"
            "      - http://unsafe.example\n"
            "  MaxSize: 100\n"
            "  MaxImageFileSize: 52428800\n"
            "  MaxProcessingTime: 60\n"
            "  ParallelProcessingLimit: 1\n"
            "  PlayerCreationLimit:\n"
            "    default: 10\n"
            "  MapMarkerLimit: 20\n"
            "  MapPacketSendingRateLimit: -1\n"
            "  CacheControlMode: MANUAL_PERSISTENT\n"
            "UploadService:\n"
            "  Enabled: true\n"
            "  WebServer:\n"
            "    Host: 0.0.0.0\n"
            "    Port: 8517\n"
            "JarDefault: preserved\n"
        )
        write_plugin_jar(plugins / "ImageFrame.jar", {"config.yml": bundled_imageframe_config})
        # AuthMe 5.6.0 has no config.yml resource. Its ConfigMe settings holder
        # materializes defaults at first boot, while the bootstrap below owns the
        # security-critical values before that first boot.
        write_plugin_jar(plugins / "AuthMe-5.6.0.jar", {"plugin.yml": "name: AuthMe\n"})
        write_text(server / "server.properties", "online-mode=false\nrcon.port=25575\n")
        write_text(
            image_config,
            bundled_imageframe_config,
        )
        write_text(
            auth_config,
            "ExternalBoardOptions:\n  bCryptLog2Round: 10\nsettings:\n    preserveMe: true\n    security:\n        minPasswordLength: 5\n        passwordHash: SHA256\n        legacyHashes: []\n",
        )
        with auth_config.open("a", encoding="utf-8") as auth_file:
            auth_file.write("Protection:\n    geoIpDatabase:\n        enabled: true\n")
        write_text(voice_config, "bind_address=*\n")
        sync_runtime(server, policy_path, voice_template)
        image_text = read_text(image_config)
        auth_text = read_text(auth_config)
        if (
            "Enabled: true" not in image_text
            or "https://avatars.mds.yandex.net/" not in image_text
            or "https://photos.anysex.com/" not in image_text
            or "MaxImageFileSize: 8388608" not in image_text
            or "MapPacketSendingRateLimit: 20" not in image_text
            or "PlayerCreationLimit:\n    default: 10\n    president: 50\n    admin: -1" not in image_text
        ):
            raise PolicyError("ImageFrame security policy did not replace unsafe values")
        if "UploadService:\n  Enabled: false\n  WebServer:\n    Host: \"127.0.0.1\"" not in image_text:
            raise PolicyError("ImageFrame embedded upload service was not disabled and loopback-bound")
        if "passwordHash: \"BCRYPT\"" not in auth_text or "minPasswordLength: 12" not in auth_text or "preserveMe: true" not in auth_text or "geoIpDatabase:\n        enabled: false" not in auth_text:
            raise PolicyError("AuthMe policy did not preserve unrelated configuration while hardening passwords")
        try:
            validate_voicechat(server / "server.properties", voice_config, "0", "")
        except PolicyError:
            pass
        else:
            raise PolicyError("Offline wildcard voice chat was not blocked")
        validate_voicechat(server / "server.properties", voice_config, "1", "Operator accepted the limitation")

        fresh_server = root / "fresh-install" / "minecraft" / "server"
        fresh_plugins = fresh_server / "plugins"
        write_text(fresh_server / "server.properties", "online-mode=true\nrcon.port=25575\n")
        write_plugin_jar(fresh_plugins / "ImageFrame.jar", {"config.yml": bundled_imageframe_config})
        write_plugin_jar(fresh_plugins / "AuthMe-5.6.0.jar", {"plugin.yml": "name: AuthMe\n"})
        sync_runtime(fresh_server, policy_path, voice_template)
        fresh_image_text = read_text(fresh_plugins / "ImageFrame" / "config.yml")
        fresh_auth_text = read_text(fresh_plugins / "AuthMe" / "config.yml")
        if "JarDefault: preserved" not in fresh_image_text:
            raise PolicyError("Fresh ImageFrame config was not seeded from its bundled JAR resource")
        if (
            "passwordHash: \"BCRYPT\"" not in fresh_auth_text
            or "minPasswordLength: 12" not in fresh_auth_text
            or "legacyHashes:\n      - \"SHA256\"" not in fresh_auth_text
        ):
            raise PolicyError("Fresh AuthMe security bootstrap did not enforce the managed password policy")

        write_text(voice_config, "bind_address=127.0.0.1\n")
        validate_voicechat(server / "server.properties", voice_config, "0", "")
    print("GAME_RUNTIME_HARDENING_SELFTEST_OK")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    subparsers = parser.add_subparsers(dest="command")
    sync = subparsers.add_parser("sync")
    sync.add_argument("--server-dir", required=True, type=Path)
    sync.add_argument("--policy", required=True, type=Path)
    sync.add_argument("--voicechat-template", required=True, type=Path)
    voice = subparsers.add_parser("check-voicechat")
    voice.add_argument("--server-properties", required=True, type=Path)
    voice.add_argument("--voicechat-config", required=True, type=Path)
    voice.add_argument("--allow-insecure-offline-voicechat", default="0")
    voice.add_argument("--exception-reason", default="")
    luckperms = subparsers.add_parser("apply-luckperms-imageframe")
    luckperms.add_argument("--server-properties", required=True, type=Path)
    luckperms.add_argument("--password-env", default="COPIMINE_RCON_PASSWORD")
    luckperms.add_argument("--timeout-seconds", type=int, default=90)
    luckperms.add_argument("--admin-group", default="admin")
    return parser


def main(argv: list[str]) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.self_test:
            self_test()
            return 0
        if args.command == "sync":
            sync_runtime(args.server_dir, args.policy, args.voicechat_template)
            print("GAME_RUNTIME_HARDENING_SYNCED")
            return 0
        if args.command == "check-voicechat":
            validate_voicechat(
                args.server_properties,
                args.voicechat_config,
                args.allow_insecure_offline_voicechat,
                args.exception_reason,
            )
            print("VOICECHAT_SECURITY_GATE_PASSED")
            return 0
        if args.command == "apply-luckperms-imageframe":
            password = os.environ.get(args.password_env, "")
            if not password or password == "CHANGE_ME":
                raise PolicyError(f"Missing RCON password environment variable: {args.password_env}")
            apply_luckperms_imageframe(args.server_properties, password, args.timeout_seconds, args.admin_group)
            return 0
        parser.error("Specify a command or --self-test")
    except PolicyError as error:
        print(f"GAME_RUNTIME_HARDENING_ERROR: {error}", file=sys.stderr)
        return 1
    return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
