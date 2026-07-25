# FitScore / Hem — iOS

SwiftUI, iOS 17+. The Xcode project builds without any SPM dependencies so it runs immediately.

## Requirements

- Xcode 15+
- iOS 17 simulator

## Build & run

```bash
cd ios/FitScore
open FitScore.xcodeproj
```

Or from the command line, target a simulator:

```bash
xcodebuild -project FitScore.xcodeproj -scheme FitScore \
  -destination 'generic/platform=iOS Simulator' \
  -configuration Debug build
```

To run:

```bash
xcodebuild -project FitScore.xcodeproj -scheme FitScore \
  -destination 'platform=iOS Simulator,name=iPhone 15' build
```

## Supabase

The app is wired to the FitScore Supabase project:

- URL: `https://ilrzqifdmjvooeyqvexd.supabase.co`
- Publishable key (client-safe) is embedded in `SupabaseClient.swift`.

`SupabaseClient` is a minimal URLSession-based wrapper so the app builds
without pulling `supabase-swift`. To adopt the official SDK, add
`https://github.com/supabase-community/supabase-swift` via SwiftPM and
replace the calls in `SupabaseClient.swift`.

Existing tables used: `profiles`, `outfits`, `closet_items`,
`credit_transactions`, `sunday_letters`. Storage bucket `outfits`
already exists.

## Architecture

- `FitScoreApp.swift` — app entry, injects `SessionStore`.
- `SessionStore.swift` — nav stage + user state + `MockHemService`.
- `DesignSystem.swift` — `Palette`, `Serif`, `Eyebrow`, `PrimaryButton`,
  `SecondaryButton`, `ScoreChip`, `PullQuote`, `SectionRow`, `TanCard`,
  `Hairline`, gold gradient.
- `RootView.swift` routes between: `SplashView` → `SignInView` →
  `OnboardingGenderView` → `OnboardingVibeView` → `MainTabView`.
- `MainTabView.swift` — five-tab layout with center black camera FAB.
- Tabs: `TodayView`, `StudioView`, `JournalView`, `YouView`.
- Sheets: `ScoreLookSheet`, `PaywallView`. Push: `ScoreDetailView`.

## Design tokens

Paper `#F3EEE4`, Card `#F7F2E8`, Ink `#141210`, Muted `#6B6459`, Bronze
`#B0743A`, Gold gradient `#C99A5B → #8C6033`. Serif via SF New York
(`design: .serif`), body via SF Pro. Primary CTA: 56pt black fill,
white uppercase tracked, 4pt radius. Eyebrows: 11pt, tracked 2,
bronze. Active tab: bronze underline.
