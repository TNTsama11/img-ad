package com.imgad.data.local

import android.content.Context
import com.imgad.domain.port.DefaultProviderStore

class PreferenceDefaultProviderStore(context: Context) : DefaultProviderStore {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun get(): String? = preferences.getString(KEY, null)

    override fun set(providerId: String?) {
        preferences.edit().putString(KEY, providerId).apply()
    }

    private companion object {
        const val NAME = "imgad_settings"
        const val KEY = "default_provider_id"
    }
}
