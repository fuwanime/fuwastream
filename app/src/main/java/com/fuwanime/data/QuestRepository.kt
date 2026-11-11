package com.fuwanime.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import com.fuwanime.data.local.FuwaDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Device-local quest board: reset every 3 days. */
@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey val id: String,
    /** quest id template this row was generated from. */
    val templateId: String,
    val title: String,
    val target: Int,
    val progress: Int,
    val xpReward: Int,
    /** Border id unlocked when finished ("" = none). */
    val borderReward: String,
    val cycleStart: Long,
    val done: Boolean,
    val claimed: Boolean,
)

@Dao
interface QuestDao {
    @Query("SELECT * FROM quests")
    fun observeAll(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests")
    suspend fun all(): List<QuestEntity>

    @Upsert
    suspend fun upsertAll(entities: List<QuestEntity>)

    @Upsert
    suspend fun upsert(entity: QuestEntity)

    @Query("DELETE FROM quests")
    suspend fun clear()
}

/** Avatar borders earned from quests. */
object AvatarBorders {
    data class Border(val id: String, val title: String, val color: Long, val glow: Long)

    val ALL = listOf(
        Border("none", "Tanpa Border", 0xFF6B6478, 0x00000000),
        Border("silver", "Silver", 0xFFC0C6D4, 0x66C0C6D4),
        Border("gold", "Gold", 0xFFFBBF24, 0x66FBBF24),
        Border("rose", "Rose", 0xFFEC4899, 0x66EC4899),
        Border("violet", "Violet", 0xFF8B5CF6, 0x668B5CF6),
        Border("emerald", "Emerald", 0xFF34D399, 0x6634D399),
        Border("azure", "Azure", 0xFF38BDF8, 0x6638BDF8),
        Border("ember", "Ember", 0xFFF97316, 0x66F97316),
        Border("crimson", "Crimson", 0xFFEF4444, 0x66EF4444),
        Border("aurora", "Aurora", 0xFF4ADE80, 0x664ADE80),
        Border("nebula", "Nebula", 0xFFA78BFA, 0x66A78BFA),
        Border("royal", "Royal", 0xFFEAB308, 0x66EAB308),
    )

    fun byId(id: String): Border = ALL.firstOrNull { it.id == id } ?: ALL[0]
}

/** Templates for the rotating quest board. */
object QuestTemplates {
    data class Template(
        val id: String,
        val title: String,
        val target: Int,
        val xp: Int,
        val border: String = "",
    )

    val ALL = listOf(
        Template("watch5", "Tonton 5 episode", 5, 200),
        Template("watch10", "Tonton 10 episode", 10, 400, "silver"),
        Template("watch25", "Tonton 25 episode", 25, 900, "rose"),
        Template("read3", "Baca 3 chapter manga", 3, 150),
        Template("read8", "Baca 8 chapter manga", 8, 350, "azure"),
        Template("read15", "Baca 15 chapter manga", 15, 700, "emerald"),
        Template("fav3", "Favoritkan 3 anime", 3, 180),
        Template("search5", "Lakukan 5 pencarian", 5, 120),
        Template("dl1", "Unduh 1 episode", 1, 150, "violet"),
        Template("watch60", "Tonton total 60 menit", 60, 500, "gold"),
    )
}

/** Quest progress + border inventory, reset every 3 days. */
class QuestRepository(private val db: FuwaDatabase, private val profile: ProfileRepository) {
    private val dao: QuestDao = db.questDao()

    companion object {
        /** 3 days in ms. */
        const val CYCLE_MS = 3L * 24 * 60 * 60 * 1000
    }

    val quests: Flow<List<QuestEntity>> = dao.observeAll().map { list -> ensureCycle(list) }

    /** Picks 5 templates per cycle deterministically from the day number. */
    private suspend fun ensureCycle(existing: List<QuestEntity>): List<QuestEntity> {
        val now = System.currentTimeMillis()
        val current = existing.firstOrNull()?.cycleStart
        if (current != null && now - current < CYCLE_MS && existing.isNotEmpty()) return existing
        // New 3-day cycle: seed a stable random-ish pick.
        val cycle = now / CYCLE_MS
        val seed = (cycle * 2654435761L) xor 0x9E3779B9
        val picks = QuestTemplates.ALL.shuffled(java.util.Random(seed)).take(5)
        val start = cycle * CYCLE_MS
        val rows = picks.map {
            QuestEntity(
                id = "${it.id}@$cycle",
                templateId = it.id,
                title = it.title,
                target = it.target,
                progress = 0,
                xpReward = it.xp,
                borderReward = it.border,
                cycleStart = start,
                done = false,
                claimed = false,
            )
        }
        db.withTransaction { dao.clear(); dao.upsertAll(rows) }
        return rows
    }

    /** Progress counters come from app events; idempotent within a cycle. */
    suspend fun increment(kind: QuestKind, amount: Int = 1) {
        val list = ensureCycle(dao.all())
        val updated = list.map { q ->
            val matches = when (kind) {
                QuestKind.WATCH -> q.templateId.startsWith("watch")
                QuestKind.READ -> q.templateId.startsWith("read")
                QuestKind.FAVORITE -> q.templateId.startsWith("fav")
                QuestKind.SEARCH -> q.templateId.startsWith("search")
                QuestKind.DOWNLOAD -> q.templateId.startsWith("dl")
                QuestKind.MINUTES -> q.templateId == "watch60"
            }
            if (!matches || q.done) q else {
                val p = (q.progress + amount).coerceAtMost(q.target)
                q.copy(progress = p, done = p >= q.target)
            }
        }
        dao.upsertAll(updated)
    }

    /** Minutes variant accumulates instead of counting events. */
    suspend fun addMinutes(minutes: Int) {
        val list = ensureCycle(dao.all())
        val updated = list.map { q ->
            if (q.templateId != "watch60" || q.done) q else {
                val p = (q.progress + minutes).coerceAtMost(q.target)
                q.copy(progress = p, done = p >= q.target)
            }
        }
        dao.upsertAll(updated)
    }

    /** Claims a finished quest: XP + border (and possibly a new rank tag). */
    suspend fun claim(quest: QuestEntity): AvatarBorders.Border? {
        if (!quest.done || quest.claimed) return null
        dao.upsert(quest.copy(claimed = true))
        profile.addRawXp(quest.xpReward.toLong())
        return if (quest.borderReward.isNotBlank()) AvatarBorders.byId(quest.borderReward) else null
    }
}

enum class QuestKind { WATCH, READ, FAVORITE, SEARCH, DOWNLOAD, MINUTES }
