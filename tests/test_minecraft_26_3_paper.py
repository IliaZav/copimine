"""The real, pinned Paper server JAR must be staged for migration review."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]


def _git_shell_path(value):
    value = str(value)
    drive = re.match(r"^([A-Za-z]):[\\/](.*)$", value)
    if drive is None:
        return value
    tail = drive.group(2).replace("\\", "/")
    return f"/{drive.group(1).lower()}/{tail}"


def test_git_shell_path_conversion_preserves_posix_absolute_paths():
    assert _git_shell_path("/tmp/copimine/fsmonitor.sh") == "/tmp/copimine/fsmonitor.sh"
    assert _git_shell_path(r"C:\Users\tester\fsmonitor.sh") == "/c/Users/tester/fsmonitor.sh"


def test_paper_server_candidate_matches_locked_build_and_download_object():
    lock = json.loads((ROOT / "tools/minecraft-26.3/profile.lock.json").read_text(encoding="utf-8"))
    paper = lock["paper"]

    assert lock["minimumJava"] == 25
    assert lock["buildJavaMajor"] == 25
    assert paper["build"] == 169
    assert paper["channel"] == "BETA"
    assert paper["api"] == "26.3.build.169-beta"
    assert paper["size"] == 55_138_949
    assert paper["sha256"] == "185d9f4267afca48959d6a56150cf8803b6645aee10de3ed147df7ab1ff781fb"
    assert paper["url"].startswith("https://fill-data.papermc.io/v1/objects/")
    assert paper["url"].endswith(f"/{paper['sha256']}/paper-26.3-{paper['build']}.jar")

    candidate = ROOT / "build/minecraft-26.3/server" / f"paper-26.3-{paper['build']}.jar"
    assert candidate.is_file(), "PrepareMigrationCandidates.ps1 must stage the pinned Paper server JAR"
    assert candidate.stat().st_size == paper["size"]
    assert hashlib.sha256(candidate.read_bytes()).hexdigest() == paper["sha256"]


def test_copimine_client_candidate_matches_locked_build_artifact():
    lock = json.loads((ROOT / "tools/minecraft-26.3/profile.lock.json").read_text(encoding="utf-8"))
    artifact = lock["clientArtifact"]
    candidate = ROOT / "build/minecraft-26.3/client/libs" / artifact["filename"]

    assert artifact["filename"] == "CopiMineClient-0.1.1+26.3.jar"
    assert artifact["size"] > 0
    assert len(artifact["sha512"]) == 128
    assert candidate.is_file(), "PrepareMigrationCandidates.ps1 must build CopiMineClient before profile install"
    assert candidate.stat().st_size == artifact["size"]
    assert hashlib.sha512(candidate.read_bytes()).hexdigest() == artifact["sha512"]


def test_admin_plugin_compiles_against_the_pinned_paper_voicechat_api():
    lock = json.loads((ROOT / "tools/minecraft-26.3/server-plugins.lock.json").read_text(encoding="utf-8"))
    voicechat = next(row for row in lock["modules"] if row["pluginName"] == "voicechat")
    gradle = (ROOT / "tools/minecraft-26.3/build.gradle").read_text(encoding="utf-8")
    build_script = (ROOT / "scripts/minecraft/BuildMigrationPlugins.ps1").read_text(encoding="utf-8")

    assert voicechat["filename"] == "voicechat-bukkit-2.6.24.jar"
    assert "server-plugins.lock.json" in gradle
    assert "voiceChatApiCandidate" in gradle
    assert "adminCompileOnly files(voiceChatApiCandidate)" in gradle
    assert "voicechat-fabric-1.21.1-2.6.16.jar" not in gradle
    assert "voicechat-bukkit-2.6.24.jar" in build_script
    assert build_script.index("Save-PinnedArtifact -Uri $voiceChatPlugin.url") < build_script.index("& $gradle ")


def test_migration_build_scripts_guard_generated_paths_before_external_writers():
    scripts = ROOT / "scripts/minecraft"
    coreprotect = (scripts / "BuildCoreProtectMigration.ps1").read_text(encoding="utf-8")
    plugins = (scripts / "BuildMigrationPlugins.ps1").read_text(encoding="utf-8")
    candidates = (scripts / "PrepareMigrationCandidates.ps1").read_text(encoding="utf-8")

    assert coreprotect.index("Assert-DirectoryTreeNoReparse -Path $source") < coreprotect.index("Invoke-MigrationGit -RepositoryPath $source -HooksPath $gitHooksPath -GitArguments @('init')")
    assert "& git -C $source" not in coreprotect
    assert coreprotect.index("Assert-DirectoryTreeNoReparse -Path $buildOutput") < coreprotect.index("& $maven ")
    assert "Copy-PinnedArtifactAtomically -Source $jar -Destination $destination" in coreprotect
    assert "buildSha512" in coreprotect and "buildSize" in coreprotect
    assert "Save-TextFileAtomically -Path $receiptPath" in coreprotect
    assert coreprotect.index("Save-TextFileAtomically -Path $receiptPath") < coreprotect.index("Remove-Item -LiteralPath $destination -Force")
    assert plugins.index("Assert-DirectoryTreeNoReparse -Path $pluginOutputRoot") < plugins.index("& $gradle ")
    assert candidates.index("Assert-AtomicFileDestination -Path $legacyPack") < candidates.index("build-resourcepack.ps1")
    assert candidates.index("Assert-DirectoryTreeNoReparse -Path $clientBuildOutput") < candidates.index("& $gradle ")


def test_coreprotect_git_checkout_disables_cache_configured_hooks(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    git = shutil.which("git")
    if not shell or not git:
        import pytest
        pytest.skip("CoreProtect Git hook isolation requires Git and PowerShell")

    repository = tmp_path / "CoreProtect"
    repository.mkdir()
    environment = dict(os.environ)
    environment.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": str(tmp_path / "empty-global.gitconfig")})
    subprocess.run([git, "init", str(repository)], check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "config", "user.name", "Migration Test"],
                   check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "config", "user.email", "migration-test@example.invalid"],
                   check=True, capture_output=True, text=True, env=environment)
    (repository / "tracked.txt").write_text("first\n", encoding="utf-8")
    subprocess.run([git, "-C", str(repository), "add", "tracked.txt"], check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "commit", "-m", "first"], check=True, capture_output=True, text=True, env=environment)
    first_commit = subprocess.run([git, "-C", str(repository), "rev-parse", "HEAD"], check=True,
                                  capture_output=True, text=True, env=environment).stdout.strip()
    default_branch = subprocess.run([git, "-C", str(repository), "branch", "--show-current"], check=True,
                                    capture_output=True, text=True, env=environment).stdout.strip()
    (repository / "tracked.txt").write_text("second\n", encoding="utf-8")
    subprocess.run([git, "-C", str(repository), "commit", "-am", "second"], check=True, capture_output=True, text=True, env=environment)

    hooks = tmp_path / "untrusted-hooks"
    hooks.mkdir()
    marker = tmp_path / "hook-ran.txt"
    hook = hooks / "post-checkout"
    hook.write_text("#!/bin/sh\nprintf invoked > \"$MIGRATION_HOOK_MARKER\"\n", encoding="utf-8", newline="\n")
    hook.chmod(0o755)
    environment["MIGRATION_HOOK_MARKER"] = str(marker).replace("\\", "/")
    subprocess.run([git, "-C", str(repository), "config", "core.hooksPath", str(hooks)],
                   check=True, capture_output=True, text=True, env=environment)
    git_version = subprocess.run([git, "--version"], check=True, capture_output=True,
                                 text=True, env=environment).stdout
    version_match = re.search(r"git version (\d+)\.(\d+)", git_version)
    assert version_match, f"could not parse Git version: {git_version}"
    supports_configured_hooks = tuple(map(int, version_match.groups())) >= (2, 54)
    fsmonitor_marker = tmp_path / "fsmonitor-hook-ran.txt"
    fsmonitor_script = tmp_path / "fsmonitor.sh"
    fsmonitor_script_posix = _git_shell_path(fsmonitor_script)
    fsmonitor_marker_posix = _git_shell_path(fsmonitor_marker)
    fsmonitor_script.write_text(
        "#!/bin/sh\n"
        f"printf invoked > '{fsmonitor_marker_posix}'\n"
        "printf '0000000000000000000000000000000000000000\\n'\n",
        encoding="utf-8",
        newline="\n",
    )
    subprocess.run([git, "-C", str(repository), "config", "core.fsmonitor", f"sh '{fsmonitor_script_posix}'"],
                   check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "status", "--short"],
                   check=True, capture_output=True, text=True, env=environment)
    assert fsmonitor_marker.is_file(), "the fixture must prove the repository fsmonitor command is executable"
    fsmonitor_marker.unlink()
    configured_marker = tmp_path / "configured-hook-ran.txt"
    included_marker = tmp_path / "included-hook-ran.txt"
    case_variant_marker = tmp_path / "case-variant-hook-ran.txt"
    if supports_configured_hooks:
        environment["MIGRATION_CONFIGURED_HOOK_MARKER"] = str(configured_marker).replace("\\", "/")
        environment["MIGRATION_INCLUDED_HOOK_MARKER"] = str(included_marker).replace("\\", "/")
        environment["MIGRATION_CASE_VARIANT_HOOK_MARKER"] = str(case_variant_marker).replace("\\", "/")
        subprocess.run([git, "-C", str(repository), "config", "extensions.worktreeConfig", "true"],
                       check=True, capture_output=True, text=True, env=environment)
        subprocess.run([git, "-C", str(repository), "config", "--worktree", "hook.migration-test.event", "post-checkout"],
                       check=True, capture_output=True, text=True, env=environment)
        subprocess.run([git, "-C", str(repository), "config", "--worktree", "hook.migration-test.command",
                        'printf configured > "$MIGRATION_CONFIGURED_HOOK_MARKER"'],
                       check=True, capture_output=True, text=True, env=environment)
        subprocess.run([git, "-C", str(repository), "config", "--worktree", "hook.Migration-Test.event", "post-checkout"],
                       check=True, capture_output=True, text=True, env=environment)
        subprocess.run([git, "-C", str(repository), "config", "--worktree", "hook.Migration-Test.command",
                        'printf case-variant > "$MIGRATION_CASE_VARIANT_HOOK_MARKER"'],
                       check=True, capture_output=True, text=True, env=environment)
        included_config = tmp_path / "included-hooks.gitconfig"
        subprocess.run([git, "config", "--file", str(included_config), "hook.included-test.event", "post-checkout"],
                       check=True, capture_output=True, text=True, env=environment)
        subprocess.run([git, "config", "--file", str(included_config), "hook.included-test.command",
                        'printf included > "$MIGRATION_INCLUDED_HOOK_MARKER"'],
                       check=True, capture_output=True, text=True, env=environment)
        subprocess.run([git, "-C", str(repository), "config", "--add", "include.path", str(included_config)],
                       check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "checkout", "--detach", first_commit],
                   check=True, capture_output=True, text=True, env=environment)
    assert marker.is_file(), "the fixture must prove its cache-configured hook is executable"
    if supports_configured_hooks:
        assert configured_marker.is_file(), "the fixture must prove its config-defined hook is executable"
        assert case_variant_marker.is_file(), "the fixture must prove Git hook friendly names preserve case"
        assert included_marker.is_file(), "the fixture must prove its included config hook is executable"
    marker.unlink()
    if configured_marker.exists():
        configured_marker.unlink()
    if included_marker.exists():
        included_marker.unlink()
    if case_variant_marker.exists():
        case_variant_marker.unlink()
    subprocess.run([git, "-C", str(repository), "checkout", default_branch],
                   check=True, capture_output=True, text=True, env=environment)
    marker.unlink()
    if configured_marker.exists():
        configured_marker.unlink()
    if included_marker.exists():
        included_marker.unlink()
    if case_variant_marker.exists():
        case_variant_marker.unlink()

    if fsmonitor_marker.exists():
        fsmonitor_marker.unlink()
    helper = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").as_posix().replace("'", "''")
    repo_ps = str(repository).replace("'", "''")
    safe_hooks = tmp_path / "empty-hooks"
    safe_hooks.mkdir()
    hooks_ps = str(safe_hooks).replace("'", "''")
    explicit_config_ps = str(tmp_path / "explicit-config.gitconfig").replace("'", "''")
    (tmp_path / "explicit-config.gitconfig").write_text("[migration-test]\n  marker = isolated\n", encoding="utf-8")
    script = tmp_path / "checkout.ps1"
    script.write_text(f"""$ErrorActionPreference = 'Stop'
. '{helper}'
$env:GIT_CONFIG = '{explicit_config_ps}'
$expectedGitConfig = $env:GIT_CONFIG
Set-Alias -Name git -Value Write-Output -Scope Script -Force
Invoke-MigrationGit -RepositoryPath '{repo_ps}' -HooksPath '{hooks_ps}' -GitArguments @('checkout', '--detach', '{first_commit}')
Invoke-MigrationGit -RepositoryPath '{repo_ps}' -HooksPath '{hooks_ps}' -GitArguments @('status', '--short')
if ($env:GIT_CONFIG -ne $expectedGitConfig) {{ throw 'Migration Git wrapper did not restore GIT_CONFIG' }}
""", encoding="utf-8")

    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                            capture_output=True, text=True, timeout=30, env=environment)

    assert result.returncode == 0, result.stdout + result.stderr
    actual_head = subprocess.run([git, "-C", str(repository), "rev-parse", "HEAD"], check=True,
                                 capture_output=True, text=True, env=environment).stdout.strip()
    assert actual_head == first_commit, "the protected pinned checkout must complete successfully"
    assert not marker.exists(), "cache-configured post-checkout hook must not run during pinned checkout"
    assert not configured_marker.exists(), "worktree-configured post-checkout hook must not run during pinned checkout"
    assert not included_marker.exists(), "included post-checkout hook must not run during pinned checkout"
    assert not case_variant_marker.exists(), "case-distinct configured hooks must both be disabled during pinned checkout"
    assert not fsmonitor_marker.exists(), "repository fsmonitor command must not run during pinned migration Git operations"
    coreprotect = (ROOT / "scripts/minecraft/BuildCoreProtectMigration.ps1").read_text(encoding="utf-8")
    assert "Invoke-MigrationGit" in coreprotect
    ast_check = tmp_path / "check-coreprotect-git-calls.ps1"
    ast_check.write_text(r"""param([string]$ScriptPath)
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($ScriptPath, [ref]$tokens, [ref]$errors)
if ($errors) { throw ($errors | Out-String) }
$commands = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.CommandAst] }, $true)
$assignments = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.AssignmentStatementAst] }, $true)
$normalize = { param([string]$text) ($text -replace '\s+', ' ').Trim() }
$expectedJavaAssignment = 'Join-Path $JavaHome ''bin/java.exe'''
$expectedMavenAssignment = 'Join-Path $toolchain (''apache-maven-''+$lock.maven.version+''/bin/mvn.cmd'')'
$expectedMavenCommand = '& $maven --batch-mode --no-transfer-progress --file (Join-Path $source ''pom.xml'') (''-Dproject.branch=''+$lock.branchLabel) ''-DskipTests=false'' verify'
$javaAssignments = @($assignments | Where-Object { $_.Left -is [Management.Automation.Language.VariableExpressionAst] -and $_.Left.VariablePath.UserPath -ceq 'java' })
$mavenAssignments = @($assignments | Where-Object { $_.Left -is [Management.Automation.Language.VariableExpressionAst] -and $_.Left.VariablePath.UserPath -ceq 'maven' })
if ($javaAssignments.Count -ne 1 -or (& $normalize $javaAssignments[0].Right.Extent.Text) -cne $expectedJavaAssignment) {
    throw 'The Java command must resolve from the pinned JavaHome executable path'
}
if ($mavenAssignments.Count -ne 1 -or (& $normalize $mavenAssignments[0].Right.Extent.Text) -cne $expectedMavenAssignment) {
    throw 'The Maven command must resolve from the pinned toolchain executable path'
}
$violations = @()
$externalCommands = @()
$startMethodCalls = $ast.FindAll({
    param($node)
    $node -is [Management.Automation.Language.InvokeMemberExpressionAst] -and
        $node.Member -is [Management.Automation.Language.StringConstantExpressionAst] -and
        $node.Member.Value -ieq 'Start'
}, $true)
foreach ($startCall in $startMethodCalls) { $violations += $startCall.Extent.Text }
foreach ($command in $commands) {
    $name = $command.GetCommandName()
    $alias = if ($name) { Get-Alias -Name $name -ErrorAction SilentlyContinue | Select-Object -First 1 } else { $null }
    $resolvedName = if ($alias) { $alias.Definition } else { $name }
    $forbidden = '^(?i:git(?:\.(?:exe|cmd|bat|ps1))?|Start-Process|Invoke-Expression|cmd(?:\.exe)?|bash|sh|iex|start|saps|Set-Alias|New-Alias|Remove-Alias|Import-Alias)$'
    $aliasProviderMutation = ($name -match '^(?i:Set-Item|New-Item|Remove-Item|Copy-Item|Move-Item|Clear-Item)$' -or
        $resolvedName -match '^(?i:Set-Item|New-Item|Remove-Item|Copy-Item|Move-Item|Clear-Item)$') -and
        $command.Extent.Text -match '(?i)\bAlias:'
    if ($name -match $forbidden -or $resolvedName -match $forbidden -or $aliasProviderMutation) {
        $violations += $command.Extent.Text
    }
    if ($command.InvocationOperator -eq [Management.Automation.Language.TokenKind]::Ampersand) {
        $normalized = & $normalize $command.Extent.Text
        $allowed = @(
            '& $java --version',
            $expectedMavenCommand
        )
        if ($allowed -cnotcontains $normalized) {
            $violations += $command.Extent.Text
        }
        $externalCommands += $normalized
    }
}
if ($externalCommands.Count -ne 2 -or $externalCommands -cnotcontains '& $java --version' -or
    $externalCommands -cnotcontains $expectedMavenCommand) {
    $violations += 'missing or unexpected external command set'
}
if ($violations) { throw ('Unexpected external Git/process invocation: ' + ($violations -join '; ')) }
""", encoding="utf-8")
    ast_result = subprocess.run(
        [shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(ast_check),
         str(ROOT / "scripts/minecraft/BuildCoreProtectMigration.ps1")],
        capture_output=True, text=True, timeout=30, env=environment,
    )
    assert ast_result.returncode == 0, ast_result.stdout + ast_result.stderr
    unsafe_script = tmp_path / "unsafe-coreprotect-command.ps1"
    unsafe_script.write_text(
        coreprotect.replace("$java=Join-Path $JavaHome 'bin/java.exe'", "$java='git.exe'", 1),
        encoding="utf-8",
    )
    unsafe_result = subprocess.run(
        [shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(ast_check), str(unsafe_script)],
        capture_output=True, text=True, timeout=30, env=environment,
    )
    assert unsafe_result.returncode != 0, "the AST regression guard must reject reassigned tool variables"
    for alias_invocation in (
        "iex 'git status'",
        "start git status",
        "Set-Alias -Name g -Value git\ng status",
        "ni Alias:g -Value git\ng status",
        "[System.Diagnostics.Process]::Start('git.exe', 'status')",
        "git.cmd status",
        "git.bat status",
    ):
        unsafe_script.write_text(coreprotect + "\n" + alias_invocation + "\n", encoding="utf-8")
        unsafe_result = subprocess.run(
            [shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(ast_check), str(unsafe_script)],
            capture_output=True, text=True, timeout=30, env=environment,
        )
        assert unsafe_result.returncode != 0, f"the AST regression guard must reject alias invocation: {alias_invocation}"
    git_calls = [line for line in coreprotect.splitlines() if "Invoke-MigrationGit -RepositoryPath $source" in line]
    assert len(git_calls) == 8
    assert all("-HooksPath $gitHooksPath" in line for line in git_calls)
    helper_source = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").read_text(encoding="utf-8")
    assert "config --includes --name-only --get-regexp '^hook\\..*\\.event$'" in helper_source
    assert "'hook.'+$friendlyName+'.enabled=false'" in helper_source
    assert "core.fsmonitor=false" in helper_source
    assert all(name in helper_source for name in ("GIT_CONFIG_NOSYSTEM", "GIT_CONFIG_GLOBAL", "GIT_CONFIG"))


def test_coreprotect_git_cache_preflight_rejects_untrusted_local_config_and_attributes(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    git = shutil.which("git")
    if not shell or not git:
        import pytest
        pytest.skip("CoreProtect Git cache preflight requires Git and PowerShell")

    repository = tmp_path / "CoreProtect-cache"
    repository.mkdir()
    environment = dict(os.environ)
    environment.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": str(tmp_path / "empty-global.gitconfig")})
    subprocess.run([git, "init", str(repository)], check=True, capture_output=True, text=True, env=environment)
    expected_remote = "https://github.com/PlayPro/CoreProtect.git"
    subprocess.run([git, "-C", str(repository), "remote", "add", "origin", expected_remote],
                   check=True, capture_output=True, text=True, env=environment)

    helper = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").as_posix().replace("'", "''")
    repo_ps = str(repository).replace("'", "''")
    hooks = tmp_path / "empty-hooks"
    hooks.mkdir()
    hooks_ps = str(hooks).replace("'", "''")
    expected_remote_ps = expected_remote.replace("'", "''")
    script = tmp_path / "verify-cache.ps1"
    script.write_text(f"""$ErrorActionPreference = 'Stop'
. '{helper}'
$repository = '{repo_ps}'
$hooks = '{hooks_ps}'
$remote = '{expected_remote_ps}'
Assert-MigrationGitCacheSafe -RepositoryPath $repository -HooksPath $hooks -ExpectedRemote $remote
$attributes = Join-Path $repository '.git/info/attributes'
Set-Content -LiteralPath $attributes -Value 'tracked.txt filter=migration-test'
$attributesRejected = $false
try {{ Assert-MigrationGitCacheSafe -RepositoryPath $repository -HooksPath $hooks -ExpectedRemote $remote }}
catch {{ $attributesRejected = $true }}
if (-not $attributesRejected) {{ throw 'Repository-local info attributes were not rejected' }}
Remove-Item -LiteralPath $attributes -Force
& git -C $repository config filter.migration-test.smudge 'Write-Output poisoned'
if ($LASTEXITCODE -ne 0) {{ throw 'Could not create the malicious filter fixture' }}
$filterRejected = $false
try {{ Assert-MigrationGitCacheSafe -RepositoryPath $repository -HooksPath $hooks -ExpectedRemote $remote }}
catch {{ $filterRejected = $true }}
if (-not $filterRejected) {{ throw 'Unapproved local filter configuration was not rejected' }}
""", encoding="utf-8")

    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                            capture_output=True, text=True, timeout=30, env=environment)
    assert result.returncode == 0, result.stdout + result.stderr

    helper_source = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").read_text(encoding="utf-8")
    coreprotect = (ROOT / "scripts/minecraft/BuildCoreProtectMigration.ps1").read_text(encoding="utf-8")
    assert "function Assert-MigrationGitCacheSafe" in helper_source
    assert "info/attributes" in helper_source
    assert "remote.origin.url" in helper_source and "remote.origin.fetch" in helper_source
    fetch_index = coreprotect.index("GitArguments @('fetch'")
    checkout_index = coreprotect.index("GitArguments @('checkout'")
    status_index = coreprotect.index("GitArguments @('status'")
    assert coreprotect.rfind("Assert-MigrationGitCacheSafe", 0, status_index) >= 0
    assert coreprotect.rfind("Assert-MigrationGitCacheSafe", 0, fetch_index) >= 0
    assert coreprotect.rfind("Assert-MigrationGitCacheSafe", fetch_index, checkout_index) >= fetch_index


def test_coreprotect_git_cache_initialization_refuses_nonempty_cache_without_git(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    if not shell:
        import pytest
        pytest.skip("CoreProtect Git cache initialization preflight requires PowerShell")

    helper = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").as_posix().replace("'", "''")
    nonempty_cache = tmp_path / "CoreProtect-uninitialized-cache"
    nonempty_cache.mkdir()
    (nonempty_cache / "unexpected.txt").write_text("preserve and refuse", encoding="utf-8")
    empty_cache = tmp_path / "CoreProtect-empty-cache"
    registered_cache = tmp_path / "CoreProtect-existing-repository"
    registered_cache.mkdir()
    (registered_cache / ".git").mkdir()
    nonempty_ps = str(nonempty_cache).replace("'", "''")
    empty_ps = str(empty_cache).replace("'", "''")
    registered_ps = str(registered_cache).replace("'", "''")
    script = tmp_path / "verify-cache-initialization.ps1"
    script.write_text(f"""$ErrorActionPreference = 'Stop'
. '{helper}'
$nonempty = '{nonempty_ps}'
$rejected = $false
try {{ Assert-MigrationGitCacheReadyForInitialization -RepositoryPath $nonempty }}
catch {{
  $rejected = $true
  if ($_.Exception.Message -notmatch [regex]::Escape($nonempty)) {{ throw 'Refusal did not identify the cache to clear' }}
}}
if (-not $rejected) {{ throw 'A non-empty cache without Git metadata was accepted' }}
if (Test-Path -LiteralPath (Join-Path $nonempty '.git')) {{ throw 'Refusal modified the uninitialized cache' }}
$empty = '{empty_ps}'
New-Item -ItemType Directory -Path $empty | Out-Null
Assert-MigrationGitCacheReadyForInitialization -RepositoryPath $empty
$registered = '{registered_ps}'
Assert-MigrationGitCacheReadyForInitialization -RepositoryPath $registered
""", encoding="utf-8")

    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr

    coreprotect = (ROOT / "scripts/minecraft/BuildCoreProtectMigration.ps1").read_text(encoding="utf-8")
    guard_index = coreprotect.index("Assert-MigrationGitCacheReadyForInitialization -RepositoryPath $source")
    init_index = coreprotect.index("GitArguments @('init')")
    assert guard_index < init_index
    assert "Add-MigrationGitOrigin -RepositoryPath $source" in coreprotect
    helper_source = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").read_text(encoding="utf-8")
    assert "CoreProtect origin configuration failed; remove the incomplete source cache before retrying:" in helper_source


def test_coreprotect_origin_failure_reports_incomplete_source_cache(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    git = shutil.which("git")
    if not shell or not git:
        import pytest
        pytest.skip("CoreProtect origin failure recovery requires Git and PowerShell")

    repository = tmp_path / "CoreProtect-origin-cache"
    repository.mkdir()
    environment = dict(os.environ)
    environment.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": str(tmp_path / "empty-global.gitconfig")})
    for name in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_EXEC_PATH", "GIT_NO_REPLACE_OBJECTS"):
        environment.pop(name, None)
    subprocess.run([git, "init", str(repository)], check=True, capture_output=True, text=True, env=environment)
    existing_remote = "https://example.invalid/poisoned.git"
    expected_remote = "https://github.com/PlayPro/CoreProtect.git"
    subprocess.run([git, "-C", str(repository), "remote", "add", "origin", existing_remote],
                   check=True, capture_output=True, text=True, env=environment)
    helper = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").as_posix().replace("'", "''")
    repo_ps = str(repository).replace("'", "''")
    hooks = tmp_path / "empty-hooks"
    hooks.mkdir()
    hooks_ps = str(hooks).replace("'", "''")
    expected_remote_ps = expected_remote.replace("'", "''")
    script = tmp_path / "verify-origin-failure.ps1"
    script.write_text(f"""$ErrorActionPreference = 'Stop'
. '{helper}'
$rejected = $false
try {{ Add-MigrationGitOrigin -RepositoryPath '{repo_ps}' -HooksPath '{hooks_ps}' -ExpectedRemote '{expected_remote_ps}' }}
catch {{
  $rejected = $true
  if ($_.Exception.Message -notmatch [regex]::Escape('{repo_ps}')) {{ throw 'Origin failure did not identify the incomplete source cache' }}
  if ($_.Exception.Message -notmatch 'remove the incomplete source cache before retrying') {{ throw 'Origin failure did not explain recovery' }}
}}
if (-not $rejected) {{ throw 'An existing origin was accepted as a successful initialization' }}
""", encoding="utf-8")

    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                            capture_output=True, text=True, timeout=30, env=environment)
    assert result.returncode == 0, result.stdout + result.stderr

    coreprotect = (ROOT / "scripts/minecraft/BuildCoreProtectMigration.ps1").read_text(encoding="utf-8")
    assert "Add-MigrationGitOrigin -RepositoryPath $source" in coreprotect


def test_coreprotect_git_cache_preflight_rejects_index_flags(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    git = shutil.which("git")
    if not shell or not git:
        import pytest
        pytest.skip("CoreProtect Git index preflight requires Git and PowerShell")

    repository = tmp_path / "CoreProtect-index-cache"
    repository.mkdir()
    environment = dict(os.environ)
    environment.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": str(tmp_path / "empty-global.gitconfig")})
    for name in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_EXEC_PATH", "GIT_NO_REPLACE_OBJECTS"):
        environment.pop(name, None)
    subprocess.run([git, "init", str(repository)], check=True, capture_output=True, text=True, env=environment)
    expected_remote = "https://github.com/PlayPro/CoreProtect.git"
    subprocess.run([git, "-C", str(repository), "remote", "add", "origin", expected_remote],
                   check=True, capture_output=True, text=True, env=environment)
    tracked = repository / "pom.xml"
    tracked.write_text("<project>pinned</project>\n", encoding="utf-8")
    subprocess.run([git, "-C", str(repository), "add", "pom.xml"],
                   check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "-c", "user.name=Migration Test", "-c",
                    "user.email=migration-test@example.invalid", "commit", "-m", "pinned"],
                   check=True, capture_output=True, text=True, env=environment)

    helper = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").as_posix().replace("'", "''")
    repo_ps = str(repository).replace("'", "''")
    hooks = tmp_path / "empty-hooks"
    hooks.mkdir()
    hooks_ps = str(hooks).replace("'", "''")
    expected_remote_ps = expected_remote.replace("'", "''")
    script = tmp_path / "verify-index-flags.ps1"
    script.write_text(f"""$ErrorActionPreference = 'Stop'
. '{helper}'
$repository = '{repo_ps}'
$hooks = '{hooks_ps}'
$remote = '{expected_remote_ps}'
$tracked = Join-Path $repository 'pom.xml'
foreach ($flag in @('--assume-unchanged', '--skip-worktree')) {{
    & git -C $repository update-index $flag -- pom.xml
    if ($LASTEXITCODE -ne 0) {{ throw ('Could not set index flag ' + $flag) }}
    [IO.File]::WriteAllText($tracked, '<project>changed</project>' + [Environment]::NewLine)
    $status = (& git -C $repository status --porcelain --untracked-files=all | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $status) {{ throw ('The fixture did not hide its modified tracked file with ' + $flag) }}
    $rejected = $false
    try {{ Assert-MigrationGitCacheSafe -RepositoryPath $repository -HooksPath $hooks -ExpectedRemote $remote }}
    catch {{ $rejected = $true }}
    if (-not $rejected) {{ throw ('The CoreProtect cache preflight accepted the hidden modified file with ' + $flag) }}
    & git -C $repository update-index --no-assume-unchanged --no-skip-worktree -- pom.xml
    if ($LASTEXITCODE -ne 0) {{ throw ('Could not clear index flag ' + $flag) }}
    [IO.File]::WriteAllText($tracked, '<project>pinned</project>' + [Environment]::NewLine)
}}
""", encoding="utf-8")

    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                            capture_output=True, text=True, timeout=30, env=environment)
    assert result.returncode == 0, result.stdout + result.stderr


def test_coreprotect_git_cache_preflight_rejects_active_info_exclude_rules(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    git = shutil.which("git")
    if not shell or not git:
        import pytest
        pytest.skip("CoreProtect Git exclude preflight requires Git and PowerShell")

    repository = tmp_path / "CoreProtect-exclude-cache"
    repository.mkdir()
    environment = dict(os.environ)
    environment.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": str(tmp_path / "empty-global.gitconfig")})
    for name in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_EXEC_PATH", "GIT_NO_REPLACE_OBJECTS"):
        environment.pop(name, None)
    subprocess.run([git, "init", str(repository)], check=True, capture_output=True, text=True, env=environment)
    expected_remote = "https://github.com/PlayPro/CoreProtect.git"
    subprocess.run([git, "-C", str(repository), "remote", "add", "origin", expected_remote],
                   check=True, capture_output=True, text=True, env=environment)
    hidden_source = repository / "src/test/java/HiddenTest.java"
    hidden_source.parent.mkdir(parents=True)
    hidden_source.write_text("class HiddenTest {}\n", encoding="utf-8")
    (repository / ".git/info/exclude").write_text("src/test/java/HiddenTest.java\n", encoding="utf-8")
    status = subprocess.run([git, "-C", str(repository), "status", "--porcelain", "--untracked-files=all"],
                            check=True, capture_output=True, text=True, env=environment)
    assert status.stdout.strip() == "", "the fixture must prove info/exclude hides the added test source"

    helper = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").as_posix().replace("'", "''")
    repo_ps = str(repository).replace("'", "''")
    hooks = tmp_path / "empty-hooks"
    hooks.mkdir()
    hooks_ps = str(hooks).replace("'", "''")
    expected_remote_ps = expected_remote.replace("'", "''")
    script = tmp_path / "verify-info-exclude.ps1"
    script.write_text(f"""$ErrorActionPreference = 'Stop'
. '{helper}'
$rejected = $false
try {{ Assert-MigrationGitCacheSafe -RepositoryPath '{repo_ps}' -HooksPath '{hooks_ps}' -ExpectedRemote '{expected_remote_ps}' }}
catch {{ $rejected = $true }}
if (-not $rejected) {{ throw 'The CoreProtect cache preflight accepted an active repository-local exclude rule' }}
""", encoding="utf-8")
    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                            capture_output=True, text=True, timeout=30, env=environment)
    assert result.returncode == 0, result.stdout + result.stderr


def test_migration_git_init_ignores_environment_templates(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    if not shell:
        import pytest
        pytest.skip("Migration Git template isolation requires PowerShell")

    template = tmp_path / "untrusted-template"
    (template / "info").mkdir(parents=True)
    (template / "hooks").mkdir()
    (template / "info/attributes").write_text("*.txt filter=untrusted\n", encoding="utf-8")
    destination = tmp_path / "initialized-repository"
    destination.mkdir()
    hooks = tmp_path / "empty-hooks"
    hooks.mkdir()
    helper = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").as_posix().replace("'", "''")
    destination_ps = str(destination).replace("'", "''")
    hooks_ps = str(hooks).replace("'", "''")
    template_ps = str(template).replace("'", "''")
    script = tmp_path / "initialize.ps1"
    script.write_text(f"""$ErrorActionPreference = 'Stop'
. '{helper}'
$template = '{template_ps}'
Remove-Item Env:GIT_DIR -ErrorAction SilentlyContinue
$env:GIT_INDEX_FILE = ''
$env:GIT_TEMPLATE_DIR = $template
Invoke-MigrationGit -RepositoryPath '{destination_ps}' -HooksPath '{hooks_ps}' -GitArguments @('init')
if ($LASTEXITCODE -ne 0) {{ throw 'Isolated repository init failed' }}
if ($env:GIT_TEMPLATE_DIR -ne $template) {{ throw 'Migration Git wrapper did not restore GIT_TEMPLATE_DIR' }}
if (Test-Path Env:GIT_DIR) {{ throw 'Migration Git wrapper did not restore an unset GIT_DIR' }}
if (-not (Test-Path Env:GIT_INDEX_FILE) -or $env:GIT_INDEX_FILE -ne '') {{ throw 'Migration Git wrapper did not restore an empty GIT_INDEX_FILE' }}
""", encoding="utf-8")
    environment = dict(os.environ)
    environment.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": str(tmp_path / "empty-global.gitconfig")})
    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                            capture_output=True, text=True, timeout=30, env=environment)
    assert result.returncode == 0, result.stdout + result.stderr
    assert not (destination / ".git/info/attributes").exists()


def test_migration_git_wrapper_clears_repository_and_exec_path_overrides(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    git = shutil.which("git")
    if not shell or not git:
        import pytest
        pytest.skip("Migration Git environment isolation requires Git and PowerShell")

    source = tmp_path / "source"
    alternate = tmp_path / "alternate"
    source.mkdir()
    alternate.mkdir()
    environment = dict(os.environ)
    environment.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": str(tmp_path / "empty-global.gitconfig")})
    for name in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_EXEC_PATH"):
        environment.pop(name, None)
    for repository, content in ((source, "source\n"), (alternate, "alternate\n")):
        subprocess.run([git, "init", str(repository)], check=True, capture_output=True, text=True, env=environment)
        subprocess.run([git, "-C", str(repository), "config", "user.name", "Migration Test"],
                       check=True, capture_output=True, text=True, env=environment)
        subprocess.run([git, "-C", str(repository), "config", "user.email", "migration-test@example.invalid"],
                       check=True, capture_output=True, text=True, env=environment)
        (repository / "tracked.txt").write_text(content, encoding="utf-8")
        subprocess.run([git, "-C", str(repository), "add", "tracked.txt"],
                       check=True, capture_output=True, text=True, env=environment)
        subprocess.run([git, "-C", str(repository), "commit", "-m", "fixture"],
                       check=True, capture_output=True, text=True, env=environment)
    (source / "tracked.txt").write_text("dirty source\n", encoding="utf-8")

    hooks = tmp_path / "empty-hooks"
    hooks.mkdir()
    fake_exec = tmp_path / "fake-git-exec"
    fake_exec.mkdir()
    helper = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").as_posix().replace("'", "''")
    source_ps = str(source).replace("'", "''")
    alternate_ps = str(alternate).replace("'", "''")
    hooks_ps = str(hooks).replace("'", "''")
    fake_exec_ps = str(fake_exec).replace("'", "''")
    index_ps = str(alternate / ".git/index").replace("'", "''")
    script = tmp_path / "verify-environment.ps1"
    script.write_text(f"""$ErrorActionPreference = 'Stop'
. '{helper}'
$env:GIT_DIR = '{str(alternate / '.git').replace("'", "''")}'
$env:GIT_WORK_TREE = '{alternate_ps}'
$env:GIT_INDEX_FILE = '{index_ps}'
$env:GIT_EXEC_PATH = '{fake_exec_ps}'
$status = @(Invoke-MigrationGit -RepositoryPath '{source_ps}' -HooksPath '{hooks_ps}' -GitArguments @('status','--porcelain'))
if ($LASTEXITCODE -ne 0) {{ throw 'Isolated source status failed' }}
if (-not (($status | Out-String).Trim())) {{ throw 'Inherited Git repository overrides hid the dirty source tree' }}
if ($env:GIT_DIR -notlike '*alternate*\\.git' -or $env:GIT_WORK_TREE -ne '{alternate_ps}' -or
    $env:GIT_INDEX_FILE -ne '{index_ps}' -or $env:GIT_EXEC_PATH -ne '{fake_exec_ps}') {{
    throw 'Migration Git wrapper did not restore caller environment'
}}
$execPath = (Invoke-MigrationGit -RepositoryPath '{source_ps}' -HooksPath '{hooks_ps}' -GitArguments @('--exec-path') | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $execPath -eq '{fake_exec_ps}') {{ throw 'Inherited GIT_EXEC_PATH was not cleared for Git execution' }}
""", encoding="utf-8")
    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                            capture_output=True, text=True, timeout=30, env=environment)
    assert result.returncode == 0, result.stdout + result.stderr


def test_migration_git_wrapper_disables_replace_refs_and_restores_environment(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    git = shutil.which("git")
    if not shell or not git:
        import pytest
        pytest.skip("Migration Git replacement isolation requires Git and PowerShell")

    repository = tmp_path / "replacement-repository"
    repository.mkdir()
    environment = dict(os.environ)
    environment.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": str(tmp_path / "empty-global.gitconfig")})
    for name in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_EXEC_PATH", "GIT_NO_REPLACE_OBJECTS"):
        environment.pop(name, None)
    subprocess.run([git, "init", str(repository)], check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "config", "user.name", "Migration Test"],
                   check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "config", "user.email", "migration-test@example.invalid"],
                   check=True, capture_output=True, text=True, env=environment)
    tracked = repository / "tracked.txt"
    tracked.write_text("pinned content\n", encoding="utf-8")
    subprocess.run([git, "-C", str(repository), "add", "tracked.txt"], check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "commit", "-m", "pinned"], check=True, capture_output=True, text=True, env=environment)
    pinned_commit = subprocess.run([git, "-C", str(repository), "rev-parse", "HEAD"], check=True,
                                   capture_output=True, text=True, env=environment).stdout.strip()
    tracked.write_text("replacement content\n", encoding="utf-8")
    subprocess.run([git, "-C", str(repository), "commit", "-am", "replacement"], check=True,
                   capture_output=True, text=True, env=environment)
    replacement_commit = subprocess.run([git, "-C", str(repository), "rev-parse", "HEAD"], check=True,
                                        capture_output=True, text=True, env=environment).stdout.strip()
    subprocess.run([git, "-C", str(repository), "replace", pinned_commit, replacement_commit],
                   check=True, capture_output=True, text=True, env=environment)
    replaced_content = subprocess.run([git, "-C", str(repository), "show", f"{pinned_commit}:tracked.txt"],
                                      check=True, capture_output=True, text=True, env=environment).stdout
    assert replaced_content == "replacement content\n", "the fixture must prove replace refs affect ordinary Git reads"

    hooks = tmp_path / "empty-hooks"
    hooks.mkdir()
    helper = (ROOT / "scripts/minecraft/InvokeMigrationGit.ps1").as_posix().replace("'", "''")
    repository_ps = str(repository).replace("'", "''")
    hooks_ps = str(hooks).replace("'", "''")
    script = tmp_path / "verify-replace-refs.ps1"
    script.write_text(f"""$ErrorActionPreference = 'Stop'
. '{helper}'
$env:GIT_NO_REPLACE_OBJECTS = 'caller-value'
$content = (Invoke-MigrationGit -RepositoryPath '{repository_ps}' -HooksPath '{hooks_ps}' -GitArguments @('show','{pinned_commit}:tracked.txt') | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $content -ne 'pinned content') {{ throw ('Migration Git read followed an untrusted replace ref: '+$content) }}
if ($env:GIT_NO_REPLACE_OBJECTS -ne 'caller-value') {{ throw 'Migration Git wrapper did not restore GIT_NO_REPLACE_OBJECTS' }}
""", encoding="utf-8")
    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                            capture_output=True, text=True, timeout=30, env=environment)
    assert result.returncode == 0, result.stdout + result.stderr


def test_migration_server_startup_validator_separates_native_stderr(tmp_path):
    shell = shutil.which("powershell") or shutil.which("pwsh")
    if not shell:
        import pytest
        pytest.skip("Migration server startup validator requires PowerShell")

    startup_script = (ROOT / "scripts/minecraft/StartMigrationTestServer.ps1").read_text(encoding="utf-8")
    assert "-RedirectStandardOutput" in startup_script
    assert "-RedirectStandardError" in startup_script
    assert ".ExitCode" in startup_script
    assert "$settingsOutput = @(& $pythonLauncher.Source -3.13 $pluginInstaller @startupSettingsArguments 2>&1)" not in startup_script

    python_script = tmp_path / "emit_startup_settings.py"
    python_script.write_text(
        "import json, sys\n"
        "print(json.dumps({'serverIp': '127.0.0.1'}))\n"
        "print('validator warning', file=sys.stderr)\n"
        "sys.exit(7 if len(sys.argv) > 1 else 0)\n",
        encoding="utf-8",
    )
    python_ps = sys.executable.replace("'", "''")
    python_script_ps = str(python_script).replace("'", "''")
    out_ok = str(tmp_path / "stdout-ok.json").replace("'", "''")
    err_ok = str(tmp_path / "stderr-ok.txt").replace("'", "''")
    out_fail = str(tmp_path / "stdout-fail.json").replace("'", "''")
    err_fail = str(tmp_path / "stderr-fail.txt").replace("'", "''")
    script = tmp_path / "capture-native-output.ps1"
    script.write_text(f"""$ErrorActionPreference = 'Stop'
$python = '{python_ps}'
$script = '{python_script_ps}'
$success = Start-Process -FilePath $python -ArgumentList ('"' + $script + '"') -NoNewWindow -Wait -PassThru -RedirectStandardOutput '{out_ok}' -RedirectStandardError '{err_ok}'
$stdout = [IO.File]::ReadAllText('{out_ok}')
$stderr = [IO.File]::ReadAllText('{err_ok}')
$settings = $stdout | ConvertFrom-Json -ErrorAction Stop
if ($success.ExitCode -ne 0 -or $settings.serverIp -ne '127.0.0.1' -or $stderr -notmatch 'validator warning') {{ throw 'Successful validator warning handling failed' }}
$failure = Start-Process -FilePath $python -ArgumentList ('"' + $script + '" fail') -NoNewWindow -Wait -PassThru -RedirectStandardOutput '{out_fail}' -RedirectStandardError '{err_fail}'
$failureStderr = [IO.File]::ReadAllText('{err_fail}')
if ($failure.ExitCode -ne 7 -or $failureStderr -notmatch 'validator warning') {{ throw 'Validator failure exit code or stderr was lost' }}
""", encoding="utf-8")
    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr


def test_migration_ci_workspace_revision_uses_git_application_not_shadow_alias(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    git = shutil.which("git")
    if not shell or not git:
        import pytest
        pytest.skip("migration CI Git resolution requires Git and PowerShell")

    repository = tmp_path / "migration-ci-repository"
    repository.mkdir()
    environment = dict(os.environ)
    environment.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": str(tmp_path / "empty-global.gitconfig")})
    subprocess.run([git, "init", str(repository)], check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "config", "user.name", "Migration Test"],
                   check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "config", "user.email", "migration-test@example.invalid"],
                   check=True, capture_output=True, text=True, env=environment)
    (repository / "tracked.txt").write_text("migration-ci\n", encoding="utf-8")
    subprocess.run([git, "-C", str(repository), "add", "tracked.txt"],
                   check=True, capture_output=True, text=True, env=environment)
    subprocess.run([git, "-C", str(repository), "commit", "-m", "migration CI fixture"],
                   check=True, capture_output=True, text=True, env=environment)
    expected_revision = subprocess.run([git, "-C", str(repository), "rev-parse", "--verify", "HEAD"],
                                       check=True, capture_output=True, text=True, env=environment).stdout.strip()

    script_path = (ROOT / "scripts/minecraft/RunJavaPluginCi.ps1").as_posix().replace("'", "''")
    repository_path = str(repository).replace("'", "''")
    probe = tmp_path / "probe-migration-ci-git-resolution.ps1"
    probe.write_text(f"""$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile('{script_path}', [ref]$tokens, [ref]$errors)
if ($errors) {{ throw ($errors | Out-String) }}
$functions = @($ast.FindAll({{ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Get-MigrationWorkspaceRevision' }}, $true))
if ($functions.Count -ne 1) {{ throw 'Expected one Get-MigrationWorkspaceRevision function.' }}
$functionPath = Join-Path ([IO.Path]::GetTempPath()) (([guid]::NewGuid().ToString('N')) + '.ps1')
[IO.File]::WriteAllText($functionPath, $functions[0].Extent.Text)
function Get-FakeGit {{ return 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' }}
Set-Alias -Name git -Value Get-FakeGit -Scope Script -Force
. $functionPath
$global:LASTEXITCODE = 0
$actualRevision = Get-MigrationWorkspaceRevision -WorkspaceRoot '{repository_path}'
if ($actualRevision -cne '{expected_revision}') {{ throw 'Git alias shadowing changed the recovered workspace revision.' }}
Remove-Item -LiteralPath $functionPath -Force
""", encoding="utf-8")
    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(probe)],
                            capture_output=True, text=True, timeout=30, env=environment)
    assert result.returncode == 0, result.stdout + result.stderr


def test_end_rift_event_gate_hashes_without_get_file_hash_cmdlet(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    if not shell:
        import pytest
        pytest.skip("End Rift gate PowerShell hash validation requires PowerShell")

    sample = tmp_path / "pinned-dependency.bin"
    sample.write_bytes(b"migration gate dependency\n")
    expected_hash = hashlib.sha256(sample.read_bytes()).hexdigest()
    script_path = (ROOT / "tests/RunEndRiftEventChecks.ps1").as_posix().replace("'", "''")
    sample_path = str(sample).replace("'", "''")
    probe = tmp_path / "probe-end-rift-gate-hash.ps1"
    probe.write_text(f"""$scriptText = [IO.File]::ReadAllText('{script_path}')
if ($scriptText.Contains('Get-FileHash')) {{ throw 'The End Rift gate still depends on Get-FileHash.' }}
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile('{script_path}', [ref]$tokens, [ref]$errors)
if ($errors) {{ throw ($errors | Out-String) }}
$functions = @($ast.FindAll({{ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Get-FileSha256' }}, $true))
if ($functions.Count -ne 1) {{ throw 'Expected one Get-FileSha256 function.' }}
$functionPath = Join-Path ([IO.Path]::GetTempPath()) (([guid]::NewGuid().ToString('N')) + '.ps1')
[IO.File]::WriteAllText($functionPath, $functions[0].Extent.Text)
function Get-FileHash {{ throw 'Get-FileHash must not be required by the gate runtime.' }}
. $functionPath
$actualHash = Get-FileSha256 -Path '{sample_path}'
if ($actualHash -cne '{expected_hash}') {{ throw 'The End Rift gate SHA-256 helper returned the wrong digest.' }}
Remove-Item -LiteralPath $functionPath -Force
""", encoding="utf-8")
    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(probe)],
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr


def test_migration_build_scripts_bound_gradle_caches_and_grim_patch_outputs():
    scripts = ROOT / "scripts/minecraft"
    plugins = (scripts / "BuildMigrationPlugins.ps1").read_text(encoding="utf-8")
    candidates = (scripts / "PrepareMigrationCandidates.ps1").read_text(encoding="utf-8")
    grim = (ROOT / "thirdparty/grim-creative-patch/minecraft-26.3/build.ps1").read_text(encoding="utf-8")

    assert "--project-cache-dir $gradleProjectCache" in plugins
    assert plugins.index("Reset-GeneratedDirectory -Path $gradleProjectCache") < plugins.index("& $gradle ")
    assert "--project-cache-dir $clientGradleProjectCache" in candidates
    assert candidates.index("Reset-GeneratedDirectory -Path $clientGradleProjectCache") < candidates.index("& $gradle ")
    assert grim.index("Assert-AtomicFileDestination -Path $inputJar") < grim.index("Get-FileHash -LiteralPath $inputJar")
    assert grim.index("Reset-GeneratedDirectory -Path $classes") < grim.index("javac.exe")
    assert grim.index("Assert-AtomicFileDestination -Path $outputJar") < grim.index("Copy-Item -LiteralPath $inputJar")
    assert grim.index("Assert-AtomicFileDestination -Path $outputJar", grim.index("Copy-Item -LiteralPath $inputJar")) < grim.index("jar.exe")


def test_candidate_prep_stages_exact_locked_client_mod_jars_for_acceptance():
    candidates = (ROOT / "scripts/minecraft/PrepareMigrationCandidates.ps1").read_text(encoding="utf-8")

    assert "client-mods.lock.json" in candidates
    assert "build/minecraft-26.3/client-mods" in candidates
    assert "Save-PinnedArtifact -Uri $clientMod.url -Destination $modCandidate -Algorithm SHA512" in candidates
    assert "-ExpectedHash $clientMod.sha512 -ExpectedSize ([long]$clientMod.size)" in candidates
    assert "$clientModsLock.loader -cne 'fabric'" in candidates
    assert "$fabricApiModule.version -cne $profile.fabric.api" in candidates


def test_migration_build_scripts_clear_generated_trees_before_writers():
    scripts = ROOT / "scripts/minecraft"
    coreprotect = (scripts / "BuildCoreProtectMigration.ps1").read_text(encoding="utf-8")
    plugins = (scripts / "BuildMigrationPlugins.ps1").read_text(encoding="utf-8")
    candidates = (scripts / "PrepareMigrationCandidates.ps1").read_text(encoding="utf-8")
    grim = (ROOT / "thirdparty/grim-creative-patch/minecraft-26.3/build.ps1").read_text(encoding="utf-8")

    assert coreprotect.index("Reset-GeneratedDirectory -Path $buildOutput") < coreprotect.index("& $maven ")
    assert "Copy-PinnedArtifactAtomically -Source $jar -Destination $destination" in coreprotect
    assert "Remove-Item -LiteralPath $artifactBackup" in coreprotect
    for path in ("$pluginOutputRoot", "$gradleProjectCache", "$localProjectCache"):
        assert f"Reset-GeneratedDirectory -Path {path}" in plugins
    assert plugins.index("Reset-GeneratedDirectory -Path $pluginOutputRoot") < plugins.index("& $gradle ")
    for path in ("$clientBuildOutput", "$clientGradleProjectCache", "$clientLocalProjectCache"):
        assert f"Reset-GeneratedDirectory -Path {path}" in candidates
    assert candidates.index("Reset-GeneratedDirectory -Path $clientBuildOutput") < candidates.index("& $gradle ")
    assert grim.index("Reset-GeneratedDirectory -Path $classes") < grim.index("javac.exe")
    assert grim.index("Remove-Item -LiteralPath $outputJar") < grim.index("Copy-Item -LiteralPath $inputJar")


def test_coreprotect_artifact_copy_preserves_previous_candidate_until_verified(tmp_path):
    shell = shutil.which("pwsh") or shutil.which("powershell")
    if not shell:
        import pytest
        pytest.skip("PowerShell artifact publication requires a Windows verification host")

    helper = (ROOT / "scripts/minecraft/DownloadPinnedArtifact.ps1").as_posix().replace("'", "''")
    source = tmp_path / "source.jar"
    destination = tmp_path / "CoreProtect.jar"
    source_bytes = b"verified CoreProtect candidate"
    previous_bytes = b"previous verified candidate"
    source.write_bytes(source_bytes)
    destination.write_bytes(previous_bytes)
    expected = hashlib.sha512(source_bytes).hexdigest()
    source_ps = str(source).replace("'", "''")
    destination_ps = str(destination).replace("'", "''")
    script = tmp_path / "publish.ps1"
    script.write_text(f"""$ErrorActionPreference = 'Stop'
. '{helper}'
$source = '{source_ps}'
$destination = '{destination_ps}'
$failed = $false
try {{ Copy-PinnedArtifactAtomically -Source $source -Destination $destination -Algorithm SHA512 -ExpectedHash ('0' * 128) -ExpectedSize {len(source_bytes)} }}
catch {{ $failed = $true }}
if (-not $failed) {{ throw 'Wrong pinned digest was accepted' }}
if ([IO.File]::ReadAllText($destination) -cne 'previous verified candidate') {{ throw 'Previous candidate was changed after verification failure' }}
$backup = Copy-PinnedArtifactAtomically -Source $source -Destination $destination -Algorithm SHA512 -ExpectedHash '{expected}' -ExpectedSize {len(source_bytes)}
if ([IO.File]::ReadAllText($destination) -cne 'verified CoreProtect candidate') {{ throw 'Verified candidate was not published' }}
if (-not $backup -or [IO.File]::ReadAllText($backup) -cne 'previous verified candidate') {{ throw 'Previous candidate was not retained for receipt commit' }}
if (@(Get-ChildItem -LiteralPath (Split-Path -Parent $destination) -Filter 'CoreProtect.jar.candidate.*.tmp').Count -ne 0) {{ throw 'Temporary candidate was not cleaned up' }}
""", encoding="utf-8")

    result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                            capture_output=True, text=True, timeout=30)

    assert result.returncode == 0, result.stdout + result.stderr
