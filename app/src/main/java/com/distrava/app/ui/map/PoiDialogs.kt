package com.distrava.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.distrava.app.R
import com.distrava.app.data.db.PoiEntity
import com.distrava.app.data.db.PoiKind

@Composable
fun poiKindLabel(kind: PoiKind): String = stringResource(
    when (kind) {
        PoiKind.WATER -> R.string.poi_kind_water
        PoiKind.REPAIR -> R.string.poi_kind_repair
        PoiKind.RESTROOM -> R.string.poi_kind_restroom
        PoiKind.FOOD -> R.string.poi_kind_food
        PoiKind.OTHER -> R.string.poi_kind_other
    },
)

/** Criação de ponto de interesse; o nome vazio usa o nome do tipo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPoiDialog(onDismiss: () -> Unit, onSave: (name: String, kind: PoiKind) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(PoiKind.WATER) }
    val kindLabel = poiKindLabel(kind)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.poi_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PoiKind.entries.forEach { k ->
                        FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(poiKindLabel(k)) })
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.poi_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifEmpty { kindLabel }, kind) }) {
                Text(stringResource(R.string.poi_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun PoiDetailDialog(poi: PoiEntity, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(poi.name) },
        text = { Text(poiKindLabel(poi.poiKind), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.poi_delete), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
