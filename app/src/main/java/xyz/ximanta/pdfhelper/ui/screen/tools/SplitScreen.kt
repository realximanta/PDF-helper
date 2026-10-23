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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Description
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

enum class SplitMode { RANGE, EACH_PAGE }

data class SplitUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val mode: SplitMode = SplitMode.RANGE,
    val rangeInput: String = "",
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val resultName: String = "",
    val error: String? = null,
)

class SplitViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = _state.asStateFlow()

    fun loadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    sourceUri = uri,
                    sourceName = FileIo.displayName(context, uri) ?: "document.pdf",
                    resultUri = null,
                    error = null,
                    isWorking = true,
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
                _state.update {
                    it.copy(isWorking = false, error = messageFor(e), sourceUri = null)
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        isWorking = false,
                        error = e.message ?: "Cannot read file",
                        sourceUri = null,
                    )
                }
            }
        }
    }

    fun setMode(mode: SplitMode) = _state.update { it.copy(mode = mode) }

    fun setRange(text: String) = _state.update { it.copy(rangeInput = text) }

    fun split() {
        val snap = _state.value
        val src = snap.sourceUri ?: return
        if (snap.isWorking) return

        val pages = parseRanges(snap.rangeInput, snap.pageCount)
        if (snap.mode == SplitMode.RANGE && pages.isEmpty()) {
            _state.update { it.copy(error = "Enter a valid range like 1-3, 5, 7-9") }
            return
        }

        _state.update { it.copy(isWorking = true, error = null, resultUri = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    splitOnDisk(getApplication(), src, snap.mode, pages, snap.resultName)
                }
                _state.update {
                    it.copy(
                        isWorking = false,
                        resultUri = result.first,
                        resultName = result.second,
                    )
                }
            } catch (e: PdfEngine.PdfError) {
                _state.update { it.copy(isWorking = false, error = messageFor(e)) }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        isWorking = false,
                        error = e.message ?: "Split failed",
                    )
                }
            }
        }
    }

    private fun splitOnDisk(
        context: Context,
        src: Uri,
        mode: SplitMode,
        pages: List<Int>,
        _resultName: String,
    ): Pair<Uri, String> {
        val srcBytes = FileIo.readBytes(context, src)
        val source = PdfEngine.load(srcBytes)
        try {
            val out = PDDocument()
            try {
                when (mode) {
                    SplitMode.RANGE -> {
                        pages.forEach { idx ->
                            out.importPage(source.getPage(idx - 1))
                        }
                    }
                    SplitMode.EACH_PAGE -> {
                        source.pages.forEach { page ->
                            out.importPage(page)
                        }
                    }
                }
                val bytes = PdfEngine.save(out)
                val name = "split-${System.currentTimeMillis()}.pdf"
                val uri = PdfEngine.stageShare(context, name, bytes)
                return uri to name
            } finally {
                out.close()
            }
        } finally {
            source.close()
        }
    }

    private fun messageFor(e: PdfEngine.PdfError): String = when (e) {
        is PdfEngine.PdfError.NeedsPassword -> "PDF is password-protected. Unlock it first."
        is PdfEngine.PdfError.WrongPassword -> "Incorrect password."
        is PdfEngine.PdfError.Corrupt -> "PDF is damaged or unreadable."
        is PdfEngine.PdfError.Io -> "Could not read the file."
    }

    /** Parse "1-3, 5, 7-9" → sorted list of 1-based page numbers. */
    private fun parseRanges(input: String, pageCount: Int): List<Int> {
        if (input.isBlank()) return emptyList()
        val result = mutableListOf<Int>()
        input.split(',', ' ').filter { it.isNotBlank() }.forEach { part ->
            val dash = part.indexOf('-')
            try {
                if (dash > 0) {
                    val a = part.substring(0, dash).trim().toInt()
                    val b = part.substring(dash + 1).trim().toInt()
                    val lo = minOf(a, b)
                    val hi = maxOf(a, b)
                    for (n in lo..hi) if (n in 1..pageCount) result += n
                } else {
                    val n = part.trim().toInt()
                    if (n in 1..pageCount) result += n
                }
            } catch (_: NumberFormatException) {
                // skip invalid tokens
            }
        }
        return result.distinct().sorted()
    }
}

@Composable
fun SplitScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SplitViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.loadFile(context, it) } }

    ToolScaffold(
        title = stringResource(R.string.tool_split),
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
                        onClick = { vm.split() },
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
                        icon = Icons.Outlined.CallSplit,
                        title = stringResource(R.string.tool_split),
                        subtitle = "Pick a PDF to extract a range or every page.",
                    )
                }

                androidx.compose.material3.Button(
                    onClick = { picker.launch(FileIo.PDF_MIMES) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.sourceUri == null)
                            stringResource(R.string.action_choose_file)
                        else
                            stringResource(R.string.action_choose_file).let { "$it (re-pick)" },
                    )
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
                    Text(
                        text = "Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        FilterChip(
                            selected = state.mode == SplitMode.RANGE,
                            onClick = { vm.setMode(SplitMode.RANGE) },
                            label = { Text("Custom range") },
                        )
                        FilterChip(
                            selected = state.mode == SplitMode.EACH_PAGE,
                            onClick = { vm.setMode(SplitMode.EACH_PAGE) },
                            label = { Text("Every page") },
                        )
                    }

                    if (state.mode == SplitMode.RANGE) {
                        OutlinedTextField(
                            value = state.rangeInput,
                            onValueChange = vm::setRange,
                            label = { Text("Pages (e.g. 1-3, 5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (state.mode == SplitMode.EACH_PAGE && state.pageCount > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Note: this will produce a PDF containing all pages in extracted form.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.error?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    ResultBanner(success = false, message = msg)
                }
                if (state.resultUri != null) {
                    Spacer(Modifier.height(12.dp))
                    ResultBanner(
                        success = true,
                        message = "Split — ${state.resultName}",
                    )
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
