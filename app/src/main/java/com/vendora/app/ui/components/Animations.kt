package com.vendora.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.vendora.app.ui.theme.Motion
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Wraps [content] so it fades + slides up into place a little after the
 * screen appears, with [index] controlling the stagger delay. Used for
 * cards/list rows across Settings, Inventory, History, and Ledger so a
 * screen's sections cascade in together instead of popping in all at once.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    baseDelayMillis: Long = 50L,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(baseDelayMillis * index)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.enterTween()) + slideInVertically(Motion.enterTween()) { it / 5 },
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Animates numeric changes by counting smoothly from the old value to the
 * new one, instead of the text just snapping — used for revenue totals and
 * other headline numbers so an update reads as "the number grew" rather
 * than "the screen changed".
 */
@Composable
fun AnimatedCounter(
    value: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    prefix: String = "\u20B9",
    decimals: Int = 2
) {
    val displayed by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = Motion.smooth(),
        label = "counter"
    )
    Text(
        text = "$prefix${String.format(Locale.US, "%,.${decimals}f", displayed)}",
        style = style,
        color = color,
        modifier = modifier
    )
}

/**
 * A modifier that gives any tappable surface a subtle "press down" scale —
 * small, but it's what makes buttons and cards feel responsive rather than
 * static. Apply to a Modifier chain alongside your own clickable/onClick.
 */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = Motion.bouncy(),
        label = "press_scale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
