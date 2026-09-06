package com.fitrater.app.ui.screens.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitrater.app.data.Supa
import com.fitrater.app.data.billing.RcBilling
import com.fitrater.app.data.model.ClosetItem
import com.fitrater.app.data.model.OutfitInsert
import com.fitrater.app.data.model.averaged
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.data.service.HemScored
import com.fitrater.app.data.service.HemService
import com.fitrater.app.ui.components.ReportContentSheet
import com.fitrater.app.ui.components.ReportKind
import com.fitrater.app.ui.components.SkeletonBar
import com.fitrater.app.ui.theme.BurnedCaption
import com.fitrater.app.ui.theme.CircleCloseButton
import com.fitrater.app.ui.theme.DashedBox
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.Hairline
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.ProBadge
import com.fitrater.app.ui.theme.ProBadgeStyle
import com.fitrater.app.ui.theme.ScoreChip
import com.fitrater.app.ui.theme.SegmentedPill
import com.fitrater.app.util.CreditsBus
import com.fitrater.app.util.CreditsGate
import com.fitrater.app.util.GateResult
import com.fitrater.app.util.ToastBus
import io.github.jan.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// COPY. Every string below is byte-identical to the iOS TryOnView. If you edit
// one here, edit the Swift twin in the same commit — copy drift between the two
// platforms is treated as a bug in this repo.
// ---------------------------------------------------------------------------

private const val C_EYEBROW = "WEAR IT"
private const val C_TITLE = "Try on"
private const val C_SUBTITLE = "Two photos: your fit + the piece you want to try on."
private const val C_YOU = "YOU"
private const val C_DROP_1 = "Your full-length photo"
private const val C_DROP_2A = "or "
private const val C_DROP_2B = "browse files"
private const val C_PHOTO_RULE = "Full length, plain wall, arms down. Hem can't invent what it can't see."
private const val C_CAMERA = "Camera"
private const val C_PHOTOS = "Photos"
private const val C_PIECE = "PIECE"
private const val C_SEG_STUDIO = "From Studio"
private const val C_SEG_UPLOAD = "Upload"
private const val C_ADD_PLUS = "+"
private const val C_ADD_DESIGN = "Design"
private const val C_EMPTY_STUDIO = "Nothing in your Studio yet. Design a piece and try it on."
private const val C_OPEN_STUDIO = "Open Studio"
private const val C_UPLOAD_1 = "Garment photo"
private const val C_UPLOAD_2 = "Flat lay works best."
private const val C_PRODUCT_LINK = "Try from a product link"
private const val C_FOOT_NO_PHOTO = "Add your photo to continue"
private const val C_FOOT_NO_PIECE = "Pick a piece to continue"
private const val C_ERR_TRYON = "That didn't come out. Nothing was charged."
private const val C_ERR_IMPORT = "That photo didn't import. Try another one."
private const val C_ERR_CREDIT_SYNC =
    "Credits didn't sync — your balance may look high until the next refresh."

private const val C_LINK_EYEBROW = "PRODUCT LINK"
private const val C_LINK_TITLE = "Paste the page"
private const val C_LINK_BODY = "Hem pulls the item's own photo. You'll see it before anything is charged."
private const val C_LINK_PLACEHOLDER = "https://…"
private const val C_LINK_FETCH = "Fetch"
private const val C_LINK_CANCEL = "Cancel"
private const val C_LINK_BUSY = "Reading the page…"
private const val C_LINK_ERR_HTTPS = "That needs to be an https link."
private const val C_LINK_ERR_NO_IMAGE =
    "That page didn't hand over a picture. Screenshot the item and upload it instead."
private const val C_LINK_ERR_UNREACHABLE =
    "Couldn't reach that page. Some stores block automated reads — upload a screenshot instead."

private const val C_WAIT_TRYON_EYEBROW = "COMPOSING"
private const val C_WAIT_TRYON_TITLE = "Dressing you now"
private val C_WAIT_TRYON_TIPS = listOf(
    "Draping the fabric on your frame…",
    "Matching fit tension and folds.",
    "Preserving your face, restyling the rest.",
    "One-of-one, no filters — just cloth.",
)

private const val C_WORN_TITLE = "Worn"
private const val C_WORN_SUBTITLE = "Hem composited the piece onto your frame."
private const val C_CAPTION_ON_YOU = "on you"
private const val C_CAPTION_YOURS = "yours"
// Two different fallbacks, byte-identical to the Swift `pieceDisplayName`. One
// shared constant made an unnamed Studio piece read "linked to Your upload".
private const val C_FALLBACK_UPLOAD = "your upload"
private const val C_FALLBACK_STUDIO = "the piece"
private const val C_VERDICT_FAILED = "Hem couldn't read this one. The look is still yours."
private const val C_SAVE_SAVING = "Saving to Journal…"
private const val C_SAVE_FAILED = "Not saved — the look is still on screen."
private const val C_SAVE_RETRY = "Retry"
private const val C_REPORT_A11Y = "Report this try-on"
private const val C_SEASON_TILE = "Season swap"
private const val C_ANOTHER_TILE = "Try another piece"
private const val C_DONE = "Done"

private const val C_SEASON_EYEBROW = "SEASON SWAP"
private const val C_SEASON_TITLE = "Re-shoot this look"
private const val C_SEASON_BODY = "Same you, same piece — different weather."
private val C_SEASONS = listOf("Summer", "Autumn", "Winter", "Spring")
private const val C_SEASON_CANCEL = "Cancel"
private const val C_WAIT_SEASON_EYEBROW = "RE-SHOOTING"
private const val C_WAIT_SEASON_TITLE = "Swapping the season"
private val C_WAIT_SEASON_TIPS = listOf(
    "Same face, same cut — new weather.",
    "Adjusting the light before the layers.",
    "Palette first, then the outer layer.",
    "One frame, re-shot.",
)
private const val C_ERR_SEASON = "The re-shoot didn't come out. Nothing was charged."

/**
 * Season re-shoot prompt. NOT user-facing, but it must stay byte-identical to the
 * Swift `seasonPrompt(_:)` — two platforms drifting here means the same tap gives
 * two different renders.
 */
internal fun seasonPrompt(season: String): String {
    val guidance = when (season) {
        "Summer" -> "high summer — bright even daylight, outer layers removed, warm sun-bleached palette"
        "Autumn" -> "early autumn — low golden light, a light knit or jacket layered over, rust and olive accents"
        "Winter" -> "cold January — flat overcast light, a plausible outer coat and scarf layered over, muted winter palette"
        else -> "spring — soft diffused light after rain, a light unlined jacket, fresh cool palette"
    }
    return "Re-shoot this exact person wearing this exact outfit. Keep the face, hair, body " +
        "proportions, pose, and the garment's cut, colour, and fabric 100% identical. Change only " +
        "the season styling: $guidance. Editorial full-length photograph, natural light, plain warm background."
}

/**
 * One-line kill switch for the product-link import. If App Review pushes back on
 * client-side page reads, flip this to false: the row disappears entirely rather
 * than degrading into a stub that does nothing.
 */
private const val PRODUCT_LINK_ENABLED = true

/** iOS uses a 20pt gutter on this screen. HemSpace.gutter is 24.dp and would break parity. */
private val PAGE_H = 20.dp

private sealed interface Phase {
    data object Compose : Phase
    data class Busy(val eyebrow: String, val title: String, val tips: List<String>) : Phase
    data class Result(val url: String) : Phase
}

private enum class SaveState { Idle, Saving, Saved, Failed }

/** The single reason the footer button is not tappable. One table, both platforms. */
private sealed interface ComposeBlock {
    data object NeedPerson : ComposeBlock
    data object NeedPiece : ComposeBlock
    data class NeedCredits(val missing: Int) : ComposeBlock
    data object Ready : ComposeBlock
}

/** Flow 2 — Try on: a piece worn on the user's own photo, then judged by Hem. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TryOnScreen(
    onClose: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenStudioCreate: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Honour the OS "remove animations" setting for the one animated affordance
    // on this screen (the lifted piece card).
    val reduceMotion = remember {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }

    // ---- Studio pieces ----
    var pieces by remember { mutableStateOf<List<ClosetItem>>(emptyList()) }
    var loadingPieces by remember { mutableStateOf(true) }
    // Saveable so a trip to the camera screen does not silently drop the selection.
    var pickedPieceId by rememberSaveable { mutableStateOf<String?>(null) }
    val pickedPiece = pieces.firstOrNull { it.id == pickedPieceId }

    // ---- Piece source. "studio" = a generated piece; "upload" = the user's own photo. ----
    var pieceSource by rememberSaveable { mutableStateOf("studio") }
    var importedPieceBytes by remember { mutableStateOf<ByteArray?>(null) }
    // The storage PATH, never the 1-hour signed URL: a stale cached URL is why an
    // hour-old import used to fail with an opaque fal_error.
    var importedPiecePath by rememberSaveable { mutableStateOf<String?>(null) }
    var importedPieceName by rememberSaveable { mutableStateOf<String?>(null) }
    var importingPiece by remember { mutableStateOf(false) }
    // The picked bytes do not survive a trip to the camera screen but the storage
    // path does, so re-sign it for the preview rather than showing an empty slot
    // above an enabled button.
    var importedPreviewUrl by remember { mutableStateOf<String?>(null) }

    // ---- The person ----
    var personUri by remember { mutableStateOf<Uri?>(null) }
    var personBytes by remember { mutableStateOf<ByteArray?>(null) }

    // ---- Flow ----
    var phase by remember { mutableStateOf<Phase>(Phase.Compose) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveState by remember { mutableStateOf(SaveState.Idle) }
    var savedOutfitId by remember { mutableStateOf<String?>(null) }
    var verdict by remember { mutableStateOf<HemScored?>(null) }
    var verdictLoading by remember { mutableStateOf(false) }
    var verdictFailed by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(0f) }
    // Captured at generate time so the result caption cannot drift if the user
    // changes the compose-screen selection afterwards.
    var resultPieceName by remember { mutableStateOf(C_FALLBACK_STUDIO) }
    var resultPieceId by remember { mutableStateOf<String?>(null) }
    // The season the on-screen result was re-shot for. Retry must not drop it.
    var lastSeason by remember { mutableStateOf<String?>(null) }
    // Monotonic. Guards every state write a save makes, so a save belonging to a
    // previous run cannot paint its verdict / saved-state under the current image.
    var runToken by remember { mutableStateOf(0) }

    // ---- Sheets ----
    var showUrlSheet by remember { mutableStateOf(false) }
    var showSeasonSheet by remember { mutableStateOf(false) }

    val balance by CreditsBus.balance.collectAsState()

    val pieceDisplayName = when {
        pieceSource == "upload" ->
            importedPieceName?.trim()?.ifBlank { null } ?: C_FALLBACK_UPLOAD
        else -> pickedPiece?.name?.trim()?.ifBlank { null } ?: C_FALLBACK_STUDIO
    }

    // ---- Import path shared by the gallery picker and the product-link sheet ----
    suspend fun importPiece(raw: ByteArray, name: String?): Boolean {
        importingPiece = true
        val processed = withContext(Dispatchers.IO) { downscaleJpeg(raw) }
        importedPieceBytes = processed
        val path = runCatching {
            val uid = Repo.userId ?: error("Not signed in")
            val p = "${uid}/tryon-ref-${UUID.randomUUID()}.jpg"
            withContext(Dispatchers.IO) {
                Supa.client.storage.from("outfits").upload(p, processed) { upsert = false }
            }
            p
        }.getOrElse {
            Log.e("TryOn", "piece upload failed", it)
            importedPieceBytes = null
            importingPiece = false
            ToastBus.post(C_ERR_IMPORT)
            return false
        }
        importedPiecePath = path
        importedPieceName = name?.trim()?.take(40)?.ifBlank { null }
        pieceSource = "upload"
        importingPiece = false
        return true
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        personUri = u
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(u)?.buffered()?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                personUri = null
                ToastBus.post(C_ERR_IMPORT)
                return@launch
            }
            personBytes = withContext(Dispatchers.IO) { downscaleJpeg(bytes) }
        }
    }

    val pieceGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(u)?.buffered()?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                ToastBus.post(C_ERR_IMPORT)
                return@launch
            }
            importPiece(bytes, null)
        }
    }

    LaunchedEffect(importedPiecePath, importedPieceBytes) {
        val path = importedPiecePath
        importedPreviewUrl = if (path != null && importedPieceBytes == null) {
            Repo.signedOutfitUrl(path)
        } else {
            null
        }
    }

    LaunchedEffect(Unit) {
        // Belt-and-suspenders: Try-on is Pro-only. If a Free user lands here via deep link
        // or state restoration, bounce them straight to the paywall.
        // Refresh opportunistically, then read the SAME cached flag iOS reads.
        // refreshCustomerInfo() returns null offline and when RevenueCat is not
        // configured, so treating null as "not Pro" locked out real subscribers
        // and every server-granted Pro, which never touches RevenueCat at all.
        runCatching { RcBilling.refreshCustomerInfo() }
        val proNow = RcBilling.hasServerPro() || RcBilling.isPro()
        if (!proNow) {
            onClose()
            onOpenPaywall()
            return@LaunchedEffect
        }
        CreditsBus.refresh()
        pieces = runCatching { Repo.closetItems() }.getOrDefault(emptyList())
        loadingPieces = false
        // Pull any bytes handed over by CameraCaptureScreen.
        val pending = com.fitrater.app.util.CameraBus.consume()
        if (pending != null) {
            personBytes = pending
            personUri = null
        }
    }

    // ---- Save + judge. Shared by the first render and every season re-shoot. ----
    suspend fun saveLook(url: String, seasonSuffix: String?, token: Int) {
        if (token != runToken) return
        saveState = SaveState.Saving
        verdict = null
        verdictFailed = false
        verdictLoading = true

        val prepared = runCatching {
            val outBytes = Repo.downloadBytes(url)
            val outPath = withContext(Dispatchers.IO) { Repo.uploadOutfitPhoto(outBytes, ext = "png") }
            outPath to Repo.signedOutfitUrl(outPath)
        }.getOrElse {
            Log.e("TryOn", "result upload failed", it)
            if (token != runToken) return
            verdictLoading = false
            verdictFailed = true
            saveState = SaveState.Failed
            return
        }
        val outPath = prepared.first
        val signed = prepared.second

        // The score is absorbed into the try-on price: no gate, no spend. fal fetches
        // the URL server-side, so it has to be publicly reachable — the signed URL is,
        // and it is the same upload the Journal row needs anyway.
        val scored = if (signed == null) {
            null
        } else {
            runCatching {
                // No brief and no honesty lookup: the tone is fixed server-side,
                // and a try-on has no occasion the wearer chose.
                HemService.score(signed, "Everyday")
            }.getOrElse {
                Log.e("TryOn", "score failed", it)
                null
            }
        }
        if (token != runToken) return
        verdict = scored
        verdictLoading = false
        verdictFailed = scored == null

        runCatching {
            val comment = scored?.hemComment?.takeIf { it.isNotBlank() } ?: "Try-on: $resultPieceName"
            val row = Repo.insertOutfit(
                OutfitInsert(
                    user_id = Repo.userId ?: error("Not signed in"),
                    photo_path = outPath,
                    score = scored?.let { it.subscores.averaged ?: it.score } ?: 0.0,
                    occasion = "Everyday",
                    hem_comment = comment + (seasonSuffix?.let { " · $it" } ?: ""),
                    subscores = scored?.subscores,
                    swaps = scored?.swaps,
                    kind = "tryon",
                    linked_piece_id = resultPieceId,
                ),
            )
            row.id
        }.onSuccess { id ->
            if (token != runToken) return
            savedOutfitId = id
            saveState = SaveState.Saved
        }.onFailure {
            Log.e("TryOn", "journal save failed", it)
            if (token != runToken) return
            saveState = SaveState.Failed
        }
    }

    // ---- The paid render ----
    fun startTryOn() {
        if (phase is Phase.Busy) return
        error = null
        // Claim Busy SYNCHRONOUSLY. CreditsGate.check is network-bound (credits,
        // trial state, monthly spend); deciding busy only after it returned left
        // the enabled ink button live for hundreds of ms, and two taps charged
        // 40 credits for a single look.
        val nameNow = pieceDisplayName
        val idNow = if (pieceSource == "upload") null else pickedPiece?.id
        runToken += 1
        val token = runToken
        phase = Phase.Busy(C_WAIT_TRYON_EYEBROW, C_WAIT_TRYON_TITLE, C_WAIT_TRYON_TIPS)
        scope.launch {
            val gate = CreditsGate.check(Supa.TRYON_COST)
            if (gate !is GateResult.Ok) {
                phase = Phase.Compose
                CreditsGate.explainAndBlock(gate)
                if (gate is GateResult.InsufficientBalance) onOpenPaywall()
                CreditsBus.refresh()
                return@launch
            }
            runCatching {
                val pb = personBytes ?: withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(personUri!!)?.buffered()?.use { it.readBytes() }
                }?.let { withContext(Dispatchers.IO) { downscaleJpeg(it) } }
                    ?: error("Could not read photo")
                personBytes = pb
                val personPath = Repo.uploadOutfitPhoto(pb)
                val personSigned = Repo.signedOutfitUrl(personPath) ?: error("Could not sign person URL")
                // Always re-sign at generate time: a cached signed URL expires in an hour.
                val pieceSigned = when (pieceSource) {
                    "upload" -> Repo.signedOutfitUrl(importedPiecePath ?: error("No garment photo"))
                        ?: error("Could not sign garment URL")
                    else -> pickedPiece?.image_path?.let { Repo.signedClosetUrl(it) }
                        ?: error("No piece selected")
                }
                // Pass the raw category through; the edge function decides FASHN (garments)
                // vs nano-banana (hats, glasses, shoes, …).
                val cat = if (pieceSource == "upload") null else pickedPiece?.category?.lowercase(Locale.US)
                val resp = Repo.tryOnPiece(personSigned, pieceSigned, cat ?: "auto")
                resp.image_url ?: error(resp.error ?: "no image")
            }.onSuccess { url ->
                runCatching { Repo.spendCredits(Supa.TRYON_COST, "tryon") }
                    .onFailure {
                        Log.e("TryOn", "spend failed", it)
                        ToastBus.post(C_ERR_CREDIT_SYNC)
                    }
                resultPieceName = nameNow
                resultPieceId = idNow
                wipe = 0f
                savedOutfitId = null
                lastSeason = null
                phase = Phase.Result(url)
                scope.launch { saveLook(url, null, token) }
            }.onFailure {
                Log.e("TryOn", "try-on failed", it)
                phase = Phase.Compose
                error = C_ERR_TRYON
            }
        }
    }

    // ---- The paid re-shoot ----
    fun seasonSwap(season: String) {
        val current = (phase as? Phase.Result)?.url ?: return
        error = null
        // Same reason as startTryOn: the gate is a suspend call, so Busy has to be
        // claimed before it or a double tap buys two re-shoots.
        runToken += 1
        val token = runToken
        phase = Phase.Busy(C_WAIT_SEASON_EYEBROW, C_WAIT_SEASON_TITLE, C_WAIT_SEASON_TIPS)
        scope.launch {
            val gate = CreditsGate.check(Supa.SEASON_SWAP_COST)
            if (gate !is GateResult.Ok) {
                phase = Phase.Result(current)
                CreditsGate.explainAndBlock(gate)
                if (gate is GateResult.InsufficientBalance) onOpenPaywall()
                CreditsBus.refresh()
                return@launch
            }
            runCatching {
                // generate-piece switches to nano-banana/edit whenever image_urls is
                // non-empty — same deployed function, no backend change.
                val resp = Repo.generatePiece(seasonPrompt(season), listOf(current))
                resp.image_url ?: error(resp.error ?: "no image")
            }.onSuccess { url ->
                runCatching { Repo.spendCredits(Supa.SEASON_SWAP_COST, "studio_gen_variation") }
                    .onFailure {
                        Log.e("TryOn", "spend failed", it)
                        ToastBus.post(C_ERR_CREDIT_SYNC)
                    }
                wipe = 0f
                savedOutfitId = null
                lastSeason = season
                phase = Phase.Result(url)
                scope.launch { saveLook(url, season, token) }
            }.onFailure {
                Log.e("TryOn", "season swap failed", it)
                phase = Phase.Result(current)
                error = C_ERR_SEASON
            }
        }
    }

    val hasPerson = personBytes != null || personUri != null
    val hasPiece = when (pieceSource) {
        "upload" -> importedPiecePath != null
        else -> pickedPiece != null
    }
    val block: ComposeBlock = when {
        !hasPerson -> ComposeBlock.NeedPerson
        !hasPiece -> ComposeBlock.NeedPiece
        // Server-granted Pro bypasses balance in CreditsGate, so it must not be
        // told it is short — that would be a disabled button with a false reason.
        balance != null && balance!! < Supa.TRYON_COST && !RcBilling.hasServerPro() ->
            ComposeBlock.NeedCredits(Supa.TRYON_COST - balance!!)
        else -> ComposeBlock.Ready
    }

    val phaseNow = phase

    Box(Modifier.fillMaxSize().background(HemColors.Paper)) {
        Scaffold(
            containerColor = HemColors.Paper,
            // MainActivity consumes window insets once for the whole app.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column(Modifier.fillMaxWidth().background(HemColors.Paper)) {
                    Hairline()
                    Column(Modifier.fillMaxWidth().padding(horizontal = PAGE_H, vertical = 14.dp)) {
                        val err = error
                        if (err != null) {
                            Text(
                                err,
                                style = HemType.body.copy(fontSize = 12.5.sp, color = HemColors.Bronze),
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        if (phaseNow is Phase.Result) {
                            PrimaryButton(
                                label = C_DONE,
                                onClick = onClose,
                                height = 62.dp,
                                corner = 8.dp,
                            )
                        } else {
                            PrimaryButton(
                                label = "Try it on · ${Supa.TRYON_COST} credits",
                                onClick = { startTryOn() },
                                enabled = block is ComposeBlock.Ready && phaseNow !is Phase.Busy,
                                height = 62.dp,
                                corner = 8.dp,
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                when (block) {
                                    is ComposeBlock.NeedPerson -> FootNote(C_FOOT_NO_PHOTO)
                                    is ComposeBlock.NeedPiece -> FootNote(C_FOOT_NO_PIECE)
                                    is ComposeBlock.NeedCredits -> FootNote("Need ${block.missing} more credits")
                                    is ComposeBlock.Ready -> {
                                        val b = balance
                                        if (b == null) {
                                            Box(Modifier.width(150.dp)) {
                                                SkeletonBar(height = 11.dp)
                                            }
                                        } else {
                                            FootNote("$b credits left · result in ~30 seconds")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) { inner ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ---- Eyebrow row (shared by both states) ----
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = PAGE_H, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Eyebrow(C_EYEBROW, size = 10.sp)
                    Spacer(Modifier.width(8.dp))
                    ProBadge()
                    Spacer(Modifier.weight(1f))
                    CircleCloseButton(onClick = onClose)
                }

                if (phaseNow is Phase.Result) {
                    // ============================ WORN ============================
                    Text(
                        C_WORN_TITLE,
                        style = HemType.serifDisplay.copy(fontSize = 36.sp, lineHeight = 42.sp),
                        modifier = Modifier.padding(horizontal = PAGE_H),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        C_WORN_SUBTITLE,
                        style = HemType.bodyMuted.copy(fontSize = 14.sp),
                        modifier = Modifier.padding(horizontal = PAGE_H),
                    )
                    Spacer(Modifier.height(20.dp))

                    var cardWidth by remember { mutableStateOf(1) }
                    DashedBox(
                        modifier = Modifier
                            .padding(horizontal = PAGE_H)
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .onSizeChanged { cardWidth = it.width.coerceAtLeast(1) }
                            .pointerInputWipe(
                                onWipe = { wipe = it },
                                current = { wipe },
                            ),
                        corner = 14.dp,
                    ) {
                        // Under-layer: the photo the user came in with.
                        val pb = personBytes
                        if (pb != null) {
                            AsyncImage(
                                model = pb,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        AsyncImage(
                            model = phaseNow.url,
                            contentDescription = "Try-on result: $resultPieceName on you",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    clipRect(left = size.width * wipe) {
                                        this@drawWithContent.drawContent()
                                    }
                                },
                        )
                        if (wipe > 0.002f) {
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .offset { IntOffset((wipe * cardWidth).roundToInt() - 1, 0) }
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.9f)),
                            )
                        }
                        BurnedCaption(
                            leading = resultPieceName,
                            trailingItalic = if (wipe > 0.02f) C_CAPTION_YOURS else C_CAPTION_ON_YOU,
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(HemColors.Paper.copy(alpha = 0.9f))
                                .clickable { reporting = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Flag,
                                contentDescription = C_REPORT_A11Y,
                                tint = HemColors.Ink,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    // ---- Hem's read ----
                    Column(Modifier.fillMaxWidth().padding(horizontal = PAGE_H)) {
                        when {
                            verdictLoading -> {
                                Box(Modifier.width(72.dp)) { SkeletonBar(height = 26.dp, corner = 999.dp) }
                                Spacer(Modifier.height(10.dp))
                                SkeletonBar(height = 12.dp, widthFraction = 0.9f)
                                Spacer(Modifier.height(6.dp))
                                SkeletonBar(height = 12.dp, widthFraction = 0.6f)
                            }
                            verdict != null -> {
                                val v = verdict!!
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ScoreChip(score = v.subscores.averaged ?: v.score)
                                }
                                if (v.hemComment.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(v.hemComment, style = HemType.serifQuote.copy(fontSize = 16.sp))
                                }
                                val swaps = v.swaps.take(2)
                                if (swaps.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    swaps.forEach { s ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                            Text("·", style = HemType.bodyMuted.copy(fontSize = 13.sp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(s, style = HemType.bodyMuted.copy(fontSize = 13.sp))
                                        }
                                    }
                                }
                            }
                            verdictFailed -> Text(
                                C_VERDICT_FAILED,
                                style = HemType.bodyMuted.copy(fontSize = 13.sp),
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    // ---- Tri-state save row ----
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = PAGE_H),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (saveState) {
                            SaveState.Saving -> {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = HemColors.Bronze,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(C_SAVE_SAVING, style = HemType.bodyMuted.copy(fontSize = 13.sp))
                            }
                            SaveState.Saved -> {
                                Box(
                                    Modifier.size(18.dp).clip(CircleShape).background(HemColors.Ink),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        // Decorative: the sentence beside it says the same thing.
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Saved to Journal · linked to $resultPieceName",
                                    style = HemType.body.copy(fontSize = 13.sp),
                                )
                            }
                            SaveState.Failed -> {
                                Text(
                                    C_SAVE_FAILED,
                                    style = HemType.body.copy(fontSize = 13.sp, color = HemColors.Bronze),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    C_SAVE_RETRY,
                                    style = HemType.body.copy(
                                        fontSize = 13.sp,
                                        color = HemColors.Ink,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                    modifier = Modifier.clickable {
                                        val season = lastSeason
                                        val token = runToken
                                        scope.launch { saveLook(phaseNow.url, season, token) }
                                    },
                                )
                            }
                            SaveState.Idle -> Unit
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = PAGE_H),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SecondaryTile(
                            label = C_SEASON_TILE,
                            pro = true,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = {
                                if (RcBilling.isPro() || RcBilling.hasServerPro()) {
                                    showSeasonSheet = true
                                } else {
                                    onOpenPaywall()
                                }
                            },
                        )
                        SecondaryTile(
                            label = C_ANOTHER_TILE,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = {
                                // Invalidate any save still running for this look.
                                runToken += 1
                                lastSeason = null
                                phase = Phase.Compose
                                saveState = SaveState.Idle
                                savedOutfitId = null
                                verdict = null
                                verdictFailed = false
                                verdictLoading = false
                                wipe = 0f
                                error = null
                                if (pieceSource == "upload") {
                                    importedPiecePath = null
                                    importedPieceBytes = null
                                    importedPieceName = null
                                    importedPreviewUrl = null
                                } else {
                                    pickedPieceId = null
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                } else {
                    // ========================== COMPOSE ==========================
                    Text(
                        C_TITLE,
                        style = HemType.serifDisplay.copy(fontSize = 36.sp, lineHeight = 42.sp),
                        modifier = Modifier.padding(horizontal = PAGE_H),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        C_SUBTITLE,
                        style = HemType.bodyMuted.copy(fontSize = 14.sp),
                        modifier = Modifier.padding(horizontal = PAGE_H),
                    )
                    Spacer(Modifier.height(28.dp))

                    Eyebrow(C_YOU, size = 10.sp, modifier = Modifier.padding(horizontal = PAGE_H))
                    Spacer(Modifier.height(10.dp))
                    DashedBox(
                        modifier = Modifier
                            .padding(horizontal = PAGE_H)
                            .fillMaxWidth()
                            .aspectRatio(16f / 11f)
                            .clickable {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        dashed = !hasPerson,
                    ) {
                        when {
                            personBytes != null -> AsyncImage(
                                model = personBytes,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            personUri != null -> AsyncImage(
                                model = personUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.Image,
                                    // Decorative: the two lines under it carry the meaning.
                                    contentDescription = null,
                                    tint = HemColors.Muted,
                                    modifier = Modifier.size(26.dp),
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(C_DROP_1, style = HemType.body.copy(fontSize = 14.sp))
                                Spacer(Modifier.height(4.dp))
                                Row {
                                    Text(C_DROP_2A, style = HemType.bodyMuted.copy(fontSize = 12.sp))
                                    Text(
                                        C_DROP_2B,
                                        style = HemType.bodyMuted.copy(
                                            fontSize = 12.sp,
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    )
                                }
                            }
                        }
                        if (hasPerson) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(HemColors.Paper.copy(alpha = 0.9f))
                                    .clickable {
                                        personBytes = null
                                        personUri = null
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove your photo",
                                    tint = HemColors.Ink,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.padding(horizontal = PAGE_H),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        UnderlineLink(C_CAMERA, onClick = onOpenCamera)
                        UnderlineLink(C_PHOTOS) {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        C_PHOTO_RULE,
                        style = HemType.bodyMuted.copy(fontSize = 12.sp),
                        modifier = Modifier.padding(horizontal = PAGE_H),
                    )

                    Spacer(Modifier.height(24.dp))
                    // FlowRow, not Row: mirrors the iOS ViewThatFits fallback so the
                    // pill drops to its own line at large font scales instead of
                    // squeezing the label off the edge.
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = PAGE_H),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Eyebrow(
                            C_PIECE,
                            size = 10.sp,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        SegmentedPill(
                            options = listOf("studio" to C_SEG_STUDIO, "upload" to C_SEG_UPLOAD),
                            selection = pieceSource,
                            onSelect = { pieceSource = it },
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    if (pieceSource == "upload") {
                        DashedBox(
                            modifier = Modifier
                                .padding(horizontal = PAGE_H)
                                .fillMaxWidth()
                                .aspectRatio(16f / 11f)
                                .clickable(enabled = !importingPiece) {
                                    pieceGalleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                            dashed = importedPieceBytes == null && importedPreviewUrl == null,
                        ) {
                            val ib: Any? = importedPieceBytes ?: importedPreviewUrl
                            if (ib != null) {
                                AsyncImage(
                                    model = ib,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (importingPiece) {
                                    Box(
                                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            color = Color.White,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                if (importedPieceName != null) {
                                    BurnedCaption(leading = importedPieceName!!)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.Image,
                                        // Decorative: the two lines under it carry the meaning.
                                        contentDescription = null,
                                        tint = HemColors.Muted,
                                        modifier = Modifier.size(26.dp),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(C_UPLOAD_1, style = HemType.body.copy(fontSize = 14.sp))
                                    Spacer(Modifier.height(4.dp))
                                    Text(C_UPLOAD_2, style = HemType.bodyMuted.copy(fontSize = 12.sp))
                                }
                            }
                        }
                    } else if (loadingPieces) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_H),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(3) {
                                Box(Modifier.width(96.dp).padding(top = 12.dp)) {
                                    SkeletonBar(height = 150.dp, corner = 10.dp)
                                }
                            }
                        }
                    } else if (pieces.isEmpty()) {
                        DashedBox(
                            modifier = Modifier.padding(horizontal = PAGE_H).fillMaxWidth(),
                            corner = 14.dp,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                Text(C_EMPTY_STUDIO, style = HemType.body.copy(fontSize = 14.sp))
                                Spacer(Modifier.height(14.dp))
                                UnderlineLink(C_OPEN_STUDIO, onClick = onOpenStudioCreate)
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_H),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            itemsIndexed(
                                pieces,
                                key = { i, p -> p.id ?: "piece-$i" },
                            ) { index, piece ->
                                PieceStripCard(
                                    piece = piece,
                                    index = index,
                                    selected = pickedPieceId != null && pickedPieceId == piece.id,
                                    reduceMotion = reduceMotion,
                                    onTap = {
                                        // Re-tapping the selected card unselects it (parity with e25df5a).
                                        pickedPieceId = if (pickedPieceId == piece.id) null else piece.id
                                    },
                                )
                            }
                            item(key = "add-design") {
                                AddDesignCard(onClick = onOpenStudioCreate)
                            }
                        }
                    }

                    if (PRODUCT_LINK_ENABLED) {
                        Spacer(Modifier.height(20.dp))
                        ProductLinkRow(
                            modifier = Modifier.padding(horizontal = PAGE_H),
                            onClick = {
                                if (RcBilling.isPro() || RcBilling.hasServerPro()) {
                                    showUrlSheet = true
                                } else {
                                    onOpenPaywall()
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        if (phaseNow is Phase.Busy) {
            HemWaitingOverlay(
                eyebrow = phaseNow.eyebrow,
                title = phaseNow.title,
                tips = phaseNow.tips,
            )
        }
    }

    if (reporting) {
        val gen = (phaseNow as? Phase.Result)?.url
        ReportContentSheet(
            contentKind = ReportKind.OUTFIT,
            // Falls back to the result URL until the Journal row lands.
            contentId = savedOutfitId ?: gen ?: "",
            onDismiss = { reporting = false },
        )
    }

    if (showUrlSheet) {
        ProductLinkSheet(
            scope = scope,
            onDismiss = { showUrlSheet = false },
            onImported = { bytes, title ->
                scope.launch {
                    showUrlSheet = false
                    importPiece(bytes, title)
                }
            },
        )
    }

    if (showSeasonSheet) {
        SeasonSwapSheet(
            onDismiss = { showSeasonSheet = false },
            onConfirm = { season ->
                showSeasonSheet = false
                seasonSwap(season)
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Private pieces of this screen
// ---------------------------------------------------------------------------

@Composable
private fun FootNote(text: String) {
    Text(
        text,
        style = HemType.bodyMuted.copy(fontSize = 12.sp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun UnderlineLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = HemType.bodyMuted.copy(
            fontSize = 12.sp,
            textDecoration = TextDecoration.Underline,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** One card in the bleeding piece strip. Selected cards lift and underline. */
@Composable
private fun PieceStripCard(
    piece: ClosetItem,
    index: Int,
    selected: Boolean,
    reduceMotion: Boolean,
    onTap: () -> Unit,
) {
    var signed by remember(piece.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(piece.id) {
        signed = piece.image_path?.let { Repo.signedClosetUrl(it) }
    }
    val spec: androidx.compose.animation.core.AnimationSpec<Dp> =
        if (reduceMotion) snap() else androidx.compose.animation.core.spring()
    // The strip's height is constant (162dp of block + label); the selection lifts
    // by giving up its top padding, so nothing ever needs to draw outside the row.
    val topPad by animateDpAsState(
        targetValue = if (selected) 0.dp else 12.dp,
        animationSpec = spec,
        label = "pieceLift",
    )
    val blockHeight by animateDpAsState(
        targetValue = if (selected) 162.dp else 150.dp,
        animationSpec = spec,
        label = "pieceHeight",
    )
    Column(
        Modifier
            .width(96.dp)
            .padding(top = topPad)
            .clickable(onClick = onTap),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(blockHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(HemColors.CardCream),
        ) {
            val s = signed
            if (s != null) {
                AsyncImage(
                    model = s,
                    contentDescription = piece.name ?: piece.category,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            BurnedCaption(
                leading = String.format(Locale.US, "STUDIO %02d", index + 1),
                inset = 8.dp,
                leadingSize = 8.5.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            piece.name ?: piece.category ?: "piece",
            style = HemType.body.copy(
                fontSize = 12.sp,
                lineHeight = 15.sp,
                textDecoration = if (selected) TextDecoration.Underline else null,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Reserve two lines so the strip does not jitter as names wrap — a
            // MINIMUM, not a fixed height: at fontScale >= 1.2 a hard 32.dp
            // clipped the second line mid-glyph.
            modifier = Modifier.heightIn(min = 32.dp),
        )
    }
}

@Composable
private fun AddDesignCard(onClick: () -> Unit) {
    Column(Modifier.width(96.dp).padding(top = 12.dp).clickable(onClick = onClick)) {
        DashedBox(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            corner = 10.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(C_ADD_PLUS, style = HemType.body.copy(fontSize = 20.sp, color = HemColors.Muted))
                Spacer(Modifier.height(4.dp))
                Text(C_ADD_DESIGN, style = HemType.bodyMuted.copy(fontSize = 12.sp))
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ProductLinkRow(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(C_PRODUCT_LINK, style = HemType.bodyMuted.copy(fontSize = 14.sp))
        Spacer(Modifier.weight(1f))
        ProBadge(style = ProBadgeStyle.TextOnly)
    }
}

@Composable
private fun SecondaryTile(
    label: String,
    modifier: Modifier = Modifier,
    pro: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier
            // heightIn, not height: at large font scales "Try another piece" has
            // to wrap to a second line rather than ellipsize inside a fixed box.
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, HemColors.Hairline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            style = HemType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (pro) {
            Spacer(Modifier.width(4.dp))
            Text(
                "PRO",
                style = TextStyle(
                    fontFamily = com.fitrater.app.ui.theme.SansFamily,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = HemColors.Bronze,
                ),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

/** Full-screen busy state. TryOnScreen used to only relabel the button. */
@Composable
private fun HemWaitingOverlay(eyebrow: String, title: String, tips: List<String>) {
    var tipIndex by remember { mutableStateOf(0) }
    LaunchedEffect(tips) {
        while (true) {
            delay(2600)
            tipIndex = (tipIndex + 1) % tips.size
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            // Swallow taps so nothing underneath can be poked mid-render.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow(eyebrow, size = 10.sp)
            Spacer(Modifier.height(12.dp))
            Text(title, style = HemType.serifTitle, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = HemColors.Ink)
            Spacer(Modifier.height(24.dp))
            Text(
                tips[tipIndex],
                style = HemType.bodyMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonSwapSheet(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var season by remember { mutableStateOf(C_SEASONS.first()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HemColors.Paper,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = PAGE_H, vertical = 20.dp)) {
            Eyebrow(C_SEASON_EYEBROW, size = 10.sp)
            Spacer(Modifier.height(8.dp))
            Text(C_SEASON_TITLE, style = HemType.serifTitle.copy(fontSize = 26.sp))
            Spacer(Modifier.height(6.dp))
            Text(C_SEASON_BODY, style = HemType.bodyMuted.copy(fontSize = 14.sp))
            Spacer(Modifier.height(20.dp))
            // IntrinsicSize.Min + fillMaxHeight: a chip whose label wraps at a large
            // font scale grows, and the other three grow with it, exactly like the
            // iOS ChipRow. The old fixed row clipped the label mid-word.
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                C_SEASONS.forEach { s ->
                    val active = s == season
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) HemColors.Ink else Color.Transparent)
                            .border(
                                1.dp,
                                if (active) HemColors.Ink else HemColors.Hairline,
                                RoundedCornerShape(999.dp),
                            )
                            .clickable { season = s }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s,
                            style = HemType.body.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (active) Color.White else HemColors.Ink,
                            ),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton(
                label = "Re-shoot · ${Supa.SEASON_SWAP_COST} credits",
                onClick = { onConfirm(season) },
                height = 62.dp,
                corner = 8.dp,
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                UnderlineLink(C_SEASON_CANCEL, onClick = onDismiss)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductLinkSheet(
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onImported: (ByteArray, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pasted by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        containerColor = HemColors.Paper,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = PAGE_H, vertical = 20.dp)) {
            Eyebrow(C_LINK_EYEBROW, size = 10.sp)
            Spacer(Modifier.height(8.dp))
            Text(C_LINK_TITLE, style = HemType.serifTitle.copy(fontSize = 26.sp))
            Spacer(Modifier.height(6.dp))
            Text(C_LINK_BODY, style = HemType.bodyMuted.copy(fontSize = 14.sp))
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = pasted,
                    onValueChange = { pasted = it; linkError = null },
                    singleLine = true,
                    enabled = !busy,
                    textStyle = HemType.body.copy(fontSize = 14.sp),
                    cursorBrush = SolidColor(HemColors.Ink),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (pasted.isEmpty()) {
                                Text(C_LINK_PLACEHOLDER, style = HemType.bodyMuted.copy(fontSize = 14.sp))
                            }
                            field()
                        }
                    },
                )
            }
            val e = linkError
            if (e != null) {
                Spacer(Modifier.height(10.dp))
                Text(e, style = HemType.body.copy(fontSize = 12.5.sp, color = HemColors.Bronze))
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                label = if (busy) C_LINK_BUSY else C_LINK_FETCH,
                enabled = !busy && pasted.isNotBlank(),
                onClick = {
                    if (busy) return@PrimaryButton
                    busy = true
                    linkError = null
                    scope.launch {
                        when (val r = fetchProductLink(pasted)) {
                            is LinkResult.Ok -> {
                                busy = false
                                onImported(r.bytes, r.title)
                            }
                            is LinkResult.Err -> {
                                busy = false
                                linkError = r.message
                            }
                        }
                    }
                },
                height = 62.dp,
                corner = 8.dp,
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                UnderlineLink(C_LINK_CANCEL) { if (!busy) onDismiss() }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Product-link import. Pure client: no edge function, no HTML rendering — the
// page body is read only to pull three meta tags out of it.
// ---------------------------------------------------------------------------

private sealed interface LinkResult {
    class Ok(val bytes: ByteArray, val title: String?) : LinkResult
    class Err(val message: String) : LinkResult
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private const val HTML_CAP = 256 * 1024
private const val IMAGE_CAP = 8 * 1024 * 1024

private suspend fun fetchProductLink(raw: String): LinkResult = withContext(Dispatchers.IO) {
    val parsed = runCatching { URL(raw.trim()) }.getOrNull()
    if (parsed == null || !parsed.protocol.equals("https", ignoreCase = true)) {
        return@withContext LinkResult.Err(C_LINK_ERR_HTTPS)
    }
    val page = runCatching {
        val conn = (parsed.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", DESKTOP_UA)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            error("HTTP $code")
        }
        val finalUrl = conn.url
        val body = conn.inputStream.use { readAtMost(it, HTML_CAP) }
        finalUrl to String(body, Charsets.UTF_8)
    }.getOrElse {
        Log.e("TryOn", "product link read failed", it)
        return@withContext LinkResult.Err(C_LINK_ERR_UNREACHABLE)
    }
    val finalUrl = page.first
    val html = page.second

    val imageRef = metaContent(html, "og:image")
        ?: metaContent(html, "twitter:image")
        ?: return@withContext LinkResult.Err(C_LINK_ERR_NO_IMAGE)
    val title = metaContent(html, "og:title")

    // Resolves relative ("/img/x.jpg") and protocol-relative ("//cdn/x.jpg") refs.
    val imageUrl = runCatching { URL(finalUrl, imageRef) }.getOrNull()
        ?: return@withContext LinkResult.Err(C_LINK_ERR_NO_IMAGE)
    if (!imageUrl.protocol.equals("https", ignoreCase = true)) {
        return@withContext LinkResult.Err(C_LINK_ERR_HTTPS)
    }

    val bytes = runCatching {
        val conn = (imageUrl.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", DESKTOP_UA)
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            error("HTTP $code")
        }
        conn.inputStream.use { readAtMost(it, IMAGE_CAP) }
    }.getOrElse {
        Log.e("TryOn", "product image fetch failed", it)
        return@withContext LinkResult.Err(C_LINK_ERR_UNREACHABLE)
    }
    if (bytes.isEmpty() || BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) {
        return@withContext LinkResult.Err(C_LINK_ERR_NO_IMAGE)
    }
    LinkResult.Ok(bytes, title)
}

/** Pulls one `<meta property|name="…" content="…">` value. Never renders the page. */
private fun metaContent(html: String, key: String): String? {
    val k = Regex.escape(key)
    val forward = Regex(
        """<meta[^>]*?(?:property|name)\s*=\s*["']$k["'][^>]*?content\s*=\s*["']([^"']*)["']""",
        RegexOption.IGNORE_CASE,
    )
    val reverse = Regex(
        """<meta[^>]*?content\s*=\s*["']([^"']*)["'][^>]*?(?:property|name)\s*=\s*["']$k["']""",
        RegexOption.IGNORE_CASE,
    )
    val hit = forward.find(html)?.groupValues?.getOrNull(1)
        ?: reverse.find(html)?.groupValues?.getOrNull(1)
        ?: return null
    return decodeEntities(hit).trim().ifBlank { null }
}

private fun decodeEntities(s: String): String = s
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#34;", "\"")
    .replace("&#39;", "'")
    .replace("&#x27;", "'")
    .replace("&apos;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&nbsp;", " ")

private fun readAtMost(input: InputStream, cap: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0
    while (total < cap) {
        val n = input.read(buf)
        if (n <= 0) break
        val take = minOf(n, cap - total)
        out.write(buf, 0, take)
        total += take
    }
    return out.toByteArray()
}

/** Resize the longest edge to 2048 and re-encode JPEG q=85. Mirrors the capture path. */
private fun downscaleJpeg(bytes: ByteArray): ByteArray {
    val bm = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return bytes
    val max = 2048
    val longest = maxOf(bm.width, bm.height)
    val scaled = if (longest <= max) {
        bm
    } else {
        val ratio = max.toFloat() / longest
        Bitmap.createScaledBitmap(bm, (bm.width * ratio).toInt(), (bm.height * ratio).toInt(), true)
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray()
}

/**
 * Horizontal-only drag that wipes the composite back to the original photo.
 * Release snaps back. Costs nothing, so it is never gated.
 */
private fun Modifier.pointerInputWipe(
    onWipe: (Float) -> Unit,
    current: () -> Float,
): Modifier = this.pointerInput(Unit) {
    detectHorizontalDragGestures(
        onDragEnd = { onWipe(0f) },
        onDragCancel = { onWipe(0f) },
    ) { change, drag ->
        change.consume()
        val w = size.width.toFloat().coerceAtLeast(1f)
        onWipe(((current() * w + drag) / w).coerceIn(0f, 1f))
    }
}
