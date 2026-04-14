package eu.kanade.tachiyomi.data.library

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import tachiyomi.domain.anime.model.AnimeTitle

class AnimeLibraryUpdateJobTest {

    @Test
    fun `filters by included and excluded categories like manga`() {
        val first = AnimeTitle.create().copy(id = 1L, source = 10L, favorite = true, title = "First")
        val second = AnimeTitle.create().copy(id = 2L, source = 10L, favorite = true, title = "Second")
        val third = AnimeTitle.create().copy(id = 3L, source = 11L, favorite = true, title = "Third")

        filterAnimeToUpdate(
            favorites = listOf(first, second, third),
            categoryIdsByAnimeId = mapOf(
                first.id to listOf(1L),
                second.id to listOf(2L),
            ),
            categoryId = -1L,
            sourceId = -1L,
            includedCategories = setOf(1L, 0L),
            excludedCategories = setOf(2L),
        ).map { it.id } shouldContainExactly listOf(1L, 3L)
    }

    @Test
    fun `filters by scoped category and source when provided`() {
        val first = AnimeTitle.create().copy(id = 1L, source = 10L, favorite = true, title = "First")
        val second = AnimeTitle.create().copy(id = 2L, source = 11L, favorite = true, title = "Second")
        val third = AnimeTitle.create().copy(id = 3L, source = 10L, favorite = true, title = "Third")

        filterAnimeToUpdate(
            favorites = listOf(first, second, third),
            categoryIdsByAnimeId = mapOf(
                first.id to listOf(1L),
                second.id to listOf(1L),
                third.id to listOf(2L),
            ),
            categoryId = 1L,
            sourceId = 10L,
            includedCategories = emptySet(),
            excludedCategories = emptySet(),
        ).map { it.id } shouldContainExactly listOf(1L)
    }
}
