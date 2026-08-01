# App Review Notes — Fitrater

Paste this into ASC → Version → App Review Information → Notes.

---

## What Fitrater is

Fitrater is an editorial styling workspace. It reads the outfit a user photographs and returns a numeric score plus a short critique in the voice of an in-app critic named Hem. Beyond scoring, users build a wardrobe (Studio), render garments on their own body (Try-on), and stage looks as magazine covers (Compose Cover). It is not a social network, has no public feed, and no user-generated content is shared between accounts.

## Demo account

- Email: `applereview+demo@fitrater.ai`
- Password: (fill in before submission)
- This account has the `fitscore_pro` entitlement granted server-side, so reviewers can test all paid features (unlimited scoring, Try-on, Compose Cover, brutal-mode critiques) without a real purchase.
- Credit balance is pre-loaded with 500 credits so Try-on / Cover renders succeed immediately.

To create this account:
1. Sign up in the app with the email above.
2. In Supabase Studio → Table Editor → `profiles` row for this user → set `is_pro = true`, `credits = 500`.
3. In RevenueCat Dashboard → Customers → search the Supabase user id → grant `fitscore_pro` entitlement with expiry set to 5 years out.

## Feature walkthrough (2 minutes)

1. **Sign in** with the demo account. Land on **Home** — see the "Rate it" card, latest score, weekly Wrapped.
2. **Home → Rate it** → snap or choose any photo of an outfit → Score screen shows the numeric read and Hem's note.
3. **Studio tab** → tap **+** → add a garment photo → it's saved to the closet grid.
4. Tap any Studio piece → **Try on** → renders the garment on the user's calibrated body (calibration photo is pre-set on the demo account).
5. Any score or Try-on → **Compose Cover** → pick a template → magazine cover is composed with typography.
6. **Journal tab** → timeline of every score, Try-on, and cover.
7. **You tab** → Style DNA, settings, Delete Account.

## In-App Purchases

Six IAPs are configured. All map through RevenueCat (public SDK key `appl_CzEAtrNVYSafdHDIXAFTUOgIgAU`):

**Subscriptions (auto-renewable, one group: Fitrater Pro)**
- `fitrater_pro_weekly` — $6.99/wk — 7-day free trial
- `fitrater_pro_monthly` — $14.99/mo — 3-day free trial
- `fitrater_pro_annual` — $79.99/yr — 7-day free trial

**Consumable credit packs**
- `fitrater_pro_credits_100` — $2.99 → 100 credits
- `fitrater_pro_credits_500` — $9.99 → 400 credits (SKU says 500, grants 400; historical naming from an earlier bundle size)
- `fitrater_pro_credits_1500` — $29.99 → 1200 credits

Restore Purchases is on the paywall and in Settings.

## AI pipeline — data flow

Fitrater uses **Fal AI** as its rendering vendor. Fal routes to different underlying models depending on task:
- Scoring critique: text model (currently Google Gemini via Fal).
- Try-on: FASHN virtual try-on model.
- Compose Cover: nano-banana image composition.

Data flow: photo is uploaded from device → Supabase Storage (private bucket, per-user RLS) → an authenticated request from our backend calls Fal with a signed URL → Fal returns rendered image → written back to Supabase → shown to user. **Photos are not retained by Fal for model training** (per Fal's ToS). Users can delete any photo, any generation, and their entire account from the You tab.

## Account deletion

Available in-app: **You tab → Settings → Delete Account**. Deletes the auth user, all photos in Storage, and cascades all Postgres rows. No server-side retention.

## Age rating rationale

Rated 12+. The app accepts photos of the user's fully-clothed body for Try-on calibration and does not permit nudity (a moderation pass rejects such uploads). No user-to-user contact, no location, no gambling, no violent or mature content.

## Contact

Efe Simsek — efe@cloudgeng.com — (fill in a real phone number that will be answered).

## Anything unusual to note

- The app opens straight into an onboarding flow on first launch. Reviewers should complete the short questionnaire (5 screens) before landing on Home. The demo account has onboarding already completed, so this only matters if the reviewer creates a fresh account.
- Compose Cover generates typography that includes editorial headline text. This text is AI-generated in-app and is not sourced from any copyrighted publication.
