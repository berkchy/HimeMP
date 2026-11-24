package hime.mp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SettingsSection("Library & storage") {
                folders.forEach { folder ->
                    SettingsRow(
                        title = folder,
                        description = "Music library folder",
                        leadingIcon = Icons.Default.Folder,
                        trailing = {
                            FilledTonalIconButton(
                                onClick = { viewModel.removeFolder(folder) },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.Delete, "Remove folder", modifier = Modifier.size(18.dp))
                            }
                        },
                    )
                    SettingsDivider()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsActionButton(Icons.Default.Add, "Add") { folderPicker.launch(null) }
                    SettingsActionButton(Icons.Default.Refresh, "Quick") { viewModel.rescan(incremental = true) }
                    SettingsActionButton(Icons.Default.Update, "Full") { viewModel.rescan(incremental = false) }
                    SettingsActionButton(Icons.Default.Delete, "Cache") { viewModel.clearCache() }
                }
                SettingsDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Library stats",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${stats.tracks} tracks • ${stats.albums} albums • ${stats.artists} artists • ${stats.playlists} playlists",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${formatBytes(stats.totalBytes)} • ${formatDuration(stats.totalDurationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SettingsSection("Appearance & motion") {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val theme = settings["theme"] ?: "system"
                    val themes = listOf(
                        Triple("system", "System", Icons.Default.BrightnessAuto),
                        Triple("light", "Light", Icons.Default.WbSunny),
                        Triple("dark", "Dark", Icons.Default.ModeNight),
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themes.forEachIndexed { index, (key, label, icon) ->
                            SegmentedButton(
                                selected = theme == key,
                                onClick = { viewModel.setSetting("theme", key) },
                                shape = SegmentedButtonDefaults.itemShape(index, themes.size),
                                icon = {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                }
                SettingsDivider()
                SettingsDropdownRow(
                    label = "Color source",
                    value = settings["colorSource"] ?: "adaptive",
                    options = listOf(
                        "adaptive" to "Adaptive",
                        "album" to "Album",
                        "system" to "System",
                        "classic" to "Classic",
                    ),
                    leadingIcon = Icons.Default.AutoAwesome,
                    onChange = { viewModel.setSetting("colorSource", it) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    label = "Dynamic color",
                    description = "Use wallpaper-derived colors",
                    leadingIcon = Icons.Default.AutoAwesome,
                    checked = settings["dynamicColor"]?.toBoolean() ?: true,
                    onChange = { viewModel.setSetting("dynamicColor", it.toString()) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    label = "AMOLED dark",
                    description = "Pure black background in dark theme",
                    leadingIcon = Icons.Default.ModeNight,
                    checked = settings["amoled"]?.toBoolean() ?: false,
                    onChange = { viewModel.setSetting("amoled", it.toString()) },
                )
                SettingsDivider()
                SettingsDropdownRow(
                    label = "Progress bar style",
                    value = settings["progressStyle"] ?: "pill",
                    options = listOf(
                        "pill" to "Pill",
                        "classic" to "Classic",
                        "thin" to "Thin",
                        "segmented" to "Segmented",
                    ),
                    leadingIcon = Icons.Default.Tune,
                    onChange = { viewModel.setSetting("progressStyle", it) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    label = "Reduced motion",
                    description = "Minimize player animations",
                    leadingIcon = Icons.Default.Refresh,
                    checked = settings["reducedMotion"]?.toBoolean() ?: false,
                    onChange = { viewModel.setSetting("reducedMotion", it.toString()) },
                )
            }
        }

        item {
            SettingsSection("Lyrics") {
                SettingsDropdownRow(
                    label = "Alignment",
                    value = settings["lyricsAlignment"] ?: "center",
                    options = listOf(
                        "center" to "Center",
                        "start" to "Start",
                        "end" to "End",
                    ),
                    leadingIcon = Icons.Default.Tune,
                    onChange = { viewModel.setSetting("lyricsAlignment", it) },
                )
                SettingsDivider()
                SettingsDropdownRow(
                    label = "Text size",
                    value = settings["lyricsSize"] ?: "normal",
                    options = listOf(
                        "small" to "Small",
                        "normal" to "Normal",
                        "large" to "Large",
                        "xlarge" to "Extra large",
                    ),
                    leadingIcon = Icons.Default.Update,
                    onChange = { viewModel.setSetting("lyricsSize", it) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    label = "Auto-scroll",
                    description = "Follow the active lyric line",
                    leadingIcon = Icons.Default.Refresh,
                    checked = settings["lyricsAutoScroll"]?.toBoolean() != false,
                    onChange = { viewModel.setSetting("lyricsAutoScroll", it.toString()) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    label = "Tap line to seek",
                    description = "Jump to a line when tapped",
                    leadingIcon = Icons.Default.Update,
                    checked = settings["lyricsSeek"]?.toBoolean() != false,
                    onChange = { viewModel.setSetting("lyricsSeek", it.toString()) },
                )
            }
        }

        item {
            SettingsSection("Audio & playback") {
                SettingsSwitchRow(
                    label = "Gapless playback",
                    description = "Continue without gaps between tracks",
                    leadingIcon = Icons.Default.Refresh,
                    checked = settings["gapless"]?.toBoolean() ?: true,
                    onChange = { viewModel.setSetting("gapless", it.toString()) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    label = "Audio focus handling",
                    description = "Pause or duck when other apps play audio",
                    leadingIcon = Icons.Default.Tune,
                    checked = settings["audioFocus"]?.toBoolean() ?: true,
                    onChange = { viewModel.setSetting("audioFocus", it.toString()) },
                )
            }
        }

        item {
            SettingsSection("Discord Rich Presence") {
                if (!DiscordRpcManager.RPC_APPROVED) {
                    SettingsRow(
                        title = "Connection pending",
                        description = "Discord RPC approval pending. Connect and customization options will appear once approved.",
                        leadingIcon = Icons.Default.Info,
                    )
                } else {
                    val rpcStatus by DiscordRpcManager.status.collectAsState()
                    val state by PlaybackController.state.collectAsState()
                    val track by PlaybackController.currentTrack.collectAsState()
                    SettingsSwitchRow(
                        label = "Enable Rich Presence",
                        description = "Show what you are listening to on Discord",
                        leadingIcon = Icons.Default.AutoAwesome,
                        checked = settings["discordRpc"]?.toBoolean() ?: false,
                        onChange = {
                            updateDiscordSetting("discordRpc", it.toString())
                            DiscordRpcManager.setEnabled(it)
                        },
                    )
                    SettingsDivider()
                    SettingsDropdownRow(
                        label = "Presence status",
                        value = settings["discordPresenceStatus"] ?: "auto",
                        options = listOf(
                            "auto" to "Auto",
                            "online" to "Online",
                            "idle" to "Idle",
                            "dnd" to "Do Not Disturb",
                            "invisible" to "Invisible",
                        ),
                        leadingIcon = Icons.Default.Info,
                        onChange = { updateDiscordSetting("discordPresenceStatus", it) },
                    )
                    SettingsDivider()
                    SettingsDropdownRow(
                        label = "Activity type",
                        value = settings["discordActivityType"] ?: "2",
                        options = listOf(
                            "0" to "Playing",
                            "1" to "Streaming",
                            "2" to "Listening",
                            "3" to "Watching",
                        ),
                        leadingIcon = Icons.Default.Tune,
                        onChange = { updateDiscordSetting("discordActivityType", it) },
                    )
                    SettingsDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        SettingsTextField(
                            label = "Activity name",
                            value = settings["discordName"] ?: DiscordRpcManager.DEFAULT_NAME,
                            onChange = { updateDiscordSetting("discordName", it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        SettingsTextField(
                            label = "Details",
                            value = settings["discordDetails"] ?: DiscordRpcManager.DEFAULT_DETAILS,
                            onChange = { updateDiscordSetting("discordDetails", it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        SettingsTextField(
                            label = "State",
                            value = settings["discordState"] ?: DiscordRpcManager.DEFAULT_STATE,
                            onChange = { updateDiscordSetting("discordState", it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Formats: <hime.player.song.name> <hime.player.song.artist> <hime.player.song.album> <hime.player.song.info> <hime.player.status> <hime.player.song.duration> <hime.player.song.remaining>",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingsDivider()
                    val connecting = rpcStatus == "Waiting for Discord" || rpcStatus.startsWith("Connecting")
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        FilledTonalButton(
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
                    }
                    SettingsDivider()
                    SettingsRow(
                        title = "Status",
                        value = rpcStatus,
                        leadingIcon = Icons.Default.Info,
                    )
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        DiscordPresencePreview(state, track, settings)
                    }
                }
            }
        }

        item {
            notice?.let { SettingsNotice(it) }
        }
        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    description: String? = null,
    leadingIcon: ImageVector? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(clickable)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            trailing != null -> {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
            onClick != null -> {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String? = null,
    leadingIcon: ImageVector? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingsDropdownRow(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    leadingIcon: ImageVector? = null,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == value }?.second ?: value
    Box(modifier = Modifier.fillMaxWidth()) {
        SettingsRow(
            title = label,
            value = current,
            leadingIcon = leadingIcon,
            onClick = { expanded = true },
        )
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
private fun SettingsActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun SettingsNotice(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsTextField(label: String, value: String, onChange: (String) -> Unit) {
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
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF5865F2))
                    .padding(14.dp),
                tint = Color.White,
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
