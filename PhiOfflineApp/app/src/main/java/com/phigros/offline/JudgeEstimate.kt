package com.phigros.offline

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Offline judgment breakdown estimate.
 *
 * The local save only stores `score / acc / fc` per chart — it does NOT persist
 * Perfect / Good / Bad / Miss counts or the max combo. Those fields are null here
 * and are shown as "本地未记录" in the UI.
 *
 * The Perfect/Good split below is a theoretical estimate under the most
 * optimistic assumption that every non-Perfect note is a Good (no Bad/Miss),
 * mirroring the common community calculator:
 *   acc/100 = (P + 0.65·G) / N ,  P + G = N
 *   => G = N·(1 - acc/100) / 0.35 ,  P = N - G
 */
data class JudgeEstimate(
    val combo: Int,
    val perfect: Int,
    val good: Int,
    val bad: Int? = null,
    val miss: Int? = null,
    val maxCombo: Int? = null,
    /** Good -> Perfect count needed to raise acc by 0.01 percentage point. */
    val goodToPerfect: Int?,
    /** Bad/Miss(0-score) -> Perfect count needed to raise acc by 0.01 percentage point. */
    val missToPerfect: Int?
)

object JudgeEstimator {

    private const val GOOD_WEIGHT = 0.65
    private const val GOOD_LOSS = 1.0 - GOOD_WEIGHT // 0.35

    /** acc is in percent (e.g. 97.27). combo = total note count from notes.tsv. */
    fun estimate(acc: Double, combo: Int): JudgeEstimate? {
        if (combo <= 0) return null
        val w = (acc / 100.0).coerceIn(0.0, 1.0)

        val goodRaw = combo * (1.0 - w) / GOOD_LOSS
        val good = min(combo, max(0, goodRaw.roundToInt()))
        val perfect = combo - good

        val goodToPerfect = if (acc >= 100.0) 0 else ceil(0.01 * combo / 35.0).toInt()
        val missToPerfect = if (acc >= 100.0) 0 else ceil(0.01 * combo / 100.0).toInt()

        return JudgeEstimate(
            combo = combo,
            perfect = perfect,
            good = good,
            goodToPerfect = goodToPerfect,
            missToPerfect = missToPerfect
        )
    }
}
