"""No End Rift Java test may sit on disk unexecuted.

The gate compiles a source bundle and then runs a hand-written list of test
class names. A test that is never added to that list is dead weight: it reports
nothing, and a regression it was written to catch goes unnoticed. That is not a
hypothetical — thirty-one End Rift tests were found in exactly that state, among
them the boss group-aggro, fireball, Wave 7 trial-target and Echo scaling
suites.

A test is allowed to stay out of the gate only when it cannot be compiled into
the gate bundle, and that has to be stated with the reason, so removing a test
from the list is a decision someone wrote down instead of an accident.
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GATE = ROOT / "tests" / "RunEndRiftEventChecks.ps1"

# Tests that reach outside the gate bundle. Every entry must say why, and must
# name the very package that is missing from the bundle.
NOT_IN_GATE_BUNDLE = {
    "AngelWingsFlightPolicyTest": "me.copimine.artifacts",
    "ArtifactCatalogMigrationPolicyTest": "me.copimine.artifacts",
    "ArtifactDatabaseBootstrapLifecycleTest": "me.copimine.artifacts",
    "ArtifactEconomyStartupReadinessGateTest": "me.copimine.artifacts",
    "ArtifactNewItemsPolicyTest": "me.copimine.artifacts",
    "ArtifactRevenuePayoutPolicyTest": "me.copimine.artifacts",
    "ArtifactRewardServiceContractTest": "me.copimine.artifacts.api",
    "ArtifactShopPurchaseRecoveryGuardTest": "me.copimine.artifacts",
    "AuthenticationHookReadinessPolicyTest": "me.serverrp.autheffects",
    "ClientVisualAckStatusPolicyTest": "me.copimine.clientbridge",
    "CombatArtifactMathTest": "me.copimine.artifacts",
    "CombatArtifactShotPolicyTest": "me.copimine.artifacts",
    "DepositJournalTest": "org.bukkit",
    "EncounterResourceScopeTest": "org.bukkit",
    "EndRiftEchoSharedRegionTest": "package me.copimine.endevent.domain;",
    "EventLayoutStoreTest": "me.copimine.endevent.EventLayoutStore",
    "EventRewardIdempotencyPolicyTest": "me.copimine.artifacts.domain",
    "EventStateStoreTest": "me.copimine.endevent.EventStateStore",
    "EventTaskRegistryTest": "org.bukkit.scheduler",
    "GravediggerContractCapturePolicyTest": "me.copimine.artifacts",
    "HazardMutationJournalTest": "me.copimine.endevent.HazardMutationJournal",
    "LegacyEndRiftSnapshotDecoderTest": "org.bukkit.configuration.file",
    "MinibossGroupSpellPolicyTest": "package me.copimine.endevent.domain;",
    "NarcoticRecipeBookConfigTest": "me.copimine.artifacts",
    "NarcoticRecipeBookDataTest": "me.copimine.artifacts",
    "NarcoticSigningKeyringTest": "package me.copimine.narcotics.item;",
    "NightCloakPolicyTest": "me.copimine.artifacts",
    "OrphanShopTransferRecoveryStateTest": "me.copimine.artifacts",
    "OrphanTransferReconciliationRunGateTest": "me.copimine.artifacts",
    "PostWaveRecoveryServiceTest": "org.bukkit",
    "RepairKitMathTest": "me.copimine.artifacts",
    "SpiderEffectLeasePolicyTest": "package me.copimine.endevent.domain;",
    "VeinMinerPolicyTest": "me.copimine.artifacts",
    "Wave7ReturnLifecycleTest": "me.copimine.endevent.runtime.AttemptLifecycleController",
    "WorldAccessServiceContractTest": "me.copimine.worldcore.api",
    "BoundedBridgeUtfReaderTest": "package me.copimine.endevent;",
    "CombatSequenceTest": "package me.copimine.endevent.runtime;",
    "EchoLimboThresholdTest": "package me.copimine.endevent.domain.wave7;",
    "EchoRealmAssignmentPolicyTest": "package me.copimine.endevent.domain.wave7;",
    "WardenSlamTriggerPolicyTest": "package me.copimine.endevent.domain;",
    "WaveSixDurableStateTest": "package me.copimine.endevent.domain;",
    "WaveTwoStagePolicyTest": "package me.copimine.endevent.domain;",
    "WaveTwoOwnershipPolicyTest": "package me.copimine.endevent.domain;",
}


def _registered_tests() -> set[str]:
    script = GATE.read_text(encoding="utf-8")
    start = script.index("$pureTests = @(")
    end = script.index("\n)", start)
    return set(re.findall(r"'([A-Za-z0-9_]+)'", script[start:end]))


def _test_files() -> dict[str, Path]:
    return {path.stem: path for path in (ROOT / "tests").glob("*Test.java")}


def test_every_end_rift_java_test_is_run_by_the_gate() -> None:
    registered = _registered_tests()
    files = _test_files()
    unexecuted = sorted(
        name
        for name in files
        if name not in registered and name not in NOT_IN_GATE_BUNDLE
    )
    assert not unexecuted, (
        "these Java tests exist but the gate never runs them: "
        + ", ".join(unexecuted)
        + ". Register them in tests/RunEndRiftEventChecks.ps1, or add them to "
        "NOT_IN_GATE_BUNDLE with the package that keeps them out."
    )


def test_the_exemptions_still_name_a_real_reason() -> None:
    files = _test_files()
    registered = _registered_tests()
    problems = []
    for name, required_package in NOT_IN_GATE_BUNDLE.items():
        if name not in files:
            problems.append(f"{name}: exempted but no longer on disk")
            continue
        if name in registered:
            problems.append(f"{name}: exempted but already registered")
            continue
        source = files[name].read_text(encoding="utf-8")
        if required_package not in source:
            problems.append(
                f"{name}: exempted for {required_package}, which it no longer uses"
            )
    assert not problems, "stale gate exemptions: " + "; ".join(problems)


def test_the_gate_never_lists_a_test_that_is_missing() -> None:
    files = _test_files()
    missing = sorted(name for name in _registered_tests() if name not in files)
    assert not missing, "the gate runs tests that do not exist: " + ", ".join(missing)


def test_the_gate_resolves_package_qualified_test_names() -> None:
    # A test with a package declaration cannot be started by simple name; the
    # runner reads the declaration instead of failing on a ClassNotFoundException.
    script = GATE.read_text(encoding="utf-8")
    assert "qualifiedName" in script, (
        "the gate must resolve a test's package before running it"
    )
