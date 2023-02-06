package com.tashby.callmetwice.data

import android.telephony.PhoneNumberUtils
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class WhitelistRepository (private val dataStore: DataStore<Preferences>) {
    val whitelist: Flow<String> = dataStore.data
        .catch {
            if (it is IOException) {
                Log.e("WhitelistRepo", "Error reading preferences.", it)
                emit(emptyPreferences())
            }
        }
        .map { preferences -> preferences[THE_KEY] ?: "" }

    private companion object {
        val THE_KEY = stringPreferencesKey("whitelist")
    }

    suspend fun addPhoneNumber(phoneNumber: String) {
        val strippedNumber = PhoneNumberUtils.formatNumber(phoneNumber, "US")

        dataStore.edit { preferences ->
            val numbers = preferences[THE_KEY]?.split(",")?.toMutableList()

            if (numbers != null) {
                if (numbers.isEmpty() || strippedNumber !in numbers) {
                    numbers.add(strippedNumber)
                    preferences[THE_KEY] = numbers.joinToString(",")
                }
            } else {
                preferences[THE_KEY] = strippedNumber
            }
        }
    }

    suspend fun removePhoneNumber(phoneNumber: String) {
        val strippedNumber = PhoneNumberUtils.formatNumber(phoneNumber, "US")

        dataStore.edit { preferences ->
            val numbers = preferences[THE_KEY]?.split(",")?.toMutableList()

            if (numbers != null && strippedNumber in numbers) {
                numbers.remove(strippedNumber)
                preferences[THE_KEY] = numbers.joinToString(",")
            }
        }
    }
}