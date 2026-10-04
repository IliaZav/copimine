"""Guard the installable CopiMineClient artifact against stale mob assets."""

from __future__ import annotations

import io
import os
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "CopiMineClient"
BUILD = CLIENT / "build"
DEV_JAR = BUILD / "devlibs" / "CopiMineClient-0.1.1-dev.jar"
BUILD_JAR = BUILD / "libs" / "CopiMineClient-0.1.1.jar"
STAGED_JAR = ROOT / "thirdparty" / "client-mods" / "CopiMineClient-0.1.1.jar"
MODPACK = ROOT / "thirdparty" / "CopiMineMods.zip"
MODPACK_CLIENT_ENTRY = "mods/CopiMineClient-0.1.1.jar"
SOURCE_TEXTURES = CLIENT / "src" / "main" / "resources" / "assets" / "copimineclient" / "textures" / "entity"
EXPECTED_CURRENT_SKELETON_TEXTURE = "end_rift_user_skeleton.png"

MODEL_CLASSES = (
    "me/copimine/client/EndEventTextureCatalog.class",
    "me/copimine/client/RiftEventSkeletonModel.class",
    "me/copimine/client/RiftEventEndermanModel.class",
    "me/copimine/client/RiftSpiderModel.class",
    "me/copimine/client/RitualSphereRenderer.class",
    "me/copimine/client/RitualSphereMesh.class",
)
MOB_TEXTURES = (
    "end_rift_user_skeleton.png",
    "end_rift_user_enderman.png",
    "end_rift_user_spider.png",
    "end_rift_elite.png",
    "end_rift_elite_skeleton.png",
    "end_rift_elite_spider.png",
    "end_event_ritual_shell.png",
    "end_event_ritual_membrane.png",
)
PROFILE_JAR_ENV = "COPIMINE_PROFILE_CLIENT_JAR"


def jar_payloads(payload: bytes) -> dict[str, bytes]:
    with ZipFile(io.BytesIO(payload)) as archive:
        return {
            info.filename: archive.read(info)
            for info in archive.infolist()
            if not info.is_dir()
        }


def assert_current_mob_payload(artifact: str, payload: bytes) -> None:
    entries = jar_payloads(payload)
    for class_name in MODEL_CLASSES:
        assert class_name in entries, f"{artifact} is missing current mob class {class_name}"

    compiled_classes = BUILD / "classes" / "java" / "main"
    if compiled_classes.is_dir():
        missing_classes = {
            path.relative_to(compiled_classes).as_posix()
            for path in compiled_classes.rglob("*.class")
            if path.relative_to(compiled_classes).as_posix() not in entries
        }
        assert not missing_classes, (
            f"{artifact} is missing compiled client classes: {sorted(missing_classes)}"
        )

    catalog = entries[MODEL_CLASSES[0]]
    expected_texture = EXPECTED_CURRENT_SKELETON_TEXTURE.encode("ascii")
    assert expected_texture in catalog, (
        f"{artifact} EndEventTextureCatalog does not bind the ordinary skeleton "
        f"to {EXPECTED_CURRENT_SKELETON_TEXTURE}"
    )
    assert b"end_rift_skeleton.png" not in catalog, (
        f"{artifact} still binds the generated-palette skeleton atlas to the ordinary skeleton"
    )
    for visual_id, texture in (
        ("END_RIFT_ELITE_V1", "end_rift_elite.png"),
        ("END_RIFT_ELITE_SKELETON_V1", "end_rift_elite_skeleton.png"),
        ("END_RIFT_ELITE_SPIDER_V1", "end_rift_elite_spider.png"),
    ):
        assert visual_id.encode("ascii") in catalog, f"{artifact} is missing {visual_id}"
        assert texture.encode("ascii") in catalog, f"{artifact} does not bind {texture}"

    for filename in MOB_TEXTURES:
        source = SOURCE_TEXTURES / filename
        entry = f"assets/copimineclient/textures/entity/{filename}"
        assert entry in entries, f"{artifact} is missing texture entry {entry}"
        assert entries[entry] == source.read_bytes(), (
            f"{artifact} texture {entry} differs from current source {source}"
        )


def test_source_catalog_uses_the_supplied_ordinary_skeleton_atlas() -> None:
    catalog = (CLIENT / "src" / "main" / "java" / "me" / "copimine" / "client"
               / "EndEventTextureCatalog.java").read_text(encoding="utf-8")
    assert ('textures.put("END_RIFT_SKELETON_V1", '
            'entityTexture("end_rift_user_skeleton.png"));') in catalog
    assert ('textures.put("END_RIFT_SKELETON_V1", '
            'entityTexture("end_rift_skeleton.png"));') not in catalog


def test_dev_jar_is_compiler_output_and_remap_output_is_newer() -> None:
    assert DEV_JAR.is_file(), f"Gradle jar/remapJar input is missing: {DEV_JAR}"
    dev_entries = jar_payloads(DEV_JAR.read_bytes())
    outputs = (
        BUILD / "classes" / "java" / "main",
        BUILD / "resources" / "main",
    )
    java_sources = CLIENT / "src" / "main" / "java"
    for class_name in MODEL_CLASSES:
        class_path = Path(class_name)
        source = java_sources / class_path.with_suffix(".java")
        compiled = outputs[0] / class_path
        assert source.is_file(), f"current client source is missing: {source}"
        assert compiled.is_file(), f"compiled client class is missing: {compiled}"
        assert source.stat().st_mtime_ns <= compiled.stat().st_mtime_ns, (
            f"compiled class {compiled} predates current source {source}"
        )
    latest_input_ns = 0
    compared = 0
    for output_root in outputs:
        for path in output_root.rglob("*"):
            if not path.is_file():
                continue
            latest_input_ns = max(latest_input_ns, path.stat().st_mtime_ns)
            entry = path.relative_to(output_root).as_posix()
            assert entry in dev_entries, f"Gradle dev JAR is missing current input {entry}"
            assert dev_entries[entry] == path.read_bytes(), (
                f"Gradle dev JAR entry {entry} differs from remapJar input {path}"
            )
            compared += 1
    assert compared, "no compiled client classes or processed resources were checked"
    assert latest_input_ns <= DEV_JAR.stat().st_mtime_ns, (
        "Gradle dev JAR predates compiled classes or processed resources"
    )
    assert DEV_JAR.stat().st_mtime_ns <= BUILD_JAR.stat().st_mtime_ns, (
        "Gradle remapJar output predates its dev JAR input"
    )


def test_built_client_jar_contains_current_mob_classes_and_textures() -> None:
    assert BUILD_JAR.is_file(), f"built client JAR is missing: {BUILD_JAR}"
    assert_current_mob_payload(str(BUILD_JAR), BUILD_JAR.read_bytes())


def test_staged_client_jar_contains_current_mob_classes_and_textures() -> None:
    assert STAGED_JAR.is_file(), f"staged client JAR is missing: {STAGED_JAR}"
    assert BUILD_JAR.read_bytes() == STAGED_JAR.read_bytes(), (
        "thirdparty client JAR must be byte-identical to the Gradle remapJar output"
    )
    assert_current_mob_payload(str(STAGED_JAR), STAGED_JAR.read_bytes())


def test_installable_modpack_contains_current_client_jar() -> None:
    assert MODPACK.is_file(), f"installable modpack is missing: {MODPACK}"
    with ZipFile(MODPACK) as archive:
        payload = archive.read(MODPACK_CLIENT_ENTRY)
    assert payload == BUILD_JAR.read_bytes(), (
        "installable modpack client JAR must be byte-identical to the Gradle remapJar output"
    )
    assert_current_mob_payload(f"{MODPACK}!/{MODPACK_CLIENT_ENTRY}", payload)


def test_configured_live_profile_jar_matches_the_gradle_remap_output() -> None:
    configured_path = os.environ.get(PROFILE_JAR_ENV)
    if not configured_path:
        import pytest

        pytest.skip(f"set {PROFILE_JAR_ENV} to audit a local Minecraft profile")
    profile_jar = Path(configured_path)
    assert profile_jar.is_file(), f"configured live-profile client JAR is missing: {profile_jar}"
    assert profile_jar.read_bytes() == BUILD_JAR.read_bytes(), (
        f"live-profile client JAR {profile_jar} is stale relative to Gradle remapJar output"
    )
    assert_current_mob_payload(str(profile_jar), profile_jar.read_bytes())
