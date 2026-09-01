# App Privacy Questionnaire — Answers

Fill this in at ASC → App Privacy → Edit.

Checked against the source tree on 2026-09-01. Version 1.0, build 16.

---

## What is actually in the binary

Verified by grep over `ios/FitScore/` and over the Swift package products in
`FitScore.xcodeproj/project.pbxproj`:

- **Supabase** — auth, database, storage. First-party backend.
- **RevenueCat** — purchases and entitlements.
- **Fal AI** — reached from Supabase edge functions, not from the client SDK.
  Receives photos for the duration of a render.
- **Google Sign-In** — optional sign-in method.
- **Firebase Core + Firebase Crashlytics** — crash reporting only.
  `FirebaseApp.configure()` at `FitScore/App/FitraterApp.swift:17` is the only
  Firebase call in the app; nothing sets a Crashlytics user identifier.
- **FirebaseAnalytics / GoogleAppMeasurement** — **being removed from the
  target.** These answers assume it is gone. Confirm before submitting: the
  Analytics package product must no longer appear in the app target's
  Frameworks phase. If it ships, these answers are wrong and you must add
  Product Interaction / Other Usage Data collected by Google.

There is **no** advertising SDK, **no** attribution SDK, and no Amplitude,
Mixpanel, PostHog, Segment or equivalent. There is no `AdSupport`, no
`ATTrackingManager` and no `AppTrackingTransparency` anywhere in the iOS tree —
grep returns zero hits — so **IDFA: No** and **tracking: No** are both truthful,
and no App Tracking Transparency prompt is needed.

**Global answer: "Yes, we collect data from this app."**

---

## Data Types Collected

For every entry below: **Used for tracking = No.** Nothing is shared with a data
broker, joined with third-party data, or used for advertising.

### 1. Contact Info → Email Address
- **Collected:** Yes
- **Linked to user:** Yes
- **Purposes:** App Functionality (account creation, sign-in)
- **Where:** Supabase Auth
- **Optional?** No — an account is required

### 2. User Content → Photos or Videos
- **Collected:** Yes
- **Linked to user:** Yes
- **Purposes:** App Functionality (outfit scoring, Studio wardrobe, Try on, body
  calibration, cover composition), Personalization (style profile)
- **Where:** Supabase Storage in private per-user buckets; sent transiently to
  Fal AI for the duration of a render and not retained by Fal for training

### 3. User Content → Other User Content
- **Collected:** Yes
- **Linked to user:** Yes
- **Purposes:** App Functionality, Personalization
- **What:** style preferences, outfit and garment tags, scores and critiques,
  saved looks, Journal entries, Studio pieces, cover templates, body-calibration
  measurements, stylist chat and wardrobe memory rows, and the optional free-text
  note a user types when reporting generated content
- **Where:** Supabase Postgres

  Note: chat transcripts in "Chat with Hem" are session-only on the device and
  are not written to a server-side message log.

### 4. Identifiers → User ID
- **Collected:** Yes
- **Linked to user:** Yes
- **Purposes:** App Functionality
- **What:** the Supabase user UUID, server-generated. Passed to RevenueCat as the
  App User ID so entitlements can be looked up.
- **Where:** Supabase, RevenueCat

### 5. Identifiers → Device ID
- **Collected:** Yes
- **Linked to user:** Yes
- **Purposes:** App Functionality (RevenueCat uses the vendor identifier as a
  fallback subject for purchase and restore)
- **Where:** RevenueCat

### 6. Purchases → Purchase History
- **Collected:** Yes
- **Linked to user:** Yes
- **Purposes:** App Functionality (unlock the Pro entitlement, restore purchases,
  credit grants)
- **Where:** RevenueCat, mirrored to Supabase

### 7. Diagnostics → Crash Data
- **Collected:** Yes
- **Linked to user:** **No**
- **Purposes:** App Functionality
- **What:** device model, OS version, app version, crash stack trace
- **Where:** **Google (Firebase Crashlytics)** — a third party. Declare it as
  such.

  The "not linked" answer is accurate because nothing in the app sets a
  Crashlytics user identifier or custom keys. If that ever changes, this answer
  must change with it.

  This matches the published Privacy Policy, which discloses Firebase
  Crashlytics by name and states that Firebase Analytics and advertising SDKs are
  not used.

---

## Data NOT collected — leave these unchecked

- **Location.** There is no `CoreLocation` or `CLLocation` usage anywhere in the
  iOS target and no `NSLocation*UsageDescription` in `Info.plist`. The Home
  weather card that used a hardcoded coordinate has been removed and the app
  makes no weather request. The published Privacy Policy now states in print that
  the iOS app does not request or use location, so declaring Location here would
  contradict it. (Approximate location applies to the **Android** build only and
  must not be declared on the iOS submission.)
- **Audio Data.** The voice-transcription path has been removed from the iOS
  client and there is no `NSMicrophoneUsageDescription` in `Info.plist`.
- **Health & Fitness.** No HealthKit. Body-calibration measurements are styling
  data recorded under User Content, not health data.
- **Financial Info.** All payment is handled by Apple; no card data reaches the
  app.
- **Contacts, Search History, Browsing History, Sensitive Info, Gameplay
  Content, Advertising Data.**
- **Product Interaction / Other Usage Data.** Conditional on FirebaseAnalytics
  actually being removed from the target — see the note at the top. There is no
  other analytics pipeline.
- **Customer Support.** Support is a plain `mailto:` to efe@cloudgeng.com; there
  is no in-app support form collecting data. In-app *content reports* are covered
  under User Content above.

---

## Also relevant to the questionnaire

- **Push notifications:** none. The app has no notification code and the
  `aps-environment` entitlement has been removed from
  `FitScore/Fitrater.entitlements`.
- **Purpose strings present in `Info.plist`:** camera, photo library read, photo
  library add. No others. If a reviewer asks why a permission is requested, those
  three strings are the answer and each corresponds to a real flow.

---

## Third-Party SDKs — data handling

| SDK | Data it handles | Purpose | Retention |
|---|---|---|---|
| Supabase | Email, user id, photos, all user content | App Functionality | Until account deletion |
| RevenueCat | User id, device id, purchase history | App Functionality | Per RevenueCat's retention policy |
| Google Sign-In | Email, Google account id (only if the user chooses Google) | App Functionality | Until account deletion |
| Firebase Crashlytics (Google) | Device model, OS version, app version, crash stack trace — not linked to an account | App Functionality (Diagnostics → Crash Data) | Per Firebase's retention policy |
| Fal AI (called server-side) | Photos submitted for a render | App Functionality | Transient; not retained for training per Fal's terms |

Supabase, RevenueCat, Google Sign-In and Firebase all ship privacy manifests.
Xcode bundles them into the app's privacy report automatically. If Xcode warns at
archive time about a missing privacy manifest, bump that package rather than
suppressing the warning — Apple checks the aggregate report.

---

## Data deletion — confirmed present

Apple requires in-app account deletion for any app with sign-in. It exists:
**You tab → Help & privacy → Delete my account → type DELETE → confirm**
(`FitScore/You/HelpPrivacySheet.swift:76` and the confirmation alert at
`:99-111`).

It is a real hard delete performed server-side by
`supabase/functions/delete-account/index.ts`, invoked from
`FitScore/Data/Repo.swift:1325-1357`: every user-owned row across the
application tables, every object under the user's prefix in all five storage
buckets, and finally the auth user itself. No retention window, no soft delete.
The same screen also offers "Export my data".

**Before submitting, confirm the `delete-account` function is deployed.** The
code path throws and shows "Delete failed" if the function is missing, which a
reviewer would read as a broken deletion flow.
