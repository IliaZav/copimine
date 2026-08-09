# Public Pages Copy And Design Audit

## Goal

Remove service-facing, release-facing, and AI-like filler copy from every public page while keeping the existing CopiMine visual language, useful player information, and responsive behavior intact.

## Design decision

Use a copy-first cleanup instead of a visual rewrite. The current public shell, green/teal token palette, Minecraft artwork, card system, navigation, and mobile menu are coherent and should remain. The audit evidence shows that the main problem is content density and technical wording, not the foundation of the layout.

Public pages will show what a player needs to decide or act:

- server address, current availability, version, election result, president, published laws, shop contents, and download actions;
- friendly modpack facts: Minecraft version, Fabric loader, number of included mods, and external downloads only when the player must install something separately;
- short recovery actions on 404 and load-error pages.

The following remain non-public or deployment-only:

- archive SHA values, release hashes, archive timestamps, source commits, payload manifests, file paths, and release verification details;
- API, route, health-check, deployment, and old-build explanations;
- timestamps presented only to prove that an API refreshed, rather than to explain a player-facing event.

## Page behavior

### Home and server

Keep the address, live status, president, current published laws, election summary, treasury balance, and treasury history. Remove archive verification copy and the treasury `Обновлено ...` line. A failed API response must remain actionable but short and player-readable.

### Elections

Keep approved candidates, vote totals, stage, and the in-game voting explanation. Remove every `Только просмотр` label and the generated timestamp. Retain a clearly labeled refresh action because it is a user action; its loading state may say `Обновляем данные`, but it must not expose a technical refresh timestamp.

### Modpack

Keep the download action, Minecraft version, Fabric loader, included mod count, friendly component names/versions, and a clear note when an extra download is genuinely required. Remove SHA, archive size, archive modification date, raw `mods/*.jar` paths, and license labels from the player-facing cards. The complete release metadata continues to be generated in `deploy/release_manifest.json` and remains available to administrators.

### Shops, sign-in, registration, and cart

Keep copy that explains payment, account, whitelist, PIN, and in-game delivery because it prevents user mistakes. Remove no functional instruction merely because it is explanatory. Shorten repeated headings only where the same instruction is already visible in the adjacent action.

### 404 and load-error pages

Use one clear explanation, one primary recovery action, and one secondary navigation action. Remove references to old cabinet versions, internal routes, API handlers, health-checks, and “full contour” troubleshooting. These pages are for recovery, not operations diagnostics.

## Visual and accessibility requirements

- Preserve the existing type scale, contrast tokens, card borders, and responsive grid.
- Keep the public navigation usable at the existing mobile breakpoint; do not introduce a horizontal scroll region.
- Allow long law and product text to wrap inside its card using `min-width: 0`, `max-width: 100%`, and `overflow-wrap: anywhere` where an unbroken user-provided string can occur.
- Keep semantic headings, link/button labels, and visible focus styles.
- Empty and unavailable states must say what is missing and what the player can do next, without exposing implementation details.

## Verification contract

1. Add source-level contracts that reject forbidden public copy and release metadata in player-facing templates/renderers while allowing it in admin/deploy files.
2. Run JavaScript syntax checks and the existing public-page, election, inventory, release, and wipe contract suites.
3. Build the complete release and validate its signed payload manifest.
4. Capture fresh desktop screenshots for all public routes and mobile screenshots for the home, elections, shops, and modpack routes. Reject any screenshot with clipped text, horizontal overflow, loading state, or stale forbidden copy.
5. Compare the installed server payload hashes with the signed release manifest and fetch each public route over HTTPS.
6. Before the destructive game wipe, create the database dump, counts, manifest, and world snapshot described in `docs/deploy/COPIMINE_GAME_WIPE_MANIFEST_RU.md`. Preserve website accounts, whitelist, `ops.json`, `server.properties`, prices/catalogs, and brewing configuration; wipe only the enumerated election, AR/economy/artifact runtime, brewing-player runtime, and world data.
