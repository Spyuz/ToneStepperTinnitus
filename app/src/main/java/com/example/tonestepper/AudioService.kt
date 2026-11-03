package com.example.tonestepper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AudioService : Service() {

    companion object {
        const val ACTION_START = "com.example.tonestepper.START"
        private const val CHANNEL_ID = "tone_stepper_audio"
        private const val NOTIFICATION_ID = 501
        private const val SAMPLE_RATE = 48000
    }

    @Volatile private var running = false
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    private val prefs by lazy {
        getSharedPreferences("ToneStepper", MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START && !running) {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, notification())
            running = true
            audioThread = Thread { playAudio() }.also { it.start() }
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tone Stepper")
            .setContentText("Riproduzione audio attiva")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tone Stepper audio",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun playAudio() {
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) {
            stopSelf()
            return
        }

        val bufferSamples = maxOf(1024, minBytes / 2)
        val buffer = ShortArray(bufferSamples + bufferSamples % 2)

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        var phase = 0.0
        var noiseState = 0.0
        var lastNoise = 0.0
        var pink0 = 0.0
        var pink1 = 0.0
        var pink2 = 0.0
        var pink3 = 0.0
        var pink4 = 0.0
        var pink5 = 0.0

        var nextToneAt = System.nanoTime()
        var toneUntil = 0L
        var toneFrequency = 15000.0
        val startedAt = System.nanoTime()

        try {
            while (running) {
                val now = System.nanoTime()
                val durationMin = prefs.getString("duration", "10")
                    ?.toLongOrNull() ?: 10L

                if (durationMin > 0 &&
                    now - startedAt >= durationMin * 60_000_000_000L
                ) break

                val base = prefs.getString("frequency", "15000")
                    ?.toDoubleOrNull() ?: 15000.0
                val intervalMs = prefs.getString("interval", "500")
                    ?.toLongOrNull() ?: 500L

                if (now >= nextToneAt) {
                    val semitone = Random.nextInt(-2, 3)
                    toneFrequency = (base * Math.pow(
                        2.0, semitone / 12.0
                    )).coerceIn(20.0, 20000.0)
                    toneUntil = now + 100_000_000L
                    nextToneAt = now + intervalMs * 1_000_000L
                }

                val earMode = prefs.getString("earMode", "both") ?: "both"
                val toneGain = prefs.getInt("toneVolume", 20) / 100.0 * 0.25
                val noiseGain = prefs.getInt("noiseVolume", 10) / 100.0 * 0.15
                val noiseMode = prefs.getInt("noiseType", 0)
                val toneActive = now < toneUntil

                var index = 0
                while (index + 1 < buffer.size) {
                    val tone = if (toneActive) {
                        sin(phase) * toneGain
                    } else 0.0

                    phase += 2.0 * PI * toneFrequency / SAMPLE_RATE
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI

                    val white = Random.nextDouble(-1.0, 1.0)
                    noiseState = 0.98 * noiseState + 0.02 * white
                    pink0 = 0.99886 * pink0 + white * 0.0555179
                    pink1 = 0.99332 * pink1 + white * 0.0750759
                    pink2 = 0.96900 * pink2 + white * 0.1538520
                    pink3 = 0.86650 * pink3 + white * 0.3104856
                    pink4 = 0.55 * pink4 + white * 0.5329522
                    pink5 = -0.7616 * pink5 - white * 0.0168980
                    val pink = (pink0 + pink1 + pink2 + pink3 + pink4 +
                        pink5 + white * 0.5362) * 0.11
                    val noise = when (noiseMode) {
                        1 -> white
                        2 -> pink
                        3 -> noiseState
                        4 -> white - lastNoise
                        else -> 0.0
                    }
                    lastNoise = white

                    val leftTone = if (earMode != "right") tone else 0.0
                    val rightTone = if (earMode != "left") tone else 0.0
                    val left = (leftTone + noise * noiseGain)
                        .coerceIn(-0.95, 0.95)
                    val right = (rightTone + noise * noiseGain)
                        .coerceIn(-0.95, 0.95)

                    buffer[index] = (left * 32767).toInt().toShort()
                    buffer[index + 1] = (right * 32767).toInt().toShort()
                    index += 2
                }

                var written = 0
                while (written < buffer.size && running) {
                    val count = track.write(
                        buffer, written, buffer.size - written
                    )
                    if (count <= 0) break
                    written += count
                }
            }
        } catch (_: Exception) {
            // Arresto del servizio o uscita audio non disponibile.
        } finally {
            running = false
            try {
                track.pause()
                track.flush()
                track.stop()
            } catch (_: Exception) {}
            track.release()
            audioTrack = null
            stopSelf()
        }
    }

    override fun onDestroy() {
        running = false
        try {
            audioTrack?.pause()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
