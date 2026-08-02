-- Bank PIN hardening: persistent PIN tables must contain hashes only.
-- The temporary reset delivery blob remains one-time and is cleared on claim.

ALTER TABLE bank_pin_hashes DROP COLUMN IF EXISTS pin_sealed;
ALTER TABLE bank_account_pins DROP COLUMN IF EXISTS pin_sealed;
