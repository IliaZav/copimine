# Artifacts Shop Deep Audit Plan

## 1. Establish a baseline

- [x] Run all artifacts, donation, AR shop, and cart validators.
- [x] Compile the artifacts plugin and record warnings separately from failures.

## 2. Trace state-changing paths

- [x] Review shop GUI actions, cart and purchase transactions.
- [x] Review donation claim, delivery, official-item validation, and ownership changes.
- [x] Review every terminal item-loss handler and the durable loss-journal reconciler.

## 3. Repair confirmed defects

- [x] Add a focused regression test that fails before each change.
- [x] Apply the smallest safe code repair and rerun its test.
- [x] Check asynchronous database work and player-facing error normalization in affected handlers.

## 4. Verify and integrate

- [x] Run the complete shop-related validator set and compile the plugin.
- [x] Inspect the diff for data-destructive or payment-provider changes.
- [ ] Fast-forward the verified branch to local and remote `main` without deploying or restarting services.
