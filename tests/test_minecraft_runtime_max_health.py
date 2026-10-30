"""The real launcher must verify Spigot's boss-health cap without rewriting settings."""
import json

import pytest

from tests.test_minecraft_migration_server_plugins import (
    _write_startup_validation_fixture, load, write_bootstrap_fixture,
)


def spigot(cap):
    return ("# Operator settings must survive validation byte-for-byte.\n"
            "settings:\n  attribute:\n    maxHealth:\n      max: " + cap + "\n"
            "world-settings:\n  default:\n    view-distance: 7\n")


def runtime_bytes(runtime):
    return {str(path.relative_to(runtime)): path.read_bytes()
            for path in runtime.rglob("*") if path.is_file()}


def startup(mod, root, full_plugin_set=False):
    args = ["--startup-settings-json"]
    if not full_plugin_set:
        args.append("--unauthenticated-test-runtime")
    mod.main(root=root, argv=args)


INVALID = {
    "missing-file": None,
    "default-1024": spigot("1024"),
    "below-required": spigot("19999.999"),
    "negative": spigot("-1"),
    "nan": spigot(".nan"),
    "positive-infinity": spigot(".inf"),
    "negative-infinity": spigot("-.inf"),
    "boolean": spigot("true"),
    "quoted-number": spigot("'20000.0'"),
    "list": spigot("[20000]"),
    "null": spigot("null"),
    "overflow": spigot("9" * 1000),
    "missing-path": "settings:\n  attribute:\n    maxHealth: {}\n",
    "scalar-parent": "settings: 20000\n",
    "duplicate-cap": "settings:\n  attribute:\n    maxHealth:\n      max: 1024\n      max: 20000\n",
    "duplicate-settings": spigot("1024") + spigot("20000"),
    "multiple-documents": spigot("20000") + "---\n" + spigot("20000"),
    "malformed-yaml": "settings: [\n",
    "unsafe-tag": spigot("!!python/object/apply:builtins.str [20000]"),
}


@pytest.mark.parametrize("case", INVALID)
def test_startup_refuses_invalid_boss_health_cap_without_runtime_mutation(tmp_path, capsys, case):
    mod, runtime, _ = _write_startup_validation_fixture(tmp_path)
    target = runtime / "spigot.yml"
    text = INVALID[case]
    if text is None:
        target.unlink(missing_ok=True)
    else:
        target.write_text(text, encoding="utf-8")
    before = runtime_bytes(runtime)

    with pytest.raises(ValueError, match="spigot|maxHealth|health"):
        startup(mod, tmp_path)

    assert capsys.readouterr().out == "", "Rejected startup must not publish launch settings"
    assert runtime_bytes(runtime) == before, "Startup validation modified operator/runtime files"


@pytest.mark.parametrize("cap", ["20000", "20000.0", "25000.5"])
@pytest.mark.parametrize("full_plugin_set", [False, True])
def test_startup_accepts_supported_cap_and_preserves_all_settings(tmp_path, capsys, cap, full_plugin_set):
    mod, runtime, _ = _write_startup_validation_fixture(tmp_path, full_plugin_set=full_plugin_set)
    (runtime / "spigot.yml").write_text(spigot(cap), encoding="utf-8")
    before = runtime_bytes(runtime)

    startup(mod, tmp_path, full_plugin_set)

    result = json.loads(capsys.readouterr().out)
    assert result["serverIp"] == "127.0.0.1" and result["serverPort"] == 25566
    assert runtime_bytes(runtime) == before


def test_full_plugin_startup_also_refuses_default_health_cap(tmp_path, capsys):
    mod, runtime, _ = _write_startup_validation_fixture(tmp_path, full_plugin_set=True)
    (runtime / "spigot.yml").write_text(spigot("1024"), encoding="utf-8")
    before = runtime_bytes(runtime)

    with pytest.raises(ValueError, match="spigot|maxHealth|health"):
        startup(mod, tmp_path, True)

    assert capsys.readouterr().out == ""
    assert runtime_bytes(runtime) == before


def test_startup_requires_the_existing_safe_yaml_dependency(tmp_path, monkeypatch):
    import sys
    mod, runtime, _ = _write_startup_validation_fixture(tmp_path)
    (runtime / "spigot.yml").write_text(spigot("20000"), encoding="utf-8")
    before = runtime_bytes(runtime)
    monkeypatch.setitem(sys.modules, "yaml", None)

    with pytest.raises(ValueError, match="PyYAML"):
        startup(mod, tmp_path)

    assert runtime_bytes(runtime) == before


def test_fresh_preparation_seeds_only_the_required_spigot_health_setting(tmp_path):
    import yaml
    mod = load()
    write_bootstrap_fixture(tmp_path)

    runtime = mod.prepare_runtime(tmp_path)

    target = runtime / "spigot.yml"
    assert target.is_file(), "Fresh runtime inherited Spigot's insufficient default maxHealth"
    assert yaml.safe_load(target.read_text(encoding="utf-8")) == {
        "settings": {"attribute": {"maxHealth": {"max": 20000.0}}},
    }
    assert (runtime / "eula.txt").read_text(encoding="ascii") == "eula=false\n"


@pytest.mark.parametrize("existing", [None, spigot("1024"), spigot("35000")])
def test_preparation_preserves_existing_spigot_config_instead_of_rewriting_it(tmp_path, existing):
    mod = load()
    write_bootstrap_fixture(tmp_path)
    runtime = mod.prepare_runtime(tmp_path)
    target = runtime / "spigot.yml"
    if existing is None:
        target.unlink(missing_ok=True)
    else:
        target.write_text(existing, encoding="utf-8")
    before = runtime_bytes(runtime)

    assert mod.prepare_runtime(tmp_path) == runtime

    assert runtime_bytes(runtime) == before
