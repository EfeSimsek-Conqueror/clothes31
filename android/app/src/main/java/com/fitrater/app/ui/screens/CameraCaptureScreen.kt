package com.fitrater.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * In-app camera capture with a 4:5 portrait framing guide.
 *
 * Flow:
 *   permission → live preview + shutter → captured preview → Use photo (crop 4:5 + resize + JPEG q=85) → onCaptured.
 *
 * If the CAMERA permission is denied, we fall back to the system photo picker so the user is
 * never trapped.
 */
@Composable
fun CameraCaptureScreen(
    onClose: () -> Unit,
    onCaptured: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionAsked by remember { mutableStateOf(false) }
    var permissionDeniedTwice by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.buffered()?.use { it.readBytes() }
                }
                if (bytes != null) {
                    val processed = withContext(Dispatchers.IO) {
                        processBitmapBytes(bytes, cropToPortrait = false)
                    }
                    onCaptured(processed)
                }
            }
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) {
            if (permissionAsked) permissionDeniedTwice = true
            permissionAsked = true
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permLauncher.launch(Manifest.permission.CAMERA)
            permissionAsked = true
        }
    }

    // Camera state
    var lensBack by remember { mutableStateOf(true) }
    var flashOn by remember { mutableStateOf(false) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Fallback when permission truly denied
    if (permissionDeniedTwice && !permissionGranted) {
        LaunchedEffect(Unit) {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(HemColors.Ink),
    ) {
        val bmp = capturedBitmap
        if (bmp != null) {
            // Review screen
            ReviewCapture(
                preview = capturedPreview,
                onRetake = {
                    capturedBitmap = null
                    capturedPreview = null
                },
                onUse = {
                    if (busy) return@ReviewCapture
                    busy = true
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) {
                            processCapturedBitmap(bmp)
                        }
                        Log.i("Camera", "captured bytes=${bytes.size}")
                        onCaptured(bytes)
                    }
                },
                onClose = onClose,
            )
        } else if (permissionGranted) {
            // Live preview + overlay + controls
            CameraLivePreview(
                lensBack = lensBack,
                flashOn = flashOn,
                imageCapture = imageCapture,
                onShutter = {
                    if (busy) return@CameraLivePreview
                    busy = true
                    error = null
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val executor = Executors.newSingleThreadExecutor()
                    imageCapture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                var bytes: ByteArray? = null
                                var err: Throwable? = null
                                try {
                                    val raw = imageProxyToBitmap(image)
                                    // Downscale raw sensor bitmap (can be ~160MB) — crashes Canvas otherwise.
                                    val small = resizeLongestEdge(raw, 2048)
                                    if (small !== raw) raw.recycle()
                                    // Crop to the 4:5 guide frame, encode JPEG, hand off.
                                    bytes = processCapturedBitmap(small)
                                    small.recycle()
                                } catch (t: Throwable) {
                                    Log.e("Camera", "decode failed", t)
                                    err = t
                                } finally {
                                    image.close()
                                    executor.shutdown()
                                }
                                // onCaptured triggers a nav pop → LifecycleRegistry.setCurrentState
                                // which must run on the main thread. This callback fires on the camera
                                // executor thread, so marshal back.
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    busy = false
                                    if (err != null) error = err.message
                                    else if (bytes != null) onCaptured(bytes)
                                }
                            }

                            override fun onError(exc: ImageCaptureException) {
                                Log.e("Camera", "capture failed", exc)
                                error = exc.message
                                busy = false
                                executor.shutdown()
                            }
                        },
                    )
                },
                onFlip = { lensBack = !lensBack },
                onFlashToggle = { flashOn = !flashOn },
                onGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onClose = onClose,
                lifecycleOwner = lifecycleOwner,
            )
        } else {
            // Waiting on permission / fallback
            Column(
                Modifier.fillMaxSize().padding(HemSpace.gutter),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Camera permission needed",
                    style = HemType.serifSection.copy(color = Color.White),
                )
                Spacer(Modifier.height(HemSpace.sm))
                Text(
                    "You can also pick from your library.",
                    style = HemType.bodyMuted.copy(color = Color.White.copy(alpha = 0.7f)),
                )
            }
        }

        if (error != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 24.dp, end = 24.dp),
            ) {
                Text(error!!, style = HemType.bodyMuted.copy(color = Color(0xFFE0A47A)))
            }
        }
    }
}

@Composable
private fun CameraLivePreview(
    lensBack: Boolean,
    flashOn: Boolean,
    imageCapture: ImageCapture,
    onShutter: () -> Unit,
    onFlip: () -> Unit,
    onFlashToggle: () -> Unit,
    onGallery: () -> Unit,
    onClose: () -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(lensBack, flashOn) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val selector = if (lensBack) CameraSelector.DEFAULT_BACK_CAMERA
                else CameraSelector.DEFAULT_FRONT_CAMERA
                imageCapture.flashMode =
                    if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            } catch (t: Throwable) {
                Log.e("Camera", "bind failed", t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                runCatching { providerFuture.get().unbindAll() }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        FramingOverlay()

        // Top row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(icon = { Icon(Icons.Filled.Close, "Close", tint = Color.White) }, onClick = onClose)
            Spacer(Modifier.weight(1f))
            RoundIconButton(icon = { Icon(Icons.Filled.Cameraswitch, "Flip", tint = Color.White) }, onClick = onFlip)
        }

        // Bottom controls
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIconButton(
                icon = {
                    Icon(
                        if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        "Flash",
                        tint = Color.White,
                    )
                },
                onClick = onFlashToggle,
            )
            ShutterButton(onClick = onShutter)
            RoundIconButton(
                icon = { Icon(Icons.Filled.PhotoLibrary, "Gallery", tint = Color.White) },
                onClick = onGallery,
            )
        }
    }
}

@Composable
private fun FramingOverlay() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val hPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        // 4:5 portrait — width limited by 82% of screen, then height = w * 5/4
        val frameW = wPx * 0.82f
        val frameH = frameW * 5f / 4f
        val maxH = hPx * 0.72f
        val actualH = if (frameH > maxH) maxH else frameH
        val actualW = if (frameH > maxH) actualH * 4f / 5f else frameW
        val left = (wPx - actualW) / 2f
        val top = (hPx - actualH) / 2f - hPx * 0.02f
        val corner = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
        val stroke = with(androidx.compose.ui.platform.LocalDensity.current) { 2.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            // Darkened area outside frame using even-odd fill
            val outer = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
            val inner = Path().apply { addRect(Rect(left, top, left + actualW, top + actualH)) }
            val hole = Path()
            hole.op(outer, inner, PathOperation.Difference)
            drawPath(hole, color = Color(0x66141210))

            // Hairline rectangle
            drawRect(
                color = Color(0x99141210),
                topLeft = Offset(left, top),
                size = GSize(actualW, actualH),
                style = Stroke(width = stroke),
            )

            // Corner brackets
            val bracket = corner
            val bStroke = stroke * 1.8f
            val col = Color.White.copy(alpha = 0.92f)
            // TL
            drawLine(col, Offset(left, top), Offset(left + bracket, top), bStroke)
            drawLine(col, Offset(left, top), Offset(left, top + bracket), bStroke)
            // TR
            drawLine(col, Offset(left + actualW, top), Offset(left + actualW - bracket, top), bStroke)
            drawLine(col, Offset(left + actualW, top), Offset(left + actualW, top + bracket), bStroke)
            // BL
            drawLine(col, Offset(left, top + actualH), Offset(left + bracket, top + actualH), bStroke)
            drawLine(col, Offset(left, top + actualH), Offset(left, top + actualH - bracket), bStroke)
            // BR
            drawLine(col, Offset(left + actualW, top + actualH), Offset(left + actualW - bracket, top + actualH), bStroke)
            drawLine(col, Offset(left + actualW, top + actualH), Offset(left + actualW, top + actualH - bracket), bStroke)
        }
        // Eyebrow under frame
        val labelTop = with(androidx.compose.ui.platform.LocalDensity.current) { (top + actualH).toDp() } + 12.dp
        Box(
            Modifier.fillMaxSize().padding(top = labelTop),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                "FRAME YOUR LOOK",
                style = HemType.smallLabel.copy(
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 3.sp,
                ),
            )
        }
    }
}

@Composable
private fun RoundIconButton(icon: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed) 0.9f else 1f
    Box(
        Modifier
            .size(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((72 * scale).dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(3.dp, HemColors.CardCream, CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size((56 * scale).dp)
                    .clip(CircleShape)
                    .background(HemColors.Ink),
            )
        }
    }
}

@Composable
private fun ReviewCapture(
    preview: ImageBitmap?,
    onRetake: () -> Unit,
    onUse: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(HemColors.Ink)) {
        if (preview != null) {
            androidx.compose.foundation.Image(
                bitmap = preview,
                contentDescription = "Captured",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        FramingOverlay()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 20.dp, end = 20.dp),
        ) {
            RoundIconButton(icon = { Icon(Icons.Filled.Close, "Close", tint = Color.White) }, onClick = onClose)
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Transparent)
                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onRetake),
                contentAlignment = Alignment.Center,
            ) {
                Text("Retake", style = HemType.body.copy(color = Color.White, fontWeight = FontWeight.Medium))
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(HemColors.CardCream)
                    .clickable(onClick = onUse),
                contentAlignment = Alignment.Center,
            ) {
                Text("Use photo", style = HemType.body.copy(color = HemColors.Ink, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

// ---- Bitmap processing helpers ----

/** Convert an ImageProxy (JPEG-encoded) to a rotation-corrected Bitmap. */
private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return bm
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, matrix, true)
}

/**
 * Center-crop to 4:5 portrait, resize longest edge to 2048px, encode JPEG q=85.
 */
private fun processCapturedBitmap(src: Bitmap): ByteArray {
    val cropped = centerCrop45(src)
    val resized = resizeLongestEdge(cropped, 2048)
    val out = ByteArrayOutputStream()
    resized.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray()
}

/** Resize + JPEG-encode arbitrary source bytes (gallery fallback). */
private fun processBitmapBytes(bytes: ByteArray, cropToPortrait: Boolean): ByteArray {
    val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val stage = if (cropToPortrait) centerCrop45(bm) else bm
    val resized = resizeLongestEdge(stage, 2048)
    val out = ByteArrayOutputStream()
    resized.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray()
}

private fun centerCrop45(src: Bitmap): Bitmap {
    val targetRatio = 4f / 5f // width / height
    val srcRatio = src.width.toFloat() / src.height.toFloat()
    return if (srcRatio > targetRatio) {
        // too wide → crop sides
        val newW = (src.height * targetRatio).toInt()
        val x = (src.width - newW) / 2
        Bitmap.createBitmap(src, x, 0, newW, src.height)
    } else {
        // too tall → crop top/bottom
        val newH = (src.width / targetRatio).toInt()
        val y = (src.height - newH) / 2
        Bitmap.createBitmap(src, 0, y, src.width, newH)
    }
}

private fun resizeLongestEdge(src: Bitmap, max: Int): Bitmap {
    val longest = maxOf(src.width, src.height)
    if (longest <= max) return src
    val scale = max.toFloat() / longest.toFloat()
    val nw = (src.width * scale).toInt()
    val nh = (src.height * scale).toInt()
    return Bitmap.createScaledBitmap(src, nw, nh, true)
}
