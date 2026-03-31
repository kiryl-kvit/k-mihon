package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BrowseFeedNameDialog(
    title: StringResource,
    initialValue: String = "",
    duplicateName: (String) -> Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val trimmedValue = value.trim()
    val isDuplicate = remember(trimmedValue) {
        trimmedValue.isNotEmpty() && duplicateName(trimmedValue)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(title)) },
        text = {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                value = value,
                onValueChange = { value = it },
                label = { Text(text = stringResource(MR.strings.name)) },
                supportingText = {
                    Text(
                        text = if (isDuplicate) {
                            stringResource(MR.strings.browse_feed_preset_exists)
                        } else {
                            stringResource(MR.strings.information_required_plain)
                        },
                    )
                },
                isError = isDuplicate,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = trimmedValue.isNotEmpty() && !isDuplicate,
                onClick = { onConfirm(trimmedValue) },
            ) {
                Text(text = stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
fun DeleteBrowsePresetDialog(
    presetName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(MR.strings.action_delete))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.are_you_sure))
        },
        text = {
            Text(text = stringResource(MR.strings.browse_delete_preset_confirmation, presetName))
        },
    )
}
