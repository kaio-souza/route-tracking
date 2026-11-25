package com.distrava.app.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.distrava.app.R
import com.distrava.app.share.QrPayload

/** Mostra o QR de um texto já codificado para ser escaneado por outro celular. */
@Composable
fun ShareQrDialog(title: String, hint: String, text: String, onDismiss: () -> Unit) {
    val bitmap = remember(text) { QrPayload.toBitmap(text, 720).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxWidth())
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
