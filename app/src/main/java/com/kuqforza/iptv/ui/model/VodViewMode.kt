package com.kuqforza.iptv.ui.model

enum class VodViewMode(val storageValue: String) {
    MODERN("modern"),
    CLASSIC("classic");

    companion object {
        fun fromStorage(value: String?): VodViewMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: MODERN
    }
}
