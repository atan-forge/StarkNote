package com.stark.note.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.stark.note.ui.theme.AccentMuted
import com.stark.note.ui.theme.OnSurfacePrimary
import com.stark.note.ui.theme.StarkDimensions
import com.stark.note.ui.theme.StarkSpacing
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarkTopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, enabled = backEnabled) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            navigationIconContentColor = OnSurfacePrimary,
            titleContentColor = OnSurfacePrimary,
            actionIconContentColor = OnSurfacePrimary
        )
    )
}

@Composable
fun StarkSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = StarkSpacing.xSmall, bottom = StarkSpacing.small)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(content = { content() })
        }
    }
}

@Composable
fun StarkIconContainer(
    icon: ImageVector,
    contentDescription: String? = null
) {
    Box(
        modifier = Modifier
            .size(StarkDimensions.iconContainer)
            .background(AccentMuted, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(StarkDimensions.icon)
        )
    }
}

@Composable
fun DelayedLoadingState(
    modifier: Modifier = Modifier,
    delayMillis: Long = 250
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        visible = true
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (visible) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
