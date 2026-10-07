package com.example.tonestepper

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import kotlin.math.*
import kotlin.random.Random

class AudioService : Service() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"

        private const val CHANNEL_ID = "ToneStepperAudio"
        private const val NOTIFICATION_ID = 1001
    }

    @Volatile
    private var running = false

    private var audioThread: Thread? = null

    private val sampleRate = 48000

    private val semitones =
        intArrayOf(-2, -1, 0, 1, 2)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                stopAudio()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_START -> {

                startAsForeground()

                if (!running) {
                    startAudio()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startAsForeground() {

        val openIntent =
            Intent(this, MainActivity::class.java)

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        val stopIntent =
            Intent(this, AudioService::class.java)
                .setAction(ACTION_STOP)

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        val notification =
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Tone Stepper")
                .setContentText("Riproduzione audio attiva")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(
                    android.R.drawable.ic_media_pause,
                    "STOP",
                    stopPendingIntent
                )
                .build()

        if (Build.VERSION.SDK_INT >= 29) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun startAudio() {

        running = true

        val prefs =
            getSharedPreferences(
                "ToneStepperPrefs",
                MODE_PRIVATE
            )

        audioThread = Thread {

            val minBuffer =
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

            val track =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(
                                AudioAttributes.USAGE_MEDIA
                            )
                            .setContentType(
                                AudioAttributes.CONTENT_TYPE_MUSIC
                            )
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(
                                AudioFormat.ENCODING_PCM_16BIT
                            )
                            .setSampleRate(sampleRate)
                            .setChannelMask(
                                AudioFormat.CHANNEL_OUT_STEREO
                            )
                            .build()
                    )
                    .setBufferSizeInBytes(
                        max(minBuffer, 16384)
                    )
                    .setTransferMode(
                        AudioTrack.MODE_STREAM
                    )
                    .build()

            val framesPerChunk = 512

            val buffer =
                ShortArray(framesPerChunk * 2)

            var phase = 0.0

            var pinkL = 0.0
            var pinkR = 0.0

            var brownL = 0.0
            var brownR = 0.0

            var previousWhiteL = 0.0
            var previousWhiteR = 0.0

            val sessionStart =
                SystemClock.elapsedRealtime()

            fun makeNoise(
                type: String,
                left: Boolean
            ): Double {

                if (type == "Nessuno")
                    return 0.0

                val white =
                    Random.nextDouble(-1.0, 1.0)

                return when (type) {

                    "Bianco" -> white

                    "Rosa" -> {

                        if (left) {
                            pinkL =
                                0.98 * pinkL +
                                0.02 * white

                            (pinkL * 5.0)
                                .coerceIn(-1.0, 1.0)

                        } else {

                            pinkR =
                                0.98 * pinkR +
                                0.02 * white

                            (pinkR * 5.0)
                                .coerceIn(-1.0, 1.0)
                        }
                    }

                    "Marrone" -> {

                        if (left) {

                            brownL =
                                (brownL + white * 0.02)
                                    .coerceIn(-1.0, 1.0)

                            brownL

                        } else {

                            brownR =
                                (brownR + white * 0.02)
                                    .coerceIn(-1.0, 1.0)

                            brownR
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

                    /*
                     * Questi valori vengono riletti a ogni tono.
                     * Frequenza e intervallo quindi possono cambiare
                     * senza STOP/START.
                     */

                    val center =
                        prefs.getFloat(
                            "frequency",
                            15000f
                        ).toDouble()
                            .coerceIn(
                                100.0,
                                20000.0
                            )

                    val intervalMs =
                        prefs.getInt(
                            "interval",
                            500
                        ).coerceIn(
                            50,
                            60000
                        )

                    val durationMinutes =
                        prefs.getFloat(
                            "duration",
                            0f
                        ).toDouble()
                            .coerceAtLeast(0.0)

                    if (durationMinutes > 0.0) {

                        val maxDuration =
                            (durationMinutes *
                                60000.0).toLong()

                        if (
                            SystemClock.elapsedRealtime() -
                            sessionStart >= maxDuration
                        ) {
                            running = false
                            break
                        }
                    }

                    /*
                     * Semitono completamente casuale.
                     */

                    val semi =
                        semitones[
                            Random.nextInt(
                                semitones.size
                            )
                        ]

                    val frequency =
                        center *
                        2.0.pow(
                            semi / 12.0
                        )

                    /*
                     * Canale indipendentemente casuale.
                     */

                    val leftChannel =
                        Random.nextBoolean()

                    val totalFrames =
                        (
                            intervalMs /
                            1000.0 *
                            sampleRate
                        ).toLong()
                            .coerceAtLeast(1)

                    val fadeFrames =
                        min(
                            (0.020 * sampleRate)
                                .toInt(),
                            max(
                                1,
                                (totalFrames / 5)
                                    .toInt()
                            )
                        )

                    var produced = 0L

                    while (
                        produced < totalFrames &&
                        running
                    ) {

                        /*
                         * Volumi e rumore vengono riletti
                         * a ogni buffer (~10 ms):
                         * risposta praticamente immediata.
                         */

                        val toneAmp =
                            (
                                prefs.getInt(
                                    "toneVolume",
                                    10
                                ) / 100.0
                            ) * 0.30

                        val noiseAmp =
                            (
                                prefs.getInt(
                                    "noiseVolume",
                                    5
                                ) / 100.0
                            ) * 0.20

                        val noiseType =
                            prefs.getString(
                                "noiseType",
                                "Nessuno"
                            ) ?: "Nessuno"

                        val frameCount =
                            min(
                                framesPerChunk.toLong(),
                                totalFrames - produced
                            ).toInt()

                        for (i in 0 until frameCount) {

                            val position =
                                produced + i

                            val edge =
                                min(
                                    position,
                                    totalFrames -
                                    1 -
                                    position
                                )

                            val fade =
                                if (edge < fadeFrames)
                                    edge.toDouble() /
                                    fadeFrames
                                else
                                    1.0

                            val tone =
                                sin(phase) *
                                toneAmp *
                                fade

                            val noiseL =
                                makeNoise(
                                    noiseType,
                                    true
                                ) * noiseAmp

                            val noiseR =
                                makeNoise(
                                    noiseType,
                                    false
                                ) * noiseAmp

                            val left =
                                noiseL +
                                if (leftChannel)
                                    tone
                                else
                                    0.0

                            val right =
                                noiseR +
                                if (!leftChannel)
                                    tone
                                else
                                    0.0

                            val index = i * 2

                            buffer[index] =
                                (
                                    left.coerceIn(
                                        -1.0,
                                        1.0
                                    ) *
                                    Short.MAX_VALUE
                                ).toInt()
                                    .toShort()

                            buffer[index + 1] =
                                (
                                    right.coerceIn(
                                        -1.0,
                                        1.0
                                    ) *
                                    Short.MAX_VALUE
                                ).toInt()
                                    .toShort()

                            phase +=
                                2.0 *
                                Math.PI *
                                frequency /
                                sampleRate

                            if (
                                phase >=
                                2.0 * Math.PI
                            ) {
                                phase -=
                                    2.0 *
                                    Math.PI
                            }
                        }

                        val written =
                            track.write(
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

            } catch (_: Exception) {

                running = false

            } finally {

                try {
                    track.pause()
                    track.flush()
                    track.stop()
                } catch (_: Exception) {}

                track.release()

                running = false

                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )

                stopSelf()
            }

        }.also {
            it.start()
        }
    }

    private fun stopAudio() {
        running = false
        audioThread?.interrupt()
        audioThread = null
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Riproduzione Tone Stepper",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "Mantiene Tone Stepper attivo in background"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? =
        null
}
