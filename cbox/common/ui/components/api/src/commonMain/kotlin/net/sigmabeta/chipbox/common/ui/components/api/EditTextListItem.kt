package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.utils.FocusAreaShape
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.EditTextListModel

// Always-lifted (it's an active input), mirroring the expanded dropdown's elevation + breathing room.
private val EDIT_TEXT_ELEVATION = 4.dp
private val EDIT_TEXT_LIFT_PADDING = 16.dp
private val EDIT_TEXT_CONTENT_PADDING = 12.dp
private const val HINT_ALPHA = 0.5f

/**
 * Renderer for an [EditTextListModel]: a lifted container (styled like the expanded
 * [ExpandingDropdownListItem]) with two rows — an editable single-line text field on top, and a
 * cancel/submit button pair below.
 *
 * The typed text is local to this composable. Submitting (the confirm button, or the keyboard's
 * Done/enter action) dispatches [SageAction.EditTextSubmitted] with the text and the model's id;
 * cancelling dispatches [SageAction.EditTextCancelled] with the id. When
 * [EditTextListModel.allowEmpty] is false, both submit paths are blocked while the field is blank
 * (the confirm button is disabled and enter is ignored).
 */
@Composable
fun EditTextListItem(
    model: EditTextListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    // TextFieldValue (not a bare String) so the cursor can start at the end of the prefilled text.
    var fieldValue by remember(model.id) {
        mutableStateOf(TextFieldValue(model.initialText, TextRange(model.initialText.length)))
    }
    val canSubmit = model.allowEmpty || fieldValue.text.isNotBlank()

    fun submit() {
        if (canSubmit) actionSink.sendAction(SageAction.EditTextSubmitted(model.id, fieldValue.text))
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = FocusAreaShape,
        shadowElevation = EDIT_TEXT_ELEVATION,
        tonalElevation = EDIT_TEXT_ELEVATION,
        modifier = modifier.padding(EDIT_TEXT_LIFT_PADDING),
    ) {
        Column(modifier = Modifier.padding(padding)) {
            if (model.header.isNotBlank()) {
                Text(
                    text = model.header,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        start = EDIT_TEXT_CONTENT_PADDING,
                        end = EDIT_TEXT_CONTENT_PADDING,
                        top = EDIT_TEXT_CONTENT_PADDING,
                    ),
                )
            }

            EditTextField(
                value = fieldValue,
                hint = model.hint,
                autoFocus = model.autoFocus,
                onValueChanged = { fieldValue = it },
                onSubmit = ::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EDIT_TEXT_CONTENT_PADDING),
            )

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EDIT_TEXT_CONTENT_PADDING)
                    .padding(bottom = EDIT_TEXT_CONTENT_PADDING),
            ) {
                TextButton(onClick = { actionSink.sendAction(SageAction.EditTextCancelled(model.id)) }) {
                    Text(text = model.cancelLabel)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = ::submit, enabled = canSubmit) {
                    Text(text = model.submitLabel)
                }
            }
        }
    }
}

@Composable
private fun EditTextField(
    value: TextFieldValue,
    hint: String,
    autoFocus: Boolean,
    onValueChanged: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val focusRequester = remember { FocusRequester() }
        // Match SearchBar: skip auto-focus under Paparazzi / @Preview, where requesting focus routes
        // through Layoutlib's IME mock and crashes. Runtime is unaffected.
        val skipFocus = LocalInspectionMode.current
        LaunchedEffect(Unit) {
            if (autoFocus && !skipFocus) focusRequester.requestFocus()
        }

        BasicTextField(
            value = value,
            singleLine = true,
            onValueChange = onValueChanged,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )

        if (value.text.isEmpty()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.alpha(HINT_ALPHA),
            )
        }
    }
}
