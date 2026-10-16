from pathlib import Path
import json
import re


ROOT = Path(__file__).resolve().parents[1]
BUILDERS = (
    ROOT / "copimine-economy-core" / "build-plugin.ps1",
    ROOT / "copimine-election-core" / "build-plugin.ps1",
    ROOT / "copimine-artifacts" / "build-plugin.ps1",
    ROOT / "copimine-admin-plugin" / "build-plugin.ps1",
)
LOCKED_BUILDERS = (
    ROOT / "copimine-world-core" / "build-plugin.ps1",
    ROOT / "minecraft" / "server" / "plugins" / "AuthEffects" / "build-plugin.ps1",
)


def test_unlocked_plugin_builders_omit_source_and_javadoc_archives():
    compile_lock = json.loads(
        (ROOT / "tools" / "minecraft-26.3" / "java-plugin-compile-dependencies.lock.json").read_text(
            encoding="utf-8"
        )
    )
    locked_dependencies = [
        *compile_lock["dependencies"],
        *compile_lock["paper26_3Dependencies"],
    ]
    assert all(
        re.search(r"-(?:sources|javadoc)\.jar$", dependency["path"], re.IGNORECASE) is None
        for dependency in locked_dependencies
    )

    for builder in (*BUILDERS, *LOCKED_BUILDERS):
        source = builder.read_text(encoding="utf-8")
        assert "java-plugin-compile-dependencies.lock.json" in source, builder.relative_to(ROOT)
        assert "Assert-LockedCompileFile -Path $compileDependencies[$index]" in source, builder.relative_to(ROOT)

    admin_source = (ROOT / "copimine-admin-plugin" / "build-plugin.ps1").read_text(encoding="utf-8")
    archive_filter = r"Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' }"
    assert admin_source.count(archive_filter) == 4
    assert "[IO.Path]::GetFileName($optionalDependency) -match '-(sources|javadoc)\\.jar$'" in admin_source


def test_world_core_and_autheffects_use_only_the_verified_dependency_lock():
    ambient_discovery = (
        "Get-ChildItem -Path $groupPath",
        "Get-ChildItem -Path (Join-Path $serverDir 'libraries')",
        "Get-ChildItem -Path $serverLibraries",
    )
    for builder in LOCKED_BUILDERS:
        source = builder.read_text(encoding="utf-8")
        assert "java-plugin-compile-dependencies.lock.json" in source
        assert "Assert-LockedCompileFile -Path $compileDependencies[$index]" in source
        assert "$cp = @($paperApi) + $compileDependencies" in source
        assert all(marker not in source for marker in ambient_discovery), builder.relative_to(ROOT)
