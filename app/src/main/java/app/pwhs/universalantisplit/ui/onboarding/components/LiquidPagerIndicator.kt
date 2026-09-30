package app.pwhs.universalantisplit.ui.onboarding.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun LiquidPagerIndicator(
    pageCount: Int,
    currentPage: Int,
    currentPageOffsetFraction: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    indicatorSize: Dp = 8.dp,
    spacing: Dp = 10.dp,
) {
    val totalWidth = indicatorSize * (pageCount + 2) + spacing * (pageCount - 1)

    Canvas(
        modifier = modifier
            .width(totalWidth)
            .height(indicatorSize * 1.5f)
    ) {
        val sizePx = indicatorSize.toPx()
        val spacingPx = spacing.toPx()
        val cornerRadius = CornerRadius(sizePx / 2f, sizePx / 2f)

        // 1. Draw inactive dots
        var currentX = 0f
        for (i in 0 until pageCount) {
            val dotX = i * (sizePx + spacingPx)
            val centerY = size.height / 2f

            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(dotX, centerY - sizePx / 2f),
                size = Size(sizePx, sizePx),
                cornerRadius = cornerRadius
            )
        }

        // 2. Draw active expanding liquid pill
        val offset = currentPageOffsetFraction.coerceIn(-1f, 1f)
        val fromPage = currentPage
        val toPage = if (offset > 0) fromPage + 1 else if (offset < 0) fromPage - 1 else fromPage
        val absOffset = abs(offset)

        val baseWidth = sizePx
        val expandedWidth = sizePx * 3.2f

        val startX = fromPage * (sizePx + spacingPx)
        val targetX = toPage * (sizePx + spacingPx)

        val left: Float
        val right: Float

        if (offset >= 0) {
            left = startX + (targetX - startX) * (absOffset * 0.8f)
            right = startX + baseWidth + (targetX - startX) * (absOffset * 1.2f).coerceAtMost(1f)
        } else {
            left = startX + (targetX - startX) * (absOffset * 1.2f).coerceAtMost(1f)
            right = startX + baseWidth + (targetX - startX) * (absOffset * 0.8f)
        }

        val pillLeft = minOf(left, right)
        val pillRight = maxOf(left + baseWidth, right)
        val pillWidth = (pillRight - pillLeft).coerceAtLeast(baseWidth)

        val centerY = size.height / 2f
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(pillLeft, centerY - sizePx / 2f),
            size = Size(pillWidth, sizePx),
            cornerRadius = cornerRadius
        )
    }
}
