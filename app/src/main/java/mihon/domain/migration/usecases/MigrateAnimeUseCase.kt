package mihon.domain.migration.usecases

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.anime.download.AnimeDownloadManager
import kotlinx.coroutines.CancellationException
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.domain.anime.interactor.GetMergedAnime
import tachiyomi.domain.anime.interactor.SyncAnimeWithSource
import tachiyomi.domain.anime.interactor.UpdateMergedAnime
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimeEpisodeUpdate
import tachiyomi.domain.anime.model.AnimePlaybackState
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.model.AnimeTitleUpdate
import tachiyomi.domain.anime.repository.AnimeEpisodeRepository
import tachiyomi.domain.anime.repository.AnimePlaybackStateRepository
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.category.interactor.GetAnimeCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.util.MergeGroupMember
import tachiyomi.domain.util.MergeGroupReplacement
import tachiyomi.domain.util.replaceMergeGroupMember
import java.time.Instant

class MigrateAnimeUseCase(
    private val sourcePreferences: SourcePreferences,
    private val animeDownloadManager: AnimeDownloadManager,
    private val animeRepository: AnimeRepository,
    private val animeEpisodeRepository: AnimeEpisodeRepository,
    private val animePlaybackStateRepository: AnimePlaybackStateRepository,
    private val syncAnimeWithSource: SyncAnimeWithSource,
    private val getAnimeCategories: GetAnimeCategories,
    private val setAnimeCategories: SetAnimeCategories,
    private val getMergedAnime: GetMergedAnime,
    private val updateMergedAnime: UpdateMergedAnime,
) {

    suspend operator fun invoke(current: AnimeTitle, target: AnimeTitle, replace: Boolean) {
        val flags = sourcePreferences.migrationFlags.get()

        try {
            try {
                syncAnimeWithSource(target)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Worst case, episodes won't be synced before state transfer.
            }

            if (MigrationFlag.CHAPTER in flags) {
                migrateEpisodes(current.id, target.id)
            }

            if (MigrationFlag.CATEGORY in flags) {
                val categoryIds = getAnimeCategories.await(current.id).map { it.id }
                setAnimeCategories.await(target.id, categoryIds)
            }

            if (MigrationFlag.REMOVE_DOWNLOAD in flags) {
                animeDownloadManager.deleteEpisodes(current, animeEpisodeRepository.getEpisodesByAnimeId(current.id))
            }

            val currentAnimeUpdate = AnimeTitleUpdate(
                id = current.id,
                favorite = false,
                dateAdded = 0,
            )
                .takeIf { replace }
            val targetAnimeUpdate = AnimeTitleUpdate(
                id = target.id,
                favorite = true,
                episodeFlags = current.episodeFlags,
                dateAdded = if (replace) current.dateAdded else Instant.now().toEpochMilli(),
                notes = if (MigrationFlag.NOTES in flags) current.notes else null,
            )

            animeRepository.updateAll(listOfNotNull(currentAnimeUpdate, targetAnimeUpdate))

            if (replace) {
                updateMergeGroup(current.id, target.id)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
        }
    }

    private suspend fun migrateEpisodes(currentId: Long, targetId: Long) {
        val previousEpisodes = animeEpisodeRepository.getEpisodesByAnimeId(currentId)
        val targetEpisodes = animeEpisodeRepository.getEpisodesByAnimeId(targetId)
        val maxWatchedEpisode = previousEpisodes
            .filter { it.watched || it.completed }
            .mapNotNull { it.episodeNumber.takeIf(::isRecognizedEpisodeNumber) }
            .maxOrNull()

        val previousPlaybackStates = buildMap {
            previousEpisodes.forEach { episode ->
                put(episode, animePlaybackStateRepository.getByEpisodeId(episode.id))
            }
        }

        val episodeUpdates = mutableListOf<AnimeEpisodeUpdate>()
        val playbackUpdates = mutableListOf<AnimePlaybackState>()
        targetEpisodes.forEach { targetEpisode ->
            val previousEpisode = findMatchingEpisode(targetEpisode, previousEpisodes)
            var watched = targetEpisode.watched
            var completed = targetEpisode.completed
            var dateFetch = targetEpisode.dateFetch

            if (previousEpisode != null) {
                watched = previousEpisode.watched
                completed = previousEpisode.completed
                dateFetch = previousEpisode.dateFetch
                previousPlaybackStates[previousEpisode]?.let { playbackState ->
                    playbackUpdates += playbackState.copy(episodeId = targetEpisode.id)
                }
            }

            if (maxWatchedEpisode != null &&
                isRecognizedEpisodeNumber(targetEpisode.episodeNumber) &&
                targetEpisode.episodeNumber <= maxWatchedEpisode
            ) {
                watched = true
                completed = true
            }

            episodeUpdates += AnimeEpisodeUpdate(
                id = targetEpisode.id,
                watched = watched,
                completed = completed,
                dateFetch = dateFetch,
            )
        }

        if (episodeUpdates.isNotEmpty()) {
            animeEpisodeRepository.updateAll(episodeUpdates)
        }
        playbackUpdates.forEach { animePlaybackStateRepository.upsert(it) }
    }

    private fun findMatchingEpisode(targetEpisode: AnimeEpisode, previousEpisodes: List<AnimeEpisode>): AnimeEpisode? {
        return if (isRecognizedEpisodeNumber(targetEpisode.episodeNumber)) {
            previousEpisodes.firstOrNull {
                isRecognizedEpisodeNumber(it.episodeNumber) && it.episodeNumber == targetEpisode.episodeNumber
            }
        } else {
            previousEpisodes.firstOrNull { it.name == targetEpisode.name }
        }
    }

    private fun isRecognizedEpisodeNumber(episodeNumber: Double): Boolean {
        return episodeNumber >= 0.0
    }

    private suspend fun updateMergeGroup(currentId: Long, targetId: Long) {
        val currentGroup = getMergedAnime.awaitGroupByAnimeId(currentId)
        if (currentGroup.isEmpty()) return

        val targetGroup = getMergedAnime.awaitGroupByAnimeId(targetId)
        val replacement = replaceMergeGroupMember(
            currentId = currentId,
            replacementId = targetId,
            currentGroup = currentGroup.map {
                MergeGroupMember(targetId = it.targetId, memberId = it.animeId, position = it.position)
            },
            replacementGroup = targetGroup.map {
                MergeGroupMember(targetId = it.targetId, memberId = it.animeId, position = it.position)
            },
        ) ?: return

        replacement.targetIdsToRemoveReplacementFrom.forEach { replacementTargetId ->
            updateMergedAnime.awaitRemoveMembers(replacementTargetId, listOf(targetId))
        }

        when (replacement) {
            is MergeGroupReplacement.Delete -> updateMergedAnime.awaitDeleteGroup(replacement.targetId)
            is MergeGroupReplacement.Upsert -> updateMergedAnime.awaitMerge(
                replacement.targetId,
                replacement.orderedMemberIds,
            )
        }
    }
}
