package com.eltonvs.obdapp.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val POLLING_INTERVAL_KEY = longPreferencesKey("polling_interval")
        private val THEME_KEY = stringPreferencesKey("theme")
        private val LAST_DEVICE_ADDRESS_KEY = stringPreferencesKey("last_device_address")
        private val LAST_DEVICE_NAME_KEY = stringPreferencesKey("last_device_name")

        const val DEFAULT_POLLING_INTERVAL = 1000L
        const val DEFAULT_THEME = "dark"
    }

    val pollingInterval: Flow<Long> = dataStore.data.map { preferences ->
        preferences[POLLING_INTERVAL_KEY] ?: DEFAULT_POLLING_INTERVAL
    }

    val theme: Flow<String> = dataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: DEFAULT_THEME
    }

    val lastDeviceAddress: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LAST_DEVICE_ADDRESS_KEY]
    }

    val lastDeviceName: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LAST_DEVICE_NAME_KEY]
    }

    suspend fun setPollingInterval(intervalMs: Long) {
        dataStore.edit { preferences ->
            preferences[POLLING_INTERVAL_KEY] = intervalMs
        }
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }

    suspend fun setLastDevice(address: String, name: String) {
        dataStore.edit { preferences ->
            preferences[LAST_DEVICE_ADDRESS_KEY] = address
            preferences[LAST_DEVICE_NAME_KEY] = name
        }
    }
}
