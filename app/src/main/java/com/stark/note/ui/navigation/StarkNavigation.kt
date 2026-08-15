package com.stark.note.ui.navigation

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stark.note.MainActivity
import com.stark.note.BuildConfig
import com.stark.note.R
import com.stark.note.StarkNoteApp
import com.stark.note.domain.export.ExportBackupUseCase
import com.stark.note.domain.export.ExportResult
import com.stark.note.domain.export.ImportBackupUseCase
import com.stark.note.domain.export.ImportResult
import com.stark.note.domain.export.MIN_BACKUP_PASSWORD_LENGTH
import com.stark.note.domain.note.NoteType
import com.stark.note.ui.auth.BiometricGate
import com.stark.note.ui.auth.BiometricUnlockPolicy
import com.stark.note.ui.auth.PinEntryScreen
import com.stark.note.ui.auth.PinSetupScreen
import com.stark.note.ui.editor.ListEditorScreen
import com.stark.note.ui.editor.ListEditorViewModel
import com.stark.note.ui.editor.NoteEditorScreen
import com.stark.note.ui.editor.NoteEditorViewModel
import com.stark.note.ui.list.NoteListScreen
import com.stark.note.ui.list.NoteListViewModel
import com.stark.note.ui.settings.SettingsScreen
import com.stark.note.ui.settings.SettingsViewModel
import com.stark.note.ui.settings.BackupDialogState
import com.stark.note.platform.storage.CredentialResult
import com.stark.note.platform.storage.PinVerifyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class Screen(val route: String) {
    object List : Screen("list")
    object Settings : Screen("settings")
    object NoteEditor : Screen("note_editor/{noteId}") {
        fun createRoute(noteId: String?) = "note_editor/${noteId ?: "new"}"
    }
    object ListEditor : Screen("list_editor/{noteId}") {
        fun createRoute(noteId: String?) = "list_editor/${noteId ?: "new"}"
    }
    object PinSetup : Screen("pin_setup")
    object PinGate : Screen("pin_gate")
}

@Composable
fun StarkNavigation() {
    val navController = rememberNavController()
    val activity = LocalContext.current as MainActivity
    val app = activity.application as StarkNoteApp
    val biometricGate = remember(activity) { BiometricGate(activity) }
    val hapticFeedback = LocalHapticFeedback.current

    val scope = rememberCoroutineScope()
    var pendingAuthActionName by rememberSaveable { mutableStateOf(PendingAuthAction.NONE.name) }
    var pendingAuthRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var showForgotPinOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showForgotPinResetDialog by rememberSaveable { mutableStateOf(false) }
    var lockAfterPinSetup by rememberSaveable { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(false) }
    var biometricEnabled by remember { mutableStateOf(false) }

    suspend fun refreshCredentialState() {
        hasPin = (app.securePreferences.hasPin() as? CredentialResult.Success)?.value == true
        biometricEnabled = (app.securePreferences.isBiometricUnlockEnabled(
            defaultEnabled = hasPin && biometricGate.canAuthenticate()
        ) as? CredentialResult.Success)?.value == true
    }

    LaunchedEffect(Unit) {
        app.pinResetCoordinator.resumeIfNeeded()
        refreshCredentialState()
    }

    fun requestPinAuth(action: PendingAuthAction, route: String? = null) {
        pendingAuthActionName = action.name
        pendingAuthRoute = route
        navController.navigate(Screen.PinGate.route)
    }

    fun showBiometricPrompt(
        onSuccess: () -> Unit,
        onFallbackToPin: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        biometricGate.authenticate(
            onSuccess = {
                app.securitySession.unlock()
                onSuccess()
            },
            onPinFallback = onFallbackToPin,
            onCancel = onCancel
        )
    }

    fun routineBiometricUnlockEnabled(): Boolean {
        val available = biometricGate.canAuthenticate()
        return BiometricUnlockPolicy.shouldUse(
            hasPin = hasPin,
            available = available,
            enabled = biometricEnabled
        )
    }

    fun openBiometricSettings() {
        runCatching {
            activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }.onFailure {
            Toast.makeText(activity, activity.getString(R.string.could_not_open_settings), Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.List.route,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Screen.List.route) {
            val listViewModel: NoteListViewModel = viewModel(
                factory = NoteListViewModel.provideFactory(app.noteRepository)
            )
            NoteListScreen(
                viewModel = listViewModel,
                onNoteClicked = { note ->
                    val destination = if (note.noteType == NoteType.LIST) Screen.ListEditor.createRoute(note.id) else Screen.NoteEditor.createRoute(note.id)
                    if (note.isLocked && !app.securitySession.isUnlocked.value && routineBiometricUnlockEnabled()) {
                        showBiometricPrompt(
                            onSuccess = { navController.navigate(destination) },
                            onFallbackToPin = {
                                requestPinAuth(PendingAuthAction.OPEN_NOTE, destination)
                            }
                        )
                    } else if (note.isLocked && !app.securitySession.isUnlocked.value) {
                        requestPinAuth(PendingAuthAction.OPEN_NOTE, destination)
                    } else {
                        navController.navigate(destination)
                    }
                },
                onAddNoteClicked = { type ->
                    val newNoteId = UUID.randomUUID().toString()
                    val destination = if (type == NoteType.LIST) {
                        Screen.ListEditor.createRoute(newNoteId)
                    } else {
                        Screen.NoteEditor.createRoute(newNoteId)
                    }
                    navController.navigate(destination)
                },
                onSettingsClicked = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.NoteEditor.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val noteIdArg = backStackEntry.arguments?.getString("noteId")
            val generatedNoteId = rememberSaveable(noteIdArg) { UUID.randomUUID().toString() }
            val noteId = if (noteIdArg == null || noteIdArg == "new") generatedNoteId else noteIdArg
            val editorViewModel: NoteEditorViewModel = viewModel(
                factory = NoteEditorViewModel.provideFactory(
                    app.noteRepository, app.lockUseCase, app.securitySession, { hasPin }, noteId
                ),
                key = noteId
            )
            val lockAfterPin by backStackEntry.savedStateHandle
                .getStateFlow(LOCK_AFTER_PIN_KEY, false)
                .collectAsState()
            val authSuccess by backStackEntry.savedStateHandle
                .getStateFlow(AUTH_SUCCESS_KEY, false)
                .collectAsState()
            LaunchedEffect(lockAfterPin) {
                if (lockAfterPin) {
                    backStackEntry.savedStateHandle[LOCK_AFTER_PIN_KEY] = false
                    editorViewModel.onPinSetupComplete()
                }
            }
            LaunchedEffect(authSuccess) {
                if (authSuccess) {
                    backStackEntry.savedStateHandle[AUTH_SUCCESS_KEY] = false
                    editorViewModel.onAuthSuccess()
                }
            }
            NoteEditorScreen(
                viewModel = editorViewModel,
                onBack = {
                    scope.launch {
                        if (editorViewModel.saveNow()) {
                            navController.popBackStack()
                        }
                    }
                },
                onDeleted = { navController.popBackStack() },
                onPinSetupRequired = {
                    lockAfterPinSetup = true
                    navController.navigate(Screen.PinSetup.route)
                },
                onAuthRequired = {
                    if (routineBiometricUnlockEnabled()) {
                        showBiometricPrompt(
                            onSuccess = { editorViewModel.onAuthSuccess() },
                            onFallbackToPin = {
                                requestPinAuth(PendingAuthAction.REVEAL_NOTE)
                            }
                        )
                    } else {
                        requestPinAuth(PendingAuthAction.REVEAL_NOTE)
                    }
                },
                onMessage = { message ->
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                }
            )
        }

        composable(
            route = Screen.ListEditor.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val noteIdArg = backStackEntry.arguments?.getString("noteId")
            val generatedNoteId = rememberSaveable(noteIdArg) { UUID.randomUUID().toString() }
            val noteId = if (noteIdArg == null || noteIdArg == "new") generatedNoteId else noteIdArg
            val listViewModel: ListEditorViewModel = viewModel(
                factory = ListEditorViewModel.provideFactory(app.noteRepository, noteId),
                key = noteId
            )
            ListEditorScreen(
                viewModel = listViewModel,
                onBack = {
                    scope.launch {
                        if (listViewModel.saveNow()) {
                            navController.popBackStack()
                        }
                    }
                },
                onDeleted = { navController.popBackStack() },
                onMessage = { message ->
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                }
            )
        }

        composable(Screen.Settings.route) { backStackEntry ->
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(
                    app.noteRepository,
                    ExportBackupUseCase(app.noteRepository, app.lockUseCase),
                    ImportBackupUseCase(app.noteRepository, app.lockUseCase)
                )
            )
            val backupUiState by settingsViewModel.backupUiState.collectAsState()
            val backupFileBridge = remember(activity) { activity.backupFileBridge() }
            val importUri by backupFileBridge.importUri.collectAsState()
            val exportUri by backupFileBridge.exportUri.collectAsState()
            val exportAuthorized by backStackEntry.savedStateHandle
                .getStateFlow(EXPORT_AUTHORIZED_KEY, false)
                .collectAsState()

            fun requestExportPassword() {
                settingsViewModel.showExportPassword()
            }

            LaunchedEffect(exportAuthorized) {
                if (exportAuthorized) {
                    backStackEntry.savedStateHandle[EXPORT_AUTHORIZED_KEY] = false
                    requestExportPassword()
                }
            }

            LaunchedEffect(exportUri) {
                val uri = exportUri ?: return@LaunchedEffect
                val export = backupFileBridge.readPendingExport(uri)
                if (export == null) {
                    Toast.makeText(activity, activity.getString(R.string.could_not_save_backup), Toast.LENGTH_SHORT).show()
                    return@LaunchedEffect
                }
                when (writeBackupText(activity.contentResolver, export.first, export.second)) {
                    BackupWriteResult.Success -> {
                        backupFileBridge.clearPendingExport()
                        Toast.makeText(activity, activity.getString(R.string.backup_saved), Toast.LENGTH_SHORT).show()
                    }
                    BackupWriteResult.Failed -> {
                        Toast.makeText(activity, activity.getString(R.string.could_not_save_backup), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            LaunchedEffect(importUri) {
                val uri = importUri ?: return@LaunchedEffect
                backupFileBridge.consumeImportUri()
                when (val result = readBackupText(activity.contentResolver, uri)) {
                    BackupReadResult.Empty -> {
                        Toast.makeText(activity, activity.getString(R.string.backup_file_empty), Toast.LENGTH_SHORT).show()
                    }
                    BackupReadResult.TooLarge -> {
                        Toast.makeText(activity, activity.getString(R.string.backup_file_too_large), Toast.LENGTH_SHORT).show()
                    }
                    BackupReadResult.Failed -> {
                        Toast.makeText(activity, activity.getString(R.string.could_not_read_backup), Toast.LENGTH_SHORT).show()
                    }
                    is BackupReadResult.Success -> {
                        settingsViewModel.showImportConfirmation(result.text)
                    }
                }
            }

            if (backupUiState.dialog == BackupDialogState.EXPORT_PASSWORD) {
                BackupPasswordDialog(
                    title = stringResource(R.string.backup_export_title),
                    description = stringResource(R.string.backup_export_description),
                    confirmRequired = true,
                    minimumLength = MIN_BACKUP_PASSWORD_LENGTH,
                    errorMessage = backupUiState.errorMessage,
                    inProgress = backupUiState.inProgress,
                    onDismiss = settingsViewModel::dismissBackupDialog,
                    onConfirm = { password ->
                        scope.launch {
                            val passwordChars = password.toCharArray()
                            try {
                                when (val result = settingsViewModel.exportBackup(passwordChars)) {
                                    is ExportResult.Success -> {
                                        if (!activity.openBackupExportPicker(defaultBackupFileName(), result.encryptedText)) {
                                            Toast.makeText(activity, activity.getString(R.string.could_not_open_saver), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    is ExportResult.Failure -> Unit
                                }
                            } finally {
                                passwordChars.fill('\u0000')
                            }
                        }
                    }
                )
            }

            if (backupUiState.dialog == BackupDialogState.IMPORT_CONFIRMATION) {
                AlertDialog(
                    onDismissRequest = settingsViewModel::dismissBackupDialog,
                    title = { Text(stringResource(R.string.import_confirmation_title)) },
                    text = {
                        Text(stringResource(R.string.import_confirmation_text))
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                settingsViewModel.showImportPassword()
                            }
                        ) {
                            Text(stringResource(R.string.import_notes))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = settingsViewModel::dismissBackupDialog
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (backupUiState.dialog == BackupDialogState.IMPORT_PASSWORD) {
                BackupPasswordDialog(
                    title = stringResource(R.string.backup_import_title),
                    description = stringResource(R.string.backup_import_description),
                    confirmRequired = false,
                    minimumLength = 1,
                    errorMessage = backupUiState.errorMessage,
                    inProgress = backupUiState.inProgress,
                    onDismiss = settingsViewModel::dismissBackupDialog,
                    onConfirm = { password ->
                        scope.launch {
                            val passwordChars = password.toCharArray()
                            try {
                                when (val result = settingsViewModel.importBackup(passwordChars)) {
                                    is ImportResult.Success -> {
                                        Toast.makeText(
                                            activity,
                                            activity.getString(R.string.backup_restored, result.noteCount),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        navController.popBackStack(Screen.List.route, false)
                                    }
                                    is ImportResult.Failure, null -> Unit
                                }
                            } finally {
                                passwordChars.fill('\u0000')
                            }
                        }
                    }
                )
            }

            SettingsScreen(
                onBack = { navController.popBackStack() },
                onExport = {
                    scope.launch {
                        if (!settingsViewModel.hasNotes()) {
                            Toast.makeText(activity, activity.getString(R.string.no_notes_to_export), Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val hasLockedNotes = settingsViewModel.hasLockedNotes()
                        if (hasLockedNotes && !app.securitySession.isUnlocked.value && routineBiometricUnlockEnabled()) {
                            showBiometricPrompt(
                                onSuccess = { requestExportPassword() },
                                onFallbackToPin = {
                                    requestPinAuth(PendingAuthAction.EXPORT_BACKUP)
                                }
                            )
                        } else if (hasLockedNotes && !app.securitySession.isUnlocked.value) {
                            requestPinAuth(PendingAuthAction.EXPORT_BACKUP)
                        } else {
                            requestExportPassword()
                        }
                    }
                },
                onImport = {
                    if (!activity.openBackupImportPicker()) {
                        Toast.makeText(activity, activity.getString(R.string.could_not_open_picker), Toast.LENGTH_SHORT).show()
                    }
                },
                onChangePin = {
                    if (hasPin) {
                        requestPinAuth(PendingAuthAction.CHANGE_PIN)
                    } else {
                        navController.navigate(Screen.PinSetup.route)
                    }
                },
                hasPin = hasPin,
                biometricEnabled = biometricEnabled,
                biometricAvailable = biometricGate.canAuthenticate(),
                onBiometricToggle = { enabled ->
                    if (hasPin && biometricGate.canAuthenticate()) {
                        scope.launch {
                            if (app.securePreferences.setBiometricUnlockEnabled(enabled) is CredentialResult.Success) {
                                biometricEnabled = enabled
                            }
                        }
                    }
                },
                onOpenBiometricSettings = ::openBiometricSettings,
                versionName = BuildConfig.VERSION_NAME
            )
        }

        composable(Screen.PinSetup.route) {
            PinSetupScreen(
                title = stringResource(if (hasPin) R.string.change_pin else R.string.set_pin),
                onPinConfirmed = { pin ->
                    scope.launch {
                        val saveResult = app.securePreferences.savePin(pin)
                        if (saveResult is CredentialResult.Success) {
                            refreshCredentialState()
                            if (lockAfterPinSetup) {
                                lockAfterPinSetup = false
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(LOCK_AFTER_PIN_KEY, true)
                            }
                        } else {
                            lockAfterPinSetup = false
                            Toast.makeText(activity, activity.getString(R.string.could_not_save_pin), Toast.LENGTH_SHORT).show()
                        }
                        navController.popBackStack()
                    }
                },
                onBack = {
                    lockAfterPinSetup = false
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.PinGate.route) {
            var error by remember { mutableStateOf<String?>(null) }
            val canResetWithBiometric = biometricGate.canAuthenticate()
            fun startBiometricPinReset() {
                showForgotPinOptionsDialog = false
                showBiometricPrompt(
                    onSuccess = {
                        pendingAuthActionName = PendingAuthAction.NONE.name
                        pendingAuthRoute = null
                        navController.navigate(Screen.PinSetup.route) {
                            popUpTo(Screen.PinGate.route) { inclusive = true }
                        }
                    },
                    onFallbackToPin = { showForgotPinResetDialog = true },
                    onCancel = { showForgotPinOptionsDialog = true }
                )
            }
            if (showForgotPinOptionsDialog) {
                AlertDialog(
                    onDismissRequest = { showForgotPinOptionsDialog = false },
                    title = { Text(stringResource(R.string.forgot_pin_title)) },
                    text = {
                        Text(
                            if (canResetWithBiometric) {
                                stringResource(R.string.forgot_pin_biometric)
                            } else {
                                stringResource(R.string.forgot_pin_no_biometric)
                            }
                        )
                    },
                    confirmButton = {
                        if (canResetWithBiometric) {
                            Button(onClick = { startBiometricPinReset() }) {
                                Text(stringResource(R.string.use_biometric))
                            }
                        } else {
                            Button(onClick = {
                                showForgotPinOptionsDialog = false
                                showForgotPinResetDialog = true
                            }) {
                                Text(stringResource(R.string.continue_label))
                            }
                        }
                    },
                    dismissButton = {
                        if (canResetWithBiometric) {
                            TextButton(
                                onClick = {
                                    showForgotPinOptionsDialog = false
                                    showForgotPinResetDialog = true
                                }
                            ) {
                                Text(stringResource(R.string.delete_locked_notes))
                            }
                        } else {
                            TextButton(onClick = { showForgotPinOptionsDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                )
            }
            if (showForgotPinResetDialog) {
                var resetConfirmation by remember { mutableStateOf("") }
                val canReset = resetConfirmation == "DELETE"
                AlertDialog(
                    onDismissRequest = {
                        resetConfirmation = ""
                        showForgotPinResetDialog = false
                    },
                    title = { Text(stringResource(R.string.reset_pin_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.reset_pin_warning))
                            OutlinedTextField(
                                value = resetConfirmation,
                                onValueChange = { resetConfirmation = it },
                                label = { Text(stringResource(R.string.type_delete)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                scope.launch {
                                    when (val resetResult = app.pinResetCoordinator.reset()) {
                                        is com.stark.note.domain.lock.PinResetResult.Success -> {
                                        app.securitySession.lock()
                                        refreshCredentialState()
                                        pendingAuthActionName = PendingAuthAction.NONE.name
                                        pendingAuthRoute = null
                                        resetConfirmation = ""
                                        showForgotPinResetDialog = false
                                        Toast.makeText(activity, activity.getString(R.string.pin_reset_success), Toast.LENGTH_SHORT).show()
                                        navController.popBackStack(Screen.List.route, false)
                                        }
                                        is com.stark.note.domain.lock.PinResetResult.Failure -> {
                                        Toast.makeText(
                                            activity,
                                            activity.getString(
                                                if (resetResult.lockedNotesDeleted) {
                                                    R.string.pin_reset_finish_failed
                                                } else {
                                                    R.string.pin_reset_failed
                                                }
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        }
                                    }
                                }
                            },
                            enabled = canReset
                        ) {
                            Text(stringResource(R.string.reset))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            resetConfirmation = ""
                            showForgotPinResetDialog = false
                        }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
            PinEntryScreen(
                title = stringResource(R.string.enter_pin),
                onPinEntered = { pin ->
                    scope.launch {
                        val credentialResult = app.securePreferences.verifyPin(pin)
                        if (credentialResult !is CredentialResult.Success) {
                            error = activity.getString(R.string.could_not_verify_pin)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            return@launch
                        }
                        val result = credentialResult.value
                        when (result) {
                            PinVerifyResult.Success -> {
                                app.securitySession.unlock()
                                val action = runCatching {
                                    PendingAuthAction.valueOf(pendingAuthActionName)
                                }.getOrDefault(PendingAuthAction.NONE)
                                val route = pendingAuthRoute
                                pendingAuthActionName = PendingAuthAction.NONE.name
                                pendingAuthRoute = null
                                when (action) {
                                    PendingAuthAction.OPEN_NOTE -> if (route != null) {
                                        navController.navigate(route) {
                                            popUpTo(Screen.PinGate.route) { inclusive = true }
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                    PendingAuthAction.REVEAL_NOTE -> {
                                        navController.previousBackStackEntry?.savedStateHandle?.set(AUTH_SUCCESS_KEY, true)
                                        navController.popBackStack()
                                    }
                                    PendingAuthAction.EXPORT_BACKUP -> {
                                        navController.previousBackStackEntry?.savedStateHandle?.set(EXPORT_AUTHORIZED_KEY, true)
                                        navController.popBackStack()
                                    }
                                    PendingAuthAction.CHANGE_PIN -> navController.navigate(Screen.PinSetup.route) {
                                        popUpTo(Screen.PinGate.route) { inclusive = true }
                                    }
                                    PendingAuthAction.NONE -> navController.popBackStack()
                                }
                            }
                            PinVerifyResult.Failure -> {
                                error = activity.getString(R.string.wrong_pin)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            is PinVerifyResult.LockedOut -> {
                                error = activity.getString(R.string.pin_locked_out, result.remainingSeconds)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                },
                onBack = { navController.popBackStack() },
                errorMessage = error,
                onForgotPin = {
                    showForgotPinOptionsDialog = true
                }
            )
        }
    }
}

private enum class PendingAuthAction {
    NONE,
    OPEN_NOTE,
    REVEAL_NOTE,
    EXPORT_BACKUP,
    CHANGE_PIN
}

private const val LOCK_AFTER_PIN_KEY = "starknote_lock_after_pin"
private const val AUTH_SUCCESS_KEY = "starknote_auth_success"
private const val EXPORT_AUTHORIZED_KEY = "starknote_export_authorized"

@Composable
private fun BackupPasswordDialog(
    title: String,
    description: String,
    confirmRequired: Boolean,
    minimumLength: Int,
    errorMessage: String?,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val localError = when {
        password.isEmpty() -> null
        password.length < minimumLength -> pluralStringResource(R.plurals.password_minimum, minimumLength, minimumLength)
        confirmRequired && confirmation.isNotEmpty() && password != confirmation -> stringResource(R.string.password_mismatch)
        else -> errorMessage
    }
    val canSubmit = password.length >= minimumLength && (!confirmRequired || password == confirmation) && !inProgress

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                if (confirmRequired) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(stringResource(R.string.confirm_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                }
                if (localError != null) {
                    Text(text = localError)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val submittedPassword = password
                    password = ""
                    confirmation = ""
                    onConfirm(submittedPassword)
                },
                enabled = canSubmit
            ) {
                Text(if (inProgress) stringResource(R.string.working) else stringResource(R.string.continue_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private sealed class BackupReadResult {
    data class Success(val text: String) : BackupReadResult()
    object Empty : BackupReadResult()
    object TooLarge : BackupReadResult()
    object Failed : BackupReadResult()
}

private sealed class BackupWriteResult {
    object Success : BackupWriteResult()
    object Failed : BackupWriteResult()
}

private suspend fun readBackupText(contentResolver: ContentResolver, uri: Uri): BackupReadResult {
    return withContext(Dispatchers.IO) {
        runCatching {
            val size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length
            } ?: -1L
            if (size > MAX_BACKUP_FILE_BYTES) {
                return@withContext BackupReadResult.TooLarge
            }
            val text = try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readLimitedText(MAX_BACKUP_FILE_BYTES)
                }.orEmpty()
            } catch (e: BackupTooLargeException) {
                return@withContext BackupReadResult.TooLarge
            }
            if (text.isBlank()) BackupReadResult.Empty else BackupReadResult.Success(text)
        }.getOrElse {
            BackupReadResult.Failed
        }
    }
}

private suspend fun writeBackupText(contentResolver: ContentResolver, uri: Uri, text: String): BackupWriteResult {
    return withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.writeText(text)
            } ?: return@withContext BackupWriteResult.Failed
            BackupWriteResult.Success
        }.getOrElse {
            BackupWriteResult.Failed
        }
    }
}

private fun InputStream.readLimitedText(maxBytes: Int): String {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    val output = ByteArrayOutputStream()
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        total += read
        if (total > maxBytes) {
            throw BackupTooLargeException()
        }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

private fun OutputStream.writeText(text: String) {
    write(text.toByteArray(Charsets.UTF_8))
}

private fun defaultBackupFileName(): String {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return "starknote-backup-$date.json"
}

private class BackupTooLargeException : IllegalStateException()

private const val MAX_BACKUP_FILE_BYTES = 10 * 1024 * 1024
