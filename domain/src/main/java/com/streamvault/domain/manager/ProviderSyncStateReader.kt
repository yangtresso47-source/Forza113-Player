package com.kuqforza.domain.manager

import com.kuqforza.domain.model.SyncState

interface ProviderSyncStateReader {
    fun currentSyncState(providerId: Long): SyncState
}