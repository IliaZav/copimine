"""Exercise real EconomyCore balance methods across a transient JDBC outage."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

import pytest

ROOT = Path(os.environ.get("COPIMINE_ECONOMY_TEST_ROOT", Path(__file__).resolve().parents[1]))
SOURCE = ROOT / "copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java"


def _method(section, signature):
    start = section.index(signature)
    opening = section.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (section[end] == "{") - (section[end] == "}")
        end += 1
    return section[start:end]


@pytest.mark.parametrize("route", ["bank", "donation"])
def test_balance_reads_propagate_outage_and_retry_after_recovery(tmp_path, route):
    source = SOURCE.read_text(encoding="utf-8")
    bank = source.split("private final class BankServiceImpl", 1)[1].split("private final class OfficialArServiceImpl", 1)[0]
    donation = source.split("private final class DonationBalanceServiceImpl", 1)[1].split("private final class", 1)[0]
    signature = "public long balance(UUID playerUuid, String playerName)"
    async_signature = "public CompletableFuture<Long> balanceAsync(UUID playerUuid, String playerName)"
    future_signature = "private <T> CompletableFuture<T> dbFuture(String label, SqlSupplier<T> body)"
    probe = tmp_path / "BalanceOutageProbe.java"
    probe.write_text(r'''
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BalanceOutageProbe {
    volatile boolean outage;
    AtomicBoolean databaseReady = new AtomicBoolean(true);
    ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    CompletableFuture<Void> databaseStartupFuture = CompletableFuture.completedFuture(null);
    interface SqlSupplier<T> { T get() throws Exception; }
    Connection openConnection() throws SQLException {
        if (outage) throw new SQLException("synthetic connection outage");
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("close")) return null;
                throw new AssertionError("unexpected JDBC call " + method.getName());
            });
    }
    void requireAsyncBankContext(String ignored) {}
    void ensureBankAccount(Connection c, String id, String name) {}
    void ensureDonationAccount(Connection c, String id, String name) {}
    String first(String a, String b) { return a == null || a.isBlank() ? b : a; }
    String bankAccountId(String uuid) { return "bank:" + uuid; }
    long scalarLong(Connection c, String sql, String id) {
        return sql.contains("donation_accounts") ? 42L : 73L;
    }
    static String safeError(Throwable error) { return error.getMessage(); }
''' + _method(source, future_signature) + r'''
    final class Bank {
''' + _method(bank, signature) + "\n" + _method(bank, async_signature) + r'''
    }
    final class Donation {
''' + _method(donation, signature) + "\n" + _method(donation, async_signature) + r'''
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void mustFail(CompletableFuture<Long> operation, String name) throws Exception {
        try {
            long value = operation.get(5, TimeUnit.SECONDS);
            throw new AssertionError(name + " returned fake balance " + value + " during outage");
        } catch (ExecutionException expected) {
            Throwable cause = expected;
            while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
            check(cause != null, name + " must preserve the JDBC failure cause");
        }
    }
    public static void main(String[] args) throws Exception {
        var probe = new BalanceOutageProbe();
        try {
            UUID player = new UUID(0, 123);
            var bank = probe.new Bank();
            var donation = probe.new Donation();
            check(bank.balanceAsync(player, "test").get() == 73L, "bank must read real 73");
            check(donation.balanceAsync(player, "test").get() == 42L, "donation must read real 42");
            probe.outage = true;
            if (args[0].equals("bank")) {
                mustFail(bank.balanceAsync(player, "test"), "bank");
            } else {
                mustFail(donation.balanceAsync(player, "test"), "donation");
            }
            probe.outage = false;
            check(bank.balanceAsync(player, "test").get() == 73L, "bank retry after reconnect must read 73");
            check(donation.balanceAsync(player, "test").get() == 42L, "donation retry after reconnect must read 42");
        } finally { probe.dbExecutor.shutdownNow(); }
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(probe)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "BalanceOutageProbe", route], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_previous_economy_candidate_remains_an_authorized_upgrade_source():
    lock = json.loads((ROOT / "tools/minecraft-26.3/server-plugins.lock.json").read_text(encoding="utf-8"))
    economy = next(row for row in lock["unchangedBaseline"] if row["pluginName"] == "CopiMineEconomyCore")
    previous = "29fce619840efb99fc56e80552272cd4b67580042c10015546dbd98a9ddae4e1"
    assert previous in economy["candidate"]["replaceSha256"]
    assert previous in economy["replaceSha256"]
    assert economy["candidate"]["nativeVerified"] is False


if __name__ == "__main__":
    test_balance_reads_propagate_outage_and_retry_after_recovery(Path(tempfile.mkdtemp(prefix="copimine-balance-probe-")), "bank")
