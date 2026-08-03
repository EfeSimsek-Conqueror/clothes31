package com.fitrater.app.data.demo

import com.fitrater.app.data.model.Annotation
import com.fitrater.app.data.model.ClosetItem
import com.fitrater.app.data.model.HemNote
import com.fitrater.app.data.model.Outfit
import com.fitrater.app.data.model.Profile
import com.fitrater.app.data.model.PushSettings
import com.fitrater.app.data.model.Subscores
import com.fitrater.app.data.model.SundayLetter
import com.fitrater.app.data.repo.LatestActivity
import com.fitrater.app.data.repo.Stats
import android.content.Context

/**
 * LOCAL SCREENSHOT HARNESS — NOT FOR COMMIT.
 *
 * Seeds the real UI with a fixed set of looks so the Play Store captures show
 * populated screens without a sign-in, a backend round trip, or spent credits.
 * Every screen, layout and style below is the app's own; only the rows are canned.
 * Copy follows the Hem voice already in `HemService.scoreStub`.
 *
 * Flip [on] to false (or just revert this file plus the `Demo.on` guards in
 * Repo.kt / MainActivity.kt) to restore normal behaviour.
 */
object Demo {

    const val on: Boolean = true

    /** Photos ship in app/src/main/assets/demo/ and load through Coil's asset scheme. */
    private fun asset(name: String) = "file:///android_asset/demo/$name"

    val profile = Profile(
        id = "demo-user",
        display_name = "Alex",
        email = "alex@example.com",
        gender = "unspecified",
        style_tags = listOf("Minimal", "Street", "Tailored"),
        honesty = "balanced",
        onboarded = true,
        is_pro = true,
        first_run_done = true,
        preferred_colors = listOf("#2E3A4B", "#C8B49A", "#4A4F3C"),
        rated_ok = true,
        paywall_shown = true,
    )

    val credits = 240

    val outfits: List<Outfit> = listOf(
        Outfit(
            id = "d1",
            user_id = "demo-user",
            name = "Navy coach jacket",
            score = 9.1,
            photo_path = "man_street_navy_jacket.jpg",
            verdict = "Best look this month",
            occasion = "Weekend",
            weather_c = 14,
            hem_comment = "Navy on indigo is a narrow lane and you stayed in it. " +
                "The black boot is what stops it drifting casual.",
            dominant_colors = listOf("#1E2A44", "#3A4A6B", "#221C18"),
            subscores = Subscores(color = 9.4, fit = 9.0, style_match = 9.2, seasonal = 8.6),
            swaps = listOf(
                "A lighter tee would open up the middle.",
                "Roll the cuff once — the boot has earned the room.",
            ),
            annotations = listOf(
                Annotation(50.0, 30.0, "Jacket", 9.3, "Shoulder sits exactly where it should."),
                Annotation(48.0, 62.0, "Jeans", 8.9, "Indigo sits close enough to read deliberate."),
                Annotation(52.0, 88.0, "Boots", 9.0, "Black stops the whole thing going soft."),
            ),
            created_at = "2026-08-19T09:12:00Z",
            kind = "score",
        ),
        Outfit(
            id = "d2",
            user_id = "demo-user",
            name = "Grey overshirt",
            score = 8.7,
            photo_path = "man_street_grey_overshirt.jpg",
            verdict = "Sharp",
            occasion = "Work",
            weather_c = 17,
            hem_comment = "Grey on grey, rescued by the brown boot. That one warm note " +
                "is doing more work than anything else here.",
            dominant_colors = listOf("#6E7176", "#E8E3D8", "#6B4A32"),
            subscores = Subscores(color = 8.5, fit = 9.1, style_match = 8.8, seasonal = 8.3),
            swaps = listOf(
                "A darker trouser would sharpen the split at the waist.",
                "Keep the boot — it is the only warm thing in the frame.",
            ),
            annotations = listOf(
                Annotation(50.0, 34.0, "Overshirt", 9.1, "Clean shoulder, no pull at the button."),
                Annotation(49.0, 70.0, "Trousers", 8.6, "Break lands where it should."),
            ),
            created_at = "2026-08-18T08:40:00Z",
            kind = "score",
        ),
        Outfit(
            id = "d3",
            user_id = "demo-user",
            name = "Navy cord shirt",
            score = 8.4,
            photo_path = "man_mirror_navy_cord.jpg",
            verdict = "Easy win",
            occasion = "Everyday",
            weather_c = 12,
            hem_comment = "Corduroy adds the texture this palette was missing. " +
                "Easy, and it still looks decided.",
            dominant_colors = listOf("#2A3550", "#8A8E93", "#1F1B19"),
            subscores = Subscores(color = 8.2, fit = 8.6, style_match = 8.5, seasonal = 8.4),
            swaps = listOf(
                "A cuffed hem would show more of the boot.",
                "One warm accessory would lift all that blue.",
            ),
            annotations = listOf(
                Annotation(50.0, 36.0, "Cord shirt", 8.5, "Reads relaxed, not sloppy."),
                Annotation(50.0, 92.0, "Boots", 8.3, "Palette-friendly, low-drama."),
            ),
            created_at = "2026-08-15T18:05:00Z",
            kind = "score",
        ),
        Outfit(
            id = "d4",
            user_id = "demo-user",
            name = "Grey hoodie",
            score = 7.8,
            photo_path = "man_mirror_grey_hoodie.jpg",
            verdict = "Close",
            occasion = "Everyday",
            weather_c = 16,
            hem_comment = "The trousers are trying and the hoodie is not. " +
                "Pick one register and commit to it.",
            dominant_colors = listOf("#5A5A5E", "#4B4C39", "#F4F4F2"),
            subscores = Subscores(color = 8.1, fit = 7.4, style_match = 7.9, seasonal = 7.8),
            swaps = listOf(
                "Swap the hoodie for a crew and this jumps a point.",
                "The white sole is fine — leave it alone.",
            ),
            annotations = listOf(
                Annotation(50.0, 33.0, "Hoodie", 7.4, "Volume up top hides the line the trousers set."),
                Annotation(49.0, 66.0, "Trousers", 8.2, "Olive is the best thing here."),
            ),
            created_at = "2026-08-11T10:20:00Z",
            kind = "score",
        ),
    )

    fun signedUrl(path: String): String = asset(path.substringAfterLast('/'))

    /** Read one of the bundled demo photos, so a showcase screen starts loaded. */
    fun assetBytes(context: Context, name: String): ByteArray =
        context.assets.open("demo/$name").use { it.readBytes() }

    val best: Outfit get() = outfits.maxByOrNull { it.score ?: 0.0 }!!
    val latest: Outfit get() = outfits.first()
    val average: Double get() = outfits.mapNotNull { it.score }.average()

    val latestActivity: LatestActivity
        get() = LatestActivity.OutfitItem(latest, signedUrl(latest.photo_path!!))

    val hemNote = HemNote(
        id = "n1",
        user_id = "demo-user",
        body = "Four looks this week and the navy one is still ahead. " +
            "You score highest when the palette stays under three colours.",
        created_at = "2026-08-20T07:00:00Z",
    )

    val letter = SundayLetter(
        id = "l1",
        user_id = "demo-user",
        week_start = "2026-08-17",
        week_end = "2026-08-23",
        body = "Your average climbed 0.6 this week. The pattern: you do your best work " +
            "in two colours plus one texture.",
        variant = "weekly",
        created_at = "2026-08-23T07:00:00Z",
    )

    val stats = Stats(
        pieces = 12,
        looks = 24,
        bestScore = 9.1,
        creditsUsed = 96,
        streakDays = 6,
        monthDelta = 0.6,
    )

    /**
     * Closet pieces, as garment photos rather than full-body looks — a "piece" tile
     * showing a whole person on a street reads as a placeholder.
     *
     * `category` must be one of StudioScreen's canonical lowercase keys
     * (top / bottom / outerwear / dress / shoes / accessory). Anything else falls
     * through its `ordering` list and lands in an arbitrary trailing section.
     * `color_hex` drives the tile tint, so it tracks the garment.
     */
    val closet: List<ClosetItem> = listOf(
        ClosetItem(
            id = "c1", user_id = "demo-user", category = "top",
            subcategory = "Oxford shirt", name = "White oxford",
            image_path = "piece_white_oxford.jpg", color_hex = "#E8E6E1",
            worn_count = 6, created_at = "2026-08-19T09:12:00Z",
        ),
        ClosetItem(
            id = "c2", user_id = "demo-user", category = "top",
            subcategory = "Sweatshirt", name = "Grey marl sweatshirt",
            image_path = "piece_grey_sweatshirt.jpg", color_hex = "#A8A9AB",
            worn_count = 9, created_at = "2026-08-17T08:40:00Z",
        ),
        ClosetItem(
            id = "c3", user_id = "demo-user", category = "top",
            subcategory = "T-shirt", name = "White cotton tee",
            image_path = "piece_white_tee.jpg", color_hex = "#EFEFEE",
            worn_count = 12, created_at = "2026-08-14T07:55:00Z",
        ),
        ClosetItem(
            id = "c4", user_id = "demo-user", category = "bottom",
            subcategory = "Jeans", name = "Straight-leg jeans",
            image_path = "piece_straight_jeans.jpg", color_hex = "#4C6A8C",
            worn_count = 11, created_at = "2026-08-18T18:05:00Z",
        ),
        ClosetItem(
            id = "c5", user_id = "demo-user", category = "bottom",
            subcategory = "Chinos", name = "Khaki chinos",
            image_path = "piece_khaki_chinos.jpg", color_hex = "#C2A67C",
            worn_count = 7, created_at = "2026-08-12T10:20:00Z",
        ),
        ClosetItem(
            id = "c6", user_id = "demo-user", category = "outerwear",
            subcategory = "Overcoat", name = "Charcoal wool overcoat",
            image_path = "piece_charcoal_overcoat.jpg", color_hex = "#3C3E42",
            worn_count = 4, created_at = "2026-08-16T09:30:00Z",
        ),
        ClosetItem(
            id = "c7", user_id = "demo-user", category = "outerwear",
            subcategory = "Field jacket", name = "Olive field jacket",
            image_path = "piece_olive_field_jacket.jpg", color_hex = "#5A5F42",
            worn_count = 5, created_at = "2026-08-10T11:15:00Z",
        ),
        ClosetItem(
            id = "c8", user_id = "demo-user", category = "accessory",
            subcategory = "Cap", name = "Navy ball cap",
            image_path = "piece_navy_cap.jpg", color_hex = "#26314A",
            worn_count = 3, created_at = "2026-08-08T16:45:00Z",
        ),
    )


    // ---- One dedicated look per feature -------------------------------------
    // Each showcase screen gets its own photo rather than reusing one model or
    // stacking all four into a single grid.
    const val SCORE_PHOTO  = "man_street_navy_jacket.jpg"    // Score a look
    const val ROAST_PHOTO  = "man_mirror_grey_hoodie.jpg" // Roast
    const val DECODE_PHOTO = "woman_street_camel_coat.jpg"     // Decode style
    const val VERSUS_A     = "man_mirror_navy_cord.jpg"        // A vs B — A
    const val VERSUS_B     = "man_street_grey_overshirt.jpg"     // A vs B — B

    /**
     * Try-on result. STAGED, not model output: this is the charcoal overcoat of closet
     * piece `c6`, photographed worn. The real screen renders whatever the try-on model
     * returns. Flagged in README-2026-08.md — do not present it as a real generation.
     */
    const val TRYON_RESULT = "tryon_charcoal_overcoat.jpg"

    /** Brutal-mode roast copy, in the voice of HemService's own stub quotes. */
    val roastComment =
        "A hoodie and the good trousers. Half of you got dressed, the other half " +
        "gave up — pick a side."

    val roastScore = 7.2

    /** A vs B result. */
    val versusWinner = "b"
    val versusScoreA = 7.8
    val versusScoreB = 9.1
    val versusComment =
        "B, and it is not close. A is a safe indoor answer; B is the one you would " +
        "want to be photographed in."
    val versusReasonA = "Cord reads soft and easy, but the whole thing sits in one flat tone."
    val versusReasonB = "Structure up top, clean line down. The boots finish it properly."

    /** Decode result — the breakdown of a reference look. */
    val decodeSignature = "Long line, warm neutral, everything else out of the way"
    val decodePalette = listOf("#B08256", "#F2F0EC", "#33353A", "#D8CBB8")
    val decodePieces = listOf(
        Triple("Coat", "Belted, below the knee", "Camel wool, soft shoulder"),
        Triple("Tee", "High crew", "Heavy cotton, off-white"),
        Triple("Trousers", "Straight, cropped", "Charcoal wool"),
        Triple("Sneaker", "Low profile", "Smooth white leather"),
    )

    val push = PushSettings(
        user_id = "demo-user",
        enabled = true,
        morning_stylist = true,
        weekly_task = true,
        wrapped = true,
    )
}
