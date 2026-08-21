package com.example.sillybilibili.data.local

import androidx.room.Room
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseIndexTest {

    @Test
    fun `video library has indexes for available and category sorted browsing`() {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val indexes = buildSet {
                database.openHelper.writableDatabase.query("PRAGMA index_list('videos')").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }

            assertTrue(indexes.contains("index_videos_source_available_added_at"))
            assertTrue(indexes.contains("index_videos_source_available_category_added_at"))
        } finally {
            database.close()
        }
    }
}
