package com.kuqforza.iptv.ui.theme

import com.kuqforza.iptv.ui.design.AppSpacing
import com.kuqforza.iptv.ui.design.LocalAppSpacing

typealias Spacing = AppSpacing

val LocalSpacing = LocalAppSpacing

fun defaultSpacing(): Spacing = AppSpacing()
