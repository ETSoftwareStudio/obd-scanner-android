package com.eltonvs.obdapp.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dataStore = context.dataStore

        companion object {
            private val POLLING_INTERVAL_KEY = longPreferencesKey("polling_interval")
            private val THEME_KEY = stringPreferencesKey("theme")
            private val LAST_DEVICE_ADDRESS_KEY = stringPreferencesKey("last_device_address")
            private val LAST_DEVICE_NAME_KEY = stringPreferencesKey("last_device_name")
            private val WAS_CONNECTED_KEY = booleanPreferencesKey("was_connected")
            private val AUTO_CONNECT_KEY = booleanPreferencesKey("auto_connect")
            private val TELEMETRY_ENABLED_KEY = booleanPreferencesKey("telemetry_enabled")

            const val DEFAULT_POLLING_INTERVAL = 1000L
            const val DEFAULT_THEME = "system"
            const val DEFAULT_TELEMETRY_ENABLED = true
        }

        val pollingInterval: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[POLLING_INTERVAL_KEY] ?: DEFAULT_POLLING_INTERVAL
            }

        val theme: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[THEME_KEY] ?: DEFAULT_THEME
            }

        val lastDeviceAddress: Flow<String?> =
            dataStore.data.map { preferences ->
                preferences[LAST_DEVICE_ADDRESS_KEY]
            }

        val lastDeviceName: Flow<String?> =
            dataStore.data.map { preferences ->
                preferences[LAST_DEVICE_NAME_KEY]
            }

        val wasConnected: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[WAS_CONNECTED_KEY] ?: false
            }

        val autoConnect: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[AUTO_CONNECT_KEY] ?: true
            }

        val telemetryEnabled: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[TELEMETRY_ENABLED_KEY] ?: DEFAULT_TELEMETRY_ENABLED
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

        suspend fun setLastDevice(
            address: String,
            name: String,
        ) {
            dataStore.edit { preferences ->
                preferences[LAST_DEVICE_ADDRESS_KEY] = address
                preferences[LAST_DEVICE_NAME_KEY] = name
            }
        }

        suspend fun setWasConnected(connected: Boolean) {
            dataStore.edit { preferences ->
                preferences[WAS_CONNECTED_KEY] = connected
            }
        }

        suspend fun setAutoConnect(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[AUTO_CONNECT_KEY] = enabled
            }
        }

        suspend fun setTelemetryEnabled(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[TELEMETRY_ENABLED_KEY] = enabled
            }
        }
    }
