package com.imgad.data.local

import com.imgad.domain.port.ArchiveData
import com.imgad.domain.port.ArchiveImporter
import com.imgad.domain.port.ArchiveManifest
import com.imgad.domain.port.ImportedArchive
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.ZipInputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ImportArchive : ArchiveImporter {
    override suspend fun import(input: InputStream, password: CharArray?): ImportedArchive {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0L
        var count = 0
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                require(count <= ExportArchive.MAX_ENTRIES) { "Too many archive entries" }
                ExportArchive.validateEntry(entry.name)
                val bytes = readLimited(zip, ExportArchive.MAX_ENTRY_BYTES, total)
                total += bytes.size
                require(entry.name !in entries) { "Duplicate archive entry" }
                entries[entry.name] = bytes
            }
        }
        val manifest = Json.decodeFromString<ArchiveManifest>(String(requireNotNull(entries["manifest.json"]), StandardCharsets.UTF_8))
        require(manifest.formatVersion == 1) { "Unsupported archive format" }
        val data = Json.decodeFromString<ArchiveData>(String(requireNotNull(entries["data.json"]), StandardCharsets.UTF_8))
        val decrypted = if (manifest.encryptedSecrets) {
            require(password != null) { "Password is required" }
            decryptSecrets(requireNotNull(data.encryptedSecrets), password)
        } else null
        return ImportedArchive(manifest, data.copy(encryptedSecrets = decrypted), entries.filterKeys { it.startsWith("assets/") })
    }

    private fun readLimited(input: InputStream, max: Long, total: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(ExportArchive.BUFFER_SIZE)
        var size = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            size += count
            require(size <= max && total + size <= ExportArchive.MAX_TOTAL_BYTES) { "Archive is too large" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun decryptSecrets(encoded: String, password: CharArray): String {
        require(encoded.length <= MAX_SECRET_PAYLOAD_CHARS) { "Encrypted secret payload is too large" }
        val parts = encoded.split(ExportArchive.DELIMITER, limit = 4)
        require(parts.size == 3) { "Invalid encrypted secret payload" }
        val values = runCatching { parts.map { part -> Base64.getDecoder().decode(part) } }
            .getOrElse { throw IllegalArgumentException("Invalid encrypted secret payload", it) }
        require(values[0].size == ExportArchive.SALT_BYTES && values[1].size == ExportArchive.IV_BYTES) {
            "Invalid encrypted secret payload"
        }
        return try {
            val cipher = Cipher.getInstance(ExportArchive.TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, ExportArchive.deriveKey(password, values[0]), GCMParameterSpec(ExportArchive.TAG_BITS, values[1]))
            }
            String(cipher.doFinal(values[2]), StandardCharsets.UTF_8)
        } catch (error: AEADBadTagException) {
            throw IllegalArgumentException("密码错误或归档已损坏", error)
        }
    }

    private companion object {
        const val MAX_SECRET_PAYLOAD_CHARS = 1024 * 1024
    }
}
