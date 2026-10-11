"""Regression contract for strict dependency verification in the 26.3 Gradle builds."""

import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]
GRADLE_NS = "https://schema.gradle.org/dependency-verification"
NS = {"g": GRADLE_NS}


@pytest.mark.parametrize(
    ("relative_path", "required_artifact", "allows_loom_generated_merge"),
    [
        ("tools/minecraft-26.3/gradle/verification-metadata.xml", "paper-api", False),
        ("tools/minecraft-26.3/client/gradle/verification-metadata.xml", "fabric-loom", True),
    ],
)
def test_migration_gradle_projects_verify_external_artifacts_strictly(
    relative_path: str, required_artifact: str, allows_loom_generated_merge: bool
) -> None:
    metadata_path = ROOT / relative_path
    assert metadata_path.is_file(), f"Missing strict dependency verification metadata: {relative_path}"

    document = ET.parse(metadata_path).getroot()
    assert document.tag == f"{{{GRADLE_NS}}}verification-metadata"
    config = document.find("g:configuration", NS)
    assert config is not None
    assert config.findtext("g:verify-metadata", namespaces=NS) == "true"
    trusted_artifacts = config.find("g:trusted-artifacts", NS)
    if allows_loom_generated_merge:
        assert trusted_artifacts is not None
        trusts = trusted_artifacts.findall("g:trust", NS)
        assert len(trusts) == 1
        trust = trusts[0]
        assert trust.attrib == {
            "group": r"^net[.]minecraft$",
            "name": r"^minecraft-merged-[a-f0-9]{10,64}$",
            "version": r"^26[.]3$",
            "file": r"^minecraft-merged-[a-f0-9]{10,64}-26[.]3[.](jar|pom)$",
            "regex": "true",
            "reason": (
                "Fabric Loom creates this transient merged Minecraft 26.3 jar and POM "
                "locally from checksum-verified Minecraft inputs."
            ),
        }
    else:
        assert trusted_artifacts is None

    components = document.findall("g:components/g:component", NS)
    assert components, f"No verified dependencies are recorded in {relative_path}"
    verified_artifacts = []
    for component in components:
        for artifact in component.findall("g:artifact", NS):
            name = artifact.get("name", "")
            hashes = {
                checksum.get("value", "")
                for checksum in artifact
                if checksum.tag in {f"{{{GRADLE_NS}}}sha256", f"{{{GRADLE_NS}}}sha512"}
            }
            assert hashes, f"Artifact {name!r} has no strong checksum in {relative_path}"
            assert all(re.fullmatch(r"[0-9a-f]{64}|[0-9a-f]{128}", value) for value in hashes)
            verified_artifacts.append(name)

    assert any(required_artifact in name for name in verified_artifacts), (
        f"Expected migration dependency {required_artifact!r} is not checksum-verified"
    )


def test_server_voice_chat_candidate_integrity_is_lazy_until_admin_compile() -> None:
    build_script = (ROOT / "tools/minecraft-26.3/build.gradle").read_text(encoding="utf-8")

    assert "tasks.register('verifyVoiceChatApi')" in build_script
    assert re.search(
        r"tasks\.named\('compileAdminJava'\)\s*\{\s*dependsOn\s*\(?\s*verifyVoiceChatApi\s*\)?\s*\}",
        build_script,
    )
    task_start = build_script.index("tasks.register('verifyVoiceChatApi')")
    task_end = build_script.index("layout.buildDirectory", task_start)
    task_body = build_script[task_start:task_end]
    assert "doLast {" in task_body
    task_action_body = task_body.split("doLast {", 1)[1]
    for check in (
        "voiceChatApiCandidate.isFile()",
        "voiceChatApiCandidate.length()",
        "MessageDigest.getInstance('SHA-512')",
        "if (voiceChatApiSha512 != voiceChatPlugin.sha512)",
    ):
        assert check in task_action_body, f"{check} must run inside the verification task action"


def test_client_gradle_metadata_covers_fabric_loom_plugin_marker_and_implementation() -> None:
    metadata_path = ROOT / "tools/minecraft-26.3/client/gradle/verification-metadata.xml"
    document = ET.parse(metadata_path).getroot()
    coordinates = {
        (component.get("group"), component.get("name"), component.get("version"))
        for component in document.findall("g:components/g:component", NS)
    }

    assert (
        "net.fabricmc.fabric-loom",
        "net.fabricmc.fabric-loom.gradle.plugin",
        "1.17.21",
    ) in coordinates
    assert ("net.fabricmc", "fabric-loom", "1.17.21") in coordinates

    loom_component = next(
        component
        for component in document.findall("g:components/g:component", NS)
        if (component.get("group"), component.get("name"), component.get("version"))
        == ("net.fabricmc", "fabric-loom", "1.17.21")
    )
    loom_artifact = next(
        artifact
        for artifact in loom_component.findall("g:artifact", NS)
        if artifact.get("name") == "fabric-loom-1.17.21.jar"
    )
    loom_jar = loom_artifact.find("g:sha256", NS)
    assert loom_jar is not None
    assert loom_jar.get("value") == "89c08938d865621172e3fb31036081399b2731a4971f599b42724b2509ff5b64"
    assert loom_jar.get("origin") == (
        "publisher_sha256_sidecar: "
        "https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.17.21/"
        "fabric-loom-1.17.21.jar.sha256"
    )

    verified_sources = {
        ("net.fabricmc", "fabric-loom", "fabric-loom-1.17.21.module"): (
            "b4b57c34129fe2b113c9db754d7deca89c527eeffbb08612af3c64760e9006b3",
            "https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.17.21/"
            "fabric-loom-1.17.21.module.sha256",
        ),
        (
            "net.fabricmc.fabric-loom",
            "net.fabricmc.fabric-loom.gradle.plugin",
            "net.fabricmc.fabric-loom.gradle.plugin-1.17.21.pom",
        ): (
            "15cc54da3a3afd9b4d683fd28707a3bcd3aa9668f4a7576468c05a56675cd0e4",
            "https://maven.fabricmc.net/net/fabricmc/fabric-loom/"
            "net.fabricmc.fabric-loom.gradle.plugin/1.17.21/"
            "net.fabricmc.fabric-loom.gradle.plugin-1.17.21.pom.sha256",
        ),
    }
    components = document.findall("g:components/g:component", NS)
    for (group, name, artifact_name), (expected_hash, sidecar_url) in verified_sources.items():
        component = next(
            component
            for component in components
            if component.get("group") == group
            and component.get("name") == name
            and component.get("version") == "1.17.21"
        )
        artifact = next(
            artifact
            for artifact in component.findall("g:artifact", NS)
            if artifact.get("name") == artifact_name
        )
        checksum = artifact.find("g:sha256", NS)
        assert checksum is not None
        assert checksum.get("value") == expected_hash
        assert checksum.get("origin") == f"publisher_sha256_sidecar: {sidecar_url}"


def test_client_gradle_metadata_does_not_checksum_pin_the_transient_merged_jar() -> None:
    metadata_path = ROOT / "tools/minecraft-26.3/client/gradle/verification-metadata.xml"
    document = ET.parse(metadata_path).getroot()

    transient_merge = [
        component
        for component in document.findall("g:components/g:component[@group='net.minecraft']", NS)
        if component.get("name", "").startswith("minecraft-merged-")
    ]
    assert transient_merge == []


def test_server_paper_api_checksums_match_the_profile_pin_and_official_sidecar() -> None:
    metadata_path = ROOT / "tools/minecraft-26.3/gradle/verification-metadata.xml"
    profile_path = ROOT / "tools/minecraft-26.3/profile.lock.json"
    document = ET.parse(metadata_path).getroot()
    profile = json.loads(profile_path.read_text(encoding="utf-8"))
    paper_api = next(
        component
        for component in document.findall("g:components/g:component", NS)
        if (component.get("group"), component.get("name"), component.get("version"))
        == ("io.papermc.paper", "paper-api", "26.3.build.169-beta")
    )

    paper_jar = next(
        artifact
        for artifact in paper_api.findall("g:artifact", NS)
        if artifact.get("name") == "paper-api-26.3.build.169-beta.jar"
    ).find("g:sha256", NS)
    assert paper_jar is not None
    assert paper_jar.get("value") == profile["paper"]["apiSha256"]
    assert paper_jar.get("origin") == (
        "publisher_sha256_sidecar: "
        "https://repo.papermc.io/repository/maven-public/io/papermc/paper/"
        "paper-api/26.3.build.169-beta/paper-api-26.3.build.169-beta.jar.sha256"
    )

    paper_module = next(
        artifact
        for artifact in paper_api.findall("g:artifact", NS)
        if artifact.get("name") == "paper-api-26.3.build.169-beta.module"
    ).find("g:sha256", NS)
    assert paper_module is not None
    assert paper_module.get("value") == "b84b128c981389eb46a82c3341283c6f5766f2409d882521399d9e9444538b13"
    assert paper_module.get("origin") == (
        "publisher_sha256_sidecar: "
        "https://repo.papermc.io/repository/maven-public/io/papermc/paper/"
        "paper-api/26.3.build.169-beta/paper-api-26.3.build.169-beta.module.sha256"
    )


def test_gradle_dependency_provenance_covers_every_external_checksum() -> None:
    manifest_paths = {
        "server": ROOT / "tools/minecraft-26.3/gradle/verification-metadata.xml",
        "client": ROOT / "tools/minecraft-26.3/client/gradle/verification-metadata.xml",
    }
    expected_checksums = {}
    checksum_nodes = {}
    for root_name, metadata_path in manifest_paths.items():
        document = ET.parse(metadata_path).getroot()
        for component in document.findall("g:components/g:component", NS):
            for artifact in component.findall("g:artifact", NS):
                checksum = artifact.find("g:sha256", NS)
                assert checksum is not None
                key = (
                    root_name,
                    component.get("group"),
                    component.get("name"),
                    component.get("version"),
                    artifact.get("name"),
                )
                expected_checksums[key] = checksum.get("value")
                checksum_nodes[key] = checksum

    report_path = ROOT / "docs/minecraft-26.3/GRADLE_DEPENDENCY_PROVENANCE.json"
    report = json.loads(report_path.read_text(encoding="utf-8"))
    assert report["schemaVersion"] == 1
    provenance = {
        (
            artifact["root"],
            artifact["group"],
            artifact["module"],
            artifact["version"],
            artifact["artifact"],
        ): artifact
        for artifact in report["artifacts"]
    }
    assert provenance.keys() == expected_checksums.keys()
    for key, expected_sha256 in expected_checksums.items():
        record = provenance[key]
        assert record["sha256"] == expected_sha256
        assert record["observedSha256"] == expected_sha256
        assert record["sourceUrl"].startswith("https://")
        assert record["verification"] in {
            "publisher_sha256_sidecar",
            "direct_https_artifact_download",
        }
        assert checksum_nodes[key].get("origin") == (
            f"{record['verification']}: {record['sourceUrl']}"
        )

    assert report["counts"] == {
        "totalExternalArtifacts": len(expected_checksums),
        "publisherSidecarMatches": 440,
        "directArtifactMatches": 91,
        "alternateMirrorSidecarMismatches": 6,
    }
    mirror_mismatches = [
        artifact for artifact in report["artifacts"] if "alternateSidecarObservation" in artifact
    ]
    assert len(mirror_mismatches) == 6
    for artifact in mirror_mismatches:
        alternate = artifact["alternateSidecarObservation"]
        expected_mirror_path = "/".join(
            (
                artifact["group"].replace(".", "/"),
                artifact["module"],
                artifact["version"],
                f"{artifact['artifact']}.sha256",
            )
        )
        assert artifact["sourceHost"] == "repo.maven.apache.org"
        assert artifact["sourceUrl"].startswith("https://repo.maven.apache.org/maven2/")
        assert artifact["verification"] == "direct_https_artifact_download"
        assert alternate["url"] == (
            f"https://repo.papermc.io/repository/maven-public/{expected_mirror_path}"
        )
        assert re.fullmatch(r"[0-9a-f]{64}", alternate["sha256"])
        assert alternate["sha256"] != artifact["sha256"]
