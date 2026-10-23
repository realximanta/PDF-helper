package xyz.ximanta.pdfhelper.io

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * SAF + FileProvider helpers. Every tool uses these.
 * No storage permissions needed — SAF URIs only.
 */
object FileIo {

    // ---- MIME presets for OpenDocument / CreateDocument ----
    val PDF_MIMES   = arrayOf("application/pdf")
    val IMAGE_MIMES = arrayOf("image/*")
    val TEXT_MIMES  = arrayOf("text/*", "text/plain", "text/markdown")
    val PDF_MIME    = "application/pdf"

    // ---- Cache dir used for share-sheet staging ----
    private fun sharedCacheDir(context: Context): File =
        File(context.cacheDir, "shared").apply { if (!exists()) mkdirs() }

    private fun outputsDir(context: Context): File =
        File(context.filesDir, "outputs").apply { if (!exists()) mkdirs() }

    // ============================================================
    // Read / write
    // ============================================================
    fun readBytes(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot open $uri")
    }

    fun writeBytes(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IOException("Cannot write $uri")
    }

    // ============================================================
    // Name suggestion
    // ============================================================
    fun displayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }

    fun withSuffix(name: String?, suffix: String): String {
        val base = name?.substringBeforeLast('.') ?: "document"
        return "$base$suffix.pdf"
    }

    // ============================================================
    // Share / open via FileProvider
    // ============================================================
    fun shareableUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

    /** Write bytes to cache/shared/ and return a FileProvider content:// URI. */
    fun stageForShare(context: Context, fileName: String, bytes: ByteArray): Uri {
        val target = File(sharedCacheDir(context), fileName)
        target.writeBytes(bytes)
        return shareableUri(context, target)
    }

    /** Write bytes to files/outputs/ (app-private persistent) and return File. */
    fun persistOutput(context: Context, fileName: String, bytes: ByteArray): File {
        val target = File(outputsDir(context), fileName)
        target.writeBytes(bytes)
        return target
    }

    // ============================================================
    // Contract helpers for Compose
    // ============================================================
    fun suggestPdfName(prefix: String = "pdf-helper"): String = "$prefix.pdf"
}
