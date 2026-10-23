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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Description
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
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
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
import xyz.ximanta.pdfhelper.ui.components.PrimaryActionButton
import xyz.ximanta.pdfhelper.ui.components.ProcessingOverlay
import xyz.ximanta.pdfhelper.ui.components.ResultBanner
import xyz.ximanta.pdfhelper.ui.components.SecondaryActionButton
import xyz.ximanta.pdfhelper.ui.components.ToolScaffold

enum class PageSize(val label: String) {
    A4("A4"),
    LETTER("Letter"),
}

data class TextToPdfUiState(
    val text: String = "",
    val sourceName: String = "",
    val pageSize: PageSize = PageSize.A4,
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val resultName: String = "",
    val error: String? = null,
)

class TextToPdfViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(TextToPdfUiState())
    val state: StateFlow<TextToPdfUiState> = _state.asStateFlow()

    fun setText(value: String) = _state.update {
        it.copy(text = value, error = null, resultUri = null)
    }

    fun setPageSize(size: PageSize) = _state.update { it.copy(pageSize = size) }

    fun loadTextFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null, resultUri = null) }
            try {
                val content = withContext(Dispatchers.IO) {
                    String(FileIo.readBytes(context, uri), Charsets.UTF_8)
                }
                val name = FileIo.displayName(context, uri) ?: "text.txt"
                _state.update {
                    it.copy(isWorking = false, text = content, sourceName = name)
                }
            } catch (e: Throwable) {
                _state.update { it.copy(isWorking = false, error = e.message ?: "Cannot read file") }
            }
        }
    }

    fun build() {
        val snap = _state.value
        if (snap.isWorking || snap.text.isBlank()) return
        _state.update { it.copy(isWorking = true, error = null, resultUri = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    buildPdf(getApplication(), snap.text, snap.pageSize)
                }
                _state.update {
                    it.copy(isWorking = false, resultUri = result.first, resultName = result.second)
                }
            } catch (e: Throwable) {
                _state.update { it.copy(isWorking = false, error = e.message ?: "Build failed") }
            }
        }
    }

    private fun buildPdf(
        context: Context,
        text: String,
        pageSize: PageSize,
    ): Pair<Uri, String> {
        val out = PDDocument()
        try {
            val pageRect = when (pageSize) {
                PageSize.A4 -> PDRectangle.A4
                PageSize.LETTER -> PDRectangle.LETTER
            }
            val margin = 56f
            val fontSize = 11f
            val leading = fontSize * 1.45f
            val font = PDType1Font.HELVETICA
            val usableWidth = pageRect.width - margin * 2
            val usableHeight = pageRect.height - margin * 2

            var page = PDPage(pageRect).also { out.addPage(it) }
            var stream = PDPageContentStream(out, page)
            stream.beginText()
            stream.setFont(font, fontSize)
            stream.newLineAtOffset(margin, pageRect.height - margin)
            var cursorY = pageRect.height - margin

            val lines = wrapText(text, font, fontSize, usableWidth)
            for (line in lines) {
                if (cursorY - leading < margin) {
                    stream.endText()
                    stream.close()
                    page = PDPage(pageRect).also { out.addPage(it) }
                    stream = PDPageContentStream(out, page)
                    stream.beginText()
                    stream.setFont(font, fontSize)
                    stream.newLineAtOffset(margin, pageRect.height - margin)
                    cursorY = pageRect.height - margin
                }
                stream.showText(sanitize(line))
                stream.newLineAtOffset(0f, -leading)
                cursorY -= leading
            }
            stream.endText()
            stream.close()

            val bytes = PdfEngine.save(out)
            val name = "text-${System.currentTimeMillis()}.pdf"
            val uri = PdfEngine.stageShare(context, name, bytes)
            return uri to name
        } finally {
            out.close()
        }
    }

    /** Wrap text into lines that fit usableWidth. */
    private fun wrapText(
        text: String,
        font: PDType1Font,
        fontSize: Float,
        usableWidth: Float,
    ): List<String> {
        val result = mutableListOf<String>()
        text.replace("\r\n", "\n").replace("\r", "\n").split("\n").forEach { paragraph ->
            if (paragraph.isEmpty()) {
                result += ""
                return@forEach
            }
            val words = paragraph.split(' ')
            val line = StringBuilder()
            var lineWidth = 0f
            for (word in words) {
                val wordWidth = safeWidth(font, fontSize, word)
                val spaceWidth = if (line.isEmpty()) 0f else safeWidth(font, fontSize, " ")
                if (lineWidth + spaceWidth + wordWidth > usableWidth && line.isNotEmpty()) {
                    result += line.toString()
                    line.setLength(0)
                    line.append(word)
                    lineWidth = wordWidth
                } else {
                    if (line.isNotEmpty()) {
                        line.append(' ')
                        lineWidth += spaceWidth
                    }
                    line.append(word)
                    lineWidth += wordWidth
                }
            }
            if (line.isNotEmpty()) result += line.toString()
        }
        return result
    }

    private fun safeWidth(font: PDType1Font, size: Float, s: String): Float {
        return try {
            font.getStringWidth(s) / 1000f * size
        } catch (_: Throwable) {
            s.length * size * 0.5f
        }
    }

    /** Replace characters outside WinAnsi that Type1 fonts can't encode. */
    private fun sanitize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            if (ch.code in 32..126 || ch.code == 9) sb.append(ch)
            else if (ch == '\t') sb.append(' ')
            else sb.append('?')
        }
        return sb.toString()
    }
}

@Composable
fun TextToPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: TextToPdfViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.loadTextFile(context, it) } }

    ToolScaffold(
        title = stringResource(R.string.tool_text_to_pdf),
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
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
                    enabled = !state.isWorking && state.text.isNotBlank(),
                    onClick = { vm.build() },
                )
            }
        },
    ) { innerMod ->
        Box(innerMod) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { picker.launch(FileIo.TEXT_MIMES) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Load .txt/.md")
                    }
                    OutlinedButton(
                        onClick = { vm.setText("") },
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Clear")
                    }
                }

                if (state.sourceName.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Loaded: ${state.sourceName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Page size",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PageSize.entries.forEach { size ->
                        FilterChip(
                            selected = state.pageSize == size,
                            onClick = { vm.setPageSize(size) },
                            label = { Text(size.label) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.text,
                    onValueChange = vm::setText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = { Text("Text or Markdown") },
                    placeholder = { Text("Type or paste content here…") },
                    minLines = 8,
                )

                state.error?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    ResultBanner(success = false, message = msg)
                }
                if (state.resultUri != null) {
                    Spacer(Modifier.height(12.dp))
                    ResultBanner(success = true, message = "Built — ${state.resultName}")
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
