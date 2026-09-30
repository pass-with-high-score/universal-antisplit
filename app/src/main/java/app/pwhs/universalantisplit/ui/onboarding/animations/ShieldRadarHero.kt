package app.pwhs.universalantisplit.ui.onboarding.animations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ShieldRadarHero(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_radar")

    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    val waveExpand by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_expand"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(190.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width * 0.45f

            // 1. Concentric radar guide circles
            drawCircle(
                color = primaryColor.copy(alpha = 0.15f),
                radius = maxRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.15f),
                radius = maxRadius * 0.65f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // 2. Expanding radar pulse wave
            val waveAlpha = (1f - waveExpand).coerceIn(0f, 1f) * 0.5f
            drawCircle(
                color = tertiaryColor.copy(alpha = waveAlpha),
                radius = maxRadius * waveExpand,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Sweeping radar beam line
            val rad = Math.toRadians(radarAngle.toDouble())
            val beamEnd = Offset(
                center.x + (maxRadius * cos(rad)).toFloat(),
                center.y + (maxRadius * sin(rad)).toFloat()
            )
            drawLine(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.8f), Color.Transparent),
                    center = center,
                    radius = maxRadius
                ),
                start = center,
                end = beamEnd,
                strokeWidth = 2.5.dp.toPx()
            )

            // 4. Central inner circle
            drawCircle(
                color = primaryColor.copy(alpha = 0.12f),
                radius = maxRadius * 0.42f,
                center = center
            )
        }

        // Center Shield Icon
        Icon(
            imageVector = Icons.Rounded.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        )
    }
}
