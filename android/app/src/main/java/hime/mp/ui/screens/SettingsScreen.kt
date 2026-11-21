package hime.mp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hime.mp.discord.DiscordRpcManager
import hime.mp.ui.components.CardShape
import hime.mp.ui.components.SectionHeader
import hime.mp.ui.viewmodel.SettingsViewModel
import hime.mp.util.Permissions
import hime.mp.util.formatBytes
import hime.mp.util.formatDuration

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
            SettingsCard {
                folders.forEach { folder ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                folder,
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            FilledTonalIconButton(
                                onClick = { viewModel.removeFolder(folder) },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.Delete, "Remove folder", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                ) {
                    SettingIconButton(Icons.Default.Add, "Add") { folderPicker.launch(null) }
                    SettingIconButton(Icons.Default.Refresh, "Quick") { viewModel.rescan(incremental = true) }
                    SettingIconButton(Icons.Default.Update, "Full") { viewModel.rescan(incremental = false) }
                    SettingIconButton(Icons.Default.Delete, "Cache") { viewModel.clearCache() }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "${stats.tracks} tracks • ${stats.albums} albums • ${stats.artists} artists • ${stats.playlists} playlists",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${formatBytes(stats.totalBytes)} • ${formatDuration(stats.totalDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Section("Appearance & motion")
            SettingsCard {
                val theme = settings["theme"] ?: "system"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                ) {
                    SettingIconButton(Icons.Default.BrightnessAuto, "System", theme == "system") {
                        viewModel.setSetting("theme", "system")
                    }
                    SettingIconButton(Icons.Default.WbSunny, "Light", theme == "light") {
                        viewModel.setSetting("theme", "light")
                    }
                    SettingIconButton(Icons.Default.ModeNight, "Dark", theme == "dark") {
                        viewModel.setSetting("theme", "dark")
                    }
                }
                Spacer(Modifier.height(12.dp))
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
        }
        item {
            Section("Audio & playback")
            SettingsCard {
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
        }
        item {
            Section("Discord Rich Presence")
            SettingsCard {
                val rpcStatus by DiscordRpcManager.status.collectAsState()
                SettingSwitch(
                    label = "Enable Rich Presence",
                    checked = settings["discordRpc"]?.toBoolean() ?: false,
                    onChange = {
                        viewModel.setSetting("discordRpc", it.toString())
                        DiscordRpcManager.setEnabled(it)
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                ) {
                    Button(onClick = { DiscordRpcManager.authorize(context) }) {
                        Text(if (rpcStatus == "Connected") "Reconnect" else "Connect")
                    }
                    OutlinedButton(onClick = { DiscordRpcManager.logout() }) {
                        Text("Logout")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Status: $rpcStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Upload an art asset named himemp_icon in the Discord portal to show the app logo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingIconButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(50.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
