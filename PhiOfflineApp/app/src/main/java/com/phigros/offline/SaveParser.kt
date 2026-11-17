package com.phigros.offline

import android.util.Base64

/** One per-chart record parsed from the local save (one `<string>` entry). */
data class LocalRecord(
    val songId: String,   // "Song.Composer", matches difficulty.tsv id
    val level: String,    // EZ / HD / IN / AT / Legacy
    val score: Long,
    val acc: Float,
    val isFc: Boolean
)

/** Parsed result of the whole local save. */
data class ParsedSave(
    val records: List<LocalRecord>,
    val playerId: String?
)

/**
 * Parses the Phigros local save (playerprefs.xml).
 *
 * The file lives at:
 *   /data/data/com.PigeonGames.Phigros/shared_prefs/com.PigeonGames.Phigros.v2.playerprefs.xml
 * (or under /storage/emulated/0/Android/data/... on some devices).
 *
 * Every `<string name="K">V</string>` entry has its key AND value individually
 * encrypted with Phigros' custom block cipher (see LocalCrypto). After decryption:
 *   - key `playerID` -> the player identifier (e.g. GUEST / a TapTap id)
 *   - key `Song.Composer.Version.Record.Difficulty` -> value is a JSON score
 *     `{"s":<score>,"a":<accuracy>,"c":<0 not-fc / 1 fc>}`.
 */
object SaveParser {

    private val RECORD_SPLIT = ".Record."

    /** Difficulty token -> canonical level used by the analyzer/adapter. */
    val LEVEL_MAP: Map<String, String> = mapOf(
        "EZ" to "EZ", "HD" to "HD", "IN" to "IN", "AT" to "AT", "Legacy" to "Legacy"
    )

    fun parseSave(xml: String): ParsedSave {
        val records = ArrayList<LocalRecord>()
        var playerId: String? = null
        val re = Regex("""<string\s+name="([^"]+)"[^>]*>([^<]*)</string>""")
        for (m in re.findAll(xml)) {
            val keyBytes = decode(m.groupValues[1]) ?: continue
            val plainKey = try {
                LocalCrypto.decrypt(keyBytes)?.toString(Charsets.UTF_8)
            } catch (e: Exception) {
                null
            } ?: continue

            if (plainKey == "playerID") {
                val vb = decode(m.groupValues[2]) ?: continue
                playerId = try {
                    LocalCrypto.decrypt(vb)?.toString(Charsets.UTF_8)
                } catch (e: Exception) {
                    null
                }
                continue
            }
            if (!plainKey.contains(RECORD_SPLIT)) continue

            val idx = plainKey.lastIndexOf(RECORD_SPLIT)
            val head = plainKey.substring(0, idx)            // Song.Composer.Version
            val diffToken = plainKey.substring(idx + RECORD_SPLIT.length)
            val level = LEVEL_MAP[diffToken.uppercase()] ?: continue
            val songId = head.substringBeforeLast('.')       // strip chart version

            val valBytes = decode(m.groupValues[2]) ?: continue
            val plainVal = try {
                LocalCrypto.decrypt(valBytes)?.toString(Charsets.UTF_8)
            } catch (e: Exception) {
                null
            } ?: continue

            val score = Regex("""\"s"\s*:\s*(-?\d+)""").find(plainVal)
                ?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val acc = Regex("""\"a"\s*:\s*(-?[\d.]+)""").find(plainVal)
                ?.groupValues?.get(1)?.toFloatOrNull() ?: continue
            val c = Regex("""\"c"\s*:\s*(\d+)""").find(plainVal)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0

            records.add(LocalRecord(songId, level, score, acc, c == 1))
        }
        return ParsedSave(records, playerId)
    }

    /** URL-decode (%xx) then base64-decode a stored key/value. Null on failure. */
    private fun decode(s: String): ByteArray? {
        val unquoted = urlDecodePreservePlus(s)
        return try {
            Base64.decode(unquoted, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    /** Decode %xx escapes only (keeps '+' intact, unlike URLDecoder). */
    private fun urlDecodePreservePlus(s: String): String {
        if (!s.contains('%')) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) {
                    out.append(v.toChar())
                    i += 3
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
