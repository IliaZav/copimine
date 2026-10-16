# End Rift transition intermission watchdog

This watchdog covers the six transition intermissions between Waves 1–7. Each
intermission asks the active, living, online encounter roster to stand on its
own distinct transition rune. The full roster must hold that arrangement
continuously for 10 seconds (200 server ticks) to advance. A player leaving or
changing runes breaks the hold and starts its progress again. The rune overlays
show the current hold state.

## Ten-minute deadline

The transition phase also has an absolute **600,000 ms (10 minute)** deadline.
It is persisted with the event state and is independent of the 10-second rune
hold. Partial rune progress does not extend the deadline. A clean server restart
keeps an existing positive deadline, so time spent offline counts toward the
same window. If a legacy snapshot has no deadline, bootstrap starts and persists
one full 600,000 ms window and logs `END_EVENT_TIMER_REPAIRED`.

## What happens at timeout

When the deadline expires, the plugin logs `END_RIFT_INTERMISSION_TIMEOUT` and
aborts the uncommitted attempt. It removes transition-rune visuals, clears the
completed wave objective and transient combat state, clears the attempt roster
and uncommitted reward bookkeeping, then rebuilds the start-rune layout. If
that setup succeeds, the phase returns to `READY_FOR_PLAYERS` and the restored
state is saved synchronously. The timeout does not advance to another wave.

If the event world is unavailable or the start-rune setup, visuals, or state
save cannot be restored, the plugin enters `RECOVERY_REQUIRED`. Check the
`END_RIFT_INTERMISSION_TIMEOUT_START_RUNE_*` log entries and resolve the recovery
condition before allowing another attempt.

## Operator evidence

The timeout warning includes the event, intermission phase, roster, the
`timeout_ms=600000` value, and `action=abort-uncommitted-attempt`. On restart,
`END_EVENT_INTERMISSION_REHYDRATED` records the restored phase and deadline.
These messages distinguish a preserved in-progress transition from a newly
repaired legacy deadline.
