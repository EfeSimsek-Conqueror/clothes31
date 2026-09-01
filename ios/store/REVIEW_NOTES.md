# App Review Notes — Fitrater

Paste the block below into ASC → Version → App Review Information → Notes.

Everything in this file was checked against the source tree on 2026-09-01.
Version 1.0, build 16.

---

## ⚠️ REQUIRED BEFORE SUBMIT — do not paste this file until these are filled in

Three fields in this document are placeholders. Apple rejects on any one of them.

1. **Demo account password** (§Demo account). The account does not exist yet.
   Create it and paste the real password.
2. **Demo account email** — confirm the address you actually created.
3. **Contact phone number** (§Contact). Apple sometimes calls. A placeholder here
   is a metadata rejection.

Search this file for `FILL_IN` before pasting. If any `FILL_IN` remains, stop.

---

## What Fitrater is

Fitrater is a private styling workspace. A user photographs an outfit and gets a
numeric score plus a short written critique from an in-app AI critic called Hem.
Around that, users build a wardrobe (Studio), render garments onto a photo of
themselves (Try on), plan outfits for an occasion, chat with Hem, and stage a
look as a magazine cover (Compose a cover).

It is not a social network. There is no feed, no followers, no profiles, and no
content is shared between accounts. Everything a user creates is visible only to
that user.

Minimum age is 16 (stated in the Terms at https://fitrater.ai/terms).

## Demo account

- Email: `FILL_IN` (suggested: `applereview@fitrater.ai`)
- Password: `FILL_IN`
- The account is pre-granted Pro and pre-loaded with credits, so every paid
  feature can be exercised without a purchase.

Sign in on the first screen using **the email field, then "Use a password
instead"**. Apple sign-in and Google sign-in are also offered but are not needed
for the demo account.

Note for us, not for Apple: in-app *sign-up* by email is magic-link only, so the
demo account's password has to be set server-side in Supabase Auth. See the
submission checklist.

## Feature walkthrough

Times are approximate; the whole pass is about four minutes.

1. **Sign in.** The sign-in screen carries a footer linking to the Terms of Use
   and the Privacy Policy; both open in an in-app browser. Nothing is uploaded
   before this point.
2. **Onboarding.** A fresh account is asked two short questions (who you dress
   as, what you're going for) and then offered a body calibration photo. The demo
   account has already completed all of this and lands straight on Home.
3. **Home (TODAY tab).** Latest look, a weekly summary, and quick entries.
4. **The camera button** in the centre of the tab bar opens the feature menu.
   Every AI feature in the app is listed there with its credit cost. Tap a row to
   expand it, then GO:
   - **Score a look** — 5 credits. One photo of an outfit → score, per-piece
     notes, one-line verdict.
   - **Try on** — 20 credits, **Pro only**. A photo of yourself plus a garment →
     the garment rendered onto you. On a non-Pro account this row opens the
     paywall instead.
   - **A vs B** — 8 credits. Two photos, one winner.
   - **Chat with Hem** — free. A text chat with the stylist. See the note on
     failure handling below.
   - **Decode style** — 10 credits. A reference photo → the pieces, palette and
     a one-line style signature.
   - **Occasion Coach** — free once a week, then 15 credits. Describe an
     occasion, get three outfits assembled from your saved pieces.
   - **Compose a cover** — 10 credits. A photo → a magazine-style cover with a
     masthead, headline and cover lines. **The first time a reviewer runs this,
     a consent screen titled "Whose face is this?" appears and must be
     accepted** — see §Likeness consent.
   - **Decode the invite** — 6 credits. A photo of an invitation → the dress code
     read back plus three outfit combinations.
5. **STUDIO tab.** The saved wardrobe grid. **+** opens a gateway that offers a
   single generated piece or a multi-piece outfit wizard; the wizard walks
   through pieces one at a time and offers three alternatives per piece. Studio
   also holds "Design a cover", which builds a reusable cover template.
6. **JOURNAL tab.** A timeline of everything created, filterable.
7. **YOU tab.** Stats, Style profile, Body profile, Appearance, Manage Fitrater
   Pro (with Restore purchases), Help & privacy, and a "Rate on the App Store"
   row that simply opens the App Store — it grants nothing and unlocks nothing.

## In-App Purchases

All purchases run through RevenueCat. The iOS public SDK key compiled into the
build is `appl_XcRnJzmGqEityyPyHuMWRdaSWvV`
(`FitScore/Data/SupaClient.swift:30`). All tiers grant the same RevenueCat
entitlement, `fitscore_pro` (`FitScore/Data/RcBilling.swift:18`).

**Auto-renewable subscriptions** — one group, "Fitrater Pro":
`fitrater_v2_weekly`, `fitrater_v2_monthly`, `fitrater_v2_annual`.

**Consumable credit packs:**

| Product ID | Price (USD) | Credits granted |
|---|---|---|
| `fitrater_v2_credits_100` | $1.99 | 150 |
| `fitrater_v2_credits_500` | $6.99 | 500 |
| `fitrater_v2_credits_1500` | $14.99 | 1,200 |
| `fitrater_v2_credits_5000` | $39.99 | 3,000 |

The pack names are historical; the grant table
(`FitScore/Data/SupaClient.swift:86-91`) and the paywall tiles
(`FitScore/You/CreditsSheet.swift:33-37`) both use the numbers in the table
above, and the App Store display names state the real grant, so no user sees a
number that differs from what they receive.

Trial and renewal terms shown on the paywall are read from each product's
StoreKit introductory offer at runtime, so the screen always states whatever
offers are live in App Store Connect at the moment of review.

**Restore Purchases** appears in two places: on the paywall
(`FitScore/You/CreditsSheet.swift:327`) and under **You → Manage Fitrater Pro**
(`FitScore/You/subpages/ManageProView.swift:52`). Both call the same code path.

## AI pipeline — what leaves the device and where it goes

Fitrater uses **Fal AI** as its model gateway. Fal routes to different models per
task: a vision-language model for scoring and chat, a virtual try-on model for
Try on, and image-composition/editing models for Studio pieces and covers.

Flow: the photo is uploaded from the device to Supabase Storage (private buckets,
per-user row-level security) → a Supabase edge function, authenticated as the
signed-in user, sends a signed, short-lived URL to Fal → Fal returns the result →
it is written back to the user's own storage and shown. Photos are not retained
by Fal for model training, per Fal's terms.

Nothing is sent to any analytics or advertising service. The only third-party SDK
that receives anything beyond Supabase, RevenueCat and Fal is Firebase
Crashlytics, which receives crash stack traces not linked to an account.

## Content safety

These are the controls that actually exist in the shipping build. There is no
pre-upload nudity classifier, and this document does not claim one.

- **Likeness consent (Compose a cover).** Before the first cover is generated,
  the user must accept a screen titled "Whose face is this?" confirming the
  person in the photo is them or has agreed to appear, and that they will not use
  the result to impersonate anyone or imply an endorsement. Implemented at
  `FitScore/Camera/MagazineCoverSheet.swift:45,151,536`; the compose action is
  hard-blocked until it is accepted.
- **User-typed cover text is screened server-side.** Masthead, headline, pull
  quote and cover lines are run through a profanity and slur filter before any
  image is generated; a match returns an error and nothing is rendered
  (`supabase/functions/compose-cover/index.ts:1556-1586`, and the same screen in
  `supabase/functions/create-cover-template/index.ts`). This matters because
  covers are the one artefact designed to be shared outside the app.
- **Provider-side image safety.** The cover composition path runs the image model
  at its conservative safety setting (`compose-cover/index.ts:849`), and a
  provider safety rejection is surfaced to the user as "The photo you uploaded
  was flagged by our image model's safety filter. Try a photo with a shirt on."
  (`compose-cover/index.ts:1658, 1848`). A flagged photo produces no image.
- **Model instructions constrain the text.** The scoring model is instructed to
  critique the clothes and never the person, and only one tone ships — direct and
  constructive (`supabase/functions/score-outfit/index.ts:16-27`; the field is
  coerced server-side, so an older client cannot request a harsher tone). The
  chat model carries the same rule
  (`supabase/functions/stylist-chat/index.ts:26`). Body calibration is prompted
  as style measurement, not as any kind of assessment of the person
  (`supabase/functions/analyze-body/index.ts:78`).
- **In-app reporting of AI output.** Users can report any generated result:
  long-press an assistant reply in Chat with Hem, "Report this critique" at the
  foot of a score detail, "Report this cover" under a finished cover. Reports are
  written to a private table and reviewed by us; there is no public surface.

## Stylist Chat — expected failure behaviour

If the chat backend is unreachable, the app shows a "Couldn't reach Hem right
now" banner with a Retry button and adds **no** assistant message. It never
invents a reply. If a reviewer sees that banner, the network or the backend is
the cause, not a broken feature.

## Account deletion

**You tab → Help & privacy → Delete my account → type DELETE → confirm.**

Deletion is performed server-side by a dedicated function
(`supabase/functions/delete-account/index.ts`), invoked from
`FitScore/Data/Repo.swift:1325-1357`. It removes every row the user owns across
all application tables, recursively deletes their objects from all five storage
buckets, and finally deletes the auth user itself — so the same Apple ID, Google
account or email cannot sign back into the deleted account. There is no
retention window and no soft-delete. If any step fails the app says so
explicitly rather than reporting success.

The same screen also offers "Export my data".

## Age rating

The app accepts photographs of the user, fully clothed, and returns AI-written
critiques and AI-generated images. It includes an AI chatbot (Chat with Hem).
There is no user-to-user contact, no location use, no gambling, no violence and
no mature content. Nudity is not a supported input; the image provider's safety
filter rejects such photos on the cover path and the app tells the user so.

## Contact

Efe Simsek — efe@cloudgeng.com — phone: `FILL_IN`

## Anything else worth knowing

- A fresh account goes through a short onboarding (two questions plus an optional
  body-calibration photo) before reaching Home. The demo account has completed
  it, so this only appears if the reviewer creates a new account.
- Cover headlines and cover lines are generated by the model in-app. They are not
  taken from any publication, and no publication or fashion-house name appears
  anywhere in the app's copy or in the prompts sent to the model.
- Try on is the only Pro-gated feature reachable from the camera menu. The
  demo account has Pro, so it opens directly.
