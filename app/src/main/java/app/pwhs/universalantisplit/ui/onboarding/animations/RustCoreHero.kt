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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RustCoreHero(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rust_core")

    val gearRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gear_rotation"
    )

    val reverseRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reverse_rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(190.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.width * 0.44f
            val midRadius = size.width * 0.33f
            val innerRadius = size.width * 0.22f

            // 1. Outer dashed ring rotating counter-clockwise
            drawCircle(
                color = secondaryColor.copy(alpha = 0.35f),
                radius = outerRadius,
                center = center,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), reverseRotation)
                )
            )

            // 2. Draw 6-sided hexagon core
            val hexPath = Path()
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60 + gearRotation).toDouble())
                val x = center.x + (midRadius * cos(angle)).toFloat()
                val y = center.y + (midRadius * sin(angle)).toFloat()
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()

            drawPath(
                path = hexPath,
                color = primaryColor.copy(alpha = 0.6f),
                style = Stroke(width = 3.dp.toPx())
            )

            // 3. Hexagon node points
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60 + gearRotation).toDouble())
                val x = center.x + (midRadius * cos(angle)).toFloat()
                val y = center.y + (midRadius * sin(angle)).toFloat()
                drawCircle(
                    color = primaryColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            }

            // 4. Inner glowing orb
            drawCircle(
                color = primaryColor.copy(alpha = 0.18f * pulse),
                radius = innerRadius * pulse,
                center = center
            )
        }

        // Center Bolt Icon (High speed energy)
        Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(46.dp)
        )
    }
}
