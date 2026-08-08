# Zhuzevo Use and Chups Jump Boost Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore use of validated Zhuzevo when a prior listener cancelled only the underlying block interaction, and add Jump Boost III to Chups without changing elections or brewing.

**Architecture:** Keep `NarcoticItemFactory.resolveOfficial` and the existing database-first consumption transaction as the only authorization and durability gates. Reorder the already-authorized narcotic branch ahead of the generic cancelled-event guard, while still denying the clicked block. Add the Chups effect only in `config.yml`; do not modify `OverdoseService` or cauldron code.

**Tech Stack:** Java 21, Paper API 1.21.1, PowerShell build scripts, Python/pytest contract tests, PostgreSQL-backed Paper plugin runtime, SSH/SCP deployment.

## Global Constraints

- Do not modify election source/JAR, website, economy, database schema, `CauldronBrewingService`, recipe handling, or brewing persistence.
- Do not remove official-item provenance, HMAC, issued-instance registration, reservation, or exact physical-consumption checks.
- Do not add inventory resynchronization or another anti-dupe handler.
- Preserve all unrelated dirty worktree changes and stage only files listed in the tasks.
- Never wipe worlds or the database; make timestamped remote backups before deployment.
- Verify local/remote SHA-256 and `copimine-minecraft` status before claiming deployment.

---

### Task 1: Add regression contracts for the two requested behaviors

**Files:**
- Modify: `tests/test_requested_gameplay_fixes.py` after the existing Chups/brewing config contract near line 143.

**Interfaces:**
- Consumes: `read()` and `between()` helpers already defined in the test file.
- Produces: two pytest contracts that fail against the current event ordering and current Chups config.

- [ ] **Step 1: Write the failing tests**

Add these exact tests without changing existing brewing assertions:

```python
def test_validated_narcotic_use_precedes_generic_cancelled_event_guard():
    plugin = read("copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java")
    official_index = plugin.index("if (official != null)")
    cancelled_index = plugin.index("if (event.isCancelled())")
    official = plugin[official_index:cancelled_index]

    assert official_index < cancelled_index
    assert "overdoseService.isStateReady(player)" in official
    assert "event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)" in official
    assert "database.reserveConsumption" in official


def test_chups_has_jump_boost_three_in_normal_effects():
    config = read("copimine-narcotics/config.yml")
    chups = between(config, "  chups:", "  borshevik:")
    normal = between(chups, "    normal_effects:", "    overdose_effects:")
    assert re.search(
        r"^      - type:JUMP_BOOST,amplifier:2,duration_seconds:90$",
        normal,
        flags=re.MULTILINE,
    )
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run from `D:\Desktop\Copimine\copimine-main`:

```powershell
python -m pytest -q tests/test_requested_gameplay_fixes.py -k "validated_narcotic_use_precedes_generic_cancelled_event_guard or chups_has_jump_boost_three_in_normal_effects"
```

Expected result: both new tests fail; no production source has been changed yet.

- [ ] **Step 3: Review the staged test diff**

Run:

```powershell
git diff -- tests/test_requested_gameplay_fixes.py
git diff --check
```

Confirm the diff contains only the two new contracts, then stage and commit only this test file:

```powershell
git add -- tests/test_requested_gameplay_fixes.py
git diff --cached --name-only
git commit -m "test: cover zhuzevo use and chups jump boost"
```

---

### Task 2: Apply the minimal Narcotics source/config fix

**Files:**
- Modify: `copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java:197-204`.
- Modify: `copimine-narcotics/config.yml:212-216`.
- Test: `tests/test_requested_gameplay_fixes.py` from Task 1.

**Interfaces:**
- Consumes: the existing `official` resolver, reset gate, state readiness, reservation, and exact physical consumption path.
- Produces: official narcotics are handled before the generic cancelled-event guard; non-narcotic cancelled interactions retain the existing guard; Chups receives amplifier 2 Jump Boost.

- [ ] **Step 1: Move only the generic cancelled-event guard**

Keep the `resetInProgress` block before use handling. Move the existing:

```java
if (event.isCancelled()) {
    if (official != null || (event.getClickedBlock() != null && cauldronService.isSupportedCauldron(event.getClickedBlock())
            && recipeService.canEnterCauldron(inHand))) {
        player.sendMessage(ChatColor.RED + "Взаимодействие с котлом запрещено защитой региона.");
    }
    return;
}
```

to immediately after the complete `if (official != null) { ... return; }` branch. Do not edit any line inside the official reservation/effect path except as required by this relocation. The generic guard must no longer mention `official != null`, because official items have already returned.

- [ ] **Step 2: Add the Chups effect**

Insert this exact line in the Chups `normal_effects` list after `SLOW_FALLING`:

```yaml
      - type:JUMP_BOOST,amplifier:2,duration_seconds:90
```

Do not change Chups recipe or overdose effects.

- [ ] **Step 3: Run the focused tests and verify GREEN**

Run:

```powershell
python -m pytest -q tests/test_requested_gameplay_fixes.py -k "validated_narcotic_use_precedes_generic_cancelled_event_guard or chups_has_jump_boost_three_in_normal_effects"
```

Expected result: both tests pass.

- [ ] **Step 4: Confirm the source scope before building**

Run:

```powershell
git diff --name-only -- copimine-election-core copimine-narcotics/cauldron
git diff --stat -- copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java copimine-narcotics/config.yml
```

Expected result: no diff under election or cauldron paths; only the Narcotics listener and config are changed by this task.

---

### Task 3: Build and verify the Narcotics artifact

**Files:**
- Modify through build output only: `copimine-narcotics/CopiMineNarcotics.jar` and the local runtime copy under `minecraft/server/plugins/CopiMineNarcotics.jar`.

**Interfaces:**
- Consumes: the source/config from Task 2 and the existing Paper API/dependency resolution in `build-plugin.ps1`.
- Produces: a reproducible Narcotics JAR containing the fixed handler and config.

- [ ] **Step 1: Build the plugin**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\copimine-narcotics\build-plugin.ps1
```

Expected result: `CopiMineNarcotics.jar` is rebuilt and copied to the local server plugin directory.

- [ ] **Step 2: Verify source and artifact contents**

Run:

```powershell
jar tf .\copimine-narcotics\CopiMineNarcotics.jar | Select-String 'CopiMineNarcotics.class|config.yml|plugin.yml'
Get-FileHash -Algorithm SHA256 .\copimine-narcotics\CopiMineNarcotics.jar, .\minecraft\server\plugins\CopiMineNarcotics.jar
```

Use `javap -classpath .\copimine-narcotics\CopiMineNarcotics.jar -c -p me.copimine.narcotics.CopiMineNarcotics` as a bytecode sanity check and confirm both local copies have identical SHA-256.

- [ ] **Step 3: Run the Narcotics strictness and gameplay contracts**

Run:

```powershell
python -m pytest -q tests/test_requested_gameplay_fixes.py tests/test_stackable_items_contract.py
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineNarcoticsOfficialItemStrictness.ps1
```

Expected result: all selected tests and the official-item strictness validator pass.

---

### Task 4: Review, commit, and push only the requested patch

**Files:**
- Commit: `copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java`.
- Commit: `copimine-narcotics/config.yml`.
- Commit: `copimine-narcotics/CopiMineNarcotics.jar`.
- Commit: `tests/test_requested_gameplay_fixes.py` if it is not already committed by Task 1.
- Preserve and do not stage: release metadata, economy JAR, third-party archives, probe files, election files, brewing files, and unrelated prior changes.

**Interfaces:**
- Consumes: verified source/config/JAR and the pre-existing AR patch in the worktree.
- Produces: a Git commit pushed to `origin/agent/deep-election-artifacts-audit` without force-pushing or resetting unrelated work.

- [ ] **Step 1: Inspect the exact candidate diff**

Run:

```powershell
git status --short
git diff --name-only HEAD -- copimine-election-core copimine-narcotics/cauldron
git diff -- copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java copimine-narcotics/config.yml
```

Expected result: no election/cauldron diff and only the requested Narcotics source/config changes are candidates.

- [ ] **Step 2: Stage and verify the patch list**

Run:

```powershell
git add -- copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java copimine-narcotics/config.yml copimine-narcotics/CopiMineNarcotics.jar minecraft/server/plugins/CopiMineNarcotics.jar
git diff --cached --check
git diff --cached --name-only
```

Do not stage any other path. If the test was not committed in Task 1, stage it here; otherwise leave it untouched.

- [ ] **Step 3: Commit and push**

Run:

```powershell
git commit -m "fix: restore zhuzevo use and add chups jump boost"
git push -u origin agent/deep-election-artifacts-audit
```

Expected result: both commands succeed without force-push.

---

### Task 5: Deploy through the controlled server workflow and verify runtime artifacts

**Files:**
- Remote plugin: `/opt/copimine/minecraft/server/plugins/CopiMineNarcotics.jar`.
- Remote mutable config: `/opt/copimine/minecraft/server/plugins/CopiMineNarcotics/config.yml`.
- Remote backup directory: `/opt/copimine/backups` or the configured CopiMine backup directory discovered from `common.sh`.

**Interfaces:**
- Consumes: the committed, verified local Narcotics JAR/config and the repository installer scripts.
- Produces: an installed remote plugin with recoverable old files, matching SHA-256, loaded service, and no world/database reset.

- [ ] **Step 1: Validate the installer and current service read-only**

Run locally:

```powershell
Get-Content -Raw .\deploy\ubuntu\install_release.sh
Get-Content -Raw .\deploy\shared\common.sh
```

Run remotely:

```powershell
ssh -p 2222 -o BatchMode=yes qwerty@90.188.115.155 "systemctl is-active copimine-minecraft; sha256sum /opt/copimine/minecraft/server/plugins/CopiMineNarcotics.jar; stat -c '%s %n' /opt/copimine/minecraft/server/plugins/CopiMineNarcotics.jar /opt/copimine/minecraft/server/plugins/CopiMineNarcotics/config.yml"
```

Confirm the installer does not receive `--wipe-worlds`, database reset, or any destructive flag.

- [ ] **Step 2: Create remote timestamped backups before upload**

Run this single remote command:

```powershell
ssh -p 2222 -o BatchMode=yes qwerty@90.188.115.155 "stamp=`$(date -u +%Y%m%d-%H%M%S); backup=/opt/copimine/backups/codex-narcotics-`$stamp; mkdir -p -- \`$backup; cp -p -- /opt/copimine/minecraft/server/plugins/CopiMineNarcotics.jar /opt/copimine/minecraft/server/plugins/CopiMineNarcotics/config.yml \`$backup/; test -s \`$backup/CopiMineNarcotics.jar; test -s \`$backup/config.yml; sha256sum \`$backup/CopiMineNarcotics.jar \`$backup/config.yml; printf 'BACKUP=%s\\n' \`$backup"
```

Record the printed backup path and hashes in the final report. The command
does not remove or overwrite the live files.

- [ ] **Step 3: Upload the two changed Narcotics files without append/resume**

Run these commands after calculating the local hashes:

```powershell
$stamp = Get-Date -AsUTC -Format yyyyMMdd-HHmmss
$stage = "/home/qwerty/copimine-upload/codex-narcotics-$stamp"
ssh -p 2222 -o BatchMode=yes qwerty@90.188.115.155 "mkdir -p -- '$stage'"
scp -P 2222 -o BatchMode=yes .\copimine-narcotics\CopiMineNarcotics.jar .\copimine-narcotics\config.yml "qwerty@90.188.115.155:$stage/"
ssh -p 2222 -o BatchMode=yes qwerty@90.188.115.155 "sha256sum '$stage/CopiMineNarcotics.jar' '$stage/config.yml'; stat -c '%s %n' '$stage/CopiMineNarcotics.jar' '$stage/config.yml'"
```

Compare both remote hashes and sizes with the local values before installation. Do not use `sftp put -a`.

- [ ] **Step 4: Install with the existing release entrypoint**

Use the repository's existing `install_release.sh`/unpack workflow only if its validated payload layout accepts the staged plugin-only patch. Otherwise use the same verified staging/backup procedure with an atomic `mv` of only these two plugin paths; do not invoke full replacement or any world/database operation for an unrelated release.

- [ ] **Step 5: Restart and verify the active server**

Restart only `copimine-minecraft` after the plugin files are atomically installed, then run:

```powershell
ssh -p 2222 -o BatchMode=yes qwerty@90.188.115.155 "systemctl is-active copimine-minecraft; sha256sum /opt/copimine/minecraft/server/plugins/CopiMineNarcotics.jar; grep -n 'JUMP_BOOST' /opt/copimine/minecraft/server/plugins/CopiMineNarcotics/config.yml; tail -n 120 /opt/copimine/minecraft/server/logs/latest.log | grep -E 'CopiMineNarcotics|Narcotics|Exception'"
```

Confirm the service is active, the remote JAR hash equals the local committed artifact, the config contains amplifier 2, and startup has no Narcotics exception.
