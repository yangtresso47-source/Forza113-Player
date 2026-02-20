package com.streamvault.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.streamvault.data.local.dao.*
import com.streamvault.data.local.entity.*

@Database(
    entities = [
        ProviderEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        CategoryEntity::class,
        ProgramEntity::class,
        FavoriteEntity::class,
        VirtualGroupEntity::class
    ],
    version = 3,
    exportSchema = true   // ← was false; schema JSON now tracked in version control
)
abstract class StreamVaultDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun programDao(): ProgramDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun virtualGroupDao(): VirtualGroupDao

    companion object {
        /**
         * Migration 2 → 3: added parental-control protection columns.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE categories ADD COLUMN is_user_protected INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE channels ADD COLUMN is_user_protected INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE movies ADD COLUMN is_adult INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE movies ADD COLUMN is_user_protected INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE series ADD COLUMN is_adult INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE series ADD COLUMN is_user_protected INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE episodes ADD COLUMN is_adult INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE episodes ADD COLUMN is_user_protected INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Placeholder for the next schema change.  Add ALTER TABLE statements here when
        // an entity is modified before bumping version to 4.
        // val MIGRATION_3_4 = object : Migration(3, 4) { ... }
    }
}
