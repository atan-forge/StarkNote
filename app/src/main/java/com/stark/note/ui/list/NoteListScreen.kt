package com.stark.note.ui.list

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stark.note.R
import com.stark.note.domain.note.Note
import com.stark.note.domain.note.NoteType
import com.stark.note.ui.components.DelayedLoadingState
import com.stark.note.ui.components.StarkIconContainer
import com.stark.note.ui.components.StarkTopBar
import com.stark.note.ui.theme.AccentMuted
import com.stark.note.ui.theme.OnAccent
import com.stark.note.ui.theme.OnSurfaceSecondary
import com.stark.note.ui.theme.StarkDimensions
import com.stark.note.ui.theme.StarkSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    onNoteClicked: (Note) -> Unit,
    onAddNoteClicked: (NoteType) -> Unit,
    onSettingsClicked: () -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    val isReady by viewModel.isReady.collectAsState()
    var showTypePicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    fun createNote(type: NoteType) {
        scope.launch {
            sheetState.hide()
            showTypePicker = false
            onAddNoteClicked(type)
        }
    }

    if (showTypePicker) {
        ModalBottomSheet(
            onDismissRequest = { showTypePicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = StarkDimensions.settingsContentWidth)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = StarkSpacing.large)
                    .padding(bottom = StarkSpacing.section)
            ) {
                Text(
                    text = stringResource(R.string.new_note),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = StarkSpacing.large)
                )
                TypeOptionRow(
                    title = stringResource(R.string.plain_note),
                    subtitle = stringResource(R.string.plain_note_subtitle),
                    icon = Icons.Default.Description,
                    onClick = { createNote(NoteType.NORMAL) }
                )
                Spacer(modifier = Modifier.size(StarkSpacing.small))
                TypeOptionRow(
                    title = stringResource(R.string.checklist),
                    subtitle = stringResource(R.string.checklist_subtitle),
                    icon = Icons.Default.CheckBox,
                    onClick = { createNote(NoteType.LIST) }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            StarkTopBar(
                title = stringResource(R.string.notes),
                actions = {
                    IconButton(onClick = onSettingsClicked) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showTypePicker = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = OnAccent,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_note))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                !isReady -> DelayedLoadingState(modifier = Modifier.fillMaxSize())
                notes.isEmpty() -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    onCreate = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showTypePicker = true
                    }
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = StarkDimensions.listContentWidth),
                    contentPadding = PaddingValues(
                        start = StarkSpacing.large,
                        top = StarkSpacing.small,
                        end = StarkSpacing.large,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(StarkSpacing.medium)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNoteClicked(note)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(note: Note, onClick: () -> Unit) {
    val title = note.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.untitled)
    val preview = when {
        note.isLocked -> stringResource(R.string.locked)
        note.noteType == NoteType.LIST -> stringResource(R.string.checklist)
        note.body.isBlank() -> stringResource(R.string.empty_note)
        else -> note.body
    }
    val modifiedTime = remember(note.updatedAt) {
        DateUtils.getRelativeTimeSpanString(
            note.updatedAt.toEpochMilliseconds(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = StarkDimensions.minimumTouchTarget),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = StarkSpacing.large,
                vertical = StarkSpacing.medium
            ),
            verticalArrangement = Arrangement.spacedBy(StarkSpacing.small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = if (note.title.isNullOrBlank()) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = modifiedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceSecondary,
                    modifier = Modifier.padding(start = StarkSpacing.medium)
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                if (note.isLocked || note.noteType == NoteType.LIST) {
                    Icon(
                        imageVector = if (note.isLocked) Icons.Rounded.Lock else Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (note.isLocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(StarkSpacing.small))
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    onCreate: () -> Unit
) {
    Column(
        modifier = modifier.padding(StarkSpacing.xLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = AccentMuted
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.no_notes),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = StarkSpacing.xLarge)
        )
        Text(
            text = stringResource(R.string.no_notes_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceSecondary,
            modifier = Modifier.padding(top = StarkSpacing.small)
        )
        Button(
            onClick = onCreate,
            modifier = Modifier.padding(top = StarkSpacing.xLarge)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(StarkSpacing.small))
            Text(stringResource(R.string.new_note))
        }
    }
}

@Composable
private fun TypeOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = StarkDimensions.minimumTouchTarget),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(StarkSpacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StarkIconContainer(icon = icon)
            Spacer(modifier = Modifier.width(StarkSpacing.large))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceSecondary,
                    modifier = Modifier.padding(top = StarkSpacing.xSmall)
                )
            }
        }
    }
}
