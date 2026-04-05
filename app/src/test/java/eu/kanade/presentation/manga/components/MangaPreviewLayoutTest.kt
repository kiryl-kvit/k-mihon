package eu.kanade.presentation.manga.components

import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaPreviewLayoutTest {

    @Test
    fun `extra large preview adapts column count to available width`() {
        mangaPreviewGridColumnCount(MangaPreviewSizeUi.EXTRA_LARGE, 320.dp) shouldBe 1
        mangaPreviewGridColumnCount(MangaPreviewSizeUi.EXTRA_LARGE, 700.dp) shouldBe 2
        mangaPreviewGridColumnCount(MangaPreviewSizeUi.EXTRA_LARGE, 900.dp) shouldBe 3
    }

    @Test
    fun `preview state treats short chapters as loaded content`() {
        val previewState = MangaScreenModel.MangaPreviewState(
            chapterId = 1L,
            pages = listOf(
                MangaScreenModel.PreviewPage(ReaderPage(0)),
                MangaScreenModel.PreviewPage(ReaderPage(1)),
                MangaScreenModel.PreviewPage(ReaderPage(2)),
            ),
            pageCount = 5,
        )

        previewState.hasLoadedContent shouldBe true
    }
}
