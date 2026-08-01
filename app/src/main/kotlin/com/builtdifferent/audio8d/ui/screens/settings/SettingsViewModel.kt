package com.builtdifferent.audio8d.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.builtdifferent.audio8d.data.db.OutputFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val darkTheme: Boolean? = null, // null = follow system
    val defaultFormat: OutputFormat = OutputFormat.MP3_320,
    val cacheSizeBytes: Long = 0L
)

private const val PREFS_NAME = "audio8d_settings"
private const val KEY_DARK_THEME = "dark_theme" // "system" | "dark" | "light"
private const val KEY_DEFAULT_FORMAT = "default_format"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            darkTheme = when (prefs.getString(KEY_DARK_THEME, "system")) {
                "dark" -> true; "light" -> false; else -> null
            },
            defaultFormat = OutputFormat.valueOf(
                prefs.getString(KEY_DEFAULT_FORMAT, OutputFormat.MP3_320.name) ?: OutputFormat.MP3_320.name
            ),
            cacheSizeBytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setDarkTheme(value: Boolean?) {
        prefs.edit().putString(KEY_DARK_THEME, if (value == null) "system" else if (value) "dark" else "light").apply()
        _uiState.value = _uiState.value.copy(darkTheme = value)
    }

    fun setDefaultFormat(format: OutputFormat) {
        prefs.edit().putString(KEY_DEFAULT_FORMAT, format.name).apply()
        _uiState.value = _uiState.value.copy(defaultFormat = format)
    }

    fun clearCache() {
        viewModelScope.launch {
            context.cacheDir.walkBottomUp().forEach { if (it.isFile) it.delete() }
            _uiState.value = _uiState.value.copy(cacheSizeBytes = 0L)
        }
    }
}
