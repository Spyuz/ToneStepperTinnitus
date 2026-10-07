package com.example.tonestepper

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("ToneStepperPrefs", MODE_PRIVATE)

        setContentView(R.layout.activity_main)

        requestNotificationPermission()
        setupInterface()

        if (!prefs.getBoolean("disclaimerAccepted", false)) {
            showDisclaimer()
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }

    private fun setupInterface() {

        val frequency = findViewById<EditText>(R.id.frequency)
        val interval = findViewById<EditText>(R.id.interval)
        val duration = findViewById<EditText>(R.id.duration)

        val toneVolume = findViewById<SeekBar>(R.id.toneVolume)
        val noiseVolume = findViewById<SeekBar>(R.id.noiseVolume)

        val noiseType = findViewById<Spinner>(R.id.noiseType)

        frequency.setText(
            prefs.getFloat("frequency", 15000f).toString().removeSuffix(".0")
        )

        interval.setText(
            prefs.getInt("interval", 500).toString()
        )

        duration.setText(
            prefs.getFloat("duration", 0f).toString().removeSuffix(".0")
        )

        toneVolume.progress =
            prefs.getInt("toneVolume", 10)

        noiseVolume.progress =
            prefs.getInt("noiseVolume", 5)

        val noises = arrayOf(
            "Nessuno",
            "Bianco",
            "Rosa",
            "Marrone",
            "Viola"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            noises
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        noiseType.adapter = adapter

        val savedNoise =
            prefs.getString("noiseType", "Nessuno") ?: "Nessuno"

        val savedPosition =
            noises.indexOf(savedNoise).coerceAtLeast(0)

        noiseType.setSelection(savedPosition)

        frequency.liveChange {
            val value =
                frequency.text.toString().toFloatOrNull() ?: return@liveChange

            prefs.edit()
                .putFloat("frequency", value.coerceIn(100f, 20000f))
                .apply()
        }

        interval.liveChange {
            val value =
                interval.text.toString().toIntOrNull() ?: return@liveChange

            prefs.edit()
                .putInt("interval", value.coerceIn(50, 60000))
                .apply()
        }

        duration.liveChange {
            val value =
                duration.text.toString().toFloatOrNull() ?: return@liveChange

            prefs.edit()
                .putFloat("duration", value.coerceAtLeast(0f))
                .apply()
        }

        toneVolume.setOnSeekBarChangeListener(
            simpleSeekListener {
                prefs.edit()
                    .putInt("toneVolume", toneVolume.progress)
                    .apply()
            }
        )

        noiseVolume.setOnSeekBarChangeListener(
            simpleSeekListener {
                prefs.edit()
                    .putInt("noiseVolume", noiseVolume.progress)
                    .apply()
            }
        )

        noiseType.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    prefs.edit()
                        .putString("noiseType", noises[position])
                        .apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        findViewById<Button>(R.id.start).setOnClickListener {

            saveCurrentValues(
                frequency,
                interval,
                duration,
                toneVolume,
                noiseVolume,
                noiseType
            )

            val intent =
                Intent(this, AudioService::class.java)
                    .setAction(AudioService.ACTION_START)

            ContextCompat.startForegroundService(this, intent)
        }

        findViewById<Button>(R.id.stop).setOnClickListener {

            val intent =
                Intent(this, AudioService::class.java)
                    .setAction(AudioService.ACTION_STOP)

            startService(intent)
        }
    }

    private fun saveCurrentValues(
        frequency: EditText,
        interval: EditText,
        duration: EditText,
        toneVolume: SeekBar,
        noiseVolume: SeekBar,
        noiseType: Spinner
    ) {

        prefs.edit()
            .putFloat(
                "frequency",
                frequency.text.toString().toFloatOrNull()
                    ?.coerceIn(100f, 20000f) ?: 15000f
            )
            .putInt(
                "interval",
                interval.text.toString().toIntOrNull()
                    ?.coerceIn(50, 60000) ?: 500
            )
            .putFloat(
                "duration",
                duration.text.toString().toFloatOrNull()
                    ?.coerceAtLeast(0f) ?: 0f
            )
            .putInt("toneVolume", toneVolume.progress)
            .putInt("noiseVolume", noiseVolume.progress)
            .putString("noiseType", noiseType.selectedItem.toString())
            .apply()
    }

    private fun showDisclaimer() {

        AlertDialog.Builder(this)
            .setTitle("Avvertenza")
            .setMessage(
                "Tone Stepper genera toni ad alta frequenza e rumori audio.\n\n" +
                "L'app non è un dispositivo medico e non è destinata a " +
                "diagnosticare, trattare o curare l'acufene o altre condizioni.\n\n" +
                "Utilizza un volume basso e confortevole. Interrompi l'ascolto " +
                "in caso di fastidio, dolore, peggioramento dell'acufene o " +
                "altri sintomi.\n\n" +
                "L'utilizzo dell'app non sostituisce il parere di un medico " +
                "o di un professionista sanitario qualificato."
            )
            .setCancelable(false)
            .setPositiveButton("HO LETTO E ACCETTO") { _, _ ->

                prefs.edit()
                    .putBoolean("disclaimerAccepted", true)
                    .apply()
            }
            .show()
    }

    private fun EditText.liveChange(action: () -> Unit) {

        addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    action()
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        )
    }

    private fun simpleSeekListener(
        action: () -> Unit
    ): SeekBar.OnSeekBarChangeListener {

        return object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) action()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }
}
