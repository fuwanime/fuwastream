package com.fuwanime

import com.fuwanime.data.manga.KakaMangaSource
import com.fuwanime.data.manga.MangaParkSource
import com.fuwanime.data.manga.MangaStatus
import com.fuwanime.data.manga.MangaType
import com.fuwanime.data.manga.RawkumaSource
import com.fuwanime.data.manga.isRawkumaId
import com.fuwanime.data.manga.shouldUseRawkuma
import com.fuwanime.data.manga.sourceRank
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawkumaSourceTest {
    private val client = OkHttpClient()
    private val source = RawkumaSource(client)

    @Test fun listMapsRawIdsCoverStatusAndMangaType() {
        val html = """
            <div class="card">
              <a href="https://rawkuma.net/manga/sample-title/"><img src="https://rawkuma.net/wp-content/uploads/sample.jpg" alt="Sample Title"></a>
              <p class="font-normal text-xs">Ongoing</p>
              <a href="https://rawkuma.net/manga/sample-title/"><h1 class="title"> Sample Title </h1></a>
            </div>
        """.trimIndent()

        val manga = source.parseList(html).single()
        assertEquals("rk:sample-title", manga.id)
        assertTrue(isRawkumaId(manga.id))
        assertEquals("Sample Title", manga.title)
        assertEquals("https://rawkuma.net/wp-content/uploads/sample.jpg", manga.coverUrl)
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertEquals("Manga", manga.type)
    }

    @Test fun searchMapsFiveResultMarkupWithoutInventingMetadata() {
        val html = """
            <div id="searchResults">
              <a href="https://rawkuma.net/manga/one-piece/">
                <img src="https://rawkuma.net/wp-content/uploads/one-piece.jpg" alt="One Piece">
                <div><h3>One Piece</h3><p>A pirate story</p></div>
              </a>
            </div>
        """.trimIndent()

        val manga = source.parseSearch(html).single()
        assertEquals("rk:one-piece", manga.id)
        assertEquals("One Piece", manga.title)
        assertEquals("Manga", manga.type)
        assertEquals(MangaStatus.UNKNOWN, manga.status)
    }

    @Test fun detailUsesBookJsonLdMetadata() {
        val html = """
            <script type="application/ld+json">{
              "@context":"https://schema.org",
              "@type":["Book","ComicSeries"],
              "name":"Seitokai ni mo Ana wa Aru!",
              "description":"Japanese &amp; English summary",
              "image":{"@type":"ImageObject","url":"https://rawkuma.net/wp-content/uploads/cover.jpg"},
              "author":{"@type":"Person","name":"Muchimaro"},
              "genre":["Comedy","Ecchi","School Life"],
              "creativeWorkStatus":"Ongoing",
              "isCompleted":false,
              "datePublished":"2022",
              "alternateName":"Even the Student Council Has Holes!,生徒会にも穴はある!"
            }</script>
            <span>Type</span></h4><div><p>Manga</p></div>
        """.trimIndent()

        val manga = source.parseDetail(html, "seitokai-ni-mo-ana-wa-aru")
        assertEquals("rk:seitokai-ni-mo-ana-wa-aru", manga.id)
        assertEquals("Seitokai ni mo Ana wa Aru!", manga.title)
        assertEquals("Japanese & English summary", manga.description)
        assertEquals("Muchimaro", manga.author)
        assertEquals(2022, manga.year)
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertEquals(listOf("Comedy", "Ecchi", "School Life"), manga.tags)
        assertTrue(manga.altTitles.contains("生徒会にも穴はある!"))
    }

    @Test fun chaptersAreJapaneseRawAndPagesOnlyUseRawkumaCdn() {
        val detail = """
            <div data-chapter-number="191">
              <a href="https://rawkuma.net/manga/sample/chapter-191.435906/">
                <span>Chapter 191</span><time datetime="2026-11-18T00:05:00Z">4 days ago</time>
              </a>
            </div>
            <div data-chapter-number="190.5">
              <a href="https://rawkuma.net/manga/sample/chapter-190.5.435100/">
                <span>Chapter 190.5 Bonus</span><time datetime="2026-11-17T00:05:00Z">5 days ago</time>
              </a>
            </div>
        """.trimIndent()
        val chapters = source.parseChapters(detail, "sample")

        assertEquals(listOf("191", "190.5"), chapters.map { it.number })
        assertEquals("rk:sample|chapter-191.435906", chapters.first().id)
        assertEquals("ja", chapters.first().language)
        assertEquals("Rawkuma", chapters.first().group)
        assertTrue(chapters.first().publishAt > 0)
        assertNull(chapters.first().title)
        assertEquals("Bonus", chapters.last().title)

        val reader = """
            <img src="https://rawkuma.net/logo.png">
            <section data-image-data="1">
              <img src="https://cdn.rawkuma.com/s/sample-raw/191/1.jpg">
              <img data-src="https://cdn.rawkuma.com/s/sample-raw/191/2.webp">
            </section>
        """.trimIndent()
        assertEquals(
            listOf(
                "https://cdn.rawkuma.com/s/sample-raw/191/1.jpg",
                "https://cdn.rawkuma.com/s/sample-raw/191/2.webp",
            ),
            source.parsePages(reader),
        )
    }

    @Test fun rawkumaOnlyJoinsMangaBrowseOrTitleSearch() {
        assertFalse(shouldUseRawkuma("", null, hasGenreFilter = false))
        assertTrue(shouldUseRawkuma("", MangaType.MANGA, hasGenreFilter = false))
        assertFalse(shouldUseRawkuma("", MangaType.MANGA, hasGenreFilter = true))
        assertTrue(shouldUseRawkuma("one piece", null, hasGenreFilter = false))
        assertFalse(shouldUseRawkuma("solo leveling", MangaType.MANHWA, hasGenreFilter = false))
        assertFalse(shouldUseRawkuma("battle through", MangaType.MANHUA, hasGenreFilter = false))
    }

    @Test fun englishCardsPreserveTheirDeclaredType() {
        val kaka = """
            <div class="group relative flex flex-col">
              <img src="https://img.example/manhua.webp" alt="Saint Knight">
              <span class="uppercase">Manhua</span>
              <h3><a title="Saint Knight" href="/manga/saint-knight">Saint Knight</a></h3>
            </div>
        """.trimIndent()
        assertEquals("Manhua", KakaMangaSource(client).parseList(kaka, "https://example.com").single().type)

        val park = """
            {"@type":"ListItem","position":1,"url":"https://mangapark.cc/manga/northern-duke","name":"Northern Duke"}
            <a href="/manga/northern-duke" class="poster" title="Northern Duke"><img src="/cover.jpg"></a>
            <div class="info"><div><span class="type">Manhwa</span></div>
              <a href="/manga/northern-duke" title="Northern Duke">Northern Duke</a>
            </div>
        """.trimIndent()
        assertEquals("Manhwa", MangaParkSource(client).parseSearch(park).single().type)
    }

    @Test fun englishDetailsPreserveTheirDeclaredType() {
        val kaka = """
            <h1>Saint Knight</h1>
            <script type="application/ld+json">{"@type":"Book","genre":["Fantasy","Manhua"]}</script>
        """.trimIndent()
        assertEquals(
            "Manhua",
            KakaMangaSource(client).parseDetail(kaka, "saint-knight", "https://example.com").type,
        )

        val park = """
            <meta property="og:title" content="Northern Duke - MangaPark">
            <div><span>Type:</span><span>Manhwa</span></div>
        """.trimIndent()
        assertEquals("Manhwa", MangaParkSource(client).parseDetail(park, "northern-duke").type)
    }

    @Test fun rawSourceRanksBelowTranslatedSources() {
        assertTrue(sourceRank("mp:sample|chapter") < sourceRank("rk:sample|chapter"))
        assertTrue(sourceRank("rk:sample|chapter") < sourceRank("unknown:chapter"))
    }
}
