package tachiyomi.domain.source.service

import eu.kanade.tachiyomi.source.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.AnimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AnimeSourceManager {

    val isInitialized: StateFlow<Boolean>

    val catalogueSources: Flow<List<AnimeCatalogueSource>>

    fun get(sourceKey: Long): AnimeSource?

    fun getCatalogueSources(): List<AnimeCatalogueSource>
}
