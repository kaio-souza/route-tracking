package com.distrava.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.distrava.app.R
import com.distrava.app.recording.ElevationProfile
import com.distrava.app.ui.Format
import com.distrava.app.ui.theme.DistravaOrange
import kotlin.math.roundToInt

/** Perfil de altitude pela distância, com área preenchida. */
@Composable
fun ElevationChart(profile: ElevationProfile, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Text(
            stringResource(R.string.label_elevation_profile).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = muted,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val s = profile.samples
            if (s.size < 2) return@Canvas
            val maxDist = s.last().first.coerceAtLeast(1.0)
            // Margem de 5 m para terreno plano não virar um serrote.
            val span = (profile.maxM - profile.minM).coerceAtLeast(10.0)
            val base = profile.minM - (span - (profile.maxM - profile.minM)) / 2
            fun x(d: Double) = (d / maxDist * size.width).toFloat()
            fun y(a: Double) = (size.height - (a - base) / span * size.height * 0.9 - size.height * 0.05).toFloat()

            val line = Path().apply {
                moveTo(x(s.first().first), y(s.first().second))
                for (i in 1 until s.size) lineTo(x(s[i].first), y(s[i].second))
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(s.last().first), size.height)
                lineTo(x(s.first().first), size.height)
                close()
            }
            drawPath(area, DistravaOrange.copy(alpha = 0.18f))
            drawPath(line, DistravaOrange, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.elevation_range, profile.minM.roundToInt(), profile.maxM.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                Format.distance(profile.samples.last().first),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}
