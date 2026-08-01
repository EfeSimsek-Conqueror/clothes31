# In-App Purchases — App Store Connect

Configure these in App Store Connect → your app → Features → In-App Purchases and Subscriptions.

Bundle: `com.fitrater.app`
Entitlement (RevenueCat server name): `fitscore_pro`
Pro entitlement code constant: `RcBilling.entitlementPro`

---

## Subscription Group: "Fitrater Pro"

Create a single Subscription Group named **Fitrater Pro** (reference name: `fitrater_pro`). Put all three subscriptions inside it so users can upgrade/downgrade cleanly.

Group localization (English):
- Display Name: `Fitrater Pro`
- Description: `Unlimited scoring, Try-on, Compose Cover, and the weekly Sunday Letter.`

---

### 1. `fitrater_pro_weekly` — Weekly

| Field | Value |
|---|---|
| Product ID | `fitrater_pro_weekly` |
| Reference Name | Fitrater Pro Weekly |
| Duration | 1 Week |
| Price (USD) | $6.99 |
| Price (TRY) | ~₺249 (use ASC's auto-generated tier for equivalent) |
| Introductory Offer | 7-day Free Trial (new subscribers only) |
| Family Sharing | Off |

Localization (English):
- Display Name: `Fitrater Pro — Weekly`
- Description: `Full Pro access, billed weekly. 7-day free trial for new subscribers. Unlocks unlimited scoring, Try-on, Compose Cover, and brutal-mode critiques.`

Review notes:
```
Weekly Pro tier. Grants the "fitscore_pro" entitlement in RevenueCat.
Unlocks: unlimited outfit scoring, unlimited Try-on renders, Compose Cover
templates, brutal-mode Hem critiques, weekly Sunday Letter.
Test account already has Pro granted — no purchase needed to test features.
```

---

### 2. `fitrater_pro_monthly` — Monthly

| Field | Value |
|---|---|
| Product ID | `fitrater_pro_monthly` |
| Reference Name | Fitrater Pro Monthly |
| Duration | 1 Month |
| Price (USD) | $14.99 |
| Introductory Offer | 3-day Free Trial (new subscribers only) |
| Family Sharing | Off |

Localization (English):
- Display Name: `Fitrater Pro — Monthly`
- Description: `Full Pro access, billed monthly. 3-day free trial for new subscribers. Unlimited scoring, Try-on, Compose Cover, and the Sunday Letter.`

Review notes:
```
Monthly Pro tier. Same "fitscore_pro" entitlement as weekly/annual.
```

---

### 3. `fitrater_pro_annual` — Annual (best value)

| Field | Value |
|---|---|
| Product ID | `fitrater_pro_annual` |
| Reference Name | Fitrater Pro Annual |
| Duration | 1 Year |
| Price (USD) | $79.99 |
| Introductory Offer | 7-day Free Trial (new subscribers only) |
| Family Sharing | Off |
| Marketing note | "Save 55% vs weekly" |

Localization (English):
- Display Name: `Fitrater Pro — Annual`
- Description: `Full Pro access for a year. 7-day free trial for new subscribers. Save 55% vs weekly billing. Everything in Pro: unlimited scoring, Try-on, Compose Cover, Sunday Letter.`

Review notes:
```
Annual Pro tier. In RevenueCat this maps to the "$rc_annual" package
in the "default" Offering. Same "fitscore_pro" entitlement.
```

---

## Consumable Credit Packs

Credits are one-time purchases for users who don't want a subscription. They fund Try-on and Compose Cover renders. Client-side grant table lives in `SupaClient.swift → Supa.creditPackGrants`. **SKU IDs below MUST match the code exactly** or the credit grant will silently no-op.

### `fitrater_pro_credits_100` — Starter

| Field | Value |
|---|---|
| Product ID | `fitrater_pro_credits_100` |
| Reference Name | Starter — 100 credits |
| Type | Consumable |
| Price (USD) | $2.99 |
| Grants | 100 credits |

Localization:
- Display Name: `Starter — 100 credits`
- Description: `100 credits for scoring, Try-on, and Compose Cover.`

Review notes:
```
Consumable. Grants 100 in-app credits on successful purchase.
Credits are spent on AI renders (Try-on, Compose Cover). No expiry.
```

---

### `fitrater_pro_credits_500` — Popular (grants 400)

| Field | Value |
|---|---|
| Product ID | `fitrater_pro_credits_500` |
| Reference Name | Popular — 400 credits |
| Type | Consumable |
| Price (USD) | $9.99 |
| Grants | **400 credits** |

FLAG — historical naming: the SKU is `_500` but grants **400** credits. This is intentional (per `Supa.creditPackGrants` in `SupaClient.swift:66`). The App Store display name and description use "400 credits" so users are never misled. Do not rename the SKU — renaming would break users who already own it.

Localization:
- Display Name: `Popular — 400 credits`
- Description: `400 credits — best value pack for occasional Try-on and Cover renders.`

Review notes:
```
Consumable. Grants 400 in-app credits. SKU ID contains "_500" for
historical reasons; the user-facing display and actual grant are 400.
```

---

### `fitrater_pro_credits_1500` — Pro Pack (grants 1200)

| Field | Value |
|---|---|
| Product ID | `fitrater_pro_credits_1500` |
| Reference Name | Pro Pack — 1200 credits |
| Type | Consumable |
| Price (USD) | $29.99 |
| Grants | **1200 credits** |

Localization:
- Display Name: `Pro Pack — 1200 credits`
- Description: `1200 credits — power-user bundle for heavy Try-on and Cover work.`

Review notes:
```
Consumable. Grants 1200 in-app credits. Same historical-naming convention
as the 500-SKU (name > actual grant).
```

---

## Cross-check with client code

| Product ID | Client constant / grant | Source |
|---|---|---|
| `fitrater_pro_credits_100` | 100 | `SupaClient.swift:65` |
| `fitrater_pro_credits_500` | 400 | `SupaClient.swift:66` |
| `fitrater_pro_credits_1500` | 1200 | `SupaClient.swift:67` |
| `fitrater_pro_weekly` | entitlement `fitscore_pro` | `RcBilling.swift:18` |
| `fitrater_pro_monthly` | entitlement `fitscore_pro` | `RcBilling.swift:18` |
| `fitrater_pro_annual` | entitlement `fitscore_pro` | `RcBilling.swift:18` |

## Inconsistencies flagged
1. **Existing `Fitrater.storekit`** (local test config) uses `fitrater_pro_monthly` and `fitrater_pro_annual` — NOT `fitrater_pro_monthly/annual/weekly`. And it has NO weekly tier.
   - Decision path A (recommended): create the three new `fitrater_pro_*` SKUs in App Store Connect (matches user's ASC spec), and UPDATE `Fitrater.storekit` to match so the sandbox test config aligns with production. Weekly tier is currently missing from storekit entirely.
   - Decision path B: keep the storekit IDs (`fitrater_pro_monthly`, `fitrater_pro_annual`) and mirror those in ASC. Would need a new `fitrater_v2_weekly` SKU. This is less clean because the naming mixes "v2" (credits) with subscriptions.
   - **This document assumes Path A.** If Path B is preferred, rename product IDs throughout this file.
2. No client code currently references the subscription product IDs by name — Purchases are made via RevenueCat's `Package` API (`RcBilling.swift:119`), so ID naming is purely a dashboard/ASC concern. Safe to pick either.
