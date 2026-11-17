package com.phigros.offline

/**
 * Custom block cipher used by Phigros's local save (playerprefs.xml).
 *
 * Ported 1:1 from PhigrosLibraryCSharp/LocalSave/LocalSave.cs
 * (https://github.com/yt6983138/PhigrosLibraryCSharp). It is a bespoke
 * AES-256-CBC-style cipher with hard-coded lookup tables (custom S-Box,
 * an embedded round-key array, a GF(2^8) multiply table and a custom
 * ShiftRows mapping), NOT the standard Rijndael used by javax.crypto.
 *
 * Input: the raw ciphertext bytes (URL-decoded base64 from the XML value).
 * Returns the decrypted UTF-8 bytes, or null if the input is malformed
 * (not a multiple of 16, or invalid PKCS#7-style padding).
 */
object LocalCrypto {

    private val S_BOX = intArrayOf(
        82, 9, 106, 213, 48, 54, 165, 56, 191, 64, 163, 158, 129, 243, 215, 251,
        124, 227, 57, 130, 155, 47, 255, 135, 52, 142, 67, 68, 196, 222, 233, 203,
        84, 123, 148, 50, 166, 194, 35, 61, 238, 76, 149, 11, 66, 250, 195, 78, 8,
        46, 161, 102, 40, 217, 36, 178, 118, 91, 162, 73, 109, 139, 209, 37, 114,
        248, 246, 100, 134, 104, 152, 22, 212, 164, 92, 204, 93, 101, 182, 146,
        108, 112, 72, 80, 253, 237, 185, 218, 94, 21, 70, 87, 167, 141, 157, 132,
        144, 216, 171, 0, 140, 188, 211, 10, 247, 228, 88, 5, 184, 179, 69, 6,
        208, 44, 30, 143, 202, 63, 15, 2, 193, 175, 189, 3, 1, 19, 138, 107, 58,
        145, 17, 65, 79, 103, 220, 234, 151, 242, 207, 206, 240, 180, 230, 115,
        150, 172, 116, 34, 231, 173, 53, 133, 226, 249, 55, 232, 28, 117, 223,
        110, 71, 241, 26, 113, 29, 41, 197, 137, 111, 183, 98, 14, 170, 24, 190,
        27, 252, 86, 62, 75, 198, 210, 121, 32, 154, 219, 192, 254, 120, 205, 90,
        244, 31, 221, 168, 51, 136, 7, 199, 49, 177, 18, 16, 89, 39, 128, 236, 95,
        96, 81, 127, 169, 25, 181, 74, 13, 45, 229, 122, 159, 147, 201, 156, 239,
        160, 224, 59, 77, 174, 42, 245, 176, 200, 235, 187, 60, 131, 83, 153, 97,
        23, 43, 4, 126, 186, 119, 214, 38, 225, 105, 20, 99, 85, 33, 12, 125
    )

    private val INV_SBOX = intArrayOf(
        98, 127, 241, 148, 33, 133, 224, 17, 200, 21, 232, 30, 99, 155, 154, 0, 0,
        28, 118, 107, 130, 108, 41, 189, 150, 87, 133, 137, 241, 154, 111, 214,
        219, 215, 7, 53, 250, 82, 231, 36, 50, 71, 15, 58, 81, 220, 149, 58, 209,
        154, 92, 235, 83, 246, 117, 86, 197, 161, 240, 223, 52, 59, 159, 9, 59,
        12, 6, 45, 193, 94, 225, 9, 243, 25, 238, 51, 162, 197, 123, 9, 235, 60,
        125, 234, 184, 202, 8, 188, 125, 107, 248, 99, 73, 80, 103, 106, 108, 137,
        4, 22, 173, 215, 229, 31, 94, 206, 11, 44, 252, 11, 112, 37, 91, 23, 44,
        213, 227, 221, 36, 105, 158, 182, 220, 10, 215, 230, 187, 96, 234, 99,
        212, 24, 71, 180, 49, 7, 25, 122, 58, 43, 229, 113, 74, 14, 130, 180, 250,
        126, 97, 105, 222, 23, 255, 223, 2, 29, 40, 57, 185, 125, 232, 53, 43, 44,
        175, 129, 26, 43, 182, 251, 32, 0, 83, 138, 106, 14, 111, 202, 248, 213,
        14, 163, 38, 194, 241, 124, 36, 223, 217, 69, 157, 162, 166, 107, 17, 25,
        9, 234, 11, 50, 191, 17, 43, 50, 236, 155, 65, 60, 161, 222, 123, 62, 175,
        125, 93, 252, 94, 1, 121, 35, 135, 68, 228, 129, 253, 2, 29, 14, 244, 232,
        22, 60, 75, 249, 61, 14, 167, 98, 124, 50
    )

    private val INITIAL_VECTOR = intArrayOf(
        190, 86, 22, 127, 131, 218, 59, 239, 239, 248, 24, 97, 165, 197, 243, 205
    )

    private val RCON = intArrayOf(
        0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40,
        42, 44, 46, 48, 50, 52, 54, 56, 58, 60, 62, 64, 66, 68, 70, 72, 74, 76, 78,
        80, 82, 84, 86, 88, 90, 92, 94, 96, 98, 100, 102, 104, 106, 108, 110, 112,
        114, 116, 118, 120, 122, 124, 126, 128, 130, 132, 134, 136, 138, 140, 142,
        144, 146, 148, 150, 152, 154, 156, 158, 160, 162, 164, 166, 168, 170, 172,
        174, 176, 178, 180, 182, 184, 186, 188, 190, 192, 194, 196, 198, 200, 202,
        204, 206, 208, 210, 212, 214, 216, 218, 220, 222, 224, 226, 228, 230, 232,
        234, 236, 238, 240, 242, 244, 246, 248, 250, 252, 254, 27, 25, 31, 29, 19, 17,
        23, 21, 11, 9, 15, 13, 3, 1, 7, 5, 59, 57, 63, 61, 51, 49, 55, 53, 43, 41, 47,
        45, 35, 33, 39, 37, 91, 89, 95, 93, 83, 81, 87, 85, 75, 73, 79, 77, 67, 65,
        71, 69, 123, 121, 127, 125, 115, 113, 119, 117, 107, 105, 111, 109, 99, 97,
        103, 101, 155, 153, 159, 157, 147, 145, 151, 149, 139, 137, 143, 141, 131,
        129, 135, 133, 187, 185, 191, 189, 179, 177, 183, 181, 171, 169, 175, 173,
        163, 161, 167, 165, 219, 217, 223, 221, 211, 209, 215, 213, 203, 201, 207,
        205, 195, 193, 199, 197, 251, 249, 255, 253, 243, 241, 247, 245, 235, 233,
        239, 237, 227, 225, 231, 229
    )

    private val TEMP = intArrayOf(0, 13, 10, 7, 4, 1, 14, 11, 8, 5, 2, 15, 12, 9, 6, 3)

    /** Decrypt a 16-byte-aligned ciphertext block. Returns null on malformed input. */
    fun decrypt(data: ByteArray): ByteArray? {
        if (data.isEmpty() || data.size % 16 != 0) return null
        val out = data.copyOf()
        val state = INITIAL_VECTOR.copyOf()
        var e = 0
        while (e < data.size) {
            val block = data.copyOfRange(e, e + 16)
            for (n in 0 until 16) block[n] = ((block[n].toInt() and 0xFF) xor INV_SBOX[224 + n]).toByte()
            shiftRows(block)
            subBytes(block)
            var i = 208
            while (i >= 16) {
                for (n in 0 until 16) block[n] = ((block[n].toInt() and 0xFF) xor INV_SBOX[i + n]).toByte()
                mixColumns(block)
                shiftRows(block)
                subBytes(block)
                i -= 16
            }
            for (n in 0 until 16) block[n] = ((block[n].toInt() and 0xFF) xor INV_SBOX[n]).toByte()
            for (j in 0 until 16) block[j] = ((block[j].toInt() and 0xFF) xor state[j]).toByte()
            for (t in 0 until 16) state[t] = data[e + t].toInt() and 0xFF
            for (n in 0 until 16) out[e + n] = block[n]
            e += 16
        }
        val pad = out[out.size - 1].toInt() and 0xFF
        if (pad == 0 || pad > 16) return null
        return out.copyOfRange(0, out.size - pad)
    }

    private fun subBytes(state: ByteArray) {
        for (t in 0 until 16) state[t] = S_BOX[state[t].toInt() and 0xFF].toByte()
    }

    private fun shiftRows(state: ByteArray) {
        val tmp = state.copyOf()
        for (i in 0 until 16) state[i] = tmp[TEMP[i]]
    }

    private fun mixColumns(state: ByteArray) {
        var n = 0
        while (n < 16) {
            val i0 = state[n].toInt() and 0xFF
            val i1 = state[n + 1].toInt() and 0xFF
            val i2 = state[n + 2].toInt() and 0xFF
            val i3 = state[n + 3].toInt() and 0xFF
            val sCol = i0 xor i1 xor i2 xor i3
            val rcCol = RCON[sCol]
            val dI0I2 = RCON[RCON[rcCol xor i0 xor i2]] xor sCol
            val dI1I3 = RCON[RCON[rcCol xor i1 xor i3]] xor sCol
            state[n] = ((state[n].toInt() and 0xFF) xor (dI0I2 xor RCON[i0 xor i1])).toByte()
            state[n + 1] = ((state[n + 1].toInt() and 0xFF) xor (dI1I3 xor RCON[i1 xor i2])).toByte()
            state[n + 2] = ((state[n + 2].toInt() and 0xFF) xor (dI0I2 xor RCON[i2 xor i3])).toByte()
            state[n + 3] = ((state[n + 3].toInt() and 0xFF) xor (dI1I3 xor RCON[i3 xor i0])).toByte()
            n += 4
        }
    }
}
