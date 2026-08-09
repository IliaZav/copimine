#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# A small, independent data-only backup for gameplay/plugin state. The full
# daily backup remains the recovery source for credentials and account data;
# this rotation deliberately contains only the allow-listed non-critical
# gameplay tables below.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../shared/common.sh"

copimine_require_root
for command in date find flock pg_dump psql realpath sha256sum stat; do
  copimine_need "$command"
done

BACKUP_ROOT="${COPIMINE_NONCRITICAL_DB_BACKUP_ROOT:-$COPIMINE_BACKUP_DIR/db-noncritical}"
LOCK_FILE="${COPIMINE_NONCRITICAL_DB_BACKUP_LOCK:-$COPIMINE_BACKUP_DIR/.noncritical-db-backup.lock}"
RETENTION_SECONDS="${COPIMINE_NONCRITICAL_DB_RETENTION_SECONDS:-86400}"
[[ "$RETENTION_SECONDS" =~ ^[0-9]+$ ]] || copimine_fail "Non-critical database retention must be numeric"

pg_host="$(copimine_env_value POSTGRES_HOST)"
pg_port="$(copimine_env_value POSTGRES_PORT)"
pg_user="$(copimine_env_value POSTGRES_USER)"
pg_db="$(copimine_env_value POSTGRES_DB)"
pg_schema="$(copimine_env_value POSTGRES_SCHEMA)"
pg_password="$(copimine_env_value POSTGRES_PASSWORD)"
pg_host="${pg_host:-127.0.0.1}"
pg_port="${pg_port:-5432}"
pg_schema="${pg_schema:-copimine}"
[[ "$pg_host" =~ ^[A-Za-z0-9_.:-]+$ ]] || copimine_fail "POSTGRES_HOST is invalid"
[[ "$pg_port" =~ ^[0-9]+$ ]] || copimine_fail "POSTGRES_PORT is invalid"
[[ "$pg_user" =~ ^[A-Za-z_][A-Za-z0-9_]{0,48}$ ]] || copimine_fail "POSTGRES_USER is invalid"
[[ "$pg_db" =~ ^[A-Za-z_][A-Za-z0-9_]{0,40}$ ]] || copimine_fail "POSTGRES_DB is invalid"
[[ "$pg_schema" =~ ^[A-Za-z_][A-Za-z0-9_]{0,40}$ ]] || copimine_fail "POSTGRES_SCHEMA is invalid"
[[ -n "$pg_password" && "$pg_password" != "CHANGE_ME" ]] || copimine_fail "POSTGRES_PASSWORD is missing"

# These are gameplay/plugin records only. Identity, credentials, whitelist
# linkage, and website CMS rows are not part of this backup contract.
candidate_tables=(
  cmv4_audit_events cmv4_bank_ledger cmv4_bank_transfers
  ar_accounts ar_batches ar_ledger ar_operations ar_transfers ar_deposits
  ar_withdrawals ar_admin_issues ar_suspicious_items ar_ground_expiration_log
  ar_money_supply_snapshots ar_atms atm_events atm_sessions atm_audit
  elections election_phases election_candidates election_applications
  election_application_reviews election_ballots election_votes election_stations
  election_results election_presidents election_decrees election_petitions
  election_audit election_voting_blocks election_visual_cleanup_queue
  election_stages polling_stations cik_chairs cik_seals candidate_applications
  candidates ballots votes rounds round_candidates president_terms president_laws
  president_law_reviews president_broadcasts president_taxes president_tax_payments
  president_tax_exemptions protected_blocks text_display_links
  artifact_item_instances artifact_shops artifact_purchases artifact_repairs
  artifact_suspicious_events artifact_audit_log artifact_pending_deliveries
  artifact_purchase_limit_resets artifact_revenue_payouts artifact_repair_events
  donation_accounts donation_balance_ledger donation_payment_sessions
  donation_purchases donation_item_claims
  narcotics_brewing_states narcotics_player_overdose narcotics_player_usage_window
  narcotics_admin_audit narcotics_item_texture_migrations narcotics_pending_refunds
  narcotics_pending_outputs narcotics_consumption_reservations narcotics_issued_instances
  admin_requests plugin_events admin_actions site_audit moderation_actions
  prank_audit system_checks repair_actions smoke_results discord_status_state
  discord_notifications_log bridge_events status_channel_snapshots
  auth_effects_disable_audit
)

mkdir -p "$BACKUP_ROOT"
chmod 700 "$BACKUP_ROOT"
exec 9>"$LOCK_FILE"
flock -n 9 || { printf '[copimine-noncritical-db-backup] another backup is running; skipped\n'; exit 0; }

stamp="$(date -u +%Y%m%d-%H%M%S)"
final_stem="copimine-noncritical-$stamp"
tmp_dir="$(mktemp -d "$BACKUP_ROOT/.${final_stem}.XXXXXX")"
cleanup() {
  local status=$?
  rm -rf -- "$tmp_dir"
  exit "$status"
}
trap cleanup EXIT

table_literals=""
for table in "${candidate_tables[@]}"; do
  table_literals+="'${table}',"
done
table_literals="${table_literals%,}"

mapfile -t existing_tables < <(
  PGPASSWORD="$pg_password" psql -X -q -h "$pg_host" -p "$pg_port" -U "$pg_user" -d "$pg_db" -Atv ON_ERROR_STOP=1 \
    -c "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname='${pg_schema}' AND tablename = ANY(ARRAY[${table_literals}]::text[]) ORDER BY tablename;"
)
(( ${#existing_tables[@]} > 0 )) || copimine_fail "No allow-listed non-critical tables exist in schema $pg_schema"

table_args=()
for table in "${existing_tables[@]}"; do
  [[ "$table" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || copimine_fail "Unsafe table name returned by PostgreSQL: $table"
  table_args+=("--table=${pg_schema}.${table}")
done

dump_tmp="$tmp_dir/data.dump"
PGPASSWORD="$pg_password" pg_dump \
  --format=custom \
  --data-only \
  --no-owner \
  --no-acl \
  --host="$pg_host" \
  --port="$pg_port" \
  --username="$pg_user" \
  --dbname="$pg_db" \
  "${table_args[@]}" \
  --file="$dump_tmp"
[[ -s "$dump_tmp" ]] || copimine_fail "Non-critical database dump is empty"

final_dump="$BACKUP_ROOT/$final_stem.dump"
final_sha="$final_dump.sha256"
final_manifest="$BACKUP_ROOT/$final_stem.manifest"
mv -- "$dump_tmp" "$final_dump"
sha256sum "$final_dump" > "$tmp_dir/data.dump.sha256"
mv -- "$tmp_dir/data.dump.sha256" "$final_sha"
{
  printf 'created_at=%s\n' "$(date --iso-8601=seconds)"
  printf 'database=%s\n' "$pg_db"
  printf 'schema=%s\n' "$pg_schema"
  printf 'format=custom\n'
  printf 'scope=data-only-allow-listed-gameplay\n'
  printf 'retention_seconds=%s\n' "$RETENTION_SECONDS"
  for table in "${existing_tables[@]}"; do
    printf 'table=%s\n' "$table"
  done
} > "$tmp_dir/data.dump.manifest"
mv -- "$tmp_dir/data.dump.manifest" "$final_manifest"
chmod 600 "$final_dump" "$final_sha" "$final_manifest"

now="$(date +%s)"
while IFS= read -r old_dump; do
  [[ -n "$old_dump" ]] || continue
  old_name="$(basename "$old_dump")"
  [[ "$old_name" =~ ^copimine-noncritical-[0-9]{8}-[0-9]{6}\.dump$ ]] || copimine_fail "Unexpected non-critical backup: $old_name"
  [[ "$old_name" == "$(basename "$final_dump")" ]] && continue
  old_mtime="$(stat -c %Y -- "$old_dump")"
  if (( now - old_mtime > RETENTION_SECONDS )); then
    resolved_old="$(realpath -e -- "$old_dump")"
    [[ "$resolved_old" == "$BACKUP_ROOT/"* ]] || copimine_fail "Retention target escaped backup root"
    old_stem="${old_name%.dump}"
    rm -f -- "$resolved_old" "$BACKUP_ROOT/$old_stem.dump.sha256" "$BACKUP_ROOT/$old_stem.manifest"
  fi
done < <(find "$BACKUP_ROOT" -maxdepth 1 -type f -name 'copimine-noncritical-*.dump' -print | sort -r)

printf '[copimine-noncritical-db-backup] created %s (%s tables)\n' "$final_dump" "${#existing_tables[@]}"
