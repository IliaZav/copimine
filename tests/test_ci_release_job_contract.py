import re
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "ci.yml"
SHARED_GATE = ROOT / "scripts" / "minecraft" / "RunJavaPluginCi.ps1"


def _mask_powershell_non_code(script: str) -> str:
    masked = list(script)
    state = "code"
    block_depth = 0

    def mask(start: int, end: int) -> None:
        for index in range(start, end):
            if script[index] not in "\r\n":
                masked[index] = " "

    index = 0
    while index < len(script):
        char = script[index]
        next_pair = script[index : index + 2]

        if state == "block-comment":
            if next_pair == "<#":
                block_depth += 1
                mask(index, index + 2)
                index += 2
                continue
            if next_pair == "#>":
                block_depth -= 1
                mask(index, index + 2)
                index += 2
                if block_depth == 0:
                    state = "code"
                continue
            mask(index, index + 1)
        elif state == "line-comment":
            if char in "\r\n":
                state = "code"
            else:
                mask(index, index + 1)
        elif state in ("single-string", "double-string"):
            if char not in "\r\n":
                mask(index, index + 1)
            if state == "single-string" and char == "'":
                if next_pair == "''":
                    mask(index, index + 2)
                    index += 2
                    continue
                state = "code"
            elif state == "double-string" and char == "`":
                if index + 1 < len(script):
                    mask(index + 1, index + 2)
                    index += 2
                    continue
            elif state == "double-string" and char == '"':
                state = "code"
        elif state in ("single-here-string", "double-here-string"):
            terminator = "'@" if state == "single-here-string" else '"@'
            at_line_start = index == 0 or script[index - 1] == "\n"
            terminator_end = index + len(terminator)
            if (
                at_line_start
                and script.startswith(terminator, index)
                and (terminator_end == len(script) or script[terminator_end] in "\r\n")
            ):
                mask(index, terminator_end)
                index = terminator_end
                state = "code"
                continue
            mask(index, index + 1)
        elif next_pair == "<#":
            state = "block-comment"
            block_depth = 1
            mask(index, index + 2)
            index += 2
            continue
        elif char == "#":
            state = "line-comment"
            mask(index, index + 1)
        elif next_pair in ("@'", '@"') and index + 2 < len(script) and script[index + 2] in "\r\n":
            state = "single-here-string" if next_pair == "@'" else "double-here-string"
            mask(index, index + 2)
            index += 2
            continue
        elif char == "'":
            state = "single-string"
            mask(index, index + 1)
        elif char == '"':
            state = "double-string"
            mask(index, index + 1)

        index += 1

    return "".join(masked)


def _mask_powershell_constant_false_branches(script: str) -> str:
    masked = list(script)
    branch_pattern = re.compile(r"(?i)\bif\s*\(\s*\$false\s*\)\s*\{")
    search_from = 0
    while match := branch_pattern.search(script, search_from):
        depth = 0
        closing_brace = None
        for index in range(match.end() - 1, len(script)):
            if script[index] == "{":
                depth += 1
            elif script[index] == "}":
                depth -= 1
                if depth == 0:
                    closing_brace = index
                    break
        if closing_brace is None:
            search_from = match.end()
            continue
        for index in range(match.start(), closing_brace + 1):
            if script[index] not in "\r\n":
                masked[index] = " "
        search_from = closing_brace + 1
    return "".join(masked)


def _yaml_script_invokes_native_acceptance(node) -> bool:
    invocation = r"& $pythonCommand .\scripts\minecraft\validate_native_acceptance.py --root $env:CI_PROJECT_DIR"
    if isinstance(node, yaml.ScalarNode):
        script = _mask_powershell_non_code(node.value)
        script = _mask_powershell_constant_false_branches(script)
        return any(
            line.strip() == invocation or line.strip().startswith(invocation + " ")
            for line in script.splitlines()
        )
    if isinstance(node, yaml.SequenceNode):
        return any(_yaml_script_invokes_native_acceptance(child) for child in node.value)
    return False


def _yaml_job_script_invokes_native_acceptance(node) -> bool:
    if not isinstance(node, yaml.MappingNode):
        return False
    return any(
        isinstance(key, yaml.ScalarNode)
        and key.value == "script"
        and _yaml_script_invokes_native_acceptance(value)
        for key, value in node.value
    )


def _native_acceptance_artifact_section(gitlab: str) -> str:
    document = yaml.compose(gitlab, Loader=yaml.BaseLoader)
    if not isinstance(document, yaml.MappingNode):
        return ""
    validation_job_index = next(
        (
            index
            for index, (key, value) in enumerate(document.value)
            if isinstance(key, yaml.ScalarNode)
            and key.value == "minecraft-26-3-migration"
            and _yaml_job_script_invokes_native_acceptance(value)
        ),
        None,
    )
    if validation_job_index is None:
        return ""
    validation_job_start = document.value[validation_job_index][0].start_mark.index
    validation_job_end = (
        document.value[validation_job_index + 1][0].start_mark.index
        if validation_job_index + 1 < len(document.value)
        else len(gitlab)
    )
    validation_job_tail = gitlab[validation_job_start:validation_job_end]
    artifacts_header = re.search(r"(?m)^  artifacts:\s*$", validation_job_tail)
    if not artifacts_header:
        return ""
    return validation_job_tail[artifacts_header.start() :]


def test_java_release_job_prepares_python_and_asserts_resource_pack_artifact() -> None:
    text = WORKFLOW.read_text(encoding="utf-8")
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")
    java_job = text.split("\n  java-plugins:\n", 1)[1]

    setup = "uses: actions/setup-python@v5"
    install = "python -m pip install --disable-pip-version-check 'Pillow==12.3.0'"
    build = "build-resourcepack.ps1"
    archive_assertion = "Resource pack archive was not created"
    sidecar_assertion = "Resource-pack digest sidecar was not created"
    gate_invocation = "RunJavaPluginCi.ps1"

    assert setup in java_job
    assert install in java_job
    assert gate_invocation in java_job
    assert build in shared_gate
    assert archive_assertion in shared_gate
    assert sidecar_assertion in shared_gate
    assert java_job.index(setup) < java_job.index(gate_invocation)
    assert java_job.index(install) < java_job.index(gate_invocation)
    assert shared_gate.index(build) < shared_gate.index(archive_assertion)
    assert shared_gate.index(archive_assertion) < shared_gate.index("RunCopiMineValidators.ps1")


def test_ci_restores_build_outputs_after_the_shared_java_gate() -> None:
    github = WORKFLOW.read_text(encoding="utf-8")
    gitlab = (ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8")
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")

    assert "[switch] $KeepBuildOutputs" in shared_gate
    assert "New-MigrationOutputSnapshot" in shared_gate
    assert "Restore-MigrationOutputSnapshot" in shared_gate
    assert "-KeepBuildOutputs" not in github
    assert "-KeepBuildOutputs" not in gitlab


def test_plugin_build_scripts_disable_javac_annotation_processing() -> None:
    scripts = (
        "copimine-world-core/build-plugin.ps1",
        "copimine-artifacts/build-plugin.ps1",
        "copimine-end-event/build-plugin.ps1",
        "copimine-economy-core/build-plugin.ps1",
        "copimine-election-core/build-plugin.ps1",
        "copimine-narcotics/build-plugin.ps1",
        "copimine-admin-plugin/build-plugin.ps1",
        "minecraft/server/plugins/AuthEffects/build-plugin.ps1",
    )

    for relative in scripts:
        assert "-proc:none" in (ROOT / relative).read_text(encoding="utf-8"), relative


def test_plugin_build_scripts_find_paper_api_in_the_selected_maven_repository() -> None:
    scripts = (
        "copimine-world-core/build-plugin.ps1",
        "copimine-artifacts/build-plugin.ps1",
        "copimine-end-event/build-plugin.ps1",
        "copimine-economy-core/build-plugin.ps1",
        "copimine-election-core/build-plugin.ps1",
        "copimine-narcotics/build-plugin.ps1",
        "copimine-admin-plugin/build-plugin.ps1",
        "minecraft/server/plugins/AuthEffects/build-plugin.ps1",
    )

    for relative in scripts:
        source = (ROOT / relative).read_text(encoding="utf-8")
        repo_override = source.index("$mavenRepo = if ($env:COPIMINE_MAVEN_REPOSITORY)")
        paper_lookup = source.index("paper-api-*-R0.1-SNAPSHOT.jar")

        assert repo_override < paper_lookup, relative
        assert re.search(r"Get-ChildItem\s+(?:-Path\s+|-LiteralPath\s+)?\$mavenRepo", source), relative


def test_local_java_gate_compiles_and_runs_client_visual_ack_status_policy_test() -> None:
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")

    test_section = shared_gate.split("$ackTestSource = Join-Path $repo 'tests/ClientVisualAckStatusPolicyTest.java'", 1)[1]
    test_section = test_section.split("$poseGeneratorCheck =", 1)[0]

    assert "& $javac" in test_section
    assert "& $java" in test_section
    assert "ClientVisualAckStatusPolicyTest" in test_section
    assert "Client visual ACK status policy test failed." in test_section


def test_github_and_gitlab_require_signed_evidence_when_migration_locks_are_promoted() -> None:
    github = WORKFLOW.read_text(encoding="utf-8")
    gitlab = (ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8")
    guard_flag = "--require-evidence-for-accepted-locks"

    assert "Require evidence when migration locks claim acceptance" in github
    assert guard_flag in github
    github_workflow = yaml.load(github, Loader=yaml.BaseLoader)
    github_steps = github_workflow["jobs"]["static-and-contract"]["steps"]
    evidence_guard = next(step for step in github_steps if step.get("name") == "Require evidence when migration locks claim acceptance")
    assert "if" not in evidence_guard
    assert "refs/heads/codex/minecraft-26-3-migration" not in github
    assert "if ($migrationAcceptanceRef)" not in gitlab
    assert "validate_native_acceptance.py --root $env:CI_PROJECT_DIR --require-evidence-for-accepted-locks" in gitlab
    assert guard_flag in gitlab
    assert "Skipping native acceptance validation" not in gitlab


def test_local_java_gate_snapshots_generated_client_assets_and_isolates_gradle_home() -> None:
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")
    for relative in (
        "CopiMineClient/src/main/resources/assets/copimineclient/geometry/end_rift_tentacle.json",
        "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_tentacle_hd.png",
        "resourcepacks/src/assets/copimine/textures/item/end_event_rift_tentacle_hd.png",
        "resourcepacks/src/assets/copimine/models/item/end_event_rift_tentacle.json",
        "minecraft/server/server.properties",
        "thirdparty/client-mods/CopiMineClient-0.1.1.jar",
        "thirdparty/thirdparty_manifest.json",
        "thirdparty/checksums.txt",
        "thirdparty/CopiMineMods.zip",
        "thirdparty/CopiMineMods.sha1",
        "thirdparty/CopiMineMods.sha256",
        "thirdparty/_modpack_stage",
        "admin-web/frontend/assets/public-data/modpack_snapshot.json",
        "CopiMineClient/.gradle",
        "tests/build",
    ):
        assert relative in shared_gate
    assert "GRADLE_USER_HOME" in shared_gate
    assert "$tempName)" in shared_gate
    assert "PYTHONDONTWRITEBYTECODE" in shared_gate
    assert "no:cacheprovider" in shared_gate


def test_local_java_gate_stops_its_isolated_gradle_daemon_before_restoring_outputs() -> None:
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")

    stop_call = "Stop-MigrationGradleDaemon -GradleHome $isolatedGradleHome"
    restore_call = "Restore-MigrationOutputSnapshot -Snapshot $outputSnapshot"

    assert "function Stop-MigrationGradleDaemon" in shared_gate
    assert stop_call in shared_gate
    assert shared_gate.index(stop_call) < shared_gate.index(restore_call)
    assert "--stop" in shared_gate


def test_local_java_gate_recovers_interrupted_snapshots_and_bounds_temp_retention() -> None:
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")

    recovery = "Read-MigrationOutputSnapshot -ManifestPath $previousManifest -WorkspaceRoot $expectedWorkspace"
    new_snapshot = "New-MigrationOutputSnapshot -WorkspaceRoot $repo"

    assert "recoveryRecordPath" in shared_gate
    assert recovery in shared_gate
    assert shared_gate.index(recovery) < shared_gate.index(new_snapshot)
    assert "Write-MigrationRecoveryRecord" in shared_gate
    assert "Remove-MigrationTemporaryRoot" in shared_gate
    assert "TemporaryBase = [IO.Path]::GetFullPath($TemporaryBase)" in shared_gate


def test_local_java_gate_locks_per_workspace_before_recovery_and_keeps_state_outside_temp_directory() -> None:
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")

    lock_open = "[IO.File]::Open($gateLockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)"
    recovery_read = "Read-MigrationRecoveryRecord -RecoveryRecordPath $recoveryRecordPath"

    assert "$recoveryStateDirectory = Join-Path $recoveryBase" in shared_gate
    assert "$recoveryRecordPath = Join-Path $recoveryStateDirectory" in shared_gate
    assert "$gateLockPath = Join-Path $recoveryStateDirectory" in shared_gate
    assert lock_open in shared_gate
    assert shared_gate.index(lock_open) < shared_gate.index("$workspaceRevision = Get-MigrationWorkspaceRevision")
    assert shared_gate.index("$workspaceRevision = Get-MigrationWorkspaceRevision") < shared_gate.index(recovery_read)
    assert "Another Java plugin CI gate is already active for this workspace" in shared_gate
    assert "if ($gateLockStream) { $gateLockStream.Dispose() }" in shared_gate


def test_local_java_gate_pins_recovery_java_and_restores_process_environment() -> None:
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")
    recovery_read = "Read-MigrationRecoveryRecord -RecoveryRecordPath $recoveryRecordPath"

    assert "$value = [Environment]::GetEnvironmentVariable($name, [EnvironmentVariableTarget]::Process)" in shared_gate
    assert "$wasSet = $null -ne $value" in shared_gate
    assert "$originalProcessEnvironment.Contains($name)" not in shared_gate
    assert shared_gate.index("$env:JAVA_HOME = $jdk") < shared_gate.index(recovery_read)
    assert shared_gate.index("$env:PATH = (Join-Path $jdk 'bin')") < shared_gate.index(recovery_read)
    for name in (
        "JAVA_HOME",
        "PATH",
        "GRADLE_USER_HOME",
        "PAPER_PLACEHOLDER_API_JAR",
        "PAPER_VOICECHAT_API_JAR",
        "COPIMINE_MAVEN_REPOSITORY",
        "PAPER_COMPILE_DEPS",
        "PAPER_API_JAR",
        "PYTHONDONTWRITEBYTECODE",
        "PYTEST_ADDOPTS",
    ):
        assert f"'{name}'" in shared_gate
    assert "[Environment]::SetEnvironmentVariable($name, $snapshot.Value, [EnvironmentVariableTarget]::Process)" in shared_gate
    assert "[Environment]::SetEnvironmentVariable($name, $null, [EnvironmentVariableTarget]::Process)" in shared_gate


def test_local_java_gate_binds_recovery_to_head_and_withholds_restore_after_revision_change() -> None:
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")

    assert "function Get-MigrationWorkspaceRevision" in shared_gate
    assert "-WorkspaceRevision $workspaceRevision" in shared_gate
    assert "WorkspaceRevision = $WorkspaceRevision" in shared_gate
    assert "automatic restoration was withheld" in shared_gate
    assert "output restoration was withheld and recovery data was preserved" in shared_gate
    assert "Workspace outputs had changed since the interrupted gate snapshot" in shared_gate
    archive = "Preserve-MigrationOutputStateBeforeRecovery -WorkspaceRoot $repo"
    restore = "Restore-MigrationOutputSnapshot -Snapshot $previousRecovery.Snapshot"
    assert shared_gate.index("Test-MigrationOutputSnapshotMatchesWorkspace -Snapshot $previousRecovery.Snapshot") < shared_gate.index(archive)
    assert shared_gate.index(archive) < shared_gate.index(restore)


def test_end_rift_gate_runs_in_a_child_process_and_captures_its_exit_output() -> None:
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")

    assert "RunEndRiftEventChecks.ps1" in shared_gate
    assert "-RedirectStandardOutput $eventStdoutLog -RedirectStandardError $eventStderrLog" in shared_gate
    assert "$eventProcessHandle = $eventProcess.Handle" in shared_gate
    assert "$eventProcess.Refresh()" in shared_gate
    assert "$childExitCode = $eventProcess.ExitCode" in shared_gate
    assert "if ($null -eq $childExitCode) { throw 'End Rift runner exited without a readable process code.' }" in shared_gate
    assert "$completedMarker" not in shared_gate
    assert "Preserving failed gate inputs and diagnostics" in shared_gate
    assert "GenerateBossAnimationPoses.ps1'" in shared_gate
    assert "build_modpack.ps1'" in shared_gate
    assert "copimine-end-event/build-plugin.ps1'" in shared_gate


def test_voicechat_lock_filename_is_confined_to_the_temporary_directory() -> None:
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")

    assert "[IO.Path]::GetFileName($voicechatFilename)" in shared_gate
    assert "-StopAt $tempRoot" in shared_gate


def test_gitlab_release_job_matches_python_and_source_provenance_gates() -> None:
    gitlab = (ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8")
    shared_gate = SHARED_GATE.read_text(encoding="utf-8")

    source_sha_check = "git rev-parse HEAD"
    clean_checkout_check = "git status --porcelain --untracked-files=all"
    source_sha_guard = "if ($LASTEXITCODE -ne 0 -or $checkoutSha -ne $env:CI_COMMIT_SHA)"
    dirty_checkout_guard = "if ($initialStatus.Count -ne 0)"
    candidate_build = "PrepareMigrationCandidates.ps1"

    assert source_sha_check in gitlab
    assert clean_checkout_check in gitlab
    assert source_sha_guard in gitlab
    assert dirty_checkout_guard in gitlab
    assert "GitLab source checkout is not clean before release artifact preparation." in gitlab
    assert gitlab.index(source_sha_check) < gitlab.index(candidate_build)
    assert gitlab.index(clean_checkout_check) < gitlab.index(candidate_build)
    assert gitlab.index(clean_checkout_check) < gitlab.index("build-resourcepack.ps1")
    assert "$pythonCommand -m compileall -q admin-web/backend" in gitlab
    assert "RunJavaPluginCi.ps1" in gitlab
    assert "RunCopiMineValidators.ps1" not in gitlab
    assert "RunCopiMineValidators.ps1" in shared_gate


def test_gitlab_migration_toolchains_and_temp_outputs_stay_outside_checkout() -> None:
    gitlab = (ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8")

    assert "$ciTemporaryDirectory = Join-Path ([IO.Path]::GetTempPath())" in gitlab
    assert "$ciTemporaryDirectoryFull = [IO.Path]::GetFullPath($ciTemporaryDirectory)" in gitlab
    assert "$projectRootFull = [IO.Path]::GetFullPath($env:CI_PROJECT_DIR)" in gitlab
    assert "$projectRootPrefix = $projectRootFull + [IO.Path]::DirectorySeparatorChar" in gitlab
    assert "CI temporary directory must be outside the source checkout." in gitlab
    assert 'Join-Path $ciTemporaryDirectory "python-3.13.16"' in gitlab
    assert 'Join-Path $ciTemporaryDirectory ".ci-temp' not in gitlab
    assert "-TemporaryDirectory $ciTemporaryDirectory" in gitlab


def test_gitlab_release_job_checks_for_build_side_effects_after_candidate_builds() -> None:
    gitlab = (ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8")

    final_status_check = "$postBuildStatus = @(git status --porcelain --untracked-files=all)"
    final_status_guard = "if ($LASTEXITCODE -ne 0) { throw 'GitLab post-build source checkout status could not be verified.' }"
    final_dirty_guard = "if ($unexpectedPostBuildStatus.Count -ne 0)"
    java_plugin_gate = "RunJavaPluginCi.ps1"
    expected_generated_paths = (
        "^ M thirdparty/grim-creative-patch/build/GrimAC-2\\.3\\.74-40684fb-creative-fix\\.jar$",
        "^\\?\\? test-results\\.xml$",
        "^\\?\\? ci-verified-native-acceptance\\.zip$",
        "^\\?\\? artifacts/end-rift-diagnostics/",
    )

    migration_job = gitlab[gitlab.index("minecraft-26-3-migration:"):]
    assert final_status_check in migration_job
    assert final_status_guard in migration_job
    assert final_dirty_guard in migration_job
    assert migration_job.index(final_status_check) > migration_job.index(java_plugin_gate)
    assert migration_job.index(final_status_check) < migration_job.index("  artifacts:")
    for expected_path in expected_generated_paths:
        assert expected_path in migration_job


def test_github_checkout_gate_fails_when_git_status_cannot_be_read() -> None:
    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")

    status_check = "$status = @(git status --porcelain)"
    status_guard = "if ($LASTEXITCODE -ne 0) { throw 'GitHub source checkout status could not be verified.' }"
    unexpected_changes_check = "$unexpected = @($status | Where-Object {"

    assert status_check in workflow
    assert status_guard in workflow
    assert unexpected_changes_check in workflow
    assert workflow.index(status_check) < workflow.index(status_guard)
    assert workflow.index(status_guard) < workflow.index(unexpected_changes_check)


def test_gitlab_migration_job_requires_trusted_acceptance_key_before_candidate_builds() -> None:
    gitlab = (ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8")
    signing_docs = (ROOT / "docs/minecraft-26.3/NATIVE_ACCEPTANCE_SIGNING.md").read_text(encoding="utf-8")

    trust_key_guard = "$env:MIGRATION_ACCEPTANCE_PUBLIC_KEY -cnotmatch '^[0-9a-f]{64}$'"
    candidate_build = "PrepareMigrationCandidates.ps1"
    acceptance_validation = "validate_native_acceptance.py --root $env:CI_PROJECT_DIR --expected-source-commit"

    assert trust_key_guard in gitlab
    assert "Configure MIGRATION_ACCEPTANCE_PUBLIC_KEY as a GitLab project or group CI/CD variable" in gitlab
    assert gitlab.index(trust_key_guard) < gitlab.index(candidate_build)
    assert gitlab.index(candidate_build) < gitlab.index(acceptance_validation)
    assert "ci_pipeline_variables_minimum_override_role=no_one_allowed" in signing_docs


def test_native_acceptance_gate_follows_evidence_on_every_ref() -> None:
    github = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    gitlab = (ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8")

    github_workflow = yaml.load(github, Loader=yaml.BaseLoader)
    github_steps = github_workflow["jobs"]["static-and-contract"]["steps"]
    steps_by_name = {step.get("name"): step for step in github_steps}
    assert "if" not in steps_by_name["Require evidence when migration locks claim acceptance"]
    expected_evidence_condition = "hashFiles('artifacts/minecraft-26.3/native-acceptance/**') != ''"
    assert steps_by_name["Verify migration acceptance source provenance"]["if"].strip() == expected_evidence_condition
    assert steps_by_name["Require native Minecraft 26.3 acceptance evidence"]["if"].strip() == expected_evidence_condition
    assert steps_by_name["Upload verified Minecraft 26.3 acceptance evidence"]["if"].strip() == (
        "success() && " + expected_evidence_condition
    )

    gitlab_job = yaml.load(gitlab, Loader=yaml.BaseLoader)["minecraft-26-3-migration"]
    gitlab_script = gitlab_job["script"][0]
    assert "$migrationAcceptanceRef" not in gitlab_script
    assert "$migrationAcceptanceRequired = $nativeAcceptanceFiles.Count -gt 0" in gitlab_script
    assert "$nativeAcceptanceDirectory" in gitlab_script
    assert "$nativeAcceptanceFiles" in gitlab_script
    assert "Get-ChildItem -LiteralPath $nativeAcceptanceDirectory -File -Recurse" in gitlab_script
    assert "$nativeAcceptanceFiles.Count -gt 0" in gitlab_script
    assert "if ($migrationAcceptanceRequired) {" in gitlab_script
    assert "$pythonCommand .\\scripts\\minecraft\\validate_native_acceptance.py --root $env:CI_PROJECT_DIR --require-evidence-for-accepted-locks" in gitlab_script
    assert gitlab_script.index("if ($migrationAcceptanceRequired) {") < gitlab_script.index(
        "validate_native_acceptance.py --root $env:CI_PROJECT_DIR"
    )


def test_gitlab_uses_source_branch_pipeline_for_migration_candidates() -> None:
    gitlab = yaml.load((ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
    rules = gitlab["workflow"]["rules"]

    assert [rule.get("if") for rule in rules if "if" in rule] == [
        '$CI_PIPELINE_SOURCE == "merge_request_event" && $CI_MERGE_REQUEST_SOURCE_BRANCH_NAME =~ /^codex\\/minecraft-26-3-migration(-candidate-[0-9]{8})?$/',
        '$CI_COMMIT_BRANCH =~ /^codex\\/minecraft-26-3-migration(-candidate-[0-9]{8})?$/',
        '$CI_PIPELINE_SOURCE == "merge_request_event"',
        "$CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS",
        "$CI_COMMIT_BRANCH",
        "$CI_COMMIT_TAG",
    ]
    assert rules[0]["when"] == "never"
    assert rules[3]["when"] == "never"
    assert rules[-1] == {"when": "never"}


def test_github_acceptance_guard_triggers_for_all_push_and_pull_request_refs() -> None:
    github = yaml.load((ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
    triggers = github["on"]

    assert triggers["push"] == ""
    assert triggers["pull_request"] == ""


def test_native_acceptance_is_bound_to_the_exact_source_parent_and_ci_head():
    github = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    gitlab = (ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8")

    assert "$sha -ne $env:MIGRATION_CI_HEAD_SHA" in github
    assert "$checkoutSha -ne $env:CI_COMMIT_SHA" in gitlab
    for config in (github, gitlab):
        assert "git rev-list --parents -n 1" in config
        assert "git diff --quiet $migrationSourceCommit $checkoutSha --" in config or \
            "git diff --quiet $migrationSourceCommit $sha --" in config
        assert "artifacts/minecraft-26.3/native-acceptance" in config
        if config is gitlab:
            assert "--expected-source-commit $migrationSourceCommit" in config
    assert "--expected-source-commit $env:MIGRATION_SOURCE_COMMIT" in github
    assert '"MIGRATION_SOURCE_COMMIT=$migrationSourceCommit"' in github


def test_native_acceptance_artifacts_are_published_only_after_validation():
    github = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    gitlab = (ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8")

    diagnostics_step = github.split("- name: Upload CI diagnostics", 1)[1].split("- name:", 1)[0]
    accepted_step = yaml.load(github, Loader=yaml.BaseLoader)["jobs"]["static-and-contract"]["steps"]
    accepted_step = next(
        step for step in accepted_step if step.get("name") == "Upload verified Minecraft 26.3 acceptance evidence"
    )
    assert "if: always()" in diagnostics_step
    assert "native-acceptance/" not in diagnostics_step
    assert accepted_step["if"].strip().startswith("success() &&")
    assert "--verified-output $verifiedAcceptanceArchive" in github.split(
        "- name: Require native Minecraft 26.3 acceptance evidence", 1
    )[1].split("- name:", 1)[0]
    assert accepted_step["with"]["path"] == "ci-verified-native-acceptance.zip"
    assert accepted_step["with"]["if-no-files-found"] == "error"

    validation_position = gitlab.index("validate_native_acceptance.py --root $env:CI_PROJECT_DIR")
    assert "Join-Path $env:CI_PROJECT_DIR 'ci-verified-native-acceptance.zip'" in gitlab
    assert "--verified-output $verifiedAcceptanceArchive" in gitlab[validation_position:]
    archive_guard = (
        "if (-not (Test-Path -LiteralPath $verifiedAcceptanceArchive -PathType Leaf)) {\n"
        "          throw 'Native acceptance validation succeeded without creating its required CI archive.'\n"
        "        }"
    )
    assert archive_guard in gitlab[validation_position:]
    assert "Get-ChildItem -LiteralPath $nativeAcceptanceSource" not in gitlab
    artifact_section = _native_acceptance_artifact_section(gitlab)
    assert "when: on_success" in artifact_section
    assert "- ci-verified-native-acceptance.zip" in artifact_section
    assert "- artifacts/minecraft-26.3/native-acceptance/" not in artifact_section


def test_unrelated_gitlab_job_cannot_supply_native_acceptance_artifacts():
    gitlab = (ROOT / ".gitlab-ci.yml").read_text(encoding="utf-8")
    validation_position = gitlab.index("validate_native_acceptance.py --root $env:CI_PROJECT_DIR")
    native_artifacts_position = gitlab.index("\n  artifacts:", validation_position)
    gitlab_without_native_artifacts = gitlab[:native_artifacts_position]
    gitlab_with_unrelated_artifacts = gitlab_without_native_artifacts + (
        "\nunrelated-job:\n"
        "  stage: migration\n"
        "  script: ['true']\n"
        "  artifacts:\n"
        "    when: on_success\n"
        "    paths:\n"
        "      - ci-verified-native-acceptance.zip\n"
    )

    artifact_section = _native_acceptance_artifact_section(gitlab_with_unrelated_artifacts)
    assert artifact_section == ""


def test_hidden_validation_template_cannot_supply_migration_job_artifacts():
    invocation = r"& $pythonCommand .\scripts\minecraft\validate_native_acceptance.py --root $env:CI_PROJECT_DIR"
    gitlab = (
        "stages: [migration]\n"
        ".validation-template:\n"
        "  script:\n"
        f"    - '{invocation}'\n"
        "  artifacts:\n"
        "    paths:\n"
        "      - ci-verified-native-acceptance.zip\n"
        "minecraft-26-3-migration:\n"
        "  stage: migration\n"
        "  script: ['echo no validation']\n"
        "  artifacts:\n"
        "    paths:\n"
        "      - wrong-job-artifact.zip\n"
    )

    artifact_section = _native_acceptance_artifact_section(gitlab)
    assert artifact_section == ""


def test_validation_marker_in_job_variable_does_not_select_job_artifacts():
    marker = "validate_native_acceptance.py --root $env:CI_PROJECT_DIR"
    gitlab = (
        "stages: [migration]\n"
        "migration-job:\n"
        "  stage: migration\n"
        "  variables:\n"
        f"    UNUSED_VALIDATION_COMMAND: '{marker}'\n"
        "  script: ['echo not validating']\n"
        "  artifacts:\n"
        "    paths:\n"
        "      - ci-verified-native-acceptance.zip\n"
    )

    parsed_gitlab = yaml.load(gitlab, Loader=yaml.BaseLoader)
    assert parsed_gitlab["migration-job"]["script"] == ["echo not validating"]
    artifact_section = _native_acceptance_artifact_section(gitlab)
    assert artifact_section == ""


def test_echoed_validation_marker_does_not_select_job_artifacts():
    marker = "validate_native_acceptance.py --root $env:CI_PROJECT_DIR"
    gitlab = (
        "stages: [migration]\n"
        "migration-job:\n"
        "  stage: migration\n"
        f"  script: ['echo {marker}']\n"
        "  artifacts:\n"
        "    paths:\n"
        "      - ci-verified-native-acceptance.zip\n"
    )

    parsed_gitlab = yaml.load(gitlab, Loader=yaml.BaseLoader)
    assert parsed_gitlab["migration-job"]["script"] == [f"echo {marker}"]
    artifact_section = _native_acceptance_artifact_section(gitlab)
    assert artifact_section == ""


def test_powershell_block_comment_does_not_select_native_acceptance_artifacts():
    invocation = r"& $pythonCommand .\scripts\minecraft\validate_native_acceptance.py --root $env:CI_PROJECT_DIR"
    gitlab = (
        "stages: [migration]\n"
        "migration-job:\n"
        "  stage: migration\n"
        "  script: |\n"
        "    <#\n"
        f"    {invocation}\n"
        "    #>\n"
        "    Write-Host 'validation is commented out'\n"
        "  artifacts:\n"
        "    paths:\n"
        "      - ci-verified-native-acceptance.zip\n"
    )

    parsed_gitlab = yaml.load(gitlab, Loader=yaml.BaseLoader)
    assert invocation in parsed_gitlab["migration-job"]["script"]
    artifact_section = _native_acceptance_artifact_section(gitlab)
    assert artifact_section == ""


def test_powershell_here_string_does_not_select_native_acceptance_artifacts():
    invocation = r"& $pythonCommand .\scripts\minecraft\validate_native_acceptance.py --root $env:CI_PROJECT_DIR"
    gitlab = (
        "stages: [migration]\n"
        "migration-job:\n"
        "  stage: migration\n"
        "  script: |\n"
        "    $example = @'\n"
        f"    {invocation}\n"
        "    '@\n"
        "    Write-Host 'validation is only text'\n"
        "  artifacts:\n"
        "    paths:\n"
        "      - ci-verified-native-acceptance.zip\n"
    )

    parsed_gitlab = yaml.load(gitlab, Loader=yaml.BaseLoader)
    assert invocation in parsed_gitlab["migration-job"]["script"]
    artifact_section = _native_acceptance_artifact_section(gitlab)
    assert artifact_section == ""


def test_powershell_constant_false_branch_does_not_select_native_acceptance_artifacts():
    invocation = r"& $pythonCommand .\scripts\minecraft\validate_native_acceptance.py --root $env:CI_PROJECT_DIR"
    gitlab = (
        "stages: [migration]\n"
        "minecraft-26-3-migration:\n"
        "  stage: migration\n"
        "  script: |\n"
        "    if ($migrationAcceptanceRequired) {\n"
        "      if ($false) {\n"
        f"        {invocation}\n"
        "      }\n"
        "    }\n"
        "    Write-Host 'acceptance is not required'\n"
        "  artifacts:\n"
        "    paths:\n"
        "      - ci-verified-native-acceptance.zip\n"
    )

    artifact_section = _native_acceptance_artifact_section(gitlab)
    assert artifact_section == ""


def test_inline_gitlab_job_key_ends_native_acceptance_artifact_section():
    marker = r"& $pythonCommand .\scripts\minecraft\validate_native_acceptance.py --root $env:CI_PROJECT_DIR"
    gitlab_with_inline_job = (
        "stages: [migration]\n"
        "minecraft-26-3-migration:\n"
        "  stage: migration\n"
        "  script: [\n"
        f"    '{marker}',\n"
        '"echo: hi",\n'
        "  ]\n"
        "  artifacts:\n"
        "    paths:\n"
        "      - ci-verified-native-acceptance.zip\n"
        "inline-job: { stage: migration, script: ['true'] }\n"
    )

    parsed_gitlab = yaml.load(gitlab_with_inline_job, Loader=yaml.BaseLoader)
    assert parsed_gitlab["minecraft-26-3-migration"]["script"][1] == "echo: hi"
    artifact_section = _native_acceptance_artifact_section(gitlab_with_inline_job)
    assert "ci-verified-native-acceptance.zip" in artifact_section
    assert "inline-job:" not in artifact_section


def test_java_event_tests_bound_heap_and_compiler_threads() -> None:
    event_runner = (ROOT / "tests" / "RunEndRiftEventChecks.ps1").read_text(encoding="utf-8")

    assert "$javaTestJvmArgs = @('-Xms128m', '-Xmx1g', '-XX:ActiveProcessorCount=4')" in event_runner
    assert "& java @javaTestJvmArgs -cp $Classpath $MainClass" in event_runner
