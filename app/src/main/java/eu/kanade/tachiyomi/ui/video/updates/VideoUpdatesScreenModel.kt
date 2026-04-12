package eu.kanade.tachiyomi.ui.video.updates

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
import tachiyomi.domain.video.model.VideoUpdatesWithRelations
import tachiyomi.domain.video.repository.VideoUpdatesRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime

class VideoUpdatesScreenModel(
    private val videoUpdatesRepository: VideoUpdatesRepository = Injekt.get(),
    private val application: Application = Injekt.get(),
) : StateScreenModel<VideoUpdatesScreenModel.State>(State()) {

    private val after = ZonedDateTime.now().minusMonths(3).toInstant().toEpochMilli()
    private val limit = 500L

    init {
        screenModelScope.launchIO {
            videoUpdatesRepository.subscribeWithWatched(
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

    private fun List<VideoUpdatesWithRelations>.toUiModels(): List<VideoUpdatesUiModel> {
        return map { update -> VideoUpdatesUiModel.Item(update) }
            .insertSeparators { before, after ->
                val beforeDate = before?.update?.dateFetch?.toLocalDate()
                val afterDate = after?.update?.dateFetch?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> VideoUpdatesUiModel.Header(afterDate)
                    else -> null
                }
            }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val error: String? = null,
        val list: ImmutableList<VideoUpdatesUiModel> = emptyList<VideoUpdatesUiModel>().toImmutableList(),
    )
}

sealed interface VideoUpdatesUiModel {
    data class Header(val date: java.time.LocalDate) : VideoUpdatesUiModel

    data class Item(
        val update: VideoUpdatesWithRelations,
    ) : VideoUpdatesUiModel
}
