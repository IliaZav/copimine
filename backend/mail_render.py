"""Письма CopiMine: простой текст на графитовом фоне, без карточек и рамок."""
import html

# Палитра сайта: графит #171c1f, основной текст #e7edf1, вторичный #9aa7b0, акцент #2de39b.
INK = "#e7edf1"
MUTED = "#9aa7b0"
ACCENT = "#2de39b"
PAGE = "#171c1f"
SITE = "https://copimine.ru"

FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif"

PURPOSE_COPY = {
    "register": ("Подтверждение регистрации", "Введите код, чтобы завершить регистрацию на copimine.ru."),
    "login": ("Код для входа", "Введите код, чтобы войти в аккаунт CopiMine."),
    "reset": ("Сброс пароля", "Введите код, чтобы задать новый пароль."),
    "link_email": ("Подтверждение почты", "Введите код, чтобы привязать этот адрес к аккаунту."),
}

PREHEADER = "Одноразовый код CopiMine действует 10 минут."

# Официальный wordmark сайта (frontend/assets/img/logo.webp, 520x104, пропорция 5:1).
# В письме — PNG 260x52 для совместимости с почтовыми клиентами.
# Тёмная тема Gmail инвертирует картинки с прозрачным фоном: PNG «запечён» на цвет
# письма (#171c1f), поэтому прозрачных пикселей в нём нет и инвертировать нечего.
# ?v=2 сбрасывает кеш у тех, кто уже получил старый прозрачный файл.
LOGO_URL = SITE + "/assets/img/mail-logo.png?v=2"


def render(*, purpose, code, action_url="", action_label="", recipient="", expires_minutes=10):
    """HTML письма: сплошной фон, без рамок, блоков и изображений."""
    title, lead = PURPOSE_COPY.get(purpose, ("Код подтверждения CopiMine", "Введите код ниже."))
    link = ""
    if action_url:
        label = html.escape(action_label or "Открыть copimine.ru")
        link = (
            f'<p style="margin:26px 0 0;font:15px/24px {FONT}">'
            f'<a href="{html.escape(action_url, quote=True)}" '
            f'style="color:{ACCENT};text-decoration:underline">{label}</a></p>'
        )
    return f"""<!doctype html>
<html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light only">
<meta name="supported-color-schemes" content="light only">
<title>{html.escape(title)}</title>
<style>
/* Почтовые клиенты с тёмной темой инвертируют картинки — логотип защищаем отдельно. */
img.cm-logo{{filter:none!important;-webkit-filter:none!important;opacity:1!important;-ms-filter:none!important}}
</style></head>
<body style="margin:0;padding:0;background:{PAGE}">
<div style="display:none;font-size:1px;color:{PAGE};max-height:0;overflow:hidden">{html.escape(PREHEADER)}</div>
<div style="background:{PAGE};padding:40px 24px">
<div style="max-width:460px;margin:0 auto;font:15px/24px {FONT};color:{INK}">
<p style="margin:0 0 30px;line-height:0"><img src="{LOGO_URL}" width="140" height="28" alt="CopiMine" class="cm-logo" style="display:block;width:140px;height:28px;border:0;outline:none;text-decoration:none"></p>
<p style="margin:0 0 10px;color:{MUTED}">{html.escape(title)}</p>
<p style="margin:0 0 26px">{html.escape(lead)}</p>
<p style="margin:0 0 26px;font:600 40px/48px ui-monospace,SFMono-Regular,Consolas,monospace;letter-spacing:10px;color:{INK}">{html.escape(code)}</p>
<p style="margin:0;color:{MUTED}">Код действует {expires_minutes} минут.</p>
{link}
<p style="margin:30px 0 0;color:{MUTED};font-size:13px;line-height:21px">Если вы ничего не запрашивали, просто проигнорируйте это письмо.<br>Никому не сообщайте код — сотрудники CopiMine его не спрашивают.</p>
</div>
</div>
</body></html>"""


def text_version(*, purpose, code, action_url="", expires_minutes=10):
    """Текстовая часть для почтовых клиентов без HTML."""
    title, lead = PURPOSE_COPY.get(purpose, ("Код подтверждения CopiMine", "Введите код ниже."))
    lines = ["CopiMine", "", title, lead, "", f"Код: {code}", f"Действует {expires_minutes} минут.", ""]
    if action_url:
        lines += [f"Открыть: {action_url}", ""]
    lines += [
        "Если вы ничего не запрашивали, проигнорируйте письмо.",
        "Никому не сообщайте код — сотрудники CopiMine его не спрашивают.",
        "",
        "copimine.ru",
    ]
    return "\n".join(lines)
