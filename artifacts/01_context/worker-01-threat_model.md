# Threat model

## Overview

CopiMine is a Minecraft/Paper server application composed of Bukkit plugins for economy, elections, narcotics, artifacts, world controls, and administration, plus an `admin-web` FastAPI service and browser cabinet. The web service exposes privileged administration, player recovery/payment flows, Discord integration, RCON and deployment/backup operations backed by PostgreSQL. This round models the repository at revision `76b61cb94bf0d616b6eb3138bd7f86a38ce3283e`.

## Threat Model, Trust Boundaries, and Assumptions

Trust boundaries are: (1) Internet browsers and reverse proxy to FastAPI HTTP endpoints; (2) untrusted Minecraft players/clients to the Paper server and plugin command, inventory, chat, and plugin-channel handlers; (3) authenticated panel administrators/operators to RCON, database, filesystem, deployment, and Discord controls; (4) plugins to PostgreSQL and shared server/filesystem state; and (5) the service to external YooKassa and Discord APIs. Configuration and environment files are deployment-controlled but can alter authentication and transport behavior.

Relevant attacker inputs include HTTP methods, paths, query/body fields, headers, cookies, CSRF/origin values, player commands and item metadata/PDC, plugin-channel payloads, database/configuration values, and provider callback data. Assume TLS and host-level access are not automatically present, public routes are reachable by network attackers, and Minecraft clients can forge arbitrary client-side input. Credentials, session cookies, RCON/database access, player balances and inventory, election ballots/mandates, narcotics state, backups, deployment artifacts, and personal/payment data are security assets. Core invariants are authenticated authorization for every privileged action, confidentiality and integrity of credentials/sessions, server-side ownership and role checks for game state, payment authenticity, safe filesystem boundaries, and availability/rate limits on recovery and administrative functions.

## Attack Surface, Mitigations, and Attacker Stories

The HTTP surface includes login/refresh/logout, panel administration, player cabinet and recovery endpoints, payments/webhooks, skin/proxy and status endpoints, RCON/deployment/backup APIs, and Discord callbacks. Mitigations observed include CSRF middleware and origin checks, role-specific dependencies, explicit confirmation gates for sensitive database actions, provider-side payment verification, bounded/rate-limited recovery codes, fixed RCON command allowlists, path containment and archive-name sanitization, managed download allowlists, and plugin-channel/session/capability checks. The transport configuration is a notable exception: the login guard accepts HTTP when `ALLOW_INSECURE_HTTP_AUTH` is true or omitted for an HTTP public URL, and cookies then omit the Secure attribute.

Minecraft attack stories include a player attempting to invoke admin commands, forge menu/plugin-channel messages, alter item PDC ownership, or race election/economy transitions. Plugins generally gate commands by permissions and re-check player/session/owner state server-side. An external attacker may submit forged payment callbacks or malformed HTTP data; payment confirmation and schema validation are expected to prevent state changes. An administrator compromise would expose RCON, database, deployment and player data, so those operations require strong transport and authorization controls.

## Severity Calibration

Critical: unauthenticated or remotely exploitable compromise of administrator credentials/sessions, arbitrary RCON or database takeover, or broad economy/election/server integrity loss.

High: a network-reachable weakness that enables credential/session interception, privileged authorization bypass, payment/economy forgery, or material player-data compromise with a practical attack path. The HTTP authentication downgrade is calibrated High pending deployment validation.

Medium: scoped IDOR, meaningful information disclosure, weak recovery/rate limiting, or integrity loss requiring an authenticated or constrained attacker.

Low: defense-in-depth gaps, local-only diagnostics, nuisance denial of service, or issues without a credible security impact.

Repository: copimine
Version: 76b61cb94bf0d616b6eb3138bd7f86a38ce3283e
