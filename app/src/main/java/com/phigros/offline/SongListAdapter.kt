package com.phigros.offline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.util.Locale

/** Flat list entry: either a song card or a section divider banner. */
sealed class ListItem {
    data class Song(val result: SongResult, val rank: Int, val showLine: Boolean = false) : ListItem()
    data class Header(val title: String) : ListItem()
}

/** Renders song cards and section banners, mirroring the phi-plugin list. */
class SongListAdapter(
    private val context: android.content.Context,
    private val onClick: (SongResult) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<ListItem> = emptyList()

    fun submit(list: List<ListItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ListItem.Song -> TYPE_SONG
        is ListItem.Header -> TYPE_HEADER
    }

    fun isHeader(position: Int): Boolean = items.getOrNull(position) is ListItem.Header

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_header, parent, false))
        } else {
            SongVH(inflater.inflate(R.layout.item_song, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.Song -> {
                (holder as SongVH).bind(item)
                holder.itemView.setOnClickListener { onClick(item.result) }
            }
            is ListItem.Header -> (holder as HeaderVH).bind(item.title)
        }
    }

    class SongVH(val v: View) : RecyclerView.ViewHolder(v) {
        private val coverImage = v.findViewById<ImageView>(R.id.cover_image)
        private val rankText = v.findViewById<TextView>(R.id.rank_text)
        private val songName = v.findViewById<TextView>(R.id.song_name)
        private val levelBadge = v.findViewById<TextView>(R.id.level_badge)
        private val accText = v.findViewById<TextView>(R.id.acc_text)
        private val suggestText = v.findViewById<TextView>(R.id.suggest_text)
        private val scoreText = v.findViewById<TextView>(R.id.score_text)
        private val ratingIcon = v.findViewById<ImageView>(R.id.rating_icon)

        fun bind(item: ListItem.Song) {
            val s = item.result
            val rating = ratingOf(s.score, s.fullCombo)
            val lc = LEVEL_COLORS[s.level] ?: 0xFF9C9C9C.toInt()

            val art = SongArt.bitmapFor(v.context, s.id)
            if (art != null) coverImage.setImageBitmap(art) else coverImage.setImageDrawable(null)

            rankText.text = "#${item.rank}"
            songName.text = s.song

            levelBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 4f * v.resources.displayMetrics.density
                setColor(lc and 0x88FFFFFF.toInt())
            }
            levelBadge.text = String.format(Locale.US, "%.1f", s.difficulty)

            accText.text = String.format(Locale.US, "%.2f%%", s.acc)
            val line = if (item.showLine) s.overLevelLine else null
            if (line != null) {
                suggestText.text = String.format(Locale.US, "线 %.2f%%", line)
                suggestText.setTextColor(suggestColor(line))
            } else {
                val suggest = Analyzer.suggestAcc(
                    s.rks, s.difficulty, DetailCache.records,
                    DetailCache.totalRks, DetailCache.phiTopRks
                )
                // 无法推分：满分也够不到门槛，或所需 acc 已不高于当前 acc（已达标）。
                if (suggest == null || suggest <= s.acc + 0.01) {
                    suggestText.text = "无法推分"
                    suggestText.setTextColor(0xFF888888.toInt())
                } else {
                    suggestText.text = String.format(Locale.US, ">> %.2f%%", suggest)
                    suggestText.setTextColor(0xFFADFF2F.toInt())
                }
            }

            scoreText.text = s.score.toString()

            val icon = RATING_ICONS[rating] ?: R.drawable.ic_rating_new
            ratingIcon.setImageResource(icon)
        }
    }

    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title = v.findViewById<TextView>(R.id.header_title)
        fun bind(text: String) {
            title.text = text
        }
    }

    companion object {
        const val TYPE_SONG = 0
        const val TYPE_HEADER = 1

        val LEVEL_COLORS = mapOf(
            "EZ" to 0xFF7AE07A.toInt(),
            "HD" to 0xFF009DFF.toInt(),
            "IN" to 0xFFFF6161.toInt(),
            "AT" to 0xFF9C9C9C.toInt(),
            "Legacy" to 0xFF7700FF.toInt()
        )

        val RATING_ICONS = mapOf(
            "phi" to R.drawable.ic_rating_phi,
            "FC" to R.drawable.ic_rating_fc,
            "V" to R.drawable.ic_rating_v,
            "S" to R.drawable.ic_rating_s,
            "A" to R.drawable.ic_rating_a,
            "B" to R.drawable.ic_rating_b,
            "C" to R.drawable.ic_rating_c,
            "F" to R.drawable.ic_rating_f,
            "NEW" to R.drawable.ic_rating_new
        )

        /** Over-level line color bands, mirroring phi-plugin suggestType 0..5. */
        fun suggestColor(line: Double): Int {
            return when {
                line < 98.5 -> 0xFFE64D4D.toInt()
                line < 99.0 -> 0xFFE6934D.toInt()
                line < 99.5 -> 0xFF33CC66.toInt()
                line < 99.7 -> 0xFF33C2E6.toInt()
                line < 99.85 -> 0xFFA64DE6.toInt()
                else -> 0xFFA64DE6.toInt()
            }
        }

        /** Thresholds mirror phi-plugin LevelRecordInfo.Rating. */
        fun ratingOf(score: Long, fc: Boolean): String {            if (score >= 1000000) return "phi"
            if (fc) return "FC"
            if (score == 0L) return "NEW"
            if (score < 700000) return "F"
            if (score < 820000) return "C"
            if (score < 880000) return "B"
            if (score < 920000) return "A"
            if (score < 960000) return "S"
            return "V"
        }
    }
}

/** Loads embedded song artwork from assets, keyed by song id (Song.Composer). */
object SongArt {
    private var index: Map<String, String>? = null
    private val cache = LruCache<String, Bitmap>(16)

    private fun index(context: android.content.Context): Map<String, String> {
        index?.let { return it }
        val map = HashMap<String, String>()
        try {
            val text = context.assets.open("art_index.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                map[k] = obj.getString(k)
            }
        } catch (e: Exception) {
            // no artwork mapping
        }
        index = map
        return map
    }

    fun bitmapFor(context: android.content.Context, songId: String): Bitmap? {
        cache.get(songId)?.let { return it }
        val file = index(context)[songId] ?: return null
        val bmp = try {
            val opts = BitmapFactory.Options()
            opts.inPreferredConfig = Bitmap.Config.RGB_565
            context.assets.open("art/$file").use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            }
        } catch (e: Exception) {
            null
        } ?: return null
        cache.put(songId, bmp)
        return bmp
    }
}
