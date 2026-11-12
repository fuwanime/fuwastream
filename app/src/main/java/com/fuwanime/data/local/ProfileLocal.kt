package com.fuwanime.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** The single local profile row (id is always 1). An empty [name] means "show the default name". */
@Entity(tableName = "user_profile")
data class ProfileEntity(@PrimaryKey val id: Int = 1, val name: String, val xp: Long, val equippedBorder: String?)

/** Progress of one quest. Progress itself lives here only; the profile row keeps no quest counters. */
@Entity(tableName = "quest_progress")
data class QuestEntity(@PrimaryKey val id: String, val progress: Int, val claimed: Int)

/** Items (episode or chapter ids) that already counted towards a quest, so each counts once. */
@Entity(tableName = "counted_items")
data class CountedEntity(@PrimaryKey val key: String)

@Entity(tableName = "unlocked_borders")
data class BorderEntity(@PrimaryKey val id: String)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<ProfileEntity?>

    @Query("INSERT OR IGNORE INTO user_profile(id, name, xp, equippedBorder) VALUES(1, '', 0, NULL)")
    suspend fun ensureProfile()

    @Query("SELECT xp FROM user_profile WHERE id = 1")
    suspend fun xp(): Long?

    @Query("UPDATE user_profile SET name = :name WHERE id = 1")
    suspend fun setName(name: String)

    @Query("UPDATE user_profile SET xp = xp + :amount WHERE id = 1")
    suspend fun addXp(amount: Long)

    @Query("UPDATE user_profile SET equippedBorder = :id WHERE id = 1")
    suspend fun setEquippedBorder(id: String?)

    @Query("SELECT * FROM quest_progress")
    fun observeQuests(): Flow<List<QuestEntity>>

    @Query("INSERT OR IGNORE INTO quest_progress(id, progress, claimed) VALUES(:id, 0, 0)")
    suspend fun ensureQuest(id: String)

    /** Adds [by] to the quest, never past [cap]; a claimed quest stays frozen. */
    @Query("UPDATE quest_progress SET progress = MIN(progress + :by, :cap) WHERE id = :id AND claimed = 0")
    suspend fun bumpQuest(id: String, by: Int, cap: Int)

    /** Returns the number of changed rows: 1 only for the first claim of a finished quest. */
    @Query("UPDATE quest_progress SET claimed = 1 WHERE id = :id AND claimed = 0 AND progress >= :target")
    suspend fun markClaimed(id: String, target: Int): Int

    /** Returns -1 when [item] was already counted. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markCounted(item: CountedEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun grantBorder(border: BorderEntity)

    @Query("SELECT id FROM unlocked_borders")
    fun observeBorders(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM unlocked_borders WHERE id = :id")
    suspend fun hasBorder(id: String): Int

    @Query("DELETE FROM user_profile")
    suspend fun clearProfile()

    @Query("DELETE FROM quest_progress")
    suspend fun clearQuests()

    @Query("DELETE FROM counted_items")
    suspend fun clearCounted()

    @Query("DELETE FROM unlocked_borders")
    suspend fun clearBorders()
}
