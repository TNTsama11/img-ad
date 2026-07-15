package com.imgad.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {
    private lateinit var context: Context
    private lateinit var store: KeystoreSecretStore
    private val aliases = listOf("provider/unsafe alias", "overwrite", "remove")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(KeystoreSecretStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = KeystoreSecretStore(context)
        aliases.forEach(store::remove)
    }

    @After
    fun tearDown() {
        aliases.forEach(store::remove)
    }

    @Test
    fun putAndGetRoundTripsWithoutPersistingPlaintext() {
        val plaintext = "test-secret-that-must-not-be-stored"

        store.put(aliases[0], plaintext)

        assertEquals(plaintext, store.get(aliases[0]))
        val preferencesFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/${KeystoreSecretStore.PREFERENCES_NAME}.xml",
        )
        assertFalse(preferencesFile.readText().contains(plaintext))
    }

    @Test
    fun putOverwritesExistingSecret() {
        store.put(aliases[1], "old-secret")

        store.put(aliases[1], "new-secret")

        assertEquals("new-secret", store.get(aliases[1]))
    }

    @Test
    fun removeDeletesStoredSecret() {
        store.put(aliases[2], "secret")

        store.remove(aliases[2])

        assertNull(store.get(aliases[2]))
    }
}
