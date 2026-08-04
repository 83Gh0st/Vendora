package com.vendora.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vendora.app.ui.theme.Accent
import com.vendora.app.ui.theme.BackgroundLight
import com.vendora.app.ui.theme.GradientEnd
import com.vendora.app.ui.theme.GradientMid
import com.vendora.app.ui.theme.GradientStart
import com.vendora.app.ui.theme.Motion
import com.vendora.app.ui.theme.Primary
import com.vendora.app.ui.theme.Success
import com.vendora.app.ui.theme.TextSecondaryLight
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class TutorialPage(
    val icon: ImageVector,
    val accent: Color,
    val title: String,
    val description: String
)

// Icons here deliberately mirror the bottom navigation bar 1:1 (see
// VendoraNavItem in MainActivity) so each page visually maps to the tab
// it's explaining, reinforcing rather than duplicating the app's own UI.
private val tutorialPages = listOf(
    TutorialPage(
        icon = Icons.Filled.Storefront,
        accent = Primary,
        title = "Welcome to Vendora!",
        description = "Your shop, in your pocket. Here's a quick look at everything you can do — you can reopen this anytime from Tools."
    ),
    TutorialPage(
        icon = Icons.Filled.ShoppingCart,
        accent = Primary,
        title = "Sell — ring up sales fast",
        description = "Scan or search a product, adjust the quantity, then check out with Cash, UPI, Split payment, or Khata (credit) — all on one screen."
    ),
    TutorialPage(
        icon = Icons.Filled.List,
        accent = Accent,
        title = "Stock — manage your inventory",
        description = "Add products by scanning a barcode or typing details in by hand. Tap the pencil on any product to update its price, stock, or unit whenever they change."
    ),
    TutorialPage(
        icon = Icons.Filled.Person,
        accent = Primary,
        title = "Khata — customer credit, tracked",
        description = "Sell something on credit and it's logged here automatically. See who owes what, and settle it the moment they pay you back."
    ),
    TutorialPage(
        icon = Icons.Filled.DateRange,
        accent = Accent,
        title = "History — every sale, remembered",
        description = "Browse past sales day by day, track revenue over time, and see exactly what sold and how it was paid for."
    ),
    TutorialPage(
        icon = Icons.Filled.Settings,
        accent = Primary,
        title = "Tools — everything else",
        description = "Sync to the cloud, share your shop's join code with staff, export your inventory, print QR codes, and build restock orders — all from here."
    ),
    TutorialPage(
        icon = Icons.Filled.TaskAlt,
        accent = Success,
        title = "You're all set!",
        description = "Start selling whenever you're ready. Want a refresher later? Reopen this walkthrough anytime from Tools → How Vendora Works."
    )
)

/**
 * Full-screen "How Vendora Works" walkthrough. Used two ways: automatically
 * the first time a new shop owner (or staff member) reaches the main app
 * (see VendoraApp in MainActivity.kt), and on demand from the Tools screen.
 * [onFinish] fires on Skip, Get Started, and the system back gesture alike —
 * they all mean the same thing here: "I'm done looking at this."
 */
@Composable
fun TutorialOverlay(onFinish: () -> Unit) {
    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = BackgroundLight) {
            TutorialContent(onFinish = onFinish)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TutorialContent(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { tutorialPages.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == tutorialPages.lastIndex

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(GradientStart, GradientMid, GradientEnd),
                        start = Offset(0f, 0f),
                        end = Offset(900f, 900f)
                    ),
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text(
                "How Vendora Works",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            if (!isLastPage) {
                TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text("Skip", color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            tutorialPages.indices.forEach { i ->
                val selected = i == pagerState.currentPage
                val width by animateDpAsState(
                    targetValue = if (selected) 22.dp else 8.dp,
                    animationSpec = Motion.bouncy(),
                    label = "dot_width"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(8.dp)
                        .width(width)
                        .background(if (selected) Primary else TextSecondaryLight.copy(alpha = 0.25f), RoundedCornerShape(50))
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val fraction = minOf(1f, abs(pageOffset))
            val scale = lerp(1f, 0.85f, fraction)
            val fade = lerp(1f, 0.4f, fraction)
            TutorialPageContent(
                page = tutorialPages[page],
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = fade
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (pagerState.currentPage > 0) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("Back")
                }
            }

            Button(
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(if (pagerState.currentPage > 0) 2f else 1f)
                    .height(52.dp)
            ) {
                Text(if (isLastPage) "Get Started" else "Next", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TutorialPageContent(page: TutorialPage, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(page.accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = page.accent,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondaryLight,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}
