package com.example.tonestepper

import android.media.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

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
            val center = frequency.text.toString().toDoubleOrNull() ?: 15000.0
            val seconds = (duration.text.toString().toDoubleOrNull() ?: 10.0).coerceIn(0.5, 60.0)
            val amp = (volume.progress / 100.0).coerceIn(0.0, 1.0) * 0.35
            startAudio(center, seconds, amp)
        }

        findViewById<Button>(R.id.stop).setOnClickListener { stopAudio() }
    }

    private fun startAudio(center: Double, seconds: Double, amplitude: Double) {
        running = true
        audioThread = Thread {
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
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
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuffer, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val chunk = ShortArray(1024)
            val samplesPerTone = (seconds * sampleRate).toLong()
            val fadeSamples = (0.075 * sampleRate).toInt() // 75 ms
            var phase = 0.0

            try {
                track.play()
                while (running) {
                    for (semi in sequence) {
                        if (!running) break
                        val freq = center * 2.0.pow(semi / 12.0)
                        runOnUiThread {
                            status.text = "Riproduzione: %.0f Hz  (%+d semitoni)".format(freq, semi)
                        }

                        var produced = 0L
                        while (produced < samplesPerTone && running) {
                            val count = min(chunk.size.toLong(), samplesPerTone - produced).toInt()
                            for (i in 0 until count) {
                                val pos = produced + i
                                val edge = min(pos, samplesPerTone - 1 - pos)
                                val fade = if (edge < fadeSamples) edge.toDouble() / fadeSamples else 1.0
                                val value = sin(phase) * amplitude * fade
                                chunk[i] = (value * Short.MAX_VALUE).toInt().toShort()
                                phase += 2.0 * Math.PI * freq / sampleRate
                                if (phase >= 2.0 * Math.PI) phase -= 2.0 * Math.PI
                            }
                            track.write(chunk, 0, count)
                            produced += count
                        }
                    }
                }
            } finally {
                try { track.pause(); track.flush(); track.stop() } catch (_: Exception) {}
                track.release()
                runOnUiThread { status.text = "Fermato" }
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
