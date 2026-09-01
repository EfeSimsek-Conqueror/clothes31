# App Store Connect — Metadata

Copy-paste ready. Bundle id: `com.fitrater.app`. ASC app id `6794940458`.

Checked against the source tree on 2026-09-01. Version 1.0, build 16.

---

## App Name (30 char max)
```
Fitrater
```

## Subtitle (30 char max)
```
Your outfit, edited by AI
```
(24 chars)

## Promotional Text (170 char max)
```
Compose a cover turns any look into a magazine spread. Try a garment on your own photo. Score any fit before you leave the mirror. Ask Hem anything, free.
```
(152 chars)

## Description (4000 char max)

Every feature named below exists in build 16 and is reachable from the camera
button in the tab bar or from the Studio tab. Nothing here is aspirational.

```
Fitrater is a private style desk. Score an outfit, build a wardrobe, try garments on your own photo, plan what to wear for an occasion, and stage any look as a magazine cover — all in one editorial workspace.

SCORE
Photograph the fit you are about to wear. Fitrater reads silhouette, fabric, palette, and occasion, then returns a numeric score with per-piece notes and a short editorial line from Hem, a resident critic who writes like a magazine editor. One voice: direct and constructive. Real strengths, real weaknesses, always with a fix.

TRY ON
Upload a photo of yourself and a garment — a Studio piece, a screenshot from a shop, a photo of a friend's coat — and see it rendered on you. How the shoulder sits, how the hem lands, how the colour reads, before the parcel arrives.

A VS B
Two photos, one winner, one sentence explaining why.

CHAT WITH HEM
Ask anything — what to wear, what to swap, whether the shoes work. Hem answers in the same honest voice and remembers your recent looks. Free, no credits.

STUDIO
Save every piece you own in a clean grid, tagged by palette, silhouette and season. Generate a new piece from a description, or run the outfit wizard to build a full look one piece at a time with three alternatives at each step. Studio pieces drop straight into Try on and into Compose a cover.

OCCASION COACH
Describe the moment — the wedding, the interview, the first date. Hem plans three outfits from what you already own and names the gaps. Or photograph the invitation itself and let Hem read the dress code for you.

COMPOSE A COVER
Turn a look into a magazine-style cover — masthead, headline, cover lines, all generated for you. Design and save your own cover templates in Studio. Made to be shared.

JOURNAL
Every score, every try-on, every cover kept in a quiet timeline. Filter by season, by score, by palette.

YOU
A style profile the app builds from what you wear and what you save. It shapes the tone of Hem's notes and the covers he suggests. You can edit it, reset it, or turn it off. Export everything, or delete your account and every photo in it, from one screen.

PRO
Fitrater is free to start. Fitrater Pro unlocks unlimited scoring, Studio without the cap, Try-on, the weekly Sunday letter from Hem, and Hem's full memory of your wardrobe. Credit packs are available if you would rather pay once than subscribe.

Fitrater is a workspace for the way you actually get dressed. It is not a social network. There is no feed. There are no followers. There is only your closet, your body, and a critic who will tell you the truth.
```

## Keywords (100 char max, comma-separated, no spaces)
```
fashion,outfit,style,ai,rating,studio,wardrobe,tryon,cover,editor,fit,closet,dress,look
```
(87 chars)

Do not reintroduce a publication name here. The previous keyword list contained
`vogue`, a Condé Nast trademark; it has been replaced with `wardrobe`.

## URLs
- **Support URL:** `https://fitrater.ai/support`
- **Marketing URL:** `https://fitrater.ai`
- **Privacy Policy URL:** `https://fitrater.ai/privacy`

The `/support` route now exists in the repo at `web/app/support/page.tsx`. It
must be **deployed** before submission — a 404 support URL is a 1.5 rejection on
its own. Confirm all three URLs load in a browser before you submit.

## Copyright
```
© 2026 Fitrater
```

## Categories
- **Primary:** Lifestyle
- **Secondary:** Photo & Video

## Age Rating

Do not copy an old answer. Apple replaced the 4+/9+/12+/17+ tiers; "12+" no
longer exists, and the questionnaire now asks separately about AI features. Fill
it in from the app's actual behaviour:

- **AI chatbot:** **Yes.** "Chat with Hem" is a free, open-ended text chat with a
  language model, reachable from the camera menu.
- **AI-generated content shown to the user:** **Yes.** Scores and critiques,
  generated garment images, virtual try-on renders, and magazine covers are all
  model output.
- **User-generated content shared with others:** **No.** There is no feed, no
  profiles and no account-to-account sharing. Users can export or share an image
  to their own device or to another app, but nothing is published inside
  Fitrater.
- **Sexual content / nudity:** **None.** Nudity is not a supported input; the
  image provider's safety filter rejects such photos on the cover path and the
  app surfaces the rejection.
- **Violence, horror, profanity, alcohol/drugs, gambling, contests:** **None.**
- **Unrestricted web access:** **No.** The only outbound links are the Terms,
  the Privacy Policy, the Support page and the App Store, and they open in an
  in-app browser.
- **Medical/treatment information:** **None.** Body calibration produces styling
  measurements, not health assessments.

The app's own Terms set a minimum age of 16, so do not accept a rating tier below
that without changing the Terms first. Whatever tier the questionnaire returns
once the AI answers are given honestly is the tier to ship.

## Version Info
- **Version:** 1.0
- **Build:** 16

  `MARKETING_VERSION = 1.0` and `CURRENT_PROJECT_VERSION = 16` in
  `FitScore.xcodeproj/project.pbxproj`, and `Info.plist` now references both
  through build settings. Do not edit version numbers in `Info.plist`.
- **What's New:**
  ```
  First release.
  ```

## App Review Info
- **Sign-in required:** Yes
- **Demo account:** see `REVIEW_NOTES.md` — it still contains `FILL_IN`
  placeholders for the credentials and the phone number
- **Contact:** Efe Simsek — efe@cloudgeng.com — phone number still required
- **Notes:** paste the contents of `REVIEW_NOTES.md`

## Terms removed from this document

For the record, so they are not reintroduced:

- "brutal-mode critiques" — the feature has no entry point in the app, and
  `supabase/functions/score-outfit/index.ts:23-26` coerces every request to the
  single honest tone.
- "roast" / "brutal" as feature names anywhere in customer-facing copy.
- "vogue" as a keyword.
- Weather on Home — the card has been removed; the app makes no weather or
  location request.
