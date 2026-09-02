from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN_SOURCE = (ROOT / "admin-web" / "backend" / "main.py").read_text(encoding="utf-8")
CATALOG_SOURCE = (ROOT / "copimine-artifacts" / "items.yml").read_text(encoding="utf-8-sig")


def _slice(text: str, start_marker: str, end_marker: str) -> str:
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    return text[start:end]


def _ar_block(item_id: str) -> str:
    marker = f"  - id: {item_id}\n"
    start = CATALOG_SOURCE.index(marker)
    ends = [CATALOG_SOURCE.find("\n  - id:", start + len(marker)), CATALOG_SOURCE.find("\ndonation-catalog:", start + len(marker))]
    ends = [candidate for candidate in ends if candidate != -1]
    end = min(ends) if ends else len(CATALOG_SOURCE)
    return CATALOG_SOURCE[start:end]


def _donation_block(item_id: str) -> str:
    marker = f"    - item-id: {item_id}\n"
    start = CATALOG_SOURCE.index(marker)
    end = CATALOG_SOURCE.find("\n    - item-id:", start + len(marker))
    if end == -1:
        end = len(CATALOG_SOURCE)
    return CATALOG_SOURCE[start:end]


def test_admin_create_contract_uses_site_login_and_optional_minecraft_name():
    admin_access = _slice(MAIN_SOURCE, "class AdminAccessIn(BaseModel):", "class AdminUpdateIn(BaseModel):")
    create_admin = _slice(MAIN_SOURCE, '@app.post("/api/security/admins")', '@app.patch("/api/security/admins/{username}")')

    assert "minecraft_name: Optional[str] = Field(default=None, max_length=16)" in admin_access
    assert "minecraft_name TEXT NOT NULL DEFAULT" in MAIN_SOURCE
    assert "valid_site_username(username)" in create_admin
    assert "valid_minecraft_name(username)" not in create_admin
    assert "data.minecraft_name" in create_admin
    assert "ensure_minecraft_access_for_new_admin(username, data.ensure_op, data.ensure_whitelist)" not in create_admin


def test_requested_catalog_prices_cover_all_20_ids_and_preserve_admin_only_item():
    expected_ar = {
        "zmei_gorynych": 150,
        "kopatel_transhey_shovel": 30,
        "craftsman_hammer": 300,
        "fermer_bez_sna_hoe": 50,
        "lesnoy_bespredel_axe": 90,
        "copimine_miner_pickaxe": 500,
        "smena_bez_perekura_pickaxe": 100,
        "treasurer_chestplate": 120,
        "dezhurniy_argument_sword": 200,
        "eternal_totem": 9999,
        "vechniy_razgon_firework": 9999,
    }
    expected_donation = {
        "vremya_platit_nalogi_clock": 300,
        "pohuy_na_debaffy_amulet": 150,
        "ne_segodnya_suka_shield": 50,
        "kaska_prorab_huev": 50,
        "mne_pohuy_ya_v_tanke_vest": 150,
        "kosa_nalogovoy_inspekcii": 200,
        "nu_ty_i_nakopal_blyat_pickaxe": 400,
        "batin_remen_sudnogo_dnya": 500,
    }

    for item_id, expected_price in expected_ar.items():
        block = _ar_block(item_id)
        assert re.search(rf"(?m)^\s+price_ar:\s*{expected_price}\s*$", block), (item_id, block)

    for item_id, expected_price in expected_donation.items():
        block = _donation_block(item_id)
        assert re.search(rf"(?m)^\s+price-donation:\s*{expected_price}\s*$", block), (item_id, block)

    admin_only = _ar_block("kozyrny_tuz_pozdnyakova")
    assert "source: ADMIN_ONLY" in admin_only, admin_only
    assert re.search(r"(?m)^\s+price_ar:\s*5000\s*$", admin_only), admin_only


def test_requested_catalog_descriptions_are_player_facing_and_not_stale():
    expected_ar_lore = {
        "zmei_gorynych": "С каждым ударом есть шанс нанести крит с дебаффами",
        "kopatel_transhey_shovel": "При копании песка или гравия есть маленький шанс вскопать любую драгоценную руду",
        "craftsman_hammer": "ПКМ по земле подбрасывает в воздух и станит врага",
        "fermer_bez_sna_hoe": "Шифт + ПКМ по зрелой грядке вспахивает 5 на 5",
        "lesnoy_bespredel_axe": "Небольшой шанс срубить всё дерево разом",
        "copimine_miner_pickaxe": "Копает 3 на 3",
        "smena_bez_perekura_pickaxe": "ПКМ в воздух даёт спешку II на 3 минуты",
        "treasurer_chestplate": "С каждым ударом соперника есть шанс получить положительные эффекты",
        "dezhurniy_argument_sword": "С каждым ударом есть шанс нанести удар с большим количеством дебаффов",
        "eternal_totem": "Бесконечный тотем если держать в основной руке",
        "vechniy_razgon_firework": "Бесконечный фейерверк с маленьким кд после каждого использования",
    }
    expected_donation_lore = {
        "vremya_platit_nalogi_clock": "Освобождает от налогов на 3 месяца",
        "pohuy_na_debaffy_amulet": "При нажатии ПКМ заменяет дебафф на бафф",
        "ne_segodnya_suka_shield": "На него всем похуй",
        "kaska_prorab_huev": "С маленьким шансом уменьшает урон от падения и даёт эффект спешки и скорости",
        "mne_pohuy_ya_v_tanke_vest": "Уменьшает любой входящий урон на 20% и с небольшим шансом даёт бафф",
        "kosa_nalogovoy_inspekcii": "Вампиризм; шанс 2.5% украсть 1–3 AR со счёта цели",
        "nu_ty_i_nakopal_blyat_pickaxe": "При ударе по врагу закапывает его в трапку",
        "batin_remen_sudnogo_dnya": "Большой урон, бьёт противника молнией, даёт владельцу эффект скорости и накладывает дебафф на цель",
    }
    for item_id, description in expected_ar_lore.items():
        assert description in _ar_block(item_id), (item_id, description)
    for item_id, description in expected_donation_lore.items():
        assert description in _donation_block(item_id), (item_id, description)
