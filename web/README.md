# FitScore — Web

The web client of **FitScore / Hem** — a cream editorial magazine take on an
honest closet.

## Stack

- Next.js 16 (App Router, Turbopack)
- Tailwind CSS v4
- `@supabase/ssr` + `@supabase/supabase-js`
- Google Fonts — Playfair Display, Inter, Fraunces
- `lucide-react`

## Getting started

```bash
pnpm install
pnpm dev
# open http://localhost:3000
```

Build:

```bash
pnpm build
pnpm start
```

## Environment

Copy `.env.example` to `.env.local` and fill in.

```env
NEXT_PUBLIC_SUPABASE_URL=https://YOUR-PROJECT.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=sb_publishable_xxx
# Optional legacy anon key fallback
NEXT_PUBLIC_SUPABASE_ANON_KEY_LEGACY=
```

The Supabase **management (`sbp_...`) token is server-only** and must never
be added to the client bundle. This project does not read the management
token at runtime — schema work is performed out-of-band via the Supabase
Management API.

## Routes

- `/` — Landing (editorial hero, sections, footer)
- `/signin` — Sign in (Google + email OTP via Supabase)
- `/onboarding` — Two-step gender + vibe onboarding
- `/paywall` — FitScore Pro paywall
- `/app` — Today (bottom-tab shell)
- `/app/studio` — Studio (closet pieces, credits)
- `/app/journal` — Journal (Sunday letter + list)
- `/app/you` — Profile + honesty dial + settings
- `/app/score/[id]` — Look detail w/ subscores + pieces
- `/app/score-a-look` — Scoring & Mirror action list

## Design system

Located in `components/design-system/`:

- `Eyebrow` — small-caps bronze label
- `SerifDisplay` — Playfair display headline
- `PullQuote` — italic serif quote with attribution
- `PrimaryButton` / `PrimaryLink` — 56px ink pill
- `ScoreChip` — white circular score pill
- `PieceCard` — tinted olive/rust/ink piece card
- `SectionRow` / `SectionPill` — list row with bronze pill
- `BottomTabBar` — Today / Studio / camera FAB / Journal / You
- `PhoneFrame` / `PhoneScroll` — mobile-first mockup, device-framed on desktop

## Data

`lib/data.ts` provides thin readers over `outfits`, `closet_items`,
`sunday_letters`, and sums `credit_transactions` for the credits pill.

`lib/hem.ts` returns a deterministic mock structured scoring result. **No real
LLM is called in this pass.**

## Notes

Design is the source of truth. Content is intentionally placeholder.
