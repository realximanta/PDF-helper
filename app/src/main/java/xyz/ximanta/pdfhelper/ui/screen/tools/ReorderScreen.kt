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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

data class ReorderUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    // Ordering of original 0-based page indices.
    val order: List<Int> = emptyList(),
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val resultName: String = "",
    val error: String? = null,
)

class ReorderViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ReorderUiState())
    val state: StateFlow<ReorderUiState> = _state.asStateFlow()

    fun loadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    sourceUri = uri,
                    sourceName = FileIo.displayName(context, uri) ?: "document.pdf",
                    isWorking = true,
                    error = null,
                    resultUri = null,
                    order = emptyList(),
                )
            }
            try {
                val count = withContext(Dispatchers.IO) {
                    val bytes = FileIo.readBytes(context, uri)
                    val doc = PdfEngine.load(bytes)
                    try { PdfEngine.pageCount(doc) } finally { doc.close() }
                }
                _state.update {
                    it.copy(isWorking = false, order = (0 until count).toList())
                }
            } catch (e: PdfEngine.PdfError) {
                _state.update {
                    it.copy(isWorking = false, error = messageFor(e), sourceUri = null)
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(isWorking = false, error = e.message ?: "Cannot read file", sourceUri = null)
                }
            }
        }
    }

    fun moveUp(position: Int) {
        if (position <= 0) return
        _state.update { snap ->
            val list = snap.order.toMutableList()
            val tmp = list[position - 1]
            list[position - 1] = list[position]
            list[position] = tmp
            snap.copy(order = list)
        }
    }

    fun moveDown(position: Int) {
        _state.update { snap ->
            if (position !in 0 until snap.order.lastIndex) return@update snap
            val list = snap.order.toMutableList()
            val tmp = list[position + 1]
            list[position + 1] = list[position]
            list[position] = tmp
            snap.copy(order = list)
        }
    }

    fun reset() = _state.update {
        it.copy(order = it.order.indices.toList())
    }

    fun apply() {
        val snap = _state.value
        val src = snap.sourceUri ?: return
        if (snap.isWorking || snap.order.isEmpty()) return
        if (snap.order == snap.order.indices.toList()) {
            _state.update { it.copy(error = "Order unchanged.") }
            return
        }
        _state.update { it.copy(isWorking = true, error = null, resultUri = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    reorderOnDisk(getApplication(), src, snap.order)
                }
                _state.update {
                    it.copy(isWorking = false, resultUri = result.first, resultName = result.second)
                }
            } catch (e: PdfEngine.PdfError) {
                _state.update { it.copy(isWorking = false, error = messageFor(e)) }
            } catch (e: Throwable) {
                _state.update { it.copy(isWorking = false, error = e.message ?: "Reorder failed") }
            }
        }
    }

    private fun reorderOnDisk(
        context: Context,
        src: Uri,
        order: List<Int>,
    ): Pair<Uri, String> {
        val bytes = FileIo.readBytes(context, src)
        val source = PdfEngine.load(bytes)
        try {
            val out = PDDocument()
            try {
                order.forEach { idx ->
                    if (idx in 0 until source.numberOfPages) {
                        out.importPage(source.getPage(idx))
                    }
                }
                val outBytes = PdfEngine.save(out)
                val name = "reordered-${System.currentTimeMillis()}.pdf"
                val uri = PdfEngine.stageShare(context, name, outBytes)
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
}

@Composable
fun ReorderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ReorderViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.loadFile(context, it) } }

    ToolScaffold(
        title = stringResource(R.string.tool_reorder),
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
                    .padding(16.dp),
            ) {
                if (state.sourceUri == null) {
                    EmptyPickState(
                        icon = Icons.Outlined.SwapVert,
                        title = stringResource(R.string.tool_reorder),
                        subtitle = "Pick a PDF, then move pages up or down to reorder.",
                    )
                }

                Button(
                    onClick = { picker.launch(FileIo.PDF_MIMES) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
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
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = state.sourceName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${state.order.size} pages",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { vm.reset() }) { Text("Reset") }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = state.order, key = { it }) { originalIndex ->
                            val position = state.order.indexOf(originalIndex)
                            ReorderRow(
                                position = position + 1,
                                originalPage = originalIndex + 1,
                                canMoveUp = position > 0,
                                canMoveDown = position < state.order.lastIndex,
                                onUp = { vm.moveUp(position) },
                                onDown = { vm.moveDown(position) },
                            )
                        }
                    }
                }

                state.error?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    ResultBanner(success = false, message = msg)
                }
                if (state.resultUri != null) {
                    Spacer(Modifier.height(12.dp))
                    ResultBanner(success = true, message = "Reordered — ${state.resultName}")
                }
            }

            if (state.isWorking) ProcessingOverlay()
        }
    }
}

@Composable
private fun ReorderRow(
    position: Int,
    originalPage: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$position.",
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Original page $originalPage",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(
                onClick = onUp,
                enabled = canMoveUp,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move up")
            }
            IconButton(
                onClick = onDown,
                enabled = canMoveDown,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move down")
            }
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
