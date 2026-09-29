# End Rift Local No-Auth Live Probes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the isolated End Rift live probes run when AuthMe is intentionally disabled, without weakening player-presence checks or changing production authentication.

**Architecture:** The three local bot-driven probes detect AuthMe from the live `plugins` response. With AuthMe enabled, their current registration and login-wait behavior remains intact; without it, they skip only AuthMe commands and login markers, tell the bot not to register, and still require every test player to appear in the server player list. Empty or unrecognized plugin responses fail closed. Every bot-driven local probe pins clients to its checked loopback host and port, and validates the configured and active Minecraft and RCON listeners are loopback-only.

**Tech Stack:** PowerShell local probes and behavior tests, Mineflayer offline clients.

**Spec:** `C:\Users\zavod\.codex\attachments\2145ac26-adb8-4728-ac00-94c8c777688e\pasted-text-1.txt`; user request to disable AuthMe on the test server to avoid login.

## Global Constraints

- Operate only on the isolated `local-runtime/end-rift-server` with `environment: local` and ports `25566`/`25576`.
- Never change `minecraft/server` or production authentication configuration.
- Never enter, store, or reuse a user AuthMe password; disabled-AuthMe mode uses offline bot clients without registration.
- Keep AuthMe-enabled test behavior unchanged.
- AuthMe-disabled probes must still wait for the actual players to join and must restore temporary bot environment variables and clean up spawned clients.
- Require exactly one `server-ip=127.0.0.1` and `rcon.ip=127.0.0.1` setting plus active loopback-only Minecraft and RCON TCP listeners before local bot probes run.
- Pin bot host/port environment variables to the validated local endpoint during a probe, then restore the caller's values.

## Review Focus

- AuthMe is absent from the plugin list: skip only AuthMe RCON commands and login markers, not player-online checks.
- An empty or unrecognized plugin response is unknown and must stop the probe instead of silently disabling authentication steps.
- The disposable no-auth smoke must refuse an empty, wildcard, missing, or duplicate `server-ip` setting.
- Every bot probe must also refuse missing/duplicate/non-loopback RCON configuration, inactive listeners, and wildcard/external active listener addresses.
- Live bots must ignore inherited remote host/port variables while connecting and restore the original values afterward.
- AuthMe is loaded: retain the current registration, login, and profile-settle sequence, and explicitly clear inherited no-auth mode.
- Other plugin names containing similar text must not be mistaken for AuthMe.
- Bots must not send `/register` or `/login` when AuthMe is disabled.
- Probe failure and `finally` cleanup must still restore environment variables and temporary event entities.

---

### Task 1: Make live probe AuthMe optional

**Files:**
- Create: `tests/EndRiftLocalAuthMode.ps1`
- Create: `tests/EndRiftLocalAuthModeTest.ps1`
- Modify: `tests/RunEndRiftOfficialTwoPlayerLive.ps1`
- Modify: `tests/RunEndRiftVisualFivePlayerLive.ps1`
- Modify: `tests/RunEndRiftWave6Wave7BoundariesLive.ps1`
- Modify: `tests/RunEndRiftEventChecks.ps1`
- Modify: `tests/LocalEndRiftMobCombatBot.js`
- Create: `tests/RunEndRiftLocalNoAuthSmoke.ps1`

**Interfaces:** `EndRiftLocalAuthMode.ps1` exposes `Test-EndRiftLocalAuthMeEnabled([string]$PluginListOutput) -> [bool]` and `Test-EndRiftLocalServerBind([string[]]$ServerPropertiesLines) -> [bool]`. The three probes use their existing `Invoke-LocalRcon` functions to read `plugins`, record an explicit disabled-mode marker, and continue using their existing player-online waits. No product plugin API changes.

- [x] Add PowerShell behavior tests for AuthMe enabled/disabled, a similarly named plugin, empty/unrecognized responses, and strict local loopback binding; wire them into `RunEndRiftEventChecks.ps1`.
- [x] Run the new test and confirm it fails because the auth-mode helper does not exist yet.
- [x] Add the helpers and use them in the three runners and bot; support the live multiline RGB-colored plugin response, fail closed for an unknown plugin response, clear inherited no-auth mode when AuthMe is enabled, guard only AuthMe-specific setup and wait steps, and preserve unconditional online-player waits.
- [x] Run the targeted PowerShell behavior test, then `tests/RunEndRiftEventChecks.ps1 -SkipBuilds`.
- [x] Run `tests/RunEndRiftLocalNoAuthSmoke.ps1`, inspect its runtime evidence, and confirm the static showroom was preserved.
- [x] Review the initial 11-file diff and commit only the plan, smoke evidence, behavior tests/helper, gate wiring, and three probe scripts.

### Task 2: Pin local probe endpoints and verify active listeners

**Files:**
- Modify: `tests/EndRiftLocalAuthMode.ps1`
- Modify: `tests/EndRiftLocalAuthModeTest.ps1`
- Modify: `tests/RunEndRiftLocalNoAuthSmoke.ps1`
- Modify: `tests/RunEndRiftOfficialTwoPlayerLive.ps1`
- Modify: `tests/RunEndRiftVisualFivePlayerLive.ps1`
- Modify: `tests/RunEndRiftWave6Wave7BoundariesLive.ps1`
- Modify: `tests/test_end_event_current_contract.py`
- Modify: `artifacts/end-rift-noauth-live-20260929/no-auth-smoke.log`

- [x] Add regressions for strict RCON binding, active listener scope, loopback client endpoint pinning, environment restoration, and smoke evidence propagation; observe the relevant regressions fail.
- [x] Enforce both configured and actual Minecraft/RCON loopback binding, pin every local bot probe to its checked endpoint, and restore inherited bot endpoint variables.
- [x] Run focused behavior/contract tests and the isolated no-auth smoke; confirm evidence records `127.0.0.1` for both listeners and the showroom remains unchanged.
- [x] Run `tests/RunEndRiftEventChecks.ps1 -SkipBuilds` and inspect the complete result.
- [ ] Review the final scoped diff, commit Task 2, push both task commits to `codex/end-rift-event`, and verify the remote ref equals local HEAD.
