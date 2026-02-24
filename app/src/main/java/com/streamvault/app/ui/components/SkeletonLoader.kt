package com.streamvault.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.shimmerEffect(): Modifier = composed {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.05f),
        Color.LightGray.copy(alpha = 0.2f),
    )

    val transition = rememberInfiniteTransition(label = "")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = ""
    )
    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnimation.value, y = translateAnimation.value)
        )
    )
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.DarkGray, RoundedCornerShape(8.dp))
            .shimmerEffect()
    )
}

@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    cardWidth: Int = 140,
    cardHeight: Int = 210,
    itemsCount: Int = 5
) {
    Column(modifier = modifier.padding(vertical = 16.dp)) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(24.dp)
                .background(Color.DarkGray, RoundedCornerShape(4.dp))
                .shimmerEffect()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(itemsCount) {
                SkeletonCard(
                    modifier = Modifier
                        .width(cardWidth.dp)
                        .height(cardHeight.dp)
                )
            }
        }
    }
}
