package com.kuqforza.iptv.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.kuqforza.iptv.BuildConfig
import com.kuqforza.data.local.KuqforzaDatabase
import com.kuqforza.data.local.dao.*
import com.kuqforza.data.local.dao.ChannelPreferenceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DEBUG_SLOW_QUERY_THRESHOLD_MS = 100L

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KuqforzaDatabase =
        Room.databaseBuilder(
            context,
            KuqforzaDatabase::class.java,
            "kuqforza.db"
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .openHelperFactory(
                if (BuildConfig.DEBUG) {
                    SlowQueryLoggingOpenHelperFactory(
                        delegate = FrameworkSQLiteOpenHelperFactory(),
                        slowQueryThresholdMs = DEBUG_SLOW_QUERY_THRESHOLD_MS
                    )
                } else {
                    FrameworkSQLiteOpenHelperFactory()
                }
            )
            .addMigrations(
                KuqforzaDatabase.MIGRATION_1_2,
                KuqforzaDatabase.MIGRATION_2_3,
                KuqforzaDatabase.MIGRATION_3_4,
                KuqforzaDatabase.MIGRATION_4_5,
                KuqforzaDatabase.MIGRATION_5_6,
                KuqforzaDatabase.MIGRATION_6_7,
                KuqforzaDatabase.MIGRATION_7_8,
                KuqforzaDatabase.MIGRATION_8_9,
                KuqforzaDatabase.MIGRATION_9_10,
                KuqforzaDatabase.MIGRATION_10_11,
                KuqforzaDatabase.MIGRATION_11_12,
                KuqforzaDatabase.MIGRATION_12_13,
                KuqforzaDatabase.MIGRATION_13_14,
                KuqforzaDatabase.MIGRATION_14_15,
                KuqforzaDatabase.MIGRATION_15_16,
                KuqforzaDatabase.MIGRATION_16_17,
                KuqforzaDatabase.MIGRATION_17_18,
                KuqforzaDatabase.MIGRATION_18_19,
                KuqforzaDatabase.MIGRATION_19_20,
                KuqforzaDatabase.MIGRATION_20_21,
                KuqforzaDatabase.MIGRATION_21_22,
                KuqforzaDatabase.MIGRATION_22_23,
                KuqforzaDatabase.MIGRATION_23_24,
                KuqforzaDatabase.MIGRATION_24_25,
                KuqforzaDatabase.MIGRATION_25_26,
                KuqforzaDatabase.MIGRATION_26_27,
                KuqforzaDatabase.MIGRATION_27_28,
                KuqforzaDatabase.MIGRATION_28_29,
                KuqforzaDatabase.MIGRATION_29_30,
                KuqforzaDatabase.MIGRATION_30_31,
                KuqforzaDatabase.MIGRATION_31_32,
                KuqforzaDatabase.MIGRATION_32_33,
                KuqforzaDatabase.MIGRATION_33_34,
                KuqforzaDatabase.MIGRATION_34_35,
                KuqforzaDatabase.MIGRATION_35_36,
                KuqforzaDatabase.MIGRATION_36_37,
                KuqforzaDatabase.MIGRATION_37_38,
                KuqforzaDatabase.MIGRATION_38_39,
                KuqforzaDatabase.MIGRATION_39_40
            )
            // NOTE: fallbackToDestructiveMigration() intentionally removed.
            // All future schema changes MUST add a corresponding Migration in KuqforzaDatabase.
            .build()

    @Provides fun provideProviderDao(db: KuqforzaDatabase): ProviderDao = db.providerDao()
    @Provides fun provideChannelDao(db: KuqforzaDatabase): ChannelDao = db.channelDao()
    @Provides fun provideChannelPreferenceDao(db: KuqforzaDatabase): ChannelPreferenceDao = db.channelPreferenceDao()
    @Provides fun provideMovieDao(db: KuqforzaDatabase): MovieDao = db.movieDao()
    @Provides fun provideSeriesDao(db: KuqforzaDatabase): SeriesDao = db.seriesDao()
    @Provides fun provideEpisodeDao(db: KuqforzaDatabase): EpisodeDao = db.episodeDao()
    @Provides fun provideCategoryDao(db: KuqforzaDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideCatalogSyncDao(db: KuqforzaDatabase): CatalogSyncDao = db.catalogSyncDao()
    @Provides fun provideProgramDao(db: KuqforzaDatabase): ProgramDao = db.programDao()
    @Provides fun provideFavoriteDao(db: KuqforzaDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideVirtualGroupDao(db: KuqforzaDatabase): VirtualGroupDao = db.virtualGroupDao()
    @Provides fun providePlaybackHistoryDao(db: KuqforzaDatabase): PlaybackHistoryDao = db.playbackHistoryDao()
    @Provides fun provideTmdbIdentityDao(db: KuqforzaDatabase): TmdbIdentityDao = db.tmdbIdentityDao()
    @Provides fun provideSearchHistoryDao(db: KuqforzaDatabase): SearchHistoryDao = db.searchHistoryDao()
    @Provides fun provideSyncMetadataDao(db: KuqforzaDatabase): SyncMetadataDao = db.syncMetadataDao()
    @Provides fun provideMovieCategoryHydrationDao(db: KuqforzaDatabase): MovieCategoryHydrationDao = db.movieCategoryHydrationDao()
    @Provides fun provideSeriesCategoryHydrationDao(db: KuqforzaDatabase): SeriesCategoryHydrationDao = db.seriesCategoryHydrationDao()
    @Provides fun provideEpgSourceDao(db: KuqforzaDatabase): EpgSourceDao = db.epgSourceDao()
    @Provides fun provideProviderEpgSourceDao(db: KuqforzaDatabase): ProviderEpgSourceDao = db.providerEpgSourceDao()
    @Provides fun provideEpgChannelDao(db: KuqforzaDatabase): EpgChannelDao = db.epgChannelDao()
    @Provides fun provideEpgProgrammeDao(db: KuqforzaDatabase): EpgProgrammeDao = db.epgProgrammeDao()
    @Provides fun provideChannelEpgMappingDao(db: KuqforzaDatabase): ChannelEpgMappingDao = db.channelEpgMappingDao()
    @Provides fun provideCombinedM3uProfileDao(db: KuqforzaDatabase): CombinedM3uProfileDao = db.combinedM3uProfileDao()
    @Provides fun provideCombinedM3uProfileMemberDao(db: KuqforzaDatabase): CombinedM3uProfileMemberDao = db.combinedM3uProfileMemberDao()
    @Provides fun provideRecordingScheduleDao(db: KuqforzaDatabase): RecordingScheduleDao = db.recordingScheduleDao()
    @Provides fun provideRecordingRunDao(db: KuqforzaDatabase): RecordingRunDao = db.recordingRunDao()
    @Provides fun provideProgramReminderDao(db: KuqforzaDatabase): ProgramReminderDao = db.programReminderDao()
    @Provides fun provideRecordingStorageDao(db: KuqforzaDatabase): RecordingStorageDao = db.recordingStorageDao()
}
