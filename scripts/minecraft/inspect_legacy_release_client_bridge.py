from __future__ import annotations

import sys
from pathlib import Path

import yaml
from yaml.constructor import ConstructorError
from yaml.nodes import MappingNode


GUARDED_KEYS = {"client_bridge", "required_client_mod_version"}


class MigrationGuardLoader(yaml.SafeLoader):
    """Safe YAML loader that rejects duplicate migration guard keys."""

    def construct_mapping(self, node: MappingNode, deep: bool = False) -> dict[object, object]:
        seen_guarded_keys: set[str] = set()
        # Check only keys written in this mapping. Flattening first expands
        # inherited merge entries into node.value, where a deliberate explicit
        # override becomes indistinguishable from a duplicate source key.
        for key_node, _ in node.value:
            if key_node.tag == "tag:yaml.org,2002:merge":
                continue
            key = self.construct_object(key_node, deep=deep)
            if isinstance(key, str) and key in GUARDED_KEYS:
                if key in seen_guarded_keys:
                    raise ConstructorError(
                        "while inspecting the legacy client bridge configuration",
                        node.start_mark,
                        f"duplicate migration guard key {key!r}",
                        key_node.start_mark,
                    )
                seen_guarded_keys.add(key)
        self.flatten_mapping(node)
        return super().construct_mapping(node, deep=deep)


def inspect_config(config_path: Path) -> str:
    try:
        config = yaml.load(config_path.read_text(encoding="utf-8-sig"), Loader=MigrationGuardLoader)
    except Exception as error:
        raise ValueError(f"cannot safely parse YAML: {error}") from error

    if config is None:
        config = {}
    if not isinstance(config, dict):
        raise ValueError("the YAML root must be a mapping")
    if "client_bridge" not in config:
        return "legacy"

    client_bridge = config["client_bridge"]
    if not isinstance(client_bridge, dict):
        raise ValueError("client_bridge must be a mapping")
    if "required_client_mod_version" not in client_bridge:
        return "legacy"

    version = client_bridge["required_client_mod_version"]
    if not isinstance(version, str):
        raise ValueError("required_client_mod_version must be a string")
    return "requires-26.3" if "+26.3" in version else "legacy"


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: inspect_legacy_release_client_bridge.py CONFIG_PATH", file=sys.stderr)
        return 2

    try:
        state = inspect_config(Path(argv[1]))
    except (OSError, ValueError) as error:
        print(f"Legacy release client bridge inspection failed: {error}", file=sys.stderr)
        return 2

    print(state)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
