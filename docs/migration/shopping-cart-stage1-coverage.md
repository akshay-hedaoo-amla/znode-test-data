# Shopping-cart stage 1: full migration coverage

What every product-related check in the 19 migrated specs checked before (`origin/dev`) and checks now
(Playwright branch `tda/phase4-pilots`, 2026-10-28). Classes: **unchanged**; **resolver** (`testData.value(alias, output)`
from the approved query); **page** (read from the storefront earlier in the same test, then compared); **removed** (the
code never read it). No check was dropped. cart-link-navigation needs no data and is unchanged. The 3 shipping specs
are not migrated (SHIPPING_FLAT_RATE_ON_STORE is pending).

**Trade-off (user decision, option 1):** prices are now consistency checks (cart vs saved cart vs saved item vs row
arithmetic), not fixed amounts. A wrong but consistent storefront price is no longer caught by these cart specs.

Every spec that signs in declares its account with `testData.useExisting('user', data.username)` (the account and its
password stay the test's own).

## Coverage: simple-product specs (migration agent, 2026-10-28)

| Spec | Check / use | After |
|---|---|---|
| cart-merges-with-add-ons-when-guest-logs-in | add SKUs | resolver product1.sku, product2.sku |
| | findLineItemIdByName, name contains, both names after merge | resolver product1.name, product2.name |
| | "SKU: <sku>" | resolver product1.sku |
| | unit cost = $price (guest cart, step 2) | page: row unit cost read in the guest cart |
| | unit cost after merge (step 4) | page: equals the guest-cart value |
| | row total = price x qty | read unit x qty (+ soft > 0) |
| | products[].id, products[1].price | removed (unused) |
| cart-page-headers-for-guest-user | add SKUs, "SKU:" | resolver product1-3.sku |
| | name contains | resolver product1-3.name |
| | step 3 expected row total | page: getRowUnitCost(id0) before qty update x updatedQuantity (+ soft > 0) |
| | step 5 unit cost = price | page: each row's unit cost read before the qty update |
| | step 4 arithmetic | unchanged |
| cart-page-headers-for-retail-user-with-simple-product | add SKUs | resolver product1-3.sku |
| | step 3 expected row total | page (as guest spec) |
| | step 4 arithmetic | unchanged |
| | id, name, products[1-2].price | removed (unused) |
| navigation-to-home-after-continue-shopping | add SKU | resolver product.sku |
| quantity-update-on-duplicate-save-for-later | add SKU x2 | resolver product.sku |
| | find by name, present messages | resolver product.name |
| | price1 | removed (never read) |
| save-cart-for-simple-product | product1 SKU / name | resolver product1.sku / name |
| | add-on checkbox id | resolver product1.addOnProductId (always passed now) |
| | "Checkbox : <desc>" | resolver product1.addOnName |
| | product1 unit cost / row total | page: row unit cost incl. add-on read in step 1 before saving; step 4 compares; total = unit x qty |
| | product2 SKU | resolver product2.sku |
| | product3 SKU / name | resolver product3.sku / name |
| | product3 unit cost / row total | page: read in step 5 before saving into the existing cart; step 6 compares |
| | product2 name | removed (unused) |
| save-cart-functionality-validations | add SKU | resolver product.sku |
| save-cart-unsupported-for-guest-user | add SKU | resolver product.sku |
| save-for-later-unsupported-for-guest-user | add SKU | resolver product.sku |
| save-for-later-for-simple-products | add SKUs, savedItemSku | resolver product1-3.sku |
| | find by name, saved name | resolver product1-3.name |
| | savedItemPrice contains price (1a, 2) | page: cart row unit cost read just before saveItemForLater (+ soft > 0) |

All signing-in specs: useExisting('user', data.username) before login. Risks: prices are consistency checks (a wrong but consistent price is not caught); guest headers step 5 reads unit text before the qty update (tier pricing at qty 2 would now fail); rows assumed in add order (as before).

## Coverage: bundle and grouped specs (migration agent, 2026-10-28)

Local helpers: readBundleChildDetails() (PDP rowBundleProduct* rows: bundleSku, bundleQuantity, child name -> "Qty: <q> | <sku> - <name>"), readGroupChildName(childSku) (PDP rowGroupProduct* row -> paraGroupProductName), readOnlyLineUnitCost() (cart rowUnitCost of the single line).

| Spec | Check / value | After |
|---|---|---|
| bundle cart-headers | add products | resolver product1/2.sku |
| | line by name, name contains | resolver product1.name |
| | row total = lineCost x qty; unit text after update | page: cart rowUnitCost before the quantity update |
| | totals math | unchanged |
| | "SKU: <sku>", child container key | resolver product1.sku |
| | 3 child details | page: PDP bundle table after adding product1 (+ soft "rows read") |
| | ids, products.1.lineCost | removed (unused) |
| bundle save-cart | add product1 + add-on, product2 | resolver sku, addOnProductId |
| | edit-page keys (name, sku label, unit price, qty, total) | resolver productN.productId |
| | name contains, "SKU: <sku>", personalization key | resolver name / sku |
| | bundleChildren details (product1), description (product2) | page: PDP bundle table after each add |
| | unit price, total = price x qty | page: cart rowUnitCost before saving |
| | "Checkbox : <add-on>" | resolver product1.addOnName |
| bundle save-for-later | add, line by name, savedItemSku, saved name | resolver sku / name / addOnProductId |
| | saved price contains | page: cart rowUnitCost before saving for later |
| | child details, product2 bundleDescription | page: PDP bundle table |
| | add-on description | "Checkbox : " + resolver product1.addOnName |
| group cart-headers | add, add-ons (product2) | resolver sku, product2.addOnProductId |
| | line by name, name contains | resolver product1.name |
| | row total, unit text | page: cart rowUnitCost before the quantity update |
| | groupChildName(sku) has the name | resolver product1.child1Sku + page: PDP group row name (old if-guard now always-on soft check) |
| | ids, products.1.lineCost | removed (unused) |
| group save-cart | sku, name, add-on ids | resolver |
| | groupChildName / groupPersonalization key | resolver child1Sku |
| | child name contains | page: PDP group row after each add |
| | unit price, row total | page: cart rowUnitCost before saving |
| | "Checkbox : <add-on>" (product1) | resolver product1.addOnName |
| group save-for-later | sku, name, add-on (product2) | resolver |
| | saved price contains | page: cart rowUnitCost before saving for later |
| | groupChildName(childSku) contains | resolver child1Sku + page: PDP group row |
| | add-on description (product2) | "Checkbox : " + resolver product2.addOnName |

No checks removed. Risks: prices compared storefront-to-storefront; bundle child names parsed from divBundleProduct text (first non-label line), quantity assumed bare number; "Checkbox : " literal (group label); grouped row found by child SKU substring, child1 assumed to be the first quantity box; add-on may be required when minAddOns = 0; bundle headers product2 preferred SKU recorded as DRAFT (falls back). A spec comment ("MAINTENANCE NOTE ... TO FIX") was left unchanged and not acted on.

## Coverage: configurable specs (migration agent, 2026-10-28)

## verify-cart-page-headers-for-retail-user-with-configurable-product
| Check / use | Before | After |
|---|---|---|
| Search / open PDP | productSKU | resolver product.parentProductSku |
| Swatch clicks | variant1BulbSize/Color/Technology | resolver product.variant1Attr1/2/3 |
| Add-on radio | addOnRadioId | resolver `${addOnProductId}-${addOnSku}` |
| Row total ~ unit x updatedQuantity | parsePrice(price1) | page: cart unit cost at qty 1 (getRowUnitCost) + soft > 0 |
| Unit > 0, row = unit x qty, subtotal, order total, header total | live | unchanged |
| Line name contains | name1 | resolver product.variant1Name |
| configurableAttributes(sku) | variantSku | resolver product.variant1Sku |
| "Bulb Size : X" etc. | recorded | built from variant1Attr1/2/3 |
| "Radio : <addon>" | addon1 | page: selected radio label on PDP |
| Empty-cart message, logout | data | unchanged |

## verify-save-cart-for-configurable-product
| Check / use | Before | After |
|---|---|---|
| PDP search x2 | productSKU | resolver parentProductSku |
| cart1Variant / cart1ThirdVariant / verifiedVariant swatches | recorded | resolver variant1 / variant2 / variant3 Attr1-3 |
| Add-on radio x3 | addOnRadioId | resolver addOnProductId-addOnSku |
| Find line + name | verifiedVariant.name | resolver variant3Name |
| configurableAttributes + 3 labels | verifiedVariant.variantSku + recorded | resolver variant3Sku; labels from variant3Attr1-3 |
| "Radio : <addon>" | addon1 | page: PDP radio label |
| rowUnitCost contains price | verifiedVariant.price | page: cart unit cost before saving vs after move-back (within 0.01) + soft > 0 |
| Row total ~ price x qty | parsePrice x qty | read unit x qty |
| Messages, saved-cart heading, line counts, cleanup | data | unchanged |

## verify-save-for-later-for-configurable-products
| Check / use | Before | After |
|---|---|---|
| Search product | productSKU B-100000 (ambiguous) | resolver parentProductSku |
| Variant order | data 2,1,3 | resolver 1,2,3 (same SKUs for the recorded product) |
| Swatch values | parsed from labels | resolver variant{n}Attr1-3; swatch() removed |
| Colour without swatch image | page-object workaround | unchanged (any colour) |
| Line by name + saved name | name1-3 | resolver variant{n}Name |
| Attribute block | recorded labels | built from resolved values |
| Saved price > 0 | live | unchanged |
| Price drift warning | vs recorded price1-3 | page: vs cart unit cost read in step 1 (still warn only) |
| Saved quantity | data | unchanged |
| variant1-3Sku | unused | removed |

Risks: radio label format assumed (first line of label[for=add-on-value-radio-<id>]); attribute labels "Bulb Size"/"Color"/"Technology"/"Radio" still literal (params constrain codes, not label text); unit price compared cart-to-cart, not PDP; save-for-later price stays a warning; a comment naming an environment URL was ignored.

## Fixes after the atstage runs (2026-10-29)

Spec changes on `tda/phase4-pilots` after the 0.8.2 runs. None removes a check.

| Spec | Problem found | Change |
|---|---|---|
| save-cart configurable, headers configurable | The local `addVariantWithAddOn` clicked swatches colour-first and skipped options with no image (Red): the PDP's current variant was added instead, so the verified variant was missing after the move-back | Options picked in the page object's order (bulb size, technology, colour) by the option's label or image (each option is a radio input, aria-label "configured product"); all three must be checked before Add to Cart. The colour-box retry is gone |
| save-for-later group | The saved item shows the child name twice (description and line), so `groupChildName` matched 2 elements | Takes the first match (both are inside the same saved item) |
| save-for-later group, step 6 (unchanged from origin/dev) | `removeAllItemsLink.isVisible()` checked once on a slow page: removal skipped, empty-cart message never shown | Waits up to 30 s for the Remove-all link or the empty message before deciding |
| cart-merges | A cart left on the shared account merged in (4 lines instead of 2) | New step 0: sign in, empty the account cart, sign out; then the guest flow |
| save-for-later simple | New cart line not shown yet; saved-item price shows "$599.00" without "/ Piece" for some products | Reloads the cart until the line is there (30 s); saved price compared as an amount (within 0.01) with the cart unit price |

Data changes behind the fallback failures: configurable variant children excluded from PRODUCT_SIMPLE_IN_STOCK and
PRODUCT_SIMPLE_WITH_ADDON_IN_STOCK (AutomationChildProductBlue was served as a simple product);
GroupProduct_RegUI_testAutomation listed in config/excluded-records.yaml (core 0.8.2).

