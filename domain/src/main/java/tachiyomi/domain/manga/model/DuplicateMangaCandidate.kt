package tachiyomi.domain.manga.model

import androidx.compose.runtime.Immutable

@Immutable
data class DuplicateMangaCandidate(
    val manga: Manga,
    val chapterCount: Long,
    val cheapScore: Int,
    val coverScore: Int = 0,
    val score: Int,
    val reasons: List<DuplicateMangaMatchReason>,
    val coverHashChecked: Boolean = false,
) {
    val isStrongMatch: Boolean
        get() = score >= STRONG_MATCH_SCORE

    companion object {
        const val STRONG_MATCH_SCORE = 82
    }
}

enum class DuplicateMangaMatchReason {
    DESCRIPTION,
    TITLE,
    AUTHOR,
    ARTIST,
    COVER,
    STATUS,
    GENRE,
    CHAPTER_COUNT,
}
