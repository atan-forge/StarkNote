package com.stark.note.ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stark.note.R
import com.stark.note.ui.theme.OnSurfaceSecondary
import com.stark.note.ui.theme.StarkDimensions
import com.stark.note.ui.theme.StarkSpacing
import kotlinx.coroutines.delay

@Composable
fun PinEntryScreen(
    title: String,
    onPinEntered: (String) -> Unit,
    onBack: () -> Unit,
    errorMessage: String? = null,
    onForgotPin: (() -> Unit)? = null
) {
    var pin by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            pin = ""
            isSubmitting = false
        }
    }

    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focusRequester.requestFocus() }
    }

    fun submitPin() {
        if (pin.length >= 4 && !isSubmitting) {
            isSubmitting = true
            onPinEntered(pin)
        }
    }

    PinSurface {
        Text(text = title, style = MaterialTheme.typography.headlineLarge)
        Text(
            text = stringResource(R.string.pin_unlock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceSecondary,
            modifier = Modifier.padding(top = StarkSpacing.small)
        )
        PinIndicator(
            pinLength = pin.length,
            onClick = { runCatching { focusRequester.requestFocus() } },
            modifier = Modifier.padding(top = StarkSpacing.xxLarge)
        )
        HiddenPinField(
            pin = pin,
            onPinChanged = {
                pin = it
                isSubmitting = false
            },
            focusRequester = focusRequester,
            imeAction = ImeAction.Done,
            onAction = ::submitPin
        )
        ErrorArea(message = errorMessage)
        Button(
            onClick = ::submitPin,
            enabled = pin.length >= 4 && !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(StarkDimensions.controlHeight)
        ) {
            Text(
                if (isSubmitting) stringResource(R.string.working)
                else stringResource(R.string.continue_label)
            )
        }
        if (onForgotPin != null) {
            TextButton(
                onClick = onForgotPin,
                enabled = !isSubmitting,
                modifier = Modifier.padding(top = StarkSpacing.large)
            ) {
                Text(stringResource(R.string.forgot_pin))
            }
        }
        TextButton(onClick = onBack, enabled = !isSubmitting) {
            Text(stringResource(R.string.cancel), color = OnSurfaceSecondary)
        }
    }
}

@Composable
fun PinSetupScreen(
    title: String,
    onPinConfirmed: (String) -> Unit,
    onBack: () -> Unit
) {
    var firstPin by remember { mutableStateOf("") }
    var confirmationPin by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentPin = if (confirming) confirmationPin else firstPin
    val pinMismatchText = stringResource(R.string.pin_mismatch)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(confirming) {
        delay(150)
        runCatching { focusRequester.requestFocus() }
    }

    fun continuePinEntry() {
        if (isSaving || currentPin.length < 4) return
        if (!confirming) {
            confirming = true
        } else if (firstPin == confirmationPin) {
            isSaving = true
            onPinConfirmed(firstPin)
        } else {
            confirmationPin = ""
            error = pinMismatchText
        }
    }

    PinSurface {
        Text(text = title, style = MaterialTheme.typography.headlineLarge)
        Text(
            text = stringResource(if (confirming) R.string.confirm_pin else R.string.enter_new_pin),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceSecondary,
            modifier = Modifier.padding(top = StarkSpacing.small)
        )
        StepIndicator(
            currentStep = if (confirming) 2 else 1,
            modifier = Modifier.padding(top = StarkSpacing.xLarge)
        )
        PinIndicator(
            pinLength = currentPin.length,
            onClick = { runCatching { focusRequester.requestFocus() } },
            modifier = Modifier.padding(top = StarkSpacing.xLarge)
        )
        HiddenPinField(
            pin = currentPin,
            onPinChanged = {
                if (confirming) confirmationPin = it else firstPin = it
                error = null
            },
            focusRequester = focusRequester,
            imeAction = if (confirming) ImeAction.Done else ImeAction.Next,
            onAction = ::continuePinEntry
        )
        ErrorArea(message = error)
        Button(
            onClick = ::continuePinEntry,
            enabled = currentPin.length >= 4 && !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(StarkDimensions.controlHeight)
        ) {
            Text(
                when {
                    isSaving -> stringResource(R.string.working)
                    confirming -> stringResource(R.string.save_pin)
                    else -> stringResource(R.string.continue_label)
                }
            )
        }
        TextButton(onClick = onBack, enabled = !isSaving) {
            Text(stringResource(R.string.cancel), color = OnSurfaceSecondary)
        }
    }
}

@Composable
private fun PinSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = StarkSpacing.xLarge, vertical = StarkSpacing.xxLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = StarkDimensions.authContentWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

@Composable
private fun PinIndicator(
    pinLength: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = stringResource(R.string.pin_digit_count, pinLength)
    val description = stringResource(R.string.pin_input)
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = state
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = StarkSpacing.xLarge, vertical = StarkSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(StarkSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(6) { index ->
                    val filled = index < pinLength
                    val dotColor by animateColorAsState(
                        targetValue = if (filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        label = "PIN digit"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(dotColor, CircleShape)
                    )
                }
                if (pinLength > 6) {
                    Text(
                        text = "+${pinLength - 6}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = StarkSpacing.xSmall)
                    )
                }
            }
            Text(
                text = state,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceSecondary,
                modifier = Modifier.padding(top = StarkSpacing.small)
            )
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(StarkSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(2) { index ->
            Box(
                modifier = Modifier
                    .width(if (index + 1 == currentStep) 28.dp else 10.dp)
                    .height(4.dp)
                    .background(
                        color = if (index + 1 <= currentStep) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun HiddenPinField(
    pin: String,
    onPinChanged: (String) -> Unit,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onAction: () -> Unit
) {
    BasicTextField(
        value = pin,
        onValueChange = {
            if (it.length <= 12 && it.all(Char::isDigit)) {
                onPinChanged(it)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onAction() },
            onDone = { onAction() }
        ),
        modifier = Modifier
            .size(1.dp)
            .focusRequester(focusRequester)
            .clearAndSetSemantics { }
    )
}

@Composable
private fun ErrorArea(message: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.Center
    ) {
        if (message != null) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
