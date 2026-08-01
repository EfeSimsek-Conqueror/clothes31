# App Store Submission — Chronological Checklist

Bundle: `com.fitrater.app` | Version 1.0.0

Work top to bottom. Nothing later works if an earlier item is skipped.

---

## Phase 0 — Prerequisites (one time)

- [ ] Apple Developer Program membership is active ($99/yr). Check: https://developer.apple.com/account → Membership → "Active".
- [ ] App-signing certificate + provisioning profile exist for `com.fitrater.app` (already done — app is signed and running on device).
- [ ] Xcode logged into the correct Apple ID under Settings → Accounts → Manage Certificates.
- [ ] `fitrater.ai` domain is registered and DNS points somewhere real. If not: **BLOCKER** — Apple rejects apps with 404 Support / Privacy URLs.

## Phase 1 — App Store Connect app record

- [ ] appstoreconnect.apple.com → My Apps → **+** → New App.
  - Platform: iOS
  - Name: `Fitrater`
  - Primary language: English (U.S.)
  - Bundle ID: `com.fitrater.app` (must appear in dropdown — if not, register it at developer.apple.com/account/resources/identifiers first)
  - SKU: `fitrater-ios-001` (internal, any string)
  - User Access: Full Access

## Phase 2 — Build upload

- [ ] Xcode → open the FitScore project.
- [ ] Set the run target to **Any iOS Device (arm64)**.
- [ ] Bump build number if reuploading (`Product → Scheme → Manage Schemes → Edit → Build → Version` or bump `CFBundleVersion` in Info.plist).
- [ ] Product → Archive. Wait for it.
- [ ] Organizer opens → select the archive → Distribute App → App Store Connect → Upload → Automatically manage signing → Upload.
- [ ] Wait 10–30 min for processing. ASC → your app → TestFlight → iOS Builds → the build appears with a spinner, then goes green.
- [ ] Export Compliance: answer "Does your app use encryption?" — for a stock SwiftUI + URLSession app, the answer is **No (exempt)**. If you want to be extra safe, add `ITSAppUsesNonExemptEncryption = NO` to Info.plist.

## Phase 3 — App Store metadata

- [ ] ASC → your app → App Store → 1.0 Prepare for Submission.
- [ ] Paste from `APP_STORE_METADATA.md`:
  - [ ] Subtitle
  - [ ] Promotional Text
  - [ ] Description
  - [ ] Keywords
  - [ ] Support URL, Marketing URL
  - [ ] Copyright
- [ ] Primary Category: **Lifestyle**. Secondary: **Photo & Video**.

## Phase 4 — Screenshots + icon

- [ ] Upload the six 6.9" screenshots from `/Users/efe/clothes31/ios/store/screenshots/` in this order:
  1. `1_home.png`
  2. `2_studio.png`
  3. `3_cover.png`
  4. `4_powerups.png`
  5. `5_generate.png`
  6. `6_tryon.png`
- [ ] Apple auto-scales 6.9" screenshots for 6.7" and 6.5" displays. You do NOT need to upload smaller sizes.
- [ ] iPad screenshots: skip. The app is iPhone-only unless you explicitly built it for iPad.
- [ ] App Icon: already in the build at `ios/FitScore/FitScore/Assets.xcassets/AppIcon.appiconset/icon-1024.png`. The 1024×1024 marketing icon is pulled from the archive automatically — no separate upload step.

## Phase 5 — In-App Purchases

- [ ] Create the six IAPs per `APP_STORE_IN_APP_PURCHASES.md` (three subscriptions in the "Fitrater Pro" group + three consumable credit packs).
- [ ] For each IAP: fill display name + description, upload a review screenshot (a paywall screenshot works — reuse `3_cover.png` or `5_generate.png` — Apple just wants proof the IAP exists in the app).
- [ ] Set each to "Cleared for Sale".
- [ ] Attach the three subscriptions to this version so they submit together (Version → In-App Purchases and Subscriptions → **+**).

## Phase 6 — RevenueCat

- [ ] Follow `REVENUECAT_SETUP.md` end to end.
- [ ] Test one sandbox purchase on-device before submitting.

## Phase 7 — Age rating

- [ ] Questionnaire → per `APP_STORE_METADATA.md` "Age Rating: 12+" section. All "None" except the notes there.

## Phase 8 — App Privacy

- [ ] Paste answers from `APP_PRIVACY_ANSWERS.md`.

## Phase 9 — Pricing

- [ ] Pricing and Availability → Price → **Free** (Pro is unlocked via IAP, the app itself is free).
- [ ] Availability → all territories (or restrict — user's call). Turkey + US at minimum.
- [ ] Pre-orders: off.

## Phase 10 — Review notes + demo account

- [ ] Version → App Review Information → paste `REVIEW_NOTES.md`.
- [ ] Sign-in required: Yes.
- [ ] Demo account email + password: create a fresh Supabase user with Pro pre-granted; put credentials in the review notes field.
- [ ] Contact: Efe Simsek — efe@cloudgeng.com — phone number required (real one, Apple sometimes calls).

## Phase 11 — Submit

- [ ] Version → Add for Review → Submit.
- [ ] Automatic release: **Manually release this version** (safer — you push the button once approved).
- [ ] Advertising Identifier (IDFA): **No** (app does not use it — no ad SDK, no attribution SDK).

## Wait times

- Typical: **24–48 hours** for approval on subsequent submissions.
- First submission: often **2–7 days**. Metadata rejections (e.g. dead Privacy URL, missing demo account) add 24–48h round trips.
- Expedited review: available once per year for genuine emergencies. Do not use it for the first submission.
