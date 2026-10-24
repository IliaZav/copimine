import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATRIX = ROOT / "docs" / "end-rift" / "final-26.3" / "REQUIREMENT_TEST_MATRIX.json"
MATRIX_MD = ROOT / "docs" / "end-rift" / "final-26.3" / "REQUIREMENT_TEST_MATRIX.md"

EXPECTED_COUNTS = {
    "ADM": 4, "ART": 5, "ECO": 5, "E2E": 10, "G": 12, "INF": 10,
    "PERF": 7, "PLG": 10, "REC": 7, "SEC": 6, "SND": 10, "VIS": 6,
    "W1": 7, "W2": 9, "W3": 8, "W4": 7, "W5": 8, "W6": 24, "W7": 13,
}
ALLOWED_STATUSES = {"PASS", "FAIL", "BLOCKED", "NOT RUN", "N/A"}
LIVE_PREFIXES = {"E2E", "PERF"}


def load_matrix() -> dict:
    return json.loads(MATRIX.read_text(encoding="utf-8"))


def test_registry_has_exactly_168_unique_ids() -> None:
    matrix = load_matrix()
    entries = matrix["entries"]
    assert matrix["registryCount"] == 168
    assert len(entries) == 168
    assert len({entry["id"] for entry in entries}) == 168
    assert Counter(entry["id"].split("-")[0] for entry in entries) == EXPECTED_COUNTS

    for prefix, count in EXPECTED_COUNTS.items():
        ids = [entry["id"] for entry in entries if entry["id"].split("-")[0] == prefix]
        assert ids == [f"{prefix}-{index:02d}" for index in range(1, count + 1)]


def test_every_requirement_has_layer_status_tests_and_reason() -> None:
    matrix = load_matrix()
    for entry in matrix["entries"]:
        assert entry["requirement"].strip(), entry["id"]
        assert "###" not in entry["requirement"], entry["id"]
        assert entry["layers"], entry["id"]
        assert entry["status"] in ALLOWED_STATUSES, entry["id"]
        assert entry["tests"], entry["id"]
        for test_path in entry["tests"]:
            assert (ROOT / test_path).exists(), (entry["id"], test_path)
        if entry["status"] == "BLOCKED":
            assert len(entry["statusReason"]) >= 20, entry["id"]

    summary = Counter(entry["status"] for entry in matrix["entries"])
    assert dict(sorted(summary.items())) == matrix["summary"]


def test_no_live_or_native_evidence_is_claimed_as_pass() -> None:
    matrix = load_matrix()
    for entry in matrix["entries"]:
        prefix = entry["id"].split("-")[0]
        layers = set(entry["layers"])
        if prefix in LIVE_PREFIXES or "N" in layers or entry["id"] == "SND-10":
            assert entry["status"] != "PASS", entry["id"]
        if entry["status"] == "PASS":
            assert "E" not in layers and "R" not in layers, entry["id"]
            assert "P" not in layers, entry["id"]
            assert "CI" not in layers, entry["id"]


def test_markdown_mirror_lists_every_requirement() -> None:
    matrix = load_matrix()
    markdown = MATRIX_MD.read_text(encoding="utf-8")
    assert "168" in markdown
    rows = re.findall(r"^\| ([A-Z0-9]+-\d+) \|", markdown, re.M)
    assert len(rows) == 168
    assert rows == [entry["id"] for entry in matrix["entries"]]
    assert "Native visual/audio remains false." in markdown


def test_markdown_summary_and_statuses_match_json() -> None:
    matrix = load_matrix()
    markdown = MATRIX_MD.read_text(encoding="utf-8")
    summary = matrix["summary"]
    declared = re.search(
        r"Counts: BLOCKED=(\d+), NOT RUN=(\d+), PASS=(\d+)", markdown
    )
    assert declared is not None
    assert tuple(int(value) for value in declared.groups()) == (
        summary["BLOCKED"], summary["NOT RUN"], summary["PASS"]
    )
    rows = re.findall(r"^\| ([A-Z0-9]+-\d+) \| [^|]+ \| ([A-Z/ ]+) \|", markdown, re.M)
    assert dict(rows) == {entry["id"]: entry["status"] for entry in matrix["entries"]}
