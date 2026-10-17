#!/usr/bin/env python3
"""Interactive, local-only provider setup. Credentials never enter stdout or argv."""
import argparse
import getpass
import json
import os
from pathlib import Path
import re
import sys
import tempfile

SITE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SITE))
from server.common import APIError, Config


def ask(label, current="", secret=False):
    suffix = " [настроено; Enter — сохранить, - — удалить]" if current else " [Enter — пропустить]"
    value = getpass.getpass(label+suffix+": ") if secret else input(label+(" ["+str(current)+"]" if current else "")+": ")
    if value == "-":
        return ""
    return (value if secret else value.strip()) if value else current


def save_private(path, values):
    if path.is_symlink():
        raise ValueError("Конфигурация должна быть обычным файлом.")
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd, temporary = tempfile.mkstemp(prefix=".copimine-auth-", dir=path.parent)
    try:
        os.chmod(temporary, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(values, stream, ensure_ascii=False, indent=2)
            stream.write("\n"); stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main():
    parser = argparse.ArgumentParser(description="Настроить SMTP, SMS.RU и VK ID в закрытой конфигурации CopiMine.")
    parser.add_argument("--config", required=True, help="Закрытый JSON вне папки сайта")
    parser.add_argument("--origin", default="https://copimine.ru")
    args = parser.parse_args()
    if not sys.stdin.isatty():
        raise SystemExit("Запусти мастер в своём терминале: он принимает ключи без отображения и не читает их из аргументов.")
    original = Path(args.config).expanduser()
    if original.is_symlink():
        raise SystemExit("Не используй ссылку вместо закрытого файла конфигурации.")
    destination = original.resolve()
    if destination == SITE or SITE in destination.parents:
        raise SystemExit("Помести конфигурацию вне папки сайта, например /etc/copimine-site/site.json.")
    values = json.loads(destination.read_text(encoding="utf-8")) if destination.exists() else {}
    if not isinstance(values, dict):
        raise SystemExit("Ожидался объект JSON.")
    provider = dict(values.get("notificationEnv", {}))
    print("Enter сохраняет текущее значение, - очищает его. Пароли и ключи не показываются.")
    print("\nПОЧТА — используй SMTP своего почтового сервиса и подтверждённый домен отправителя.")
    if input("Настроить почту? [д/Н]: ").strip().lower() in ("д", "да", "y", "yes"):
        for key, label, default, secret in (
            ("CM_SMTP_HOST", "SMTP-сервер", "", False),
            ("CM_SMTP_PORT", "Порт", "587", False),
            ("CM_SMTP_TLS", "Шифрование: starttls или tls", "starttls", False),
            ("CM_SMTP_USER", "Логин", "", False),
            ("CM_SMTP_PASSWORD", "Пароль SMTP", "", True),
            ("CM_SMTP_FROM", "Адрес отправителя", "", False),
        ):
            provider[key] = ask(label, provider.get(key, default), secret)
        if any(provider.get(k) for k in ("CM_SMTP_HOST", "CM_SMTP_USER", "CM_SMTP_PASSWORD", "CM_SMTP_FROM")):
            if not all(provider.get(k) for k in ("CM_SMTP_HOST", "CM_SMTP_USER", "CM_SMTP_PASSWORD", "CM_SMTP_FROM")):
                raise SystemExit("Для почты нужны SMTP-сервер, логин, пароль и адрес отправителя. Файл не изменён.")
            if not re.fullmatch(r"[A-Za-z0-9.-]+", provider["CM_SMTP_HOST"]) or provider["CM_SMTP_TLS"] not in ("starttls", "tls"):
                raise SystemExit("Проверь SMTP-сервер и шифрование. Файл не изменён.")
            if not provider["CM_SMTP_PORT"].isdigit() or not 1 <= int(provider["CM_SMTP_PORT"]) <= 65535:
                raise SystemExit("Проверь SMTP-порт. Файл не изменён.")
            from server.notifications import contact_value
            try:
                provider["CM_SMTP_FROM"] = contact_value("email", provider["CM_SMTP_FROM"])
            except APIError:
                raise SystemExit("Проверь адрес отправителя: нужен обычный email без отображаемого имени. Файл не изменён.") from None
    print("\nSMS — API-ключ из личного кабинета SMS.RU.")
    if input("Настроить SMS? [д/Н]: ").strip().lower() in ("д", "да", "y", "yes"):
        provider["CM_SMSRU_API_ID"] = ask("API-ключ", provider.get("CM_SMSRU_API_ID", ""), True)
        provider["CM_SMSRU_SENDER"] = ask("Согласованное имя отправителя (можно оставить пустым)", provider.get("CM_SMSRU_SENDER", ""))
    print("\nVK ID — приложение сайта, зарегистрированное в кабинете VK ID.")
    if input("Настроить VK? [д/Н]: ").strip().lower() in ("д", "да", "y", "yes"):
        values["vkClientId"] = ask("ID приложения", values.get("vkClientId", ""))
    mode = ask("Отправка сообщений: disabled — отключена, live — включена", provider.get("CM_NOTIFY_MODE", "disabled"))
    if mode not in ("disabled", "live"):
        raise SystemExit("Режим должен быть disabled или live. Файл не изменён.")
    provider["CM_NOTIFY_MODE"] = mode
    provider.setdefault("CM_NOTIFY_EMAIL_DAILY_LIMIT", "1000")
    provider.setdefault("CM_NOTIFY_SMS_DAILY_LIMIT", "100")
    values["notificationEnv"] = provider
    # Validate provider fields without opening the site's database or making requests.
    Config(SITE, destination.parent/"config-validation", origin=args.origin,
           vk_client_id=values.get("vkClientId", ""), notification_env=provider,
           notification_key=values.get("notificationKey", ""))
    save_private(destination, values)
    print("\nЗакрытая конфигурация сохранена:", destination)
    print("Отправка сообщений:", "включена" if mode == "live" else "отключена")
    print("Разрешённый redirect URI для VK ID:", args.origin.rstrip("/")+"/api/v6/auth/vk/callback")
    print("Перезапусти API и notification-worker с этим --config. Реальная отправка начнётся при запросе кода на сайте.")
    print("Мастер не отправлял письма и SMS и не проверял реквизиты у провайдера.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, EOFError, KeyboardInterrupt):
        raise SystemExit("Настройка не завершена. Проверь введённые данные и доступ к закрытой папке.") from None
