package com.fitrater.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitrater.app.data.Supa
import com.fitrater.app.data.auth.GoogleAuth
import com.fitrater.app.data.model.ProfileUpsert
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.ui.theme.Eyebrow
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.ui.theme.HemSpace
import com.fitrater.app.ui.theme.HemType
import com.fitrater.app.ui.theme.OutlinedPill
import com.fitrater.app.ui.theme.PrimaryButton
import com.fitrater.app.ui.theme.SerifDisplay
import com.fitrater.app.util.userMessage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(onAuthed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showEmailSheet by remember { mutableStateOf(false) }
    var emailSent by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        Modifier
            .fillMaxSize()
            .background(HemColors.Paper)
            .padding(horizontal = HemSpace.gutter, vertical = HemSpace.xl),
    ) {
        Spacer(Modifier.height(HemSpace.xxl))
        Eyebrow("FITRATER")
        Spacer(Modifier.height(HemSpace.md))
        SerifDisplay("Sign in.")
        Spacer(Modifier.height(HemSpace.md))
        Text(
            "Your closet and journal follow you across devices.",
            style = HemType.bodyMuted,
        )
        Spacer(Modifier.height(HemSpace.xxl))

        OutlinedPill(
            label = if (busy) "Signing in…" else "Continue with Google",
            onClick = {
                if (busy) return@OutlinedPill
                busy = true
                error = null
                scope.launch {
                    val result = GoogleAuth.signIn(context)
                    result.onSuccess { displayName ->
                        // upsert profile row with google name/email
                        val user = Supa.client.auth.currentUserOrNull()
                        if (user != null) {
                            runCatching {
                                Repo.upsertProfile(
                                    ProfileUpsert(
                                        id = user.id,
                                        email = user.email,
                                        display_name = displayName ?: user.email?.substringBefore("@"),
                                    ),
                                )
                            }
                        }
                        onAuthed()
                    }.onFailure {
                        error = it.userMessage("Google sign-in failed. Please try again.")
                    }
                    busy = false
                }
            },
            filled = true,
            leading = {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        // Google's mark keeps its own colours in both themes, so the
                        // glyph on it must stay dark rather than following Ink.
                        .background(Color.White)
                        .border(1.dp, HemColors.Hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "G",
                        style = HemType.body.copy(
                            color = Color(0xFF141210),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        ),
                    )
                }
            },
        )
        Spacer(Modifier.height(HemSpace.sm))
        OutlinedPill(
            label = "Continue with email",
            onClick = {
                showEmailSheet = true
                emailSent = false
                error = null
            },
        )

        if (error != null) {
            Spacer(Modifier.height(HemSpace.sm))
            Text(
                error!!,
                style = HemType.bodyMuted.copy(color = HemColors.Bronze, fontSize = 13.sp),
            )
        }

        if (showEmailSheet) {
            Spacer(Modifier.height(HemSpace.lg))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HemColors.CardCream)
                    .border(1.dp, HemColors.Hairline, RoundedCornerShape(14.dp))
                    .padding(HemSpace.md),
            ) {
                Text(
                    if (emailSent) "Check your email" else "Enter your email",
                    style = HemType.body.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(HemSpace.xs))
                Text(
                    if (emailSent)
                        "We just sent you a magic link. Open it on this device."
                    else
                        "We'll send you a magic link — no password.",
                    style = HemType.bodyMuted.copy(fontSize = 13.sp),
                )
                if (!emailSent) {
                    Spacer(Modifier.height(HemSpace.sm))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(HemColors.Surface)
                            .border(1.dp, HemColors.Hairline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            singleLine = true,
                            textStyle = HemType.body,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (emailInput.text.isEmpty()) {
                            Text(
                                "you@domain.com",
                                style = HemType.bodyMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(HemSpace.sm))
                    PrimaryButton(
                        label = if (busy) "Sending…" else "Send link",
                        onClick = {
                            if (busy) return@PrimaryButton
                            val em = emailInput.text.trim()
                            if (!em.contains("@")) {
                                error = "Enter a valid email"
                                return@PrimaryButton
                            }
                            busy = true
                            error = null
                            scope.launch {
                                runCatching {
                                    Supa.client.auth.signInWith(OTP) {
                                        email = em
                                    }
                                }.onSuccess { emailSent = true }
                                    .onFailure { error = it.userMessage("Couldn't send the link. Please try again.") }
                                busy = false
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "By continuing you agree to our Terms and Privacy Policy.",
            style = HemType.bodyMuted.copy(fontSize = 12.sp),
        )
        Spacer(Modifier.height(HemSpace.md))
    }
}
