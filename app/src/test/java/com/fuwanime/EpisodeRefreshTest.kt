package com.fuwanime

import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.remote.ApiResponse
import com.fuwanime.data.remote.EpisodeDto
import com.fuwanime.data.remote.EpisodeListData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeRefreshTest {

    @Test fun episodePage_usesCacheUntilForceRefresh() = runBlocking {
        val api = object : FakeAnimeApi() {
            // Named differently from FakeAnimeApi.calls, which records endpoint strings.
            var episodeCalls = 0

            override suspend fun episodes(
                animeId: String,
                page: Int,
                search: String,
                idUser: String,
                keyClient: String,
            ): ApiResponse<EpisodeListData> {
                episodeCalls++
                return ApiResponse(data = EpisodeListData(listOf(EpisodeDto(id = "e$episodeCalls", index = episodeCalls))))
            }
        }
        val repo = AnimeRepository(api)

        assertEquals("1", repo.episodePage("a", 0).getOrThrow().single().number)
        assertEquals("1", repo.episodePage("a", 0).getOrThrow().single().number)
        assertEquals("2", repo.episodePage("a", 0, forceRefresh = true).getOrThrow().single().number)
        assertEquals(2, api.episodeCalls)
    }
}
