package com.example.lowlevelide.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Wraps the Preferences DataStore with typed flows + setters.
 * Defaults below are tuned for a comfortable on-screen experience.
 */
class AppSettings(private val context: Context) {

    private val K_THEME = stringPreferencesKey("theme")
    private val K_EDITOR_FONT = intPreferencesKey("editor_font")
    private val K_TERMINAL_FONT = intPreferencesKey("terminal_font")
    private val K_EXTRA_KEYS = booleanPreferencesKey("extra_keys")
    private val K_USE_ROOT = booleanPreferencesKey("use_root")
    private val K_USE_PROOT = booleanPreferencesKey("use_proot")
    private val K_PERSISTENT_TERM = booleanPreferencesKey("persistent_term")
    private val K_ONBOARDED = booleanPreferencesKey("onboarded")
    private val K_LAST_OPEN_FILES = stringPreferencesKey("last_open_files")
    private val K_EDITOR_THEME = stringPreferencesKey("editor_theme")
    private val K_TERMINAL_PALETTE = stringPreferencesKey("terminal_palette")
    private val K_WORD_WRAP = booleanPreferencesKey("word_wrap")

    val themeFlow: Flow<String> = context.dataStore.data.map { it[K_THEME] ?: THEME_SYSTEM }
    val editorFontSizeFlow: Flow<Int> = context.dataStore.data.map { it[K_EDITOR_FONT] ?: 14 }
    val terminalFontSizeFlow: Flow<Int> = context.dataStore.data.map { it[K_TERMINAL_FONT] ?: 14 }
    val extraKeysFlow: Flow<Boolean> = context.dataStore.data.map { it[K_EXTRA_KEYS] ?: true }
    val useRootFlow: Flow<Boolean> = context.dataStore.data.map { it[K_USE_ROOT] ?: true }
    val useProotFlow: Flow<Boolean> = context.dataStore.data.map { it[K_USE_PROOT] ?: true }
    val persistentTerminalFlow: Flow<Boolean> = context.dataStore.data.map { it[K_PERSISTENT_TERM] ?: true }
    val onboardedFlow: Flow<Boolean> = context.dataStore.data.map { it[K_ONBOARDED] ?: false }
    val lastOpenFilesFlow: Flow<String> = context.dataStore.data.map { it[K_LAST_OPEN_FILES] ?: "" }
    val editorThemeFlow: Flow<String> = context.dataStore.data.map { it[K_EDITOR_THEME] ?: DEFAULT_EDITOR_THEME }
    val terminalPaletteFlow: Flow<String> = context.dataStore.data.map { it[K_TERMINAL_PALETTE] ?: DEFAULT_TERMINAL_PALETTE }
    val wordWrapFlow: Flow<Boolean> = context.dataStore.data.map { it[K_WORD_WRAP] ?: true }

    suspend fun setTheme(value: String) = update { it[K_THEME] = value }
    suspend fun setEditorFontSize(value: Int) = update { it[K_EDITOR_FONT] = value }
    suspend fun setTerminalFontSize(value: Int) = update { it[K_TERMINAL_FONT] = value }
    suspend fun setExtraKeys(value: Boolean) = update { it[K_EXTRA_KEYS] = value }
    suspend fun setUseRoot(value: Boolean) = update { it[K_USE_ROOT] = value }
    suspend fun setUseProot(value: Boolean) = update { it[K_USE_PROOT] = value }
    suspend fun setPersistentTerminal(value: Boolean) = update { it[K_PERSISTENT_TERM] = value }
    suspend fun setOnboarded(value: Boolean) = update { it[K_ONBOARDED] = value }
    suspend fun setLastOpenFiles(value: String) = update { it[K_LAST_OPEN_FILES] = value }
    suspend fun setEditorTheme(value: String) = update { it[K_EDITOR_THEME] = value }
    suspend fun setTerminalPalette(value: String) = update { it[K_TERMINAL_PALETTE] = value }
    suspend fun setWordWrap(value: Boolean) = update { it[K_WORD_WRAP] = value }

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs -> block(prefs) }
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val DEFAULT_EDITOR_THEME = "material-darker"
        const val DEFAULT_TERMINAL_PALETTE = "github-dark"

        /** CodeMirror theme keys → display labels. Keep in sync with CodeMirrorInstaller.THEMES. */
        val EDITOR_THEMES = linkedMapOf(
            "material-darker" to "Material Darker",
            "dracula" to "Dracula",
            "monokai" to "Monokai",
            "ayu-dark" to "Ayu Dark",
            "eclipse" to "Eclipse (light)",
            "idea" to "IntelliJ (light)"
        )

        /** Terminal palette keys → display labels. Keep in sync with TerminalPalette. */
        val TERMINAL_PALETTES = linkedMapOf(
            "github-dark" to "GitHub Dark",
            "solarized-dark" to "Solarized Dark",
            "dracula" to "Dracula",
            "gruvbox-dark" to "Gruvbox Dark",
            "nord" to "Nord",
            "light" to "Light"
        )
    }
}
