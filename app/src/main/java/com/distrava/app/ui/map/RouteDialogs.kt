package com.distrava.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.distrava.app.R
import com.distrava.app.data.db.ActivityEntity
import com.distrava.app.ui.Format

/** Nome do percurso: o nome dado ao favoritar ou, se não houver, a data. */
fun routeLabel(activity: ActivityEntity): String = activity.name ?: Format.dateTime(activity.startedAt)

/** Pede o nome ao favoritar; vem preenchido com a data e não pode ficar em branco. */
@Composable
fun FavoriteNameDialog(defaultName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.favorite_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text(stringResource(R.string.favorite_name_label)) },
                placeholder = { Text(stringResource(R.string.favorite_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.favorite_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Escolhe um percurso salvo para sobrepor no mapa.
 * [selectedId] é o atual (null = nenhum); [onPick] recebe o id escolhido ou null para limpar.
 */
@Composable
fun PickOverlayRouteDialog(
    routes: List<ActivityEntity>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.overlay_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.overlay_pick_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (routes.isEmpty()) {
                    Text(stringResource(R.string.overlay_empty))
                }
                LazyColumn(Modifier.fillMaxWidth()) {
                    item {
                        OverlayRow(stringResource(R.string.overlay_none), selectedId == null) { onPick(null) }
                    }
                    items(routes, key = { it.id }) { a ->
                        OverlayRow(routeLabel(a), a.id == selectedId) { onPick(a.id) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun OverlayRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
