package com.imgad.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppDatabaseMigrationTest {
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(database: SupportSQLiteDatabase) {
                        database.execSQL(
                            "CREATE TABLE assets (id TEXT NOT NULL PRIMARY KEY, messageId TEXT, localUri TEXT NOT NULL, " +
                                "thumbnailUri TEXT, mediaType TEXT NOT NULL, width INTEGER, height INTEGER, " +
                                "byteSize INTEGER, source TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                        )
                    }

                    override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun migrationAddsAssetAvailabilityColumnWithTrueDefault() {
        AppDatabase.MIGRATION_1_2.migrate(helper.writableDatabase)

        helper.readableDatabase.query("PRAGMA table_info(assets)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "available") {
                    found = true
                    assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("notnull")))
                    assertEquals("1", cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")))
                }
            }
            assertTrue(found)
        }
    }
}
