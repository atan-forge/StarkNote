package com.stark.note.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.stark.note.domain.note.Note
import com.stark.note.domain.note.NoteType
import com.stark.note.ui.auth.PinEntryScreen
import com.stark.note.ui.list.NoteCard
import com.stark.note.ui.settings.SettingsScreen
import com.stark.note.ui.theme.StarkNoteTheme
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test

class QuietPremiumUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noteCardSupportsLargeText() {
        composeRule.setContent {
            StarkNoteTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = 2f)
                ) {
                    NoteCard(
                        note = Note(
                            id = "note",
                            title = "A long note title that must remain readable",
                            body = "A long preview that should wrap without forcing the row into a fixed height.",
                            noteType = NoteType.NORMAL,
                            isLocked = false,
                            bodyIv = null,
                            createdAt = Instant.fromEpochMilliseconds(0),
                            updatedAt = Instant.fromEpochMilliseconds(0)
                        ),
                        onClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("A long note title that must remain readable").assertIsDisplayed()
    }

    @Test
    fun settingsRemainScrollable() {
        composeRule.setContent {
            StarkNoteTheme {
                SettingsScreen(
                    onBack = {},
                    onExport = {},
                    onImport = {},
                    onChangePin = {},
                    hasPin = true,
                    biometricEnabled = true,
                    biometricAvailable = true,
                    onBiometricToggle = {},
                    onOpenBiometricSettings = {},
                    versionName = "1.0"
                )
            }
        }

        composeRule.onNodeWithText("About StarkNote").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pinIndicatorIsAccessibleAndInteractive() {
        composeRule.setContent {
            StarkNoteTheme {
                PinEntryScreen(
                    title = "Enter PIN",
                    onPinEntered = {},
                    onBack = {}
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("PIN input")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
