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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
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

data class MergeItem(val uri: Uri, val name: String)

data class MergeUiState(
    val items: List<MergeItem> = emptyList(),
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val resultName: String = "",
    val error: String? = null,
)

class MergeViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(MergeUiState())
    val state: StateFlow<MergeUiState> = _state.asStateFlow()

    fun addFiles(context: Context, uris: List<Uri>) {
        val existing = _state.value.items.map { it.uri.toString() }.toSet()
        val fresh = uris.filter { it.toString() !in existing }.map { uri ->
            MergeItem(uri = uri, name = FileIo.displayName(context, uri) ?: "document.pdf")
        }
        if (fresh.isEmpty()) return
        _state.update {
            it.copy(items = it.items + fresh, error = null, resultUri = null)
        }
    }

    fun removeAt(index: Int) {
        _state.update {
            if (index !in it.items.indices) it
            else it.copy(items = it.items.filterIndexed { i, _ -> i != index })
        }
    }

    fun merge() {
        val snap = _state.value
        if (snap.isWorking || snap.items.size < 2) return
        _state.update { it.copy(isWorking = true, error = null, resultUri = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    mergeOnDisk(getApplication(), snap.items)
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
                        error = e.message ?: "Merge failed",
                    )
                }
            }
        }
    }

    private fun mergeOnDisk(
        context: Context,
        items: List<MergeItem>,
    ): Pair<Uri, String> {
        val out = PDDocument()
        try {
            val merger = PDFMergerUtility()
            items.forEach { item ->
                val bytes = FileIo.readBytes(context, item.uri)
                val src = PdfEngine.load(bytes)
                try {
                    merger.appendDocument(out, src)
                } finally {
                    src.close()
                }
            }
            val bytes = PdfEngine.save(out)
            val name = "merged-${System.currentTimeMillis()}.pdf"
            val uri = PdfEngine.stageShare(context, name, bytes)
            return uri to name
        } finally {
            out.close()
        }
    }

    private fun messageFor(e: PdfEngine.PdfError): String = when (e) {
        is PdfEngine.PdfError.NeedsPassword ->
            "One PDF is password-protected. Unlock it with the Password tool first."
        is PdfEngine.PdfError.WrongPassword -> "Incorrect password."
        is PdfEngine.PdfError.Corrupt -> "One of the PDFs is damaged or unreadable."
        is PdfEngine.PdfError.Io -> "Could not read one of the files."
    }
}

@Composable
fun MergeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: MergeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(context, uris)
    }

    ToolScaffold(
        title = stringResource(R.string.tool_merge),
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            if (state.items.isNotEmpty()) {
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
                        enabled = !state.isWorking && state.items.size >= 2,
                        onClick = { vm.merge() },
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
                if (state.items.isEmpty()) {
                    EmptyPickState(
                        icon = Icons.Outlined.Merge,
                        title = stringResource(R.string.tool_merge),
                        subtitle = "Pick two or more PDFs to combine in order.",
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Button(
                    onClick = { multiPicker.launch(FileIo.PDF_MIMES) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Merge, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.items.isEmpty())
                            stringResource(R.string.action_choose_files)
                        else
                            stringResource(R.string.action_add_more),
                    )
                }

                if (state.items.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = state.items,
                            key = { _, item -> item.uri.toString() },
                        ) { index, item ->
                            FileRow(
                                index = index + 1,
                                name = item.name,
                                onRemove = { vm.removeAt(index) },
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
                    ResultBanner(
                        success = true,
                        message = "Merged — ${state.resultName}",
                    )
                }
            }

            if (state.isWorking) {
                ProcessingOverlay()
            }
        }
    }
}

@Composable
private fun FileRow(
    index: Int,
    name: String,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$index",
                modifier = Modifier.width(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_remove),
                )
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
