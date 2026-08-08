-- Release hardening: bind ATM records to the block material that was present
-- when the terminal was created, and make TAX_CLOCK exemption retries safe.
ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS expected_material TEXT NOT NULL DEFAULT '';
ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS expected_model_id TEXT NOT NULL DEFAULT 'atm_terminal';
ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS expected_custom_model_data INTEGER NOT NULL DEFAULT 12002;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tax_exemptions_idempotency
  ON president_tax_exemptions(idempotency_key)
  WHERE idempotency_key <> '';
