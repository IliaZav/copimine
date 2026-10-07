# Official Wave 7 return: actual-server follow-up

The source used for the actual-server receipts below is
[9864eb2c](https://github.com/IliaZav/copimine/commit/9864eb2cc3f36872437ef965e5ddc67d077c2395)
on `codex/end-rift-event`. This is a return/inventory checkpoint for the existing
legacy trial runtime. It does not complete the V3 named trials or native
Minecraft acceptance.

## Source, review and installation

The production bootstrap regression reproduced destructive generic recovery
before the fix. Four focused regressions, the full Python suite (**979 passed,
1 skipped, 88 existing warnings**), the End Event build and registered End Rift
gate passed. These results belong to the unchanged functional source above.
Both exact-source workflows completed successfully:
[push](https://github.com/IliaZav/copimine/actions/runs/37473004728) and
[PR](https://github.com/IliaZav/copimine/actions/runs/37473013516).

CodeRabbit reviewed the immutable five-file functional checkpoint and raised
zero issues. Its sealed Codex Security review reported no confirmed
vulnerability, with explicit runtime, native and actor-resource coverage gaps.
Subsequent runtime receipts below supplement that source review; they do not
change or reopen its sealed report.

Installation checks on 2026-10-06 at 16:59 UTC found all **30** canonical/server plugin
JARs equal. End Event SHA-256:
`0cde587605e715d82708bd54b18e3e3e3a047a64bc0416d165520ccb69242cf8`.
The active-profile client JAR SHA-256 is
`6affbd3fcbbc9cf6d6778ef5d4590f47b8e51610fbf5cc02c5383cf590faa504`.
HTTP returned status 200 and 28,362,838 bytes; its pack
matched the canonical build and server offer:

- SHA-1: `62e04ec32fa9da329b1fcd8db33de4eb66e71d35`.
- SHA-256: `d53d2aa1e8644be550b5fd5e296da8af7cee4f625b94b6b0f821f74a676d35a2`.

File identity and HTTP delivery do not establish client loading or rendering.

## Actual current-source receipts

| Requirement | Actual observation | Evidence level |
| --- | --- | --- |
| Official gameplay route | Fresh protocol clients completed the ritual and Waves 1–6. Wave 4 required real reflected projectile hits against all four towers; Wave 5 completed three scheduled fog cycles; Wave 6 captured its prisoner with five casters/five guards. | Actual server/protocol mechanics |
| Explicit owner return | Each original owner died, respawned outside combat, used the public entrance and enter commands, completed 40-tick staging and returned with the same event/generation/original claim. The entrance derived from the configured arena. | Actual server/protocol mechanics |
| Actual current inventory | A one-time synthetic fixture filled all 36 storage slots, four damaged armor slots and offhand. It included vanishing and binding curses. Complete freshly saved Inventory NBT was unchanged across passive death/return; no items were reinserted from a backup. The second owner's complete current inventory also matched. | Actual saved NBT, 41-slot case and second-owner case |
| Duplicate ground items | Targeted four-block death-site queries found no new item entities for either owner. | Actual scoped query; broader integration matrix remains separate |
| Bootstrap preservation | Two graceful stop/cold boot scenarios restored the same official event, generation, chamber claims and original deadline. The second automatic scenario logged `RETURN_RESTART_RECEIPT_PASS` with the unchanged original deadline. | Actual server startup and saved-state observation |
| Owned legacy actor count after restart | The second scenario found one owned Warden before and one after boot. | Actual scoped entity query; Echo HP and finite supplies are not covered |
| Expired all-dead window | Without a completed return before the original deadline, the attempt entered `RECOVERY_REQUIRED` with `WAVE7_RETURN_GRACE_EXPIRED`; no success or boss was awarded. | Actual server state/logs |
| Cold restart followed by successful owner admission | The second scenario passed cold preservation but its fresh minimal clients never spawned. Read-only protocol observation confirmed they stayed in `CONFIGURATION` after `add_resource_pack`; they have no actual resource-pack loader. | BLOCKED fixture admission; no positive post-restart owner return receipt |
| Valid/destroyed bed and quit/reconnect | Prepared public-command/actual-bed-use probes require a live unfinished official claim. | Pending actual execution |

The clients had elevated health, buffs and test-only positioning aids. The
first current-source route needed two additional approaches to owned guard
positions, and the retry needed three, because the diagnostic client otherwise
attacked a shielded caster.
Damage and objective completion still came from actual client attack packets.
These observations do not prove natural navigation, ordinary-health balance,
animation, sound, texture loading or rendering.

## Preserved failed probes and transport limitations

The first automatic restart probe ended after its exact Paper process stopped
because it checked TCP listeners immediately, without waiting for socket
drain. The private retry waits up to ten seconds for owned sockets to close and
refuses an unrelated live port owner. The gameplay deadline/assertions were
not extended or weakened. Keep the first failed scenario as evidence; it is
not a successful positive restart/admission matrix.

The second automatic run reached a real cold boot and passed preservation and
owned-actor-count assertions. It then failed its fresh-client spawn assertion.
Packet names/state observed without any resource-pack acknowledgement showed
the required pack offer, configuration keepalives, and no transition to PLAY.
An attempted synthetic pack-loaded acknowledgement fixture was rejected by
automatic approval review with `blocked by policy`, without a more specific
reason; it was not executed. No required-pack, authentication or anti-cheat
setting was relaxed. The prepared bed and quit/reconnect probes were not run,
and four passive diagnostic targets were not successfully spawned in this run.

The repository RCON collector returns one response packet. Long entity JSON
can therefore be truncated even when the server has a complete response. The
private collector joins bounded packets through a response boundary; it was
checked against a complete response larger than 4 KiB. Complete saved NBT,
rather than shortened vanilla command text, proves the inventory comparison.

Private receipts remain under `artifacts/end-rift-waves/20261006/`, including
`wave7-bootstrap-automatic/` and its fresh retry. They contain only dedicated
synthetic account diagnostics and are not publication/review inputs. Do not
upload worlds, credentials, real player inventories, profiles or skins.

## Outstanding V3 acceptance

The actual actors remain `legacy-four-trials`. The named admission foundation
does not activate personal Echo, Marksman or Archmage. Tasks A–H still require
their runtime integration, private attribution, finite copied resources,
vanilla player presentation, authored actor actions and durable actor
HP/resource receipts. Preserve the existing boss gateway boundary.

For manual continuation, use the current isolated server with the actual Fabric
profile and successfully loaded required pack. Start a fresh official attempt;
keep an original unfinished Wave 7 claim. Die with current armor/offhand/items,
confirm normal bed or destroyed-bed fallback, then use `/cmend return` and
`/cmend return enter`. Record the two-second staging, unchanged claim and item
counts. Repeat after quit/reconnect and a cold restart within the original
120-second all-dead window; also let that original window expire and confirm
return is refused without success or boss handoff. This is an outstanding
checklist, not evidence that these manual cases were executed.

**NATIVE_VERIFIED: none for this checkpoint.** Native layout/collision/action
footage, the two-player and five/six-player ordinary-health runs, and measured
before/after server/client performance remain open. An elevated-health
protocol run is not a substitute for those requirements.
