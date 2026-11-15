package com.fuwanime.data

/** One quest: finish [target] units, then claim [rewardXp] and unlock [borderId]. */
data class QuestDef(val id: String, val target: Int, val rewardXp: Long, val borderId: String)

/** A level title and the colour it is drawn in. The name itself lives in strings_profile.xml. */
data class LevelDef(val minXp: Long, val color: Long)

data class BorderDef(val id: String, val color: Long)

/** Pure rules for levels, quests and borders; no Android types so they stay unit-testable. */
object ProfileRules {
    const val QUEST_WATCH_EPISODES = "watch_episodes"
    const val QUEST_READ_CHAPTERS = "read_chapters"
    /** Progress is stored in seconds; 60 minutes = 3600. */
    const val QUEST_WATCH_SECONDS = "watch_seconds"

    const val BORDER_SAKURA = "sakura"
    const val BORDER_OCEAN = "ocean"
    const val BORDER_GOLD = "gold"

    /** One XP per this much real playing time. */
    const val WATCH_XP_INTERVAL_MS = 5_000L

    /** An episode counts as watched past this share of its duration. */
    const val EPISODE_DONE_PERCENT = 92

    /** A chapter counts as read once the reader reaches this share of its pages. */
    const val CHAPTER_DONE_PERCENT = 90

    /** Uneven on purpose: level 2 needs 1201 XP. Index + 1 is the level. */
    val levels = listOf(
        LevelDef(0, 0xFF78909C),
        LevelDef(1_201, 0xFF66BB6A),
        LevelDef(3_000, 0xFF26A69A),
        LevelDef(6_000, 0xFF42A5F5),
        LevelDef(10_000, 0xFF7E57C2),
        LevelDef(16_000, 0xFFAB47BC),
        LevelDef(24_000, 0xFFEC407A),
        LevelDef(35_000, 0xFFFF7043),
        LevelDef(50_000, 0xFFFFCA28),
        LevelDef(70_000, 0xFFFF5252),
    )

    val borders = listOf(
        BorderDef(BORDER_SAKURA, 0xFFF48FB1),
        BorderDef(BORDER_OCEAN, 0xFF4FC3F7),
        BorderDef(BORDER_GOLD, 0xFFFFC107),
    )

    val quests = listOf(
        QuestDef(QUEST_WATCH_EPISODES, target = 5, rewardXp = 150, borderId = BORDER_SAKURA),
        QuestDef(QUEST_READ_CHAPTERS, target = 5, rewardXp = 150, borderId = BORDER_OCEAN),
        QuestDef(QUEST_WATCH_SECONDS, target = 60 * 60, rewardXp = 300, borderId = BORDER_GOLD),
    )

    fun quest(id: String): QuestDef? = quests.firstOrNull { it.id == id }

    fun border(id: String): BorderDef? = borders.firstOrNull { it.id == id }

    /** 1-based level for [xp]. */
    fun levelFor(xp: Long): Int = levels.indexOfLast { xp >= it.minXp }.coerceAtLeast(0) + 1

    fun xpForWatch(ms: Long): Long = ms.coerceAtLeast(0) / WATCH_XP_INTERVAL_MS

    fun episodeWatched(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs * 100 >= durationMs * EPISODE_DONE_PERCENT

    /** [page] is the 0-based index of the page on screen. */
    fun chapterRead(page: Int, pageCount: Int): Boolean =
        pageCount > 0 && (page + 1) * 100 >= pageCount * CHAPTER_DONE_PERCENT
}
