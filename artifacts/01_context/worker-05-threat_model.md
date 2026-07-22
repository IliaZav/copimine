# Worker 05 threat model (round 02)

## Scope and deployment surfaces

This review covers the CopiMine snapshot as a multi-component Minecraft service:

- `admin-web/backend/main.py` is a FastAPI administrative and player panel. It owns admin/player authentication, cookies and CSRF, public status/CMS/treasury/election projections, RCON and service control, filesystem-backed backups, plugin/economy/election APIs, and PostgreSQL/SQLite access.
- Paper/Bukkit plugins provide the in-game administration, economy, election, world, and narcotics workflows. Minecraft commands, GUI actions, plugin events, and database calls cross the panel/plugin boundary.
- The Fabric client and server-to-client payloads handle shader/resource-pack runtime state and visual effects.
- `deploy/`, `release/`, and `scripts/` contain shell and PowerShell release, rollback, archive extraction, database restore, and service-management tooling. Several paths are intended to run as root or an administrator.
- Discord bridge/bot integrations, YooKassa payment callbacks, public downloads, and resource packs are external integration surfaces.

## Trust boundaries and attacker capabilities

The review assumes an unauthenticated Internet user can call public HTTP endpoints, login/recovery endpoints, and public downloads; an authenticated player can operate their own account and bank/cabinet; panel administrators have role-scoped management authority; Minecraft players can invoke exposed commands and trigger plugin events; Discord/payment providers can send integration messages; and an operator or automation can supply release archives, environment files, database dumps, and configuration to deployment scripts. A crafted archive or request is considered attacker-controlled when it can reach an operator-facing script or public HTTP entry point without an additional trusted provenance check.

Protected assets include panel/admin credentials, player accounts and session cookies, bank/donation/election balances and immutable ledgers, RCON and service-control authority, filesystem secrets/configuration/world data, PostgreSQL integrity, provider/bot tokens, and package/release supply-chain integrity.

## Security invariants

1. Authentication must require a secure transport (or an explicitly bounded local exception); session and refresh cookies must be secure and rotation must be single-use/atomic.
2. CSRF and role checks must protect state-changing panel and player actions; public projections must not disclose private player or financial metadata.
3. Player ownership and admin-role checks must hold across all API, plugin, and database paths.
4. Archive extraction must validate every member for absolute paths, `..` traversal, and links before any write, including scripts running with root/admin privilege.
5. SQL identifiers and values, filesystem paths, shell arguments, and plugin payloads must be constrained to safe allowlists/parameterization.
6. Payment/provider callbacks, plugin API keys, Discord bridges, and resource-pack/mod downloads must authenticate their source and preserve ledger integrity.
7. Secrets, startup diagnostics, internal paths, and deployment metadata must not cross an unauthenticated boundary.

## Review posture

The assigned rank and deep-review worklists were read exhaustively (2,233 unique rows each; all repository files were readable). Findings in this worker are independent round-02 discoveries only. Validation and attack-path analysis remain explicit deferred closures for the coordinator's later phases.

Repository: target_sha256_850966f902d9016af18b00a3cb5dc36970fd95ed0f51dedec2e3208bf4c4c113
Version: codex-security-snapshot/v1:sha256:ee4f16137fa3ca57c0d7c29a1cfd0dfc4a224fa5048cbfa01581805d654ff39d2
