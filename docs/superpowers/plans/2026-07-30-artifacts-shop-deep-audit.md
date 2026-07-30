# Artifacts Shop Deep Audit Plan

## 1. Establish a baseline

- [ ] Run all artifacts, donation, AR shop, and cart validators.
- [ ] Compile the artifacts plugin and record warnings separately from failures.

## 2. Trace state-changing paths

- [ ] Review shop GUI actions, cart and purchase transactions.
- [ ] Review donation claim, delivery, official-item validation, and ownership changes.
- [ ] Review every terminal item-loss handler and the durable loss-journal reconciler.

## 3. Repair confirmed defects

- [ ] Add a focused regression test that fails before each change.
- [ ] Apply the smallest safe code repair and rerun its test.
- [ ] Check asynchronous database work and player-facing error normalization in affected handlers.

## 4. Verify and integrate

- [ ] Run the complete shop-related validator set and compile the plugin.
- [ ] Inspect the diff for data-destructive or payment-provider changes.
- [ ] Fast-forward the verified branch to local and remote `main` without deploying or restarting services.
