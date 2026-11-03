package com.example.tonestepper

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("ToneStepper", MODE_PRIVATE)
    }

    private lateinit var frequency: EditText
    private lateinit var interval: EditText
    private lateinit var duration: EditText
    private lateinit var ears: RadioGroup
    private lateinit var toneVolume: SeekBar
    private lateinit var noiseVolume: SeekBar
    private lateinit var noiseType: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        frequency = findViewById(R.id.frequency)
        interval = findViewById(R.id.interval)
        duration = findViewById(R.id.duration)
        ears = findViewById(R.id.earSelection)
        toneVolume = findViewById(R.id.toneVolume)
        noiseVolume = findViewById(R.id.noiseVolume)
        noiseType = findViewById(R.id.noiseType)

        frequency.setText(prefs.getString("frequency", "15000"))
        interval.setText(prefs.getString("interval", "500"))
        duration.setText(prefs.getString("duration", "10"))

        when (prefs.getString("earMode", "both")) {
            "left" -> ears.check(R.id.earLeft)
            "right" -> ears.check(R.id.earRight)
            else -> ears.check(R.id.earBoth)
        }

        toneVolume.progress = prefs.getInt("toneVolume", 20)
        noiseVolume.progress = prefs.getInt("noiseVolume", 10)

        val noises = listOf("Nessuno", "Bianco", "Rosa", "Marrone", "Violetto")
        noiseType.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            noises
        )
        noiseType.setSelection(prefs.getInt("noiseType", 0))

        updateLabels()

        ears.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.earLeft -> "left"
                R.id.earRight -> "right"
                else -> "both"
            }
            prefs.edit().putString("earMode", mode).apply()
        }

        toneVolume.setOnSeekBarChangeListener(volumeListener("toneVolume"))
        noiseVolume.setOnSeekBarChangeListener(volumeListener("noiseVolume"))

        noiseType.onItemSelectedListener = object :
            android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                prefs.edit().putInt("noiseType", position).apply()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (!saveSettings()) return@setOnClickListener

            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }

            val intent = Intent(this, AudioService::class.java)
                .setAction(AudioService.ACTION_START)
            ContextCompat.startForegroundService(this, intent)
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, AudioService::class.java))
        }
    }

    private fun volumeListener(key: String) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                bar: SeekBar?, value: Int, fromUser: Boolean
            ) {
                prefs.edit().putInt(key, value).apply()
                updateLabels()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        }

    private fun updateLabels() {
        findViewById<TextView>(R.id.toneVolumeLabel)
            .text = "${toneVolume.progress}%"
        findViewById<TextView>(R.id.noiseVolumeLabel)
            .text = "${noiseVolume.progress}%"
    }

    private fun saveSettings(): Boolean {
        val f = frequency.text.toString().toDoubleOrNull()
        val i = interval.text.toString().toLongOrNull()
        val d = duration.text.toString().toLongOrNull()

        if (f == null || f !in 100.0..20000.0 ||
            i == null || i !in 100L..10000L ||
            d == null || d < 0L
        ) {
            Toast.makeText(
                this,
                "Controlla frequenza, intervallo e durata.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val mode = when (ears.checkedRadioButtonId) {
            R.id.earLeft -> "left"
            R.id.earRight -> "right"
            else -> "both"
        }

        prefs.edit()
            .putString("frequency", f.toString())
            .putString("interval", i.toString())
            .putString("duration", d.toString())
            .putString("earMode", mode)
            .putInt("toneVolume", toneVolume.progress)
            .putInt("noiseVolume", noiseVolume.progress)
            .putInt("noiseType", noiseType.selectedItemPosition)
            .apply()

        return true
    }
}
