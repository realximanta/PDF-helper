package xyz.ximanta.pdfhelper

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application entry point.
 *
 * Responsibilities (keep this list short — Application is a god-object
 * trap; anything that can be lazy-initialized on first use should be):
 *   1. Initialize the PDFBox resource loader before any Activity starts.
 *   2. Nothing else. No DI container, no analytics, no crash reporter.
 */
class PdfHelperApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Must run BEFORE the first PDFBox call. Loads Standard-14 font
        // metrics + glyph data from assets. Idempotent — safe if called
        // twice, but we call it exactly once here.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
