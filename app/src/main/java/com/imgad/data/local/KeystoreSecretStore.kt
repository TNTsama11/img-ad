package com.imgad.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreSecretStore(context: Context) : SecretStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    override fun put(alias: String, value: String) {
        val mappedAlias = mapAlias(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey(keyAlias(mappedAlias)))
        }
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = listOf(
            PAYLOAD_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        ).joinToString(PAYLOAD_SEPARATOR)

        if (!preferences.edit().putString(preferenceKey(mappedAlias), payload).commit()) {
            throw IOException("Unable to persist encrypted secret")
        }
    }

    @Synchronized
    override fun get(alias: String): String? {
        val mappedAlias = mapAlias(alias)
        val payload = preferences.getString(preferenceKey(mappedAlias), null) ?: return null
        val parts = payload.split(PAYLOAD_SEPARATOR, limit = 3)
        require(parts.size == 3 && parts[0] == PAYLOAD_VERSION) {
            "Unsupported encrypted secret payload"
        }
        val secretKey = loadKey(keyAlias(mappedAlias))
            ?: throw IllegalStateException("Encrypted secret key is unavailable")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(parts[1], Base64.NO_WRAP)),
            )
        }
        val plaintext = cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP))
        return String(plaintext, StandardCharsets.UTF_8)
    }

    @Synchronized
    override fun remove(alias: String) {
        val mappedAlias = mapAlias(alias)
        if (!preferences.edit().remove(preferenceKey(mappedAlias)).commit()) {
            throw IOException("Unable to remove encrypted secret")
        }
        val keyStore = loadKeyStore()
        val keyAlias = keyAlias(mappedAlias)
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        loadKey(alias)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_SIZE_BITS)
                    .build(),
            )
            generateKey()
        }
    }

    private fun loadKey(alias: String): SecretKey? = loadKeyStore().getKey(alias, null) as? SecretKey

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun mapAlias(alias: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(alias.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    private fun preferenceKey(mappedAlias: String) = "secret.$mappedAlias"

    private fun keyAlias(mappedAlias: String) = "imgad.secret.v1.$mappedAlias"

    companion object {
        const val PREFERENCES_NAME = "imgad_encrypted_secrets"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PAYLOAD_VERSION = "v1"
        private const val PAYLOAD_SEPARATOR = ":"
    }
}
