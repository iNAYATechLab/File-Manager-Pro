package com.inayatechlab.filemanagerpro.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Plain-text viewing support (pure JVM — unit-testable).
 *
 * Text-like extensions are previewed in-app (TextActivity); encoding
 * detection: UTF BOM first, then strict UTF-8, falling back to the legacy
 * Windows-1252 superset so files from older tools stay readable.
 */
object TextFiles {

    /** Extensions previewed in the built-in viewer. */
    val TEXT_EXTS: Set<String> = setOf(
        "txt", "text", "md", "markdown", "json", "xml", "html", "htm", "css", "js", "mjs",
        "kt", "kts", "java", "py", "c", "cpp", "h", "hpp", "sh", "log", "ini", "cfg",
        "conf", "yaml", "yml", "sql", "bat", "csv", "tsv", "gradle", "properties", "toml",
        "env", "gitignore", "editorconfig", "rs", "go", "rb", "php", "swift", "lua", "r"
    )

    /** Head bytes read from the file — enough for typical sources, bounded. */
    const val MAX_READ_BYTES: Int = 4 shl 20 // 4 MiB

    /** Characters shown before the "truncated" marker. */
    const val MAX_CHARS: Int = 600_000

    /** Result of decoding a head of a text file. */
    data class TextResult(
        val text: String,
        /** Human label of the detected charset (e.g. "UTF-8"). */
        val charsetLabel: String,
        /** True when the file is bigger than the preview cap. */
        val truncated: Boolean
    )

    fun isTextFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in TEXT_EXTS

    /**
     * Decodes up to [MAX_READ_BYTES] bytes. Returns the decoded text (capped
     * at [MAX_CHARS] characters with a trailing ellipsis) plus the detected
     * charset label.
     */
    fun decodeHead(bytes: ByteArray): TextResult {
        val (charset, label) = detect(bytes)
        val full = try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: Exception) {
            // Malformed multi-byte sequences at the very end of a truncated
            // head must not break viewing.
            String(bytes, StandardCharsets.ISO_8859_1)
        }
        // Charset decoders keep the byte-order mark as a leading U+FEFF
        // character; drop it so viewers don't show an invisible glyph.
        val text = if (full.startsWith('\uFEFF')) full.substring(1) else full
        if (text.length <= MAX_CHARS) return TextResult(text, label, false)
        val cut = text.take(MAX_CHARS)
        val trimmed = cut.trimEnd()
        return TextResult(trimmed + "\n…", label, true)
    }

    /** Detects UTF-8/16 BOMs and validates UTF-8; Windows-1252 as fallback. */
    fun detect(bytes: ByteArray): Pair<Charset, String> {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return StandardCharsets.UTF_8 to "UTF-8"
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return StandardCharsets.UTF_16LE to "UTF-16 LE"
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return StandardCharsets.UTF_16BE to "UTF-16 BE"
        }
        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(bytes))
            StandardCharsets.UTF_8 to "UTF-8"
        } catch (e: CharacterCodingException) {
            // Legacy single-byte superset: never fails, keeps CP1252 glyphs.
            Charset.forName("windows-1252") to "Windows-1252"
        }
    }

    /** Reads the head of a file into a byte array (bounded, JVM-friendly). */
    fun readHead(file: java.io.File): ByteArray {
        if (!file.isFile) return ByteArray(0)
        val length = file.length().coerceAtMost(MAX_READ_BYTES.toLong()).toInt()
        val out = ByteArrayOutputStream(length)
        java.io.FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
        return out.toByteArray()
    }
}
