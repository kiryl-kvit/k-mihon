package tachiyomi.domain.source.service

import eu.kanade.tachiyomi.source.VideoCatalogueSource
import eu.kanade.tachiyomi.source.VideoSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface VideoSourceManager {

    val isInitialized: StateFlow<Boolean>

    val catalogueSources: Flow<List<VideoCatalogueSource>>

    fun get(sourceKey: Long): VideoSource?

    fun getCatalogueSources(): List<VideoCatalogueSource>
}
