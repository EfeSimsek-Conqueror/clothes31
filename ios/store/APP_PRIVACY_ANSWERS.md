# App Privacy Questionnaire — Answers

Fill this in at ASC → App Privacy → Edit.

Fitrater does NOT use any tracking SDKs (verified: no Amplitude, Mixpanel, PostHog, Firebase, Segment, or ad SDK in the codebase). No IDFA usage. All persistence goes to first-party Supabase; AI rendering goes to Fal AI; purchases go through RevenueCat.

**Global answer: "Yes, we collect data from this app."**

---

## Data Types Collected

For each type below, mark it collected and use the columns to answer the sub-questions.

### 1. Contact Info → Email Address
- **Collected:** Yes
- **Linked to user:** Yes
- **Used for tracking:** No
- **Purposes:** App Functionality (account creation, sign-in)
- **Where:** Supabase Auth
- **Optional?** No — required to create an account

### 2. User Content → Photos or Videos
- **Collected:** Yes
- **Linked to user:** Yes
- **Used for tracking:** No
- **Purposes:** App Functionality (outfit scoring, Studio wardrobe, Try-on calibration, Compose Cover rendering), Personalization (Style DNA)
- **Where:** Supabase Storage (primary), Fal AI (transient — sent for the duration of the render request; not retained for training per Fal's terms)

### 3. User Content → Other User Content
- **Collected:** Yes
- **Linked to user:** Yes
- **Used for tracking:** No
- **Purposes:** App Functionality, Personalization
- **What:** style preferences, outfit tags, saved Looks, Journal entries, weekly Wrapped state
- **Where:** Supabase Postgres

### 4. Identifiers → User ID
- **Collected:** Yes
- **Linked to user:** Yes
- **Used for tracking:** No
- **Purposes:** App Functionality
- **What:** Supabase user UUID (server-generated). Passed to RevenueCat as the App User ID for entitlement lookup.
- **Where:** Supabase, RevenueCat

### 5. Identifiers → Device ID
- **Collected:** Yes
- **Linked to user:** Yes
- **Used for tracking:** No
- **Purposes:** App Functionality (fraud prevention on purchases; RevenueCat uses vendor ID as fallback identifier)
- **Where:** RevenueCat

### 6. Purchases → Purchase History
- **Collected:** Yes
- **Linked to user:** Yes
- **Used for tracking:** No
- **Purposes:** App Functionality (unlock Pro entitlement, restore purchases)
- **Where:** RevenueCat, mirrored to Supabase via RC webhook

### 7. Diagnostics → Crash Data
- **Collected:** Yes (Apple's built-in TestFlight/App Store crash collection only — user opts in at OS level)
- **Linked to user:** No
- **Used for tracking:** No
- **Purposes:** App Functionality

---

## Data NOT Collected (do NOT check these)

- Health & Fitness (no HealthKit)
- Financial Info (all payment handled by Apple; no card data touches the app)
- Location (no CLLocationManager usage)
- Contacts (no Contacts framework)
- Search History
- Browsing History
- Sensitive Info
- Audio Data
- Gameplay Content
- Customer Support (unless a support form is added later)
- Advertising Data
- Product Interaction / Advertising Data / Other Usage Data — the app does not send analytics events to any third party. (If an analytics SDK is added later, revise this section.)

---

## Third-Party SDKs — Data Handling

Apple asks you to disclose SDK-collected data too. Check each vendor's own privacy manifest and align:

| SDK | Data used | Purpose | Retention |
|---|---|---|---|
| Supabase (self-hosted-flavor SDK) | Email, user id, photos, user content | App Functionality | Until account deletion |
| RevenueCat | User id, device id, purchase history | App Functionality | Per RC's retention policy |
| Fal AI (called via HTTPS from client / server) | Photos submitted for render | App Functionality | Transient — per Fal ToS, not retained for training |

Supabase and RevenueCat both ship SDK privacy manifests as of iOS 17; Xcode will bundle them automatically. If Xcode warns at Archive time about "missing privacy manifest", that vendor needs updating — check the SDK version and bump.

---

## Data Deletion

- [ ] Confirm the app exposes account deletion in Settings → You → Delete Account. Apple **requires** this for any app with sign-in as of iOS 15+. If not present, this will be rejected. Check `YouView.swift` and Settings.
