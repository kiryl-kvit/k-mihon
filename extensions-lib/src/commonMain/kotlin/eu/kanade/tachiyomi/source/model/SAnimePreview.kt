package eu.kanade.tachiyomi.source.model

data class SAnimePreview(
    val index: Int,
    val imageUrl: String,
    val title: String? = null,
    val url: String? = null,
)
