package com.fsl.neuroxapptest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MusicActivity : AppCompatActivity() {

    private lateinit var llSongList: LinearLayout
    private lateinit var llLrc: LinearLayout
    private lateinit var btnPlay: ImageView
    private lateinit var tvSongTitle: TextView
    private lateinit var tvSongArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView

    private var lrcLines: List<LrcParser.LrcLine> = emptyList()
    private var lastHighlightedIndex = -1
    private var isUserSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        llSongList = findViewById(R.id.llSongList)
        llLrc = findViewById(R.id.llLrc)
        btnPlay = findViewById(R.id.btnPlay)
        tvSongTitle = findViewById(R.id.tvSongTitle)
        tvSongArtist = findViewById(R.id.tvSongArtist)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)

        MusicPlayer.init(this)
        MusicPlayer.onStateChanged = { runOnUiThread { refreshUi() } }
        MusicPlayer.onProgress = { current, duration ->
            runOnUiThread { updateProgress(current, duration) }
        }

        // 拖动进度条
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {
                isUserSeeking = true
            }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserSeeking = false
                sb?.let { MusicPlayer.seekTo(it.progress) }
            }
        })

        btnPlay.setOnClickListener { MusicPlayer.togglePlayPause() }
        findViewById<ImageView>(R.id.btnPrev).setOnClickListener { MusicPlayer.prev() }
        findViewById<ImageView>(R.id.btnNext).setOnClickListener { MusicPlayer.next() }

        renderSongList()
        refreshUi()
    }

    private fun renderSongList() {
        llSongList.removeAllViews()
        val inflater = LayoutInflater.from(this)

        SongLibrary.SONGS.forEachIndexed { index, song ->
            val item = inflater.inflate(R.layout.item_song, llSongList, false)
            item.findViewById<TextView>(R.id.tvSongTitle).text = song.title
            item.findViewById<TextView>(R.id.tvSongArtist).text = song.artist
            item.setOnClickListener { MusicPlayer.playIndex(index) }
            llSongList.addView(item)
        }
    }

    private fun refreshUi() {
        val song = SongLibrary.SONGS.getOrNull(MusicPlayer.currentIndex)
        if (song != null) {
            tvSongTitle.text = song.title
            tvSongArtist.text = song.artist
            // 加载歌词
            lrcLines = LrcParser.parse(this, song.lrcPath)
            lastHighlightedIndex = -1
            renderLrc(-1)
        }

        btnPlay.setImageResource(
            if (MusicPlayer.isPlaying) R.drawable.playing else R.drawable.pau
        )

        val duration = MusicPlayer.getDuration()
        seekBar.max = if (duration > 0) duration else 100
        tvTotalTime.text = formatTime(duration)

        for (i in 0 until llSongList.childCount) {
            val item = llSongList.getChildAt(i)
            val title = item.findViewById<TextView>(R.id.tvSongTitle)
            if (i == MusicPlayer.currentIndex) {
                item.setBackgroundResource(R.drawable.bg_song_item_selected)
                title.setTextColor(0xFF1BA39A.toInt())
            } else {
                item.setBackgroundResource(0)
                title.setTextColor(0xFF666666.toInt())
            }
        }
    }

    private fun updateProgress(current: Int, duration: Int) {
        if (!isUserSeeking) {
            seekBar.max = if (duration > 0) duration else 100
            seekBar.progress = current
            tvCurrentTime.text = formatTime(current)
            tvTotalTime.text = formatTime(duration)

            // 更新歌词高亮
            val idx = LrcParser.findLineIndex(lrcLines, current)
            if (idx != lastHighlightedIndex) {
                lastHighlightedIndex = idx
                renderLrc(idx)
            }
        }
    }

    /**
     * 渲染歌词。highlightIndex 高亮。
     */
    private fun renderLrc(highlightIndex: Int) {
        llLrc.removeAllViews()
        if (lrcLines.isEmpty()) {
            val tv = TextView(this).apply {
                text = "暂无歌词"
                setTextColor(0xFF999999.toInt())
                textSize = 13f
                setPadding(0, 20, 0, 20)
            }
            llLrc.addView(tv)
            return
        }

        lrcLines.forEachIndexed { index, line ->
            val tv = TextView(this).apply {
                text = line.text
                textSize = 14f
                setPadding(0, 8, 0, 8)
                setTextColor(if (index == highlightIndex) 0xFF1BA39A.toInt() else 0xFF999999.toInt())
                if (index == highlightIndex) {
                    textSize = 16f
                }
            }
            llLrc.addView(tv)
        }

        // 滚动到高亮行
        if (highlightIndex >= 0 && highlightIndex < llLrc.childCount) {
            val child = llLrc.getChildAt(highlightIndex)
            val scrollView = llLrc.parent as? android.widget.ScrollView
            scrollView?.smoothScrollTo(0, child.top - 100)
        }
    }

    private fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        return "%02d:%02d".format(mm, ss)
    }

    override fun onDestroy() {
        super.onDestroy()
        MusicPlayer.release()
    }
}