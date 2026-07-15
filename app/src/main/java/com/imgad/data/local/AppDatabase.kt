package com.imgad.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.imgad.data.local.dao.AssetDao
import com.imgad.data.local.dao.MessageDao
import com.imgad.data.local.dao.ModelProfileDao
import com.imgad.data.local.dao.ProviderDao
import com.imgad.data.local.dao.SessionDao
import com.imgad.data.local.entity.AssetEntity
import com.imgad.data.local.entity.MessageEntity
import com.imgad.data.local.entity.ModelProfileEntity
import com.imgad.data.local.entity.ProviderEntity
import com.imgad.data.local.entity.SessionEntity

@Database(
    entities = [
        ProviderEntity::class,
        ModelProfileEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        AssetEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao

    abstract fun modelProfileDao(): ModelProfileDao

    abstract fun sessionDao(): SessionDao

    abstract fun messageDao(): MessageDao

    abstract fun assetDao(): AssetDao

    companion object {
        const val DB_NAME = "img_ad.db"
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE assets ADD COLUMN available INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
