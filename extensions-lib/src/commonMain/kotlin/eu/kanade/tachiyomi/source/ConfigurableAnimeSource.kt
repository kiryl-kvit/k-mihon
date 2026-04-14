package eu.kanade.tachiyomi.source

import android.content.SharedPreferences

interface ConfigurableAnimeSource : AnimeSource {

    /**
     * Gets instance of [SharedPreferences] scoped to the specific anime source.
     */
    fun getSourcePreferences(): SharedPreferences =
        sourcePreferences(animePreferenceKey())

    fun setupPreferenceScreen(screen: PreferenceScreen)
}

fun ConfigurableAnimeSource.animePreferenceKey(): String = getAnimeSourcePreferenceProvider().key(id)

fun ConfigurableAnimeSource.animeSourcePreferences(): SharedPreferences =
    getAnimeSourcePreferenceProvider().preferences(id)
