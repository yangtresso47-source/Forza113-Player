package com.streamvault.domain.model

data class Category(
    val id: Long,
    val name: String,
    val parentId: Long? = null,
    val type: ContentType = ContentType.LIVE,
    val isVirtual: Boolean = false,
    val count: Int = 0
)
