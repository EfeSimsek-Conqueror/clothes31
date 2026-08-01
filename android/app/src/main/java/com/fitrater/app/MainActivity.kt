package com.fitrater.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitrater.app.data.Supa
import com.fitrater.app.data.billing.RcBilling
import com.fitrater.app.data.repo.Repo
import com.fitrater.app.nav.Route
import com.fitrater.app.ui.components.HemBottomNav
import com.fitrater.app.ui.screens.CameraCaptureScreen
import com.fitrater.app.ui.screens.CreditsSheetContent
import com.fitrater.app.ui.screens.HomeScreen
import com.fitrater.app.ui.screens.JournalScreen
import com.fitrater.app.ui.screens.Onboard1Screen
import com.fitrater.app.ui.screens.Onboard2Screen
import com.fitrater.app.ui.screens.PaywallScreen
import com.fitrater.app.ui.screens.PlaceholderSheetContent
import com.fitrater.app.ui.screens.ScoreDetailScreen
import com.fitrater.app.ui.screens.ScoreSheetScreen
import com.fitrater.app.ui.screens.SignInScreen
import com.fitrater.app.ui.screens.SplashScreen
import com.fitrater.app.ui.screens.StudioCreateScreen
import com.fitrater.app.ui.screens.StudioScreen
import com.fitrater.app.ui.screens.YouSheetContent
import com.fitrater.app.ui.theme.HemColors
import com.fitrater.app.util.ToastBus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

private const val PAGE_HOME = 0
private const val PAGE_STUDIO = 1
private const val PAGE_JOURNAL = 2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Global uncaught exception logger — always log; always rethrow to preserve default behavior.
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("FitraterCrash", "Uncaught on ${thread.name}", throwable)
            } catch (_: Throwable) { /* never crash the crasher */ }
            prev?.uncaughtException(thread, throwable)
        }
        // Handle deep-link auth callback on cold start
        Supa.client.handleDeeplinks(intent)
        setContent { FitraterApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Supa.client.handleDeeplinks(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitraterApp() {
    MaterialTheme(colorScheme = lightColorScheme(background = HemColors.Paper, surface = HemColors.Paper)) {
        Surface(color = HemColors.Paper) {
            val nav = rememberNavController()
            val backStack by nav.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route

            val sessionStatus by Supa.client.auth.sessionStatus.collectAsState()
            val isPro by RcBilling.isPro.collectAsState()

            // Compute start destination synchronously from any already-loaded session,
            // so a returning signed-in user never sees the Splash/SignIn screens flash.
            val initialStart = remember {
                if (Supa.client.auth.currentSessionOrNull() != null) Route.Shell else Route.Splash
            }

            val focusManager = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current

            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                ToastBus.events.collect { msg ->
                    snackbarHostState.showSnackbar(msg)
                }
            }

            // Sheet states
            var showYouSheet by remember { mutableStateOf(false) }
            var showCreditsSheet by remember { mutableStateOf(false) }
            var showStyleProfileSheet by remember { mutableStateOf(false) }
            var showHelpPrivacySheet by remember { mutableStateOf(false) }
            var showCameraMenu by remember { mutableStateOf(false) }
            var placeholderMessage by remember { mutableStateOf<String?>(null) }
            // Context tag threaded into the paywall so it can render a feature-specific hero.
            var paywallContext by remember { mutableStateOf<String?>(null) }
            val openPaywall: (String?) -> Unit = { ctx ->
                paywallContext = ctx
                showCreditsSheet = true
            }

            // Auth routing
            var lastHandledUid by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(sessionStatus) {
                when (val s = sessionStatus) {
                    is SessionStatus.Authenticated -> {
                        val uid = s.session.user?.id
                        if (uid != null && uid != lastHandledUid) {
                            lastHandledUid = uid
                            runCatching { RcBilling.identify(uid) }
                            runCatching { Repo.grantSignupCreditsIfEmpty() }
                            com.fitrater.app.util.CreditsBus.refreshAsync()
                            val profile = runCatching { Repo.currentProfile() }.getOrNull()
                            RcBilling.setServerPro(profile?.is_pro == true)
                            val target = if (profile?.onboarded == true) Route.Shell else Route.Onboard1
                            nav.navigate(target) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }

                            // First-sign-in paywall: skip if we've shown it before, if user is already Pro,
                            // or if they've ever purchased. Otherwise open the credits sheet AFTER navigating.
                            val alreadyShown = runCatching { Repo.paywallShown() }.getOrDefault(true)
                            val isPro = runCatching { RcBilling.refreshCustomerInfo() }
                                .getOrNull()
                                ?.let { RcBilling.isPro(it) } == true
                            val hasPurchased = runCatching { Repo.hasEverPurchased() }.getOrDefault(false)
                            if (!alreadyShown && !isPro && !hasPurchased) {
                                showCreditsSheet = true
                                // Mark as shown eagerly so a killed process doesn't re-trigger.
                                runCatching { Repo.markPaywallShown() }
                            }
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        if (lastHandledUid != null) {
                            lastHandledUid = null
                            runCatching { RcBilling.signOut() }
                            RcBilling.setServerPro(false)
                            com.fitrater.app.util.CreditsBus.clear()
                            nav.navigate(Route.SignIn) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    else -> Unit
                }
            }

            Scaffold(
                containerColor = HemColors.Paper,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(HemColors.Paper)
                        .padding(padding),
                ) {
                    NavHost(navController = nav, startDestination = initialStart) {
                        composable(Route.Splash) {
                            SplashScreen(onContinue = { nav.navigate(Route.SignIn) })
                        }
                        composable(Route.SignIn) {
                            SignInScreen(onAuthed = { /* handled by session LaunchedEffect */ })
                        }
                        composable(Route.Onboard1) { Onboard1Screen(onContinue = { nav.navigate(Route.Onboard2) }) }
                        composable(Route.Onboard2) {
                            Onboard2Screen(onDone = {
                                nav.navigate(Route.Shell) { popUpTo(0) { inclusive = true } }
                            })
                        }
                        composable(Route.Paywall) {
                            PaywallScreen(onContinue = {
                                nav.navigate(Route.Shell) { popUpTo(0) { inclusive = true } }
                            })
                        }
                        composable(Route.Shell) {
                            AppShell(
                                isPro = isPro,
                                onOpenScoreSheet = { showCameraMenu = true },
                                onOpenDetail = { id -> nav.navigate(Route.scoreDetail(id)) },
                                onOpenCreateStudio = { nav.navigate(Route.StudioCreate) },
                                onOpenYou = { showYouSheet = true },
                                onOpenCamera = { nav.navigate(Route.Camera) },
                                onOpenCredits = { showCreditsSheet = true },
                                onOpenPaywall = openPaywall,
                                onDismissKeyboard = {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                },
                            )
                        }
                        composable(
                            Route.ScoreDetail,
                            arguments = listOf(navArgument("outfitId") { type = NavType.StringType }),
                        ) { backStackEntry ->
                            val outfitId = backStackEntry.arguments?.getString("outfitId") ?: ""
                            ScoreDetailScreen(
                                outfitId = outfitId,
                                onScoreALook = { nav.navigate(Route.ScoreSheet) },
                                onClose = { nav.popBackStack() },
                            )
                        }
                        composable(Route.StudioCreate) {
                            val editReq = remember { com.fitrater.app.util.EditRequestBus.consume() }
                            val presetType = editReq?.item?.category?.replaceFirstChar { it.uppercase() }
                            StudioCreateScreen(
                                onBack = { nav.popBackStack() },
                                onOpenPaywall = { nav.navigate(Route.Paywall) },
                                onAddedGoHome = {
                                    nav.navigate(Route.Shell) {
                                        popUpTo(Route.Shell) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                editingPieceId = editReq?.item?.id,
                                presetType = presetType,
                                presetReferenceUrl = editReq?.referenceUrl,
                            )
                        }
                        composable(Route.ScoreSheet) {
                            ScoreSheetScreen(
                                onClose = { nav.popBackStack() },
                                onScored = { outfitId ->
                                    nav.navigate(Route.scoreDetail(outfitId)) {
                                        popUpTo(Route.Shell)
                                    }
                                },
                                onOpenPaywall = { nav.navigate(Route.Paywall) },
                                onOpenCamera = { nav.navigate(Route.Camera) },
                            )
                        }
                        composable(Route.Camera) {
                            CameraCaptureScreen(
                                onClose = { nav.popBackStack() },
                                onCaptured = { bytes ->
                                    com.fitrater.app.util.CameraBus.set(bytes)
                                    nav.popBackStack()
                                },
                            )
                        }
                        composable(Route.TryOn) {
                            com.fitrater.app.ui.screens.camera.TryOnScreen(
                                onClose = { nav.popBackStack() },
                                onOpenPaywall = {
                                    nav.popBackStack()
                                    openPaywall("tryon")
                                },
                                onOpenCamera = { nav.navigate(Route.Camera) },
                                onOpenStudioCreate = { nav.navigate(Route.StudioCreate) },
                            )
                        }
                        composable(Route.Versus) {
                            com.fitrater.app.ui.screens.camera.VersusScreen(
                                onClose = { nav.popBackStack() },
                                onOpenPaywall = { nav.navigate(Route.Paywall) },
                                onOpenCamera = { nav.navigate(Route.Camera) },
                            )
                        }
                        composable(Route.Roast) {
                            com.fitrater.app.ui.screens.camera.RoastScreen(
                                isPro = isPro,
                                onClose = { nav.popBackStack() },
                                onOpenPaywall = {
                                    nav.popBackStack()
                                    openPaywall("brutal")
                                },
                                onOpenCamera = { nav.navigate(Route.Camera) },
                            )
                        }
                        composable(Route.WeeklyLetter) {
                            com.fitrater.app.ui.screens.subpages.WeeklyLetterScreen(
                                isPro = isPro,
                                onClose = { nav.popBackStack() },
                                onOpenPaywall = { openPaywall("letter") },
                            )
                        }
                        composable(Route.Appearance) {
                            com.fitrater.app.ui.screens.subpages.AppearanceScreen(
                                onClose = { nav.popBackStack() },
                            )
                        }
                        composable(Route.ManagePro) {
                            com.fitrater.app.ui.screens.subpages.ManageProScreen(
                                onClose = { nav.popBackStack() },
                                onOpenPlans = {
                                    nav.popBackStack()
                                    showCreditsSheet = true
                                },
                            )
                        }
                        composable(Route.FaqScreen) {
                            com.fitrater.app.ui.screens.subpages.FaqScreen(
                                onClose = { nav.popBackStack() },
                            )
                        }
                        composable(Route.Licenses) {
                            com.fitrater.app.ui.screens.subpages.LicensesScreen(
                                onClose = { nav.popBackStack() },
                            )
                        }
                        composable(Route.Decode) {
                            com.fitrater.app.ui.screens.camera.DecodeScreen(
                                onClose = { nav.popBackStack() },
                                onOpenPaywall = { nav.navigate(Route.Paywall) },
                            )
                        }
                    }
                }
            }

            if (showYouSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()
                ModalBottomSheet(
                    onDismissRequest = { showYouSheet = false },
                    sheetState = sheetState,
                    containerColor = HemColors.Paper,
                ) {
                    YouSheetContent(
                        isPro = isPro,
                        onOpenCredits = {
                            scope.launch {
                                showYouSheet = false
                                showCreditsSheet = true
                            }
                        },
                        onOpenPaywall = {
                            showYouSheet = false
                            openPaywall("brutal")
                        },
                        onOpenStyleProfile = {
                            showYouSheet = false
                            showStyleProfileSheet = true
                        },
                        onOpenHelpPrivacy = {
                            showYouSheet = false
                            showHelpPrivacySheet = true
                        },
                        onOpenWeeklyLetter = {
                            showYouSheet = false
                            nav.navigate(Route.WeeklyLetter)
                        },
                        onOpenAppearance = {
                            showYouSheet = false
                            nav.navigate(Route.Appearance)
                        },
                        onOpenManagePro = {
                            showYouSheet = false
                            nav.navigate(Route.ManagePro)
                        },
                        onSignedOut = {
                            showYouSheet = false
                        },
                    )
                }
            }
            if (showCreditsSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = {
                        showCreditsSheet = false
                        paywallContext = null
                    },
                    sheetState = sheetState,
                    containerColor = HemColors.Paper,
                ) {
                    CreditsSheetContent(
                        onClose = {
                            showCreditsSheet = false
                            paywallContext = null
                        },
                        paywallContext = paywallContext,
                    )
                }
            }
            if (showStyleProfileSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showStyleProfileSheet = false },
                    sheetState = sheetState,
                    containerColor = HemColors.Paper,
                ) {
                    com.fitrater.app.ui.screens.StyleProfileSheetContent(
                        onClose = { showStyleProfileSheet = false },
                    )
                }
            }
            if (showHelpPrivacySheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showHelpPrivacySheet = false },
                    sheetState = sheetState,
                    containerColor = HemColors.Paper,
                ) {
                    com.fitrater.app.ui.screens.HelpPrivacySheetContent(
                        onClose = { showHelpPrivacySheet = false },
                        onSignedOut = { showHelpPrivacySheet = false },
                        onOpenFaq = {
                            showHelpPrivacySheet = false
                            nav.navigate(Route.FaqScreen)
                        },
                        onOpenLicenses = {
                            showHelpPrivacySheet = false
                            nav.navigate(Route.Licenses)
                        },
                    )
                }
            }
            if (showCameraMenu) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showCameraMenu = false },
                    sheetState = sheetState,
                    containerColor = HemColors.Paper,
                ) {
                    com.fitrater.app.ui.screens.camera.CameraMenuSheet(
                        isPro = isPro,
                        onClose = { showCameraMenu = false },
                        onPickScore = {
                            showCameraMenu = false
                            nav.navigate(Route.ScoreSheet)
                        },
                        onPickTryOn = {
                            showCameraMenu = false
                            nav.navigate(Route.TryOn)
                        },
                        onPickVersus = {
                            showCameraMenu = false
                            nav.navigate(Route.Versus)
                        },
                        onPickRoast = {
                            showCameraMenu = false
                            nav.navigate(Route.Roast)
                        },
                        onPickDecode = {
                            showCameraMenu = false
                            nav.navigate(Route.Decode)
                        },
                        onOpenPaywall = {
                            showCameraMenu = false
                            openPaywall("tryon")
                        },
                    )
                }
            }
            val pm = placeholderMessage
            if (pm != null) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { placeholderMessage = null },
                    sheetState = sheetState,
                    containerColor = HemColors.Paper,
                ) {
                    PlaceholderSheetContent(message = pm, onClose = { placeholderMessage = null })
                }
            }
        }
    }
}

@Composable
private fun AppShell(
    isPro: Boolean,
    onOpenScoreSheet: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenCreateStudio: () -> Unit,
    onOpenYou: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenPaywall: (String?) -> Unit,
    onDismissKeyboard: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = PAGE_HOME) { 3 }
    val scope = rememberCoroutineScope()

    val currentTabRoute = when (pagerState.currentPage) {
        PAGE_HOME -> Route.Home
        PAGE_STUDIO -> Route.Studio
        PAGE_JOURNAL -> Route.Journal
        else -> Route.Home
    }

    Scaffold(
        containerColor = HemColors.Paper,
        bottomBar = {
            HemBottomNav(
                currentRoute = currentTabRoute,
                onSelect = { route ->
                    val page = when (route) {
                        Route.Home -> PAGE_HOME
                        Route.Studio -> PAGE_STUDIO
                        Route.Journal -> PAGE_JOURNAL
                        Route.You -> {
                            onOpenYou()
                            return@HemBottomNav
                        }
                        else -> PAGE_HOME
                    }
                    scope.launch { pagerState.animateScrollToPage(page) }
                },
                onCameraClick = onOpenScoreSheet,
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(HemColors.Paper)
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismissKeyboard() })
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PAGE_HOME -> HomeScreen(
                        isPro = isPro,
                        onScoreALook = onOpenScoreSheet,
                        onOpenDetail = onOpenDetail,
                        onOpenJournal = {
                            scope.launch { pagerState.animateScrollToPage(PAGE_JOURNAL) }
                        },
                        onOpenStudio = {
                            scope.launch { pagerState.animateScrollToPage(PAGE_STUDIO) }
                        },
                        onOpenCamera = onOpenCamera,
                        onOpenCredits = onOpenCredits,
                        onOpenPaywall = { onOpenPaywall("letter") },
                    )
                    PAGE_STUDIO -> StudioScreen(
                        onCreateNew = onOpenCreateStudio,
                        onOpenEdit = { item, url ->
                            com.fitrater.app.util.EditRequestBus.set(item, url)
                            onOpenCreateStudio()
                        },
                    )
                    PAGE_JOURNAL -> JournalScreen(onOpenDetail = onOpenDetail)
                }
            }
        }
    }
}
