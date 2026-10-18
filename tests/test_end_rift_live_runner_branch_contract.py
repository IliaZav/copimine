import re
import shutil
import subprocess
import tempfile
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]
CURRENT_END_RIFT_BRANCH = "codex/end-rift-26-3-integrated-final"
REQUIRED_BRANCH_GUARDED_RUNNERS = {
    "RunEndRiftMobCombatLive.ps1",
    "RunEndRiftSpellMatrixLive.ps1",
}


def test_active_local_runners_default_to_the_current_end_rift_branch() -> None:
    expected = {
        "RunEndRiftMobCombatLive.ps1": CURRENT_END_RIFT_BRANCH,
        "RunEndRiftSpellMatrixLive.ps1": CURRENT_END_RIFT_BRANCH,
    }
    for name, branch in expected.items():
        source = (ROOT / "tests" / name).read_text(encoding="utf-8")
        match = re.search(r"\[string\]\s*\$ExpectedBranch\s*=\s*'([^']+)'", source)
        assert match is not None, f"{name} must define its expected branch"
        assert match.group(1) == branch, f"{name} must default to {branch}"


def test_wave_and_tentacle_live_runners_allow_the_integration_branch() -> None:
    for name in ("RunEndRiftRuneWaitMusicLive.ps1", "RunEndRiftTentacleLive.ps1"):
        source = (ROOT / "tests" / name).read_text(encoding="utf-8")
        assert "codex/end-rift-26-3-integrated-final" in source, (
            f"{name} must run on the authorized integration branch"
        )


def _run_official_scaled_wrapper(
    wrapper_name: str, branch: str | None, environment: str = "local"
) -> subprocess.CompletedProcess[str]:
    source_tests = ROOT / "tests"
    wrapper_source = source_tests / wrapper_name
    with tempfile.TemporaryDirectory(prefix="end-rift-scaled-runner-") as temporary:
        fixture_root = Path(temporary) / "fixture-repo"
        fixture_tests = fixture_root / "tests"
        event_dir = fixture_root / "copimine-end-event"
        fixture_tests.mkdir(parents=True)
        event_dir.mkdir(parents=True)
        initial_branch = branch or "codex/detached-probe"
        subprocess.run(
            ["git", "init", "--quiet", f"--initial-branch={initial_branch}", str(fixture_root)],
            check=True,
            capture_output=True,
            text=True,
        )
        if branch is None:
            subprocess.run(
                ["git", "-C", str(fixture_root), "config", "user.email", "probe@example.invalid"],
                check=True,
                capture_output=True,
                text=True,
            )
            subprocess.run(
                ["git", "-C", str(fixture_root), "config", "user.name", "End Rift Probe"],
                check=True,
                capture_output=True,
                text=True,
            )
            subprocess.run(
                ["git", "-C", str(fixture_root), "commit", "--quiet", "--allow-empty", "-m", "fixture"],
                check=True,
                capture_output=True,
                text=True,
            )
            subprocess.run(
                ["git", "-C", str(fixture_root), "checkout", "--quiet", "--detach", "HEAD"],
                check=True,
                capture_output=True,
                text=True,
            )
        wrapper = fixture_tests / wrapper_name
        wrapper.write_bytes(wrapper_source.read_bytes())
        (event_dir / "config.yml").write_text(f"environment: {environment}\n", encoding="utf-8")
        (fixture_tests / "RunEndRiftOfficialTwoPlayerLive.ps1").write_text(
            "Write-Output 'OFFICIAL_SHARED_DRIVER_REACHED'\n", encoding="utf-8"
        )

        script = f"""
$ErrorActionPreference = 'Stop'
try {{
  & {_ps_quote(wrapper)}
  [Console]::Out.WriteLine('WRAPPER_RETURNED')
  exit 0
}} catch {{
  [Console]::Out.WriteLine("RUNNER_ERROR:$($_.Exception.Message)")
  exit 10
}}
"""
        return subprocess.run(
            [
                _powershell_executable(),
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script,
            ],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )


@pytest.mark.parametrize(
    "wrapper_name",
    ("RunEndRiftOfficialFivePlayerLive.ps1", "RunEndRiftOfficialTenPlayerLive.ps1"),
)
@pytest.mark.parametrize("branch", ("codex/end-rift-event", CURRENT_END_RIFT_BRANCH))
def test_official_scaled_wrappers_run_the_shared_driver_on_each_allowed_branch(
    wrapper_name: str, branch: str
) -> None:
    result = _run_official_scaled_wrapper(wrapper_name, branch)

    assert result.returncode == 0, result.stdout + result.stderr
    assert "OFFICIAL_SHARED_DRIVER_REACHED" in result.stdout
    assert "refused Git branch" not in (result.stdout + result.stderr)


@pytest.mark.parametrize(
    "wrapper_name",
    ("RunEndRiftOfficialFivePlayerLive.ps1", "RunEndRiftOfficialTenPlayerLive.ps1"),
)
def test_official_scaled_wrappers_refuse_unapproved_branches_before_the_driver(
    wrapper_name: str,
) -> None:
    result = _run_official_scaled_wrapper(wrapper_name, "feature/unapproved")

    assert result.returncode == 10, result.stdout + result.stderr
    assert "refused Git branch 'feature/unapproved'" in (result.stdout + result.stderr)
    assert "OFFICIAL_SHARED_DRIVER_REACHED" not in result.stdout


@pytest.mark.parametrize(
    "wrapper_name",
    ("RunEndRiftOfficialFivePlayerLive.ps1", "RunEndRiftOfficialTenPlayerLive.ps1"),
)
def test_official_scaled_wrappers_refuse_detached_head_without_null_trim_error(
    wrapper_name: str,
) -> None:
    result = _run_official_scaled_wrapper(wrapper_name, None)

    assert result.returncode == 10, result.stdout + result.stderr
    output = result.stdout + result.stderr
    assert "refused Git branch ''" in output
    assert "null-valued expression" not in output.lower()
    assert "OFFICIAL_SHARED_DRIVER_REACHED" not in result.stdout


@pytest.mark.parametrize(
    "wrapper_name",
    ("RunEndRiftOfficialFivePlayerLive.ps1", "RunEndRiftOfficialTenPlayerLive.ps1"),
)
def test_official_scaled_wrappers_keep_the_local_environment_gate(
    wrapper_name: str,
) -> None:
    result = _run_official_scaled_wrapper(
        wrapper_name, CURRENT_END_RIFT_BRANCH, environment="production"
    )

    assert result.returncode == 10, result.stdout + result.stderr
    assert "refused a non-local end rift configuration" in (
        result.stdout + result.stderr
    ).lower()
    assert "OFFICIAL_SHARED_DRIVER_REACHED" not in result.stdout


def test_end_rift_live_runner_branch_checks_accept_an_explicit_expected_branch() -> None:
    runner_paths = sorted((ROOT / "tests").glob("RunEndRift*Live.ps1"))
    branch_reader = re.compile(
        r"&\s*git\s+-C\s+\$root\s+branch\s+--show-current",
        re.IGNORECASE,
    )
    branch_guarded_paths = [
        path for path in runner_paths if branch_reader.search(path.read_text(encoding="utf-8"))
    ]

    unguarded_required_paths = sorted(
        name
        for name in REQUIRED_BRANCH_GUARDED_RUNNERS
        if not branch_reader.search((ROOT / "tests" / name).read_text(encoding="utf-8"))
    )
    assert not unguarded_required_paths, (
        "local mob/spell live runners must check the current branch before making changes: "
        f"{unguarded_required_paths}"
    )

    assert branch_guarded_paths, "expected live runners to guard their Git branch"

    missing_expected_branch: list[str] = []
    for path in (ROOT / "tests" / name for name in sorted(REQUIRED_BRANCH_GUARDED_RUNNERS)):
        source = path.read_text(encoding="utf-8")
        has_configurable_expected_branch = re.search(
            r"\[string\]\s*\$ExpectedBranch\s*=\s*'[^']+'",
            source,
            re.IGNORECASE,
        )
        compares_expected_branch = re.search(
            r"\$branch\s+-cne\s+\$ExpectedBranch\b", source, re.IGNORECASE
        )
        if not has_configurable_expected_branch or not compares_expected_branch:
            missing_expected_branch.append(path.name)
    assert not missing_expected_branch, (
        "branch-guarded live runners need a nonempty ExpectedBranch parameter and must compare "
        f"against it: {missing_expected_branch}"
    )


def test_mob_and_spell_runner_branch_guards_precede_live_cleanup() -> None:
    first_live_actions = {
        "RunEndRiftMobCombatLive.ps1": "Rcon 'cmend wave clear'",
        "RunEndRiftSpellMatrixLive.ps1": "Invoke-LocalRcon -CommandText 'cmend wave clear'",
    }
    for name, first_live_action in first_live_actions.items():
        source = (ROOT / "tests" / name).read_text(encoding="utf-8")
        guard = source.find("$branch -cne $ExpectedBranch")
        live_try = source.find("\ntry {")
        action = source.find(first_live_action)

        assert min(guard, live_try, action) >= 0, f"{name} is missing a required guard/action"
        assert guard < live_try < action, (
            f"{name} must reject a wrong branch before entering cleanup or issuing RCON"
        )


def test_spell_matrix_wait_requires_exact_online_player_names() -> None:
    runner = (ROOT / "tests" / "RunEndRiftSpellMatrixLive.ps1").read_text(encoding="utf-8")
    helpers = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")

    assert "Test-EndRiftLocalPlayerNamesOnline -PlayerListOutput $list -RequiredNames $spellMatrixBotNames" in runner
    assert "(?<![A-Za-z0-9_])" in helpers
    assert "(?![A-Za-z0-9_])" in helpers
    assert "Where-Object { $list -match [Regex]::Escape($_) }" not in runner


def _powershell_executable() -> str:
    executable = shutil.which("powershell") or shutil.which("pwsh")
    if executable is None:
        pytest.skip("PowerShell is required to exercise the local runner")
    return executable


def _ps_quote(value: str | Path) -> str:
    return "'" + str(value).replace("'", "''") + "'"


def _run_obelisk_live_runner(
    branch: str, environment: str = "local"
) -> subprocess.CompletedProcess[str]:
    source_tests = ROOT / "tests"
    runner_source = source_tests / "RunEndRiftObeliskLive.ps1"

    with tempfile.TemporaryDirectory(prefix="end-rift-obelisk-runner-") as temporary:
        fixture_root = Path(temporary) / "fixture-repo"
        fixture_tests = fixture_root / "tests"
        server_dir = fixture_root / "local-runtime" / "end-rift-server"
        event_dir = fixture_root / "copimine-end-event"
        (server_dir / "logs").mkdir(parents=True)
        fixture_tests.mkdir(parents=True)
        event_dir.mkdir(parents=True)
        runner = fixture_tests / runner_source.name
        runner.write_bytes(runner_source.read_bytes())
        (fixture_tests / "InvokeEndRiftLocalRcon.ps1").write_text("", encoding="utf-8")
        (fixture_tests / "LocalEndRiftObeliskBot.js").write_text("", encoding="utf-8")
        (event_dir / "config.yml").write_text(
            f"schema-version: 4\nenvironment: {environment}\n", encoding="utf-8"
        )
        (server_dir / "logs" / "latest.log").write_text("", encoding="utf-8")

        script = f"""
function git {{ $global:LASTEXITCODE = 0; Write-Output '{branch}' }}
function powershell {{ throw 'RCON_SENTINEL' }}
try {{
  & {_ps_quote(runner)}
}} catch {{
  $message = [string]$_.Exception.Message
  [Console]::Out.WriteLine("RUNNER_ERROR:$message")
  if ($message -like '*Refused to run outside*') {{ exit 20 }}
  if ($message -like '*environment: local*') {{ exit 21 }}
  if ($message -like '*RCON_SENTINEL*') {{ exit 22 }}
  exit 23
}}
exit 24
"""
        return subprocess.run(
            [
                _powershell_executable(),
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script,
            ],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )


def test_obelisk_live_runner_allows_only_the_authorized_integration_branch() -> None:
    allowed = _run_obelisk_live_runner(CURRENT_END_RIFT_BRANCH)
    assert allowed.returncode == 22, allowed.stdout + allowed.stderr
    assert "RCON_SENTINEL" in allowed.stdout
    assert "Refused to run outside" not in allowed.stdout

    for branch in (
        "codex/end-rift-event",
        "feature/unapproved",
        CURRENT_END_RIFT_BRANCH.upper(),
        "",
    ):
        refused = _run_obelisk_live_runner(branch)
        assert refused.returncode == 20, refused.stdout + refused.stderr
        assert "Refused to run outside" in refused.stdout
        assert "RCON_SENTINEL" not in refused.stdout


def test_obelisk_live_runner_keeps_local_environment_and_isolated_rcon_guards() -> None:
    production_environment = _run_obelisk_live_runner(
        CURRENT_END_RIFT_BRANCH, environment="production"
    )
    assert production_environment.returncode == 21, (
        production_environment.stdout + production_environment.stderr
    )
    assert "environment: local" in production_environment.stdout
    assert "RCON_SENTINEL" not in production_environment.stdout

    runner = (ROOT / "tests" / "RunEndRiftObeliskLive.ps1").read_text(encoding="utf-8")
    rcon_helper = (ROOT / "tests" / "InvokeEndRiftLocalRcon.ps1").read_text(encoding="utf-8")
    assert "-ServerDir $serverDir -RconPort 25576" in runner
    assert "Test-EndRiftLocalRuntimePath" in rcon_helper
    assert "Test-EndRiftLocalServerBind" in rcon_helper
    assert "Assert-EndRiftLocalServerBind" in rcon_helper


def test_boss_multiplayer_runner_reports_detached_head_without_null_trim() -> None:
    runner = ROOT / "tests" / "RunEndRiftBossMultiPlayerDamageLive.ps1"
    script = f"""
function git {{ $global:LASTEXITCODE = 0 }}
try {{
  & {_ps_quote(runner)}
}} catch {{
  $message = [string]$_.Exception.Message
  [Console]::Out.WriteLine($message)
  if ($message -like '*refused Git branch*') {{ exit 0 }}
  exit 10
}}
exit 11
"""
    result = subprocess.run(
        [
            _powershell_executable(),
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            script,
        ],
        capture_output=True,
        text=True,
        timeout=30,
        check=False,
    )

    assert result.returncode == 0, result.stdout + result.stderr
    assert "refused git branch" in (result.stdout + result.stderr).lower()


def test_mob_shard_and_spell_runners_report_detached_head_without_null_trim() -> None:
    for name in (
        "RunEndRiftMobCombatLive.ps1",
        "RunEndRiftShardPassivesLive.ps1",
        "RunEndRiftSpellMatrixLive.ps1",
    ):
        runner = ROOT / "tests" / name
        expected_branch_argument = (
            f" -ExpectedBranch '{CURRENT_END_RIFT_BRANCH}'"
            if name in ("RunEndRiftMobCombatLive.ps1", "RunEndRiftSpellMatrixLive.ps1")
            else ""
        )
        script = f"""
function git {{ $global:LASTEXITCODE = 0 }}
try {{
  & {_ps_quote(runner)}{expected_branch_argument}
}} catch {{
  $message = [string]$_.Exception.Message
  [Console]::Out.WriteLine("RUNNER_ERROR:$message")
  if ($message -like '*refused Git branch*') {{ exit 0 }}
  exit 10
}}
exit 11
"""
        result = subprocess.run(
            [
                _powershell_executable(),
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script,
            ],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
        assert result.returncode == 0, f"{name}: {result.stdout}{result.stderr}"
        assert "refused git branch" in (result.stdout + result.stderr).lower()


def test_official_two_player_runner_reports_detached_head_before_live_inputs() -> None:
    source_tests = ROOT / "tests"
    runner_source = source_tests / "RunEndRiftOfficialTwoPlayerLive.ps1"
    helper_source = source_tests / "EndRiftLocalAuthMode.ps1"

    with tempfile.TemporaryDirectory(prefix="end-rift-official-runner-") as temporary:
        fixture_root = Path(temporary) / "fixture-repo"
        fixture_tests = fixture_root / "tests"
        server_dir = fixture_root / "local-runtime" / "end-rift-server"
        event_dir = fixture_root / "copimine-end-event"
        fixture_tests.mkdir(parents=True)
        server_dir.mkdir(parents=True)
        event_dir.mkdir(parents=True)
        runner = fixture_tests / runner_source.name
        runner.write_bytes(runner_source.read_bytes())
        (fixture_tests / helper_source.name).write_bytes(helper_source.read_bytes())
        (event_dir / "config.yml").write_text(
            "schema-version: 4\nenvironment: local\n", encoding="utf-8"
        )

        script = f"""
function git {{ $global:LASTEXITCODE = 0 }}
try {{
  & {_ps_quote(runner)}
}} catch {{
  $message = [string]$_.Exception.Message
  [Console]::Out.WriteLine("RUNNER_ERROR:$message")
  if ($message -like '*Refused Git branch*') {{ exit 0 }}
  exit 10
}}
exit 11
"""
        result = subprocess.run(
            [
                _powershell_executable(),
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script,
            ],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )

    assert result.returncode == 0, result.stdout + result.stderr
    assert "refused git branch" in (result.stdout + result.stderr).lower()


def test_boss_multiplayer_runner_accepts_the_runtime_root_before_validating_children() -> None:
    source_tests = ROOT / "tests"
    runner_source = source_tests / "RunEndRiftBossMultiPlayerDamageLive.ps1"
    helper_source = source_tests / "EndRiftLocalAuthMode.ps1"
    branch = CURRENT_END_RIFT_BRANCH

    with tempfile.TemporaryDirectory(prefix="end-rift-boss-runner-") as temporary:
        fixture_root = Path(temporary) / "fixture-repo"
        fixture_tests = fixture_root / "tests"
        server_dir = fixture_root / "local-runtime" / "end-rift-server"
        (fixture_tests).mkdir(parents=True)
        (fixture_root / "copimine-end-event").mkdir(parents=True)
        (server_dir / "logs").mkdir(parents=True)
        runner = fixture_tests / runner_source.name
        runner.write_bytes(runner_source.read_bytes())
        (fixture_tests / helper_source.name).write_bytes(helper_source.read_bytes())
        (fixture_tests / "LocalEndRiftBossCombatBot.js").write_text("", encoding="utf-8")
        (fixture_root / "copimine-end-event" / "config.yml").write_text(
            "environment: local\n", encoding="utf-8"
        )
        (server_dir / "server.properties").write_text(
            "server-ip=127.0.0.1\nrcon.ip=127.0.0.1\n"
            "server-port=25566\nenable-rcon=true\nrcon.port=25576\n",
            encoding="utf-8",
        )
        (server_dir / "logs" / "latest.log").write_text("", encoding="utf-8")

        script = f"""
function git {{ $global:LASTEXITCODE = 0; Write-Output '{branch}' }}
try {{
  & {_ps_quote(runner)} -FirstBotName 'ProbeOne' -SecondBotName 'ProbeTwo'
}} catch {{
  $message = [string]$_.Exception.Message
  [Console]::Out.WriteLine("RUNNER_ERROR:$message")
  if ($message -like '*must stay beneath local-runtime*') {{ exit 20 }}
  if ($message -like '*listener*') {{ exit 0 }}
  exit 21
}}
exit 22
"""
        result = subprocess.run(
            [
                _powershell_executable(),
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script,
            ],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )

    assert result.returncode == 0, result.stdout + result.stderr
    assert "RUNNER_ERROR:" in result.stdout + result.stderr
