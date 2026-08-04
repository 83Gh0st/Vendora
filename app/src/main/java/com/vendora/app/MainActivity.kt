package com.vendora.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vendora.app.data.SupabaseSyncManager
import com.vendora.app.data.TutorialPreferences
import com.vendora.app.data.auth.AuthViewModel
import com.vendora.app.data.auth.LocalSession
import com.vendora.app.ui.components.TutorialOverlay
import com.vendora.app.ui.screens.AddProductScreen
import com.vendora.app.ui.screens.AuthScreen
import com.vendora.app.ui.screens.HistoryScreen
import com.vendora.app.ui.screens.InventoryScreen
import com.vendora.app.ui.screens.LedgerScreen
import com.vendora.app.ui.screens.SellScreen
import com.vendora.app.ui.screens.SettingsScreen
import com.vendora.app.ui.theme.Motion
import com.vendora.app.ui.theme.VendoraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var barcodeLauncher: ActivityResultLauncher<Intent>
    private var scanCallback: ((String?) -> Unit)? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        barcodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val scanResult = result.data?.getStringExtra("SCAN_RESULT")
                if (scanResult == null) {
                    Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
                    scanCallback?.invoke(null)
                } else {
                    Toast.makeText(this, "Scanned: $scanResult", Toast.LENGTH_SHORT).show()
                    scanCallback?.invoke(scanResult)
                }
            } else {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
                scanCallback?.invoke(null)
            }
        }

        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val session by authViewModel.session.collectAsState()

            // The native splash only needs to cover the instant it takes to
            // read the locally-stored session — there's no network round
            // trip to wait for before we know which screen to show.
            splashScreen.setKeepOnScreenCondition { false }

            val syncManager = remember { SupabaseSyncManager(this) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(session) {
                if (session != null) {
                    scope.launch { syncManager.pullIfLocalIsEmpty() }
                }
            }

            VendoraTheme {
                AnimatedContent(
                    targetState = session != null,
                    transitionSpec = {
                        (fadeIn(Motion.enterTween()) + slideInHorizontally(Motion.enterTween()) { it / 12 })
                            .togetherWith(fadeOut(Motion.quickTween()))
                    },
                    label = "app_gate"
                ) { isSignedIn ->
                    val currentSession = session
                    if (isSignedIn && currentSession != null) {
                        VendoraApp(mainActivity = this@MainActivity, session = currentSession, authViewModel = authViewModel)
                    } else {
                        AuthScreen(authViewModel = authViewModel)
                    }
                }
            }
        }
    }

    fun launchScanner(callback: (String?) -> Unit) {
        scanCallback = callback
        val intent = Intent(this, ScannerActivity::class.java)
        barcodeLauncher.launch(intent)
    }
}

/** The signed-in app: bottom-tab navigation + all the shop-management screens. */
@Composable
private fun VendoraApp(mainActivity: MainActivity, session: LocalSession, authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val context = androidx.compose.ui.platform.LocalContext.current
    val tutorialPrefs = remember { TutorialPreferences(context) }
    var showTutorial by remember { mutableStateOf(false) }
    // First time this device reaches the main app for this shop — whether
    // that's an owner right after registering or staff right after joining
    // — walk them through it once. See TutorialPreferences for the "seen"
    // bookkeeping; the on-demand replay in Tools bypasses this entirely.
    LaunchedEffect(session.shopId) {
        if (!tutorialPrefs.hasSeenTutorial(session.shopId)) {
            showTutorial = true
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute != "add_product?barcode={barcode}") {
                NavigationBar {
                    VendoraNavItem(
                        icon = Icons.Filled.ShoppingCart,
                        label = "Sell",
                        selected = currentRoute == "sell",
                        onClick = { navController.navigateToTab("sell") }
                    )
                    VendoraNavItem(
                        icon = Icons.Filled.List,
                        label = "Stock",
                        selected = currentRoute == "inventory",
                        onClick = { navController.navigateToTab("inventory") }
                    )
                    VendoraNavItem(
                        icon = Icons.Filled.Person,
                        label = "Khata",
                        selected = currentRoute == "ledger",
                        onClick = { navController.navigateToTab("ledger") }
                    )
                    VendoraNavItem(
                        icon = Icons.Filled.DateRange,
                        label = "History",
                        selected = currentRoute == "history",
                        onClick = { navController.navigateToTab("history") }
                    )
                    VendoraNavItem(
                        icon = Icons.Filled.Settings,
                        label = "Tools",
                        selected = currentRoute == "settings",
                        onClick = { navController.navigateToTab("settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "sell",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                "ledger",
                enterTransition = { fadeIn(Motion.standardTween()) },
                exitTransition = { fadeOut(Motion.quickTween()) }
            ) { LedgerScreen() }

            composable(
                "history",
                enterTransition = { fadeIn(Motion.standardTween()) },
                exitTransition = { fadeOut(Motion.quickTween()) }
            ) { HistoryScreen() }

            composable(
                "settings",
                enterTransition = { fadeIn(Motion.standardTween()) },
                exitTransition = { fadeOut(Motion.quickTween()) }
            ) { SettingsScreen(authViewModel = authViewModel) }

            composable(
                "sell",
                enterTransition = { fadeIn(Motion.standardTween()) },
                exitTransition = { fadeOut(Motion.quickTween()) }
            ) {
                SellScreen(
                    mainActivity = mainActivity,
                    onNavigateToAddProduct = { barcode ->
                        navController.navigate("add_product?barcode=$barcode")
                    }
                )
            }

            composable(
                "inventory",
                enterTransition = { fadeIn(Motion.standardTween()) },
                exitTransition = { fadeOut(Motion.quickTween()) }
            ) {
                InventoryScreen(onNavigateToAddProduct = { navController.navigate("add_product") })
            }

            composable(
                route = "add_product?barcode={barcode}",
                arguments = listOf(navArgument("barcode") {
                    type = NavType.StringType
                    nullable = true
                }),
                enterTransition = { slideInHorizontally(Motion.standardTween()) { it } + fadeIn(Motion.standardTween()) },
                exitTransition = { fadeOut(Motion.quickTween()) },
                popExitTransition = { slideOutHorizontally(Motion.standardTween()) { it } + fadeOut(Motion.standardTween()) }
            ) { backStackEntry ->
                val barcode = backStackEntry.arguments?.getString("barcode")
                AddProductScreen(
                    mainActivity = mainActivity,
                    initialBarcode = barcode,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (showTutorial) {
        TutorialOverlay(
            onFinish = {
                tutorialPrefs.markTutorialSeen(session.shopId)
                showTutorial = false
            }
        )
    }
}

private fun androidx.navigation.NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** A bottom-nav item whose icon gently scales up when selected. */
@Composable
private fun RowScope.VendoraNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = Motion.bouncy(),
        label = "nav_icon_scale"
    )
    NavigationBarItem(
        icon = {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            )
        },
        label = { Text(label) },
        selected = selected,
        onClick = onClick
    )
}
