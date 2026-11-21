package hime.mp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hime.mp.ui.components.SectionHeader
import hime.mp.util.Permissions
import hime.mp.util.formatBytes
import hime.mp.util.formatDuration
import hime.mp.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.stats.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            Permissions.takePersistablePermission(context, uri)
            Permissions.treeUriToPath(uri)?.let { viewModel.addFolder(it) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.notice.collect { notice = it }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Section("Library & storage")
            folders.forEach { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(folder, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { viewModel.removeFolder(folder) }) {
                        Icon(Icons.Default.Delete, "Remove folder")
                    }
                }
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Button(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Add folder")
                }
                Button(onClick = { viewModel.rescan(incremental = true) }, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Incremental rescan")
                }
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Button(onClick = { viewModel.rescan(incremental = false) }) {
                    Text("Full rescan")
                }
                Button(onClick = { viewModel.clearCache() }, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Clear cache")
                }
            }
            Text(
                "${stats.tracks} tracks • ${stats.albums} albums • ${stats.artists} artists • ${stats.playlists} playlists",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${formatBytes(stats.totalBytes)} • ${formatDuration(stats.totalDurationMs)}",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Section("Appearance & motion")
            val theme = settings["theme"] ?: "system"
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                    FilterChip(
                        selected = theme == value,
                        onClick = { viewModel.setSetting("theme", value) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            SettingSwitch(
                label = "Dynamic color",
                checked = settings["dynamicColor"]?.toBoolean() ?: true,
                onChange = { viewModel.setSetting("dynamicColor", it.toString()) },
            )
            SettingSwitch(
                label = "Reduced motion",
                checked = settings["reducedMotion"]?.toBoolean() ?: false,
                onChange = { viewModel.setSetting("reducedMotion", it.toString()) },
            )
        }
        item {
            Section("Audio & playback")
            SettingSwitch(
                label = "Gapless playback",
                checked = settings["gapless"]?.toBoolean() ?: true,
                onChange = { viewModel.setSetting("gapless", it.toString()) },
            )
            SettingSwitch(
                label = "Audio focus handling",
                checked = settings["audioFocus"]?.toBoolean() ?: true,
                onChange = { viewModel.setSetting("audioFocus", it.toString()) },
            )
        }
        item {
            notice?.let {
                Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    SectionHeader(title, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
