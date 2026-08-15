package com.stark.note.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.stark.note.R
import com.stark.note.domain.note.ListItem
import com.stark.note.ui.components.DelayedLoadingState
import com.stark.note.ui.components.StarkTopBar
import com.stark.note.ui.theme.OnSurfaceDisabled
import com.stark.note.ui.theme.OnSurfacePrimary
import com.stark.note.ui.theme.OnSurfaceSecondary
import com.stark.note.ui.theme.StarkDimensions
import com.stark.note.ui.theme.StarkSpacing
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun ListEditorScreen(
    viewModel: ListEditorViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit = onBack,
    onMessage: (String) -> Unit
) {
    val note by viewModel.note.collectAsState()
    val isReady by viewModel.isReady.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val title by viewModel.title.collectAsState()
    val items by viewModel.items.collectAsState()
    val newItemContent by viewModel.newItemContent.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val lifecycleOwner = context as LifecycleOwner
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearCheckedConfirm by remember { mutableStateOf(false) }
    val uncheckedItems = items.filter { !it.isChecked }.sortedBy { it.position }
    val checkedItems = items.filter { it.isChecked }.sortedBy { it.position }

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collectLatest { resourceId ->
            onMessage(context.getString(resourceId))
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                scope.launch { viewModel.saveNow() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            StarkTopBar(
                onBack = onBack,
                backEnabled = isReady,
                actions = {
                    IconButton(
                        onClick = { showMenu = true },
                        enabled = isReady && note != null
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clear_checked_items)) },
                            onClick = {
                                showClearCheckedConfirm = true
                                showMenu = false
                            },
                            enabled = checkedItems.isNotEmpty()
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.delete_list),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showDeleteConfirm = true
                                showMenu = false
                            }
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AddItemBar(
                content = newItemContent,
                onContentChange = viewModel::onNewItemContentChanged,
                onAdd = viewModel::onAddItem,
                enabled = isReady && note != null
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                !isReady || note == null -> {
                    if (loadError != null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(loadError!!),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        DelayedLoadingState(modifier = Modifier.fillMaxSize())
                    }
                }
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = StarkDimensions.editorContentWidth)
                        .padding(horizontal = StarkSpacing.xLarge)
                ) {
                    ChecklistTitleField(
                        value = title,
                        onValueChange = viewModel::onTitleChanged
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    if (items.isEmpty()) {
                        EmptyChecklistState(modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = StarkSpacing.medium),
                            verticalArrangement = Arrangement.spacedBy(StarkSpacing.xSmall)
                        ) {
                            items(uncheckedItems, key = { it.id }) { item ->
                                ListItemRow(
                                    item = item,
                                    onToggle = { viewModel.onToggleItem(item) },
                                    onDelete = { viewModel.onDeleteItem(item) }
                                )
                            }
                            if (checkedItems.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                top = StarkSpacing.large,
                                                bottom = StarkSpacing.small
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.completed),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = OnSurfaceSecondary
                                        )
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier
                                                .padding(start = StarkSpacing.medium)
                                                .weight(1f)
                                        )
                                    }
                                }
                                items(checkedItems, key = { it.id }) { item ->
                                    ListItemRow(
                                        item = item,
                                        onToggle = { viewModel.onToggleItem(item) },
                                        onDelete = { viewModel.onDeleteItem(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearCheckedConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCheckedConfirm = false },
            title = { Text(stringResource(R.string.clear_checked_items_title)) },
            text = { Text(stringResource(R.string.clear_checked_items_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            viewModel.clearCheckedItems()
                            showClearCheckedConfirm = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCheckedConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_list_title)) },
            text = { Text(stringResource(R.string.delete_list_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            val deleted = viewModel.deleteNote()
                            showDeleteConfirm = false
                            if (deleted) onDeleted()
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ChecklistTitleField(
    value: String,
    onValueChange: (String) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.headlineMedium.copy(color = OnSurfacePrimary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = StarkSpacing.small, bottom = StarkSpacing.large),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = OnSurfaceSecondary
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun ListItemRow(
    item: ListItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val textColor by animateColorAsState(
        targetValue = if (item.isChecked) OnSurfaceDisabled else OnSurfacePrimary,
        label = "checklist text"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (item.isChecked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = StarkDimensions.minimumTouchTarget)
                .padding(start = StarkSpacing.xSmall, end = StarkSpacing.xSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = OnSurfaceSecondary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle()
                    }
                    .padding(vertical = StarkSpacing.medium)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = stringResource(R.string.delete_item),
                    tint = OnSurfaceSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyChecklistState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = stringResource(R.string.no_items),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceSecondary,
            modifier = Modifier.padding(top = StarkSpacing.large)
        )
    }
}

@Composable
fun AddItemBar(
    content: String,
    onContentChange: (String) -> Unit,
    onAdd: () -> Unit,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = StarkSpacing.large, vertical = StarkSpacing.medium),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = StarkDimensions.editorContentWidth),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = StarkSpacing.large, end = StarkSpacing.xSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = OnSurfaceSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(StarkSpacing.medium))
                BasicTextField(
                    value = content,
                    onValueChange = onContentChange,
                    readOnly = !enabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurfacePrimary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = StarkSpacing.medium),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = true
                    ),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (content.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.add_item),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OnSurfaceSecondary
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                AnimatedVisibility(
                    visible = content.isNotEmpty() && enabled,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(onClick = onAdd) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.add_item),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
