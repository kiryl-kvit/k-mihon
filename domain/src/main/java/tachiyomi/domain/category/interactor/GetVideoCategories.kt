package tachiyomi.domain.category.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository

class GetVideoCategories(
    private val categoryRepository: CategoryRepository,
) {

    fun subscribe(videoId: Long): Flow<List<Category>> {
        return categoryRepository.getCategoriesByVideoIdAsFlow(videoId)
    }

    suspend fun await(videoId: Long): List<Category> {
        return categoryRepository.getCategoriesByVideoId(videoId)
    }
}
