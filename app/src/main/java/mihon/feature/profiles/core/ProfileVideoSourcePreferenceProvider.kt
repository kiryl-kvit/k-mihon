package mihon.feature.profiles.core

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.VideoSourcePreferenceProvider

internal fun videoSourcePreferenceKey(profileId: Long, sourceId: Long): String {
    return "video_source_${profileId}_$sourceId"
}

class ProfileVideoSourcePreferenceProvider(
    private val application: Application,
    private val profileStore: ProfileStore,
) : VideoSourcePreferenceProvider {

    override fun key(sourceId: Long): String {
        return videoSourcePreferenceKey(profileStore.currentProfileId, sourceId)
    }

    override fun preferences(sourceId: Long): SharedPreferences {
        return application.getSharedPreferences(key(sourceId), Context.MODE_PRIVATE)
    }
}
