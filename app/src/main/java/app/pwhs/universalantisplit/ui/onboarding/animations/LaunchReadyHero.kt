package app.pwhs.universalantisplit.ui.onboarding.animations

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LaunchReadyHero(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "launch_hero")

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating_offset"
    )

    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(190.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val orbitRadius = size.width * 0.42f
            val haloRadius = size.width * 0.28f

            // 1. Orbit dashed ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.22f),
                radius = orbitRadius,
                center = center,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f), orbitRotation)
                )
            )

            // 2. Orbiting stars / energy nodes
            val angles = floatArrayOf(0f, 120f, 240f)
            for (angle in angles) {
                val rad = Math.toRadians((angle + orbitRotation).toDouble())
                val x = center.x + (orbitRadius * cos(rad)).toFloat()
                val y = center.y + (orbitRadius * sin(rad)).toFloat()

                drawCircle(
                    color = primaryColor.copy(alpha = 0.3f),
                    radius = 8.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = primaryColor,
                    radius = 3.5f.dp.toPx(),
                    center = Offset(x, y)
                )
            }

            // 3. Central glowing radial aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f * glowPulse),
                        tertiaryColor.copy(alpha = 0.15f * glowPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = haloRadius * 1.5f
                ),
                radius = haloRadius * 1.5f,
                center = center
            )
        }

        // Animated Floating Rocket Icon
        Icon(
            imageVector = Icons.Rounded.RocketLaunch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(56.dp)
                .offset(y = floatOffset.dp)
        )
    }
}
