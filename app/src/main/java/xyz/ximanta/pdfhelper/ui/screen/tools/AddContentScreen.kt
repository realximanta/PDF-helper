package xyz.ximanta.pdfhelper.ui.screen.tools

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
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

enum class ContentTarget { ALL_PAGES, FIRST_PAGE, LAST_PAGE, SPECIFIC }
enum class ContentSlot(val label: String) {
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right"),
    CENTER("Center"),
}

data class AddContentUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    // Text
    val text: String = "",
    val fontSize: Int = 14,
    // Image
    val imageUri: Uri? = null,
    val imageName: String = "",
    // Placement
    val slot: ContentSlot = ContentSlot.CENTER,
    val target: ContentTarget = ContentTarget.ALL_PAGES,
    val specificPageInput: String = "1",
    // Status
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val resultName: String = "",
    val error: String? = null,
)

class AddContentViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(AddContentUiState())
    val state: StateFlow<AddContentUiState> = _state.asStateFlow()

    fun loadPdf(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    sourceUri = uri,
                    sourceName = FileIo.displayName(context, uri) ?: "document.pdf",
                    isWorking = true,
                    error = null,
                    resultUri = null,
                )
            }
            try {
                val count = withContext(Dispatchers.IO) {
                    val b = FileIo.readBytes(context, uri)
                    val d = PdfEngine.load(b)
                    try { PdfEngine.pageCount(d) } finally { d.close() }
                }
                _state.update { it.copy(isWorking = false, pageCount = count) }
            } catch (e: PdfEngine.PdfError) {
                _state.update { it.copy(isWorking = false, error = messageFor(e), sourceUri = null) }
            } catch (e: Throwable) {
                _state.update { it.copy(isWorking = false, error = e.message ?: "Cannot read file", sourceUri = null) }
            }
        }
    }

    fun setImage(context: Context, uri: Uri) {
        _state.update {
            it.copy(
                imageUri = uri,
                imageName = FileIo.displayName(context, uri) ?: "image",
                error = null,
                resultUri = null,
            )
        }
    }

    fun clearImage() = _state.update { it.copy(imageUri = null, imageName = "") }
    fun setText(v: String) = _state.update { it.copy(text = v) }
    fun setFontSize(v: Int) = _state.update { it.copy(fontSize = v.coerceIn(6, 96)) }
    fun setSlot(s: ContentSlot) = _state.update { it.copy(slot = s) }
    fun setTarget(t: ContentTarget) = _state.update { it.copy(target = t) }
    fun setSpecificPage(v: String) = _state.update { it.copy(specificPageInput = v) }

    fun apply() {
        val snap = _state.value
        val src = snap.sourceUri ?: return
        if (snap.isWorking) return
        if (snap.text.isBlank() && snap.imageUri == null) {
            _state.update { it.copy(error = "Add text or an image first.") }
            return
        }
        val targets = resolveTargets(snap) ?: run {
            _state.update { it.copy(error = "Enter a valid page number.") }
            return
        }
        _state.update { it.copy(isWorking = true, error = null, resultUri = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    stamp(getApplication(), snap, targets)
                }
                _state.update {
                    it.copy(isWorking = false, resultUri = result.first, resultName = result.second)
                }
            } catch (e: PdfEngine.PdfError) {
                _state.update { it.copy(isWorking = false, error = messageFor(e)) }
            } catch (e: Throwable) {
                _state.update { it.copy(isWorking = false, error = e.message ?: "Failed") }
            }
        }
    }

    private fun resolveTargets(snap: AddContentUiState): List<Int>? {
        return when (snap.target) {
            ContentTarget.ALL_PAGES -> (0 until snap.pageCount).toList()
            ContentTarget.FIRST_PAGE -> listOf(0)
            ContentTarget.LAST_PAGE -> listOf(snap.pageCount - 1)
            ContentTarget.SPECIFIC -> {
                val n = snap.specificPageInput.trim().toIntOrNull() ?: return null
                if (n !in 1..snap.pageCount) return null
                listOf(n - 1)
            }
        }
    }

    private fun stamp(
        context: Context,
        snap: AddContentUiState,
        pageIdxs: List<Int>,
    ): Pair<Uri, String> {
        val bytes = FileIo.readBytes(context, snap.sourceUri!!)
        val doc = PdfEngine.load(bytes)
        try {
            val font = PDType1Font.HELVETICA_BOLD
            val imageBytes = snap.imageUri?.let { FileIo.readBytes(context, it) }
            val imageBounds = imageBytes?.let {
                BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    .also { o -> BitmapFactory.decodeByteArray(it, 0, it.size, o) }
            }

            pageIdxs.forEach { idx ->
                if (idx !in 0 until doc.numberOfPages) return@forEach
                val page: PDPage = doc.getPage(idx)
                val box = page.mediaBox
                val pw = box.width
                val ph = box.height
                val margin = 36f

                PDPageContentStream(
                    doc, page,
                    PDPageContentStream.AppendMode.APPEND,
                    true, true,
                ).use { cs ->
                    if (snap.text.isNotBlank()) {
                        cs.beginText()
                        cs.setFont(font, snap.fontSize.toFloat())
                        val textLines = snap.text.split('\n')
                        val lineH = snap.fontSize * 1.3f
                        textLines.forEachIndexed { i, raw ->
                            val safe = sanitize(raw)
                            val w = safeTextWidth(font, snap.fontSize.toFloat(), safe)
                            val (x, y) = when (snap.slot) {
                                ContentSlot.TOP_LEFT -> margin to (ph - margin - snap.fontSize - i * lineH)
                                ContentSlot.TOP_RIGHT -> (pw - margin - w) to (ph - margin - snap.fontSize - i * lineH)
                                ContentSlot.BOTTOM_LEFT -> margin to (margin + (textLines.size - 1 - i) * lineH)
                                ContentSlot.BOTTOM_RIGHT -> (pw - margin - w) to (margin + (textLines.size - 1 - i) * lineH)
                                ContentSlot.CENTER -> ((pw - w) / 2f) to (ph / 2f - snap.fontSize * (textLines.size / 2f) + snap.fontSize - i * lineH)
                            }
                            cs.beginText()
                            cs.newLineAtOffset(x, y)
                            cs.showText(safe)
                            cs.endText()
                        }
                    }

                    if (imageBytes != null && imageBounds != null &&
                        imageBounds.outWidth > 0 && imageBounds.outHeight > 0
                    ) {
                        val xobj = JPEGFactory.createFromByteArray(doc, imageBytes)
                        // Fit within 40% of page width, keep aspect
                        val maxW = pw * 0.4f
                        val maxH = ph * 0.4f
                        val aspect = imageBounds.outWidth.toFloat() / imageBounds.outHeight.toFloat()
                        val w = if (aspect >= 1f) maxW else maxH * aspect
                        val h = w / aspect
                        val (ix, iy) = when (snap.slot) {
                            ContentSlot.TOP_LEFT -> margin to (ph - margin - h)
                            ContentSlot.TOP_RIGHT -> (pw - margin - w) to (ph - margin - h)
                            ContentSlot.BOTTOM_LEFT -> margin to margin
                            ContentSlot.BOTTOM_RIGHT -> (pw - margin - w) to margin
                            ContentSlot.CENTER -> ((pw - w) / 2f) to ((ph - h) / 2f)
                        }
                        cs.drawImage(xobj, ix, iy, w, h)
                    }
                }
            }

            val outBytes = PdfEngine.save(doc)
            val name = "edited-${System.currentTimeMillis()}.pdf"
            val uri = PdfEngine.stageShare(context, name, outBytes)
            return uri to name
        } finally {
            doc.close()
        }
    }

    private fun safeTextWidth(font: PDType1Font, size: Float, s: String): Float =
        try { font.getStringWidth(s) / 1000f * size } catch (_: Throwable) { s.length * size * 0.5f }

    private fun sanitize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(if (ch.code in 32..126) ch else '?')
        }
        return sb.toString()
    }

    private fun messageFor(e: PdfEngine.PdfError): String = when (e) {
        is PdfEngine.PdfError.NeedsPassword -> "PDF is password-protected. Unlock it first."
        is PdfEngine.PdfError.WrongPassword -> "Incorrect password."
        is PdfEngine.PdfError.Corrupt -> "PDF is damaged or unreadable."
        is PdfEngine.PdfError.Io -> "Could not read the file."
    }
}

@Composable
fun AddContentScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: AddContentViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.loadPdf(context, it) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setImage(context, it) }
    }

    ToolScaffold(
        title = stringResource(R.string.tool_add_content),
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            if (state.sourceUri != null) {
                Column(Modifier.padding(16.dp)) {
                    state.resultUri?.let { uri ->
                        SecondaryActionButton(
                            text = stringResource(R.string.action_share),
                            onClick = { sharePdf(context, uri) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    PrimaryActionButton(
                        text = stringResource(R.string.action_apply),
                        enabled = !state.isWorking,
                        onClick = { vm.apply() },
                    )
                }
            }
        },
    ) { innerMod ->
        Box(innerMod) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (state.sourceUri == null) {
                    EmptyPickState(
                        icon = Icons.Outlined.Edit,
                        title = stringResource(R.string.tool_add_content),
                        subtitle = "Pick a PDF, then overlay new text and/or an image.",
                    )
                }

                Button(
                    onClick = { pdfPicker.launch(FileIo.PDF_MIMES) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose PDF")
                }

                if (state.sourceUri != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(state.sourceName, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text("${state.pageCount} pages", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Text overlay (optional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = vm::setText,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Text to add") },
                        minLines = 3,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Font size", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf(10, 14, 18, 24, 32).forEach { s ->
                            FilterChip(
                                selected = state.fontSize == s,
                                onClick = { vm.setFontSize(s) },
                                label = { Text("$s") },
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Image overlay (optional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { imagePicker.launch(FileIo.IMAGE_MIMES) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.imageUri == null) "Pick image" else "Replace")
                        }
                        if (state.imageUri != null) {
                            OutlinedButton(onClick = { vm.clearImage() }) { Text("Clear") }
                        }
                    }
                    if (state.imageName.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(state.imageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Position", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ContentSlot.entries.forEach { s ->
                            FilterChip(selected = state.slot == s, onClick = { vm.setSlot(s) }, label = { Text(s.label) })
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Apply to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = state.target == ContentTarget.ALL_PAGES, onClick = { vm.setTarget(ContentTarget.ALL_PAGES) }, label = { Text("All") })
                        FilterChip(selected = state.target == ContentTarget.FIRST_PAGE, onClick = { vm.setTarget(ContentTarget.FIRST_PAGE) }, label = { Text("First") })
                        FilterChip(selected = state.target == ContentTarget.LAST_PAGE, onClick = { vm.setTarget(ContentTarget.LAST_PAGE) }, label = { Text("Last") })
                        FilterChip(selected = state.target == ContentTarget.SPECIFIC, onClick = { vm.setTarget(ContentTarget.SPECIFIC) }, label = { Text("Specific") })
                    }
                    if (state.target == ContentTarget.SPECIFIC) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = state.specificPageInput,
                            onValueChange = vm::setSpecificPage,
                            label = { Text("Page number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                state.error?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    ResultBanner(success = false, message = msg)
                }
                if (state.resultUri != null) {
                    Spacer(Modifier.height(12.dp))
                    ResultBanner(success = true, message = "Saved — ${state.resultName}")
                }
            }

            if (state.isWorking) ProcessingOverlay()
        }
    }
}

private fun sharePdf(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDF"))
}
