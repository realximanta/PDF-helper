package xyz.ximanta.pdfhelper.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary                  = BrandBlue600,
    onPrimary                = OnPrimary,
    primaryContainer         = BrandBlue100,
    onPrimaryContainer       = BrandBlue900,
    secondary                = AccentSky500,
    onSecondary              = OnSecondary,
    secondaryContainer       = AccentSky400,
    onSecondaryContainer     = AccentSky700,
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

private val DarkColors = darkColorScheme(
    primary                  = BrandBlue500,
    onPrimary                = OnPrimary,
    primaryContainer         = BrandBlue700,
    onPrimaryContainer       = BrandBlue100,
    secondary                = AccentSky400,
    onSecondary              = OnSecondary,
    secondaryContainer       = AccentSky700,
    onSecondaryContainer     = AccentSky400,
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

@Composable
fun PdfHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val unusedDynamic = dynamicColor

    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PdfHelperTypography,
        content = content,
    )
}
