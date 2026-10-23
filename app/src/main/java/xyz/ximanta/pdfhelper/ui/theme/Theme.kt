package xyz.ximanta.pdfhelper.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================
// Light color scheme — every slot explicitly assigned.
// Unassigned slots fall back to Material's purple baseline, so we
// assign all 30+ of them. Missing slots = purple leaks in dark mode.
// ============================================================
private val LightColors = lightColorScheme(
    primary                  = BrandBlue600,
    onPrimary                = OnPrimary,
    primaryContainer         = BrandBlue100,
    onPrimaryContainer       = BrandBlue900,

    secondary                = AccentSky500,
    onSecondary              = OnSecondary,
    secondaryContainer      = AccentSky400,
    onSecondaryContainer    = AccentSky700,

    tertiary                 = BrandBlue500,
    onTertiary               = OnPrimary,
    tertiaryContainer        = BrandBlue50,
    onTertiaryContainer      = BrandBlue700,

    background               = LightBackground,
    onBackground             = LightOnBackground,

    surface                  = LightSurface,
    onSurface                = LightOnBackground,
    surfaceVariant           = LightSurfaceVariant,
    onSurfaceVariant         = LightOnSurfaceVariant,
    surfaceTint              = BrandBlue600,

    inverseSurface           = Slate800,
    inverseOnSurface         = Slate100,
    inversePrimary           = BrandBlue500,

    outline                  = LightOutline,
    outlineVariant           = Slate200,

    error                    = ErrorRed,
    onError                  = OnError,
    errorContainer           = ErrorContainerLight,
    onErrorContainer         = ErrorRed,

    scrim                    = Slate950,
)

// ============================================================
// Dark color scheme — mirrors the light scheme slot-for-slot.
// Brand hues stay the same; only surface/onSurface swap to slate darks.
// ============================================================
private val DarkColors = darkColorScheme(
    primary                  = BrandBlue500,
    onPrimary                = OnPrimary,
    primaryContainer         = BrandBlue700,
    onPrimaryContainer       = BrandBlue100,

    secondary                = AccentSky400,
    onSecondary              = OnSecondary,
    secondaryContainer      = AccentSky700,
    onSecondaryContainer    = AccentSky400,

    tertiary                 = BrandBlue500,
    onTertiary               = OnPrimary,
    tertiaryContainer        = BrandBlue900,
    onTertiaryContainer      = BrandBlue100,

    background               = DarkBackground,
    onBackground             = DarkOnBackground,

    surface                  = DarkSurface,
    onSurface                = DarkOnBackground,
    surfaceVariant           = DarkSurfaceVariant,
    onSurfaceVariant         = DarkOnSurfaceVariant,
    surfaceTint              = BrandBlue500,

    inverseSurface           = Slate100,
    inverseOnSurface         = Slate800,
    inversePrimary           = BrandBlue700,

    outline                  = DarkOutline,
    outlineVariant           = Slate800,

    error                    = ErrorRed,
    onError                  = OnError,
    errorContainer           = ErrorContainerDark,
    onErrorContainer         = ErrorRed,

    scrim                    = Slate950,
)

// ============================================================
// PdfHelperTheme — the only theme wrapper used by every screen.
//
//   @Composable fun MyScreen() {
//       PdfHelperTheme {
//           ...
//       }
//   }
//
// Dynamic color (Material You) is OFF by default: the app is a
// document-utility — users expect the same trust-blue identity
// everywhere. Dynamic is exposed as a param for future opt-in.
// ============================================================
@Composable
fun PdfHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Fall back to our brand palettes. Dynamic-color path intentionally
    // disabled — see doc comment above.
    @Suppress("UNUSED_VARIABLE")
    val unusedDynamic = dynamicColor

    val colorScheme = if (darkTheme) DarkColors else LightColors

    // Sync the status-bar icons with the active theme.
    // Without this, light mode shows white-on-white icons and vice versa.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PdfHelperTypography,
        content = content,
    )
}
