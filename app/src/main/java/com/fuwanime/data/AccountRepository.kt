package com.fuwanime.data

import android.util.Base64
import com.fuwanime.data.local.AccountDao
import com.fuwanime.data.local.AccountEntity
import com.fuwanime.data.local.FuwaDatabase
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local-only account: login/register state lives entirely on the device (Room), no server.
 * Passwords are stored as PBKDF2 hashes, never in plain text.
 */
class AccountRepository(private val db: FuwaDatabase) {
    private val dao: AccountDao = db.accountDao()

    val account: Flow<AccountEntity?> = dao.observe()

    suspend fun current(): AccountEntity? = dao.get()

    val isLoggedIn: Flow<Boolean> = dao.observe().map { it?.loggedIn == true }

    suspend fun register(email: String, password: String, displayName: String): Result<AccountEntity> {
        val clean = email.trim().lowercase()
        if (!clean.contains('@')) return Result.failure(IllegalArgumentException("Email tidak valid"))
        if (password.length < 6) return Result.failure(IllegalArgumentException("Kata sandi minimal 6 karakter"))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt)
        val entity = AccountEntity(
            email = clean,
            displayName = displayName.trim().ifBlank { clean.substringBefore('@') },
            passwordHash = hash,
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            provider = "local",
            loggedIn = true,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return Result.success(entity)
    }

    suspend fun login(email: String, password: String): Result<AccountEntity> {
        val existing = dao.get()
        val clean = email.trim().lowercase()
        if (existing == null || existing.email != clean || existing.provider != "local") {
            return Result.failure(IllegalArgumentException("Akun tidak ditemukan. Daftar dulu."))
        }
        val salt = Base64.decode(existing.salt, Base64.NO_WRAP)
        if (!pbkdf2(password, salt).equals(existing.passwordHash, constantTime = true)) {
            return Result.failure(IllegalArgumentException("Email atau kata sandi salah"))
        }
        dao.setLoggedIn(true)
        return Result.success(existing)
    }

    /** Device-local "Google" session: no real OAuth, nothing leaves the device. */
    suspend fun loginGoogle(email: String, displayName: String): AccountEntity {
        val clean = email.trim().lowercase().ifBlank { "fuwamanimereal@gmail.com" }
        val entity = AccountEntity(
            email = clean,
            displayName = displayName.trim().ifBlank { clean.substringBefore('@') },
            passwordHash = "",
            salt = "",
            provider = "google",
            loggedIn = true,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun logout() = dao.setLoggedIn(false)

    suspend fun deleteAccount() = dao.clear()

    /** Auto-syncs the profile name with the account display name (keeps the existing bio). */
    suspend fun syncProfileName(profile: ProfileRepository) {
        val acc = dao.get() ?: return
        profile.rename(acc.displayName, null, keepBio = true)
    }

    private fun pbkdf2(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, 60_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.encodeToString(factory.generateSecret(spec).encoded, Base64.NO_WRAP)
    }

    private fun String.equals(other: String, constantTime: Boolean): Boolean {
        if (length != other.length) return false
        var diff = 0
        for (i in indices) diff = diff or (this[i].code xor other[i].code)
        return diff == 0
    }
}
