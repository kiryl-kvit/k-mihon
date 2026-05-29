package eu.kanade.tachiyomi.source.model

data class SAnimeHoverPreview(
    val videoUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val posterUrl: String? = null,
)
