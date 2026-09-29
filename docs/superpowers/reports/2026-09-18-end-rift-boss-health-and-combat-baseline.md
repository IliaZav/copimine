# End Rift boss health and combat baseline

Date: 2026-09-18
Environment: local Paper runtime (`local-runtime/end-rift-server`)
Branch: `codex/end-rift-event`

## Reproduced boundary

The local RCON status sample recorded the disposable boss as:

```text
state=VICTORY_PROCESSING
generation=1172
boss=68b56dc8-a3e0-4158-acf9-d2944248f5d2 hp=5000/5000 physical=5000/5000
bossPhase=AWAKENING
victory=BOSS_DEATH_CONFIRMED
```

The server therefore already had one real health value and a persisted configured
maximum. The reported `1024` display is a client-side attribute presentation
boundary: the native client entity value can be clamped even when the server
and the native Bukkit entity hold `5000` or more. This is why the repair keeps
the server health transaction unchanged and projects only a server-authoritative
`END_BOSS_BAR` snapshot onto the UUID-bound guardian.

## Existing damage boundary

Before this repair, accepted damage already committed to `LivingEntity#setHealth`
after cancelling the Bukkit event, but it did not emit a single structured
presentation decision. Guardian-shield rejection was also duplicated across
different damage routes, and ordinary hits had no consistent red flash, hurt
animation, impact particle, sound, or bounded recoil contract.

## Repair gates

- `EndRiftBossHealthProjectionTest` covers the bound guardian, ordinary entities,
  missing/invisible snapshots, and a mismatched boss UUID.
- `BossHitFeedbackPolicyTest` covers accepted melee/projectile hits, shield
  blocks, phase immunity, cinematic immunity, built-in sound IDs, and recoil
  bounds.
- `BossAnimationPriorityPolicyTest` covers the rule that HURT cannot replace
  DYING, FINAL_STRIKE, PHASE_TRANSITION, GROUND_SLAM, or CHEST_STRIKE.
- The native resource-pipeline gate remains required: client build, resource
  pack build, generated pose parity, runtime Paper probe, and native visual/audio
  evidence must all pass before release closure.

The test boss UUID above is disposable local evidence only; it is not a
production entity or a release configuration.
