package xyz.ximanta.pdfhelper.ui.screen.tools

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
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
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class ImageItem(val uri: Uri, val name: String)

data class ImagesToPdfUiState(
    val items: List<ImageItem> = emptyList(),
    val qualityPercent: Int = 75,       // 40..95
    val maxLongEdge: Int = 1920,        // px on longest side after resize
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val resultName: String = "",
    val error: String? = null,
)

class ImagesToPdfViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ImagesToPdfUiState())
    val state: StateFlow<ImagesToPdfUiState> = _state.asStateFlow()

    fun addFiles(context: Context, uris: List<Uri>) {
        val existing = _state.value.items.map { it.uri.toString() }.toSet()
        val fresh = uris.filter { it.toString() !in existing }.map { uri ->
            ImageItem(uri = uri, name = FileIo.displayName(context, uri) ?: "image")
        }
        if (fresh.isEmpty()) return
        _state.update { it.copy(items = it.items + fresh, error = null, resultUri = null) }
    }

    fun removeAt(index: Int) {
        _state.update {
            if (index !in it.items.indices) it
            else it.copy(items = it.items.filterIndexed { i, _ -> i != index })
        }
    }

    fun setQuality(percent: Int) {
        _state.update { it.copy(qualityPercent = percent.coerceIn(40, 95)) }
    }

    fun apply() {
        val snap = _state.value
        if (snap.isWorking || snap.items.isEmpty()) return
        _state.update { it.copy(isWorking = true, error = null, resultUri = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    buildPdf(getApplication(), snap)
                }
                _state.update {
                    it.copy(isWorking = false, resultUri = result.first, resultName = result.second)
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(isWorking = false, error = e.message ?: "Could not build PDF")
                }
            }
        }
    }

    private fun buildPdf(context: Context, snap: ImagesToPdfUiState): Pair<Uri, String> {
        val out = PDDocument()
        try {
            snap.items.forEach { item ->
                val bytes = FileIo.readBytes(context, item.uri)
                val compressed = compressToJpeg(bytes, snap.maxLongEdge, snap.qualityPercent)
                    ?: throw IllegalStateException("Could not decode: ${item.name}")

                // Page size = image aspect ratio (in points, 72 dpi baseline).
                val scale = 0.75f
                val pageW = (compressed.width * scale).coerceAtLeast(200f)
                val pageH = (compressed.height * scale).coerceAtLeast(200f)

                val page = PDPage(PDRectangle(pageW, pageH))
                out.addPage(page)

                val xObject = JPEGFactory.createFromByteArray(out, compressed.jpegBytes)
                PDPageContentStream(out, page).use { cs ->
                    cs.drawImage(xObject, 0f, 0f, pageW, pageH)
                }
            }

            val outBytes = PdfEngine.save(out)
            val name = "images-${System.currentTimeMillis()}.pdf"
            val uri = PdfEngine.stageShare(context, name, outBytes)
            return uri to name
        } finally {
            out.close()
        }
    }

    /** Decode + downsample + re-encode as JPEG at the given quality. */
    private fun compressToJpeg(srcBytes: ByteArray, maxLongEdge: Int, quality: Int): CompressedImage? {
        // 1. Probe dimensions without decoding pixels.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(srcBytes, 0, srcBytes.size, bounds)
        val origW = bounds.outWidth
        val origH = bounds.outHeight
        if (origW <= 0 || origH <= 0) return null

        // 2. Compute inSampleSize (power of 2) so decode stays memory-cheap.
        var sample = 1
        var longEdge = max(origW, origH)
        while (longEdge / sample > maxLongEdge * 2) {
            sample *= 2
            longEdge = max(origW, origH)
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(srcBytes, 0, srcBytes.size, opts) ?: return null

        // 3. Scale so the longest edge is <= maxLongEdge.
        val longEdgeDecoded = max(decoded.width, decoded.height)
        val scaled: Bitmap = if (longEdgeDecoded > maxLongEdge) {
            val ratio = maxLongEdge.toFloat() / longEdgeDecoded.toFloat()
            val w = (decoded.width * ratio).roundToInt().coerceAtLeast(1)
            val h = (decoded.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, w, h, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else decoded

        // 4. Re-encode as JPEG with the chosen quality.
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val result = CompressedImage(
            jpegBytes = out.toByteArray(),
            width = scaled.width,
            height = scaled.height,
        )
        scaled.recycle()
        return result
    }

    private data class CompressedImage(
        val jpegBytes: ByteArray,
        val width: Int,
        val height: Int,
    )
}

@Composable
fun ImagesToPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ImagesToPdfViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) vm.addFiles(context, uris) }

    ToolScaffold(
        title = stringResource(R.string.tool_images_to_pdf),
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
                if (state.items.isEmpty()) {
                    EmptyPickState(
                        icon = Icons.Outlined.Image,
                        title = stringResource(R.string.tool_images_to_pdf),
                        subtitle = "Pick JPG or PNG images. They're auto-compressed to keep PDF size small.",
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Button(
                    onClick = { multiPicker.launch(FileIo.IMAGE_MIMES) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.items.isEmpty())
                            stringResource(R.string.action_choose_files)
                        else
                            stringResource(R.string.action_add_more),
                    )
                }

                if (state.items.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "JPEG quality",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "${state.qualityPercent}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = state.qualityPercent.toFloat(),
                                onValueChange = { vm.setQuality(it.roundToInt()) },
                                valueRange = 40f..95f,
                                steps = 10,
                            )
                            Text(
                                text = "Lower = smaller PDF · Higher = sharper images",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = state.items,
                            key = { _, item -> item.uri.toString() },
                        ) { index, item ->
                            ImageRow(
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
                    ResultBanner(success = true, message = "Built — ${state.resultName}")
                }
            }

            if (state.isWorking) ProcessingOverlay()
        }
    }
}

@Composable
private fun ImageRow(
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
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_remove))
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
