package com.phigros.offline

import android.content.Context
import java.io.BufferedReader

/**
 * Bundled offline song database (from phi.iris.al web/public):
 *   difficulty.tsv -> id -> { EZ/HD/IN/AT/Legacy -> chart constant }
 *   info.tsv       -> id -> display song title
 */
object SongDatabase {
    val difficulty: MutableMap<String, MutableMap<String, Double>> = HashMap()
    val info: MutableMap<String, String> = HashMap()
    val notes: MutableMap<String, MutableMap<String, NoteStat>> = HashMap()
    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        try {
            context.assets.open("difficulty.tsv").bufferedReader().use { br ->
                br.readLine() // header
                val names = listOf("EZ", "HD", "IN", "AT", "Legacy")
                br.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    val cols = line.split("\t")
                    val id = cols[0]
                    if (id.isBlank()) return@forEachLine
                    val m = HashMap<String, Double>()
                    for (i in 1 until minOf(cols.size, 6)) {
                        val v = cols[i].trim()
                        if (v.isNotEmpty()) m[names[i - 1]] = v.toDoubleOrNull() ?: continue
                    }
                    difficulty[id] = m
                }
            }
            context.assets.open("info.tsv").bufferedReader().use { br ->
                br.readLine() // header
                br.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    val cols = line.split("\t")
                    if (cols.size >= 2 && cols[0].isNotBlank()) info[cols[0]] = cols[1].trim()
                }
            }
            context.assets.open("notes.tsv").bufferedReader().use { br ->
                br.readLine() // header
                val names = listOf("EZ", "HD", "IN", "AT")
                br.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    val cols = line.split("\t")
                    val id = cols[0]
                    if (id.isBlank()) return@forEachLine
                    val m = HashMap<String, NoteStat>()
                    for (i in 1 until minOf(cols.size, 5)) {
                        val v = cols[i].trim()
                        if (v.isEmpty()) continue
                        val p = v.split(",")
                        if (p.size == 3) {
                            val combo = p[0].toIntOrNull() ?: continue
                            val drag = p[1].toIntOrNull() ?: continue
                            val flick = p[2].toIntOrNull() ?: continue
                            m[names[i - 1]] = NoteStat(combo, drag, flick)
                        }
                    }
                    notes[id] = m
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("加载曲目数据失败：${e.message}", e)
        }
        loaded = true
    }

    /** Display title for a song id, with a sensible fallback. */
    fun titleOf(id: String): String {
        val t = info[id]
        if (t != null && t.isNotEmpty()) return t
        val dot = id.lastIndexOf('.')
        return if (dot > 0) id.substring(0, dot) else id
    }
}
