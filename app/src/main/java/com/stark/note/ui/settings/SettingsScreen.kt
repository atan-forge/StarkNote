package com.stark.note.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stark.note.R
import com.stark.note.ui.components.StarkIconContainer
import com.stark.note.ui.components.StarkSection
import com.stark.note.ui.components.StarkTopBar
import com.stark.note.ui.theme.OnSurfaceSecondary
import com.stark.note.ui.theme.StarkDimensions
import com.stark.note.ui.theme.StarkSpacing

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onChangePin: () -> Unit,
    hasPin: Boolean,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    onOpenBiometricSettings: () -> Unit,
    versionName: String
) {
    var showAbout by remember { mutableStateOf(false) }
    var selectedBiometricEnabled by remember(biometricEnabled) { mutableStateOf(biometricEnabled) }

    fun updateBiometric(enabled: Boolean) {
        selectedBiometricEnabled = enabled
        onBiometricToggle(enabled)
    }

    Scaffold(
        topBar = {
            StarkTopBar(
                title = stringResource(R.string.settings),
                onBack = onBack
            )
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
                    .widthIn(max = StarkDimensions.settingsContentWidth)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = StarkSpacing.large),
                verticalArrangement = Arrangement.spacedBy(StarkSpacing.xLarge)
            ) {
                Spacer(modifier = Modifier.size(StarkSpacing.xSmall))
                StarkSection(title = stringResource(R.string.security)) {
                    SettingsRow(
                        title = stringResource(if (hasPin) R.string.change_pin else R.string.set_pin),
                        subtitle = stringResource(
                            if (hasPin) R.string.change_pin_subtitle
                            else R.string.set_pin_subtitle
                        ),
                        icon = Icons.Default.LockReset,
                        onClick = onChangePin
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 72.dp)
                    )
                    BiometricSettingsRow(
                        enabled = hasPin && biometricAvailable,
                        checked = selectedBiometricEnabled && hasPin && biometricAvailable,
                        title = stringResource(R.string.biometric_unlock),
                        subtitle = when {
                            !hasPin -> stringResource(R.string.biometric_requires_pin)
                            !biometricAvailable -> stringResource(R.string.biometric_setup_required)
                            else -> stringResource(R.string.biometric_unlock_subtitle)
                        },
                        onClick = when {
                            !hasPin -> onChangePin
                            !biometricAvailable -> onOpenBiometricSettings
                            else -> ({ updateBiometric(!selectedBiometricEnabled) })
                        },
                        onCheckedChange = ::updateBiometric
                    )
                }

                StarkSection(title = stringResource(R.string.data_management)) {
                    SettingsRow(
                        title = stringResource(R.string.export_notes),
                        subtitle = stringResource(R.string.save_encrypted_backup),
                        icon = Icons.Default.FileUpload,
                        onClick = onExport
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 72.dp)
                    )
                    SettingsRow(
                        title = stringResource(R.string.import_notes),
                        subtitle = stringResource(R.string.restore_backup),
                        icon = Icons.Default.SaveAlt,
                        onClick = onImport
                    )
                }

                StarkSection(title = stringResource(R.string.about)) {
                    SettingsRow(
                        title = stringResource(R.string.about_starknote),
                        subtitle = stringResource(R.string.about_subtitle),
                        icon = Icons.Default.Info,
                        onClick = { showAbout = true }
                    )
                }

                Text(
                    text = stringResource(R.string.version_format, versionName),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = StarkSpacing.xLarge)
                )
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.about_starknote)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(StarkSpacing.medium)
                ) {
                    Text(
                        stringResource(R.string.version_format, versionName),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(stringResource(R.string.license_apache), color = OnSurfaceSecondary)
                    Text(stringResource(R.string.about_local))
                    Text(stringResource(R.string.about_encryption))
                    Text(stringResource(R.string.about_backup_password))
                    Text(stringResource(R.string.about_backup_recovery))
                    Text(stringResource(R.string.about_pin_recovery))
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = StarkDimensions.minimumTouchTarget)
            .padding(horizontal = StarkSpacing.large, vertical = StarkSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StarkIconContainer(icon = icon)
        Spacer(modifier = Modifier.width(StarkSpacing.large))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceSecondary,
                modifier = Modifier.padding(top = StarkSpacing.xSmall)
            )
        }
    }
}

@Composable
private fun BiometricSettingsRow(
    enabled: Boolean,
    checked: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = StarkDimensions.minimumTouchTarget)
            .padding(horizontal = StarkSpacing.large, vertical = StarkSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StarkIconContainer(icon = Icons.Default.Fingerprint)
        Spacer(modifier = Modifier.width(StarkSpacing.large))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceSecondary,
                modifier = Modifier.padding(top = StarkSpacing.xSmall)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.padding(start = StarkSpacing.small)
        )
    }
}
