package io.github.romanvht.byedpi.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.romanvht.byedpi.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A shared setting waiting to be added: from a scanned code, a tapped link or a paste. */
object StrategyInbox {
    private val _pending = MutableStateFlow<StrategyShare.Shared?>(null)
    val pending: StateFlow<StrategyShare.Shared?> = _pending.asStateFlow()

    /** Queues [text]'s setting; false when it isn't one that can be added. */
    fun offer(text: String): Boolean {
        val shared = StrategyShare.read(text) ?: return false
        _pending.value = shared
        return true
    }

    fun clear() {
        _pending.value = null
    }
}

/** [text] as a QR code, one pixel per module (scaled up without smoothing when drawn). */
private fun qrBitmap(text: String): Bitmap? = runCatching {
    val m = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    )
    val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}.getOrNull()

/** "Share this setting": a QR code to scan with another phone, and the same link as text. */
@Composable
fun ShareStrategyDialog(name: String, command: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val link = remember(name, command) { StrategyShare.link(name, command) }
    val qr = remember(link) { qrBitmap(link) }
    val qrLabel = stringResource(R.string.pg_share_qr)
    val message = stringResource(R.string.pg_share_message, name, command, link)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(stringResource(R.string.pg_share_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (qr != null) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            qr.asImageBitmap(), null, filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = qrLabel }
                        )
                    }
                }
                HelperText(stringResource(R.string.pg_share_body))
                Text(command, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.Sky)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
                runCatching { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text(stringResource(R.string.pg_share_as_text)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pg_close)) } }
    )
}

/** "Add this setting?": what someone shared, in plain words, before it's added. */
@Composable
fun ImportStrategyDialog(shared: StrategyShare.Shared, onAdd: () -> Unit, onUse: () -> Unit, onDismiss: () -> Unit) {
    val name = shared.name.ifBlank { stringResource(R.string.pg_shared_default) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(stringResource(R.string.pg_import_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.pg_import_from, name), fontSize = 15.sp)
                Text(shared.command, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.Sky)
                CommandExplainer(shared.command, max = 4)
            }
        },
        confirmButton = { TextButton(onClick = onUse) { Text(stringResource(R.string.pg_import_use)) } },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onAdd) { Text(stringResource(R.string.pg_import_add)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.pg_cancel)) }
            }
        }
    )
}
