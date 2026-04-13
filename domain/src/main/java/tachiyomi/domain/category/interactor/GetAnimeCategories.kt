package tachiyomi.domain.category.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository

class GetAnimeCategories(
    private val categoryRepository: CategoryRepository,
) {

    fun subscribe(animeId: Long): Flow<List<Category>> {
        return categoryRepository.getCategoriesByAnimeIdAsFlow(animeId)
    }

    suspend fun await(animeId: Long): List<Category> {
        return categoryRepository.getCategoriesByAnimeId(animeId)
    }
}
