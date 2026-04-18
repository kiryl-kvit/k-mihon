package eu.kanade.tachiyomi.source

import android.content.SharedPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

interface AnimeSourcePreferenceProvider {
    fun key(sourceId: Long): String
    fun preferences(sourceId: Long): SharedPreferences
}

object DefaultAnimeSourcePreferenceProvider : AnimeSourcePreferenceProvider {
    override fun key(sourceId: Long): String = "anime_source_$sourceId"

    override fun preferences(sourceId: Long): SharedPreferences {
        return sourcePreferences(key(sourceId))
    }
}

fun getAnimeSourcePreferenceProvider(): AnimeSourcePreferenceProvider {
    return runCatching { Injekt.get<AnimeSourcePreferenceProvider>() }
        .getOrElse { DefaultAnimeSourcePreferenceProvider }
}
