package eu.kanade.tachiyomi.ui.anime.updates

import androidx.compose.runtime.Immutable
import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.i18n.MR
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.anime.model.AnimeUpdatesWithRelations
import tachiyomi.domain.anime.repository.AnimeUpdatesRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime

class AnimeUpdatesScreenModel(
    private val animeUpdatesRepository: AnimeUpdatesRepository = Injekt.get(),
    private val application: Application = Injekt.get(),
) : StateScreenModel<AnimeUpdatesScreenModel.State>(State()) {

    private val after = ZonedDateTime.now().minusMonths(3).toInstant().toEpochMilli()
    private val limit = 500L

    init {
        screenModelScope.launchIO {
            animeUpdatesRepository.subscribeWithWatched(
                watched = false,
                after = after,
                limit = limit,
            )
                .catch { error ->
                    logcat(LogPriority.ERROR, error)
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            error = withUIContext { application.stringResource(MR.strings.unknown_error) },
                        )
                    }
                }
                .collectLatest { updates ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            list = updates.toUiModels().toImmutableList(),
                        )
                    }
                }
        }
    }

    private fun List<AnimeUpdatesWithRelations>.toUiModels(): List<AnimeUpdatesUiModel> {
        return map { update -> AnimeUpdatesUiModel.Item(update) }
            .insertSeparators { before, after ->
                val beforeDate = before?.update?.dateFetch?.toLocalDate()
                val afterDate = after?.update?.dateFetch?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> AnimeUpdatesUiModel.Header(afterDate)
                    else -> null
                }
            }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val error: String? = null,
        val list: ImmutableList<AnimeUpdatesUiModel> = emptyList<AnimeUpdatesUiModel>().toImmutableList(),
    )
}

sealed interface AnimeUpdatesUiModel {
    data class Header(val date: java.time.LocalDate) : AnimeUpdatesUiModel

    data class Item(
        val update: AnimeUpdatesWithRelations,
    ) : AnimeUpdatesUiModel
}
