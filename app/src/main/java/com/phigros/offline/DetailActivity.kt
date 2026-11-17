package com.phigros.offline

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/** Offline per-song detail screen: difficulty switch, current record, judgment estimate. */
class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "song_id"
        const val EXTRA_LEVEL = "song_level"
        private val LEVELS = listOf("EZ", "HD", "IN", "AT")
    }

    private lateinit var songId: String
    private var recByLevel: Map<String, SongResult> = emptyMap()
    private var diffMap: Map<String, Double> = emptyMap()
    private var noteMap: Map<String, NoteStat> = emptyMap()
    private var selected: String? = null

    private lateinit var btnBack: TextView
    private lateinit var songTitle: TextView
    private lateinit var cover: ImageView
    private lateinit var btnEz: TextView
    private lateinit var btnHd: TextView
    private lateinit var btnIn: TextView
    private lateinit var btnAt: TextView
    private lateinit var scoreText: TextView
    private lateinit var accText: TextView
    private lateinit var rksText: TextView
    private lateinit var fcText: TextView
    private lateinit var noteText: TextView
    private lateinit var diffText: TextView
    private lateinit var ratingIcon: ImageView
    private lateinit var judgePerfect: TextView
    private lateinit var judgeGood: TextView
    private lateinit var judgeBad: TextView
    private lateinit var judgeMiss: TextView
    private lateinit var judgeCombo: TextView
    private lateinit var boostText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        SongDatabase.load(applicationContext)

        songId = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }

        val records = DetailCache.records.filter { it.id == songId }
        recByLevel = records.associateBy { it.level }
        diffMap = SongDatabase.difficulty[songId] ?: emptyMap()
        noteMap = SongDatabase.notes[songId] ?: emptyMap()

        btnBack = findViewById(R.id.btn_back)
        songTitle = findViewById(R.id.song_title)
        cover = findViewById(R.id.cover_image)
        btnEz = findViewById(R.id.btn_ez)
        btnHd = findViewById(R.id.btn_hd)
        btnIn = findViewById(R.id.btn_in)
        btnAt = findViewById(R.id.btn_at)
        scoreText = findViewById(R.id.score_text)
        accText = findViewById(R.id.acc_text)
        rksText = findViewById(R.id.rks_text)
        fcText = findViewById(R.id.fc_text)
        noteText = findViewById(R.id.note_text)
        diffText = findViewById(R.id.diff_text)
        ratingIcon = findViewById(R.id.rating_icon)
        judgePerfect = findViewById(R.id.judge_perfect)
        judgeGood = findViewById(R.id.judge_good)
        judgeBad = findViewById(R.id.judge_bad)
        judgeMiss = findViewById(R.id.judge_miss)
        judgeCombo = findViewById(R.id.judge_combo)
        boostText = findViewById(R.id.boost_text)

        val requestedLevel = intent.getStringExtra(EXTRA_LEVEL)
        selected = requestedLevel?.takeIf { diffMap.containsKey(it) || recByLevel[it] != null }
            ?: LEVELS.firstOrNull { recByLevel[it] != null }
            ?: LEVELS.firstOrNull { diffMap.containsKey(it) }

        btnBack.setOnClickListener { finish() }
        applyCoverRatio()
        bindDiffButtons()
        bindRecord()
    }

    /** Keep the cover at native 16:9: height follows measured width, no crop. */
    private fun applyCoverRatio() {
        cover.viewTreeObserver.addOnGlobalLayoutListener(object :
            android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val w = cover.width
                if (w > 0) {
                    cover.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    cover.layoutParams.height = (w * 9 / 16)
                    cover.requestLayout()
                }
            }
        })
    }

    private fun levelButtons(): Map<String, TextView> =
        mapOf("EZ" to btnEz, "HD" to btnHd, "IN" to btnIn, "AT" to btnAt)

    private fun bindDiffButtons() {
        val btns = levelButtons()
        for (level in LEVELS) {
            val btn = btns[level] ?: continue
            if (diffMap.containsKey(level)) {
                btn.visibility = View.VISIBLE
                val diff = diffMap[level]
                btn.text = if (diff != null)
                    String.format(Locale.US, "%s %.1f", level, diff) else level
                btn.isSelected = level == selected
                btn.setOnClickListener {
                    selected = level
                    bindDiffButtons()
                    bindRecord()
                }
            } else {
                btn.visibility = View.GONE
            }
        }
    }

    private fun bindRecord() {
        val art = SongArt.bitmapFor(this, songId)
        if (art != null) cover.setImageBitmap(art) else cover.setImageDrawable(null)
        songTitle.text = SongDatabase.titleOf(songId)

        val level = selected
        val rec = level?.let { recByLevel[it] }
        val note = level?.let { noteMap[it] }

        if (rec == null) {
            scoreText.text = "—"
            accText.text = "—"
            rksText.text = "—"
            fcText.text = "未游玩"
            noteText.text = note?.let {
                String.format(Locale.US, "物量 %d · Drag %d · Flick %d", it.combo, it.drag, it.flick)
            } ?: "物量 —"
            diffText.text = level?.let {
                diffMap[it]?.let { d -> String.format(Locale.US, "难度 %s · 定数 %.1f", it, d) } ?: "难度 $it"
            } ?: "暂无难度数据"
            ratingIcon.setImageDrawable(null)
            judgePerfect.text = "—"
            judgeGood.text = "—"
            judgeBad.text = "—"
            judgeMiss.text = "—"
            judgeCombo.text = "—"
            val unplayedDiff = level?.let { diffMap[it] }
            val unplayedSuggest = if (unplayedDiff != null)
                Analyzer.suggestAcc(0.0, unplayedDiff, DetailCache.records, DetailCache.totalRks, DetailCache.phiTopRks)
            else null
            boostText.text = if (unplayedDiff != null && unplayedSuggest == null) "无法推分" else "该难度暂无本地记录。"
            return
        }

        scoreText.text = rec.score.toString()
        accText.text = String.format(Locale.US, "%.2f%%", rec.acc)
        rksText.text = String.format(Locale.US, "RKS %.2f", rec.rks)
        fcText.text = if (rec.fullCombo) "Full Combo" else "未 Full Combo"
        noteText.text = note?.let {
            String.format(Locale.US, "物量 %d · Drag %d · Flick %d", it.combo, it.drag, it.flick)
        } ?: "物量 —"
        diffText.text = String.format(Locale.US, "难度 %s · 定数 %.1f", rec.level, rec.difficulty)

        val rating = SongListAdapter.ratingOf(rec.score, rec.fullCombo)
        ratingIcon.setImageResource(SongListAdapter.RATING_ICONS[rating] ?: R.drawable.ic_rating_new)

        val suggest = Analyzer.suggestAcc(
            rec.rks, rec.difficulty, DetailCache.records,
            DetailCache.totalRks, DetailCache.phiTopRks
        )
        val cannotPush = suggest == null || suggest <= rec.acc + 0.01

        val judge = JudgeEstimator.estimate(rec.acc, note?.combo ?: 0)
        if (judge == null) {
            judgePerfect.text = "—"
            judgeGood.text = "—"
            judgeBad.text = "—"
            judgeMiss.text = "—"
            judgeCombo.text = "—"
            boostText.text = if (cannotPush) "无法推分" else "缺少物量表，无法估算判定与提升空间。"
            return
        }

        judgePerfect.text = judge.perfect.toString()
        judgeGood.text = judge.good.toString()
        judgeBad.text = "—"
        judgeMiss.text = "—"
        judgeCombo.text = "—"

        boostText.text = when {
            cannotPush -> "无法推分"
            rec.acc >= 100.0 -> "已 All Perfect。"
            else -> String.format(
                Locale.US,
                "再把 %d 个 Good 打成 Perfect，或 %d 个 Bad/Miss 打成 Perfect，约可提升 0.01%% acc。",
                judge.goodToPerfect ?: 0,
                judge.missToPerfect ?: 0
            )
        }
    }
}
