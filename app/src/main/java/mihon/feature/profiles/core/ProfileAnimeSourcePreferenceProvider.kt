package mihon.feature.profiles.core

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.AnimeSourcePreferenceProvider

internal fun animeSourcePreferenceKey(profileId: Long, sourceId: Long): String {
    return "anime_source_${profileId}_$sourceId"
}

class ProfileAnimeSourcePreferenceProvider(
    private val application: Application,
    private val profileStore: ProfileStore,
) : AnimeSourcePreferenceProvider {

    override fun key(sourceId: Long): String {
        return animeSourcePreferenceKey(profileStore.currentProfileId, sourceId)
    }

    override fun preferences(sourceId: Long): SharedPreferences {
        return application.getSharedPreferences(key(sourceId), Context.MODE_PRIVATE)
    }
}
