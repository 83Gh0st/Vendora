package com.vendora.app.ui.theme

import androidx.compose.ui.graphics.Color

// Vendora's brand palette: a confident emerald (trust, money, growth — the
// natural color language for a shop/sales app) paired with a warm amber
// accent for energy and calls-to-action. Chosen deliberately over a
// generic indigo/purple "AI demo" palette, and kept to a light, crisp,
// always-on-brand surface rather than following the system's dark mode —
// this is a business tool used in bright shops, not a mood app.

val Primary = Color(0xFF0E9F6E)       // Emerald — primary brand color
val PrimaryLight = Color(0xFF34D399)
val PrimaryDark = Color(0xFF047857)

val Accent = Color(0xFFF59E0B)        // Warm amber — energy, CTAs, highlights
val AccentLight = Color(0xFFFBBF24)
val AccentDark = Color(0xFFD97706)

val BackgroundLight = Color(0xFFF7F8FA)   // Soft, warm off-white
val SurfaceLight = Color(0xFFFFFFFF)      // Solid white cards (crisp, not "glass")
val TextPrimaryLight = Color(0xFF111827)
val TextSecondaryLight = Color(0xFF6B7280)

// Kept only for API compatibility with anything referencing a "dark" slot;
// VendoraTheme forces the light scheme everywhere, so these aren't used.
val BackgroundDark = Color(0xFFF7F8FA)
val SurfaceDark = Color(0xFFFFFFFF)
val TextPrimaryDark = Color(0xFF111827)
val TextSecondaryDark = Color(0xFF6B7280)

// Semantic accents used across Sell/Inventory/History for status & feedback
val Success = Color(0xFF16A34A)
val Warning = Color(0xFFF59E0B)
val Danger = Color(0xFFDC2626)

// Header/branding gradient — a single-hue emerald gradient reads as
// premium and intentional; mixing multiple unrelated hues is what makes
// generated-looking UI feel like generated-looking UI.
val GradientStart = Color(0xFF047857)
val GradientMid = Color(0xFF0E9F6E)
val GradientEnd = Color(0xFF34D399)
