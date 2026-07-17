# Bank Tax Exemption Minute Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the player-bank tax-exemption countdown accurate, low-frequency, and aligned with the server-backed product contract.

**Architecture:** `GET /api/player/elections/tax` remains the expiry source of truth. The existing browser module receives that timestamp, formats a calendar-aware remaining duration through minutes, and schedules one local repaint per minute. No API, database, or payment behavior changes.

**Tech Stack:** FastAPI API contract, vanilla ES modules, PowerShell static contract validator.

---

### Task 1: Capture the client contract

**Files:**
- Modify: `tests/ValidateCopiMineWebTaxClockExemption.ps1`
- Test: `tests/ValidateCopiMineWebTaxClockExemption.ps1`

- [ ] **Step 1: Write the failing client assertions**

Require the existing web validator to load `admin-web/frontend/assets/js/player/treasury-pages.js`, then assert the minute-based behavior and reject second-based behavior:

```powershell
$treasury = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\player\treasury-pages.js')
foreach ($marker in @('formatTaxExemptionRemaining', 'taxExemptionCountdown', 'setTimeout', '60000')) {
  if ($treasury -notmatch [regex]::Escape($marker)) { $errors.Add("Bank countdown marker is missing: $marker") }
}
if ($treasury -match 'setInterval\(render,\s*1000\)') { $errors.Add('Bank countdown must not render every second.') }
if ($treasury -match 'сек\.') { $errors.Add('Bank countdown must not expose seconds.') }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `pwsh -File tests/ValidateCopiMineWebTaxClockExemption.ps1`

Expected: failure stating that the current countdown renders every second and exposes seconds.

### Task 2: Render and schedule per minute

**Files:**
- Modify: `admin-web/frontend/assets/js/player/treasury-pages.js`
- Test: `tests/ValidateCopiMineWebTaxClockExemption.ps1`

- [ ] **Step 1: Replace second-based formatting and repeating interval**

Keep `expiresAt` as the only input. Remove the seconds segment and duplicate unreachable block. Schedule a timeout at the next minute boundary, with each callback confirming the target element still exists:

```js
function scheduleTaxExemptionCountdown(expiresAt) {
  const render = () => {
    const target = $("taxExemptionCountdown");
    if (!target) return stopTaxExemptionCountdown();
    target.textContent = formatTaxExemptionRemaining(expiresAt);
    if (Number(expiresAt) <= Date.now()) return stopTaxExemptionCountdown();
    state.taxExemptionCountdown = window.setTimeout(render, 60000 - (Date.now() % 60000));
  };
  render();
}
```

Use `clearTimeout` in the existing cleanup function. Preserve its calls before loading a new bank view and on navigation.

- [ ] **Step 2: Run the validator to verify it passes**

Run: `pwsh -File tests/ValidateCopiMineWebTaxClockExemption.ps1`

Expected: `ValidateCopiMineWebTaxClockExemption passed.`

- [ ] **Step 3: Run the broader static regression check**

Run: `python admin-web/scripts/regression_contract_test.py`

Expected: exit code 0 after updating the old seconds assertion to the minute-based contract.

### Task 3: Verify scope and commit only this remediation

**Files:**
- Modify: `tests/ValidateCopiMineWebTaxClockExemption.ps1`
- Modify: `admin-web/frontend/assets/js/player/treasury-pages.js`
- Modify: `admin-web/scripts/regression_contract_test.py`

- [ ] **Step 1: Check JavaScript syntax and whitespace**

Run: `node --check admin-web/frontend/assets/js/player/treasury-pages.js; git diff --check`

Expected: both commands exit 0.

- [ ] **Step 2: Stage the isolated files only**

Run: `git add admin-web/frontend/assets/js/player/treasury-pages.js admin-web/scripts/regression_contract_test.py tests/ValidateCopiMineWebTaxClockExemption.ps1`

Expected: staged changes contain only the tax-exemption rendering and static contract updates; all unrelated existing changes remain unstaged.

- [ ] **Step 3: Commit and push the focused change**

Run: `git commit -m "fix: refresh tax exemption once per minute"; git push`

Expected: commit succeeds and the current branch is pushed, unless the configured remote rejects access.
