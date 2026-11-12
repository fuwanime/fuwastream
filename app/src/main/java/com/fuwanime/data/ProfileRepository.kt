package com.fuwanime.data

import androidx.compose.runtime.Immutable
import androidx.room.withTransaction
import com.fuwanime.data.local.BorderEntity
import com.fuwanime.data.local.CountedEntity
import com.fuwanime.data.local.FuwaDatabase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine

@Immutable
data class QuestUi(val def: QuestDef, val progress: Int, val claimed: Boolean) {
    val done: Boolean get() = progress >= def.target
}

@Immutable
data class ProfileUi(
    val name: String,
    val xp: Long,
    val level: Int,
    val levelStartXp: Long,
    /** XP needed for the next level, or null at the top level. */
    val nextLevelXp: Long?,
    val quests: List<QuestUi>,
    val unlockedBorders: Set<String>,
    val equippedBorder: String?,
)

/** Outcome of a successful claim. */
data class Claimed(val xp: Long, val borderId: String)

/** Local profile, XP, quests and borders. Everything is on-device; there is no account. */
class ProfileRepository(private val db: FuwaDatabase) {
    private val dao = db.profileDao()

    private val _levelUps = MutableSharedFlow<Int>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits the new level whenever XP from any source crosses a level boundary. */
    val levelUps: SharedFlow<Int> = _levelUps

    fun observe(): Flow<ProfileUi> = combine(dao.observeProfile(), dao.observeQuests(), dao.observeBorders()) { p, rows, borders ->
        val xp = p?.xp ?: 0L
        val level = ProfileRules.levelFor(xp)
        ProfileUi(
            name = p?.name.orEmpty(),
            xp = xp,
            level = level,
            levelStartXp = ProfileRules.levels[level - 1].minXp,
            nextLevelXp = ProfileRules.levels.getOrNull(level)?.minXp,
            quests = ProfileRules.quests.map { d ->
                val row = rows.firstOrNull { it.id == d.id }
                QuestUi(d, row?.progress ?: 0, (row?.claimed ?: 0) == 1)
            },
            unlockedBorders = borders.toSet(),
            equippedBorder = p?.equippedBorder?.takeIf { it in borders },
        )
    }

    suspend fun setName(name: String) = db.withTransaction {
        dao.ensureProfile()
        dao.setName(name.trim().take(MAX_NAME))
    }

    /** Equips an unlocked border, or removes the border when [id] is null. False when the border is still locked. */
    suspend fun setEquippedBorder(id: String?): Boolean = db.withTransaction {
        dao.ensureProfile()
        if (id != null && dao.hasBorder(id) == 0) return@withTransaction false
        dao.setEquippedBorder(id)
        true
    }

    /**
     * The only path that turns playing time into XP and quest progress: one XP per
     * [ProfileRules.WATCH_XP_INTERVAL_MS], and the watch-time quest in whole seconds.
     */
    suspend fun recordWatch(ms: Long) {
        if (ms <= 0) return
        val change = db.withTransaction {
            ensureRows()
            val before = dao.xp() ?: 0L
            val gained = ProfileRules.xpForWatch(ms)
            dao.addXp(gained)
            val quest = checkNotNull(ProfileRules.quest(ProfileRules.QUEST_WATCH_SECONDS))
            dao.bumpQuest(quest.id, (ms / 1000).toInt(), quest.target)
            before to before + gained
        }
        announceLevelUp(change.first, change.second)
    }

    /** Counts [episodeId] once, however many times playback passes the finish threshold. */
    suspend fun onEpisodeWatched(episodeId: String) = countOnce("ep:$episodeId", ProfileRules.QUEST_WATCH_EPISODES)

    /** Counts [chapterId] once, however many pages are turned past the finish threshold. */
    suspend fun onChapterRead(chapterId: String) = countOnce("ch:$chapterId", ProfileRules.QUEST_READ_CHAPTERS)

    private suspend fun countOnce(key: String, questId: String) = db.withTransaction {
        ensureRows()
        if (dao.markCounted(CountedEntity(key)) == -1L) return@withTransaction
        val quest = checkNotNull(ProfileRules.quest(questId))
        dao.bumpQuest(quest.id, 1, quest.target)
    }

    /**
     * Pays out a finished quest. The claim is a conditional UPDATE, so a double tap changes a row only once
     * and only that first call gets XP and the border. Null when the quest is unfinished, claimed, or unknown.
     */
    suspend fun claim(questId: String): Claimed? {
        val def = ProfileRules.quest(questId) ?: return null
        val change = db.withTransaction {
            ensureRows()
            if (dao.markClaimed(def.id, def.target) == 0) return@withTransaction null
            val before = dao.xp() ?: 0L
            dao.addXp(def.rewardXp)
            dao.grantBorder(BorderEntity(def.borderId))
            before
        } ?: return null
        announceLevelUp(change, change + def.rewardXp)
        return Claimed(def.rewardXp, def.borderId)
    }

    /** Wipes profile, quests, counters and borders; used by "Hapus semua data". */
    suspend fun wipeAll() = db.withTransaction {
        dao.clearProfile(); dao.clearQuests(); dao.clearCounted(); dao.clearBorders()
    }

    private suspend fun ensureRows() {
        dao.ensureProfile()
        ProfileRules.quests.forEach { dao.ensureQuest(it.id) }
    }

    private fun announceLevelUp(before: Long, after: Long) {
        val level = ProfileRules.levelFor(after)
        if (level > ProfileRules.levelFor(before)) _levelUps.tryEmit(level)
    }

    companion object {
        const val MAX_NAME = 24
    }
}
