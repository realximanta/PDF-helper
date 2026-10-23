package xyz.ximanta.pdfhelper.ui.screen.tools

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.ximanta.pdfhelper.R
import xyz.ximanta.pdfhelper.io.FileIo
import xyz.ximanta.pdfhelper.pdf.PdfEngine
import xyz.ximanta.pdfhelper.ui.components.EmptyPickState
import xyz.ximanta.pdfhelper.ui.components.PrimaryActionButton
import xyz.ximanta.pdfhelper.ui.components.ProcessingOverlay
import xyz.ximanta.pdfhelper.ui.components.ResultBanner
import xyz.ximanta.pdfhelper.ui.components.SecondaryActionButton
import xyz.ximanta.pdfhelper.ui.components.ToolScaffold
import java.io.ByteArrayOutputStream

enum class ImageFormat(val label: String, val mime: String) {
    PNG("PNG", "image/png"),
    JPEG("JPEG", "image/jpeg"),
}

data class PdfToImagesUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val rangeInput: String = "",
    val onlyRange: Boolean = false,
    val scale: Int = 2,                 // 1..3
    val format: ImageFormat = ImageFormat.PNG,
    val isWorking: Boolean = false,
    val resultUris: List<Uri> = emptyList(),
    val error: String? = null,
)

class PdfToImagesViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PdfToImagesUiState())
    val state: StateFlow<PdfToImagesUiState> = _state.asStateFlow()

    fun loadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    sourceUri = uri,
                    sourceName = FileIo.displayName(context, uri) ?: "document.pdf",
                    isWorking = true,
                    error = null,
                    resultUris = emptyList(),
                    pageCount = 0,
                )
            }
            try {
                val count = withContext(Dispatchers.IO) {
                    val bytes = FileIo.readBytes(context, uri)
                    val doc = PdfEngine.load(bytes)
                    try { PdfEngine.pageCount(doc) } finally { doc.close() }
                }
                _state.update { it.copy(isWorking = false, pageCount = count) }
            } catch (e: PdfEngine.PdfError) {
                _state.update { it.copy(isWorking = false, error = messageFor(e), sourceUri = null) }
            } catch (e: Throwable) {
                _state.update { it.copy(isWorking = false, error = e.message ?: "Cannot read file", sourceUri = null) }
            }
        }
    }

    fun setOnlyRange(v: Boolean) = _state.update { it.copy(onlyRange = v) }
    fun setRange(t: String) = _state.update { it.copy(rangeInput = t) }
    fun setScale(s: Int) = _state.update { it.copy(scale = s.coerceIn(1, 3)) }
    fun setFormat(f: ImageFormat) = _state.update { it.copy(format = f) }

    fun render() {
        val snap = _state.value
        val src = snap.sourceUri ?: return
        if (snap.isWorking) return

        val pageIdxs: List<Int> = if (snap.onlyRange) {
            parseRanges(snap.rangeInput, snap.pageCount).map { it - 1 }
        } else {
            (0 until snap.pageCount).toList()
        }
        if (pageIdxs.isEmpty()) {
            _state.update { it.copy(error = "Enter a valid range like 1-3, 5") }
            return
        }

        _state.update { it.copy(isWorking = true, error = null, resultUris = emptyList()) }
        viewModelScope.launch {
            try {
                val uris = withContext(Dispatchers.IO) {
                    renderPages(
                        getApplication(), src, pageIdxs,
                        snap.scale, snap.format, snap.sourceName,
                    )
                }
                _state.update { it.copy(isWorking = false, resultUris = uris) }
            } catch (e: Throwable) {
                _state.update { it.copy(isWorking = false, error = e.message ?: "Render failed") }
            }
        }
    }

    private fun renderPages(
        context: Context,
        src: Uri,
        pages: List<Int>,
        scale: Int,
        format: ImageFormat,
        baseName: String,
    ): List<Uri> {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(src, "r")
            ?: throw IllegalStateException("Cannot open PDF")
        pfd.use {
            PdfRenderer(pfd).use { renderer ->
                val result = ArrayList<Uri>(pages.size)
                val stem = baseName.substringBeforeLast('.')
                pages.forEach { idx ->
                    if (idx !in 0 until renderer.pageCount) return@forEach
                    renderer.openPage(idx).use { page ->
                        val w = page.width * scale
                        val h = page.height * scale
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val out = ByteArrayOutputStream()
                        val isJpeg = format == ImageFormat.JPEG
                        bmp.compress(
                            if (isJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                            85,
                            out,
                        )
                        bmp.recycle()

                        val ext = if (isJpeg) "jpg" else "png"
                        val name = "$stem-page-${idx + 1}.$ext"
                        result += FileIo.stageForShare(context, name, out.toByteArray())
                    }
                }
                return result
            }
        }
    }

    private fun messageFor(e: PdfEngine.PdfError): String = when (e) {
        is PdfEngine.PdfError.NeedsPassword -> "PDF is password-protected. Unlock it first."
        is PdfEngine.PdfError.WrongPassword -> "Incorrect password."
        is PdfEngine.PdfError.Corrupt -> "PDF is damaged or unreadable."
        is PdfEngine.PdfError.Io -> "Could not read the file."
    }

    private fun parseRanges(input: String, pageCount: Int): List<Int> {
        if (input.isBlank()) return emptyList()
        val result = mutableListOf<Int>()
        input.split(',', ' ').filter { it.isNotBlank() }.forEach { part ->
            val dash = part.indexOf('-')
            try {
                if (dash > 0) {
                    val a = part.substring(0, dash).trim().toInt()
                    val b = part.substring(dash + 1).trim().toInt()
                    for (n in minOf(a, b)..maxOf(a, b)) if (n in 1..pageCount) result += n
                } else {
                    val n = part.trim().toInt()
                    if (n in 1..pageCount) result += n
                }
            } catch (_: NumberFormatException) {
            }
        }
        return result.distinct().sorted()
    }
}

@Composable
fun PdfToImagesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PdfToImagesViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.loadFile(context, it) } }

    ToolScaffold(
        title = stringResource(R.string.tool_pdf_to_images),
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            if (state.sourceUri != null) {
                Column(Modifier.padding(16.dp)) {
                    if (state.resultUris.isNotEmpty()) {
                        SecondaryActionButton(
                            text = "Share all (${state.resultUris.size})",
                            onClick = { shareImages(context, state.resultUris) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    PrimaryActionButton(
                        text = stringResource(R.string.action_apply),
                        enabled = !state.isWorking,
                        onClick = { vm.render() },
                    )
                }
            }
        },
    ) { innerMod ->
        Box(innerMod) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                if (state.sourceUri == null) {
                    EmptyPickState(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = stringResource(R.string.tool_pdf_to_images),
                        subtitle = "Pick a PDF, then render pages as PNG or JPEG images.",
                    )
                }

                Button(
                    onClick = { picker.launch(FileIo.PDF_MIMES) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_choose_file))
                }

                if (state.sourceUri != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = state.sourceName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${state.pageCount} pages",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !state.onlyRange,
                            onClick = { vm.setOnlyRange(false) },
                            label = { Text("All pages") },
                        )
                        FilterChip(
                            selected = state.onlyRange,
                            onClick = { vm.setOnlyRange(true) },
                            label = { Text("Custom range") },
                        )
                    }

                    if (state.onlyRange) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.rangeInput,
                            onValueChange = vm::setRange,
                            label = { Text("Pages (e.g. 1-3, 5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Resolution",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "1x", 2 to "2x", 3 to "3x").forEach { (s, label) ->
                            FilterChip(
                                selected = state.scale == s,
                                onClick = { vm.setScale(s) },
                                label = { Text(label) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Format",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImageFormat.entries.forEach { f ->
                            FilterChip(
                                selected = state.format == f,
                                onClick = { vm.setFormat(f) },
                                label = { Text(f.label) },
                            )
                        }
                    }
                }

                state.error?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    ResultBanner(success = false, message = msg)
                }
                if (state.resultUris.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ResultBanner(
                        success = true,
                        message = "Rendered ${state.resultUris.size} image(s) — tap Share all to save",
                    )
                }
            }

            if (state.isWorking) ProcessingOverlay()
        }
    }
}

private fun shareImages(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uris[0])
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share images"))
}
