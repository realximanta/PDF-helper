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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

enum class PasswordMode { PROTECT, UNPROTECT }

data class PasswordUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val isEncrypted: Boolean = false,
    val mode: PasswordMode = PasswordMode.PROTECT,
    // Protect
    val newPassword: String = "",
    val confirmPassword: String = "",
    // Unprotect
    val currentPassword: String = "",
    // Status
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val resultName: String = "",
    val error: String? = null,
)

class PasswordViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PasswordUiState())
    val state: StateFlow<PasswordUiState> = _state.asStateFlow()

    fun loadPdf(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    sourceUri = uri,
                    sourceName = FileIo.displayName(context, uri) ?: "document.pdf",
                    isWorking = true,
                    error = null,
                    resultUri = null,
                    newPassword = "",
                    confirmPassword = "",
                    currentPassword = "",
                )
            }
            try {
                val encrypted = withContext(Dispatchers.IO) {
                    val b = FileIo.readBytes(context, uri)
                    PdfEngine.isEncrypted(b)
                }
                _state.update {
                    it.copy(
                        isWorking = false,
                        isEncrypted = encrypted,
                        mode = if (encrypted) PasswordMode.UNPROTECT else PasswordMode.PROTECT,
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(isWorking = false, error = e.message ?: "Cannot read file", sourceUri = null) }
            }
        }
    }

    fun setMode(m: PasswordMode) = _state.update { it.copy(mode = m, error = null, resultUri = null) }
    fun setNew(v: String) = _state.update { it.copy(newPassword = v) }
    fun setConfirm(v: String) = _state.update { it.copy(confirmPassword = v) }
    fun setCurrent(v: String) = _state.update { it.copy(currentPassword = v) }

    fun apply() {
        val snap = _state.value
        val src = snap.sourceUri ?: return
        if (snap.isWorking) return

        when (snap.mode) {
            PasswordMode.PROTECT -> {
                if (snap.newPassword.length < 4) {
                    _state.update { it.copy(error = "Password must be at least 4 characters.") }
                    return
                }
                if (snap.newPassword != snap.confirmPassword) {
                    _state.update { it.copy(error = "Passwords do not match.") }
                    return
                }
            }
            PasswordMode.UNPROTECT -> {
                if (snap.currentPassword.isBlank()) {
                    _state.update { it.copy(error = "Enter the current password.") }
                    return
                }
            }
        }

        _state.update { it.copy(isWorking = true, error = null, resultUri = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    when (snap.mode) {
                        PasswordMode.PROTECT -> protectOnDisk(getApplication(), src, snap.newPassword)
                        PasswordMode.UNPROTECT -> unprotectOnDisk(getApplication(), src, snap.currentPassword)
                    }
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

    private fun protectOnDisk(context: Context, src: Uri, password: String): Pair<Uri, String> {
        val bytes = FileIo.readBytes(context, src)
        val doc = PdfEngine.load(bytes)
        try {
            val out = PdfEngine.protect(doc, password)
            val name = "protected-${System.currentTimeMillis()}.pdf"
            val uri = PdfEngine.stageShare(context, name, out)
            return uri to name
        } finally {
            doc.close()
        }
    }

    private fun unprotectOnDisk(context: Context, src: Uri, password: String): Pair<Uri, String> {
        val bytes = FileIo.readBytes(context, src)
        val clean = PdfEngine.removeProtection(bytes, password)
        val name = "unlocked-${System.currentTimeMillis()}.pdf"
        val uri = PdfEngine.stageShare(context, name, clean)
        return uri to name
    }

    private fun messageFor(e: PdfEngine.PdfError): String = when (e) {
        is PdfEngine.PdfError.NeedsPassword -> "This PDF is password-protected."
        is PdfEngine.PdfError.WrongPassword -> "Incorrect password — try again."
        is PdfEngine.PdfError.Corrupt -> "PDF is damaged or unreadable."
        is PdfEngine.PdfError.Io -> "Could not read the file."
    }
}

@Composable
fun PasswordScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PasswordViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.loadPdf(context, it) }
    }

    ToolScaffold(
        title = stringResource(R.string.tool_password),
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
                        text = if (state.mode == PasswordMode.PROTECT) "Protect" else "Unlock",
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
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.tool_password),
                        subtitle = "Pick a PDF to encrypt, or to remove an existing password.",
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (state.isEncrypted) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (state.isEncrypted) "Encrypted" else "Not encrypted",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.mode == PasswordMode.PROTECT,
                            onClick = { vm.setMode(PasswordMode.PROTECT) },
                            label = { Text("Protect") },
                        )
                        FilterChip(
                            selected = state.mode == PasswordMode.UNPROTECT,
                            onClick = { vm.setMode(PasswordMode.UNPROTECT) },
                            label = { Text("Unlock") },
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    if (state.mode == PasswordMode.PROTECT) {
                        PasswordField(
                            value = state.newPassword,
                            onValueChange = vm::setNew,
                            label = "New password",
                        )
                        Spacer(Modifier.height(8.dp))
                        PasswordField(
                            value = state.confirmPassword,
                            onValueChange = vm::setConfirm,
                            label = "Confirm password",
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Note: this adds a password to open the PDF. Any existing digital signature will be invalidated.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        PasswordField(
                            value = state.currentPassword,
                            onValueChange = vm::setCurrent,
                            label = "Current password",
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Unlock removes the password so the PDF opens without one. Signatures are invalidated by the re-save.",
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
                    ResultBanner(success = true, message = "Saved — ${state.resultName}")
                }
            }

            if (state.isWorking) ProcessingOverlay()
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                )
            }
        },
    )
}

private fun sharePdf(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDF"))
}
