package com.fitrater.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import com.fitrater.app.ui.theme.HemColors

/** Hosts our manifest claims with an autoVerify'd App Links filter. */
private val APP_LINK_HOSTS = setOf("fitrater.ai", "www.fitrater.ai")

/** Any http host that is not ours, used only to ask the package manager who the browsers are. */
private val BROWSER_PROBE: Uri = Uri.parse("http://non-existent-fitrater-probe.invalid")

/**
 * Open an external http(s) link in a Chrome Custom Tab.
 *
 * A CustomTabsIntent is not enough on its own for our own hosts: the builder only ever calls
 * setPackage() from setSession(), and launchUrl() just does setData() + startActivity(). With no
 * session the launched intent is still implicit, so our autoVerify'd fitrater.ai App Links filter
 * wins it, the system hands it back to MainActivity (singleTask), handleDeeplinks() ignores a
 * non-auth URL and the tap does nothing — and ActivityNotFoundException never fires, so the old
 * fallback was dead code.
 *
 * For those hosts we therefore pin an explicit package before launching. Other hosts keep the
 * implicit launch so native handlers (the Play Store app) still win their own links.
 */
fun openExternal(context: Context, url: String) {
    val uri = Uri.parse(url)
    val colors = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(HemColors.Paper.toArgb())
        .setSecondaryToolbarColor(HemColors.Ink.toArgb())
        .build()

    val customTabs = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setDefaultColorSchemeParams(colors)
        .build()
    customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // Needs the <queries> CustomTabsService entry in the manifest, or this is always null on API 30+.
    val provider = CustomTabsClient.getPackageName(context, null)
    val mustPinPackage = uri.host?.lowercase() in APP_LINK_HOSTS

    if (provider != null) {
        customTabs.intent.setPackage(provider)
        if (runCatching { customTabs.launchUrl(context, uri) }.isSuccess) return
    } else if (!mustPinPackage) {
        // No Custom Tabs provider, but this link is safe to resolve implicitly.
        try {
            customTabs.launchUrl(context, uri)
            return
        } catch (_: ActivityNotFoundException) {
            // fall through to an explicit browser
        }
    }

    val browser = resolveBrowserPackage(context)
    if (browser != null) {
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .setPackage(browser)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (opened) return
    }

    ToastBus.post("Couldn't open link — no browser on this device.")
}

/**
 * The package of a browser that isn't us. Probed with a foreign host so our own fitrater.ai App
 * Links filter can never match, and self/chooser packages are dropped defensively.
 */
private fun resolveBrowserPackage(context: Context): String? {
    val probe = Intent(Intent.ACTION_VIEW, BROWSER_PROBE)
        .addCategory(Intent.CATEGORY_BROWSABLE)

    @Suppress("DEPRECATION")
    val candidates = runCatching {
        context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrDefault(emptyList())

    return candidates
        .mapNotNull { it.activityInfo?.packageName }
        .firstOrNull { it != context.packageName && it != "android" }
}
