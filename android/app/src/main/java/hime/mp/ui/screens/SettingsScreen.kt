package hime.mp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hime.mp.R
import hime.mp.core.PlaybackState
import hime.mp.core.Track
import hime.mp.data.PlaybackController
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

    fun updateDiscordSetting(key: String, value: String) {
        viewModel.setSetting(key, value)
        DiscordRpcManager.updateOptions(settings + (key to value))
    }

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
                val state by PlaybackController.state.collectAsState()
                val track by PlaybackController.currentTrack.collectAsState()
                SettingSwitch(
                    label = "Enable Rich Presence",
                    checked = settings["discordRpc"]?.toBoolean() ?: false,
                    onChange = {
                        updateDiscordSetting("discordRpc", it.toString())
                        DiscordRpcManager.setEnabled(it)
                    },
                )
                Spacer(Modifier.height(8.dp))
                SettingDropdown(
                    label = "Presence status",
                    value = settings["discordPresenceStatus"] ?: "auto",
                    options = listOf(
                        "auto" to "Auto",
                        "online" to "Online",
                        "idle" to "Idle",
                        "dnd" to "Do Not Disturb",
                        "invisible" to "Invisible",
                    ),
                    onChange = { updateDiscordSetting("discordPresenceStatus", it) },
                )
                SettingDropdown(
                    label = "Activity type",
                    value = settings["discordActivityType"] ?: "2",
                    options = listOf(
                        "0" to "Playing",
                        "1" to "Streaming",
                        "2" to "Listening",
                        "3" to "Watching",
                    ),
                    onChange = { updateDiscordSetting("discordActivityType", it) },
                )
                SettingTextField(
                    label = "Activity name",
                    value = settings["discordName"] ?: DiscordRpcManager.DEFAULT_NAME,
                    onChange = { updateDiscordSetting("discordName", it) },
                )
                SettingTextField(
                    label = "Details",
                    value = settings["discordDetails"] ?: DiscordRpcManager.DEFAULT_DETAILS,
                    onChange = { updateDiscordSetting("discordDetails", it) },
                )
                SettingTextField(
                    label = "State",
                    value = settings["discordState"] ?: DiscordRpcManager.DEFAULT_STATE,
                    onChange = { updateDiscordSetting("discordState", it) },
                )
                Text(
                    "Formats: <hime.player.song.name> <hime.player.song.artist> <hime.player.song.album> <hime.player.song.info> <hime.player.status> <hime.player.song.duration> <hime.player.song.remaining>",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val connecting = rpcStatus == "Waiting for Discord" || rpcStatus.startsWith("Connecting")
                Button(
                    onClick = {
                        if (rpcStatus == "Connected") {
                            DiscordRpcManager.setEnabled(false)
                            updateDiscordSetting("discordRpc", "false")
                        } else {
                            DiscordRpcManager.authorize(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !connecting,
                ) {
                    Text(
                        when {
                            rpcStatus == "Connected" -> "Disconnect"
                            connecting -> "Connecting..."
                            rpcStatus.startsWith("Error") -> "Retry"
                            else -> "Connect"
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Status: $rpcStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                DiscordPresencePreview(state, track, settings)
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

@Composable
private fun SettingDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == value }?.second ?: value
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: $current", modifier = Modifier.weight(1f))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onChange(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingTextField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun DiscordPresencePreview(
    state: PlaybackState,
    track: Track?,
    settings: Map<String, String>,
) {
    val name = settings["discordName"]?.takeIf { it.isNotBlank() } ?: DiscordRpcManager.DEFAULT_NAME
    val typeLabel = when (settings["discordActivityType"]?.toIntOrNull() ?: 2) {
        0 -> "PLAYING"
        1 -> "STREAMING"
        3 -> "WATCHING"
        else -> "LISTENING"
    }
    val details = DiscordRpcManager.renderTemplate(
        settings["discordDetails"] ?: DiscordRpcManager.DEFAULT_DETAILS,
        state,
        track,
    )
    val stateText = DiscordRpcManager.renderTemplate(
        settings["discordState"] ?: DiscordRpcManager.DEFAULT_STATE,
        state,
        track,
    )
    val timing = if (state.playing && state.durationMs > 0 && track != null) {
        "${formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0))} left"
    } else {
        ""
    }
    Text("Preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF313338),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$typeLabel $name",
                    color = Color(0xFF949BA4),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (track == null) "No track playing" else details.ifBlank { "Unknown track" },
                    color = Color(0xFFF2F3F5),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stateText,
                    color = Color(0xFFB5BAC1),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (timing.isNotBlank()) {
                    Text(
                        timing,
                        color = Color(0xFFB5BAC1),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
