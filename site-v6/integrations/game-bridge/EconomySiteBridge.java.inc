    // Trusted in-process administrative API used only after signed bridge/RBAC validation.
    // Personal ATM/PIN methods are unchanged. This is an explicit administrative
    // ledger operation with its own source, immutable request binding, and audit.
    public CompletableFuture<Map<String, Object>> siteBridgeAdjust(UUID playerUuid, String playerName,
            String currency, String mode, long amount, String operationId, String actor, String reason) {
        if (playerUuid == null || !Set.of("AR", "CM").contains(first(currency, ""))
                || !Set.of("credit", "debit", "set").contains(first(mode, ""))
                || amount < 0L || amount > 1_000_000_000L || (!"set".equals(mode) && amount == 0L)
                || operationId == null || !operationId.matches("[A-Za-z0-9_-]{8,128}")
                || playerName == null || playerName.length() > 100 || actor == null || actor.length() > 100
                || reason == null || reason.length() < 3 || reason.length() > 1000) {
            CompletableFuture<Map<String, Object>> invalid = new CompletableFuture<>();
            invalid.completeExceptionally(new IllegalArgumentException("Invalid typed administrative balance request."));
            return invalid;
        }
        String requestHash = siteBridgeMoneyHash(playerUuid, currency, mode, amount, actor, reason);
        return this.<Map<String, Object>>dbFuture("site administrative balance", () -> tx(connection -> {
            update(connection, "CREATE TABLE IF NOT EXISTS economy_site_bridge_operations("
                    + "operation_id TEXT PRIMARY KEY,request_hash TEXT NOT NULL,player_uuid TEXT NOT NULL,currency TEXT NOT NULL,"
                    + "mode TEXT NOT NULL,amount BIGINT NOT NULL,status TEXT NOT NULL,balance_after BIGINT NOT NULL,"
                    + "transaction_id TEXT NOT NULL,error_code TEXT NOT NULL,actor TEXT NOT NULL,reason TEXT NOT NULL,created_at BIGINT NOT NULL)");
            // Operation lock precedes account lock so concurrent retries cannot
            // target different accounts before the journal binding is checked.
            queryOne(connection, "SELECT pg_advisory_xact_lock(hashtext(?))", "site-v6-money:" + operationId);
            Map<String, Object> existing = queryOne(connection,
                    "SELECT * FROM economy_site_bridge_operations WHERE operation_id=?", operationId);
            if (existing != null && !existing.isEmpty()) {
                if (!requestHash.equals(string(existing.get("request_hash")))) {
                    return siteBridgeMoneyResult("pending", currency, 0L, "", "IDEMPOTENCY_CONFLICT");
                }
                return siteBridgeMoneyResult(string(existing.get("status")), currency, longValue(existing.get("balance_after")),
                        string(existing.get("transaction_id")), string(existing.get("error_code")));
            }
            String accountId = "";
            long before;
            boolean active = true;
            if ("AR".equals(currency)) {
                Map<String, Object> account = ensureBankAccount(connection, playerUuid.toString(), playerName);
                accountId = string(account.get("account_id"));
                Map<String, Object> locked = queryOne(connection,
                        "SELECT balance,status FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", accountId);
                if (locked == null || locked.isEmpty()) throw new IllegalStateException("Bank account is missing.");
                before = longValue(locked.get("balance"));
                active = "ACTIVE".equalsIgnoreCase(string(locked.get("status")));
            } else {
                ensureDonationAccount(connection, playerUuid.toString(), playerName);
                before = scalarLong(connection,
                        "SELECT balance FROM donation_accounts WHERE player_uuid=? FOR UPDATE", playerUuid.toString());
            }
            long after = "set".equals(mode) ? amount : "credit".equals(mode) ? Math.addExact(before, amount) : Math.subtractExact(before, amount);
            boolean allowed = active && after >= 0L && after <= 1_000_000_000_000L;
            String state = allowed ? "executed" : "failed";
            String code = !active ? "ACCOUNT_INACTIVE" : after < 0L ? "INSUFFICIENT_BALANCE" : after > 1_000_000_000_000L ? "BALANCE_LIMIT" : "";
            String transactionId = allowed ? "site-v6-money:" + operationId : "";
            long changed = allowed ? after - before : 0L;
            long at = now();
            if (allowed && "AR".equals(currency)) {
                update(connection, "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?",
                        after, at, accountId);
                update(connection, "INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        transactionId, accountId, "SITE_ADMIN", playerUuid.toString(), changed < 0L ? "DEBIT" : "CREDIT",
                        Math.abs(changed), after, transactionId, "COMMITTED", at, actor,
                        "source=SITE_V6_ADMIN mode=" + mode + " requested=" + amount + " reason=" + reason);
            } else if (allowed) {
                update(connection, "UPDATE donation_accounts SET balance=?,updated_at=?,player_name=? WHERE player_uuid=?",
                        after, at, playerName, playerUuid.toString());
                update(connection, "INSERT INTO donation_balance_ledger(id,player_uuid,delta,balance_after,reason,actor,source,idempotency_key,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                        transactionId, playerUuid.toString(), changed, after, reason, actor, "SITE_V6_ADMIN:" + mode, transactionId, at);
            }
            update(connection, "INSERT INTO economy_site_bridge_operations(operation_id,request_hash,player_uuid,currency,mode,amount,status,balance_after,transaction_id,error_code,actor,reason,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    operationId, requestHash, playerUuid.toString(), currency, mode, amount, state, allowed ? after : before,
                    transactionId, code, actor, reason, at);
            return siteBridgeMoneyResult(state, currency, allowed ? after : before, transactionId, code);
        })).handle((result, error) -> error == null ? result
                : siteBridgeMoneyResult("pending", currency, 0L, "", "COMMIT_UNCONFIRMED"));
    }

    private String siteBridgeMoneyHash(UUID player, String currency, String mode, long amount, String actor, String reason) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
                out.writeUTF(player.toString()); out.writeUTF(currency); out.writeUTF(mode);
                out.writeLong(amount); out.writeUTF(actor); out.writeUTF(reason);
            }
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Map<String, Object> siteBridgeMoneyResult(String status, String currency, long balance,
            String transactionId, String code) {
        return Map.of("status", status, "currency", currency, "balanceAfter", balance,
                "transactionId", transactionId, "errorCode", code, "atomicNoDelivery", "failed".equals(status),
                "authority", "EconomyCore", "message", "executed".equals(status)
                    ? "EconomyCore " + currency + " balance: " + balance
                    : "failed".equals(status) ? "Administrative adjustment rejected without a balance change."
                    : "Balance result is uncertain; poll the same operation ID.");
    }


