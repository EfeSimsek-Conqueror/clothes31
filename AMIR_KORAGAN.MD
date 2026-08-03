# AMIR_KORAGAN.MD

Handoff doc for Amir Koragan. Reads top to bottom, no prior context needed.

The first section is a punch-list of what the app still needs so you can start
planning tomorrow morning. Everything after is background on what already
exists and how the pieces fit.

---

## 0. Punch-list — what to build next (priority order)

Ordered by "if we don't do this, we lose money / users / legal standing / the
launch." Each item has enough detail to scope a ticket.

### P0 — pre-launch blockers

1. **Localization (i18n)** — the entire UI is hardcoded English in both iOS
   Swift and Android Kotlin. Turkey is our biggest early market (Efe is Turkish;
   TR pricing is what he stress-tests against) but every single string is
   `Text("Score a look")` etc. What needs to happen:
   - iOS: extract to `Localizable.strings`, replace with `String(localized:)`.
     Add `tr.lproj/Localizable.strings` as the second language. Set base
     localization to English.
   - Android: extract to `res/values/strings.xml` (default) and
     `res/values-tr/strings.xml`. Replace with `stringResource(R.string.xxx)`.
   - Backend: edge functions that return prose (hem-service, occasion-coach,
     invitation-decoder, sunday letter generator) need a `locale` param and
     must instruct the LLM to respond in that language.
   - Launch languages: EN, TR. Fast follow: ES, PT-BR, FR, DE, JA.
   - Est. 3-5 days for extraction + 1 day for TR translation + 1-2 days per
     additional language of proofreading.

2. **Accessibility pass** — TalkBack/VoiceOver labels are missing on most
   interactive elements. Apple and Google both check this at review time now.
   - Add `contentDescription` to every `Icon(...)` (many are `null` or
     placeholders on Android).
   - iOS: add `.accessibilityLabel(...)` to buttons that use icons only.
   - Dynamic type: verify all `Serif.body(size)` respects the user's text
     scale setting (there's a `text_scale` preference stored but not fully
     wired).
   - Color contrast: audit `Palette.muted` on `Palette.paper` (currently borderline
     WCAG AA fail on some devices).
   - Est. 2-3 days.

3. **Privacy compliance surface**
   - **Play Data Safety form** — must be filled before v1 goes live. We
     collect: user profile, style tags, uploaded photos, purchase history,
     analytics. All server-side. Encrypted in transit + at rest via Supabase.
     Not shared with third parties except AI providers (OpenAI, Fal, etc).
   - **App Store privacy nutrition labels** — same shape.
   - **KVKK (Turkey)** — Efe is TR-based, may attract TR regulators. Need TR
     translation of privacy policy + consent language in the app for TR users.
   - **GDPR / CCPA** — Repo already has `exportUserData()` and
     `deleteAllUserData()` on both platforms. Surface these in Settings as
     "Export my data" / "Delete my account" buttons. Currently no UI entry.
   - Est. 2 days.

4. **Onboarding entry points for the 6 features we just ported to Android**
   (see "Recent session's changes" section for what was ported). Right now
   the Kotlin screens exist and the nav routes are wired, but nothing in the
   UI navigates to some of them:
   - **Body Calibration** — needs an insertion point in `OnboardScreens.kt`
     as the last step (currently onboarding stops before it).
   - **Style DNA** — needs a button in `YouSheet.kt` under the profile block.
   - **Invitation Decoder** — needs a `CameraMenuSheet` accordion (only
     "Compose a cover" is there so far; Invitation Decoder + Occasion Coach
     are missing from the menu).
   - **Occasion Coach** — same as above.
   - iOS already has all of these wired.
   - Est. half a day for all four insertion points.

5. **Real Compose-Cover camera capture verified end-to-end on Android**
   — I wired the Camera chip to navigate to `Route.Camera` and re-consume
   `CameraBus` on return. Efe hasn't verified the round-trip visually yet.
   If bytes come back but the sheet's other state (mood, masthead, prompt)
   resets, wrap those in `rememberSaveable`. Est. half a day.

6. **Play Store data-safety declaration + Advertising ID = YES**. Firebase
   Analytics ships `AD_ID` permission whether you like it or not. See "Deploy
   & release" section for the specific answers.

### P1 — first month post-launch

7. **Deep-link + share** — iOS has `WrappedCardRenderer` that produces a
   1080×1920 PNG of the Style DNA card. Android doesn't. Port it. Then wire
   share-sheet on both to include an app-store link.
8. **Referral program** — "Invite a friend, both get 100 credits."
   New Supabase table `referrals(id, inviter_uid, invitee_uid, granted_at)`
   with an edge function that grants on account creation if invited.
9. **Feature flags** — introduce Firebase RemoteConfig. Wrap paywall pricing,
   credit costs, sub caps, "which features are visible in Camera menu" behind
   flags so we can A/B without a new build.
10. **Push notifications** — Android has `PushSettings` model + toggles in
    the UI, but nothing actually schedules or sends pushes. Wire FCM + server-
    side scheduling for morning stylist, weekly wrap, Sunday letter.
11. **In-app rating prompt** — after a positive event (score ≥ 8, third
    cover composed, etc). Use Google Play In-App Review API + Apple
    `StoreKit.SKStoreReviewController`. Cap to once per 90 days.

### P2 — quarter one

12. **Content moderation on AI outputs** — the compose-cover and try-on
    functions occasionally return "content_flagged" errors. We handle them
    but don't proactively review outputs. Add a lightweight NSFW / face-
    ID sanity check on generated images before they land in the user's
    Journal.
13. **Rate limiting on edge functions** — right now anyone with a valid
    session can pound our AI provider budget. Add per-user daily / hourly
    caps at the edge-function level (roast has one; nothing else does).
14. **CDN in front of Supabase Storage** — for signed-url generation
    latency and to cushion bandwidth costs. Cloudflare works.
15. **Unit + snapshot test infrastructure** — both apps have zero tests.
    iOS: XCTest + SnapshotTesting for the score cards / cover previews.
    Android: JUnit + Compose UI tests for the paywall + score flow.
    GitHub Actions on every PR.

### P3 — nice to have

16. **Admin / support console** — a lightweight Next.js dashboard on
    Vercel that reads the Supabase project. Ability to view a user's
    outfits / credits, issue a refund, revoke Pro status.
17. **In-app support inbox** — "Contact support" form that lands in a
    Linear/Intercom.
18. **Onboarding checklist** — after signup show "Add your first piece",
    "Score your first fit", "Try a cover" checkmarks in Home.
19. **Turkish AI voice** for hem-service — currently the tone is written
    in English editorial voice; the TR translation should preserve that
    editorial register, not become a literal translation.

---

## 1. What Fitrater is

Fashion / personal style app. Marketing pitch: *"A private style desk. Rate an
outfit, build a wardrobe, try garments on your own body, and see any look
staged like a magazine cover — all in one editorial workspace."*

Core loops:

1. **Score a fit** — user photographs an outfit, an LLM ("Hem", the app's
   in-house critic voice) returns a numeric score plus a short editorial
   note. Costs credits.
2. **Studio** — the closet. User uploads / imports pieces (or generates
   them with a text prompt). Pieces are categorized and can be re-used in
   Try-on, A-vs-B, Cover, etc.
3. **Try-on** — LLM-driven image composite: user photo + a Studio piece →
   a rendered try-on. Costs more credits.
4. **Cover** — magazine-cover generator. Two variants:
   a. *Design a cover* (Studio) — 8-step wizard that produces a typography-
      only template (no subject photo).
   b. *Compose a cover* (Camera) — real face-swap of the user onto a
      cover template. Uses Fal + nano-banana pipeline.
5. **Sub-features**: Roast (brutal-mode rating), A vs B (two outfits, one
   winner), Decode style (analyze any reference photo), Occasion Coach
   (input an occasion, get 3 combos from the closet), Invitation Decoder
   (photograph an invite → OCR dress code → suggest combos), Sunday Letter
   (weekly digest from Hem).

Voice: intentionally editorial, dry, one-liners, references to magazine
culture (Vogue, Dazed, Kinfolk, SI, etc). Colors: cream (`#F3EEE4`) paper,
ink text, bronze (`#B4813E`) accent. Serif display type + inter body.

---

## 2. Tech stack

| Layer | Tech |
|---|---|
| iOS | SwiftUI, XCFramework | 
| Android | Kotlin + Jetpack Compose (Material3) |
| Backend | Supabase (Postgres, Auth via Google + Apple + email, Storage, Edge Functions in Deno) |
| AI providers | OpenAI (LLM), Fal (image gen + face-swap), Nano-banana (rendering) |
| Auth | Supabase Auth + Google Sign-in + Sign in with Apple |
| Payments | RevenueCat (iOS + Android) → StoreKit / Play Billing |
| Analytics + crash | Firebase Analytics + Crashlytics (both platforms) |
| Image loading | Coil (Android), Kingfisher (iOS) |
| Design | Playfair Display (serif), Inter (sans) — Google Fonts |

---

## 3. Repo layout

```
clothes31/
├── ios/
│   ├── FitScore/                    # main iOS project
│   │   ├── FitScore.xcodeproj
│   │   └── FitScore/                # SwiftUI source
│   │       ├── App/                 # session store, main tab view
│   │       ├── Data/                # Repo.swift, Models.swift, RcBilling, StyleDna, HemService
│   │       ├── Home/                # HomeView + cards
│   │       ├── Studio/              # StudioView, CoverTemplateCreatorView
│   │       ├── Camera/              # ScoreSheet, TryOn, Versus, Roast, Decode,
│   │       │                        #   MagazineCoverSheet, EditCoverSheet,
│   │       │                        #   OccasionCoachView, InvitationDecoderView
│   │       ├── Score/               # ScoreDetailView, ScoreAnnotationView (markup)
│   │       ├── Journal/             # JournalView
│   │       ├── You/                 # YouSheet, StyleDnaView, ManageProView, etc.
│   │       ├── Onboarding/          # OnboardingView, BodyCalibrationView,
│   │       │                        #   BodyProfileRevealView
│   │       └── Util/                # ToastBus, CreditsBus, WaitingOverlay, Haptic, etc.
│   └── store/                       # ASC submission scripts, ExportOptions.plist
│
├── android/
│   ├── app/src/main/java/com/fitrater/app/
│   │   ├── data/
│   │   │   ├── SupabaseClient.kt    # Supa singleton, cost + credit constants, RC keys
│   │   │   ├── billing/RcBilling.kt
│   │   │   ├── model/Models.kt      # all shared data classes / serialization
│   │   │   ├── repo/Repo.kt         # single-file backend gateway (huge; keep grepping)
│   │   │   ├── auth/, weather/, service/
│   │   ├── ui/
│   │   │   ├── theme/DesignSystem.kt  # HemColors, HemSpace, HemType, PrimaryButton...
│   │   │   ├── components/            # shared Composables (PhotoTile, Shimmer, ...)
│   │   │   └── screens/
│   │   │       ├── HomeScreen, StudioScreen, JournalScreen, YouScreen, YouSheet
│   │   │       ├── CameraCaptureScreen, ScoreSheetScreen, ScoreDetailScreen
│   │   │       ├── StudioCreateScreen, PieceDetailSheet
│   │   │       ├── PaywallScreen (onboarding), CreditsSheet lives inside YouSheet
│   │   │       ├── camera/           # CameraMenuSheet, TryOnScreen, VersusScreen,
│   │   │       │                     #   RoastScreen, DecodeScreen, InvitationDecoderScreen,
│   │   │       │                     #   MagazineCoverSheet, OccasionCoachScreen
│   │   │       ├── onboarding/       # BodyCalibrationScreen + BodyProfileRevealScreen
│   │   │       ├── studio/           # CoverTemplateCreatorScreen
│   │   │       └── subpages/         # WeeklyLetterScreen, ManageProScreen, FaqScreen,
│   │   │                             #   StyleDnaScreen
│   │   ├── nav/Nav.kt                # centralized route strings
│   │   ├── util/                     # CreditsBus, CameraBus, EditRequestBus, ToastBus,
│   │   │                             #   CreditsGate, Haptic
│   │   └── MainActivity.kt           # NavHost + sheet host
│   ├── build.gradle.kts              # dependencies, versionCode, versionName
│   └── store/                        # play store assets (privacy.html, terms.html, aab)
│
├── supabase/                         # DB migrations, seed data, edge functions
│   ├── functions/                    # one folder per edge function
│   └── migrations/
│
└── web/                              # marketing site, not the app
```

---

## 4. Currently working features (top-down)

**Home tab**
- Latest activity card, weekly wrapped teaser (Monday), Sunday Letter preview,
  Hem morning card, first-run banner, low-credits banner, pull-to-refresh.

**Studio tab**
- Closet grid (categorised: top/bottom/outerwear/dress/shoes/accessory).
- Import photo → auto-categorised piece; Create piece via prompt.
- Piece detail sheet with try-on count + edit.
- **"Design a cover"** button → Cover Template Creator wizard (Android just
  got this from the last session's port).

**Camera FAB → menu**
- Score a look (5 cr) · Try-on (20 cr, Pro-only) · A vs B (8 cr) · Roast
  (5 cr) · Decode style (10 cr) · Compose Cover (10 cr).
- iOS also has: Occasion Coach + Invitation Decoder in the menu.
- Android: those two exist as screens but not in the menu yet.

**Score sheet & detail**
- Take a photo, add occasion + weather, get score + per-piece breakdown +
  Hem's verdict. Save to Journal.
- Score detail overlays v1 subscore annotations + v3 markup annotations
  (arrows / lines / focus / swap) on top of the photo.
- iOS additionally shows a fit-tension heatmap; the model exists on Android
  but no UI yet.

**Try-on**
- YOU tile (user photo) + PIECE tile (closet or gallery upload) → LLM
  composite.

**Cover — 2 flows**
- Template Creator: 8-step wizard (genre → mood → masthead → headline →
  cover lines → pose → palette → review). Saves a *template* (no photo).
- Compose Cover: subject photo + optional reference cover + mood/masthead/
  prompt → face-swapped rendered cover. Templates from Studio show up in
  the "Studio" chip of the reference picker.

**Journal**
- Weekly outfit grid, month filter, weekly wrapped full view, PDF export.

**You / Profile**
- Avatar, display name, stats card, honesty setting, push toggles, credits.
- Style DNA view (iOS surfaces it via a button; Android has the screen but
  no button yet).
- Style Profile sheet (gender, vibe/style tags, sensitivities, palette
  preferences).
- Appearance (theme, text scale, reduce motion) — model wired, UI toggles
  live, actually applying them everywhere is spotty.

**Onboarding**
- Sign-in (Apple + Google + email) → gender select → vibe tags →
  Body Calibration (iOS only for now, Android has the screen ready).

**Paywall**
- Credit packs: Starter 150 / Popular 500 / Pro Pack 1200 / Mega 3000 at
  $1.99 / $6.99 / $14.99 / $39.99.
- Subscriptions: Weekly / Monthly (1,200 cr/mo) / Annual with 7-day trial
  (750 cr/mo × 12 = 9,000 cr/yr).
- Signup gift: 25 credits.

**Backend edge functions** (all deployed to `ilrzqifdmjvooeyqvexd.supabase.co`)
- `score-look` · `generate-piece` · `tryon-piece` · `versus` · `roast` ·
  `decode-style` · `analyze-body` · `transcribe-intent` · `compose-cover` ·
  `create-cover-template` · `regenerate-cover-headline` · `decode-invitation` ·
  `suggest-outfits` · `occasion-coach` · `sunday-letter`.

---

## 5. What changed in the recent session (2026-08-03)

- Payment plumbing overhaul (RevenueCat key fix, IAP substring-collision
  bug fix, safer credit-pack economics, sub description strings). Detailed
  session log: `ios/store/SESSION_2026-08-03.md`.
- iOS submitted to review as v1.0 build 11.
- Android port of six iOS features (~3,500 lines of Compose):
  1. Studio Cover Template Creator + Compose Cover + Edit Cover Sheet
     (`ui/screens/studio/CoverTemplateCreatorScreen.kt`,
     `ui/screens/camera/MagazineCoverSheet.kt`)
  2. Body Calibration + Reveal
     (`ui/screens/onboarding/BodyCalibrationScreen.kt`)
  3. Invitation Decoder
     (`ui/screens/camera/InvitationDecoderScreen.kt`)
  4. Occasion Coach (with weekly-free tracking, service, UI)
     (`ui/screens/camera/OccasionCoachScreen.kt`)
  5. Style DNA (deterministic builder + reveal screen)
     (`ui/screens/subpages/StyleDnaScreen.kt`)
  6. Score-detail markup annotation overlay (added to
     `ui/screens/ScoreDetailScreen.kt`)
- Repo additions on Android: `saveBodyProfile`, `loadBodyProfile`,
  `loadCoverTemplates` (fixed for NULL filter).
- Nav routes added: `CoverTemplate`, `ComposeCover?outfitId=`,
  `BodyCalibration`, `BodyProfileReveal`, `InvitationDecoder`,
  `OccasionCoach`, `StyleDna`.
- Working tree changes are **uncommitted**. Review + commit before pushing.

---

## 6. Domain glossary

Terms specific to this app you'll see in code and copy:

- **Hem** — the AI critic voice. Always calls the outfit, never the person.
  Ends with a fix / suggestion.
- **Studio** — the closet + piece-creation surface.
- **Piece** — one closet item (shirt, pant, dress, shoe, accessory,
  outerwear). Has category + subcategory + color.
- **Fit** — a full outfit shot (as opposed to a piece).
- **Score** — 0-10 numeric rating for a fit. Comes with subscores (color,
  fit, style_match, seasonal).
- **Markup** — v3 annotations layered on the score photo (arrow / line /
  focus circle / swap diamond).
- **Fit map** — a heatmap of tension zones (shoulders too tight, waist too
  loose, etc). iOS renders it; Android has the model, no UI yet.
- **Cover** — magazine-style visual composed from a subject photo.
- **Template** — a cover with no subject; typography + palette only. Saved
  in `magazine_covers` with `outfit_id IS NULL`.
- **Body Profile** — one-time calibration (body shape + skin undertone +
  coloring season + palette). Persists in `body_profiles`.
- **Style DNA** — deterministic monthly / all-time snapshot (avg score,
  best score, palette, observed archetype vs declared archetype, drift
  score).
- **Roast** — brutal-mode rating. Costs a bit more; capped to 3/day.
- **Sunday Letter** — Sunday-morning editorial digest of the user's week.
- **Wrapped Card** — 1080×1920 PNG snapshot of Style DNA, shareable.

---

## 7. Deploy & release

### iOS

Everything for App Store is scripted in `ios/store/`:

- `ios/store/submit.sh` — archives + exports + uploads. Uses ASC API key
  `AuthKey_BA97HB3JZJ.p8` (issuer `dbaebda1-f330-41c9-88e5-1b7c77f68829`).
- Version + build number in `ios/FitScore/FitScore.xcodeproj/project.pbxproj`
  (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`).
- Current state (2026-08-03): v1.0 build 11 in **WAITING_FOR_REVIEW**.

### Android

- Signing config uses `android/keystore.properties` +
  `android/keystore/fitrater-release.jks`. **Do NOT commit these** — they
  are gitignored.
- Bump `versionCode` + `versionName` in `android/app/build.gradle.kts`.
- Build: `./gradlew :app:bundleRelease` (needs JAVA_HOME pointing at
  Android Studio's JBR, e.g.
  `/Applications/Android Studio.app/Contents/jbr/Contents/Home`).
- Output: `android/app/build/outputs/bundle/release/app-release.aab`.
- Upload to Play Console → Production (or Internal → Closed → Open → Prod
  ladder for safer rollouts).

**Play Console gotchas**:

- **Advertising ID form** — answer **YES**. Reason: `firebase-analytics-ktx`
  auto-merges `com.google.android.gms.permission.AD_ID` into the manifest.
  Purpose to declare: **Analytics** only. Do not tick advertising /
  personalized advertising.
- **Data Safety** — collects: personal info (email + display name),
  photos, purchase history, app activity, crash data. All encrypted in
  transit, most encrypted at rest via Supabase. Users can request delete
  via in-app (needs UI surface — see P0 #3).
- Target SDK: 34+ (Play requires this in 2026).
- Keep the two AAB copies in `android/store/` for reference (`fitrater-
  1.0.0.aab`, `fitrater-1.0.1.aab`), and add each new release there.

---

## 8. Credentials + secret handling

Never commit any of the below. Rotate if leaked.

- Supabase project URL: `https://ilrzqifdmjvooeyqvexd.supabase.co` (public)
- Supabase anon key: in `SupaClient.swift` / `SupabaseClient.kt` (public,
  RLS-protected)
- RevenueCat iOS public key: `appl_XcRnJzmGqEityyPyHuMWRdaSWvV`
- RevenueCat Android public key: `goog_VwYVDpdRiqfJJKGOSLRwNuuwecv`
- RevenueCat secret (server) key: **do not put in-app**. Efe knows it;
  Amir, request separately.
- ASC API `.p8` key: `~/.appstoreconnect/private_keys/AuthKey_BA97HB3JZJ.p8`
  (local dev machine only, gitignored).
- Android release keystore: `android/keystore/fitrater-release.jks` +
  passwords in `android/keystore.properties`. Gitignored.
- Google Play service account JSON (if we set up automated Play uploads):
  not in repo yet; create in Play Console → API access.

---

## 9. RevenueCat + IAP product IDs

Same identifiers on both stores now (v2 naming):

| RC / store SKU | Grants | USA price |
|---|---|---|
| `fitrater_v2_credits_100` | 150 credits (Starter) | $1.99 |
| `fitrater_v2_credits_500` | 500 credits (Popular) | $6.99 |
| `fitrater_v2_credits_1500` | 1,200 credits (Pro Pack) | $14.99 |
| `fitrater_v2_credits_5000` | 3,000 credits (Mega) | $39.99 |
| `fitrater_v2_weekly` | Weekly subscription | ~$4.99 |
| `fitrater_v2_monthly` | Monthly sub — 1,200 cr/mo | $14.99 |
| `fitrater_v2_annual` | Annual sub — 750 cr/mo · 7-day trial | $99.99 |

The client-side grant table is in
`iOS/FitScore/FitScore/Data/SupaClient.swift` and
`android/.../data/SupabaseClient.kt`. **Grant map is the source of truth**
for how many credits a purchase adds — do not rely on RC / store
metadata.

---

## 10. Contacts

- **Efe** — founder, product, TR market lead. Contact: efe@cloudgeng.com.
  Speaks Turkish; the app UI is currently English-only (see P0 #1).
- **Amir Koragan** — you. Handoff target.

Working-tree changes from the recent session are uncommitted. Read the
session log at `ios/store/SESSION_2026-08-03.md` first for the full
before/after of pricing + IAP + submission plumbing, then start on the
punch-list in section 0.

Good luck.
