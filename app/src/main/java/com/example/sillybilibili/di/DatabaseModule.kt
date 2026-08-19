package com.example.sillybilibili.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sillybilibili.data.local.AppDatabase
import com.example.sillybilibili.data.local.dao.CategoryDao
import com.example.sillybilibili.data.local.dao.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE videos ADD COLUMN ownerName TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE videos ADD COLUMN quality TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE videos ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE videos ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE videos ADD COLUMN exportedPath TEXT")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE videos ADD COLUMN coverSourcePath TEXT")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE videos ADD COLUMN sourceAvailable INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE videos ADD COLUMN sourceLastSeenAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE videos ADD COLUMN exportedSize INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE videos ADD COLUMN exportedLastModified INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE videos ADD COLUMN onlineStatus TEXT NOT NULL DEFAULT 'UNCHECKED'")
            db.execSQL("ALTER TABLE videos ADD COLUMN onlineCheckedAt INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE videos ADD COLUMN pubdate INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "silly_bilibili.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()

    @Provides @Singleton
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides @Singleton
    fun provideVideoDao(database: AppDatabase): VideoDao = database.videoDao()
}
