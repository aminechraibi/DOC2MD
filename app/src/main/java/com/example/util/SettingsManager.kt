package com.example.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "doc2md_settings")

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

class SettingsManager(private val context: Context) {

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_AUTO_CLEAR_TEMP = booleanPreferencesKey("auto_clear_temp")
    }

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.name
        runCatching { AppThemeMode.valueOf(modeStr) }.getOrDefault(AppThemeMode.SYSTEM)
    }

    val autoClearTempFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_CLEAR_TEMP] ?: true
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setAutoClearTemp(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_CLEAR_TEMP] = enabled
        }
    }
}
