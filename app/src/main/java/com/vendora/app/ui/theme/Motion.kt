package com.vendora.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween

/**
 * Vendora's shared motion language.
 *
 * Every animated screen pulls its timing/easing/spring values from here
 * instead of picking one-off numbers, so a snappy list-entrance feels the
 * same in Inventory as it does in History, and a "settle" feels the same
 * everywhere a total or counter updates.
 */
object Motion {
    // Durations
    const val QUICK = 150
    const val STANDARD = 300
    const val SLOW = 450

    // A gentle, slightly-overshooting easing for things entering the screen
    val EnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val StandardEasing = FastOutSlowInEasing

    fun <T> enterTween() = tween<T>(durationMillis = STANDARD, easing = EnterEasing)
    fun <T> quickTween() = tween<T>(durationMillis = QUICK, easing = StandardEasing)
    fun <T> standardTween() = tween<T>(durationMillis = STANDARD, easing = StandardEasing)

    /** Bouncy-but-controlled spring for numbers, chips, and press feedback. */
    fun <T> bouncy() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Snappier, less bouncy spring for larger surfaces (cards, sheets). */
    fun <T> smooth() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
