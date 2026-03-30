package tachiyomi.domain.manga.model

fun Manga.presentationTitle(): String = displayTitle

fun String?.orEmptyIfNull(): String = this ?: ""
