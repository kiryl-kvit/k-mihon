package eu.kanade.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin

@Composable
fun AnimatedFloatingActionButton(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
            scaleIn(
                animationSpec = tween(durationMillis = 220),
                initialScale = 0.85f,
                transformOrigin = TransformOrigin(1f, 1f),
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = 140)) +
            scaleOut(
                animationSpec = tween(durationMillis = 140),
                targetScale = 0.85f,
                transformOrigin = TransformOrigin(1f, 1f),
            ),
    ) {
        content()
    }
}
