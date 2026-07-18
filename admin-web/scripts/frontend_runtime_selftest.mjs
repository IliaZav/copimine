import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

async function loadFrontendModule(relativePath) {
  const sourcePath = fileURLToPath(new URL(relativePath, import.meta.url));
  const source = await readFile(sourcePath, "utf8");
  return import(`data:text/javascript;charset=utf-8,${encodeURIComponent(source)}`);
}

const { buildCsvContent } = await loadFrontendModule("../frontend/assets/js/shared/csv.js");
const { RECIPE_DRAG_MIME, readRecipeDragIndex, writeRecipeDragIndex } = await loadFrontendModule("../frontend/assets/js/shared/recipe-drag.js");
const { fullTaxPaymentAmount, isPlayerBankRoute } = await loadFrontendModule("../frontend/assets/js/shared/player-bank.js");
const { createSuccessfulLoadRegistry } = await loadFrontendModule("../frontend/assets/js/shared/successful-load-registry.js");
const { resolveDonationBalance } = await loadFrontendModule("../frontend/assets/js/shared/player-detail-values.js");

function testCsvFormulaEscaping() {
  const csv = buildCsvContent(
    ["target", "note"],
    [
      { target: "=2+2", note: 'He said "hi"' },
      { target: " \t+RUN", note: "ordinary text" },
      { target: "@SUM(A1:A2)", note: "safe" },
    ],
  );

  assert.equal(
    csv,
    '"target","note"\n"\'=2+2","He said ""hi"""\n"\' \t+RUN","ordinary text"\n"\'@SUM(A1:A2)","safe"',
  );
}

testCsvFormulaEscaping();

function transfer(types = [], values = {}) {
  return {
    types,
    getData(type) {
      return values[type] || "";
    },
    setData(type, value) {
      values[type] = String(value);
      if (!types.includes(type)) types.push(type);
    },
  };
}

function testRecipeDragAcceptsOnlyInternalIndexes() {
  const external = transfer(["text/plain"], { "text/plain": "0" });
  assert.equal(readRecipeDragIndex(external, 3), null);

  const own = transfer();
  writeRecipeDragIndex(own, 1);
  assert.equal(own.getData(RECIPE_DRAG_MIME), "1");
  assert.equal(readRecipeDragIndex(own, 3), 1);

  const outOfBounds = transfer([RECIPE_DRAG_MIME], { [RECIPE_DRAG_MIME]: "3" });
  assert.equal(readRecipeDragIndex(outOfBounds, 3), null);
}

testRecipeDragAcceptsOnlyInternalIndexes();

function testFullTaxPaymentAndBankRoute() {
  assert.equal(fullTaxPaymentAmount("5"), 5);
  assert.equal(fullTaxPaymentAmount("0"), 0);
  assert.equal(fullTaxPaymentAmount("not-a-number"), 0);
  assert.equal(isPlayerBankRoute("bank"), true);
  assert.equal(isPlayerBankRoute("transfer"), true);
  assert.equal(isPlayerBankRoute("cabinet"), false);
}

testFullTaxPaymentAndBankRoute();

async function testFailedPageLoadCanRetry() {
  const registry = createSuccessfulLoadRegistry();
  let attempts = 0;

  await assert.rejects(
    registry.run("public-home", async () => {
      attempts += 1;
      throw new Error("offline");
    }),
    /offline/,
  );
  assert.equal(registry.has("public-home"), false);

  await registry.run("public-home", async () => {
    attempts += 1;
    return "loaded";
  });
  await registry.run("public-home", async () => {
    attempts += 1;
    return "should-not-run";
  });

  assert.equal(attempts, 2);
  assert.equal(registry.has("public-home"), true);
}

await testFailedPageLoadCanRetry();

function testDonationBalanceHasReachableFallbacks() {
  assert.equal(resolveDonationBalance({ balance: 12 }, [{ balance_after: 4 }], [{ balance_after: 3 }]), 12);
  assert.equal(resolveDonationBalance({ balance: 0 }, [{ balance_after: 4 }], [{ balance_after: 3 }]), 0);
  assert.equal(resolveDonationBalance({}, [{ balance_after: 4 }], [{ balance_after: 3 }]), 4);
  assert.equal(resolveDonationBalance({}, [], [{ balance_after: 3 }]), 3);
  assert.equal(resolveDonationBalance({}, [], []), 0);
}

testDonationBalanceHasReachableFallbacks();
console.log("Frontend runtime selftest OK");
