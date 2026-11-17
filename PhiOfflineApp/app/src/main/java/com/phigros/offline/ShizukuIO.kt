package com.phigros.offline

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Reads the Phigros save file with elevated privileges.
 *
 * Preference order:
 *  1. Shizuku (adb-shell identity) when its binder is online and granted.
 *  2. Root (`su`, uid 0) as fallback when Shizuku is unavailable or denied.
 *
 * Root commands are always run through `su -c`, i.e. as uid 0.
 */
object ShizukuIO {

    /** Shizuku binder online and this app already granted permission. */
    fun shizukuReady(): Boolean =
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    @Volatile private var rootProbed = false
    @Volatile private var rootOk = false

    /** Probe whether `su` exists and returns uid 0 (result cached after first probe). */
    fun rootReady(): Boolean {
        if (rootProbed) return rootOk
        rootOk = try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id -u"))
            val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
            p.waitFor()
            out == "0"
        } catch (e: Throwable) {
            false
        }
        rootProbed = true
        return rootOk
    }

    /** Channel that will actually be used: shizuku, root, or none. */
    fun channel(): String = when {
        shizukuReady() -> "shizuku"
        rootReady() -> "root"
        else -> "none"
    }

    /** Read a whole text file via the preferred elevated channel. */
    fun readFile(path: String): String {
        if (shizukuReady()) {
            try {
                return readViaShizuku(path)
            } catch (_: Throwable) {
                // fall through to root
            }
        }
        if (rootReady()) {
            return readViaRoot(path)
        }
        throw RuntimeException("无可用提权通道：Shizuku 未授权，且未检测到 Root")
    }

    /** Get last-modified epoch seconds via the preferred elevated channel. */
    fun statMtime(path: String): Long {
        if (shizukuReady()) {
            try {
                return statViaShizuku(path)
            } catch (_: Throwable) {
                // fall through to root
            }
        }
        return try {
            statViaRoot(path)
        } catch (_: Throwable) {
            0L
        }
    }

    private fun readViaShizuku(path: String): String {
        val proc = Shizuku.newProcess(arrayOf("cat", path), null, null)
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        val err = proc.errorStream.bufferedReader().use { it.readText() }
        if (out.isEmpty() && err.isNotEmpty()) throw RuntimeException(err.trim())
        return out
    }

    private fun statViaShizuku(path: String): Long {
        val proc = Shizuku.newProcess(arrayOf("stat", "-c", "%Y", path), null, null)
        return proc.inputStream.bufferedReader().use { it.readText() }.trim().toLong()
    }

    private fun rootCmd(cmd: String): String {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val out = p.inputStream.bufferedReader().use { it.readText() }
        val err = p.errorStream.bufferedReader().use { it.readText() }
        val code = p.waitFor()
        if (code != 0 && out.isEmpty()) {
            throw RuntimeException(err.trim().ifEmpty { "su 命令退出码 $code" })
        }
        return out
    }

    private fun readViaRoot(path: String): String = rootCmd("cat ${shellQuote(path)}")

    private fun statViaRoot(path: String): Long =
        rootCmd("stat -c %Y ${shellQuote(path)}").trim().toLong()

    private fun shellQuote(s: String): String =
        "'" + s.replace("'", "'\"'\"'") + "'"
}
