package com.fuwanime.data.local

import androidx.room.Upsert
import com.squareup.moshi.JsonClass
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Real playing time per local calendar day ([day] = epoch day), for the statistics screen. */
@JsonClass(generateAdapter = true)
@Entity(tableName = "watch_daily")
data class WatchDailyEntity(@PrimaryKey val day: Int, val watchedMs: Long)

@Dao
interface WatchDailyDao {
    @Query("INSERT OR IGNORE INTO watch_daily(day, watchedMs) VALUES(:day, 0)")
    suspend fun insertZero(day: Int)

    @Query("UPDATE watch_daily SET watchedMs = watchedMs + :ms WHERE day = :day")
    suspend fun add(day: Int, ms: Long)

    /** Two statements because SQLite before API 30 has no `ON CONFLICT DO UPDATE`. */
    @Transaction
    suspend fun addWatchTime(day: Int, ms: Long) {
        insertZero(day)
        add(day, ms)
    }

    @Query("SELECT * FROM watch_daily")
    fun observeAll(): Flow<List<WatchDailyEntity>>

    @Query("SELECT * FROM watch_daily")
    suspend fun all(): List<WatchDailyEntity>

    @Upsert
    suspend fun upsertAll(entities: List<WatchDailyEntity>)

    @Query("DELETE FROM watch_daily")
    suspend fun clear()
}
