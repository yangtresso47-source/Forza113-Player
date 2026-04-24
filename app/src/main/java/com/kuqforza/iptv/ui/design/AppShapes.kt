package com.kuqforza.iptv.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

data class AppShapes(
    val xSmall: RoundedCornerShape = RoundedCornerShape(10.dp),
    val small:  RoundedCornerShape = RoundedCornerShape(14.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(20.dp),
    val large:  RoundedCornerShape = RoundedCornerShape(26.dp),
    val xLarge: RoundedCornerShape = RoundedCornerShape(32.dp),
    val pill:   RoundedCornerShape = RoundedCornerShape(999.dp)
)

val LocalAppShapes = staticCompositionLocalOf { AppShapes() }
