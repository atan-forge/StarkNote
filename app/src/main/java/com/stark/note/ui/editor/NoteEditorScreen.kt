package com.stark.note.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.stark.note.R
import com.stark.note.ui.components.DelayedLoadingState
import com.stark.note.ui.components.StarkTopBar
import com.stark.note.ui.theme.Accent
import com.stark.note.ui.theme.Locked
import com.stark.note.ui.theme.OnSurfacePrimary
import com.stark.note.ui.theme.OnSurfaceSecondary
import com.stark.note.ui.theme.SearchHighlight
import com.stark.note.ui.theme.StarkDimensions
import com.stark.note.ui.theme.StarkSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit = onBack,
    onPinSetupRequired: () -> Unit,
    onAuthRequired: () -> Unit,
    onMessage: (String) -> Unit
) {
    val title by viewModel.title.collectAsState()
    val body by viewModel.body.collectAsState()
    val note by viewModel.note.collectAsState()
    val isReady by viewModel.isReady.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val decryptFailed by viewModel.decryptFailed.collectAsState()
    val contentLoading by viewModel.contentLoading.collectAsState()
    val findQuery by viewModel.findQuery.collectAsState()
    val searchMatches by viewModel.searchMatches.collectAsState()
    val selectedSearchMatchIndex by viewModel.selectedSearchMatchIndex.collectAsState()
    val canEdit = isReady && !contentLoading && note != null &&
        (note?.isLocked != true || (isUnlocked && !decryptFailed))
    val selectedSearchMatch = searchMatches.getOrNull(selectedSearchMatchIndex)
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val lifecycleOwner = context as LifecycleOwner
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRemoveLockConfirm by remember { mutableStateOf(false) }
    var isFindExpanded by remember { mutableStateOf(false) }
    val bodyFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var isFieldPlaced by remember { mutableStateOf(false) }
    var bodyFieldValue by remember { mutableStateOf(TextFieldValue(body)) }
    val searchScrollState = rememberScrollState()
    var searchTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var searchViewportHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                EditorUiEvent.SetupPinRequired -> onPinSetupRequired()
                EditorUiEvent.AuthRequired -> onAuthRequired()
                is EditorUiEvent.Message -> onMessage(context.getString(event.resourceId))
            }
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

    LaunchedEffect(canEdit, isFieldPlaced) {
        if (canEdit && isFieldPlaced && body.isEmpty() && !isFindExpanded) {
            delay(100)
            runCatching { bodyFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(isFindExpanded) {
        if (isFindExpanded) {
            searchScrollState.scrollTo(0)
            delay(100)
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(canEdit) {
        if (!canEdit && isFindExpanded) {
            isFindExpanded = false
            viewModel.onFindQueryChanged("")
        }
    }

    LaunchedEffect(body) {
        if (bodyFieldValue.text != body) {
            bodyFieldValue = bodyFieldValue.copy(text = body, selection = TextRange(body.length))
        }
    }

    LaunchedEffect(
        isFindExpanded,
        selectedSearchMatchIndex,
        searchMatches,
        searchTextLayout,
        searchViewportHeight
    ) {
        if (!isFindExpanded) return@LaunchedEffect
        val match = selectedSearchMatch
        val layout = searchTextLayout
        if (match != null && layout != null && searchViewportHeight > 0) {
            val line = layout.getLineForOffset(match.start.coerceIn(0, body.length))
            val lineCenter = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
            val target = (lineCenter - searchViewportHeight / 2f)
                .toInt()
                .coerceIn(0, max(0, layout.size.height - searchViewportHeight))
            searchScrollState.animateScrollTo(target)
        }
    }

    Scaffold(
        topBar = {
            Column {
                StarkTopBar(
                    onBack = onBack,
                    backEnabled = isReady && !contentLoading,
                    actions = {
                        if (isFindExpanded) {
                            IconButton(
                                onClick = {
                                    isFindExpanded = false
                                    viewModel.onFindQueryChanged("")
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_search))
                            }
                        } else {
                            IconButton(
                                onClick = { isFindExpanded = true },
                                enabled = note != null && canEdit
                            ) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                            }
                            IconButton(
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onToggleLockRequested()
                                },
                                enabled = isReady && !contentLoading && note != null
                            ) {
                                Icon(
                                    imageVector = if (note?.isLocked == true) Icons.Rounded.Lock else Icons.Outlined.Lock,
                                    contentDescription = stringResource(R.string.lock_note),
                                    tint = if (note?.isLocked == true) Locked else OnSurfacePrimary
                                )
                            }
                            IconButton(
                                onClick = { showMenu = true },
                                enabled = isReady && !contentLoading && note != null
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                if (note?.isLocked == true && isUnlocked && !decryptFailed) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.remove_lock)) },
                                        onClick = {
                                            showRemoveLockConfirm = true
                                            showMenu = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.delete_note),
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
                    }
                )
                AnimatedVisibility(
                    visible = isFindExpanded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SearchBar(
                        query = findQuery,
                        matchCount = searchMatches.size,
                        selectedMatchIndex = selectedSearchMatchIndex,
                        focusRequester = searchFocusRequester,
                        onQueryChanged = viewModel::onFindQueryChanged,
                        onPrevious = viewModel::onPreviousSearchMatch,
                        onNext = viewModel::onNextSearchMatch
                    )
                }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = StarkDimensions.editorContentWidth)
                    .padding(horizontal = StarkSpacing.xLarge)
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
                    else -> {
                        DocumentTitleField(
                            value = title,
                            onValueChange = viewModel::onTitleChanged,
                            readOnly = !canEdit || isFindExpanded
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Box(modifier = Modifier.weight(1f)) {
                            when {
                                isFindExpanded -> SearchBody(
                                    body = body,
                                    matches = searchMatches,
                                    selectedMatchIndex = selectedSearchMatchIndex,
                                    scrollState = searchScrollState,
                                    onTextLayout = { searchTextLayout = it },
                                    onViewportHeightChanged = { searchViewportHeight = it }
                                )
                                contentLoading -> DelayedLoadingState(modifier = Modifier.fillMaxSize())
                                canEdit -> BasicTextField(
                                    value = bodyFieldValue,
                                    onValueChange = {
                                        bodyFieldValue = it
                                        viewModel.onBodyChanged(it.text)
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurfacePrimary),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = StarkSpacing.large)
                                        .onGloballyPositioned { isFieldPlaced = true }
                                        .focusRequester(bodyFocusRequester),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (body.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.note_body),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = OnSurfaceSecondary
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                                else -> LockedNoteState(decryptFailed = decryptFailed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val lockedDelete = note?.isLocked == true
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    stringResource(
                        if (lockedDelete) R.string.delete_locked_note_title
                        else R.string.delete_note_title
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (lockedDelete) R.string.delete_locked_note_text
                        else R.string.delete_note_text
                    )
                )
            },
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

    if (showRemoveLockConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveLockConfirm = false },
            title = { Text(stringResource(R.string.remove_lock_title)) },
            text = { Text(stringResource(R.string.remove_lock_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.onRemoveEncryption()
                        showRemoveLockConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.remove_lock))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveLockConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    matchCount: Int,
    selectedMatchIndex: Int,
    focusRequester: FocusRequester,
    onQueryChanged: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StarkSpacing.large, vertical = StarkSpacing.small),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(
                start = StarkSpacing.large,
                top = StarkSpacing.medium,
                end = StarkSpacing.small,
                bottom = StarkSpacing.xSmall
            )
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurfacePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                cursorBrush = SolidColor(Accent),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(stringResource(R.string.search_note), color = OnSurfaceSecondary)
                        }
                        innerTextField()
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        query.isBlank() -> ""
                        matchCount == 0 -> stringResource(R.string.search_no_matches)
                        else -> stringResource(
                            R.string.search_match_count,
                            selectedMatchIndex + 1,
                            matchCount
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onPrevious, enabled = matchCount > 0) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.previous_match)
                    )
                }
                IconButton(onClick = onNext, enabled = matchCount > 0) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.next_match)
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
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
private fun LockedNoteState(decryptFailed: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Text(
            text = stringResource(
                if (decryptFailed) R.string.decrypt_failed_content
                else R.string.locked_note_content
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = StarkSpacing.large)
        )
    }
}

@Composable
private fun SearchBody(
    body: String,
    matches: List<SearchMatch>,
    selectedMatchIndex: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    onTextLayout: (TextLayoutResult) -> Unit,
    onViewportHeightChanged: (Int) -> Unit
) {
    val text = remember(body, matches, selectedMatchIndex) {
        buildAnnotatedString {
            var cursor = 0
            matches.forEachIndexed { index, match ->
                val start = match.start.coerceIn(cursor, body.length)
                val end = match.end.coerceIn(start, body.length)
                append(body.substring(cursor, start))
                if (end > start) {
                    withStyle(
                        SpanStyle(
                            background = if (index == selectedMatchIndex) Accent else SearchHighlight,
                            color = if (index == selectedMatchIndex) Color.Black else OnSurfacePrimary
                        )
                    ) {
                        append(body.substring(start, end))
                    }
                }
                cursor = end
            }
            append(body.substring(cursor))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .onGloballyPositioned { onViewportHeightChanged(it.size.height) }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(color = OnSurfacePrimary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = StarkSpacing.large),
            onTextLayout = onTextLayout
        )
    }
}
