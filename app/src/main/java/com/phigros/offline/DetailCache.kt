package com.phigros.offline

/**
 * In-memory hand-off between MainActivity and DetailActivity. The parsed
 * records are kept here (single process, offline) so the detail screen can
 * group every difficulty of the tapped song without re-reading the save.
 */
object DetailCache {
    var records: List<SongResult> = emptyList()
    /** Total B30 RKS, mirrors saveInfo.summary.rankingScore, for the suggest threshold. */
    var totalRks: Double = 0.0
    /** RKS of the highest 100% (phi) chart, mirrors findAccRecord(100,true)[0].rks. */
    var phiTopRks: Double = 0.0
}
