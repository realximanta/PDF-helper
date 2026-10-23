package xyz.ximanta.pdfhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import xyz.ximanta.pdfhelper.ui.AppNavHost
import xyz.ximanta.pdfhelper.ui.theme.PdfHelperTheme

/**
 * The only Activity in the app.
 *
 * Responsibilities (intentionally limited):
 *   1. Enable edge-to-edge drawing before Compose content inflates.
 *   2. Install the theme.
 *   3. Hand off to AppNavHost — a composable that owns the
 *      Loading → Dashboard → Tool flow.
 *
 * Everything else (routing, loading gate, state) lives in Compose,
 * where it can be previewed and unit-tested without the Activity.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw content behind the system bars. The XML theme (values/themes.xml)
        // already sets transparent bars; this call also handles the API-35+
        // "system-bar protection" toggle and the runtime insets wiring.
        enableEdgeToEdge()

        setContent {
            PdfHelperTheme {
                AppNavHost()
            }
        }
    }
}
