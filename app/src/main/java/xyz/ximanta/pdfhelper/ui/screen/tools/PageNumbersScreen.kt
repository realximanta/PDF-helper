package xyz.ximanta.pdfhelper.ui.screen.tools

import android.app.Application
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.outlined.FormatListNumbered
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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
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

enum class NumberPosition(val label: String) {
    BOTTOM_CENTER("Bottom"),
    BOTTOM_RIGHT("Bottom R"),
    BOTTOM_LEFT("Bottom L"),
    TOP_CENTER("Top"),
    TOP_RIGHT("Top R"),
    TOP_LEFT("Top L"),
}

data class PageNumbersUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val position: NumberPosition = NumberPosition.BOTTOM_CENTER,
    val startAt: Int = 1,
    val fontSize: Int = 11,
    val format: String = "{n} / {total}",
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val resultName: String = "",
    val error: String? = null,
)

class PageNumbersViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PageNumbersUiState())
    val state: StateFlow<PageNumbersUiState> = _state.asStateFlow()

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

    fun setPosition(p: NumberPosition) = _state.update { it.copy(position = p) }
    fun setStartAt(v: Int) = _state.update { it.copy(startAt = v.coerceIn(0, 99999)) }
    fun setFontSize(v: Int) = _state.update { it.copy(fontSize = v.coerceIn(6, 36)) }
    fun setFormat(v: String) = _state.update { it.copy(format = v) }

    fun apply() {
        val snap = _state.value
        val src = snap.sourceUri ?: return
        if (snap.isWorking) return
        _state.update { it.copy(isWorking = true, error = null, resultUri = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    stamp(getApplication(), snap)
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

    private fun stamp(context: Context, snap: PageNumbersUiState): Pair<Uri, String> {
        val bytes = FileIo.readBytes(context, snap.sourceUri!!)
        val doc = PdfEngine.load(bytes)
        try {
            val font = PDType1Font.HELVETICA
            val margin = 32f
            for (i in 0 until doc.numberOfPages) {
                val page: PDPage = doc.getPage(i)
                val box = page.mediaBox
                val pw = box.width
                val ph = box.height

                val number = snap.startAt + i
                val total = snap.startAt + doc.numberOfPages - 1
                val text = snap.format
                    .replace("{n}", number.toString())
                    .replace("{total}", total.toString())
                    .let(::sanitize)

                val fontSize = snap.fontSize.toFloat()
                val w = safeWidth(font, fontSize, text)
                val h = fontSize

                val (x, y) = when (snap.position) {
                    NumberPosition.BOTTOM_CENTER -> (pw - w) / 2f to margin
                    NumberPosition.BOTTOM_RIGHT -> (pw - margin - w) to margin
                    NumberPosition.BOTTOM_LEFT -> margin to margin
                    NumberPosition.TOP_CENTER -> (pw - w) / 2f to (ph - margin - h)
                    NumberPosition.TOP_RIGHT -> (pw - margin - w) to (ph - margin - h)
                    NumberPosition.TOP_LEFT -> margin to (ph - margin - h)
                }

                PDPageContentStream(
                    doc, page,
                    PDPageContentStream.AppendMode.APPEND,
                    true, true,
                ).use { cs ->
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.newLineAtOffset(x, y)
                    cs.showText(text)
                    cs.endText()
                }
            }

            val outBytes = PdfEngine.save(doc)
            val name = "numbered-${System.currentTimeMillis()}.pdf"
            val uri = PdfEngine.stageShare(context, name, outBytes)
            return uri to name
        } finally {
            doc.close()
        }
    }

    private fun safeWidth(font: PDType1Font, size: Float, s: String): Float =
        try { font.getStringWidth(s) / 1000f * size } catch (_: Throwable) { s.length * size * 0.5f }

    private fun sanitize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch.code in 32..126) ch else '?')
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
fun PageNumbersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PageNumbersViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.loadPdf(context, it) }
    }

    ToolScaffold(
        title = stringResource(R.string.tool_page_numbers),
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
                        icon = Icons.Outlined.FormatListNumbered,
                        title = stringResource(R.string.tool_page_numbers),
                        subtitle = "Pick a PDF, then stamp page numbers anywhere.",
                    )
                }

                Button(
                    onClick = { picker.launch(FileIo.PDF_MIMES) },
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
                    Text("Position", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(NumberPosition.TOP_LEFT, NumberPosition.TOP_CENTER, NumberPosition.TOP_RIGHT).forEach { p ->
                                FilterChip(selected = state.position == p, onClick = { vm.setPosition(p) }, label = { Text(p.label) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(NumberPosition.BOTTOM_LEFT, NumberPosition.BOTTOM_CENTER, NumberPosition.BOTTOM_RIGHT).forEach { p ->
                                FilterChip(selected = state.position == p, onClick = { vm.setPosition(p) }, label = { Text(p.label) })
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Format", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.format,
                        onValueChange = vm::setFormat,
                        label = { Text("{n} / {total}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Use {n} for the page number, {total} for the last number.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Font size", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf(8, 10, 11, 12, 14, 16, 20).forEach { s ->
                            FilterChip(
                                selected = state.fontSize == s,
                                onClick = { vm.setFontSize(s) },
                                label = { Text("$s") },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.startAt.toString(),
                        onValueChange = { vm.setStartAt(it.toIntOrNull() ?: 0) },
                        label = { Text("Start numbering at") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
