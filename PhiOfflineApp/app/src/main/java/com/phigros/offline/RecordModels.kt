package com.phigros.offline

/** One chart record inside the game save. */
data class LevelRecord(
    val score: Long,   // uint32
    val acc: Float,
    val fc: Boolean
)

/** Per-chart note counts, mirrored from phi-plugin notesInfo t=[tap,drag,hold,flick]. */
data class NoteStat(val combo: Int, val drag: Int, val flick: Int)

/** A computed chart result (mirrors model.Record in phi-rank-query). */
data class SongResult(
    val id: String,
    val song: String,
    val level: String,       // EZ / HD / IN / AT / Legacy
    val difficulty: Double,  // chart constant
    var rks: Double,
    val score: Long,
    var acc: Double,
    val fullCombo: Boolean,
    val note: NoteStat? = null,
    var overLevelLine: Double? = null // over-level threshold %, set for the over-level list
)

/** Full analysis result, mirrors PlayerResult. */
data class AnalysisResult(
    val records: List<SongResult>, // all records, sorted desc by rks
    val rks: Double,               // B30
    val phi: List<SongResult>,    // top 3 100% accuracy charts
    val oneGood: List<SongResult> = emptyList(),   // X mode: exactly 1 Good
    val overLevel: List<SongResult> = emptyList() // 越级: acc below the over-level line
)
