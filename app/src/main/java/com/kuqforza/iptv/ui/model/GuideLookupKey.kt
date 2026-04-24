package com.kuqforza.iptv.ui.model

import com.kuqforza.domain.model.Channel

fun Channel.guideLookupKey(): String? {
    return epgChannelId?.trim()?.takeIf { it.isNotEmpty() }
        ?: streamId.takeIf { it > 0L }?.toString()
}
