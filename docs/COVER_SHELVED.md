# Magazine Covers — shelved feature handover

Status: **shelved, not deleted.** Every line of code is still in the repo, the
edge function is still deployed, and the whole feature comes back by flipping a
single boolean. This document explains what it did, how it is switched off, how
it works end to end, and the things that were painful to get right so nobody has
to rediscover them.

Written at the time of shelving. Last real work on the server was
`043b8c5 perf(compose-cover): stop rasterising a full canvas to draw the barcode`.

---

## 1. What it did

A user could turn one of their outfit photos into a magazine cover.

The composer (`MagazineCoverSheet`) took two photos:

- **YOU** — the subject photo, shot in-app or picked from the library, or loaded
  automatically when the sheet was opened bound to an existing outfit.
- **REFERENCE** (optional) — a photo of a real magazine cover whose look the
  user wanted. Optional; without it the app produced its own editorial cover.

On top of that: a **mood** chip (Quiet luxury, Editorial, Brutalist, Romantic,
Sport, Minimal, Punk, Maximalist), an optional **masthead** override (the field
is empty by default and only shows "FITRATER" as greyed placeholder text), and
an **Edit** sheet (`EditCoverSheet`) for overriding what the AI would otherwise
pick: a free-form prompt, custom masthead, custom headline, custom pull-quote,
and up to four coverlines.

A likeness-consent prompt (`@AppStorage("cover_likeness_ack_v1")`) was shown
once before the first compose. Compose cost `Supa.magazineCoverCost` (10)
credits, plus 3 more when a reference photo was attached. Composes took roughly
30–60s behind a `WaitingOverlay`.

Two adjacent flows existed in Studio:

- **Create Cover Template** (`CoverTemplateCreatorView`) — an 8-step wizard that
  designs a cover *style* with no subject photo. Saved as a row in
  `magazine_covers` with `outfit_id NULL` and offered later as a reference
  ("My Covers") inside the composer.
- **MY COVERS rail** — a horizontal rail of the user's saved covers on the
  Studio screen, tappable into `CoverPreviewSheet`.

A finished cover was also mirrored into `outfits` with `kind = "magazine_cover"`
so it appeared in the journal beside the other tool outputs
(`MagazineCoverSheet.saveCoverToJournal`).

## 2. Why it is shelved

Product decision by the owner. It was parked, not pulled for a defect: the
feature worked end to end at the time it was switched off. There is no
outstanding crash, rejection, or broken dependency behind this. Do not read a
technical cause into it.

## 3. Exactly how it is shelved

One flag, in `/Users/efe/clothes31/ios/FitScore/FitScore/Data/SupaClient.swift`:

```swift
static let magazineCoversEnabled = false
```

Setting it to `true` restores the feature completely. There is no other switch,
no server-side kill, no remote config, and no code was deleted.

Gated entry points, all of which use `if Supa.magazineCoversEnabled`:

| File | What is gated |
| --- | --- |
| `ios/FitScore/FitScore/Camera/CameraMenuSheet.swift:126` | The "Compose a cover" row in the camera menu, including its leading `Hairline()` so the divider does not double up. |
| `ios/FitScore/FitScore/App/MainTabView.swift:95` | The `.cover` route. The route stays wired; when disabled it presents `Color.clear` that immediately clears `cameraBus.pending`, so a stray request self-dismisses instead of stranding the user on a blank sheet they cannot close. |
| `ios/FitScore/FitScore/Studio/StudioView.swift:103,108,113` | The three cover `fullScreenCover` presentations — composer, template creator, cover preview. The presentations stay attached; only their contents are gated. |
| `ios/FitScore/FitScore/Studio/StudioView.swift:405` | The "Compose a Cover" and "Create Cover Template" action cards. |
| `ios/FitScore/FitScore/Studio/StudioView.swift:264` | The MY COVERS rail. |
| `ios/FitScore/FitScore/Studio/StudioView.swift:534` | The `Repo.shared.loadCovers()` fetch in Studio refresh — skipped so we do not pay for a round-trip nobody renders. |

Nothing in `Repo.swift`, `Models.swift`, `MagazineCoverSheet.swift`,
`EditCoverSheet.swift` or `CoverTemplateCreatorView.swift` was touched by the
shelving. Those files still compile and are still built into the binary.

## 4. Architecture

**Client → edge function.** `Repo.composeCover(...)`
(`ios/FitScore/FitScore/Data/Repo.swift:881`) invokes the `compose-cover` edge
function. Photos are uploaded to the outfit bucket first and passed as *signed*
URLs (`uploadOutfitPhoto` → `signedOutfitUrl`). Response decodes into
`ComposeCoverResponse` (`Models.swift:492`): `cover_url`, `headline`,
`pull_quote`, `vol_number`, `cover_id`, `error`.

**The server.** `/Users/efe/clothes31/supabase/functions/compose-cover/index.ts`
(~2,250 lines, versioned in its header comment; currently v34/v35). It requires
a bearer JWT, resolves the caller with the service-role client, then runs a
profanity/slur/hate check over every user-typed string *before* anything is
generated or billed (`findProhibitedInAny` → `text_rejected`). Then it forks:

**Branch A — a reference cover was supplied** (`index.ts:1836`). This is the
*typography rebuild*. `readCoverStyle` sends the reference to Fal vision and
gets back a `CoverStyle` spec: masthead, meta line, coverlines, tagline,
per-element font/tracking/size, and where the featured name sits. Then
`renderFullCover` draws the user's *own* photograph full-bleed at 1080×1920 and
sets the borrowed typography over it. Nothing generative touches the
photograph — the face on the cover is the face in the camera roll,
unconditionally. The older `runReferenceSwapChain` (fal face-swap, then
flux-kontext, then two nano-banana passes, each verified by byte hash and a
multi-image vision compare) survives only as a fallback for when the style read
fails outright. If both fail, the response is `{error: "swap_failed"}` — it
deliberately never ships the untouched reference.

**Branch B — no reference** (`index.ts:2096`). Fal `any-llm/vision` writes a
headline and pull-quote from the photo, nano-banana renders an editorial base
image with an explicit "no text, no logos, no borders" instruction, and then the
same `renderFullCover` lays Fitrater's own chrome on top: masthead, a freshly
computed meta line `"<Month Year> · VOL <n> · <price>"`, headline, pull-quote,
right-aligned coverlines, a drawn barcode and a `fitrater.ai` credit.

**Typography renderer.** `renderFullCover` (`index.ts:1490`) uses ImageScript.
Fonts are fetched at render time (`loadFonts`). Ink colour is chosen per band by
sampling luminance so type stays readable over the photo. Headline, pull-quote
and coverlines are measured as **one stack** and placed as one block anchored
off the masthead band.

**Storage + DB.** Both branches upload JPEG bytes to the public
`magazine_covers` storage bucket under `"<user_id>/<outfit_id or uuid>.jpg"`,
then insert one row into `public.magazine_covers`
(`outfit_id, user_id, headline, pull_quote, vol_number, image_path`) and return
its id as `cover_id`. Clients build display URLs by hand as
`…/storage/v1/object/public/magazine_covers/<image_path>`. `vol_number` is the
ISO week of the year, or the template's own value when a template was used.

Two sibling functions exist and are also still deployed:
`create-cover-template` (pure typography, no Fal image gen — used by the
wizard) and `regenerate-cover-headline`.

## 5. What still exists while shelved

Shelving is **client-side only**.

- `compose-cover`, `create-cover-template` and `regenerate-cover-headline` are
  still deployed and still reachable by any authenticated caller. Nothing in
  the shelving revokes them, and `debug=1` still returns raw JPEG bytes.
- Existing covers still render. The `magazine_covers` rows and bucket objects
  were not touched, and any cover that was mirrored into `outfits` with
  `kind = "magazine_cover"` still appears in the journal and opens in
  `ToolResultView` (`ios/FitScore/FitScore/Score/ToolResultView.swift:38,108,352`),
  which reads its headline and pull-quote out of `signals.cover`. That path is
  **not** behind the flag — deliberately, so users do not lose history.
- `Supa.magazineCoverCost = 10` is untouched, as is
  `Supa.magazineCoverThreshold = 8.5` (which is currently unreferenced anywhere
  — it was for an auto-trigger that no longer exists).
- Both branches still bill Fal on every call if invoked directly.

## 6. Known rough edges and hard-won lessons

**The Deno worker's resource ceiling is the real constraint.** The
style-rebuild branch died with `WORKER_RESOURCE_LIMIT` on every real reference
before `494e069`. Bisection showed no single input was at fault; only the
combination of a real spec, real strings and a borrowed layout crashed it.
Assume you are near the ceiling and that any new drawing work can push it over.

**Per-glyph rasterisation is the cost.** `drawText` falls back to drawing one
glyph at a time whenever `tracking > 0`, and a real spec puts the masthead,
headline and all four coverline rows on tracked paths — around 200
`Image.renderText` calls per cover for roughly 40 distinct glyphs, multiplied
again by halo passes. The fix was a glyph cache keyed by
`(font, size, colour, char)`, cleared per request via `resetGlyphCache()`. The
hard caps in `readCoverStyle` (coverlines truncated to 22 chars, at most four,
headline size clamped to 70–150px) are **compute limits, not taste limits** —
three 44-character lines killed the isolate. Do not relax them casually.

**Do not rasterise a whole canvas to draw a small thing.** Even after the glyph
cache, real covers hit the CPU ceiling: the barcode was built as a page-sized
SVG and handed to `Image.renderSVG`, rasterising a 1080×1920 RGBA surface (~8MB)
to paint a 180×90 block. It is now drawn directly with `drawBox` (`043b8c5`),
same seed and geometry. Verified end to end at 6.4s with a 12MP subject and a
dense poster reference.

**Text used to overlap.** The original layout used fixed fractional Y positions;
whenever a borrowed layout put the masthead and the headline on the same side of
the page, they collided. The measured single-stack layout in `renderFullCover`
fixed it. If you add another text element, add it to the measured stack rather
than positioning it independently.

**Borrowed coverlines carry the reference's real third-party names.**
`readCoverStyle` asks the model for the reference's coverlines *verbatim*, so a
rebuilt cover can display real publication names, celebrity names and article
titles lifted off someone else's magazine. Commit `2021564` stripped third-party
trademarks from Fitrater's own UI copy and prompts for App Store review, but it
did not and cannot solve this: the strings come from whatever the user
photographs. This is the biggest unresolved risk in the feature and needs a
decision (filter, paraphrase, or drop borrowed coverlines) before it ships
again.

**The meta/date line goes stale in Branch A.** The rebuild passes `style.meta`
straight through, so a cover built from a 2021 reference is stamped
"Winter 2021". Only Branch B composes a live `"<Month Year> · VOL n · price"`
line. Users read that as a bug.

**Nothing here bills or refunds atomically.** The client checks credits with
`CreditsGate`, then calls the function, then spends on success. A crash between
compose and `spendCredits` gives the cover away free; the reverse ordering would
charge for failures. Fine at the current volume, worth revisiting.

## 7. Before you unshelve

1. Flip `Supa.magazineCoversEnabled` to `true` and build. No other code change
   should be needed.
2. Smoke-test both branches against the live function — one compose with a
   reference photo, one without — and confirm `built: "style-rebuild"` in the
   Branch A response rather than `"swap-chain"`. A silent style-read failure
   otherwise only shows up as a stranger in the frame.
3. Watch the function logs for `WORKER_RESOURCE_LIMIT`. If Deno's runtime, the
   ImageScript version or the font URLs have moved since, the render budget is
   the first thing to re-verify.
4. Decide what to do about borrowed third-party coverlines and the stale meta
   line before any App Store submission that includes the feature.
5. Confirm `magazine_covers` RLS and the public bucket policy are still what you
   expect — covers are served from public URLs assembled client-side.
6. Re-check credit pricing against current Fal costs. The header comment quotes
   ~$0.05 best case and ~$0.19 worst case per compose against a 10-credit price;
   the rebuild path is cheaper than the swap chain it replaced, so the numbers
   are conservative but stale.
7. Re-test the likeness-consent gate (`cover_likeness_ack_v1`) and the
   profanity filter on user-typed cover text — both are App Store commitments
   from `2021564`.
