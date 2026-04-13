package eu.kanade.tachiyomi.source

/**
 * A factory for creating anime sources at runtime.
 */
interface AnimeSourceFactory {
    /**
     * Create a new copy of the sources.
     *
     * @return The created sources.
     */
    fun createSources(): List<AnimeSource>
}
