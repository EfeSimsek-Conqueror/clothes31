@file:OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)

package com.fitrater.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.json.Json

object Supa {
    const val URL: String = "https://ilrzqifdmjvooeyqvexd.supabase.co"

    // Legacy anon JWT (supabase-kt v3 accepts publishable too, but this one is guaranteed).
    const val ANON_KEY: String =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlscnpxaWZkbWp2b29leXF2ZXhkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQzODAzNzUsImV4cCI6MjA5OTk1NjM3NX0." +
            "BfoEiMusmWwO4PAj7bD3aFDYNGdxCuXkqVSCySvRwvQ"

    const val GOOGLE_WEB_CLIENT_ID: String =
        "1062757466057-vngm8cbvrdjrra81sunt5ebi2rsf5u82.apps.googleusercontent.com"

    const val AUTH_CALLBACK_URL: String = "com.fitrater.app://auth-callback"

    // RevenueCat public Android SDK key. Get from RevenueCat dashboard →
    // Project Settings → API Keys → "Public app-specific API keys" for the Android app.
    // Format is `goog_...`. Replace before shipping the production AAB.
    const val RC_ANDROID_API_KEY: String = "goog_VwYVDpdRiqfJJKGOSLRwNuuwecv"

    // Credit economics — must match RevenueCat product credit grants.
    const val SIGNUP_CREDITS: Int = 25
    const val SCORE_COST: Int = 5
    const val GENERATE_COST: Int = 15
    const val TRYON_COST: Int = 20
    const val SEASON_SWAP_COST: Int = 10       // re-render the same look in another season
    const val VERSUS_COST: Int = 8
    const val DECODE_COST: Int = 10
    const val OCCASION_COST: Int = 15
    const val BODY_CALIBRATION_COST: Int = 0        // one-time, foundational
    const val MAGAZINE_COVER_COST: Int = 10         // nano-banana render + headline
    const val MIRROR_CLEANER_COST: Int = 0          // bundled as loyalty perk
    const val INVITATION_DECODE_COST: Int = 6       // OCR + combo generation
    const val COVER_TEMPLATE_COST: Int = 2          // Fal flux/schnell placeholder + SVG chrome
    const val TAILOR_TICKET_COST: Int = 2           // text-only prompt after heatmap
    const val FRONT_BACK_EXTRA_COST: Int = 2        // added on top of SCORE_COST for dual shot

    // ---- Studio 2.0 pricing (Aug 2026) — sequential outfit wizard ----
    // SINGLE_PIECE_COST supersedes GENERATE_COST (15) for the Studio single-piece flow.
    // Outfits are bundle-priced with a Pro gate above 2 pieces.
    // NOTE: the multi-piece constants below are not wired up yet — Android has no
    // outfit wizard so far; they exist so the Studio 2.0 port matches iOS 1:1.
    const val SINGLE_PIECE_COST: Int = 10
    const val OUTFIT_TWO_COST: Int = 18
    const val OUTFIT_THREE_COST: Int = 24
    const val OUTFIT_FOUR_COST: Int = 32
    const val OUTFIT_EXTRA_PIECE_COST: Int = 8      // per piece beyond 4
    const val EXTRA_ALTERNATIVES_COST: Int = 5      // "3 new alternatives" reroll
    const val SINGLE_ALTERNATIVE_REGEN_COST: Int = 2 // per-alternative regen
    const val STUDIO_COMBINE_STANDALONE_COST: Int = 6 // outfit combine at the end

    /**
     * Convenience: total credits for an N-piece outfit generation (each piece
     * yields 3 alternatives). Rejects/regens/combine are billed on top.
     */
    fun outfitCost(pieceCount: Int): Int = when {
        pieceCount <= 1 -> SINGLE_PIECE_COST
        pieceCount == 2 -> OUTFIT_TWO_COST
        pieceCount == 3 -> OUTFIT_THREE_COST
        pieceCount == 4 -> OUTFIT_FOUR_COST
        else -> OUTFIT_FOUR_COST + (pieceCount - 4) * OUTFIT_EXTRA_PIECE_COST
    }

    // Trial abuse guard: annual sub in its 7-day INTRO period is capped to this many credits/day.
    const val TRIAL_DAILY_CAP: Int = 20
    // Play Store rating one-shot reward.
    const val PLAY_RATING_REWARD: Int = 20

    // SKU id -> credits granted mapping (client-side, source of truth).
    val CREDIT_PACK_GRANTS: Map<String, Int> = mapOf(
        "credits_100" to 150,       // Starter — $1.99 → 150cr
        "credits_500" to 500,       // Popular — $6.99 → 500cr
        "credits_1500" to 1200,     // Pro Pack — $14.99 → 1200cr
        "credits_5000" to 3000,     // Mega — $39.99 → 3000cr (add to Play Console before enabling)
    )

    // Sub monthly credit cap (fair-use).
    const val SUB_MONTHLY_CAP: Int = 1200
    // Annual sub gives 9000/yr total (~750/mo) — enforced via monthly reset.
    const val SUB_ANNUAL_MONTHLY_CAP: Int = 750

    val client: SupabaseClient by lazy {
        createSupabaseClient(supabaseUrl = URL, supabaseKey = ANON_KEY) {
            // Ignore unknown DB columns so legacy fields on outfits/etc don't blow up
            // deserialization. `encodeDefaults` is on because the scoring-v4 `intake`
            // column round-trips a class whose fields carry defaults (so the
            // `'{}'::jsonb` on every pre-v4 row still decodes) — without it a brief
            // that happens to match the defaults would be written as an empty object
            // and the row would lose what it was graded against. `explicitNulls` stays
            // off, so a null is still omitted rather than written over a column default.
            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                coerceInputValues = true
                encodeDefaults = true
            })
            // Fal endpoints (nano-banana image gen, gemini vision scoring) can take 20–40s.
            // Default ktor timeout is 15s → surface as request timeout in the UI. Bump to 120s.
            httpConfig {
                install(HttpTimeout) {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 20_000
                    socketTimeoutMillis = 120_000
                }
            }
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "com.fitrater.app"
                host = "auth-callback"
                // Persist session to disk (SharedPreferences via multiplatform-settings-no-arg).
                // Without this, supabase-kt v3 defaults to MemorySessionManager and the user
                // must sign in on every cold launch.
                sessionManager = SettingsSessionManager()
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
    }
}
