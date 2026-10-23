package xyz.ximanta.pdfhelper.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// Brand — trust-blue
// ============================================================
val BrandBlue50  = Color(0xFFEFF6FF)
val BrandBlue100 = Color(0xFFDBEAFE)
val BrandBlue500 = Color(0xFF3B82F6)
val BrandBlue600 = Color(0xFF2563EB)   // primary light
val BrandBlue700 = Color(0xFF1D4ED8)   // pressed
val BrandBlue900 = Color(0xFF1E3A8A)

// Sky accent — secondary chips, links, focus rings
val AccentSky400 = Color(0xFF38BDF8)
val AccentSky500 = Color(0xFF0EA5E9)
val AccentSky700 = Color(0xFF0369A1)

// ============================================================
// Slate neutrals — surfaces, text, dividers
// ============================================================
val Slate50  = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)
val Slate950 = Color(0xFF020617)

// ============================================================
// Semantic — light theme
// (names map to MaterialTheme.colorScheme slots in Theme.kt)
// ============================================================
val LightBackground       = Slate50
val LightSurface          = Color(0xFFFFFFFF)
val LightSurfaceVariant   = Slate100
val LightOnBackground     = Slate900
val LightOnSurfaceVariant = Slate600
val LightOutline          = Slate300

// ============================================================
// Semantic — dark theme
// ============================================================
val DarkBackground       = Slate950
val DarkSurface          = Slate900
val DarkSurfaceVariant   = Slate800
val DarkOnBackground     = Slate200
val DarkOnSurfaceVariant = Slate400
val DarkOutline          = Slate700

// ============================================================
// Status — shared hue, separate containers per mode
// ============================================================
val Success              = Color(0xFF16A34A)
val SuccessContainerLight = Color(0xFFDCFCE7)
val SuccessContainerDark  = Color(0xFF14532D)

val Warning              = Color(0xFFD97706)
val WarningContainerLight = Color(0xFFFEF3C7)
val WarningContainerDark  = Color(0xFF78350F)

val ErrorRed             = Color(0xFFDC2626)
val ErrorContainerLight  = Color(0xFFFEE2E2)
val ErrorContainerDark   = Color(0xFF7F1D1D)

// ============================================================
// On-colors — foreground over solid brand/status surfaces
// ============================================================
val OnPrimary   = Color(0xFFFFFFFF)
val OnSecondary = Color(0xFF001E30)
val OnError     = Color(0xFFFFFFFF)
val OnSuccess   = Color(0xFFFFFFFF)
val OnWarning   = Color(0xFF1F1300)
