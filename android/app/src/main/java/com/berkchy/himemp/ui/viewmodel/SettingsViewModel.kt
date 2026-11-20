package com.berkchy.himemp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berkchy.himemp.core.LibraryStats
import com.berkchy.himemp.core.ScanReport
import com.berkchy.himemp.data.MusicRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _settings = MutableStateFlow<Map<String, String>>(emptyMap())
    val settings: StateFlow<Map<String, String>> = _settings.asStateFlow()

    private val _stats = MutableStateFlow(LibraryStats())
    val stats: StateFlow<LibraryStats> = _stats.asStateFlow()

    private val _notice = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notice: SharedFlow<String> = _notice.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                _folders.value = MusicRepository.allowedFolders()
                _settings.value = MusicRepository.settings()
                _stats.value = MusicRepository.stats()
            }
        }
    }

    fun addFolder(path: String) {
        viewModelScope.launch {
            val next = (_folders.value + path).distinct()
            runCatching { MusicRepository.setAllowedFolders(next) }
            _folders.value = next
            rescan(incremental = true)
        }
    }

    fun removeFolder(path: String) {
        viewModelScope.launch {
            val next = _folders.value - path
            runCatching { MusicRepository.setAllowedFolders(next) }
            _folders.value = next
            rescan(incremental = true)
        }
    }

    fun rescan(incremental: Boolean) {
        viewModelScope.launch {
            runCatching {
                val report: ScanReport = MusicRepository.scan(incremental)
                _notice.tryEmit(
                    "Scan complete: ${report.added} added, ${report.updated} updated, ${report.removed} removed",
                )
                _stats.value = MusicRepository.stats()
            }.onFailure { _notice.tryEmit(it.message ?: "Scan failed") }
        }
    }

    fun setSetting(key: String, value: String) {
        viewModelScope.launch {
            runCatching { MusicRepository.setSetting(key, value) }
            _settings.value = _settings.value + (key to value)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val removed = runCatching { MusicRepository.clearCaches() }.getOrDefault(0)
            com.berkchy.himemp.util.ArtCache.clear()
            _notice.tryEmit("Cleared $removed cached files")
        }
    }
}
