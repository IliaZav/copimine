# End Rift Local No-Auth Live Probes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the isolated End Rift live probes run when AuthMe is intentionally disabled, without weakening player-presence checks or changing production authentication.

**Architecture:** The three local bot-driven probes detect AuthMe from the live `plugins` response. With AuthMe enabled, their current registration and login-wait behavior remains intact; without it, they skip only AuthMe commands and login markers, tell the bot not to register, and still require every test player to appear in the server player list. Empty or unrecognized plugin responses fail closed. The disposable no-auth smoke also requires one explicit `server-ip=127.0.0.1` setting.

**Tech Stack:** PowerShell local probes and behavior tests, Mineflayer offline clients.

**Spec:** `C:\Users\zavod\.codex\attachments\2145ac26-adb8-4728-ac00-94c8c777688e\pasted-text-1.txt`; user request to disable AuthMe on the test server to avoid login.

## Global Constraints

- Operate only on the isolated `local-runtime/end-rift-server` with `environment: local` and ports `25566`/`25576`.
- Never change `minecraft/server` or production authentication configuration.
- Never enter, store, or reuse a user AuthMe password; disabled-AuthMe mode uses offline bot clients without registration.
- Keep AuthMe-enabled test behavior unchanged.
- AuthMe-disabled probes must still wait for the actual players to join and must restore temporary bot environment variables and clean up spawned clients.

## Review Focus

- AuthMe is absent from the plugin list: skip only AuthMe RCON commands and login markers, not player-online checks.
- An empty or unrecognized plugin response is unknown and must stop the probe instead of silently disabling authentication steps.
- The disposable no-auth smoke must refuse an empty, wildcard, missing, or duplicate `server-ip` setting.
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
- [ ] Review the exact 11-file diff, commit only the plan, smoke evidence, behavior tests/helper, gate wiring, and three probe scripts, then push to `codex/end-rift-event`.
