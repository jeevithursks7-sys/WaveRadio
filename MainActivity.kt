package com.example.waveradio

import android.app.Activity
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

private data class Station(val name: String, val country: String, val stream: String, val tags: String)

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val stations = mutableListOf<Station>()
    private val favourites = mutableSetOf<String>()
    private var player: MediaPlayer? = null
    private var current: Station? = null
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var nowPlaying: TextView
    private lateinit var playButton: Button
    private var category = "pop"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF101018.toInt()
        window.navigationBarColor = 0xFF101018.toInt()
        buildUi()
        loadStations(category)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 22, 20, 12)
            setBackgroundColor(0xFF101018.toInt())
        }
        setContentView(root)
        root.addView(TextView(this).apply {
            text = "WAVE RADIO  ♫"
            textSize = 25f; setTextColor(0xFF72E6C1.toInt()); gravity = Gravity.CENTER_VERTICAL
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Live stations from around the world"
            textSize = 14f; setTextColor(0xFFB9B9C8.toInt()); setPadding(0, 4, 0, 16)
        })
        search = EditText(this).apply {
            hint = "Search stations or countries"; textSize = 15f
            setSingleLine(true); setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF8D8D9B.toInt())
            setBackgroundColor(0xFF222230.toInt())
        }
        root.addView(search, LinearLayout.LayoutParams(-1, 52))
        search.setOnEditorActionListener { _, _, _ -> renderStations(); true }

        val categories = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 10) }
        listOf("Pop" to "pop", "Rock" to "rock", "Jazz" to "jazz", "Classical" to "classical", "News" to "news").forEach { (label, tag) ->
            val b = Button(this).apply { text = label; textSize = 11f; isAllCaps = false; setPadding(5, 0, 5, 0) }
            b.setOnClickListener { category = tag; loadStations(tag) }
            categories.addView(b, LinearLayout.LayoutParams(0, 44, 1f))
        }
        root.addView(categories)
        status = TextView(this).apply { text = "Loading stations…"; textSize = 13f; setTextColor(0xFFB9B9C8.toInt()); setPadding(0, 4, 0, 8) }
        root.addView(status)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val playerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 8); setBackgroundColor(0xFF242433.toInt())
        }
        nowPlaying = TextView(this).apply { text = "Nothing playing"; textSize = 15f; setTextColor(0xFFFFFFFF.toInt()) }
        playerPanel.addView(nowPlaying)
        playButton = Button(this).apply { text = "▶  Play"; isAllCaps = false }
        playButton.setOnClickListener { if (player?.isPlaying == true) pausePlayback() else current?.let { startPlayback(it) } }
        playerPanel.addView(playButton)
        root.addView(playerPanel)
    }

    private fun loadStations(tag: String) {
        status.text = "Finding live $tag stations…"
        worker.execute {
            try {
                val encoded = URLEncoder.encode(tag, "UTF-8")
                val url = URL("https://de1.api.radio-browser.info/json/stations/search?tag=$encoded&limit=80&hidebroken=true&order=clickcount&reverse=true")
                val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 12000; readTimeout = 12000; setRequestProperty("User-Agent", "WaveRadio/1.0") }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val arr = JSONArray(body)
                val result = mutableListOf<Station>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val stream = o.optString("url_resolved").ifBlank { o.optString("url") }
                    val name = o.optString("name").trim()
                    if (stream.startsWith("http") && name.isNotBlank()) result.add(Station(name, o.optString("country"), stream, o.optString("tags")))
                }
                runOnUiThread { stations.clear(); stations.addAll(result); status.text = "${stations.size} stations · tap one to listen"; renderStations() }
            } catch (_: Exception) {
                runOnUiThread { status.text = "Couldn't load stations. Check your internet and try another category."; list.removeAllViews() }
            }
        }
    }

    private fun renderStations() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val query = search.text.toString().trim().lowercase()
        val visible = stations.filter { it.name.lowercase().contains(query) || it.country.lowercase().contains(query) }
        visible.take(60).forEach { station ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(10, 10, 4, 10); setBackgroundColor(0xFF1B1B27.toInt()) }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(this).apply { text = station.name; textSize = 15f; setTextColor(0xFFFFFFFF.toInt()); maxLines = 1 })
            info.addView(TextView(this).apply { text = station.country.ifBlank { "Online radio" }; textSize = 12f; setTextColor(0xFFAAAAAA.toInt()) })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            val fav = Button(this).apply { text = if (favourites.contains(station.stream)) "♥" else "♡"; isAllCaps = false }
            fav.setOnClickListener { if (!favourites.add(station.stream)) favourites.remove(station.stream); fav.text = if (favourites.contains(station.stream)) "♥" else "♡" }
            row.addView(fav)
            row.setOnClickListener { startPlayback(station) }
            val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = 7
            list.addView(row, lp)
        }
        if (visible.isEmpty() && stations.isNotEmpty()) status.text = "No matching stations"
    }

    private fun startPlayback(station: Station) {
        stopPlayer()
        current = station
        nowPlaying.text = "Loading: ${station.name}"
        playButton.text = "Ⅱ  Pause"
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(station.stream)
                setOnPreparedListener { it.start(); runOnUiThread { nowPlaying.text = "♫  ${station.name}"; playButton.text = "Ⅱ  Pause" } }
                setOnErrorListener { _, _, _ -> runOnUiThread { nowPlaying.text = "Stream unavailable — try another station"; playButton.text = "▶  Play" }; true }
                prepareAsync()
            }
        } catch (_: Exception) { nowPlaying.text = "Couldn't play this station"; playButton.text = "▶  Play" }
    }

    private fun pausePlayback() { try { player?.pause(); playButton.text = "▶  Play" } catch (_: Exception) {} }
    private fun stopPlayer() { try { player?.stop() } catch (_: Exception) {}; player?.release(); player = null }
    override fun onDestroy() { stopPlayer(); worker.shutdownNow(); super.onDestroy() }
}
