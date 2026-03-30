package tachiyomi.domain.manga.model

data class MangaMerge(
    val targetId: Long,
    val mangaId: Long,
    val position: Long,
) {
    val isTarget: Boolean = targetId == mangaId
}
