# Phase 1 approval list

Generated 2026-10-21 from `repository/`. Every entry is `pending_approval`. To approve one, set
`"status": "approved"`, `"approvedBy": "<your name>"` and `"approvedAt": "<ISO time>"` in the file (security doc §7: queries
and collections need a senior SDET; vocabulary keys need a QA lead or senior SDET; entity-map changes can be approved by any reviewer).
Then run `mvn -P phase1-exit,test-env verify`. Latest drift report: reports/drift/drift-20261021T111707Z.md (8 ok, 0 drifted).

## Vocabulary keys (8)

| Key | Usage | Constraints | Params | Notes |
| --- | --- | --- | --- | --- |
| `BRAND_WITH_ACTIVE_PRODUCTS` | read-only | isActive true; productCount >= {minProducts} | minProducts, store | Active brand associated with the store and with at least N active products, used as the target of brand promot |
| `COUPON_AMOUNT_OFF_ORDER` | consumable | discountType amount_off_order; startDate <= {now}; endDate >= {now}; couponActive true; availableQuantity >= {redemptionsNeeded} | redemptionsNeeded, store, now | Active Amount Off Order promotion with a normal coupon code on the store and coupons left to apply. KB: Managi |
| `PRODUCT_CONFIGURABLE_WITH_VARIANTS` | read-only | type configurable; status active; variantCount >= 1; variantInventory >= {quantityUsed}; publishState production | quantityUsed, store | Active configurable parent product with buyable, in-stock variants on the store. KB: Product Types (configurab |
| `PRODUCT_SIMPLE_IN_STOCK` | read-only | type simple; status active; inventory >= {quantityUsed}; publishState production | quantityUsed, store | Active simple product on the store with enough inventory to add to cart. KB: Product Types (simple = single SK |
| `PRODUCT_SIMPLE_NOT_OF_BRAND_IN_STOCK` | read-only | type simple; status active; brand != {brand}; inventory >= {quantityUsed}; publishState production | brand, quantityUsed, store | Active in-stock simple product NOT of a given brand on the store, to prove a brand promotion does not apply to |
| `PRODUCT_SIMPLE_OF_BRAND_IN_STOCK` | read-only | type simple; status active; brand {brand}; inventory >= {quantityUsed}; publishState production | brand, quantityUsed, store | Active in-stock simple product associated with a given brand on the store, so brand promotions apply. KB: Addi |
| `STORE_WEBSTORE_ACTIVE` | read-only | webstoreDomains >= 1 | - | Store that serves a webstore: at least one active WebStore domain. ZnodePortal.IsActive is not used (it is fal |
| `USER_WEBSTORE_REGISTERED` | consumable | userType registered; isActive true | store | Active registered webstore customer of the store who can sign in, build a cart and place orders. KB: Managing  |

## Entity map (8)

| Entity | Tables | Properties | Scope / filters | Masked |
| --- | --- | --- | --- | --- |
| `brand` | ZnodeBrandDetails, ZnodeBrandDetailLocale | name=ZnodeBrandDetailLocale.BrandName; code=ZnodeBrandDetails.BrandCode; isActive=ZnodeBrandDetails.IsActive | - | - |
| `brand_products` | ZnodeBrandDetailLocale, ZnodeBrandDetails, ZnodePimProduct, ZnodePortalBrand, ZnodePortal | name=ZnodeBrandDetailLocale.BrandName; productCount=COUNT(DISTINCT ZnodePimProduct.PimProductId) | scope store=ZnodePortal.StoreCode; filter ZnodePimProduct.IsActive=true | - |
| `product` | ZnodePimProduct, ZnodePublishState | sku=ZnodePimProduct.SKU; name=ZnodePimProduct.ProductName; type=ZnodePimProduct.ProductType; status=ZnodePimProduct.IsActive; brand=ZnodePimProduct.Brand; publishState=ZnodePublishState.PublishStateCode | - | - |
| `product_stock` | ZnodeInventory, ZnodePortalWarehouse, ZnodePortal | sku=ZnodeInventory.SKU; inventory=SUM(ZnodeInventory.Quantity) | scope store=ZnodePortal.StoreCode | - |
| `product_variants` | ZnodePimProduct, ZnodePimProductTypeAssociation, ZnodeInventory, ZnodePortalWarehouse, ZnodePortal | sku=ZnodePimProduct.SKU; variantCount=COUNT(DISTINCT variant.PimProductId); variantInventory=SUM(ZnodeInventory.Quantity) | scope store=ZnodePortal.StoreCode | - |
| `promotion` | ZnodePromotion, ZnodePromotionCoupon, ZnodePromotionType, ZnodePortal | name=ZnodePromotion.Name; promoCode=ZnodePromotion.PromoCode; couponCode=ZnodePromotionCoupon.Code; availableQuantity=ZnodePromotionCoupon.AvailableQuantity; discountType=ZnodePromotionType.Name; store=ZnodePortal.StoreCode; startDate=ZnodePromotion.StartDate; endDate=ZnodePromotion.EndDate; couponActive=ZnodePromotionCoupon.IsActive; discountAmount=ZnodePromotion.Discount; orderMinimum=ZnodePromotion.OrderMinimum | - | - |
| `store` | ZnodePortal | code=ZnodePortal.StoreCode; name=ZnodePortal.StoreName | - | - |
| `store_domains` | ZnodePortal, ZnodeDomain | code=ZnodePortal.StoreCode; name=ZnodePortal.StoreName; webstoreDomains=COUNT(DISTINCT ZnodeDomain.DomainId) | filter ZnodeDomain.ApplicationType=WebStore; filter ZnodeDomain.IsActive=1 | ZnodeDomain.ApiKey |

## Requirements (6)

### testClasses.integration.marketing.promotionsAndCoupons.WebstorePromotionAndCouponVerificationTest#T01_verifyAmountOfBrandCouponFunctionality
- Confidence: medium (needs review). Linked cases: 20. Sources: 59 (22 config, 6 data, 31 created-by-test).
- Scenario: Admin creates two Amount Off Brand coupon promotions and one Percent Off Brand coupon promotion for the brand on store Multistore4 and reads back the generated coupon codes. A registered webstore user checks that the coupons are rejected for a non-brand product and below the minimum quantity, accepted for a brand product at quantity 5 and 6, places an Authorize.Net order, and admin verifies the unique coupon is marked used and the order summary matches.
- Needs `STORE_WEBSTORE_ACTIVE` as `store` {}
- Needs `USER_WEBSTORE_REGISTERED` as `user` {"store": "MaxwellsHardwareMultistore4"}
- Needs `PRODUCT_SIMPLE_OF_BRAND_IN_STOCK` as `product` {"brand": "BlackAndDecker", "store": "MaxwellsHardwareMultistore4", "quantityUsed": 6}
- Needs `PRODUCT_SIMPLE_NOT_OF_BRAND_IN_STOCK` as `nonBrandProduct` {"brand": "BlackAndDecker", "store": "MaxwellsHardwareMultistore4"}
- Needs `BRAND_WITH_ACTIVE_PRODUCTS` as `brand` {"minProducts": 1, "store": "MaxwellsHardwareMultistore4"}

### testClasses.integration.marketing.promotionsAndCoupons.WebstorePromotionAndCouponVerificationTest#T02_verifyPercentOfBrandPromotionFunctionality
- Confidence: medium (needs review). Linked cases: 5. Sources: 29 (15 config, 8 created-by-test, 6 data).
- Scenario: Admin checks the fields shown for the Percent Off Brand discount type, then creates a Percent Off Brand promotion (no coupon) for the brand on store Multistore4. A registered webstore user sees no discount for a non-brand product, a percent discount for the brand product, and then no discount after re-login with quantity 3.
- Needs `STORE_WEBSTORE_ACTIVE` as `store` {}
- Needs `USER_WEBSTORE_REGISTERED` as `user` {"store": "MaxwellsHardwareMultistore4"}
- Needs `PRODUCT_SIMPLE_OF_BRAND_IN_STOCK` as `product` {"brand": "BlackAndDecker", "store": "MaxwellsHardwareMultistore4", "quantityUsed": 3}
- Needs `PRODUCT_SIMPLE_NOT_OF_BRAND_IN_STOCK` as `nonBrandProduct` {"brand": "BlackAndDecker", "store": "MaxwellsHardwareMultistore4"}
- Needs `BRAND_WITH_ACTIVE_PRODUCTS` as `brand` {"minProducts": 1, "store": "MaxwellsHardwareMultistore4"}

### testClasses.integration.marketing.promotionsAndCoupons.WebstorePromotionAndCouponVerificationTest#T03_verifyAmountOfBrandPromotionFunctionality
- Confidence: medium (needs review). Linked cases: 5. Sources: 30 (15 config, 6 data, 8 created-by-test, 1 expected-output).
- Scenario: Admin creates an Amount Off Brand promotion (no coupon, $2 per item, min qty 2) for the brand on store Multistore4. A registered webstore user sees no discount for a non-brand product or for the brand product at quantity 1, a $4 discount at quantity 2, and then no discount after re-login with quantity 2.
- Needs `STORE_WEBSTORE_ACTIVE` as `store` {}
- Needs `USER_WEBSTORE_REGISTERED` as `user` {"store": "MaxwellsHardwareMultistore4"}
- Needs `PRODUCT_SIMPLE_OF_BRAND_IN_STOCK` as `product` {"brand": "BlackAndDecker", "store": "MaxwellsHardwareMultistore4", "quantityUsed": 2}
- Needs `PRODUCT_SIMPLE_NOT_OF_BRAND_IN_STOCK` as `nonBrandProduct` {"brand": "BlackAndDecker", "store": "MaxwellsHardwareMultistore4"}
- Needs `BRAND_WITH_ACTIVE_PRODUCTS` as `brand` {"minProducts": 1, "store": "MaxwellsHardwareMultistore4"}

### testClasses.integration.marketing.promotionsAndCoupons.WebstorePromotionAndCouponVerificationTest#T04_verifyPercentOffBrandWithMultipleCoupons
- Confidence: high. Linked cases: 1. Sources: 51 (26 config, 5 data, 20 created-by-test).
- Scenario: Admin creates two Percent Off Brand coupon promotions for the brand on store Multistore4 and reads back the generated coupon codes. A registered webstore user adds the brand product, sets quantity 5, applies both coupons and checks the combined percent discount, places an Authorize.Net order, and admin verifies the order total and summary.
- Needs `STORE_WEBSTORE_ACTIVE` as `store` {}
- Needs `USER_WEBSTORE_REGISTERED` as `user` {"store": "MaxwellsHardwareMultistore4"}
- Needs `PRODUCT_SIMPLE_OF_BRAND_IN_STOCK` as `product` {"brand": "BlackAndDecker", "store": "MaxwellsHardwareMultistore4", "quantityUsed": 5}
- Needs `BRAND_WITH_ACTIVE_PRODUCTS` as `brand` {"minProducts": 1, "store": "MaxwellsHardwareMultistore4"}

### testim-migrated/tests/webstore/pdp/verify-configurable-product-details-on-pdp-variant1.spec.ts > Webstore - PDP > verify configurable product details on pdp (variant1)
- Confidence: medium (needs review). Linked cases: 0. Sources: 39 (1 data, 34 expected-output, 4 config).
- Scenario: Guest searches the webstore for a configurable parent product by SKU, opens its PDP, selects variant 1 by its Bulb Size/Color/Technology swatches, and validates the variant's details, tiered offers, stock status, info tabs and the Frequently Bought / You May Also Like related products. Read-only; nothing is added to the cart.
- Needs `PRODUCT_CONFIGURABLE_WITH_VARIANTS` as `parentProduct` {"store": "MaxwellsHardware", "quantityUsed": 1}

### testim-migrated/tests/webstore/shopping-cart/verify-amount-off-order-coupon-functionality.spec.ts > Webstore - Shopping Cart > verify amount off order coupon functionality
- Confidence: medium (needs review). Linked cases: 0. Sources: 10 (5 data, 4 config, 1 expected-output).
- Scenario: A guest empties the cart, adds one simple product, applies an Amount Off Order coupon and checks the flat discount and order total. A registered user then signs in, re-applies the coupon on the merged cart, checks the discount again, and cleans up. No order is placed.
- Needs `PRODUCT_SIMPLE_IN_STOCK` as `product` {"quantityUsed": 1, "store": "MaxwellsHardware"}
- Needs `COUPON_AMOUNT_OFF_ORDER` as `coupon` {"redemptionsNeeded": 1, "store": "MaxwellsHardware"}
- Needs `USER_WEBSTORE_REGISTERED` as `user` {"store": "MaxwellsHardware"}

