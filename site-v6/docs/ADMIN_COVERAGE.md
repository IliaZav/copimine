# Old navigation -> consolidated v5

| Previous area | Current destination | Review implementation | Live dependency |
|---|---|---|---|
| dashboard | Overview | Queues and actionable local records | Health/ticket/order API |
| stats | Statistics | Charts from stored records / CSV | Real metrics export |
| players + inventories | Players | Searchable head dropdown, profile, native 41-slot inventory, version imports | UUID/skin/inventory bridge |
| requests + investigations + anticheat | Moderation | Cases, evidence, local decisions and rollback preview | CoreProtect/Grim/permissions adapters |
| logs | Logs | Readable/raw grouped technical stream, filters, import/export, local event updates/file following | Authenticated SSE and parser adapters |
| audit | Team / audit and common logs | All old/new audit callers bridged once | Immutable backend audit |
| shops + artifacts | Shop | Full 41-record admin catalogue, 31 public, price/history/order/delivery | Authoritative catalogue and game grants |
| economy + donation | Finance | Accounts, local ledger, payments, corrections/refund workflow, treasury | Real transactional DB/payment provider |
| elections | World / elections + player Elections | Nomination, programme, review, stages, result imports | Existing election-core and RP rules |
| treasury | Player Treasury + Finance | Balance and transactions | Existing economy-core ledger |
| sources + server | System | Config/schema views, job statuses, integration states, backups | Deployment-specific APIs |
| admins + security | Team and access | Roles, local sessions/devices, recovery scopes/outbox | Backend RBAC/MFA/session service |
| hidden plugin recipes | Recipes | Texture-backed ingredient picker, local draft/history/publish, private action logs | Real cauldron/effect schema integration |
| CMS + site settings | Content | Draft/publish/rollback, media, FAQ, pages, download bindings | Durable CMS backend/storage |
| pin/password helpers | Players / login assistance | One-use verified-contact recovery demo, no old secrets displayed | Account/plugin APIs + mail/SMS |
| launcher | Settings / devices and request confirmation | Local one-use request UI / revoke | Secure backend device grant |

See FEATURES.json for all actual routes and aliases. The prototype retains useful old workflows and removes duplicate top-level entries, but is not a claim that every server-side operation has been reimplemented. Operations that would modify real gameplay remain explicitly unconnected. Views with no authorized measurements show no data rather than fabricated 'health readiness' charts.
