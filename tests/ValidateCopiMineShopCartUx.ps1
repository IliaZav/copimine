$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$shops = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/shops.html')
$cart = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/cart.html')
$renderer = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/public/site-render.js')
$siteData = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/public/site-data.js')
$cartPage = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/public/cart-page.js')
$guardPath = Join-Path $root 'admin-web/frontend/assets/js/public/cart-checkout-guard.js'
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$source, [string]$value, [string]$name) {
  if (-not $source.Contains($value)) {
    $errors.Add("Missing ${name}: $value")
  }
}

function Require-Match([string]$source, [string]$pattern, [string]$name) {
  if ($source -notmatch $pattern) {
    $errors.Add("Missing ${name}")
  }
}

foreach ($check in @(
  @{ source = $shops; value = 'id="shopCmsNoteTitle"'; name = 'visible shop CMS title target' },
  @{ source = $shops; value = 'id="shopCmsNoteBody"'; name = 'visible shop CMS body target' },
  @{ source = $shops; value = 'aria-labelledby="shopCmsNoteTitle"'; name = 'shop CMS section label' },
  @{ source = $renderer; value = 'setCmsText("#shopCmsNoteTitle", shops.title);'; name = 'CMS title renderer' },
  @{ source = $renderer; value = 'setCmsText("#shopCmsNoteBody", shops.body);'; name = 'CMS body renderer' },
  @{ source = $siteData; value = 'fetchJson("/api/player/artifacts"'; name = 'AR ownership request' },
  @{ source = $siteData; value = 'fetchJson("/api/player/shop/owned"'; name = 'donation ownership request' },
  @{ source = $renderer; value = '"PENDING", "DELIVERING", "PENDING_DELIVERY"'; name = 'shop pending-delivery availability state' },
  @{ source = $cartPage; value = '"PENDING", "DELIVERING", "PENDING_DELIVERY"'; name = 'cart pending-delivery availability state' },
  @{ source = $renderer; value = 'const pendingInstance ='; name = 'shop pending donation label' },
  @{ source = $cartPage; value = 'const pendingInstance ='; name = 'cart pending donation label' },
  @{ source = $renderer; value = 'if (row?.enabled === false) return { unavailable: true, label:'; name = 'disabled storefront catalog item state' },
  @{ source = $renderer; value = 'addButton.disabled = unavailable;'; name = 'disabled storefront add control' },
  @{ source = $cartPage; value = 'if (item?.enabled === false) return { unavailable: true, label:'; name = 'disabled cart catalog item state' },
  @{ source = $cartPage; value = 'checkoutButton.disabled = !valid.length || valid.length !== mapped.length || !canCheckout() || checkoutBlockGuard.isBlocked(currency);'; name = 'disabled cart checkout control' },
  @{ source = $cartPage; value = 'function shouldRefreshCatalogAfterCheckoutError'; name = 'stale-cart ownership refresh' },
  @{ source = $cartPage; value = 'const staleCatalogFragments = ['; name = 'stale catalog conflict list' },
  @{ source = $cartPage; value = 'staleCatalogFragments.some'; name = 'stale catalog conflict matching' },
  @{ source = $cartPage; value = 'createCheckoutBlockGuard'; name = 'checkout guard module use' },
  @{ source = $cartPage; value = 'shouldBlockCheckoutAfterRefresh } from "./cart-checkout-guard.js"'; name = 'checkout conflict classifier use' },
  @{ source = $cartPage; value = 'checkoutBlockGuard.isBlocked(currency)'; name = 'stale checkout disabled state' },
  @{ source = $cartPage; value = 'checkoutBlockGuard.clearIfSignatureChanged'; name = 'same-currency stale checkout clearing' },
  @{ source = $cartPage; value = 'checkoutBlockGuard.resolveAfterRefresh'; name = 'ownership refresh checkout reconciliation' },
  @{ source = $cartPage; value = 'checkoutBlockGuard.blockIfSignatureMatches'; name = 'submitted-cart checkout guard' },
  @{ source = $cartPage; value = 'const submittedSignature = checkoutCartSignature(currency);'; name = 'submitted cart signature capture' },
  @{ source = $cart; value = 'id="publicCabinetBtn"'; name = 'shared cabinet header control' },
  @{ source = $cart; value = 'id="publicLogoutBtn"'; name = 'shared logout header control' },
  @{ source = $cartPage; value = 'defaultAppRouteForRole'; name = 'role-aware cabinet route' },
  @{ source = $cartPage; value = 'const authenticated = Boolean(authState?.cookieAuth && authState?.role);'; name = 'signed-in cart header state' }
)) {
  Require-Contains $check.source $check.value $check.name
}

if ($shops -match '<section\b[^>]*class="[^"]*public-shop-board-section[^"]*"[^>]*\bhidden\b') {
  $errors.Add('Legacy hidden shop board remains after the storefront moved to the cart experience.')
}

Require-Match $cartPage '(?s)function blockCheckoutCurrency\(currency, submittedSignature\)\s*\{\s*return checkoutBlockGuard\.blockIfSignatureMatches\(\s*currency,\s*submittedSignature,\s*checkoutCartSignature\(currency\),\s*\);\s*\}' 'signature-aware checkout block wrapper'
Require-Match $cartPage 'const blockedSubmittedCart = shouldBlock && blockCheckoutCurrency\(currency, submittedSignature\);' 'submitted signature on initial checkout rejection'
Require-Match $cartPage '(?s)catch \(_reloadError\)\s*\{.*?blockCheckoutCurrency\(currency, submittedSignature\);' 'submitted signature on checkout refresh failure'
Require-Match $cartPage '(?s)const submittedSignature = checkoutCartSignature\(currency\);.*?await ensureCsrfCookie\(\);' 'submitted signature capture before checkout request'
Require-Match $renderer '(?s)function shopItemAvailability\(row, currency, ownership = \{\}\).*?if \(row\?\.enabled === false\) return \{ unavailable: true, label: "\u0421\u043d\u044f\u0442\u043e \u0441 \u043f\u0440\u043e\u0434\u0430\u0436\u0438" \};.*?if \(currency === "ar"\)' 'disabled storefront item checked before currency ownership rules'
Require-Match $cartPage '(?s)function cartItemAvailability\(currency, item\).*?if \(item\?\.enabled === false\) return \{ unavailable: true, label: "\u0421\u043d\u044f\u0442\u043e \u0441 \u043f\u0440\u043e\u0434\u0430\u0436\u0438" \};.*?if \(currency === "ar"\)' 'disabled cart item checked before currency ownership rules'
Require-Match $cartPage '(?s)const staleCatalogFragments = \[.*?"\u041e\u0434\u0438\u043d \u0438\u0437 donation-\u043f\u0440\u0435\u0434\u043c\u0435\u0442\u043e\u0432 \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d"' 'disabled donation stale catalog refresh'

if (-not (Test-Path -LiteralPath $guardPath)) {
  $errors.Add('Checkout guard module is missing.')
}

if ($errors.Count -gt 0) {
  throw ("Shop cart UX validation failed:`n - " + ($errors -join "`n - "))
}

$guardScenario = @'
import { readFile } from "node:fs/promises";

const modulePath = process.argv[2];
const source = await readFile(modulePath, "utf8");
const { createCheckoutBlockGuard, shouldBlockCheckoutAfterRefresh } = await import(`data:text/javascript,${encodeURIComponent(source)}`);
const guard = createCheckoutBlockGuard();

const arSignature = "owned-artifact";
const donationSignature = "donation-item";
const submittedCartA = "artifact-a";
const changedCartB = "artifact-b";
const personalLimitError = "\u041f\u0435\u0440\u0441\u043e\u043d\u0430\u043b\u044c\u043d\u044b\u0439 \u043b\u0438\u043c\u0438\u0442";
const ownershipError = "\u0430\u043a\u0442\u0438\u0432\u043d\u044b\u0439 \u0438\u043b\u0438 \u043e\u0436\u0438\u0434\u0430\u044e\u0449\u0438\u0439 \u0432\u044b\u0434\u0430\u0447\u0438";
for (const [label, rejection] of [["personal limit", personalLimitError], ["ownership", ownershipError]]) {
  if (!shouldBlockCheckoutAfterRefresh(rejection)) throw new Error(`${label} conflict did not request a checkout block`);
  const changedCartGuard = createCheckoutBlockGuard();
  if (changedCartGuard.blockIfSignatureMatches("ar", submittedCartA, changedCartB)) {
    throw new Error(`Changed cart B was blocked by a ${label} rejection for submitted cart A`);
  }
  if (changedCartGuard.isBlocked("ar")) throw new Error(`Changed cart B remained blocked after a ${label} rejection for cart A`);
}
const matchingCartGuard = createCheckoutBlockGuard();
if (!matchingCartGuard.blockIfSignatureMatches("ar", submittedCartA, submittedCartA)) {
  throw new Error("Submitted cart A was not blocked when it still matched the current cart");
}
if (!matchingCartGuard.isBlocked("ar")) throw new Error("Matching submitted cart A did not retain its checkout block");
guard.block("ar", arSignature);
if (!guard.isBlocked("ar")) throw new Error("AR checkout was not blocked after ownership rejection");

guard.resolveAfterRefresh("ar", arSignature, false);
if (!guard.isBlocked("ar")) throw new Error("AR checkout was re-enabled after an ownership refresh without an unavailable row");

guard.block("donation", donationSignature);
guard.clearIfSignatureChanged("donation", "donation-item,next");
if (!guard.isBlocked("ar")) throw new Error("Donation cart change cleared the AR checkout block");

guard.clearIfSignatureChanged("ar", "owned-artifact,next");
if (guard.isBlocked("ar")) throw new Error("AR checkout remained blocked after its own cart changed");

guard.block("ar", arSignature);
guard.resolveAfterRefresh("ar", arSignature, true);
if (guard.isBlocked("ar")) throw new Error("AR checkout guard remained after refreshed ownership marked a row unavailable");
'@

$guardOutput = $guardScenario | node --input-type=module - $guardPath 2>&1
if ($LASTEXITCODE -ne 0) {
  throw ("Checkout guard behavioral regression failed:`n" + ($guardOutput -join "`n"))
}

Write-Host 'ValidateCopiMineShopCartUx passed.'
