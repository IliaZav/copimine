"""Письма CopiMine: только текст на фоне, без блоков, рамок и изображений."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from backend import mail_render


def test_logo_is_the_only_image_and_comes_from_our_site():
    markup = mail_render.render(purpose="reset", code="123456", action_url="https://copimine.ru/#/confirm?challenge=abc")
    images = re.findall(r"<img[^>]+src=\"([^\"]+)\"", markup)
    assert len(images) == 1, images
    assert images[0].startswith("https://copimine.ru/")
    assert "mail-logo.png" in images[0]


def test_logo_has_no_transparent_pixels_so_dark_themes_cannot_invert_it():
    """Gmail в тёмной теме инвертирует прозрачные картинки: фон должен быть запечён."""
    from PIL import Image

    path = Path(__file__).resolve().parents[1] / "frontend/assets/img/mail-logo.png"
    logo = Image.open(path).convert("RGBA")
    alphas = {px[3] for px in logo.get_flattened_data()}
    assert alphas == {255}, "в логотипе остались прозрачные пиксели — Gmail покажет негатив"
    # Угол должен совпадать с фоном письма, иначе вокруг слова будет тёмный прямоугольник.
    assert logo.getpixel((0, 0))[:3] == (0x17, 0x1C, 0x1F)
    assert "?v=" in mail_render.LOGO_URL, "смените версию, иначе клиенты возьмут старый файл из кеша"


def test_only_table_is_the_logo_plate():
    """Таблица допустима только одна — служебная подложка под логотип."""
    markup = mail_render.render(purpose="reset", code="123456", action_url="https://copimine.ru/#/confirm?challenge=abc")
    assert markup.lower().count("<table") <= 1
    assert "<script" not in markup.lower()


def test_logo_has_no_plate_no_white_and_no_inverting_meta():
    """Логотип — оригинал как есть: без подложки, без белого, без color-scheme dark."""
    markup = mail_render.render(purpose="reset", code="123456", action_url="https://copimine.ru/x")
    assert "cm-logo-plate" not in markup
    assert "#ffffff" not in markup.lower()
    assert "background:#fff" not in markup.lower()
    assert 'content="dark"' not in markup
    assert "cm-logo" in markup
    # border:0 в <img> и <table border="0"> — служебный сброс рамки, а не рамка блока
    without_img = re.sub(r"<img[^>]*>", "", markup)
    without_table = re.sub(r"<table[^>]*>", "", without_img)
    assert "border" not in without_table and "border:" not in without_table
    assert "border-radius" not in markup     # никаких карточек
    assert "<button" not in markup.lower()
    assert "background:#141a1d" not in markup  # вложенный блок-панель убран


def test_code_and_text_present():
    markup = mail_render.render(purpose="register", code="654321", action_url="https://copimine.ru/x")
    assert "654321" in markup
    assert "Подтверждение регистрации" in markup
    assert "10 минут" in markup
    assert "CopiMine" in markup


def test_link_is_plain_underline_not_button():
    url = "https://copimine.ru/#/confirm?challenge=deadbeef"
    markup = mail_render.render(purpose="link_email", code="333333", action_url=url, action_label="Подтвердить почту")
    assert url in markup
    assert "Подтвердить почту" in markup
    assert "text-decoration:underline" in markup


def test_escaping_of_untrusted_url():
    markup = mail_render.render(purpose="login", code="111111", action_url="https://copimine.ru/?a=1&b=<2>")
    assert "<2>" not in markup
    assert "&lt;2&gt;" in markup


def test_no_link_without_action_url():
    markup = mail_render.render(purpose="reset", code="444444")
    assert 'href="' not in markup


def test_text_version_matches():
    text = mail_render.text_version(purpose="reset", code="555555", action_url="https://copimine.ru/x")
    assert "555555" in text and "https://copimine.ru/x" in text and "copimine.ru" in text


def test_every_purpose_has_copy():
    for purpose in ("register", "login", "reset", "link_email"):
        title, lead = mail_render.PURPOSE_COPY[purpose]
        assert title and lead
        assert "999999" in mail_render.render(purpose=purpose, code="999999")


def test_markup_stays_small():
    markup = mail_render.render(purpose="reset", code="123456", action_url="https://copimine.ru/#/confirm?challenge=abc")
    assert len(markup) < 2500, f"письмо разрослось: {len(markup)} символов"
