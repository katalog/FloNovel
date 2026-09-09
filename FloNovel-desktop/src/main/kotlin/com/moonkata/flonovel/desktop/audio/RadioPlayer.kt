package com.moonkata.flonovel.desktop.audio

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.sourceforge.jaad.aac.Decoder
import net.sourceforge.jaad.SampleBuffer
import net.sourceforge.jaad.adts.ADTSDemultiplexer
import org.json.JSONArray
import java.awt.Desktop
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import com.moonkata.flonovel.desktop.platform.configDir

/**
 * Represents an internet radio stream option.
 */
data class RadioStreamItem(
    val name: String,
    val url: String,
)

/**
 * Holds the current radio playback state for Compose UI observers.
 */
data class RadioPlaybackState(
    val isPlaying: Boolean = false,
    val streamName: String? = null,
    val remainingSeconds: Long = 0L,
) {
    /**
     * Formats the remaining time for display in the viewer footer.
     * e.g., "58분 남음" or "1분 미만 남음".
     */
    fun formatRemainingTime(): String {
        if (!isPlaying || remainingSeconds <= 0L) return ""
        val minutes = (remainingSeconds + 59L) / 60L
        return if (minutes <= 1L && remainingSeconds < 60L) {
            "1분 미만 남음"
        } else {
            "${minutes}분 남음"
        }
    }
}

/**
 * Loads customizable radio streams from user config directory (radio_streams.json).
 * Automatically initializes from bundled resources if file does not exist yet.
 */
object RadioStreamCatalog {
    val DEFAULT_STREAMS = listOf(
        RadioStreamItem(
            name = "The Lounge Hour",
            url = "https://listen2.streamaudio.co/stream/8056",
        ),
        RadioStreamItem(
            name = "RelaxingJazz.com",
            url = "http://stream-02-eu.relaxingjazz.com/stream/3/",
        ),
        RadioStreamItem(
            name = "COTN Radio",
            url = "https://streaming.smartradio.ch:8510/stream",
        ),
    )

    fun streamsConfigFile(): Path = configDir().resolve("radio_streams.json")

    fun defaultJsonContent(): String {
        val resourceStream = RadioStreamCatalog::class.java.getResourceAsStream("/radio_streams.json")
        if (resourceStream != null) {
            try {
                return resourceStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) {}
        }
        val array = JSONArray()
        for (stream in DEFAULT_STREAMS) {
            val obj = org.json.JSONObject()
            obj.put("name", stream.name)
            obj.put("url", stream.url)
            array.put(obj)
        }
        return array.toString(2)
    }

    fun ensureConfigFileExists(targetFile: Path = streamsConfigFile()): Path {
        try {
            if (!Files.exists(targetFile)) {
                if (targetFile.parent != null) {
                    Files.createDirectories(targetFile.parent)
                }
                Files.writeString(targetFile, defaultJsonContent())
            }
        } catch (_: Exception) {}
        return targetFile
    }

    fun parseStreamsJson(text: String): List<RadioStreamItem> {
        val jsonArray = JSONArray(text)
        val list = mutableListOf<RadioStreamItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val name = obj.optString("name", "").trim()
            val url = obj.optString("url", "").trim()
            if (name.isNotEmpty() && url.isNotEmpty()) {
                list.add(RadioStreamItem(name = name, url = url))
            }
        }
        return list
    }

    fun loadStreams(file: Path = streamsConfigFile()): List<RadioStreamItem> {
        ensureConfigFileExists(file)
        return try {
            if (Files.isRegularFile(file)) {
                val text = Files.readString(file).trim()
                val parsed = parseStreamsJson(text)
                if (parsed.isNotEmpty()) parsed else DEFAULT_STREAMS
            } else {
                DEFAULT_STREAMS
            }
        } catch (_: Exception) {
            DEFAULT_STREAMS
        }
    }

    fun openConfigFile(file: Path = streamsConfigFile()) {
        ensureConfigFileExists(file)
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile())
            }
        } catch (_: Exception) {}
    }
}

/**
 * Manages background internet radio stream playback (MP3 & AAC) and auto-stop timer.
 * Completely isolated from viewer UI thread to prevent UI freezing or crashes.
 */
object RadioPlayer {
    private val lock = Any()

    private val _state = mutableStateOf(RadioPlaybackState())
    val state: State<RadioPlaybackState> = _state

    @Volatile
    private var isCancelled = false

    private var activeWorker: Thread? = null
    private var timerJob: Job? = null
    private var activeConnection: HttpURLConnection? = null
    private var activeLine: SourceDataLine? = null
    private var activeMp3Player: javazoom.jl.player.Player? = null

    /**
     * Starts streaming the selected radio item for [durationMinutes].
     */
    fun play(
        stream: RadioStreamItem,
        durationMinutes: Int,
        onError: ((String) -> Unit)? = null,
    ) {
        stop()

        synchronized(lock) {
            isCancelled = false
            val safeMinutes = durationMinutes.coerceIn(1, 1440)
            val totalSeconds = safeMinutes * 60L

            _state.value = RadioPlaybackState(
                isPlaying = true,
                streamName = stream.name,
                remainingSeconds = totalSeconds,
            )

            // 1-second interval countdown timer
            timerJob = CoroutineScope(Dispatchers.Default).launch {
                var remaining = totalSeconds
                while (isActive && remaining > 0 && !isCancelled) {
                    delay(1000L)
                    if (isCancelled) break
                    remaining -= 1
                    if (remaining <= 0) {
                        _state.value = RadioPlaybackState(isPlaying = false, streamName = null, remainingSeconds = 0L)
                        stop()
                        break
                    } else {
                        _state.value = _state.value.copy(remainingSeconds = remaining)
                    }
                }
            }

            val worker = Thread({
                try {
                    val url = URI.create(stream.url).toURL()
                    val conn = url.openConnection() as HttpURLConnection
                    synchronized(lock) {
                        if (isCancelled) {
                            try { conn.disconnect() } catch (_: Exception) {}
                            return@Thread
                        }
                        activeConnection = conn
                    }

                    conn.connectTimeout = 10000
                    conn.readTimeout = 0 // Indefinite stream
                    conn.setRequestProperty("User-Agent", "FloNovel/1.0 (Desktop)")
                    conn.setRequestProperty("Icy-MetaData", "0")
                    conn.connect()

                    val rawStream = conn.inputStream
                    val bufferedStream = BufferedInputStream(rawStream, 65536)
                    val contentType = conn.contentType ?: ""

                    val isMp3 = contentType.contains("mpeg", ignoreCase = true) ||
                            contentType.contains("mp3", ignoreCase = true) ||
                            stream.url.lowercase().contains(".mp3")

                    if (isMp3) {
                        val player = javazoom.jl.player.Player(bufferedStream)
                        synchronized(lock) {
                            if (isCancelled) {
                                try { player.close() } catch (_: Exception) {}
                                return@Thread
                            }
                            activeMp3Player = player
                        }
                        player.play()
                    } else {
                        // AAC / AAC+ decoding via JAAD
                        val adts = ADTSDemultiplexer(bufferedStream)
                        val decoder = Decoder.create(adts.decoderInfo)
                        val sampleBuffer = SampleBuffer()
                        var line: SourceDataLine? = null

                        try {
                            while (!isCancelled) {
                                val frame = adts.readNextFrame() ?: break
                                decoder.decodeFrame(frame, sampleBuffer)
                                if (line == null) {
                                    val format = AudioFormat(
                                        sampleBuffer.sampleRate.toFloat(),
                                        sampleBuffer.bitsPerSample,
                                        sampleBuffer.channels,
                                        true,
                                        sampleBuffer.isBigEndian,
                                    )
                                    val dataLine = AudioSystem.getSourceDataLine(format)
                                    dataLine.open(format, 32768)
                                    dataLine.start()
                                    synchronized(lock) {
                                        if (isCancelled) {
                                            try { dataLine.stop(); dataLine.close() } catch (_: Exception) {}
                                            return@Thread
                                        }
                                        line = dataLine
                                        activeLine = dataLine
                                    }
                                }
                                val data = sampleBuffer.data
                                line?.write(data, 0, data.size)
                            }
                        } finally {
                            try { line?.drain() } catch (_: Exception) {}
                            try { line?.stop() } catch (_: Exception) {}
                            try { line?.close() } catch (_: Exception) {}
                            synchronized(lock) {
                                activeLine = null
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isCancelled) {
                        val msg = e.message ?: e.javaClass.simpleName
                        onError?.invoke(msg)
                    }
                } finally {
                    if (!isCancelled) {
                        stop()
                    }
                }
            }, "FloNovel-RadioWorker")

            worker.isDaemon = true
            activeWorker = worker
            worker.start()
        }
    }

    /**
     * Immediately stops audio streaming and clears active timers.
     */
    fun stop() {
        synchronized(lock) {
            isCancelled = true

            timerJob?.cancel()
            timerJob = null

            try { activeMp3Player?.close() } catch (_: Exception) {}
            activeMp3Player = null

            try { activeLine?.stop() } catch (_: Exception) {}
            try { activeLine?.close() } catch (_: Exception) {}
            activeLine = null

            try { activeConnection?.disconnect() } catch (_: Exception) {}
            activeConnection = null

            try { activeWorker?.interrupt() } catch (_: Exception) {}
            activeWorker = null

            _state.value = RadioPlaybackState(isPlaying = false, streamName = null, remainingSeconds = 0L)
        }
    }
}
