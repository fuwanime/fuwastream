package com.fuwanime.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Single-row (id = 1) local user profile: display name, bio and avatar/banner file paths. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Fuwa",
    val bio: String = "",
    /** Absolute file path of a picked avatar image; blank = default generated avatar. */
    val avatarPath: String = "",
    /** Absolute file path of a picked banner image; blank = default gradient. */
    val bannerPath: String = "",
    val totalXp: Long = 0,
    /** Highest rank tag ever unlocked, index into [com.fuwanime.data.RankTags.ALL] (50 tiers). */
    val highestTagIndex: Int = 0,
    /** Comma-separated owned avatar border ids (earned from quests). */
    val ownedBorders: String = "",
    /** Currently equipped border id ("none" = default ring). */
    val equippedBorder: String = "none",
    /** Quest cycle counters, reset with the 3-day quest cycle. */
    val qEpisodes: Int = 0,
    val qChapters: Int = 0,
    val qFavorites: Int = 0,
    val qSearches: Int = 0,
    val qDownloads: Int = 0,
    val qMinutes: Int = 0,
    val updatedAt: Long = 0,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(entity: ProfileEntity)

    @Query("UPDATE profile SET totalXp = totalXp + :xp WHERE id = 1")
    suspend fun addXp(xp: Long)

    @Query("UPDATE profile SET highestTagIndex = :index WHERE id = 1 AND highestTagIndex < :index")
    suspend fun raiseTag(index: Int)

    @Query("UPDATE profile SET qEpisodes = :value WHERE id = 1")
    suspend fun setQEpisodes(value: Int)

    @Query("UPDATE profile SET qChapters = :value WHERE id = 1")
    suspend fun setQChapters(value: Int)

    @Query("UPDATE profile SET qFavorites = :value WHERE id = 1")
    suspend fun setQFavorites(value: Int)

    @Query("UPDATE profile SET qSearches = :value WHERE id = 1")
    suspend fun setQSearches(value: Int)

    @Query("UPDATE profile SET qDownloads = :value WHERE id = 1")
    suspend fun setQDownloads(value: Int)

    @Query("UPDATE profile SET qMinutes = :value WHERE id = 1")
    suspend fun setQMinutes(value: Int)

    @Query("DELETE FROM profile")
    suspend fun clear()
}
