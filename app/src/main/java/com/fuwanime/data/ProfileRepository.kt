package com.fuwanime.data

import androidx.room.withTransaction
import com.fuwanime.data.local.FuwaDatabase
import com.fuwanime.data.local.ProfileDao
import com.fuwanime.data.local.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Rank tags unlocked by level — 50 tiers. */
object RankTags {
    data class Tag(val title: String, val color: Long)

    val ALL = listOf(
        Tag("Pemula", 0xFF9E9E9E),           // 1
        Tag("Kadet", 0xFF8FD3F2),            // 2
        Tag("Newbie", 0xFF7DD3A8),           // 3
        Tag("Novice", 0xFF4ADE80),           // 4
        Tag("Penjelajah", 0xFF38BDF8),       // 5
        Tag("Petualang", 0xFF60A5FA),        // 6
        Tag("Penjaga", 0xFFA78BFA),          // 7
        Tag("Elite", 0xFFEC4899),            // 8
        Tag("Maha Guru", 0xFFF472B6),        // 9
        Tag("Menteri", 0xFFFB7185),          // 10
        Tag("Raja", 0xFFFBBF24),             // 11
        Tag("Dewa", 0xFFF97316),             // 12
        Tag("Legenda", 0xFFEF4444),          // 13
        Tag("Sepuh", 0xFFEAB308),            // 14
        Tag("Kiyora Sejati", 0xFFEC4899),    // 15
        Tag("Pemburu Musim", 0xFF22D3EE),    // 16
        Tag("Kolektor OP", 0xFF818CF8),      // 17
        Tag("Raja Maraton", 0xFF2DD4BF),     // 18
        Tag("Pengejar Episode", 0xFF34D399), // 19
        Tag("Tuan Waktu", 0xFFA3E635),       // 20
        Tag("Siluman Subtitle", 0xFFFDE047), // 21
        Tag("Kaisar Nonton", 0xFFFBBF24),    // 22
        Tag("Dewa Baca", 0xFFFB923C),        // 23
        Tag("Sang Kritikus", 0xFFF87171),    // 24
        Tag("Arsitek Watchlist", 0xFFE879F9),// 25
        Tag("Pengamat Jadwal", 0xFFC084FC),  // 26
        Tag("Ksatria Chapter", 0xFF60A5FA),  // 27
        Tag("Naga Offline", 0xFF4ADE80),     // 28
        Tag("Penyimpan Unduhan", 0xFF2DD4BF),// 29
        Tag("Maestro Kualitas", 0xFF38BDF8), // 30
        Tag("Meteor", 0xFFF472B6),           // 31
        Tag("Komet", 0xFFFB7185),            // 32
        Tag("Galaksi", 0xFFA78BFA),          // 33
        Tag("Nebula", 0xFF818CF8),           // 34
        Tag("Supernova", 0xFFF97316),        // 35
        Tag("Kuasar", 0xFF34D399),           // 36
        Tag("Pulsar", 0xFF22D3EE),           // 37
        Tag("Hollow", 0xFF94A3B8),           // 38
        Tag("Eclipse", 0xFF64748B),          // 39
        Tag("Samurai Salju", 0xFFE0F2FE),    // 40
        Tag("Shogun", 0xFFD62755),           // 41
        Tag("Onmyoji", 0xFF9F7AEA),          // 42
        Tag("Yokai", 0xFF2F855A),            // 43
        Tag("Ryu", 0xFFDD6B20),              // 44
        Tag("Kami", 0xFFD69E2E),             // 45
        Tag("Amaterasu", 0xFFF6AD55),        // 46
        Tag("Tsukuyomi", 0xFFB794F4),        // 47
        Tag("Susanoo", 0xFF63B3ED),          // 48
        Tag("Tenshi", 0xFFFEEBC8),           // 49
        Tag("Absolut", 0xFFFF2D95),          // 50
    )

    fun tagForLevel(level: Int): Tag = ALL[(level - 1).coerceIn(0, ALL.size - 1)]

    fun indexForLevel(level: Int): Int = (level - 1).coerceIn(0, ALL.size - 1)

    fun safe(index: Int): Tag = ALL[index.coerceIn(0, ALL.size - 1)]
}

object XpCurve {
    /**
     * Cumulative XP needed to REACH a level: a mixed quadratic+linear curve.
     * Level 2 needs 1201, and each level costs more than the last.
     */
    fun xpForLevel(level: Int): Long {
        if (level <= 1) return 0
        val l = (level - 1).toLong()
        return 500L * l + 701L * l * l + 50L * l * l * l
    }

    /** Level reached by a total XP amount. */
    fun levelFor(xp: Long): Int {
        var level = 1
        while (level < 999 && xpForLevel(level + 1) <= xp) level++
        return level
    }

    /** (level, xpIntoLevel, xpForNextLevel, progress 0..1) for a total XP. */
    fun progress(xp: Long): LevelProgress {
        val level = levelFor(xp)
        val base = xpForLevel(level)
        val next = xpForLevel(level + 1)
        val into = xp - base
        val span = (next - base).coerceAtLeast(1)
        return LevelProgress(level, into, next, into.toFloat() / span)
    }
}

data class LevelProgress(val level: Int, val xpIntoLevel: Long, val xpForNext: Long, val fraction: Float)

/** Profile + XP + quest counters: everything the local account owns. */
class ProfileRepository(private val db: FuwaDatabase) {
    private val dao: ProfileDao = db.profileDao()

    val profile: Flow<ProfileEntity> = dao.observe().map { it ?: ProfileEntity(updatedAt = System.currentTimeMillis()) }

    /** Ensures the single row exists before any write. */
    private suspend fun ensure(): ProfileEntity = dao.get() ?: ProfileEntity(updatedAt = System.currentTimeMillis()).also { dao.upsert(it) }

    val level: Flow<LevelProgress> = profile.map { XpCurve.progress(it.totalXp) }

    fun tagOf(p: ProfileEntity): RankTags.Tag = RankTags.safe(p.highestTagIndex)

    suspend fun rename(name: String, bio: String?, keepBio: Boolean = false) {
        val p = ensure()
        val nextBio = if (keepBio) p.bio else bio.orEmpty().trim()
        dao.upsert(p.copy(name = name.trim().ifBlank { p.name }, bio = nextBio, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setAvatar(path: String) {
        val p = ensure()
        dao.upsert(p.copy(avatarPath = path, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setBanner(path: String) {
        val p = ensure()
        dao.upsert(p.copy(bannerPath = path, updatedAt = System.currentTimeMillis()))
    }

    /** Selects the avatar border to show; must be one the user owns (or "none"). */
    suspend fun setEquippedBorder(borderId: String) {
        val p = ensure()
        val owned = p.ownedBorders.split(",").filter { it.isNotBlank() }
        if (borderId == "none" || owned.contains(borderId)) {
            dao.upsert(p.copy(equippedBorder = borderId, updatedAt = System.currentTimeMillis()))
        }
    }

    /** Adds XP directly (quest rewards) and refreshes the rank tag. */
    suspend fun addRawXp(xp: Long) {
        if (xp <= 0) return
        ensure()
        dao.addXp(xp)
        dao.raiseTag(RankTags.indexForLevel(XpCurve.levelFor(totalXp())))
    }

    /** Unlocks a border if not owned yet. */
    suspend fun grantBorder(borderId: String) {
        if (borderId.isBlank() || borderId == "none") return
        val p = ensure()
        val owned = p.ownedBorders.split(",").filter { it.isNotBlank() }.toMutableList()
        if (!owned.contains(borderId)) {
            owned.add(borderId)
            dao.upsert(p.copy(ownedBorders = owned.joinToString(","), updatedAt = System.currentTimeMillis()))
        }
    }

    /** Quest counters stored on the profile row; returns the new value. */
    suspend fun bumpQuestCounter(field: String, amount: Int = 1): Int {
        val p = ensure()
        val cur = when (field) {
            "episodes" -> p.qEpisodes
            "chapters" -> p.qChapters
            "favorites" -> p.qFavorites
            "searches" -> p.qSearches
            "downloads" -> p.qDownloads
            "minutes" -> p.qMinutes
            else -> 0
        }
        val next = cur + amount
        when (field) {
            "episodes" -> dao.setQEpisodes(next)
            "chapters" -> dao.setQChapters(next)
            "favorites" -> dao.setQFavorites(next)
            "searches" -> dao.setQSearches(next)
            "downloads" -> dao.setQDownloads(next)
            "minutes" -> dao.setQMinutes(next)
        }
        return next
    }

    /** 1 XP per 5 s of real playback. */
    suspend fun addWatchXp(ms: Long) {
        if (ms <= 0) return
        ensure()
        dao.addXp((ms / 5_000L).coerceAtLeast(1))
        dao.raiseTag(RankTags.indexForLevel(XpCurve.levelFor(totalXp())))
    }

    suspend fun totalXp(): Long = dao.get()?.totalXp ?: 0

    /** True when this write crossed into a new level; used for the "naik level" celebration. */
    suspend fun addWatchXpCheckingLevelUp(ms: Long): Boolean {
        if (ms <= 0) return false
        ensure()
        val before = XpCurve.levelFor(totalXp())
        val xp = (ms / 5_000L).coerceAtLeast(1)
        dao.addXp(xp)
        val after = XpCurve.levelFor(totalXp())
        dao.raiseTag(RankTags.indexForLevel(after))
        return after > before
    }

    suspend fun clearAll() = db.withTransaction { dao.clear() }
}
