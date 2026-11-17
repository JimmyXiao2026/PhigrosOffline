package com.phigros.offline

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Mirrors service/phigros.go calc() and CalcSongRank:
 *   RKS of a chart:
 *     acc == 100 -> chart constant
 *     acc <  70 -> 0
 *     else     -> constant * ((acc - 55) / 45)^2
 *   B30 = (sum of top 27 RKS + sum of up to 3 phi) / 30
 */
object Analyzer {
    val LEVELS = listOf("EZ", "HD", "IN", "AT", "Legacy")

    fun analyze(records: List<LocalRecord>): AnalysisResult {
        val result = ArrayList<SongResult>()
        for (r in records) {
            if (r.level == "Legacy") continue // skip Legacy like the reference
            val id = lookupId(r.songId) ?: continue
            val diff = SongDatabase.difficulty[id]?.get(r.level) ?: continue
            val note = SongDatabase.notes[id]?.get(r.level)
            val line = note?.let { overLevelLine(it.drag, it.flick, it.combo, false) }
            result.add(
                SongResult(
                    id = id,
                    song = SongDatabase.titleOf(id),
                    level = r.level,
                    difficulty = diff,
                    rks = calcSongRank(r.acc, diff),
                    score = r.score,
                    acc = r.acc.toDouble(),
                    fullCombo = r.isFc,
                    note = note,
                    overLevelLine = line
                )
            )
        }
        result.sortByDescending { it.rks }

        val phi = ArrayList<SongResult>()
        var sum = 0.0
        for (r in result) {
            if (r.acc == 100.0) {
                phi.add(r)
                sum += r.rks
                if (phi.size == 3) break
            }
        }
        for (i in result.indices) {
            if (i < 27) sum += result[i].rks
        }
        val rks = sum / 30.0

        // X mode (1 Good): mirrors fCompute.comJust1Good.
        val oneGood = result.filter { s ->
            val n = s.note
            n != null && n.combo > 0 && comJust1Good(s.score, n.combo)
        }.sortedByDescending { it.rks }

        // 越级: acc below the over-level line.
        val overLevel = result.filter { s ->
            val line = s.overLevelLine
            line != null && s.acc < line
        }.sortedByDescending { it.rks }

        for (r in result) {
            r.rks = floor(r.rks * 100) / 100
            r.acc = floor(r.acc * 100) / 100
        }
        return AnalysisResult(result, rks, phi, oneGood, overLevel)
    }

    /** 1-Good score threshold: |score - tar| <= 2, mirrors fCompute.comJust1Good. */
    fun comJust1Good(score: Long, maxc: Int): Boolean {
        if (maxc <= 0) return false
        val tar = 900000.0 * (1.0 - 0.35 / maxc) + 100000.0
        return kotlin.math.abs(score - tar) <= 2
    }

    /** Over-level line: 98.5 + 1.5*(Dr[+Fk])/combo, mirrors fCompute.getOverLevelLine. */
    fun overLevelLine(drag: Int, flick: Int, combo: Int, withFlick: Boolean): Double {
        if (combo <= 0) return 98.5
        val count = drag + (if (withFlick) flick else 0)
        return 98.5 + 1.5 * count.toDouble() / combo
    }

    fun calcSongRank(acc: Float, rank: Double): Double {
        if (acc == 100f) return rank
        if (acc < 70f) return 0.0
        val x = (acc - 55f) / 45f
        return rank * x * x
    }

    /**
     * Acc required to lift this chart into the current ranking, mirrors fCompute.suggest +
     * Save.getSuggest. Returns null when even a 100% clear cannot reach the threshold
     * （无法推分）.
     *
     * @param recRks current chart RKS (0 for an unplayed difficulty)
     * @param difficulty chart constant
     * @param all all records sorted desc by RKS (for the B30 27th-place cutoff)
     * @param totalRks total B30 RKS
     * @param phiTopRks RKS of the highest 100% chart
     */
    fun suggestAcc(
        recRks: Double,
        difficulty: Double,
        all: List<SongResult>,
        totalRks: Double,
        phiTopRks: Double
    ): Double? {
        if (difficulty <= 0) return null
        // minUpRks: rounding headroom to push total RKS up by 0.01, mirrors Save.minUpRks.
        val minUp = run {
            val m = floor(totalRks * 100) / 100.0 + 0.005 - totalRks
            if (m < 0) m + 0.01 else m
        }
        val b19 = all.getOrNull(26)?.rks ?: 0.0
        val target = max(b19, recRks) + minUp * 30.0
        var ans = 45.0 * sqrt(target / difficulty) + 55.0
        if (ans >= 100.0) {
            // High-constant phi chart: a full clear still counts, mirrors the Save.getSuggest fallback.
            if (difficulty > phiTopRks + minUp * 30.0) ans = 100.0 else return null
        }
        return ans
    }

    /**
     * Resolve a song id to the id used in difficulty.tsv. Local keys already
     * use "Song.Composer", but keep the fallbacks for robustness.
     */
    fun lookupId(key: String): String? {
        if (SongDatabase.difficulty.containsKey(key)) return key
        if (key.length > 2) {
            val k = key.substring(0, key.length - 2)
            if (SongDatabase.difficulty.containsKey(k)) return k
        }
        val dot = key.lastIndexOf('.')
        if (dot > 0) {
            val cand = key.substring(0, dot)
            if (SongDatabase.difficulty.containsKey(cand)) return cand
        }
        return null
    }
}
