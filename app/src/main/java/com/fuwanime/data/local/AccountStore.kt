package com.fuwanime.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Single-row (id = 1) LOCAL account: credentials never leave the device. */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val id: Int = 1,
    val email: String,
    val displayName: String,
    /** PBKDF2 hash of the password, blank when [provider] = google (device-local session only). */
    val passwordHash: String = "",
    val salt: String = "",
    /** "local" (email+password) or "google" (device-local Google-style session). */
    val provider: String = "local",
    val loggedIn: Boolean = true,
    val createdAt: Long = 0,
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM account WHERE id = 1")
    fun observe(): Flow<AccountEntity?>

    @Query("SELECT * FROM account WHERE id = 1")
    suspend fun get(): AccountEntity?

    @Upsert
    suspend fun upsert(entity: AccountEntity)

    @Query("UPDATE account SET loggedIn = :loggedIn WHERE id = 1")
    suspend fun setLoggedIn(loggedIn: Boolean)

    @Query("DELETE FROM account")
    suspend fun clear()
}
