# Fitrater — Google Play Store setup

Step-by-step to ship `com.fitrater.app` to the Play Store production track.

Everything you need is in this `store/` directory:

| File | What it's for |
|---|---|
| `fitrater-1.0.0.aab` | The signed Android App Bundle to upload to Play Console |
| `fitrater-icon-512.png` | 512×512 hi-res icon for the listing |
| `feature-graphic-1024x500.png` | 1024×500 feature graphic |
| `screenshots/*.png` | 6 phone screenshots (1080×2340) |
| `privacy.html` | Privacy policy — host at `https://fitrater.ai/privacy` |
| `terms.html` | Terms of service — host at `https://fitrater.ai/terms` |
| `DESCRIPTION.md` | Short + full store description |
| `RELEASE_KEYSTORE_INFO.md` | Release keystore location, passwords, and SHA-1 |

## 1. Host the legal pages

Before you can complete the Play Store data-safety and listing forms you need public URLs for the privacy policy and terms. Upload `privacy.html` and `terms.html` to `fitrater.ai/privacy` and `fitrater.ai/terms`. The pages are fully self-contained (inline CSS) — drop them into `/public` on Vercel or any static host.

## 2. Create the app in Play Console

1. Go to https://play.google.com/console → **Create app**
2. Fill in:
   - App name: **Fitrater**
   - Default language: **English (United States)**
   - App or game: **App**
   - Free or paid: **Free**
3. Confirm developer program policies and US export laws → **Create app**

## 3. Complete the setup checklist

Play Console shows a **Set up your app** checklist. Work through it top-to-bottom:

- **App access** — Select "All or some functionality is restricted." Provide reviewer credentials. Since Fitrater requires Google Sign-In, add a test Google account (or a magic-link fallback if you build one) in the notes so reviewers can get past the sign-in screen. Explain: *"App requires Google Sign-In. Please use the test account provided; all features unlock after sign-in."*
- **Ads** — **No, my app does not contain ads.**
- **Content rating** — Fill out the IARC questionnaire. Answers: no violence, no sexual content, no profanity, no gambling, no drug references, no user-generated content that's public (photos are private per-user). Target rating: **Everyone** or **Teen (13+)**.
- **Target audience and content** — Age groups: **18 and over** (or 13–17 + 18 and over if you want teens; recommended to keep 18+ since photos are involved). No appeal to children.
- **News app** — **No**.
- **Data safety** — see section 5.
- **Government app** — **No**.
- **Financial features** — **No**.
- **Health apps** — **No**.

## 4. Store listing

- **App name:** Fitrater
- **Short description:** `Score outfits, design pieces, keep a style journal — Hem's got taste.`
- **Full description:** copy the text from `DESCRIPTION.md`
- **App icon:** upload `fitrater-icon-512.png`
- **Feature graphic:** upload `feature-graphic-1024x500.png`
- **Phone screenshots:** upload all six PNGs from `screenshots/` (minimum 2, maximum 8; we have 6)
- **App category:** Lifestyle
- **Tags:** fashion, style, outfits, AI
- **Store listing contact details:**
  - Email: `efe@cloudgeng.com`
  - Website: `https://fitrater.ai`
- **Privacy policy URL:** `https://fitrater.ai/privacy`

## 5. Data safety form

Declare:

**Data collected**
- Personal info → **Email address, Name** — collected, tied to user account. Purpose: Account management. Optional: No. Encrypted in transit: Yes.
- Photos and videos → **Photos** — collected. Purpose: App functionality (scoring), Personalization. Optional: No (core feature). Encrypted in transit: Yes.
- Files and docs → not collected.
- Financial info → not collected (Google Play Billing handles payment; we do not receive card data).
- App info and performance → **Crash logs, Diagnostics** — collected. Purpose: Analytics.
- Device or other IDs → **Device or other IDs** — collected. Purpose: Analytics (RevenueCat installation id).

**Data sharing**
- Email, Name, Photos → shared with **Fal.ai** (for AI processing during a scoring / generation request).
- Purchase history → shared with **RevenueCat** (billing reconciliation).
- No data is shared for advertising or data brokerage.

**Security practices**
- Data encrypted in transit: **Yes**.
- Users can request data deletion: **Yes** (in-app + email).

## 6. In-app products (consumable credit packs)

Play Console → **Monetize → Products → In-app products → Create product**. Create four managed (consumable) products:

| Product ID | Name | Description | Default price (USD) | Credits granted |
|---|---|---|---|---|
| `credits_100` | Starter | 100 Fitrater credits | 2.99 | 100 |
| `credits_500` | Popular | 400 Fitrater credits — best value | 9.99 | 400 |
| `credits_1500` | Pro | 1,200 Fitrater credits | 29.99 | 1,200 |

For each: type = **Consumable**, status = **Active**, tax category = default.

> **Note:** The SKU ID (e.g. `credits_500`) is the internal Play Store identifier and doesn't have to equal the credit count. The app's client-side map in `SupabaseClient.kt` (`CREDIT_PACK_GRANTS`) is the source of truth for credits actually granted. Mega pack (credits_5000) is intentionally dropped to push whales toward subscriptions.

## 7. Subscriptions

Play Console → **Monetize → Products → Subscriptions → Create subscription**.

Create **one** subscription:

- Product ID: `fitrater_pro`
- Name: **Fitrater Pro**
- Description: *Unlimited monthly credit refills, priority scoring, Sunday letters, and the deeper Studio.*
- Benefits: unlimited credits, weekly Sunday letter, priority scoring, studio pro

Add **two base plans** under this subscription (Weekly plan dropped — bad economics):

| Base plan ID | Billing period | Auto-renewing | Price (USD) | Free trial | Monthly credit cap |
|---|---|---|---|---|---|
| `monthly` | 1 month | Yes | 9.99 | — | 400 |
| `annual` | 1 year | Yes | 59.99 | 7 days | 250 (3,000/yr avg) |

For the `annual` base plan, add an **Offer** with a **Free trial** phase of 7 days.

> **Monthly cap** is enforced client-side via `SUB_MONTHLY_CAP` / `SUB_ANNUAL_MONTHLY_CAP` in `SupabaseClient.kt`. Sub advertising says "unlimited-feel" — cap only trips for whales (~1% of users).

## 8. RevenueCat setup

1. Sign in to https://app.revenuecat.com and **create a new project**: Fitrater.
2. **Add an app** → Play Store → package name `com.fitrater.app`.
3. **Upload a Play Console service account JSON** so RevenueCat can read purchase status:
   - In Google Cloud Console → IAM & Admin → Service accounts → Create service account → grant it the Pub/Sub Publisher role and download a JSON key.
   - In Play Console → Setup → API access → link that service account → grant "View financial data" + "Manage orders and subscriptions" scopes.
   - Upload the JSON into the RevenueCat Play Store app config.
4. In RevenueCat → **Project Settings → API Keys** — copy the **Public app-specific API key** for the Android app (`goog_...`). Paste it into `SupabaseClient.kt`:
   ```
   const val RC_ANDROID_API_KEY: String = "goog_YOUR_KEY_HERE"
   ```
   Rebuild the release AAB (`./gradlew :app:bundleRelease`) and upload the new one.
5. **Entitlements → New entitlement** → identifier `pro`.
6. **Products → Import from Play Store** — this pulls in `credits_100`, `credits_500`, `credits_1500`, and `fitrater_pro:monthly`, `fitrater_pro:annual`. Import can take up to 10 minutes after the products are Active in Play Console. **The RevenueCat project is already set up** (`proj6d59afb8` / "clothes" project) with entitlement `pro`, packages for these SKUs, and Android API key `goog_tRCMTCDVJbHcQYWqvbfQBxgvaKi` already wired into the app.
7. **Attach** each subscription product (`fitrater_pro:*`) to the `pro` entitlement.
8. **Offerings → Create offering** → identifier `default`. Add packages:
   - Custom package `credits_100` → attach product `credits_100`
   - Custom package `credits_500` → attach product `credits_500`
   - Custom package `credits_1500` → attach product `credits_1500`
   - Custom package `credits_5000` → attach product `credits_5000`
   - `$rc_weekly` → attach `fitrater_pro:weekly`
   - `$rc_monthly` → attach `fitrater_pro:monthly`
   - `$rc_annual` → attach `fitrater_pro:annual`
9. Set the `default` offering as **current**.

## 9. Google OAuth client — add the release SHA-1

Google Sign-In will fail on the Play-installed build unless the release cert fingerprint is registered.

- Open Google Cloud Console → **APIs & Services → Credentials** (project **CLOTHES**).
- Find the Android OAuth 2.0 Client ID for `com.fitrater.app`.
- Add the release SHA-1: `2E:FF:62:D0:23:0D:29:5C:F7:DD:60:AE:95:12:B8:D9:F3:CE:2E:BC`
- **Also add the Play App Signing SHA-1** once you enroll (Play Console → App integrity → App signing key certificate → SHA-1). This is different from the upload key.

## 10. Upload the AAB

- Play Console → **Release → Production → Create new release**
- Opt in to **Play App Signing** when prompted (keep this JKS as the upload key; Google generates the signing key).
- Upload `store/fitrater-1.0.0.aab`
- Release name: `1.0.0`
- Release notes:
  ```
  Fitrater 1.0 — first launch.

  • Score any outfit with Hem, our editorial AI stylist.
  • Design garments in the Studio and add them to your closet.
  • Keep a Journal of every look — palette, best fits, monthly trend.
  • Sunday letters when you're on Pro.
  ```
- **Countries and regions:** roll out globally (or start with a smaller set for a soft launch).
- Save → Review release → **Start rollout to Production**.

## 11. Post-launch to-do

- Add the App signing SHA-1 to Google OAuth (see step 9).
- Once real purchases start hitting, watch the **RevenueCat dashboard** for reconciliation errors — most are caused by the Play Console service account missing a scope.
- The `credit_transactions` insert on IAP success is client-side. For hardened reconciliation, wire a RevenueCat webhook → Supabase edge function that verifies the transaction and inserts the credit ledger row server-side. Client-side inserts are fine for v1 but a determined user could game them.
