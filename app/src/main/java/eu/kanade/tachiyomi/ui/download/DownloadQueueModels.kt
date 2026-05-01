package eu.kanade.tachiyomi.ui.download

import eu.kanade.tachiyomi.data.download.model.Download

enum class DownloadQueueContentType {
    MANGA_CHAPTER,
    ANIME_EPISODE,
}

data class DownloadQueueHeaderModel(
    val id: Long,
    val contentType: DownloadQueueContentType,
    val title: String,
    val count: Int,
) {
    val displayTitle: String
        get() = "$title ($count)"
}

data class DownloadQueueItemModel(
    val id: Long,
    val contentType: DownloadQueueContentType,
    val title: String,
    val subtitle: String,
    val progress: Int,
    val progressMax: Int,
    val progressText: String,
)

fun Download.toDownloadQueueItemModel(): DownloadQueueItemModel {
    val pages = pages
    val progressMax = pages?.size?.times(100) ?: 1
    val progressText = pages?.let { "$downloadedImages/${it.size}" }.orEmpty()
    return DownloadQueueItemModel(
        id = chapter.id,
        contentType = DownloadQueueContentType.MANGA_CHAPTER,
        title = manga.title,
        subtitle = chapter.name,
        progress = totalProgress,
        progressMax = progressMax,
        progressText = progressText,
    )
}
