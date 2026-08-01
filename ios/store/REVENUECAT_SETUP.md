# RevenueCat — Dashboard Setup

Public iOS SDK key already wired in the app: `appl_CzEAtrNVYSafdHDIXAFTUOgIgAU`
Entitlement code constant: `fitscore_pro` (see `RcBilling.swift:18`)

Do this AFTER the six IAPs are created and in "Ready to Submit" state in App Store Connect. RevenueCat pulls product metadata from ASC, so the products must exist there first.

---

## 1. Confirm the App / Project

1. Log in → RevenueCat dashboard.
2. Project → the one whose Apple Public SDK key ends `…SafdHDIXAFTUOgIgAU`.
3. Verify: **Apps** → an iOS app with bundle id `com.fitrater.app` exists. If not, create it:
   - Name: `Fitrater iOS`
   - Bundle ID: `com.fitrater.app`
   - Upload the App Store Connect API key (Key ID, Issuer ID, .p8 file) so RevenueCat can auto-import products. Get these from App Store Connect → Users and Access → Integrations → App Store Connect API → Generate. Role: **App Manager** minimum.

## 2. App Store Shared Secret

1. App Store Connect → your app → App Information → App-Specific Shared Secret → Generate/Copy.
2. RevenueCat → Apps → Fitrater iOS → App-specific shared secret → paste → Save.
   (Required for server-side receipt validation of legacy StoreKit 1 receipts.)

## 3. Import / Create Products

Products tab → New (or "Import from App Store Connect"). You should end up with all six:

**Subscriptions**
- `fitrater_pro_weekly` — Subscription (Non-consumable auto-renewable) — duration 1 week
- `fitrater_pro_monthly` — Subscription — 1 month
- `fitrater_pro_annual` — Subscription — 1 year

**Consumables** (mark as Non-subscription → Consumable in RC)
- `fitrater_pro_credits_100`
- `fitrater_pro_credits_500`
- `fitrater_pro_credits_1500`

Attach each to the Fitrater iOS app.

## 4. Entitlement

Entitlements → New.
- **Identifier:** `fitscore_pro` (MUST match `RcBilling.entitlementPro`. This is the server name; the display can be anything.)
- **Display Name:** `Pro`
- Attach products: `fitrater_pro_weekly`, `fitrater_pro_monthly`, `fitrater_pro_annual`.
- Do NOT attach the credit-pack consumables (credits are handled via a separate ledger, not entitlements).

## 5. Offering

Offerings → New.
- **Identifier:** `default` (lowercase — RevenueCat's convention for the fallback offering the SDK returns via `Offerings.current`)
- **Display Name:** `Fitrater Pro`
- Packages (attach in this order):
  1. Package ID: `$rc_weekly` → product `fitrater_pro_weekly`
  2. Package ID: `$rc_monthly` → product `fitrater_pro_monthly`
  3. Package ID: `$rc_annual` → product `fitrater_pro_annual`

Set this Offering as **Current**.

Credit packs are NOT part of the Offering — they are surfaced separately in the app via direct product lookups.

## 6. Customer Identification (Supabase)

The app already calls `Purchases.shared.logIn(supabaseUserId)` on session start (`RcBilling.swift`). No dashboard toggle needed for that — it's automatic once the SDK sees a login.

Optional but recommended: **Integrations → Supabase** — enables the RC → Supabase attribute sync so you can see purchase state in your DB tables without a webhook. If you want push-based sync, use a webhook instead:
- Integrations → Webhooks → Add
- URL: your Supabase Edge Function endpoint (e.g. `https://<project>.supabase.co/functions/v1/rc-webhook`)
- Auth header: bearer token you control

## 7. Sandbox Testers

App Store Connect (not RevenueCat) — **Users and Access → Sandbox → Testers → +**.
- Use a fresh email (not tied to a real Apple ID). Gmail `+` aliases work: `efe+sandbox1@cloudgeng.com`.
- Set territory + password.
- On the physical iOS device: Settings → App Store → Sandbox Account → sign in with the sandbox tester.
- Do NOT sign into iCloud with the sandbox tester. It's an App Store-only identity.
- Purchases in sandbox renew on an accelerated timeline: 1 week = 3 min, 1 month = 5 min, 1 year = 1 hr. Trials still fire.

## 8. Verification Checklist

Before submitting to App Review:
- [ ] All 6 products show "Ready to Submit" or "Approved" in ASC.
- [ ] All 6 products imported and visible in RC → Products.
- [ ] `fitscore_pro` entitlement has 3 subscriptions attached.
- [ ] `default` Offering is set to Current with 3 packages (`$rc_weekly`, `$rc_monthly`, `$rc_annual`).
- [ ] App Store shared secret pasted.
- [ ] Sandbox tester created + signed in on device.
- [ ] Test flow on device: PaywallView loads packages → purchase weekly with trial → `entitlements["fitscore_pro"].isActive == true` → restore purchases works.
- [ ] Test credit pack: buy `fitrater_pro_credits_100` → credit balance increments by 100.
