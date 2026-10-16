# Wave 4 special attack starvation and Wave 7 return follow-ups

Baseline: `0dba4ed759d7f2ed5376a10e06aa78090078b459` on
`codex/end-rift-event`. The parent Waves 1–7 goal remains open.

## Actual failure

The isolated Paper instance used the published return plugin, SHA-256
`41c3303267eb755dbce3673d2b02cba887e315e8e654aec3b9293a836fe476ba`.
All 30 installed plugin files matched the canonical server distribution.
The HTTP pack digest and active client JAR were also checked. Two dedicated
credential-free synthetic clients followed the official roster/rune/objective
path through Waves 1–3. Their large HP and test combat buffs do not establish
normal balance or native rendering acceptance.

The attempt `1c9c0171-c323-43bc-aefe-0eb7313c16d5` activated all four Wave 4
obelisks but emitted no `RIFT_FIREBALL_LAUNCH` during the 120-second wait.
Only the same first obelisk repeatedly emitted its pulse. The protocol runner
failed and cleaned its own disposable attempt; it never reached Wave 7 return.
Private local evidence is under
`artifacts/end-rift-waves/20261005/wave7-return-runtime/`.

The actual tick adapter evaluated pulses before fire casts. A 20-tick warning
plus 24-tick recovery held the single shared reservation for 44 ticks. The
40-tick pulse schedule made the same map-first tower ready to reacquire the
slot whenever it expired. Neither shots nor other towers got authority.
`test_wave4_special_attack_fairness.py` executes that real tick, pulse and cast
code with the real coordinator; its RED run reproduced zero shots and pulses
only from the first tower. Evidence:
`artifacts/end-rift-waves/20261005/wave4-fireball-starvation-red.log`.

## Repair

Extend the existing fire director with a deterministic oldest-due choice among
tracked active tower pulse/shot candidates. Continue an already leased cast
before admitting a fresh one. Clear expired cast tokens without releasing a
different current reservation. Keep the existing one-special limit, projectile
cap, 20-tick warning, locked aim and 24-tick recovery. No world scan, scheduler,
entity subsystem, boss change or visual asset replacement is added.

The source regression requires shots and pulses from every one of four towers,
with nonoverlapping release/recovery reservations and invalidation after
coordinator cleanup or a new generation. Actual Paper reflection, completion
and subsequent Wave 7 return still require rerunning the failed official path.

## Return source follow-ups

The prior security scan explicitly left two source/performance questions open.
An executable public-command probe now reproduces both boundaries:

- With transition pads absent, the old entrance call invoked inherited floor
  block sampling before the loaded-chunk resolver. The return path now passes
  the persisted Core feet-level reference directly; pad-based height selection
  remains available while pads exist. The existing collision resolver still
  validates every actual candidate and refuses unavailable unsafe entrances.
- Canceling staging through offense immediately restored PENDING, allowing the
  same owner to start another synchronous checkpoint in the same tick. Entry
  now has its own 20-tick retry cooldown, separate from entrance travel, so
  travel followed by immediate legitimate entry continues to work. Cleanup and
  snapshot restoration clear both command cooldown maps.

RED evidence: `wave7-return-command-limits-red.log` and
`wave7-return-staging-retry-red.log` in the same private evidence directory's
parent. Both focused source regressions pass. No measured availability exploit
or new actual-server/native acceptance is inferred from these source results.
