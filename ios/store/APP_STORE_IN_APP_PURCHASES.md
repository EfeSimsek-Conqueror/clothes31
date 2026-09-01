# In-App Purchases — App Store Connect

Bundle: `com.fitrater.app` · ASC app id `6794940458`
RevenueCat entitlement granted by every paid tier: `fitscore_pro`
(`FitScore/Data/RcBilling.swift:18`, referenced in code as
`RcBilling.entitlementPro`)
RevenueCat iOS public SDK key in the build: `appl_XcRnJzmGqEityyPyHuMWRdaSWvV`
(`FitScore/Data/SupaClient.swift:30`)

Checked against the source tree on 2026-09-01.

---

## Read this first — the product IDs are `_v2_`, not `_pro_`

An earlier version of this document described six `fitrater_pro_*` products.
Those are **not** the products in App Store Connect. Per
`ios/store/SESSION_2026-08-03.md:18-27` and §3 of that log, three
`fitrater_pro_credits_*` records were created by mistake and then deleted, and
the live App Store Connect inventory is:

- Subscriptions, group *FitScore Pro*, group id `22266489`:
  `fitrater_v2_weekly`, `fitrater_v2_monthly`, `fitrater_v2_annual`
- Consumables: `fitrater_v2_credits_100` (ASC id `6794943465`),
  `fitrater_v2_credits_500` (`6794942873`),
  `fitrater_v2_credits_1500` (`6794942874`),
  `fitrater_v2_credits_5000` (`6794943552`)

The credit-pack IDs are load-bearing: the grant table is keyed by exact product
ID (`FitScore/Data/SupaClient.swift:86-91`) and the paywall tiles match by exact
equality (`FitScore/You/CreditsSheet.swift:33-37`). A renamed or mistyped ID
means a purchase that grants nothing.

The subscription IDs are not referenced by name anywhere in the client —
purchases go through RevenueCat's `Package` API — so they matter only to App
Store Connect and to the RevenueCat dashboard.

**Note on `FitScore/Fitrater.storekit`:** the local StoreKit test config still
contains the retired `fitrater_pro_*` IDs and has no `_5000` pack. It only
affects Xcode's local StoreKit testing, never production, which is served by
RevenueCat offerings. Testing the credit packs against it will show blank
prices. Either update it to the `_v2_` IDs or use a sandbox account instead.

---

## Subscription Group: "Fitrater Pro" (id `22266489`)

Group localization (English):
- Display Name: `Fitrater Pro`
- Description: `Unlimited scoring, Try on, Studio without the cap, and the weekly Sunday letter.`

All three tiers grant the same `fitscore_pro` entitlement, so users can move
between them cleanly.

### 1. `fitrater_v2_weekly` — Weekly

| Field | Value |
|---|---|
| Product ID | `fitrater_v2_weekly` |
| Reference Name | Fitrater Pro Weekly |
| Duration | 1 Week |
| Price (USD) | **verify in ASC before submitting** |
| Introductory Offer | **verify in ASC before submitting** |
| Family Sharing | Off |

Localization (English):
- Display Name: `Fitrater Pro — Weekly`
- Description: `Full Pro access, billed weekly. Unlimited scoring, Try on, Studio without the cap, and the weekly Sunday letter.`

Review notes:
```
Weekly Pro tier. Grants the "fitscore_pro" entitlement in RevenueCat.
Unlocks unlimited outfit scoring, Try on, Studio without the piece cap,
Hem's full wardrobe memory, and the weekly Sunday letter.
The review account already has Pro granted — no purchase is needed to test.
```

### 2. `fitrater_v2_monthly` — Monthly

| Field | Value |
|---|---|
| Product ID | `fitrater_v2_monthly` |
| Reference Name | Fitrater Pro Monthly |
| Duration | 1 Month |
| Price (USD) | $14.99 (confirmed — `SESSION_2026-08-03.md` §1c) |
| Introductory Offer | **verify in ASC before submitting** |
| Family Sharing | Off |

Localization (English):
- Display Name: `Fitrater Pro — Monthly`
- Description: `Full Pro access, billed monthly. Unlimited scoring, Try on, Studio without the cap, and the weekly Sunday letter.`

Review notes:
```
Monthly Pro tier. Same "fitscore_pro" entitlement as weekly and annual.
```

Historical note: this SKU sat in MISSING_METADATA because it lacked a Costa Rica
price. That was fixed. If it drops back to MISSING_METADATA, check for a
territory the other tiers have and this one does not.

### 3. `fitrater_v2_annual` — Annual

| Field | Value |
|---|---|
| Product ID | `fitrater_v2_annual` |
| Reference Name | Fitrater Pro Annual |
| Duration | 1 Year |
| Price (USD) | **verify in ASC before submitting** |
| Introductory Offer | **verify in ASC before submitting** |
| Family Sharing | Off |

Localization (English):
- Display Name: `Fitrater Pro — Annual`
- Description: `Full Pro access for a year. Unlimited scoring, Try on, Studio without the cap, and the weekly Sunday letter.`

Any "save X%" claim in the localization must be recomputed from the prices
actually live in ASC. Do not carry a percentage forward from an old draft.

Review notes:
```
Annual Pro tier. Maps to the "$rc_annual" package in the "default" RevenueCat
offering. Same "fitscore_pro" entitlement.
```

---

## Consumable Credit Packs

Credits fund the AI features for users who do not want a subscription. The
client grant table is `Supa.creditPackGrants` in
`FitScore/Data/SupaClient.swift:86-91`. **The product IDs below must match it
exactly** — the lookup is an exact-key dictionary hit, so a mismatch silently
grants nothing.

Prices are the final values from `SESSION_2026-08-03.md` §4.

| Product ID | Tier name | Price (USD) | Credits granted |
|---|---|---|---|
| `fitrater_v2_credits_100` | Starter | $1.99 | 150 |
| `fitrater_v2_credits_500` | Popular | $6.99 | 500 |
| `fitrater_v2_credits_1500` | Pro Pack | $14.99 | 1,200 |
| `fitrater_v2_credits_5000` | Mega | $39.99 | 3,000 |

The numeric suffix in each product ID is a historical artefact of an earlier
bundle size and is **not** the grant. Every user-visible surface — the App Store
display name, the App Store description, and the paywall tile in the app — states
the real grant from the table above, so no user is shown one number and given
another. Do not rename the SKUs; existing owners would break.

Set each localization as follows.

### `fitrater_v2_credits_100`
- Display Name: `Starter — 150 credits`
- Description: `150 credits for scoring, Try on, Studio pieces and covers.`
- Review notes: `Consumable. Grants 150 in-app credits on purchase. Credits are spent on AI generations. No expiry.`

### `fitrater_v2_credits_500`
- Display Name: `Popular — 500 credits`
- Description: `500 credits for scoring, Try on, Studio pieces and covers.`
- Review notes: `Consumable. Grants 500 in-app credits on purchase.`

### `fitrater_v2_credits_1500`
- Display Name: `Pro Pack — 1,200 credits`
- Description: `1,200 credits — for heavier Try-on and cover work.`
- Review notes: `Consumable. Grants 1,200 in-app credits on purchase.`

### `fitrater_v2_credits_5000`
- Display Name: `Mega — 3,000 credits`
- Description: `3,000 credits — the largest pack.`
- Review notes: `Consumable. Grants 3,000 in-app credits on purchase.`

---

## What credits buy (from `FitScore/Data/SupaClient.swift`)

Useful when writing IAP descriptions, and for sanity-checking that a pack is
worth something.

| Feature | Cost |
|---|---|
| Score a look | 5 |
| A vs B | 8 |
| Decode style | 10 |
| Decode the invite | 6 |
| Occasion Coach | 15 (free once a week) |
| Compose a cover | 10 |
| Try on | 20 (Pro only) |
| Studio single piece | 10 |
| Studio outfit, 2–4 pieces | 18 / 24 / 32 |
| Chat with Hem | free |

New accounts are granted 25 credits on sign-up.

---

## Cross-check with client code

| Product ID | Client value | Source |
|---|---|---|
| `fitrater_v2_credits_100` | 150 credits | `SupaClient.swift:87` |
| `fitrater_v2_credits_500` | 500 credits | `SupaClient.swift:88` |
| `fitrater_v2_credits_1500` | 1200 credits | `SupaClient.swift:89` |
| `fitrater_v2_credits_5000` | 3000 credits | `SupaClient.swift:90` |
| all three subscriptions | entitlement `fitscore_pro` | `RcBilling.swift:18` |

## Trial and renewal disclosure in the app

The paywall no longer hardcodes any trial length. Each plan tile and the renewal
disclosure are generated at runtime from that product's StoreKit
`introductoryDiscount` (`FitScore/You/CreditsSheet.swift`), so the app displays
exactly the offers configured in App Store Connect. A product configured with no
introductory offer displays no trial claim at all.

The practical consequence: **whatever you configure in ASC is what the user is
promised.** There is no second place to keep in sync, but equally there is no
safety net if an offer is configured wrongly.

## Open items for a human

1. Fill in the weekly and annual prices and introductory offers above from the
   live ASC records, then re-check the localization text against them.
2. Confirm all seven products reach READY_TO_SUBMIT and are attached to version
   1.0. `SESSION_2026-08-03.md` §7.1 records that the ASC API would not let IAPs
   be added to a review submission programmatically; do it in the ASC UI and
   verify visually.
3. Run one sandbox purchase of a subscription and one of a credit pack before
   submitting, and confirm the credit balance moves by the amount in the table.
