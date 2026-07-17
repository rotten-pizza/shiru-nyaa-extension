package com.rottenpizza.videotrimmer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.rottenpizza.videotrimmer.data.AppPrefs
import com.rottenpizza.videotrimmer.ui.LibraryViewModel
import com.rottenpizza.videotrimmer.ui.TrimViewModel
import com.rottenpizza.videotrimmer.ui.screens.EditorScreen
import com.rottenpizza.videotrimmer.ui.screens.LibraryScreen
import com.rottenpizza.videotrimmer.ui.theme.VideoTrimmerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 9 and below write through the legacy MediaStore path, which
        // needs this permission. Android 10+ is scoped and needs nothing.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }

        setContent {
            VideoTrimmerTheme {
                AppRoot()
            }
        }
    }
}

private enum class Tab { EDIT, LIBRARY }

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
private fun AppRoot() {
    val trimVm: TrimViewModel = viewModel()
    val libVm: LibraryViewModel = viewModel()

    var tab by remember { mutableStateOf(Tab.EDIT) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Storage Access Framework exposes the real original filename (the photo
    // picker can hand back a synthetic numeric id for cloud/OEM items).
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
                // Not all providers allow persisting; reading still works now.
            }
            trimVm.loadVideo(uri)
            tab = Tab.EDIT
        }
    }
    val launchPicker = { pickLauncher.launch(arrayOf("video/*")) }

    val trimState by trimVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(trimState.message) {
        trimState.message?.let {
            snackbar.showSnackbar(it)
            trimVm.consumeMessage()
        }
    }
    LaunchedEffect(trimState.lastResults) {
        if (trimState.lastResults?.any { it.success } == true) libVm.refresh()
    }

    val libState by libVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(libState.message) {
        libState.message?.let {
            snackbar.showSnackbar(it)
            libVm.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Trimr") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.EDIT,
                    onClick = { tab = Tab.EDIT },
                    icon = { Icon(Icons.Filled.ContentCut, null) },
                    label = { Text("Trim") },
                )
                NavigationBarItem(
                    selected = tab == Tab.LIBRARY,
                    onClick = { tab = Tab.LIBRARY; libVm.refresh() },
                    icon = { Icon(Icons.Filled.VideoLibrary, null) },
                    label = { Text("Library") },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.EDIT -> EditorScreen(
                    vm = trimVm,
                    onPickVideo = { launchPicker() },
                    onOpenLibrary = { tab = Tab.LIBRARY; libVm.refresh() },
                )
                Tab.LIBRARY -> LibraryScreen(libVm)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    var patchMoov by remember { mutableStateOf(prefs.patchMoovTime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Patch embedded date", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Also rewrite the MP4's internal creation_time to the source date (experimental).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = patchMoov,
                        onCheckedChange = {
                            patchMoov = it
                            prefs.patchMoovTime = it
                        },
                    )
                }
                Spacer(Modifier.padding(6.dp))
                Text(
                    "All trims are stream-copy (no re-encode). The gallery date always matches the source.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
