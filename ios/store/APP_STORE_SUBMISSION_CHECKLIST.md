# App Store Submission — Checklist

Bundle `com.fitrater.app` · ASC app id `6794940458` · **Version 1.0, build 16**

Checked against the source tree on 2026-09-01. Work top to bottom.

`[x]` means verified in the repo today. `[ ]` means a human still has to do it —
these cannot be done from the codebase.

---

## Phase 0 — Blockers that must clear before anything else

These are the items that will send the submission back if skipped. None of them
can be done by editing code.

- [ ] **Create the demo account.** In-app sign-up by email is magic-link only
      (`FitScore/Auth/EmailAuth.swift:11`), but the sign-in screen does offer a
      password path (`FitScore/SignIn/SignInView.swift:104` →
      `EmailAuth.signInWithPassword`). So: create the user in Supabase Studio →
      Authentication → Users, set a password there, then in the `profiles` row
      set `is_pro = true`, `onboarded = true`, and a healthy `credits` balance.
      Sign in once on a device to confirm the password path works and that
      onboarding is genuinely skipped. Then put the real credentials into
      `REVIEW_NOTES.md` in place of the `FILL_IN` markers.
- [ ] **Put a real phone number in `REVIEW_NOTES.md`.** Second `FILL_IN`.
- [ ] **Deploy the edge functions.** `supabase/functions/delete-account` is new
      and the app now hard-fails deletion if it is absent —
      account deletion would visibly break in review. `compose-cover` and
      `create-cover-template` also changed (text screening, safety setting) and
      the client maps a new `text_rejected` error from them.
- [ ] **Push migrations `0005_stylist_memory.sql` and `0006_content_reports.sql`.**
      Content reporting writes to `public.content_reports`; without the table the
      new report sheets fail.
- [ ] **Deploy the website.** `https://fitrater.ai/support` is a new route
      (`web/app/support/page.tsx`) and does not exist in production yet. A 404
      support URL is a guideline 1.5 rejection on its own. Load /support,
      /privacy and /terms in a browser before submitting.
- [ ] **Deploy the narrowed `apple-app-site-association`** and give Apple's CDN
      time to propagate. The app now claims only `/auth/callback` paths, so
      /privacy, /terms and /support open in the browser as intended — but the old
      cached file claims everything and would still hijack those links.
- [ ] **Xcode target changes** (owned by whoever edits `project.pbxproj`):
      - Add `FitScore/Util/ReportContentSheet.swift` to the app target. It is
        **not** currently in the project file, and three views reference it —
        the build will fail without it.
      - Remove the `FirebaseAnalytics` package product from the app target's
        Frameworks phase. Keep `FirebaseCrashlytics` and `FirebaseCore`. The App
        Privacy answers are written assuming Analytics is gone.
      - Optional cleanup: `FitScore/Camera/RoastView.swift` is still compiled but
        has no entry point anywhere in the app — dead code. Not a rejection risk
        on its own. (Other unreachable "brutal" strings also remain compiled:
        the unrendered `honestySection` in `You/YouSheet.swift:176` and the
        `.brutal` case in `Data/FeatureGates.swift`. None is reachable from the
        UI. "Brutalist" in the cover wizard is a legitimate design term and
        should stay.)
- [ ] **Configure the Sign in with Apple revocation secrets.** Token revocation
      is implemented in `delete-account` but is inert until
      `APPLE_SIWA_PRIVATE_KEY`, `APPLE_KEY_ID`, `APPLE_TEAM_ID` and
      `APPLE_CLIENT_ID` are set as function secrets. Account deletion still works
      without them — the auth user is deleted either way — but Apple expects the
      token to be revoked for apps offering Sign in with Apple. Generate the .p8
      in the Apple Developer portal and set the secrets.
- [ ] **Re-shoot all six screenshots from build 16.** See Phase 4.

## Phase 1 — App Store Connect app record

- [x] The app record exists (id `6794940458`, version 1.0 already created).
- [ ] Confirm the version is in an editable state. If a previous submission is
      still attached, delete the review submission first — screenshots and
      metadata cannot be edited while a version is WAITING_FOR_REVIEW.

## Phase 2 — Build upload

- [x] Version numbers come from build settings only. `MARKETING_VERSION = 1.0`
      and `CURRENT_PROJECT_VERSION = 16` in `project.pbxproj`; `Info.plist`
      references `$(MARKETING_VERSION)` and `$(CURRENT_PROJECT_VERSION)`.

      **Do not edit `CFBundleVersion` or `CFBundleShortVersionString` in
      `Info.plist`.** Hardcoding them there is exactly what caused every archive
      to ship build 4 regardless of the project setting. To bump the build,
      change `CURRENT_PROJECT_VERSION` in **both** the Debug and Release
      configurations of `project.pbxproj`.
- [x] `ITSAppUsesNonExemptEncryption` is already `false` in `Info.plist:54`, so
      the export-compliance question is answered in the binary and ASC will not
      prompt.
- [x] `ios/store/submit.sh` now asserts after archiving that the archived
      `CFBundleVersion` matches what `xcodebuild` reports, and fails the script
      if they disagree.
- [ ] Archive and upload. Use `ios/store/submit.sh`, or Xcode → Product →
      Archive → Distribute App → App Store Connect.
- [ ] Wait for processing, then confirm in ASC → TestFlight that the build shown
      is **16**. If it says anything else, stop and investigate before attaching.

## Phase 3 — App Store metadata

- [ ] Paste from `APP_STORE_METADATA.md`: subtitle, promotional text,
      description, keywords, support URL, marketing URL, copyright.
- [ ] Primary category **Lifestyle**, secondary **Photo & Video**.
- [x] The keyword list no longer contains `vogue`; the description no longer
      mentions brutal mode, roasts or weather.

## Phase 4 — Screenshots + icon

- [ ] **Re-shoot all six from build 16.** The current set predates Stylist Chat,
      the Studio 2.0 outfit wizard, the likeness-consent screen and the removal
      of the Home weather card, so several shots no longer match the app. A
      screenshot that shows a screen the reviewer cannot find is a 2.3.3
      rejection.
- [ ] **Delete `screenshots/2_studio.png` and do not reuse it.** For accuracy:
      it does *not* reproduce a real publication's masthead — the masthead in the
      shot reads FITRATER. What it does contain is AI-generated cover lines
      naming real living public figures ("PAUL MESCA…", "CHIMAMANDA NGOZI
      ADICHIE") on a fabricated magazine cover, which should not appear in store
      artwork. It is also labelled "CREATE YOUR COVER" while sitting in the
      Studio slot. Replace it.
- [ ] Check the remaining five for the same problem — no real person's name and
      no publication name burned into a generated cover.
- [ ] Upload the new six in order. Apple auto-scales 6.9″ shots down for 6.7″ and
      6.5″; you do not need to upload smaller sizes. Note from
      `SESSION_2026-08-03.md` §7.7 that the 6.5″ and 6.7″ slots currently hold
      *different* sets — replace both, or delete one slot, so a reviewer does not
      see two different vintages of the app.
- [ ] iPad screenshots: skip, the app is iPhone-only.
- [x] App icon ships in the build at
      `FitScore/Assets.xcassets/AppIcon.appiconset/icon-1024.png`; the marketing
      icon is pulled from the archive.

## Phase 5 — In-App Purchases

- [ ] Work through `APP_STORE_IN_APP_PURCHASES.md`. **The product IDs are
      `fitrater_v2_*`, not `fitrater_pro_*`** — an older draft of that document
      had this wrong.
- [ ] Fill in the two prices and the introductory offers marked
      "verify in ASC" in that document, from the live records.
- [ ] Confirm each credit pack's display name states the real grant
      (150 / 500 / 1,200 / 3,000) and not the number in the product ID.
- [ ] Every product must reach READY_TO_SUBMIT and be attached to version 1.0.
      `SESSION_2026-08-03.md` §7.1 records that the ASC API refuses to add IAPs
      to a review submission — do it in the ASC UI and confirm visually, or the
      app ships approved with unapproved IAPs and the paywall errors.

## Phase 6 — RevenueCat

- [ ] Follow `REVENUECAT_SETUP.md`.
- [x] The key in the binary is `appl_XcRnJzmGqEityyPyHuMWRdaSWvV`
      (`FitScore/Data/SupaClient.swift:30`) — the correct Fitrater project key.
      The old `appl_CzEAtrN…` key belonged to a different project and is gone.
- [ ] Confirm the `default` offering contains all four credit packs and all three
      subscriptions, and that each is attached to the `fitscore_pro` entitlement.
- [ ] Run one sandbox purchase of a subscription and one of a credit pack. Verify
      the credit balance moves by the exact amount in the grant table.
- [ ] Verify **Restore purchases** works from both places it appears: the paywall
      and You → Manage Fitrater Pro.

## Phase 7 — Age rating

- [ ] Re-run the questionnaire from scratch. Apple retired the 4+/9+/12+/17+
      tiers; **"12+" is no longer selectable**, and the questionnaire now asks
      about AI separately. Answer per the "Age Rating" section of
      `APP_STORE_METADATA.md`: **yes** to an AI chatbot (Chat with Hem), **yes**
      to AI-generated content, **no** to user-generated content shared with
      others. The Terms set a 16 minimum, so do not accept a tier below that
      without changing the Terms.

## Phase 8 — App Privacy

- [ ] Paste from `APP_PRIVACY_ANSWERS.md`.
- [ ] **The Crash Data answer depends on Phase 0.** It declares Diagnostics →
      Crash Data collected by Google (Firebase Crashlytics), not linked to the
      user, and declares no analytics. That is only true once FirebaseAnalytics
      is out of the target. Confirm before saving.
- [ ] Do **not** declare Location. There is no location code in the iOS build and
      the published Privacy Policy now says so explicitly.

## Phase 9 — Pricing

- [ ] Price: **Free** (paid tiers are IAPs).
- [ ] Availability: all territories, or restrict deliberately.
- [ ] Pre-orders: off.

## Phase 10 — Review notes + demo account

- [ ] Paste `REVIEW_NOTES.md` into App Review Information → Notes.
      **Search the pasted text for `FILL_IN` first.** If any remains, the demo
      credentials or the phone number are still missing.
- [ ] Sign-in required: **Yes**.
- [ ] Demo account: the credentials from Phase 0.
- [ ] Contact: Efe Simsek — efe@cloudgeng.com — real phone number.
- [ ] Support email of record everywhere is **efe@cloudgeng.com**. The old
      `support@fitrater.com` address has no mailbox and no longer appears
      anywhere in the app or on the website. Do not enter it in ASC.

## Phase 11 — Submit

- [ ] Advertising Identifier (IDFA): **No**. Verified — no `AdSupport`, no
      `ATTrackingManager` anywhere in the iOS tree.
- [ ] Release: **manually release this version**.
- [ ] Add for Review → Submit.

---

## Pre-flight smoke test on the demo account

Do this on a real device with build 16 before submitting. Each line is a claim
made in the review notes; if one fails, the notes are wrong.

- [ ] Sign in with the demo email and password. Land on Home, not onboarding.
- [ ] Sign-in screen footer opens the Terms and the Privacy Policy.
- [ ] Camera button → Score a look → completes and shows a score.
- [ ] Score detail → "Report this critique" → submits and toasts.
- [ ] Camera button → Chat with Hem → get a real reply. Kill the network and send
      again: expect the "Couldn't reach Hem" banner with Retry, and **no**
      invented assistant message.
- [ ] Long-press an assistant reply → "Report this reply" → submits.
- [ ] Camera button → Try on → opens directly (demo account is Pro), not the
      paywall.
- [ ] Camera button → Compose a cover → the "Whose face is this?" consent screen
      appears on first use and blocks generation until accepted. Then a cover
      renders, and "Report this cover" submits.
- [ ] Type profanity into a cover masthead or headline → the request is refused
      before any image is generated, with a readable message.
- [ ] Studio tab → **+** → the single-piece / outfit-wizard gateway appears.
- [ ] You → Buy credits → prices render (not "—"), Restore purchases works.
- [ ] You → Manage Fitrater Pro → shows the subscription state, not a perpetual
      upsell, and has a Restore purchases row.
- [ ] You → Rate on the App Store → opens the App Store write-review page and
      grants nothing.
- [ ] Home shows no weather card.
- [ ] On a throwaway account: You → Help & privacy → Delete my account → type
      DELETE. Expect "Account deleted", then confirm in Supabase that the auth
      user is gone and that signing in with the same identity creates a *new*
      empty account.

## Wait times

- First submission: often 2–7 days. Subsequent: 24–48 hours.
- Metadata rejections (dead support URL, missing demo account, screenshot
  mismatch) add a 24–48h round trip each. Phase 0 exists to avoid them.
