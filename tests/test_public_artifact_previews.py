import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "admin-web" / "backend"))
from commerce_catalog import load_commerce_catalog

WEB_TEXTURES = ROOT / "admin-web/frontend/assets/item-textures"
RESOURCE_TEXTURES = ROOT / "resourcepacks/src/assets/copimine/textures/item/artifacts"

PUBLIC_ARTIFACTS = (
    "gravedigger_contract",
    "signal_bell",
    "berserker_heart",
    "zhilorez_pickaxe",
    "donation_night_cloak",
)


def test_public_shop_preview_assets_match_the_canonical_resource_pack_textures() -> None:
    catalog = load_commerce_catalog(ROOT / "copimine-artifacts/items.yml")
    catalog_rows = {
        row["item_id"]: row
        for section in (catalog["ar"], catalog["donation"])
        for row in section["items"]
    }

    for item_id in PUBLIC_ARTIFACTS:
        web_image = WEB_TEXTURES / f"{item_id}.png"
        resource_pack_image = RESOURCE_TEXTURES / f"{item_id}.png"
        assert resource_pack_image.is_file(), f"missing canonical resource-pack texture for {item_id}"
        assert web_image.is_file(), f"missing public shop preview for {item_id}"
        assert catalog_rows[item_id]["image_url"] == f"/assets/item-textures/{item_id}.png"
        assert web_image.read_bytes() == resource_pack_image.read_bytes(), f"preview drift for {item_id}"
