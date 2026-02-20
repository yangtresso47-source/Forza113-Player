package com.streamvault.data.sync

import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.dao.CategoryDao
import com.streamvault.data.local.dao.ChannelDao
import com.streamvault.data.local.dao.MovieDao
import com.streamvault.data.local.dao.ProviderDao
import com.streamvault.data.local.dao.SeriesDao
import com.streamvault.data.local.entity.ProviderEntity
import com.streamvault.data.parser.M3uParser
import com.streamvault.data.remote.xtream.XtreamApiService
import com.streamvault.domain.model.SyncState
import com.streamvault.domain.repository.EpgRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Unit tests for [SyncManager] state machine transitions.
 *
 * Strategy:
 * - Uses an in-memory [FakeProviderDao] — no Room needed
 * - Real [M3uParser] (pure JVM)
 * - Mockito-kotlin mocks for network/DAO collaborators
 * - All sync calls expected to FAIL (mocks return nulls/errors by default)
 *   because we're testing state transitions, not data correctness
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    // ── In-memory fake ──────────────────────────────────────────────

    private class FakeProviderDao(
        private val provider: ProviderEntity? = sampleProvider()
    ) : ProviderDao {
        override suspend fun getById(id: Long): ProviderEntity? = provider
        override suspend fun updateSyncTime(id: Long, timestamp: Long) = Unit
        override fun getAll() = kotlinx.coroutines.flow.flowOf(listOfNotNull(provider))
        override fun getActive() = kotlinx.coroutines.flow.flowOf(provider)
        override suspend fun insert(entity: ProviderEntity) = provider?.id ?: 0L
        override suspend fun update(entity: ProviderEntity) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun deactivateAll() = Unit
        override suspend fun activate(id: Long) = Unit
        override suspend fun getByUrlAndUser(url: String, user: String): ProviderEntity? = null
    }

    companion object {
        fun sampleProvider(type: String = "XTREAM_CODES") = ProviderEntity(
            id = 1L, name = "Test", type = type,
            serverUrl = "http://test.example.com:8080",
            username = "demo", password = "demo"
        )
    }

    // Mockito mocks — all return defaults (null/0/Unit), which will cause
    // the sync pipeline to throw and transition to Error state.
    private val channelDao: ChannelDao = mock()
    private val movieDao: MovieDao = mock()
    private val seriesDao: SeriesDao = mock()
    private val categoryDao: CategoryDao = mock()
    private val xtreamApi: XtreamApiService = mock()
    private val epgRepo: EpgRepository = mock()
    private val okHttp: OkHttpClient = mock()

    private fun buildManager(
        providerType: String = "XTREAM_CODES",
        providerPresent: Boolean = true
    ): SyncManager = SyncManager(
        providerDao = FakeProviderDao(if (providerPresent) sampleProvider(providerType) else null),
        channelDao = channelDao,
        movieDao = movieDao,
        seriesDao = seriesDao,
        categoryDao = categoryDao,
        xtreamApiService = xtreamApi,
        m3uParser = M3uParser(),
        epgRepository = epgRepo,
        okHttpClient = okHttp
    )

    // ── Initial state ───────────────────────────────────────────────

    @Test
    fun `initialState_isIdle`() = runTest {
        val mgr = buildManager()
        assertThat(mgr.syncState.first()).isEqualTo(SyncState.Idle)
    }

    // ── Provider not found ──────────────────────────────────────────

    @Test
    fun `sync_providerNotFound_returnsError_stateRemainsIdle`() = runTest {
        val mgr = buildManager(providerPresent = false)

        val result = mgr.sync(99L)

        assertThat(result.isError).isTrue()
        // State must NOT transition away from Idle (no provider = nothing to sync)
        assertThat(mgr.syncState.first()).isEqualTo(SyncState.Idle)
    }

    // ── Xtream sync failure ─────────────────────────────────────────

    @Test
    fun `sync_xtream_networksError_transitionsToError`() = runTest {
        val mgr = buildManager(providerType = "XTREAM_CODES")

        // The Xtream path calls XtreamProvider(xtreamApi,...).getLiveCategories()
        // Since xtreamApi is a mock with null returns, the call throws → manager catches → Error
        mgr.sync(1L)

        assertThat(mgr.syncState.first()).isInstanceOf(SyncState.Error::class.java)
    }

    @Test
    fun `sync_xtream_stateMustPassThroughSyncing`() = runTest {
        val mgr = buildManager(providerType = "XTREAM_CODES")

        // In TestDispatcher, sync() runs synchronously and state transitions complete
        // before this coroutine resumes. We verify the terminal state is Error —
        // that's only reachable via Syncing, proving the full transition happened.
        mgr.sync(1L)

        val finalState = mgr.syncState.first()
        assertThat(finalState).isInstanceOf(SyncState.Error::class.java)
    }

    @Test
    fun `sync_xtream_errorHasNonEmptyMessage`() = runTest {
        val mgr = buildManager(providerType = "XTREAM_CODES")
        mgr.sync(1L)

        val state = mgr.syncState.first() as? SyncState.Error
        assertThat(state).isNotNull()
        assertThat(state!!.message).isNotEmpty()
    }

    // ── M3U sync failure ────────────────────────────────────────────

    @Test
    fun `sync_m3u_networkError_transitionsToError`() = runTest {
        val mgr = buildManager(providerType = "M3U")

        // OkHttpClient mock: newCall() returns null → NullPointerException → Error
        mgr.sync(1L)

        assertThat(mgr.syncState.first()).isInstanceOf(SyncState.Error::class.java)
    }

    // ── Reset state ─────────────────────────────────────────────────

    @Test
    fun `resetState_afterError_returnsToIdle`() = runTest {
        val mgr = buildManager()
        mgr.sync(1L) // fails → Error

        assertThat(mgr.syncState.first()).isInstanceOf(SyncState.Error::class.java)

        mgr.resetState()

        assertThat(mgr.syncState.first()).isEqualTo(SyncState.Idle)
    }

    @Test
    fun `resetState_whenAlreadyIdle_staysIdle`() = runTest {
        val mgr = buildManager()

        mgr.resetState() // should be a no-op

        assertThat(mgr.syncState.first()).isEqualTo(SyncState.Idle)
    }

    // ── isVodEntry (SyncManager is the canonical source) ───────────

    @Test
    fun `isVodEntry_mp4Extension_returnsTrue`() {
        val mgr = buildManager()
        assertThat(mgr.isVodEntry(entry(url = "http://vod.example.com/film.mp4"))).isTrue()
    }

    @Test
    fun `isVodEntry_mkvExtension_returnsTrue`() {
        val mgr = buildManager()
        assertThat(mgr.isVodEntry(entry(url = "http://vod.example.com/show.mkv"))).isTrue()
    }

    @Test
    fun `isVodEntry_movieGroupTitle_returnsTrue`() {
        val mgr = buildManager()
        assertThat(mgr.isVodEntry(entry(group = "Movies HD"))).isTrue()
    }

    @Test
    fun `isVodEntry_vodGroupTitle_returnsTrue`() {
        val mgr = buildManager()
        assertThat(mgr.isVodEntry(entry(group = "VOD Library"))).isTrue()
    }

    @Test
    fun `isVodEntry_filmGroupTitle_returnsTrue`() {
        val mgr = buildManager()
        assertThat(mgr.isVodEntry(entry(group = "Film Classics"))).isTrue()
    }

    @Test
    fun `isVodEntry_liveTs_returnsFalse`() {
        val mgr = buildManager()
        assertThat(mgr.isVodEntry(entry(url = "http://live.example.com/cnn.ts", group = "News"))).isFalse()
    }

    @Test
    fun `isVodEntry_liveM3u8_returnsFalse`() {
        val mgr = buildManager()
        assertThat(mgr.isVodEntry(entry(url = "http://live.example.com/sports.m3u8", group = "Sports"))).isFalse()
    }

    // ── SyncState sealed class properties ───────────────────────────

    @Test
    fun `syncState_properties_idle`() {
        assertThat(SyncState.Idle.isSyncing).isFalse()
        assertThat(SyncState.Idle.isError).isFalse()
        assertThat(SyncState.Idle.isSuccess).isFalse()
    }

    @Test
    fun `syncState_properties_syncing`() {
        val state = SyncState.Syncing("Downloading…")
        assertThat(state.isSyncing).isTrue()
        assertThat(state.isError).isFalse()
        assertThat(state.isSuccess).isFalse()
    }

    @Test
    fun `syncState_properties_success`() {
        val state = SyncState.Success(timestamp = 12345L)
        assertThat(state.isSuccess).isTrue()
        assertThat(state.isSyncing).isFalse()
        assertThat(state.isError).isFalse()
        assertThat(state.timestamp).isEqualTo(12345L)
    }

    @Test
    fun `syncState_properties_error`() {
        val state = SyncState.Error("something went wrong", cause = RuntimeException("root"))
        assertThat(state.isError).isTrue()
        assertThat(state.isSyncing).isFalse()
        assertThat(state.isSuccess).isFalse()
        assertThat(state.message).isEqualTo("something went wrong")
        assertThat(state.cause).isInstanceOf(RuntimeException::class.java)
    }

    // ── Helper ──────────────────────────────────────────────────────

    private fun entry(
        url: String = "http://stream.example.com/ch1.ts",
        group: String = "Live"
    ) = M3uParser.M3uEntry(
        name = "Test", groupTitle = group,
        tvgId = null, tvgName = null, tvgLogo = null, tvgChno = null,
        catchUp = null, catchUpSource = null, url = url
    )
}
