# CopiMine optimization and release report

The release baseline uses a bounded `SimplePgPool`/PostgreSQL V4 ledger path
for gameplay writes. The maintained units are `copimine-admin.service`,
`copimine-discord-bot.service`, `copimine-minecraft-discord-bridge.service`
and `copimine-minecraft.service`.

Paper/Purpur chunk limits, CoreProtect logging and the Chunky preparation
workflow are covered by the repository validators. SeeMore diagnostics
exposes service health, database readiness and anticheat signals without an
unbounded scan on the main server thread.

Release acceptance includes a live Ubuntu smoke test: PostgreSQL ready, all
CopiMine jars loaded, `/api/health` green and Minecraft reachable on the
configured port. Re-run the web regression suite and the full validator set
after every deployment.
