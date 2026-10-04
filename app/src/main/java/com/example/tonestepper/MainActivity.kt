package com.example.tonestepper

import android.media.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    @Volatile private var running = false
    private var audioThread: Thread? = null
    private lateinit var status: TextView

    private val sampleRate = 48000
    private val sequence = intArrayOf(-2, -1, 0, 1, 2, 1, 0, -1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val frequency = findViewById<EditText>(R.id.frequency)
        val duration = findViewById<EditText>(R.id.duration)
        val volume = findViewById<SeekBar>(R.id.volume)
        status = findViewById(R.id.status)

        findViewById<Button>(R.id.start).setOnClickListener {
            if (running) return@setOnClickListener

            val center =
                frequency.text.toString().toDoubleOrNull() ?: 15000.0

            val durationMs =
                (duration.text.toString().toDoubleOrNull() ?: 500.0)
                    .coerceIn(50.0, 60000.0)

            val amp =
                (volume.progress / 100.0)
                    .coerceIn(0.0, 1.0) * 0.35

            startAudio(center, durationMs, amp)
        }

        findViewById<Button>(R.id.stop).setOnClickListener {
            stopAudio()
        }
    }

    private fun startAudio(
        center: Double,
        durationMs: Double,
        amplitude: Double
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
                .setBufferSizeInBytes(max(minBuffer, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            // Stereo interleaved: L,R,L,R...
            val framesPerChunk = 1024
            val chunk = ShortArray(framesPerChunk * 2)

            val framesPerTone =
                (durationMs / 1000.0 * sampleRate)
                    .toLong()
                    .coerceAtLeast(1L)

            // Fade massimo 20 ms, ma mai più del 20% del tono.
            val fadeFrames = min(
                (0.020 * sampleRate).toInt(),
                max(1, (framesPerTone / 5).toInt())
            )

            var phase = 0.0

            try {
                track.play()

                while (running) {

                    for (semi in sequence) {
                        if (!running) break

                        val freq =
                            center * 2.0.pow(semi / 12.0)

                        // true = sinistra, false = destra
                        val leftChannel = Random.nextBoolean()
                        val channelName =
                            if (leftChannel) "SX" else "DX"

                        runOnUiThread {
                            status.text =
                                "%.0f Hz  •  %s  •  %+d semitoni"
                                    .format(freq, channelName, semi)
                        }

                        var produced = 0L

                        while (
                            produced < framesPerTone &&
                            running
                        ) {
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

                                val value =
                                    sin(phase) *
                                    amplitude *
                                    fade

                                val sample =
                                    (value * Short.MAX_VALUE)
                                        .toInt()
                                        .coerceIn(
                                            Short.MIN_VALUE.toInt(),
                                            Short.MAX_VALUE.toInt()
                                        )
                                        .toShort()

                                val index = i * 2

                                if (leftChannel) {
                                    chunk[index] = sample
                                    chunk[index + 1] = 0
                                } else {
                                    chunk[index] = 0
                                    chunk[index + 1] = sample
                                }

                                phase +=
                                    2.0 * Math.PI *
                                    freq / sampleRate

                                if (phase >= 2.0 * Math.PI)
                                    phase -= 2.0 * Math.PI
                            }

                            track.write(
                                chunk,
                                0,
                                frameCount * 2
                            )

                            produced += frameCount
                        }
                    }
                }

            } finally {
                try {
                    track.pause()
                    track.flush()
                    track.stop()
                } catch (_: Exception) {}

                track.release()

                runOnUiThread {
                    status.text = "Fermato"
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
