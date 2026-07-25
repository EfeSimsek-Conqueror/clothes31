# FitScore / Hem — Android

Jetpack Compose client for the FitScore / Hem experience.

## Run

Prerequisites:
- Android Studio (Ladybug or newer) — provides both the SDK and a JBR (Java 17).
- Android SDK Platform 36 and Build-Tools 35+ installed (this repo assumes `~/Library/Android/sdk`).

From the command line:

```bash
# Point Gradle at Android Studio's bundled JBR (Java 17)
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd android
./gradlew assembleDebug
./gradlew installDebug   # to install on a running emulator/device
```

Or open the `android/` folder as a project in Android Studio and press Run.

## Configuration

Supabase is wired in `data/SupabaseClient.kt` against project
`https://ilrzqifdmjvooeyqvexd.supabase.co` using the legacy anon JWT. All
reads degrade gracefully when the network / auth isn't set up, so the UI
still demos with seeded mock content.

Storage bucket used for outfit photos: `outfits` (created out-of-band via
the Management API).

## Design system

See `ui/theme/DesignSystem.kt`:
- Palette (`HemColors`): Paper `#F3EEE4`, Card cream `#F7F2E8`, Ink
  `#141210`, Muted `#6B6459`, Bronze `#B0743A`, Gold gradient
  `#C99A5B` → `#8C6033`.
- Type: `SerifFamily` = Playfair Display, `SansFamily` = Inter (Google
  Fonts, downloadable at runtime).
- Components: `PrimaryButton`, `Eyebrow`, `SerifDisplay`, `SerifTitle`,
  `PullQuote`, `ScoreChip`, `SectionRow`, `Hairline`, `OutlinedPill`.

## Screens

Wired end-to-end via `MainActivity`:
`Splash → SignIn → Onboard1 → Onboard2 → Paywall → Home` (with tabs to
`Studio`, `Journal`, `You` and the `ScoreDetail` and `ScoreSheet` flows).
