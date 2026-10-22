# End Rift 1–7 Repair Plan

**Checkout:** `D:\Desktop\Copimine\copimine-source-of-truth`
**Branch at plan time:** `codex/copimine-source-of-truth-20261020`
**Base SHA:** `88cd812a62fff626a760a6c00e4ea351d9bcc3e0`
**Execution owner:** the root Codex session only; no subagents.

## Authority and preservation boundaries

The user's latest direct instruction and the front section of
`CopiMine_Final_Repair_DB_Preserved_2026-10-21.md` govern conflicts with the
older goal objective and its embedded master prompt. Preserve the existing
PostgreSQL cluster, credentials, private environment files, plugin data,
primary runtime and user-built arena. Do not download PostgreSQL, rotate
passwords, reinitialize or reset a database/schema/world, run destructive
tests against a shared database, or replace JARs under a running Paper
process. Do not alter boss or Kagune behavior. Keep `nativeVerified=false`
without inspected graphical evidence.

## Implementation and evidence gates

1. **Record the live baseline.** Reconfirm branch, HEAD, tracked/untracked
   state, remotes, all worktrees, PostgreSQL ownership/readiness, both Paper
   process identities, active worlds, installed JARs, pinned versions and
   logs. Preserve pre-existing untracked client build assets. Do not start a
   duplicate server or run an event against a shared data store.

2. **Separate PostgreSQL provisioning from reuse.** Keep explicit first-time
   initialization restricted to a new, empty, canonical fixture. Add a
   non-provisioning ensure/validate path for an initialized cluster: validate
   path ownership, `PG_VERSION`, private credentials, cluster PID/data path,
   loopback binding, SCRAM, port, database, role, schema, ownership and
   authenticated identity with read-only SQL. Start only the owned stopped
   cluster after proving its port is free. Check the Paper lifecycle lock and
   occupied Paper port before any mutation. A duplicate, foreign port, damaged
   credential file, populated directory without `PG_VERSION`, or uncertain
   owner must fail closed. `ValidateOnly` remains read-only and distinguishes
   inspecting a verifiably owned running server from a preflight for a new
   start. Add disposable-fixture tests with process/SQL spies covering
   initialized-running, initialized-stopped, duplicate-start, wrong-port,
   missing/corrupt credentials, nonempty uninitialized data and interrupted
   provisioning; assert no DDL, password writes or private-file byte changes
   on reuse.

3. **Make database diagnostics non-secret by default.** Change the exported
   environment assertion to return only an allowlisted sanitized projection
   or no value. Exercise a dummy sentinel through pipeline output, verbose and
   error streams, JSON serialization and exceptions. Preserve validation
   behavior and never use live credentials in tests.

4. **Trace and restore authenticated V4.** Follow existing local key-file
   configuration through launcher, JVM inheritance, loader, signer service,
   challenge, AuthMe/session eligibility and bot handshake. Record safe
   reason codes for missing configuration/file, unreadable or invalid key,
   wrong key type, trust mismatch, unavailable signer and stale readiness.
   Keep signatures, pin verification, replay protection and authentication
   intact; never invent or print key material. If the trusted private key is
   absent, finish code and isolated positive/negative adapters and record the
   exact external prerequisite rather than claiming live readiness.

5. **Reconcile build and runtime provenance.** Re-run the five listed failing
   tests before changing any expectation. Trace source to compiler output,
   candidate lock/receipt, installed JAR and the JVM that loads it. Use the
   official installer and a normal Paper stop/start only when its managed
   console and world-save completion can be verified. Preserve AuthMe,
   AuthEffects, database-backed plugins and the current arena. Do not enable
   permissive local-build or hash-bypass modes.

6. **Repair gameplay in small TDD slices.** Inspect the current design docs,
   policies, adapters, recovery paths and tests for each reported defect.
   Implement one root cause at a time, with focused tests first. Prioritize
   Wave 6 guard role/giant ownership, role-specific attack/navigation,
   committed cast targeting, non-teleport leash recovery, acquisition
   hysteresis and Sphere hazard leases; then close remaining Wave 1–7 gaps
   against the approved mechanics and client/resource-pack contracts. Edit
   the central event adapter sequentially under one owner.

7. **Verify by evidence level.** Run the focused regressions after each
   change, then complete applicable PowerShell, Python, Java/Paper, Node bot,
   Fabric client, protocol, pack and artifact checks. Use isolated disposable
   fixtures for destructive recovery tests. Run real protocol bots only
   against a separately identified loopback test runtime after proving
   database/world isolation. Cover 2/5/10/11/20 participant thresholds and
   every wave the runtime safely supports; report partial coverage explicitly.
   Retain exact commands, exit codes, hashes and logs in a Markdown and
   machine-readable evidence ledger. Do not promote policy tests to adapter,
   bot, server-startup or visual evidence.

8. **Review and finish safely.** Review the complete diff, run `git diff
   --check`, inspect secret-free artifacts and locks, run available security
   and dependency scans, reconcile findings, and commit only task-owned
   changes on the authoritative branch after checking divergence. Do not
   merge to production. Produce the requested Russian final matrix, exact
   remaining blockers, reproducible commands and the detailed manual F2
   checklist. Native visual acceptance remains pending under headless-only
   execution.

## Initial gates before code changes

- Confirm a failing regression test for secret-safe validation.
- Confirm a failing reuse-path test that spies on provisioning and SQL calls.
- Identify the actual active server's owner/console and database consumers.
- Inspect the bridge key configuration and existence metadata without reading
  secret contents.
- Read the current End Rift design/acceptance docs and current test-runner
  parameters before changing wave behavior.
