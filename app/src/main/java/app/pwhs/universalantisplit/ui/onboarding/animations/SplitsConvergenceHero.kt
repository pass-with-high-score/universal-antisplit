package app.pwhs.universalantisplit.ui.onboarding.animations

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalantisplit.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplitsConvergenceHero(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splits_hero")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val convergenceProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "convergence"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width * 0.42f
            val baseRadius = size.width * 0.22f

            // 1. Rotating dashed outer orbit ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.25f),
                radius = maxRadius,
                center = center,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 16f), rotation * 2f)
                )
            )

            // 2. Converging split nodes (4 nodes representing base, abi, lang, density)
            val angles = floatArrayOf(45f, 135f, 225f, 315f)
            val colors = arrayOf(primaryColor, secondaryColor, tertiaryColor, primaryColor)

            for (i in 0 until 4) {
                val angleRad = Math.toRadians((angles[i] + rotation * 0.25f).toDouble())
                // Interpolate from outer orbit towards the center logo
                val currentDist = maxRadius - (maxRadius - baseRadius) * convergenceProgress
                val nodeX = center.x + (currentDist * cos(angleRad)).toFloat()
                val nodeY = center.y + (currentDist * sin(angleRad)).toFloat()

                // Connecting particle line to center
                drawLine(
                    color = colors[i].copy(alpha = (1f - convergenceProgress * 0.5f) * 0.35f),
                    start = Offset(nodeX, nodeY),
                    end = center,
                    strokeWidth = 1.5.dp.toPx()
                )

                // Outer halo
                drawCircle(
                    color = colors[i].copy(alpha = 0.2f),
                    radius = 14.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )

                // Solid split node
                drawCircle(
                    color = colors[i],
                    radius = 6.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
            }

            // 3. Shockwave ripple bursting when nodes converge (at the end of cycle)
            if (convergenceProgress > 0.65f) {
                val rippleAlpha = (1f - (convergenceProgress - 0.65f) / 0.35f).coerceIn(0f, 1f)
                val rippleRadius = baseRadius + (maxRadius - baseRadius) * ((convergenceProgress - 0.65f) / 0.35f)
                drawCircle(
                    color = primaryColor.copy(alpha = rippleAlpha * 0.5f),
                    radius = rippleRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // 4. Central inner glow ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.25f),
                radius = baseRadius + 4.dp.toPx(),
                center = center
            )
        }

        // Center App Logo
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(20.dp))
        )
    }
}
