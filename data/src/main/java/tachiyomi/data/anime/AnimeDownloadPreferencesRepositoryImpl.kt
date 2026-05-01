package tachiyomi.data.anime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import tachiyomi.data.ActiveProfileProvider
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.anime.model.AnimeDownloadPreferences
import tachiyomi.domain.anime.repository.AnimeDownloadPreferencesRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeDownloadPreferencesRepositoryImpl(
    private val handler: DatabaseHandler,
    private val profileProvider: ActiveProfileProvider,
) : AnimeDownloadPreferencesRepository {

    override suspend fun getByAnimeId(animeId: Long): AnimeDownloadPreferences? {
        return handler.awaitOneOrNull {
            anime_download_preferencesQueries.getByAnimeId(
                profileProvider.activeProfileId,
                animeId,
                AnimeDownloadPreferencesMapper::mapPreferences,
            )
        }
    }

    override fun getByAnimeIdAsFlow(animeId: Long): Flow<AnimeDownloadPreferences?> {
        return profileProvider.activeProfileIdFlow.flatMapLatest { profileId ->
            handler.subscribeToOneOrNull {
                anime_download_preferencesQueries.getByAnimeId(
                    profileId,
                    animeId,
                    AnimeDownloadPreferencesMapper::mapPreferences,
                )
            }
        }
    }

    override suspend fun upsert(preferences: AnimeDownloadPreferences) {
        handler.await(inTransaction = true) {
            anime_download_preferencesQueries.upsertUpdate(
                dubKey = preferences.dubKey,
                streamKey = preferences.streamKey,
                subtitleKey = preferences.subtitleKey,
                qualityMode = AnimeDownloadPreferencesMapper.encodeQualityMode(preferences.qualityMode),
                updatedAt = preferences.updatedAt,
                profileId = profileProvider.activeProfileId,
                animeId = preferences.animeId,
            )
            anime_download_preferencesQueries.upsertInsert(
                profileId = profileProvider.activeProfileId,
                animeId = preferences.animeId,
                dubKey = preferences.dubKey,
                streamKey = preferences.streamKey,
                subtitleKey = preferences.subtitleKey,
                qualityMode = AnimeDownloadPreferencesMapper.encodeQualityMode(preferences.qualityMode),
                updatedAt = preferences.updatedAt,
            )
        }
    }
}
