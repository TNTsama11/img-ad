package com.imgad.data.local

import com.imgad.domain.port.ArchiveData
import com.imgad.domain.port.ArchiveExporter
import com.imgad.domain.port.ArchiveManifest
import com.imgad.domain.port.ArchiveSnapshot
import java.io.ByteArrayInputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExportArchive(private val clock: () -> Long = System::currentTimeMillis) : ArchiveExporter {
    override suspend fun export(snapshot: ArchiveSnapshot, output: OutputStream, password: CharArray?) {
        require(snapshot.secrets.isEmpty() || password != null) { "密码是包含密钥所必需的" }
        val encrypted = snapshot.secrets.takeIf { it.isNotEmpty() }?.let { encryptSecrets(it, password!!) }
        val normalizedAssets = snapshot.assets.map { asset ->
            asset.copy(
                entryName = asset.entryName ?: snapshot.assetBytes[asset.id]?.let { "assets/${UUID.randomUUID()}.bin" },
                thumbnailEntryName = asset.thumbnailEntryName
                    ?: snapshot.thumbnailBytes[asset.id]?.let { "assets/${UUID.randomUUID()}-thumb.bin" },
            )
        }
        val data = ArchiveData(snapshot.providers, snapshot.models, snapshot.sessions, snapshot.messages, normalizedAssets, encrypted)
        val budget = ArchiveBudget()
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            write(zip, budget, "manifest.json", Json.encodeToString(ArchiveManifest(exportedAt = clock(), encryptedSecrets = encrypted != null)).toByteArray(StandardCharsets.UTF_8))
            write(zip, budget, "data.json", Json.encodeToString(data).toByteArray(StandardCharsets.UTF_8))
            normalizedAssets.forEach { asset ->
                snapshot.assetBytes[asset.id]?.let { bytes ->
                    write(zip, budget, requireNotNull(asset.entryName), bytes)
                }
                snapshot.thumbnailBytes[asset.id]?.let { bytes ->
                    write(zip, budget, requireNotNull(asset.thumbnailEntryName), bytes)
                }
            }
        }
    }

    private fun write(zip: ZipOutputStream, budget: ArchiveBudget, name: String, bytes: ByteArray) {
        budget.accept(bytes.size.toLong())
        validateEntry(name)
        require(bytes.size <= MAX_ENTRY_BYTES) { "Archive entry is too large" }
        zip.putNextEntry(ZipEntry(name))
        ByteArrayInputStream(bytes).use { it.copyTo(zip, BUFFER_SIZE) }
        zip.closeEntry()
    }

    private class ArchiveBudget {
        private var count = 0
        private var total = 0L

        fun accept(size: Long) {
            require(++count <= MAX_ENTRIES) { "Too many archive entries" }
            total += size
            require(total <= MAX_TOTAL_BYTES) { "Archive is too large" }
        }
    }

    internal fun encryptSecrets(secrets: Map<String, String>, password: CharArray): String {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv)) }
        val ciphertext = cipher.doFinal(Json.encodeToString(secrets).toByteArray(StandardCharsets.UTF_8))
        return listOf(salt, iv, ciphertext).joinToString(DELIMITER) { Base64.getEncoder().encodeToString(it) }
    }

    internal companion object {
        const val MAX_ENTRIES = 256
        const val MAX_ENTRY_BYTES = 25L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
        const val ITERATIONS = 210_000
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DELIMITER = ":"
        const val BUFFER_SIZE = 8192

        fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
            val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
            return try {
                val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                try {
                    SecretKeySpec(encoded, "AES")
                } finally {
                    encoded.fill(0)
                }
            } finally {
                spec.clearPassword()
            }
        }

        fun validateEntry(name: String) {
            require(name.isNotBlank() && !name.startsWith('/') && !name.contains('\\')) { "Invalid archive entry" }
            require(name.split('/').none { it == ".." || it.isBlank() }) { "Invalid archive entry" }
        }
    }
}
