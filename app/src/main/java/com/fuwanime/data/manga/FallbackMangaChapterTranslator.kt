package com.fuwanime.data.manga

import java.io.File

/**
 * Uses [primary] (e.g. MangaTranslate) and falls back to [fallback] (e.g. IsManga)
 * when the primary translator is busy/unavailable/unreliable.
 */
class FallbackMangaChapterTranslator(
    private val primary: MangaChapterTranslator,
    private val fallback: MangaChapterTranslator,
) : MangaChapterTranslator {

    override fun chapterDir(cacheDir: File, chapterId: String): File =
        primary.chapterDir(cacheDir, chapterId)

    override fun cachedChapterPages(cacheDir: File, chapterId: String, pageCount: Int): List<String>? {
        return primary.cachedChapterPages(cacheDir, chapterId, pageCount)
            ?: fallback.cachedChapterPages(cacheDir, chapterId, pageCount)
    }

    override suspend fun translateChapter(
        chapterId: String,
        pageUrls: List<String>,
        cacheDir: File,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<String> {
        // If already fully cached by either, return it.
        cachedChapterPages(cacheDir, chapterId, pageUrls.size)?.let { return it }

        return try {
            primary.translateChapter(chapterId, pageUrls, cacheDir, onProgress)
        } catch (e: TranslatorBusyException) {
            fallback.translateChapter(chapterId, pageUrls, cacheDir, onProgress)
        } catch (e: Exception) {
            if (isPrimaryBusy(e) || isFallbackWorthy(e)) {
                fallback.translateChapter(chapterId, pageUrls, cacheDir, onProgress)
            } else {
                throw e
            }
        }
    }

    private fun isPrimaryBusy(e: Throwable?): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            if (cur is TranslatorBusyException) return true
            val m = (cur.message ?: "").lowercase()
            if ("insufficient credit" in m || "insufficient_credits" in m || "no enough credit" in m ||
                "window_limit_exceeded" in m || "rate limit" in m || "too many requests" in m ||
                ("credit" in m && ("habis" in m || "exhaust" in m || "balance" in m || "saldo 0" in m)) ||
                "kuota habis" in m || "antrian penuh" in m || "no api key" in m
            ) return true
            cur = cur.cause
        }
        return false
    }

    private fun isFallbackWorthy(e: Throwable?): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            val m = (cur.message ?: "").lowercase()
            if ("timeout" in m || "timed out" in m ||
                "task selesai tanpa hasil" in m || "tanpa hasil" in m ||
                "unavailable" in m || "busy" in m ||
                "fetch failed" in m || "failed to connect" in m ||
                "unable to resolve" in m || "connection reset" in m ||
                "http 429" in m || "http 502" in m || "http 503" in m || "http 504" in m
            ) return true
            cur = cur.cause
        }
        return false
    }
}
