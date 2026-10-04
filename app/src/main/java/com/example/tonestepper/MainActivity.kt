package com.example.tonestepper

import android.media.*
import android.os.Bundle
import android.os.SystemClock
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    @Volatile private var running = false
    private var audioThread: Thread? = null
    private lateinit var status: TextView

    private val sampleRate = 48000
    private val semitones = intArrayOf(-2, -1, 0, 1, 2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val frequency = findViewById<EditText>(R.id.frequency)
        val duration = findViewById<EditText>(R.id.duration)
        val sessionMinutes = findViewById<EditText>(R.id.sessionMinutes)
        val toneVolume = findViewById<SeekBar>(R.id.toneVolume)
        val noiseVolume = findViewById<SeekBar>(R.id.noiseVolume)
        val noiseType = findViewById<Spinner>(R.id.noiseType)
        status = findViewById(R.id.status)

        val noiseOptions = arrayOf(
            "Nessuno", "Bianco", "Rosa", "Marrone", "Viola"
        )

        noiseType.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            noiseOptions
        )

        findViewById<Button>(R.id.start).setOnClickListener {
            if (running) return@setOnClickListener

            val center = frequency.text.toString()
                .toDoubleOrNull()
                ?.coerceIn(100.0, 20000.0)
                ?: 15000.0

            val durationMs = duration.text.toString()
                .toDoubleOrNull()
                ?.coerceIn(50.0, 60000.0)
                ?: 500.0

            val minutes = sessionMinutes.text.toString()
                .toDoubleOrNull()
                ?.coerceAtLeast(0.0)
                ?: 0.0

            val toneAmp =
                (toneVolume.progress / 100.0) * 0.30

            val noiseAmp =
                (noiseVolume.progress / 100.0) * 0.20

            startAudio(
                center,
                durationMs,
                minutes,
                toneAmp,
                noiseAmp,
                noiseType.selectedItem.toString()
            )
        }

        findViewById<Button>(R.id.stop).setOnClickListener {
            stopAudio()
        }
    }

    private fun startAudio(
        center: Double,
        durationMs: Double,
        sessionMinutes: Double,
        toneAmp: Double,
        noiseAmp: Double,
        noiseType: String
    ) {
        running = true

        audioThread = Thread {

            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuffer, 16384))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val framesPerChunk = 1024
            val buffer = ShortArray(framesPerChunk * 2)

            val framesPerTone =
                (durationMs / 1000.0 * sampleRate)
                    .toLong()
                    .coerceAtLeast(1L)

            val fadeFrames = min(
                (0.020 * sampleRate).toInt(),
                max(1, (framesPerTone / 5).toInt())
            )

            val sessionMs =
                if (sessionMinutes <= 0.0)
                    0L
                else
                    (sessionMinutes * 60000.0).toLong()

            val startTime = SystemClock.elapsedRealtime()

            var phase = 0.0
            var pinkStateL = 0.0
            var pinkStateR = 0.0
            var brownStateL = 0.0
            var brownStateR = 0.0
            var previousWhiteL = 0.0
            var previousWhiteR = 0.0

            fun makeNoise(left: Boolean): Double {

                if (noiseType == "Nessuno" || noiseAmp <= 0.0)
                    return 0.0

                val white = Random.nextDouble(-1.0, 1.0)

                return when (noiseType) {

                    "Bianco" -> white

                    "Rosa" -> {
                        if (left) {
                            pinkStateL =
                                0.98 * pinkStateL + 0.02 * white
                            (pinkStateL * 5.0)
                                .coerceIn(-1.0, 1.0)
                        } else {
                            pinkStateR =
                                0.98 * pinkStateR + 0.02 * white
                            (pinkStateR * 5.0)
                                .coerceIn(-1.0, 1.0)
                        }
                    }

                    "Marrone" -> {
                        if (left) {
                            brownStateL =
                                (brownStateL + white * 0.02)
                                    .coerceIn(-1.0, 1.0)
                            brownStateL
                        } else {
                            brownStateR =
                                (brownStateR + white * 0.02)
                                    .coerceIn(-1.0, 1.0)
                            brownStateR
                        }
                    }

                    "Viola" -> {
                        if (left) {
                            val value =
                                (white - previousWhiteL) * 0.7
                            previousWhiteL = white
                            value.coerceIn(-1.0, 1.0)
                        } else {
                            val value =
                                (white - previousWhiteR) * 0.7
                            previousWhiteR = white
                            value.coerceIn(-1.0, 1.0)
                        }
                    }

                    else -> 0.0
                }
            }

            try {
                track.play()

                while (running) {

                    if (
                        sessionMs > 0L &&
                        SystemClock.elapsedRealtime() - startTime >= sessionMs
                    ) {
                        running = false
                        break
                    }

                    // Frequenza scelta casualmente a ogni tono.
                    val semi =
                        semitones[Random.nextInt(semitones.size)]

                    val freq =
                        center * 2.0.pow(semi / 12.0)

                    // Anche il canale è scelto casualmente
                    // e indipendentemente dal semitono.
                    val leftChannel = Random.nextBoolean()

                    val channelName =
                        if (leftChannel) "SX" else "DX"

                    runOnUiThread {
                        status.text =
                            "%.0f Hz  •  %s  •  %+d semitoni"
                                .format(freq, channelName, semi)
                    }

                    var produced = 0L

                    while (produced < framesPerTone && running) {

                        if (
                            sessionMs > 0L &&
                            SystemClock.elapsedRealtime() - startTime >= sessionMs
                        ) {
                            running = false
                            break
                        }

                        val frameCount = min(
                            framesPerChunk.toLong(),
                            framesPerTone - produced
                        ).toInt()

                        for (i in 0 until frameCount) {

                            val pos = produced + i

                            val edge = min(
                                pos,
                                framesPerTone - 1 - pos
                            )

                            val fade =
                                if (edge < fadeFrames)
                                    edge.toDouble() / fadeFrames
                                else
                                    1.0

                            val tone =
                                sin(phase) * toneAmp * fade

                            val noiseL =
                                makeNoise(true) * noiseAmp

                            val noiseR =
                                makeNoise(false) * noiseAmp

                            val left =
                                noiseL +
                                if (leftChannel) tone else 0.0

                            val right =
                                noiseR +
                                if (!leftChannel) tone else 0.0

                            val index = i * 2

                            buffer[index] =
                                (
                                    left.coerceIn(-1.0, 1.0) *
                                    Short.MAX_VALUE
                                ).toInt().toShort()

                            buffer[index + 1] =
                                (
                                    right.coerceIn(-1.0, 1.0) *
                                    Short.MAX_VALUE
                                ).toInt().toShort()

                            phase +=
                                2.0 * Math.PI *
                                freq / sampleRate

                            if (phase >= 2.0 * Math.PI)
                                phase -= 2.0 * Math.PI
                        }

                        val written = track.write(
                            buffer,
                            0,
                            frameCount * 2,
                            AudioTrack.WRITE_BLOCKING
                        )

                        if (written < 0) {
                            running = false
                            break
                        }

                        produced += frameCount
                    }
                }

            } finally {

                try {
                    track.pause()
                    track.flush()
                    track.stop()
                } catch (_: Exception) {}

                track.release()

                val timerCompleted =
                    sessionMs > 0L &&
                    SystemClock.elapsedRealtime() - startTime >= sessionMs

                runOnUiThread {
                    status.text =
                        if (timerCompleted)
                            "Sessione completata"
                        else
                            "Fermato"
                }

                running = false
            }

        }.also { it.start() }
    }

    private fun stopAudio() {
        running = false
        audioThread?.interrupt()
        audioThread = null
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}
