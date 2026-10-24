package xyz.ximanta.pdfhelper.ui.screen.tools

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
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
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
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
import kotlin.math.atan2
import kotlin.math.sqrt

enum class WatermarkKind { TEXT, IMAGE }

data class WatermarkUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val kind: WatermarkKind = WatermarkKind.TEXT,
    val text: String = "CONFIDENTIAL",
    val fontSize: Int = 60,
    val opacityPercent: Int = 25,
    val imageUri: Uri? = null,
    val imageName: String = "",
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val resultName: String = "",
    val error: String? = null,
)

class WatermarkViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(WatermarkUiState())
    val state: StateFlow<WatermarkUiState> = _state.asStateFlow()

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

    fun setKind(k: WatermarkKind) = _state.update { it.copy(kind = k) }
    fun setText(v: String) = _state.update { it.copy(text = v) }
    fun setFontSize(v: Int) = _state.update { it.copy(fontSize = v.coerceIn(20, 200)) }
    fun setOpacity(v: Int) = _state.update { it.copy(opacityPercent = v.coerceIn(5, 90)) }
    fun setImage(context: Context, uri: Uri) {
        _state.update {
            it.copy(imageUri = uri, imageName = FileIo.displayName(context, uri) ?: "image")
        }
    }
    fun clearImage() = _state.update { it.copy(imageUri = null, imageName = "") }

    fun apply() {
        val snap = _state.value
        val src = snap.sourceUri ?: return
        if (snap.isWorking) return
        if (snap.kind == WatermarkKind.TEXT && snap.text.isBlank()) {
            _state.update { it.copy(error = "Enter watermark text.") }
            return
        }
        if (snap.kind == WatermarkKind.IMAGE && snap.imageUri == null) {
            _state.update { it.copy(error = "Pick an image first.") }
            return
        }
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

    private fun stamp(context: Context, snap: WatermarkUiState): Pair<Uri, String> {
        val bytes = FileIo.readBytes(context, snap.sourceUri!!)
        val doc = PdfEngine.load(bytes)
        try {
            val alpha = snap.opacityPercent / 100f
            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = alpha }
            val font = PDType1Font.HELVETICA_BOLD
            val imgBytes = if (snap.kind == WatermarkKind.IMAGE) {
                FileIo.readBytes(context, snap.imageUri!!)
            } else null

            for (pageIdx in 0 until doc.numberOfPages) {
                val page: PDPage = doc.getPage(pageIdx)
                val box = page.mediaBox
                val pw = box.width
                val ph = box.height

                PDPageContentStream(
                    doc, page,
                    PDPageContentStream.AppendMode.APPEND,
                    true, true,
                ).use { cs ->
                    cs.setGraphicsStateParameters(gs)

                    if (snap.kind == WatermarkKind.TEXT) {
                        val text = sanitize(snap.text)
                        val fontSize = snap.fontSize.toFloat()
                        val textWidth = safeWidth(font, fontSize, text)
                        val angle = atan2(ph.toDouble(), pw.toDouble()).toFloat()
                        val startX = (pw - textWidth * 0.6f * 1.6f) / 2f
                        val startY = ph / 2f - fontSize / 2f

                        cs.beginText()
                        cs.setFont(font, fontSize)
                        cs.setNonStrokingColor(Color.GRAY)
                        cs.setTextMatrix(
                            Matrix.getRotateInstance(angle, startX, startY)
                        )
                        cs.showText(text)
                        cs.endText()
                        @Suppress("UNUSED_EXPRESSION")
                        sqrt((pw * pw + ph * ph).toDouble()).toFloat()
                    } else if (imgBytes != null) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size, bounds)
                        val iw = bounds.outWidth.toFloat()
                        val ih = bounds.outHeight.toFloat()
                        val xobj = JPEGFactory.createFromByteArray(doc, imgBytes)
                        val targetW = pw * 0.55f
                        val targetH = targetW * (ih / iw)
                        val x = (pw - targetW) / 2f
                        val y = (ph - targetH) / 2f
                        cs.drawImage(xobj, x, y, targetW, targetH)
                    }
                }
            }

            val outBytes = PdfEngine.save(doc)
            val name = "watermarked-${System.currentTimeMillis()}.pdf"
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
fun WatermarkScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: WatermarkViewModel = viewModel(),
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
        title = stringResource(R.string.tool_watermark),
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
                        icon = Icons.Outlined.BrandingWatermark,
                        title = stringResource(R.string.tool_watermark),
                        subtitle = "Pick a PDF, then stamp a text or image watermark on every page.",
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.kind == WatermarkKind.TEXT, onClick = { vm.setKind(WatermarkKind.TEXT) }, label = { Text("Text") })
                        FilterChip(selected = state.kind == WatermarkKind.IMAGE, onClick = { vm.setKind(WatermarkKind.IMAGE) }, label = { Text("Image") })
                    }

                    Spacer(Modifier.height(12.dp))
                    if (state.kind == WatermarkKind.TEXT) {
                        OutlinedTextField(
                            value = state.text,
                            onValueChange = vm::setText,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Watermark text") },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Font size ${state.fontSize}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = state.fontSize.toFloat(),
                            onValueChange = { vm.setFontSize(it.toInt()) },
                            valueRange = 20f..200f,
                            steps = 17,
                        )
                    } else {
                        OutlinedButton(onClick = { imagePicker.launch(FileIo.IMAGE_MIMES) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.imageUri == null) "Pick image" else "Replace image")
                        }
                        if (state.imageName.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(state.imageName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { vm.clearImage() }) { Text("Clear") }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Opacity ${state.opacityPercent}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = state.opacityPercent.toFloat(),
                        onValueChange = { vm.setOpacity(it.toInt()) },
                        valueRange = 5f..90f,
                        steps = 16,
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
