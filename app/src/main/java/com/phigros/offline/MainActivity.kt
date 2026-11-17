package com.phigros.offline

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    companion object {
        const val REQUEST_CODE = 11451
        /** Candidate locations of the Phigros local save (checked in order). */
        val SAVE_PATHS = arrayOf(
            "/data/data/com.PigeonGames.Phigros/shared_prefs/com.PigeonGames.Phigros.v2.playerprefs.xml",
            "/storage/emulated/0/Android/data/com.PigeonGames.Phigros/shared_prefs/com.PigeonGames.Phigros.v2.playerprefs.xml"
        )
        /** Candidate locations of the cached account profile (plain JSON). */
        val USERDATA_PATHS = arrayOf(
            "/storage/emulated/0/Android/data/com.PigeonGames.Phigros/files/.userdata",
            "/data/data/com.PigeonGames.Phigros/files/.userdata"
        )
    }

    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var rksText: TextView
    private lateinit var timeText: TextView
    private lateinit var countText: TextView
    private lateinit var btnAnalyze: TextView
    private lateinit var btnB30: TextView
    private lateinit var btnAll: TextView
    private lateinit var btnX: TextView
    private lateinit var btnOver: TextView
    private lateinit var listView: RecyclerView

    private var adapter: SongListAdapter? = null
    private var analysis: AnalysisResult? = null
    private var mode = ViewMode.B30

    private enum class ViewMode { B30, ALL, X30, OVER }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        titleText = findViewById(R.id.title_text)
        statusText = findViewById(R.id.status_text)
        rksText = findViewById(R.id.rks_text)
        timeText = findViewById(R.id.time_text)
        countText = findViewById(R.id.count_text)
        btnAnalyze = findViewById(R.id.btn_analyze)
        btnB30 = findViewById(R.id.btn_b30)
        btnAll = findViewById(R.id.btn_all)
        btnX = findViewById(R.id.btn_x)
        btnOver = findViewById(R.id.btn_over)
        listView = findViewById(R.id.list)

        listView.layoutManager = gridLayoutManager()
        btnB30.isSelected = true

        btnB30.setOnClickListener { mode = ViewMode.B30; refresh() }
        btnAll.setOnClickListener { mode = ViewMode.ALL; refresh() }
        btnX.setOnClickListener { mode = ViewMode.X30; refresh() }
        btnOver.setOnClickListener { mode = ViewMode.OVER; refresh() }
        btnAnalyze.setOnClickListener { runAnalysis() }

        Shizuku.addRequestPermissionResultListener(this)
        updateShizukuStatus()
    }

    private fun gridLayoutManager(): GridLayoutManager {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val lm = GridLayoutManager(this, if (landscape) 3 else 2)
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter?.isHeader(position) == true) lm.spanCount else 1
            }
        }
        return lm
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // rotation is handled in-place (configChanges); re-flow the list for the new orientation.
        val lm = gridLayoutManager()
        listView.layoutManager = lm
        adapter?.let { listView.adapter = it }
    }

    private fun updateShizukuStatus() {
        statusText.text = when {
            ShizukuIO.shizukuReady() ->
                "Shizuku 已授权 ✓  存档路径已就绪"

            Shizuku.pingBinder() -> {
                Shizuku.requestPermission(REQUEST_CODE)
                "正在向 Shizuku 申请 adb shell 权限…"
            }

            ShizukuIO.rootReady() ->
                "Root 已就绪 ✓（uid 0）  存档路径已就绪"

            else ->
                "未检测到 Shizuku 或 Root：请先启动 Shizuku，或授予 Root 权限"
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode == REQUEST_CODE) {
            updateShizukuStatus()
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(this)
        super.onDestroy()
    }

    private fun runAnalysis() {
        if (!ShizukuIO.shizukuReady() && !ShizukuIO.rootReady()) {
            updateShizukuStatus()
            Toast.makeText(this, "需要 Shizuku 或 Root 权限", Toast.LENGTH_LONG).show()
            return
        }
        statusText.text = "正在读取存档并分析…"
        btnAnalyze.isEnabled = false
        Thread {
            try {
                SongDatabase.load(applicationContext)
                var xml: String? = null
                var savePath = ""
                var mtime = 0L
                for (p in SAVE_PATHS) {
                    val s = try { ShizukuIO.readFile(p) } catch (e: Throwable) { null }
                    if (s != null && s.isNotBlank()) {
                        xml = s
                        savePath = p
                        mtime = ShizukuIO.statMtime(p)
                        break
                    }
                }
                val content = xml ?: throw RuntimeException(
                    "无法读取存档：${SAVE_PATHS.joinToString(" / ")} 均不存在或无法访问（请先在 Phigros 中进入一次游戏以生成存档）"
                )
                val parsed = SaveParser.parseSave(content)
                if (parsed.records.isEmpty()) {
                    throw RuntimeException("存档中未找到成绩数据（请先在 Phigros 中游玩任意曲目）")
                }
                val result = Analyzer.analyze(parsed.records)
                var profile: UserProfile? = null
                for (p in USERDATA_PATHS) {
                    profile = try { UserDataParser.parse(ShizukuIO.readFile(p)) } catch (_: Throwable) { null }
                    if (profile != null) break
                }
                runOnUiThread { showResult(result, mtime, parsed.playerId, profile) }
            } catch (e: Throwable) {
                runOnUiThread {
                    statusText.text = "分析失败：${e.message}"
                    Toast.makeText(this, "分析失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread { btnAnalyze.isEnabled = true }
            }
        }.start()
    }

    private fun showResult(result: AnalysisResult, mtime: Long, playerId: String?, profile: UserProfile?) {
        analysis = result
        DetailCache.records = result.records
        DetailCache.totalRks = result.rks
        DetailCache.phiTopRks = result.records.filter { it.acc == 100.0 }
            .map { it.rks }.maxOrNull() ?: 0.0
        val displayName = profile?.nickname ?: playerId
        val idSuffix = profile?.shortId
        val label = when {
            !displayName.isNullOrBlank() ->
                "   玩家：$displayName" + (if (!idSuffix.isNullOrBlank()) " · $idSuffix" else "")
            else -> ""
        }
        statusText.text = "分析完成 ✓$label"
        val fcCount = result.records.count { it.fullCombo && it.score < 1000000L }
        rksText.text = String.format(Locale.US, "%.4f", result.rks)
        timeText.text = "存档时间：" + formatTime(if (mtime > 0) mtime else System.currentTimeMillis())
        countText.text = "${result.records.size} 首 · φ ${result.phi.size} · FC $fcCount"
        refresh()
    }

    private fun refresh() {
        val r = analysis ?: return
        val items = mutableListOf<ListItem>()
        when (mode) {
            ViewMode.B30 -> {
                val b30 = r.phi + r.records.take(27)
                b30.forEachIndexed { i, s -> items.add(ListItem.Song(s, i + 1)) }
            }
            ViewMode.ALL -> {
                // All records, grouped: top 30 (B30) / next 6 (OVER FLOW) / the rest (Others).
                val all = r.records
                all.take(30).forEachIndexed { i, s -> items.add(ListItem.Song(s, i + 1)) }
                val overflow = if (all.size > 30) all.subList(30, minOf(36, all.size)) else emptyList()
                if (overflow.isNotEmpty()) {
                    items.add(ListItem.Header("OVER FLOW"))
                    overflow.forEachIndexed { i, s -> items.add(ListItem.Song(s, 31 + i)) }
                }
                val others = if (all.size > 36) all.drop(36) else emptyList()
                if (others.isNotEmpty()) {
                    items.add(ListItem.Header("Others"))
                    others.forEachIndexed { i, s -> items.add(ListItem.Song(s, 37 + i)) }
                }
            }
            ViewMode.X30 -> {
                val list = r.oneGood.take(30)
                if (list.isEmpty()) items.add(ListItem.Header("暂无 1 Good 成绩"))
                else list.forEachIndexed { i, s -> items.add(ListItem.Song(s, i + 1)) }
            }
            ViewMode.OVER -> {
                val list = r.overLevel.take(30)
                if (list.isEmpty()) items.add(ListItem.Header("暂无越级成绩"))
                else list.forEachIndexed { i, s -> items.add(ListItem.Song(s, i + 1, true)) }
            }
        }
        if (adapter == null) {
            adapter = SongListAdapter(this) { openDetail(it) }
            listView.adapter = adapter
        }
        adapter?.submit(items)
        btnB30.isSelected = mode == ViewMode.B30
        btnAll.isSelected = mode == ViewMode.ALL
        btnX.isSelected = mode == ViewMode.X30
        btnOver.isSelected = mode == ViewMode.OVER
    }

    private fun openDetail(s: SongResult) {
        DetailCache.records = analysis?.records ?: emptyList()
        val intent = Intent(this, DetailActivity::class.java)
            .putExtra(DetailActivity.EXTRA_ID, s.id)
            .putExtra(DetailActivity.EXTRA_LEVEL, s.level)
        startActivity(intent)
    }

    private fun formatTime(epoch: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return fmt.format(Date(epoch * 1000))
    }
}
