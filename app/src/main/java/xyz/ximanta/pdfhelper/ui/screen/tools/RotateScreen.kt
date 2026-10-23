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
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
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

data class RotateUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    // Each page's accumulated rotation delta in degrees, mod 360.
    val rotations: Map<Int, Int> = emptyMap(),
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val resultName: String = "",
    val error: String? = null,
)

class RotateViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(RotateUiState())
    val state: StateFlow<RotateUiState> = _state.asStateFlow()

    fun loadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    sourceUri = uri,
                    sourceName = FileIo.displayName(context, uri) ?: "document.pdf",
                    isWorking = true,
                    error = null,
                    resultUri = null,
                    rotations = emptyMap(),
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
                    it.copy(isWorking = false, error = e.message ?: "Cannot read file", sourceUri = null)
                }
            }
        }
    }

    /** Rotate a single page index (0-based) by delta degrees. */
    fun rotatePage(index: Int, delta: Int) {
        _state.update {
            val cur = it.rotations[index] ?: 0
            val next = ((cur + delta) % 360 + 360) % 360
            it.copy(rotations = it.rotations + (index to next))
        }
    }

    /** Apply delta to every page. */
    fun rotateAll(delta: Int) {
        _state.update { snap ->
            val next = (0 until snap.pageCount).associateWith { i ->
                val cur = snap.rotations[i] ?: 0
                ((cur + delta) % 360 + 360) % 360
            }
            snap.copy(rotations = next)
        }
    }

    fun clearRotations() = _state.update { it.copy(rotations = emptyMap()) }

    fun apply() {
        val snap = _state.value
        val src = snap.sourceUri ?: return
        if (snap.isWorking) return
        if (snap.rotations.values.none { it != 0 }) {
            _state.update { it.copy(error = "Nothing to rotate yet.") }
            return
        }
        _state.update { it.copy(isWorking = true, error = null, resultUri = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    rotateOnDisk(getApplication(), src, snap.rotations)
                }
                _state.update {
                    it.copy(isWorking = false, resultUri = result.first, resultName = result.second)
                }
            } catch (e: PdfEngine.PdfError) {
                _state.update { it.copy(isWorking = false, error = messageFor(e)) }
            } catch (e: Throwable) {
                _state.update { it.copy(isWorking = false, error = e.message ?: "Rotate failed") }
            }
        }
    }

    private fun rotateOnDisk(
        context: Context,
        src: Uri,
        rotations: Map<Int, Int>,
    ): Pair<Uri, String> {
        val bytes = FileIo.readBytes(context, src)
        val doc = PdfEngine.load(bytes)
        try {
            rotations.forEach { (idx, deg) ->
                if (deg == 0) return@forEach
                if (idx in 0 until doc.numberOfPages) {
                    val page = doc.getPage(idx)
                    val current = page.rotation
                    page.rotation = ((current + deg) % 360 + 360) % 360
                }
            }
            val outBytes = PdfEngine.save(doc)
            val name = "rotated-${System.currentTimeMillis()}.pdf"
            val uri = PdfEngine.stageShare(context, name, outBytes)
            return uri to name
        } finally {
            doc.close()
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
fun RotateScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: RotateViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.loadFile(context, it) } }

    ToolScaffold(
        title = stringResource(R.string.tool_rotate),
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
                        icon = Icons.Outlined.Rotate90DegreesCw,
                        title = stringResource(R.string.tool_rotate),
                        subtitle = "Pick a PDF, then rotate any page by 90° steps.",
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
                                    text = "${state.pageCount} pages",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { vm.clearRotations() }) {
                                Text("Reset")
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { vm.rotateAll(-90) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("All 90°")
                        }
                        OutlinedButton(
                            onClick = { vm.rotateAll(90) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("All 90°")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = (0 until state.pageCount).toList(), key = { it }) { idx ->
                            val deg = state.rotations[idx] ?: 0
                            PageRotateRow(
                                pageNumber = idx + 1,
                                degrees = deg,
                                onLeft = { vm.rotatePage(idx, -90) },
                                onRight = { vm.rotatePage(idx, 90) },
                                onFlip = { vm.rotatePage(idx, 180) },
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
                    ResultBanner(success = true, message = "Rotated — ${state.resultName}")
                }
            }

            if (state.isWorking) ProcessingOverlay()
        }
    }
}

@Composable
private fun PageRotateRow(
    pageNumber: Int,
    degrees: Int,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onFlip: () -> Unit,
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
                text = "Page $pageNumber",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$degrees°",
                modifier = Modifier.width(48.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onLeft, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Rotate left")
            }
            IconButton(onClick = onRight, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate right")
            }
            IconButton(onClick = onFlip, modifier = Modifier.size(36.dp)) {
                Text("180", style = MaterialTheme.typography.labelSmall)
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
