from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    ROOT / "copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java",
    ROOT / "copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java",
    ROOT / "copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java",
    ROOT / "copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java",
]


def test_custom_block_visuals_use_one_cross_plugin_pdc_namespace() -> None:
    for path in SOURCES:
        source = path.read_text(encoding="utf-8")
        assert 'new NamespacedKey("copimine", "visual_entity_type")' in source
        assert 'new NamespacedKey("copimine", "visual_kind")' in source
        assert 'new NamespacedKey("copimine", "visual_linked_id")' in source
        assert 'new NamespacedKey("copimine", "visual_model_id")' in source


def test_custom_block_visual_cleanup_accepts_legacy_plugin_owned_entities() -> None:
    for path in SOURCES:
        source = path.read_text(encoding="utf-8")
        assert "readVisualString" in source
        assert "isLegacyProtectedVisualModel" in source
        assert "copimineeconomycore" in source
        assert "copimineultimateadminplus" in source
        assert "copimineartifacts" in source
        assert "copimineelectioncore" in source
