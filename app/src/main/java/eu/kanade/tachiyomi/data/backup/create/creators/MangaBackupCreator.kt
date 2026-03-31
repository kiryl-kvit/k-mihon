package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.backupChapterMapper
import eu.kanade.tachiyomi.data.backup.models.backupTrackMapper
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.data.ActiveProfileProvider
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.model.History
import tachiyomi.domain.manga.interactor.GetMergedManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaBackupCreator(
    private val handler: DatabaseHandler = Injekt.get(),
    private val profileProvider: ActiveProfileProvider = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
    private val getMergedManga: GetMergedManga = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
) {

    suspend operator fun invoke(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        return invoke(profileProvider.activeProfileId, mangas, options)
    }

    suspend operator fun invoke(
        profileId: Long,
        mangas: List<Manga>,
        options: BackupOptions,
    ): List<BackupManga> {
        val allMangaById = mangaRepository.getAllMangaByProfile(profileId).associateBy { it.id }
        return mangas.map {
            backupManga(profileId, it, options, allMangaById)
        }
    }

    private suspend fun backupManga(
        profileId: Long,
        manga: Manga,
        options: BackupOptions,
        allMangaById: Map<Long, Manga>,
    ): BackupManga {
        // Entry for this manga
        val mangaObject = manga.toBackupManga()

        mangaObject.excludedScanlators = handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(profileId, manga.id)
        }

        if (options.chapters) {
            // Backup all the chapters
            handler.awaitList {
                chaptersQueries.getChaptersByMangaId(
                    profileId = profileId,
                    mangaId = manga.id,
                    applyScanlatorFilter = 0, // false
                    mapper = backupChapterMapper,
                )
            }
                .takeUnless(List<BackupChapter>::isEmpty)
                ?.let { mangaObject.chapters = it }
        }

        if (options.categories) {
            // Backup categories for this manga
            val categoriesForManga = if (profileId == profileProvider.activeProfileId) {
                getCategories.await(manga.id)
            } else {
                handler.awaitList {
                    categoriesQueries.getCategoriesByMangaId(profileId, manga.id) { id, name, order, flags ->
                        Category(
                            id = id,
                            name = name,
                            order = order,
                            flags = flags,
                        )
                    }
                }
            }
            if (categoriesForManga.isNotEmpty()) {
                mangaObject.categories = categoriesForManga.map { it.order }
            }
        }

        if (options.tracking) {
            val tracks = handler.awaitList {
                manga_syncQueries.getTracksByMangaId(profileId, manga.id, backupTrackMapper)
            }
            if (tracks.isNotEmpty()) {
                mangaObject.tracking = tracks
            }
        }

        if (options.history) {
            val historyByMangaId = if (profileId == profileProvider.activeProfileId) {
                getHistory.await(manga.id)
            } else {
                handler.awaitList {
                    historyQueries.getHistoryByMangaId(profileId, manga.id) { _, chapterId, lastRead, timeRead ->
                        History(
                            id = 0,
                            chapterId = chapterId,
                            readAt = lastRead,
                            readDuration = timeRead,
                        )
                    }
                }
            }
            if (historyByMangaId.isNotEmpty()) {
                val history = historyByMangaId.map { history ->
                    val chapter = handler.awaitOne {
                        chaptersQueries.getChapterById(history.chapterId, profileId)
                    }
                    BackupHistory(chapter.url, history.readAt?.time ?: 0L, history.readDuration)
                }
                if (history.isNotEmpty()) {
                    mangaObject.history = history
                }
            }
        }

        val mergeGroup = getMergedManga.awaitGroupByMangaId(manga.id)
        if (mergeGroup.isNotEmpty()) {
            val targetId = mergeGroup.first().targetId
            val targetManga = allMangaById[targetId]
            val position = mergeGroup.firstOrNull { it.mangaId == manga.id }?.position?.toInt()
            if (targetManga != null && position != null) {
                mangaObject.mergeTargetSource = targetManga.source
                mangaObject.mergeTargetUrl = targetManga.url
                mangaObject.mergePosition = position
            }
        }

        return mangaObject
    }
}

private fun Manga.toBackupManga() =
    BackupManga(
        url = this.url,
        title = this.title,
        displayName = this.displayName,
        artist = this.artist,
        author = this.author,
        description = this.description,
        genre = this.genre.orEmpty(),
        status = this.status.toInt(),
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewer = (this.viewerFlags.toInt() and ReadingMode.MASK),
        viewer_flags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        notes = this.notes,
        initialized = this.initialized,
    )
