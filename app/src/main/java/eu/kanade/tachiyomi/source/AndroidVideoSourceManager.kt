package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.domain.source.service.VideoSourceManager
import java.util.concurrent.ConcurrentHashMap

class AndroidVideoSourceManager(
    private val extensionManager: ExtensionManager,
) : VideoSourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, VideoSource>())

    override val catalogueSources: Flow<List<VideoCatalogueSource>> = sourcesMapFlow.map {
        it.values.filterIsInstance<VideoCatalogueSource>()
    }

    init {
        scope.launch {
            extensionManager.installedExtensionsFlow
                .collectLatest { extensions ->
                    val mutableMap = ConcurrentHashMap<Long, VideoSource>()
                    extensions.filterIsInstance<Extension.InstalledVideo>().forEach { extension ->
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                    _isInitialized.value = true
                }
        }
    }

    override fun get(sourceKey: Long): VideoSource? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getCatalogueSources(): List<VideoCatalogueSource> {
        return sourcesMapFlow.value.values.filterIsInstance<VideoCatalogueSource>()
    }
}
