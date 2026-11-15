package com.fuwanime

import com.fuwanime.data.manga.MangaStatus
import com.fuwanime.data.manga.comixChapter
import com.fuwanime.data.manga.comixManga
import com.fuwanime.data.manga.comixPageUrls
import com.fuwanime.data.manga.mangaPageCandidates
import com.fuwanime.data.manga.sourceRank
import com.fuwanime.data.manga.translationChunks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComixMappingTest {
    @Test fun mapsStableBridgeOutput() {
        val manga = comixManga(
            mapOf(
                "hid" to "x0ynk",
                "title" to "Villains Are Destined to Die",
                "type" to "manhwa",
                "status" to "finished",
                "poster" to mapOf("medium" to "/images/covers/x@280.jpg", "large" to "/images/covers/x.jpg"),
                "synopsis" to "A villain story.",
                "year" to 2020,
                "altTitles" to listOf("Alt Name"),
                "tags" to listOf("Action", "Drama"),
                "authors" to listOf("Author"),
            ),
        )
        val chapter = comixChapter(
            mapOf(
                "id" to 11_723_370,
                "number" to "214",
                "title" to "Finale",
                "language" to "en",
                "group" to "TappyToon",
                "pages" to 1,
                "relativeTime" to "2d ago",
            ),
            "x0ynk",
        )
        val pages = comixPageUrls(
            mapOf(
                "pages" to listOf(
                    mapOf(
                        "url" to "https://cdn.example/1.webp",
                        "urls" to listOf("https://cdn.example/1.webp", "https://backup.example/1.webp"),
                    ),
                ),
            ),
        )

        assertEquals("cx:x0ynk", manga.id)
        assertEquals("Villains Are Destined to Die", manga.title)
        assertEquals("https://comix.ws/images/covers/x.jpg", manga.coverUrl)
        assertEquals(listOf("Action", "Drama"), manga.tags)
        assertEquals("Author", manga.author)
        assertEquals(MangaStatus.COMPLETED, manga.status)
        assertEquals("cx:x0ynk:11723370", chapter.id)
        assertEquals("214", chapter.number)
        assertEquals("TappyToon", chapter.group)
        assertEquals(
            listOf("https://cdn.example/1.webp", "https://backup.example/1.webp"),
            mangaPageCandidates(pages.single()),
        )
    }

    @Test fun mapsCurrentBrowseItem() {
        val manga = comixManga(
            mapOf(
                "SZ" to "x0ynk",
                "k_" to "Villains Are Destined to Die",
                "nv" to "manhwa",
                "yx" to "finished",
                "fL" to mapOf("HS" to "/images/covers/x@280.jpg", "Tr" to "/images/covers/x.jpg"),
                "U$" to "A villain story.",
                "Dx" to listOf(mapOf("k_" to "Action"), mapOf("k_" to "Drama")),
                "gS" to listOf(mapOf("k_" to "Josei")),
                "ZN" to listOf(mapOf("k_" to "Author")),
                "Hv" to listOf("Villains Are Destined to Die", "Alt Name"),
                "Pi" to 2020,
            ),
        )

        assertEquals("cx:x0ynk", manga.id)
        assertEquals("Villains Are Destined to Die", manga.title)
        assertEquals("https://comix.ws/images/covers/x.jpg", manga.coverUrl)
        assertEquals(MangaStatus.COMPLETED, manga.status)
        assertEquals(2020, manga.year)
        assertEquals("Author", manga.author)
        assertEquals(listOf("Alt Name"), manga.altTitles)
        assertEquals("Manhwa", manga.type)
        assertEquals(listOf("Action", "Drama", "Josei"), manga.tags)
    }

    @Test fun mapsLegacyBrowseItem() {
        val manga = comixManga(
            mapOf(
                "fH" to "63057",
                "rf" to "A Bona fide Killer",
                "GR" to "manhwa",
                "oF" to "releasing",
                "Zs" to mapOf("QC" to "/images/covers/x@280.jpg", "sP" to "/images/covers/x.jpg"),
                "fK" to "A killer story.",
                "hV" to listOf(mapOf("rf" to "Action"), mapOf("rf" to "Drama")),
                "A5" to listOf(mapOf("rf" to "Action")),
                "uy" to listOf(mapOf("rf" to "Author")),
                "Ga" to listOf("A Bona fide Killer", "Alt Name"),
                "_r" to 2020.0,
            ),
        )

        assertEquals("cx:63057", manga.id)
        assertEquals("A Bona fide Killer", manga.title)
        assertEquals("https://comix.ws/images/covers/x.jpg", manga.coverUrl)
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertEquals(2020, manga.year)
        assertEquals("Author", manga.author)
        assertEquals(listOf("Alt Name"), manga.altTitles)
        assertEquals("Manhwa", manga.type)
        assertEquals(listOf("Action", "Drama"), manga.tags)
    }

    @Test fun mapsCurrentChapterAndReaderPages() {
        val chapter = comixChapter(
            mapOf(
                "hk" to 11275751,
                "Z_" to 164,
                "nb" to "S4-14",
                "aK" to "en",
                "rk" to mapOf("nb" to "Tapas"),
            ),
            "63057",
        )
        assertEquals("cx:63057:11275751", chapter.id)
        assertEquals("164", chapter.number)
        assertEquals("S4-14", chapter.title)
        assertEquals("en", chapter.language)
        assertEquals("Tapas", chapter.group)
        assertEquals(1, chapter.pages)

        val pages = comixPageUrls(
            mapOf(
                "iE" to listOf(
                    mapOf("Gh" to "https://1xx.example/a.webp"),
                    mapOf("Gh" to "http://insecure.example/b.webp"),
                    mapOf("Gh" to "https://1xx.example/c.webp"),
                ),
            ),
        )
        assertEquals(listOf("https://1xx.example/a.webp", "https://1xx.example/c.webp"), pages)
    }

    @Test fun mapsLegacyChapterAndReaderPages() {
        val chapter = comixChapter(
            mapOf(
                "kM" to 11275751.0,
                "Xn" to 164.0,
                "f9" to "S4-14",
                "FP" to "en",
                "gm" to mapOf("f9" to "Tapas"),
            ),
            "63057",
        )
        assertEquals("cx:63057:11275751", chapter.id)
        assertEquals("164", chapter.number)
        assertEquals("en", chapter.language)
        assertEquals("Tapas", chapter.group)
        assertEquals(1, chapter.pages)

        val pages = comixPageUrls(
            mapOf(
                "iT" to listOf(
                    mapOf("H\$" to "https://1xx.example/a.webp"),
                    mapOf("H\$" to "http://insecure.example/b.webp"),
                    mapOf("H\$" to "https://1xx.example/c.webp"),
                ),
            ),
        )
        assertEquals(listOf("https://1xx.example/a.webp", "https://1xx.example/c.webp"), pages)
    }

    @Test fun comixRanksBelowIndonesianSources() {
        assertEquals(0, sourceRank("sh:1"))
        assertEquals(1, sourceRank("km:1"))
        assertEquals(2, sourceRank("cx:63057:11275751"))
    }

    @Test fun translationChunksKeepEveryLine() {
        val text = (1..1_000).joinToString("\n") { "line $it" }
        val chunks = translationChunks(text, limit = 200)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 200 })
        assertEquals(text, chunks.joinToString("\n"))
    }

    @Test fun translationChunksSplitOneLongLine() {
        val chunks = translationChunks("x".repeat(250), limit = 100)
        assertEquals(listOf("x".repeat(100), "x".repeat(100), "x".repeat(50)), chunks)
    }
}
